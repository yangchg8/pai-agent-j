package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolCallChunk
 * @description Defines a streamed fragment of a tool call.
 */
public interface ToolCallChunk {
    /**
     * Returns the chunk index within the tool call stream.
     *
     * @return the chunk index
     */
    int index();

    /**
     * Returns the tool call identifier.
     *
     * @return the tool call identifier
     */
    String id();

    /**
     * Returns the tool name.
     *
     * @return the tool name
     */
    String name();

    /**
     * Returns the streamed argument fragment.
     *
     * @return the argument fragment
     */
    String arguments();
}
