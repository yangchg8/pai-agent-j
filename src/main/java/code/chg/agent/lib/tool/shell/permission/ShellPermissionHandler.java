package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.lib.tool.shell.safety.ShellAnalysisResult;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellPermissionHandler
 * @description Strategy interface for handling shell permission checks based on analysis status.
 */
public interface ShellPermissionHandler {

    /**
     * Check if this handler supports the given analysis status.
     *
     * @param status the analysis status to check
     * @return true if this handler can process the given status
     */
    boolean supports(ShellAnalysisResult.Status status);

    /**
     * Handle the permission check for the given analysis result.
     *
     * @param policy   the current session's permission policy, may be null
     * @param analysis the static analysis result
     * @param command  the original command string
     * @return the permission result
     */
    ToolPermissionResult handle(ToolPermissionPolicy policy, ShellAnalysisResult analysis, String command);
}
