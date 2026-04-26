package code.chg.agent.core.tool;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolPermissionResult
 * @description Defines the outcome of a tool permission check.
 */
public interface ToolPermissionResult {
    /**
     * Indicates whether the tool call can proceed immediately.
     *
     * @return {@code true} when permission is already granted
     */
    boolean hasPermission();

    /**
     * Returns the authorization mode for the tool call.
     *
     * @return the authorization mode
     */
    ToolAuthorizationMode authorizationMode();
}
