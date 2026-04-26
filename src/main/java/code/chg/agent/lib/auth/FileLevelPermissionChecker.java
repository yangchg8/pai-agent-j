package code.chg.agent.lib.auth;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.llm.component.AuthorizationRequirementContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * File/directory-level permission checker (reusable across tools).
 * <p>
 * Resource format:
 * <ul>
 *   <li>{@code FILE:/absolute/path/file.txt} — a concrete file path</li>
 *   <li>{@code DIR:/absolute/path/dir} — a directory, exact match</li>
 *   <li>{@code DIR:/absolute/path/dir/*} — directory and its immediate children</li>
 *   <li>{@code DIR:/absolute/path/dir/**} — directory and all recursive descendants</li>
 * </ul>
 * <p>
 * Permission levels: NONE, READ, WRITE, EXECUTE, ALL.
 * <p>
 *
 * @author yangchg <yangchg314@gmail.com>
 * @title FileLevelPermissionChecker
 * @description Reusable permission matcher for file and directory resources.
 */
public final class FileLevelPermissionChecker {

    private FileLevelPermissionChecker() {
    }

    /**
     * Check whether the policy grants all {@code required} permissions for the given {@code resource}.
     *
     * @param policy   the permission policy (session + global)
     * @param resource the resource string, e.g. {@code "FILE:/tmp/foo.txt"} or {@code "DIR:/workdir/**"}
     * @param required the permissions required to access this resource
     * @return granted result, or a rule-based authorization request for the missing permissions
     */
    public static ToolPermissionResult checkPermission(ToolPermissionPolicy policy,
                                                       String resource,
                                                       List<Permission> required) {
        if (required == null || required.isEmpty()) {
            return ToolPermissionResultFactory.granted();
        }

        // 1. Check global permission levels
        if (policy != null && policy.globalCoversAll(required)) {
            return ToolPermissionResultFactory.granted();
        }

        // 2. Check session + global resource permissions
        Set<Permission> missingPerms = collectMissingPermissions(policy, resource, required);
        if (missingPerms.isEmpty()) {
            return ToolPermissionResultFactory.granted();
        }

        // 3. Return authorization request for missing
        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips("Access required for: " + resource);
        AuthorizationRequirementContent.AuthorizationRequirementItem item =
                new AuthorizationRequirementContent.AuthorizationRequirementItem();
        item.setResource(resource);
        item.setPermissions(new ArrayList<>(missingPerms));
        content.setItems(List.of(item));
        return ToolPermissionResultFactory.requireDirectAuthorization(content);
    }

    /**
     * Batch-check multiple resource requirements at once.
     * Returns granted only when ALL requirements are satisfied; otherwise returns
     * a single authorization request listing all missing (resource, permissions) pairs.
     *
     * @param policy       the permission policy
     * @param requirements list of (resource, required permissions) pairs
     * @return granted or a combined authorization request
     */
    public static ToolPermissionResult checkPermissions(ToolPermissionPolicy policy,
                                                        List<ResourceRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return ToolPermissionResultFactory.granted();
        }

        List<AuthorizationRequirementContent.AuthorizationRequirementItem> missingItems = new ArrayList<>();

        for (ResourceRequirement req : requirements) {
            // 1. Global levels cover all?
            if (policy != null && policy.globalCoversAll(req.permissions())) {
                continue;
            }
            // 2. Session / global resource permissions?
            Set<Permission> missing = collectMissingPermissions(policy, req.resource(), req.permissions());
            if (!missing.isEmpty()) {
                AuthorizationRequirementContent.AuthorizationRequirementItem item =
                        new AuthorizationRequirementContent.AuthorizationRequirementItem();
                item.setResource(req.resource());
                item.setPermissions(new ArrayList<>(missing));
                missingItems.add(item);
            }
        }

        if (missingItems.isEmpty()) {
            return ToolPermissionResultFactory.granted();
        }

        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips("Authorization required for " + missingItems.size() + " resource(s)");
        content.setItems(missingItems);
        return ToolPermissionResultFactory.requireDirectAuthorization(content);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Collect the subset of {@code required} permissions not already granted by the policy for this resource.
     */
    private static Set<Permission> collectMissingPermissions(ToolPermissionPolicy policy,
                                                             String resource,
                                                             Collection<Permission> required) {
        Set<Permission> missing = EnumSet.noneOf(Permission.class);
        if (required == null) {
            return missing;
        }
        for (Permission perm : required) {
            if (!isGranted(policy, resource, perm)) {
                missing.add(perm);
            }
        }
        return missing;
    }

    /**
     * Return true if the policy grants {@code perm} for {@code resource}.
     */
    private static boolean isGranted(ToolPermissionPolicy policy, String resource, Permission perm) {
        if (policy == null || policy.getPermissions() == null) {
            return false;
        }
        for (ToolPermission granted : policy.getPermissions()) {
            if (matchesResource(granted.getResource(), resource)) {
                if (granted.hasPermission(Permission.ALL) || granted.hasPermission(perm)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determine whether {@code grantedPattern} (from the policy) covers {@code requiredPath}.
     * <p>
     * Matching is type-aware but cross-type capable:
     * <ul>
     *   <li>Exact equality always matches.</li>
     *   <li>{@code DIR:/x/**} covers any FILE: or DIR: path under {@code /x/}.</li>
     *   <li>{@code DIR:/x/*} covers any FILE: or DIR: path that is an immediate child of {@code /x/}.</li>
     *   <li>Path extraction strips the type prefix ({@code FILE:}, {@code DIR:}) before comparison.</li>
     *   <li>{@code FILE:/x} only matches {@code FILE:/x} exactly.</li>
     *   <li>{@code COMMAND:} and {@code TOOL:} resources use exact match only.</li>
     * </ul>
     */
    public static boolean matchesResource(String grantedPattern, String requiredPath) {
        if (grantedPattern == null || requiredPath == null) {
            return false;
        }
        if (grantedPattern.equals(requiredPath)) {
            return true;
        }

        // Non-file/dir types: exact match only
        if (grantedPattern.startsWith("COMMAND:") || grantedPattern.startsWith("TOOL:")) {
            return false;
        }

        // Both must be FILE: or DIR: for glob matching
        if (!grantedPattern.startsWith("FILE:") && !grantedPattern.startsWith("DIR:")) {
            return false;
        }

        // Strip type prefix from pattern and required path for path comparison
        String patternPath = stripTypePrefix(grantedPattern);
        String requiredRawPath = stripTypePrefix(requiredPath);

        if (patternPath == null || requiredRawPath == null) {
            return false;
        }

        // Recursive wildcard: DIR:/dir/** matches any path under /dir/
        if (patternPath.endsWith("/**")) {
            String base = patternPath.substring(0, patternPath.length() - 2); // "/dir/"
            return requiredRawPath.startsWith(base);
        }

        // Single-level wildcard: DIR:/dir/* matches immediate children of /dir/
        if (patternPath.endsWith("/*")) {
            String base = patternPath.substring(0, patternPath.length() - 1); // "/dir/"
            if (!requiredRawPath.startsWith(base)) {
                return false;
            }
            String remaining = requiredRawPath.substring(base.length());
            return !remaining.isEmpty() && !remaining.contains("/");
        }

        return false;
    }

    /**
     * Strip the type prefix ({@code FILE:}, {@code DIR:}) and return the raw path,
     * or {@code null} if no recognized prefix is found.
     */
    private static String stripTypePrefix(String resource) {
        if (resource.startsWith("FILE:")) {
            return resource.substring("FILE:".length());
        }
        if (resource.startsWith("DIR:")) {
            return resource.substring("DIR:".length());
        }
        return null;
    }

    // ── Value type ───────────────────────────────────────────────────────

    /**
     * A (resource, required-permissions) pair used for batch checks.
     */
    public record ResourceRequirement(String resource, List<Permission> permissions) {
        public static ResourceRequirement of(String resource, Permission... perms) {
            return new ResourceRequirement(resource, List.of(perms));
        }
    }
}
