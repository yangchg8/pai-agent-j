package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.lib.auth.FileLevelPermissionChecker;
import code.chg.agent.lib.tool.shell.safety.ResourcePermission;
import code.chg.agent.llm.component.AuthorizationRequirementContent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellPermissionPolicyMatcher
 * @description Shared utilities for matching resource permissions against session policies for the Shell tool.
 */
public final class ShellPermissionPolicyMatcher {

    private ShellPermissionPolicyMatcher() {
    }

    /**
     * Check if the existing policy grants all required permissions
     * for every resource in the analysis result.
     * <p>
     * Checks in order:
     * <ol>
     *   <li>Global permission levels (e.g. READ globally approved → all read-only resources pass).</li>
     *   <li>Session / global resource permissions via glob-aware matching.</li>
     * </ol>
     */
    public static boolean policyGrantsAllPermissions(ToolPermissionPolicy policy,
                                                     List<ResourcePermission> resourcePermissions) {
        if (resourcePermissions == null || resourcePermissions.isEmpty()) {
            return false;
        }
        for (ResourcePermission required : resourcePermissions) {
            if (!isResourceGranted(policy, required)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a single resource permission is fully granted by the policy,
     * considering both global permission levels and session/global resource permissions.
     */
    public static boolean isResourceGranted(ToolPermissionPolicy policy, ResourcePermission required) {
        if (policy == null) {
            return false;
        }
        // 1. Check global permission levels
        if (policy.globalCoversAll(required.getPermissions())) {
            return true;
        }
        // 2. Check session + global resource permissions
        if (policy.getPermissions() == null) {
            return false;
        }
        for (ToolPermission granted : policy.getPermissions()) {
            if (matchesResource(granted.getResource(), required.getAbsolutePath())) {
                if (granted.hasPermission(Permission.ALL)) {
                    return true;
                }
                boolean allGranted = true;
                for (Permission perm : required.getPermissions()) {
                    if (perm == Permission.ALL) {
                        if (!granted.hasPermission(Permission.ALL)) {
                            allGranted = false;
                            break;
                        }
                    } else if (!granted.hasPermission(perm)) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collect resource permissions that are NOT already granted by the policy.
     */
    public static List<ResourcePermission> collectMissingPermissions(ToolPermissionPolicy policy,
                                                                     List<ResourcePermission> required) {
        List<ResourcePermission> missing = new ArrayList<>();
        for (ResourcePermission rp : required) {
            if (!isResourceGranted(policy, rp)) {
                missing.add(rp);
            }
        }
        return missing;
    }

    /**
     * Build authorization requirement items from resource permissions.
     */
    public static List<AuthorizationRequirementContent.AuthorizationRequirementItem> buildAuthItems(
            List<ResourcePermission> resourcePermissions) {
        List<AuthorizationRequirementContent.AuthorizationRequirementItem> items = new ArrayList<>();
        for (ResourcePermission rp : resourcePermissions) {
            AuthorizationRequirementContent.AuthorizationRequirementItem item =
                    new AuthorizationRequirementContent.AuthorizationRequirementItem();
            item.setResource(rp.getAbsolutePath());
            item.setPermissions(new ArrayList<>(rp.getPermissions()));
            items.add(item);
        }
        return items;
    }

    /**
     * Match a granted resource pattern against a required resource path.
     * Delegates to {@link FileLevelPermissionChecker#matchesResource} which handles
     * cross-type matching (DIR: policy ↔ FILE: requirement) and glob wildcards.
     * COMMAND: and TOOL: resources use exact match only.
     */
    public static boolean matchesResource(String grantedPattern, String requiredPath) {
        return FileLevelPermissionChecker.matchesResource(grantedPattern, requiredPath);
    }
}
