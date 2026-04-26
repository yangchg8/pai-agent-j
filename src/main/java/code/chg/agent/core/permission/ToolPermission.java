package code.chg.agent.core.permission;

import lombok.Getter;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolPermission
 * @description Represents the permission granted to a specific tool on a specific resource path.
 */
@Getter
public class ToolPermission {

    private final String resource;

    private final Set<Permission> grantedPermissions;

    public ToolPermission(String resource, Set<Permission> grantedPermissions) {
        this.resource = resource;
        this.grantedPermissions = grantedPermissions != null
                ? EnumSet.copyOf(grantedPermissions)
                : EnumSet.noneOf(Permission.class);
    }

    public void addPermission(Collection<Permission> permissions) {
        if (permissions == null) {
            return;
        }
        grantedPermissions.addAll(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return grantedPermissions.contains(permission);
    }
}
