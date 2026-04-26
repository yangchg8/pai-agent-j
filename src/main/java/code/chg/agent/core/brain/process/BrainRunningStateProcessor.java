package code.chg.agent.core.brain.process;

import code.chg.agent.core.brain.AbstractBrain;
import code.chg.agent.core.brain.BrainRunningState;
import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;
import code.chg.agent.core.event.body.AuthorizationScope;
import code.chg.agent.core.event.body.AuthorizationRequestEventBody;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.event.message.AuthorizationResponseEventMessage;
import code.chg.agent.core.event.message.ToolEventMessage;
import code.chg.agent.core.event.message.ToolAuthorizationRequestEventMessage;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.prompt.system.PermissionPrompt;
import code.chg.agent.llm.LLMClient;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.ToolCall;
import code.chg.agent.llm.component.AuthorizationRequirementContent;
import code.chg.agent.llm.message.ToolCallResponseLLMMessage;
import code.chg.agent.utils.MessageIdGenerator;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title BrainRunningStateProcessor
 * @description Processor responsible for updating tool execution state before the next model call.
 */
@Slf4j
public class BrainRunningStateProcessor implements EventMessageProcessor {
    private final AbstractBrain brain;

    public BrainRunningStateProcessor(AbstractBrain brain) {
        this.brain = brain;
    }

    @Override
    public EventMessageResponse process(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        EventMessageResponse response = null;
        switch (message.type()) {
            case TOOL_CALL_RESPONSE -> {
                response = processToolCallResponse(message, context, chain);
            }
            case TOOL_AUTHORIZATION_REQUEST -> {
                response = processToolAuthorizationRequest(message, context, chain);
            }
            case TOOL_AUTHORIZATION_RESPONSE -> {
                response = processToolAuthorizationResponse(message, context, chain);
            }
            case HUMAN_MESSAGE -> {
                response = processHumanMessage(message, context, chain);
            }
            default -> {
                response = new EventMessageResponse(null, false,
                        "No state transition is defined for " + message.type() + " in BrainRunningStateProcessor.");
            }
        }
        if (response == null) {
            return null;
        }
        if (!response.isCallLLM()) {
            return response;
        }
        if (response.getMessages() != null) {
            List<LLMMessage> llmMessages = response.getMessages();
            List<ToolCall> toolCalls = llmMessages.stream().flatMap(llmMessage -> llmMessage.toolCalls().stream()).toList();
            log.debug("Agent response, call tools count:{}", toolCalls.size());
            brain.getBrainRunningState().overrideRunningTools(toolCalls);
            for (ToolCall toolCall : toolCalls) {
                brain.publish(new ToolEventMessage(toolCall, brain.getBrainRunningState().getToolPermission(toolCall.name())));
            }
        }
        return response;
    }


    private EventMessageResponse processHumanMessage(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        if (!brain.getBrainRunningState().nonRunningTool()) {
            return new EventMessageResponse(null, false, "Waiting for running tool calls to finish.");
        }
        return chain.next(message, context);
    }

    private EventMessageResponse processToolCallResponse(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        String id = ((ToolEventBody) message.body()).toolCallId();
        boolean isContinue = brain.getBrainRunningState().markFinishAndContinue(id);
        if (!isContinue) {
            return new EventMessageResponse(null, false, "Waiting for the remaining tool calls to finish.");
        }
        log.debug("All tools have finished, agent can continue call model.");
        return chain.next(message, context);
    }

