package code.chg.agent.core.tool;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolLLMBasedAuthorizationMode
 * @description Defines an authorization mode that requires model-generated guidance.
 */
public interface ToolLLMBasedAuthorizationMode extends ToolAuthorizationMode {
    /**
     * Returns the prompt used to request authorization guidance.
     *
     * @return the authorization prompt
     */
    String getPrompt();

    /**
     * Returns the authorization mode type.
     *
     * @return the LLM-based authorization mode type
     */
    default ToolAuthModeType getType() {
        return ToolAuthModeType.LLM_BASED;
    }
}
