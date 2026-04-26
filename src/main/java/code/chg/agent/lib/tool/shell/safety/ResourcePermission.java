package code.chg.agent.lib.tool.shell.safety;

import code.chg.agent.core.permission.Permission;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ResourcePermission
 * @description Represents a resource (absolute file path) and the permissions required to access it,
 */
@Getter
public class ResourcePermission {

    private final String absolutePath;
    private final Set<Permission> permissions;

    public ResourcePermission(String absolutePath, Set<Permission> permissions) {
        this.absolutePath = absolutePath;
        this.permissions = permissions != null ? EnumSet.copyOf(permissions) : EnumSet.noneOf(Permission.class);
    }

    public ResourcePermission(String absolutePath, Permission permission) {
        this.absolutePath = absolutePath;
        this.permissions = EnumSet.of(permission);
    }
}