    private EventMessageResponse processToolAuthorizationResponse(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        AuthorizationResponseEventMessage authorizationMessage = (AuthorizationResponseEventMessage) message;
        AuthorizationResponseEventBody eventBody = authorizationMessage.getBody();
        BrainRunningState runningState = brain.getBrainRunningState();
        BrainRunningState.RunningTool runningTool = runningState.queryAuthorizingTool(eventBody.authorizationId());
        if (runningTool == null) {
            return new EventMessageResponse(null, false, "No tool is currently awaiting authorization.");
        }
        AuthorizationScope scope = eventBody.scope();
        if (scope == AuthorizationScope.REJECTED) {
            String toolCallRejectedId = MessageIdGenerator.generateWithPrefix("tool_call_rejected");
            runningTool.markRejected();
            context.chat().publish(ChannelMessageBuilder.builder(toolCallRejectedId, brain.name(), ChannelMessageType.TOOL_CALL_REJECTED)
                    .toolCallRejected(runningTool.id(), "human rejected").build(true));
            return new EventMessageResponse(Collections.singletonList(
                    new ToolCallResponseLLMMessage(MessageIdGenerator.generateWithPrefix("tool_auth_reject"), runningTool.id(), "Authorization failed: execution was rejected.")),
                    false, "Tool execution was rejected.");
        }
        if (scope == AuthorizationScope.CONTINUE) {
            // Allow execution this time without saving any permissions; tool skips permission check via CONTINUE status
            runningTool.markContinue();
            this.brain.publish(new ToolEventMessage(runningTool.getToolCall(), runningState.getToolPermission(runningTool.name()), true));
            return new EventMessageResponse(null, false, "Retrying the tool call without persisting permissions.");
        }
        BrainRunningState.AuthorizationStatus authorizationStatus = runningTool.getAuthorizationStatus();
        AuthorizationRequirementContent authorizationRequirementContent = authorizationStatus.getContent();
        ToolPermissionPolicy executePermissionPolicy;
        if (scope == AuthorizationScope.GRANT) {
            runningState.authorizeTool(runningTool.name(), authorizationRequirementContent);
            ToolPermissionPolicy permissionPolicy = runningState.getToolPermission(runningTool.name());
            executePermissionPolicy = new ToolPermissionPolicy(runningTool.name(), permissionPolicy.getPermissions());
        } else {
            ToolPermissionPolicy permissionPolicy = runningState.getToolPermission(runningTool.name());
            executePermissionPolicy = permissionPolicy != null
                    ? new ToolPermissionPolicy(runningTool.name(), permissionPolicy.getPermissions())
                    : new ToolPermissionPolicy(runningTool.name());
            List<AuthorizationRequirementContent.AuthorizationRequirementItem> items = authorizationRequirementContent.getItems();
            if (items != null) {
                for (AuthorizationRequirementContent.AuthorizationRequirementItem item : items) {
                    executePermissionPolicy.addPermission(item.getResource(), item.getPermissions());
                }
            }
        }
        runningTool.markRequest();
        this.brain.publish(new ToolEventMessage(runningTool.getToolCall(), executePermissionPolicy));
        return new EventMessageResponse(null, false, "Retrying the tool call with updated permissions.");
    }


    private EventMessageResponse processToolAuthorizationRequest(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        ToolAuthorizationRequestEventMessage permissionEventMessage = (ToolAuthorizationRequestEventMessage) message;
        BrainRunningState.RunningTool runningTool = brain.getBrainRunningState().getRunningTool(permissionEventMessage.toolCallId());
        if (runningTool.isFinished()) {
            return new EventMessageResponse(null, false, "The tool call has already finished.");
        }
        AuthorizationRequestEventBody eventBody = permissionEventMessage.getEventBody();

        if (eventBody.hasDirectContent()) {
            AuthorizationRequirementContent directContent = eventBody.getDirectContent();
            String permissionId = MessageIdGenerator.generateWithPrefix("perm_req");
            runningTool.markAuthorizing(permissionId, directContent);
            context.chat().publish(ChannelMessageBuilder.builder(permissionId, brain.name(), ChannelMessageType.TOOL_AUTHORIZATION_REQUEST)
                    .authorizationRequirement(runningTool.id(), directContent)
                    .build(true));
            return new EventMessageResponse(null, false, "Waiting for a rule-based authorization decision.");
        }

        ToolCall toolCall = runningTool.getToolCall();
        LLMClient client = brain.client();
        String input = PermissionPrompt.permissionPrompt(eventBody.getAuthorizationPrompt(), toolCall.name(), toolCall.arguments());
        try {
            AuthorizationRequirementContent authorizationRequirementContent = client.structureResponseChat(input, AuthorizationRequirementContent.class);
            String permissionId = MessageIdGenerator.generateWithPrefix("perm_req");
            runningTool.markAuthorizing(permissionId, authorizationRequirementContent);
            context.chat().publish(ChannelMessageBuilder.builder(permissionId, brain.name(), ChannelMessageType.TOOL_AUTHORIZATION_REQUEST)
                    .authorizationRequirement(runningTool.id(), authorizationRequirementContent)
                    .build(true));
            return new EventMessageResponse(null, false, "Waiting for an authorization decision.");
        } catch (Exception e) {
            log.error("processToolPermissionRequest exception:{}", e.getMessage(), e);
            return new EventMessageResponse(null, false, "Failed to process the tool authorization request.");
        }
    }
}
