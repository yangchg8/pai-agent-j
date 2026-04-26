package code.chg.agent.core.event.message;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.AuthorizationRequestEventBody;
import code.chg.agent.core.event.body.EventBody;
import code.chg.agent.llm.component.AuthorizationRequirementContent;
import code.chg.agent.utils.MessageIdGenerator;
import lombok.Getter;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolAuthorizationRequestEventMessage
 * @description Event message requesting approval before a tool call can continue.
 */
@Getter
public class ToolAuthorizationRequestEventMessage implements EventMessage {

    private final AuthorizationRequestEventBody eventBody;
    private final String id;

    /**
     * LLM-based authorization: carries a prompt string for LLM analysis.
     */
    public ToolAuthorizationRequestEventMessage(String toolCallId, String permissionRequirement) {
        id = MessageIdGenerator.generateWithPrefix("auth_request");
        eventBody = new AuthorizationRequestEventBody(toolCallId, permissionRequirement);
    }

    /**
     * Rule-based authorization: carries pre-built structured content, Brain skips LLM.
     */
    public ToolAuthorizationRequestEventMessage(String toolCallId, AuthorizationRequirementContent directContent) {
        id = MessageIdGenerator.generateWithPrefix("auth_request");
        eventBody = new AuthorizationRequestEventBody(toolCallId, directContent);
    }

    public String toolCallId() {
        return eventBody.getToolCallId();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public EventMessageType type() {
        return EventMessageType.TOOL_AUTHORIZATION_REQUEST;
    }

    @Override
    public EventBody body() {
        return eventBody;
    }
}
