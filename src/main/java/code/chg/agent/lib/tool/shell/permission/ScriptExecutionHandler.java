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
 * @title ScriptExecutionHandler
 * @description Handles permission checks for script execution commands (e.g. "bash run.sh", "python main.py").
 */
public class ScriptExecutionHandler implements ShellPermissionHandler {

    @Override
    public boolean supports(ShellAnalysisResult.Status status) {
        return status == ShellAnalysisResult.Status.SCRIPT_EXECUTION;
    }

    @Override
    public ToolPermissionResult handle(ToolPermissionPolicy policy, ShellAnalysisResult analysis, String command) {
        List<ResourcePermission> resourcePermissions = analysis.getResourcePermissions();

        if (ShellPermissionPolicyMatcher.policyGrantsAllPermissions(policy, resourcePermissions)) {
            return ToolPermissionResultFactory.granted();
        }

        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips("Execute script: " + command);
        content.setItems(ShellPermissionPolicyMatcher.buildAuthItems(resourcePermissions));
        return ToolPermissionResultFactory.requireDirectAuthorization(content);
    }
}
