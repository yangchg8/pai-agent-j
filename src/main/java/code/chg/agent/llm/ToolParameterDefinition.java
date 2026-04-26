package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolParameterDefinition
 * @description Defines metadata for a tool parameter.
 */
public interface ToolParameterDefinition {
    /**
     * Returns the parameter description.
     *
     * @return the parameter description
     */
    String description();

    /**
     * Returns the parameter name.
     *
     * @return the parameter name
     */
    String name();

    /**
     * Indicates whether the parameter is required.
     *
     * @return {@code true} when the parameter is required
     */
    boolean required();

    /**
     * Returns the parameter type definition.
     *
     * @return the parameter type definition
     */
    ToolParameterType type();
}
