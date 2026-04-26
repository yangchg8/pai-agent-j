package code.chg.agent.core.event;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageType
 * @description Enumeration of event message types.
 */
public enum EventMessageType {

    /**
     * Human message.
     */
    HUMAN_MESSAGE,

    /**
     * Agent message.
     */
    AGENT_MESSAGE,

    /**
     * Tool call request message.
     */
    TOOL_CALL_REQUEST,
    /**
     * Tool call authorization request.
     */
    TOOL_AUTHORIZATION_REQUEST,

    /**
     * Tool authorization result message.
     */
    TOOL_AUTHORIZATION_RESPONSE,

    /**
     * Tool call response message.
     */
    TOOL_CALL_RESPONSE,

    /**
     * Tool call rejection message.
     */
    TOOL_CALL_REJECTED,

    /**
     * System message.
     */
    SYSTEM_MESSAGE
}
