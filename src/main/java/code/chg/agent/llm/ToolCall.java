package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolCall
 * @description Defines a model-issued tool call.
 */
public interface ToolCall {
    /**
     * Returns the tool call identifier.
     *
     * @return the tool call identifier
     */
    String id();

    /**
     * Returns the requested tool name.
     *
     * @return the tool name
     */
    String name();

    /**
     * Returns the serialized tool arguments.
     *
     * @return the serialized arguments
     */
    String arguments();
}
