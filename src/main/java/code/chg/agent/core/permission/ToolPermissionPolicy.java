package code.chg.agent.core.permission;

import lombok.Getter;

import java.util.*;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolPermissionPolicy
 * @description Permission policy for a tool, containing both session-authorized permissions and
 */
@Getter
public class ToolPermissionPolicy {

    private final String toolName;

    /**
     * Session-authorized resource permissions (accumulated from user grant actions).
     */
    private final List<ToolPermission> permissions;

    /**
     * Globally pre-authorized permission levels. Any resource that only requires permissions
     * within this set is automatically granted without explicit user authorization.
     * Example: if {READ} is in this set, all read-only tool invocations pass without asking.
     */
    private final Set<Permission> globalPermissionLevels;

    public ToolPermissionPolicy(String toolName, List<ToolPermission> permissions) {
        this.toolName = toolName;
        this.permissions = new ArrayList<>(permissions);
        this.globalPermissionLevels = EnumSet.noneOf(Permission.class);
    }

    public ToolPermissionPolicy(String toolName, List<ToolPermission> permissions, Set<Permission> globalPermissionLevels) {
        this.toolName = toolName;
        this.permissions = new ArrayList<>(permissions);
        this.globalPermissionLevels = globalPermissionLevels != null
                ? EnumSet.copyOf(globalPermissionLevels)
                : EnumSet.noneOf(Permission.class);
    }

    public ToolPermissionPolicy(String toolName) {
        this.toolName = toolName;
        this.permissions = new ArrayList<>();
        this.globalPermissionLevels = EnumSet.noneOf(Permission.class);
    }

    /**
     * Returns true if the given permission is globally pre-authorized.
     */
    public boolean isGloballyPermitted(Permission permission) {
        return globalPermissionLevels.contains(Permission.ALL) || globalPermissionLevels.contains(permission);
    }

    /**
     * Returns true if all required permissions are covered by the global permission levels.
     */
    public boolean globalCoversAll(Collection<Permission> required) {
        if (globalPermissionLevels.contains(Permission.ALL)) {
            return true;
        }
        for (Permission p : required) {
            if (!globalPermissionLevels.contains(p)) {
                return false;
            }
        }
        return true;
    }

    public void addPermission(String resources, List<Permission> permissions) {
        if (resources == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        ToolPermission matchPermission = this.permissions.stream()
                .filter(item -> resources.equals(item.getResource()))
                .findFirst()
                .orElse(null);
        if (matchPermission == null) {
            ToolPermission newPermission = new ToolPermission(resources, new HashSet<>(permissions));
            this.permissions.add(newPermission);
            return;
        }
        matchPermission.addPermission(permissions);
    }
}
