package code.chg.agent.core.tool;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolAuthModeType
 * @description Enumerates supported tool authorization strategies.
 */
public enum ToolAuthModeType {
    /**
     * Authorization delegated to the model.
     */
    LLM_BASED,
    /**
     * Authorization decided directly from rules.
     */
    RULE_BASED,
    /**
     * Authorization denied without execution.
     */
    REJECTED
}
