package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.lib.tool.shell.safety.ResourcePermission;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellCommandPermissionDecision
 * @description Provides the ShellCommandPermissionDecision implementation.
 */
public final class ShellCommandPermissionDecision {

    public enum Status {
        ANALYZED,
        UNANALYZABLE
    }

    private final Status status;
    private final List<ResourcePermission> resourcePermissions;
    private final String reason;

    private ShellCommandPermissionDecision(Status status,
                                           List<ResourcePermission> resourcePermissions,
                                           String reason) {
        this.status = status;
        this.resourcePermissions = resourcePermissions == null ? List.of() : List.copyOf(resourcePermissions);
        this.reason = reason;
    }

    public static ShellCommandPermissionDecision analyzed(List<ResourcePermission> resourcePermissions) {
        return new ShellCommandPermissionDecision(Status.ANALYZED, resourcePermissions, null);
    }

    public static ShellCommandPermissionDecision unanalyzable(String reason) {
        return new ShellCommandPermissionDecision(Status.UNANALYZABLE, List.of(), reason);
    }

    public Status getStatus() {
        return status;
    }

    public List<ResourcePermission> getResourcePermissions() {
        return resourcePermissions;
    }

    public String getReason() {
        return reason;
    }
}