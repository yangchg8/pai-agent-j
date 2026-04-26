package code.chg.agent.llm;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolDefinition
 * @description Defines metadata exposed for a callable tool.
 */
public interface ToolDefinition {
    /**
     * Returns the tool name.
     */
    String name();

    /**
     * Returns the tool description.
     */
    String description();

    /**
     * Returns the tool parameter definitions.
     */
    List<ToolParameterDefinition> parameters();
}
