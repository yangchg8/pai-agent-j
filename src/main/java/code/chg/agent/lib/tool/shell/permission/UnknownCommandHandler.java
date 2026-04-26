package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.lib.tool.shell.safety.ResourcePermission;
import code.chg.agent.lib.tool.shell.safety.ShellAnalysisResult;
import code.chg.agent.llm.component.AuthorizationRequirementContent;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title UnknownCommandHandler
 * @description Handles permission checks for commands not in the whitelist.
 */
public class UnknownCommandHandler implements ShellPermissionHandler {

    @Override
    public boolean supports(ShellAnalysisResult.Status status) {
        return status == ShellAnalysisResult.Status.NOT_IN_WHITELIST;
    }

    @Override
    public ToolPermissionResult handle(ToolPermissionPolicy policy, ShellAnalysisResult analysis, String command) {
        if (analysis.getCommandCount() == 1) {
            return handleSingleUnknownCommand(policy, analysis);
        }
        return handleFallback(command);
    }

    private ToolPermissionResult handleSingleUnknownCommand(ToolPermissionPolicy policy,
                                                            ShellAnalysisResult analysis) {
        List<ResourcePermission> resourcePermissions = analysis.getResourcePermissions();

        if (ShellPermissionPolicyMatcher.policyGrantsAllPermissions(policy, resourcePermissions)) {
            return ToolPermissionResultFactory.granted();
        }

        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips("Command '" + analysis.getUnrecognizedCommand()
                + "' is not in the built-in whitelist. "
                + "Authorize this command to allow all invocations of '"
                + analysis.getUnrecognizedCommand() + "'.");
        content.setItems(ShellPermissionPolicyMatcher.buildAuthItems(resourcePermissions));
        return ToolPermissionResultFactory.requireDirectAuthorization(content);
    }

    private ToolPermissionResult handleFallback(String command) {
        return ToolPermissionResultFactory.singleAuthorization(
                "Execute shell command: "
                        + command
                        + "\nSingle-use authorization required.");
    }
}
