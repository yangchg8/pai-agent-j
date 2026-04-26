package code.chg.agent.core.tool;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolAuthorizationMode
 * @description Defines how a tool call must be authorized.
 */
public interface ToolAuthorizationMode {
    /**
     * Returns the authorization mode type.
     *
     * @return the authorization mode type
     */
    ToolAuthModeType getType();
}
