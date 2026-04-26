package code.chg.agent.core.tool;

import code.chg.agent.core.event.*;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.event.message.ToolAuthorizationRequestEventMessage;
import code.chg.agent.core.event.message.ToolEventMessage;
import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.llm.ToolDefinition;
import code.chg.agent.llm.ToolParameterDefinition;
import code.chg.agent.utils.JsonUtil;
import code.chg.agent.utils.ToolResponseFiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AbstractTool
 * @description Base implementation for reflective tool definitions and execution hooks.
 */
public abstract class AbstractTool implements Tool {
    ToolDefinition toolDefinition;
    Object object;
    Method method;
    EventMessageBus eventMessageBus;

    public AbstractTool(ToolDefinition toolDefinition, Object object, Method method) {
        this.toolDefinition = toolDefinition;
        this.object = object;
        this.method = method;
        this.eventMessageBus = null;
    }

    public static DefaultToolBuilder builder(String name) {
        return new DefaultToolBuilder(name);
    }

    @Override
    public String name() {
        return toolDefinition.name();
    }

    @Override
    public String description() {
        return toolDefinition.description();
    }

    @Override
    public List<ToolParameterDefinition> parameters() {
        return toolDefinition.parameters();
    }

    @Override
    public boolean concern(EventMessage message) {
        EventMessageType messageType = message.type();
        if (messageType == null) {
            return false;
        }
        if (messageType == EventMessageType.TOOL_CALL_REQUEST) {
            ToolEventMessage toolEventMessage = (ToolEventMessage) message;
            ToolEventBody messageBody = toolEventMessage.getBody();
            return name().equals(messageBody.name());
        }
        return false;
    }

    public boolean checkPermission(ToolEventMessage toolEventMessage) {
        ToolEventBody messageBody = toolEventMessage.getBody();
        try {
            String rawArguments = messageBody.arguments();
            Object[] arguments = parseObject(rawArguments);
            ToolPermissionResult toolPermissionResult = this.checkPermission(toolEventMessage.getPermissionPolicy(), arguments);
            if (toolPermissionResult == null) {
                return false;
            }
            if (toolPermissionResult.hasPermission()) {
                return true;
            }
            String toolCallId = messageBody.getToolCall().id();
            ToolAuthorizationMode authorizationMode = toolPermissionResult.authorizationMode();
            switch (authorizationMode.getType()) {
                case LLM_BASED -> {
                    ToolLLMBasedAuthorizationMode llmBasedMode = (ToolLLMBasedAuthorizationMode) authorizationMode;
                    this.eventMessageBus.publish(new ToolAuthorizationRequestEventMessage(toolCallId, llmBasedMode.getPrompt()));

                }
                case RULE_BASED -> {
                    ToolRuleBasedAuthorizationMode ruleBasedMode = (ToolRuleBasedAuthorizationMode) authorizationMode;
                    this.eventMessageBus.publish(new ToolAuthorizationRequestEventMessage(toolCallId, ruleBasedMode.authorizationContent()));
                }
                case REJECTED -> {
                    this.eventMessageBus.publish(new ToolEventMessage(messageBody.getToolCall(), "tool permission check failed, reject execute"));
                }
            }
        } catch (Exception e) {
            this.eventMessageBus.publish(new ToolEventMessage(messageBody.getToolCall(), "tool check permission error:" + e.getMessage()));
        }
        return false;
    }

    @Override
    public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
        ToolEventMessage toolEventMessage = (ToolEventMessage) message;
        ToolEventBody toolEventBody = (ToolEventBody) message.body();
        if (!toolEventMessage.isSkipPermissionCheck() && !checkPermission(toolEventMessage)) {
            return;
        }
        Object returnObj;
        try {
            Object[] arguments = parseObject(toolEventBody.arguments());
            returnObj = method.invoke(object, arguments);
        } catch (Throwable throwable) {
            returnObj = throwable;
        }
        String responseJson = JsonUtil.toJson(returnObj);
        String modelResponseJson = ToolResponseFiles.prepare(name(), toolEventBody.getToolCall().id(), responseJson)
                .inlineResponse();
        context.chat().publish(ChannelMessageBuilder
                .builder(toolEventBody.getToolCall().id(), name(), ChannelMessageType.TOOL_CALL_RESPONSE)
                .toolCallResponse(toolEventBody.getToolCall().id(), name(), modelResponseJson)
                .build(true));
        this.eventMessageBus.publish(new ToolEventMessage(toolEventBody.getToolCall(), modelResponseJson));
        callBack.onSuccess();
    }

    @Override
    public void onSubscribe(EventMessageBus eventMessageBus) {
        this.eventMessageBus = eventMessageBus;
    }

    private Object[] parseObject(String arguments) {
        List<ToolParameterDefinition> paramDefs = toolDefinition.parameters();
        if (paramDefs == null || paramDefs.isEmpty()) {
            return new Object[0];
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramDefs.size() == 1) {
            Object[] singleArgumentResult = tryParseSingleArgument(arguments, paramDefs.get(0).name(), paramTypes[0]);
            if (singleArgumentResult != null) {
                return singleArgumentResult;
            }
        }
        ObjectMapper mapper = JsonUtil.getObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(arguments);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse arguments JSON: " + arguments, e);
        }
        Object[] result = new Object[paramDefs.size()];
        for (int i = 0; i < paramDefs.size(); i++) {
            String name = paramDefs.get(i).name();
            JsonNode valueNode = root.get(name);
            if (valueNode == null || valueNode.isNull()) {
                result[i] = null;
                continue;
            }
            try {
                result[i] = mapper.treeToValue(valueNode, paramTypes[i]);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to deserialize parameter '" + name + "' to " + paramTypes[i].getSimpleName(), e);
            }
        }
        return result;
    }

    private Object[] tryParseSingleArgument(String arguments, String parameterName, Class<?> parameterType) {
        ObjectMapper mapper = JsonUtil.getObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(arguments);
        } catch (Exception ignored) {
            if (String.class.equals(parameterType)) {
                return new Object[]{arguments};
            }
            return null;
        }

        JsonNode valueNode;
        if (root == null || root.isNull()) {
            valueNode = null;
        } else if (root.isObject()) {
            valueNode = root.get(parameterName);
            if (valueNode == null && root.size() == 1) {
                valueNode = root.elements().next();
            }
        } else {
            valueNode = root;
        }

        if (valueNode == null || valueNode.isNull()) {
            return new Object[]{null};
        }

        try {
            return new Object[]{mapper.treeToValue(valueNode, parameterType)};
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to deserialize single parameter '" + parameterName + "' to " + parameterType.getSimpleName(), e);
        }
    }

}
