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
 * @title AnalyzedCommandHandler
 * @description Handles permission checks for commands where all are in the whitelist.
 */
public class AnalyzedCommandHandler implements ShellPermissionHandler {

    @Override
    public boolean supports(ShellAnalysisResult.Status status) {
        return status == ShellAnalysisResult.Status.ANALYZED;
    }

    @Override
    public ToolPermissionResult handle(ToolPermissionPolicy policy, ShellAnalysisResult analysis, String command) {
        List<ResourcePermission> resourcePermissions = analysis.getResourcePermissions();

        if (ShellPermissionPolicyMatcher.policyGrantsAllPermissions(policy, resourcePermissions)) {
            return ToolPermissionResultFactory.granted();
        }

        // Collect only missing permissions (those not already granted by session policy)
        List<ResourcePermission> missingPermissions =
                ShellPermissionPolicyMatcher.collectMissingPermissions(policy, resourcePermissions);

        if (missingPermissions.isEmpty()) {
            return ToolPermissionResultFactory.granted();
        }

        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips("Execute shell command: " + command);
        content.setItems(ShellPermissionPolicyMatcher.buildAuthItems(missingPermissions));
        return ToolPermissionResultFactory.requireDirectAuthorization(content);
    }
}
