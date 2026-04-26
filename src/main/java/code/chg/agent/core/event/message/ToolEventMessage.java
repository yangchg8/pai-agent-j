package code.chg.agent.core.event.message;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.EventBody;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.llm.ToolCall;
import code.chg.agent.utils.MessageIdGenerator;
import lombok.Getter;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolEventMessage
 * @description Event message representing tool execution input or a completed response.
 */
@Getter
public class ToolEventMessage implements EventMessage {

    private final ToolEventBody body;
    private final EventMessageType messageType;
    private final ToolPermissionPolicy permissionPolicy;
    private final String id;
    private final boolean skipPermissionCheck;

    public ToolEventMessage(ToolCall toolCall, ToolPermissionPolicy permissionPolicy) {
        this.body = new ToolEventBody(toolCall);
        this.messageType = EventMessageType.TOOL_CALL_REQUEST;
        this.id = MessageIdGenerator.generateWithPrefix("tool_request");
        this.permissionPolicy = permissionPolicy;
        this.skipPermissionCheck = false;
    }

    /**
     * Constructor for CONTINUE scope: tool is allowed to execute without permission checks.
     */
    public ToolEventMessage(ToolCall toolCall, ToolPermissionPolicy permissionPolicy, boolean skipPermissionCheck) {
        this.body = new ToolEventBody(toolCall);
        this.messageType = EventMessageType.TOOL_CALL_REQUEST;
        this.id = MessageIdGenerator.generateWithPrefix("tool_request");
        this.permissionPolicy = permissionPolicy;
        this.skipPermissionCheck = skipPermissionCheck;
    }

    public ToolEventMessage(ToolCall toolCall, String response) {
        this.body = new ToolEventBody(toolCall, response);
        this.messageType = EventMessageType.TOOL_CALL_RESPONSE;
        this.id = MessageIdGenerator.generateWithPrefix("tool_response");
        this.permissionPolicy = null;
        this.skipPermissionCheck = false;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public EventMessageType type() {
        return messageType;
    }

    @Override
    public EventBody body() {
        return body;
    }

}
