package code.chg.agent.core.channel;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChannelMessageType
 * @description Enumeration of chat channel message types.
 */
public enum ChannelMessageType {
    /**
     * Streamed reasoning content chunk.
     */
    THINKING_CHUNK,

    /**
     * Complete reasoning content.
     */
    THINKING,
    /**
     * Streamed LLM content chunk.
     */
    LLM_CONTENT_CHUNK,
    /**
     * Complete LLM content.
     */
    LLM_CONTENT,

    /**
     * Streamed tool call arguments.
     */
    TOOL_CALL_REQUEST_CHUNK,

    /**
     * Tool call response.
     */
    TOOL_CALL_RESPONSE,


    TOKEN_USAGE,

    COMPACT_NOTICE,

    SYSTEM_NOTICE,

    /**
     * Tool authorization request.
     */
    TOOL_AUTHORIZATION_REQUEST,
    /**
     * Tool execution rejection message.
     */
    TOOL_CALL_REJECTED,

    /**
     * Error message.
     */
    ERROR,

}
