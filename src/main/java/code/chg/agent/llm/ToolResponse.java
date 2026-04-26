package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolResponse
 * @description Defines the response returned for a tool call.
 */
public interface ToolResponse {
    /**
     * Returns the originating tool call.
     *
     * @return the originating tool call
     */
    ToolCall toolCall();
}
