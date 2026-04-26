package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.lib.tool.shell.safety.ShellAnalysisResult;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title FallbackHandler
 * @description Handles permission checks for unparseable commands and mixed pipelines.
 */
public class FallbackHandler implements ShellPermissionHandler {

    @Override
    public boolean supports(ShellAnalysisResult.Status status) {
        return status == ShellAnalysisResult.Status.UNPARSEABLE;
    }

    @Override
    public ToolPermissionResult handle(ToolPermissionPolicy policy, ShellAnalysisResult analysis, String command) {
        return ToolPermissionResultFactory.singleAuthorization(
                "Execute shell command: "
                        + command
                        + "\nSingle-use authorization required.");
    }
}
