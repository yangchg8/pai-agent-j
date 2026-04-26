package code.chg.agent.lib.auth;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.llm.component.AuthorizationRequirementContent;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolLevelPermissionChecker
 * @description Tool-level permission checker.
 */
public final class ToolLevelPermissionChecker {

    private ToolLevelPermissionChecker() {
    }

    /**
     * Check whether the policy grants tool-level access for the given tool name.
     *
     * @param policy   the current permission policy (may be null → always unauthorized)
     * @param toolName the tool name to check
     * @return granted result if authorized, or a rule-based authorization request otherwise
     */
    public static ToolPermissionResult checkPermission(ToolPermissionPolicy policy, String toolName) {
        String resource = "TOOL:" + toolName;

        if (policyGrantsTool(policy, resource)) {
            return ToolPermissionResultFactory.granted();
        }

        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips("Authorize tool '" + toolName + "' — once granted, all invocations of this tool will be allowed.");
        AuthorizationRequirementContent.AuthorizationRequirementItem item =
                new AuthorizationRequirementContent.AuthorizationRequirementItem();
        item.setResource(resource);
        item.setPermissions(List.of(Permission.ALL));
        content.setItems(List.of(item));
        return ToolPermissionResultFactory.requireDirectAuthorization(content);
    }

    /**
     * Check whether the policy grants TOOL-level ALL permission for the given resource path.
     */
    private static boolean policyGrantsTool(ToolPermissionPolicy policy, String resource) {
        if (policy == null) {
            return false;
        }
        // Check global permission levels (ALL globally → granted)
        if (policy.isGloballyPermitted(Permission.ALL)) {
            return true;
        }
        if (policy.getPermissions() == null) {
            return false;
        }
        for (ToolPermission granted : policy.getPermissions()) {
            if (resource.equals(granted.getResource())) {
                return granted.hasPermission(Permission.ALL);
            }
        }
        return false;
    }
}
