package code.chg.agent.core.brain;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.llm.ToolCall;
import code.chg.agent.llm.component.AuthorizationRequirementContent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.*;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title BrainRunningState
 * @description Tracks running tools, authorization state, and inherited permissions for a brain.
 */
@Getter
public class BrainRunningState {
    List<RunningTool> runningTools;
    Map<String, ToolPermissionPolicy> toolPermissions;

    /**
     * Global resource permissions pre-authorized at agent construction (e.g. DIR:/workdir/** ALL).
     */
    private List<ToolPermission> globalResourcePermissions = new ArrayList<>();

    /**
     * Global permission levels pre-authorized at agent construction (e.g. {READ}).
     */
    private Set<Permission> globalPermissionLevels = EnumSet.noneOf(Permission.class);

    /**
     * Configure global permissions. Call this once at agent startup.
     *
     * @param resourcePermissions pre-authorized resource permissions injected into every tool policy
     * @param permissionLevels    globally auto-approved permission levels
     */
    public void setGlobalConfig(List<ToolPermission> resourcePermissions, Set<Permission> permissionLevels) {
        this.globalResourcePermissions = resourcePermissions != null ? new ArrayList<>(resourcePermissions) : new ArrayList<>();
        this.globalPermissionLevels = permissionLevels != null && !permissionLevels.isEmpty()
                ? EnumSet.copyOf(permissionLevels)
                : EnumSet.noneOf(Permission.class);
    }

    public ToolPermissionPolicy getToolPermission(String name) {
        List<ToolPermission> merged = new ArrayList<>(globalResourcePermissions);
        if (toolPermissions != null && toolPermissions.containsKey(name)) {
            merged.addAll(toolPermissions.get(name).getPermissions());
        }
        return new ToolPermissionPolicy(name, merged, globalPermissionLevels);
    }

    public boolean isFinishedTool(String id) {
        if (runningTools == null || runningTools.isEmpty() || id == null) {
            return false;
        }
        return runningTools.stream().anyMatch(r -> r.id().equals(id) && r.isFinished());
    }

    public boolean isAuthorizingStatus(String authorizationId) {
        if (runningTools == null || runningTools.isEmpty() || authorizationId == null) {
            return false;
        }
        return runningTools.stream().anyMatch(r -> r.isAuthorizing() && r.authorizationId().equals(authorizationId));
    }

    public void authorizeTool(String toolName, AuthorizationRequirementContent authorizationRequirementContent) {
        if (toolPermissions == null) {
            toolPermissions = new java.util.HashMap<>();
        }
        ToolPermissionPolicy toolPermissionPolicy = new ToolPermissionPolicy(toolName);
        List<AuthorizationRequirementContent.AuthorizationRequirementItem> items = authorizationRequirementContent.getItems();
        if (items != null) {
            for (AuthorizationRequirementContent.AuthorizationRequirementItem item : items) {
                toolPermissionPolicy.addPermission(item.getResource(), item.getPermissions());
            }
        }
        toolPermissions.put(toolName, toolPermissionPolicy);
    }

    public RunningTool queryAuthorizingTool(String authorizationId) {
        return runningTools.stream()
                .filter(r -> r.isAuthorizing() &&
                        r.authorizationId().equals(authorizationId)).findFirst()
                .orElse(null);

    }

    public boolean nonRunningTool() {
        if (runningTools == null || runningTools.isEmpty()) {
            return true;
        }
        return runningTools.stream().allMatch(RunningTool::isFinished);
    }

    public void overrideRunningTools(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            this.runningTools = Collections.emptyList();
            return;
        }
        this.runningTools = toolCalls.stream().map(RunningTool::ofRequest).toList();
    }

    public synchronized boolean markFinishAndContinue(String id) {
        boolean existRunningTool = false;
        if (runningTools == null || runningTools.isEmpty() || id == null) {
            return true;
        }
        for (RunningTool runningTool : runningTools) {
            if (runningTool.id().equals(id)) {
                runningTool.markCompleted();
            }
            if (!runningTool.isFinished()) {
                existRunningTool = true;
            }
        }
        return !existRunningTool;
    }

    public RunningTool getRunningTool(String id) {
        if (id == null || runningTools == null) {
            return null;
        }
        for (RunningTool runningTool : runningTools) {
            if (runningTool.id().equals(id)) {
                return runningTool;
            }
        }
        return null;
    }

    @Data
    public static class RunningTool {
        private RunningToolStatus status;
        private ToolCall toolCall;
        private AuthorizationStatus authorizationStatus;


        public static RunningTool ofRequest(ToolCall toolCall) {
            RunningTool runningTool = new RunningTool();
            runningTool.setToolCall(toolCall);
            runningTool.setStatus(RunningToolStatus.REQUEST);
            return runningTool;
        }

        public String name() {
            return toolCall.name();
        }

        public String id() {
            return toolCall.id();
        }

        public void markCancelled() {
            this.status = RunningToolStatus.CANCELLED;
            this.authorizationStatus = null;
        }

        public void markRejected() {
            this.status = RunningToolStatus.REJECTED;
            this.authorizationStatus = null;
        }

        public void markCompleted() {
            this.status = RunningToolStatus.COMPLETED;
            this.authorizationStatus = null;
        }

        public void markRequest() {
            this.status = RunningToolStatus.REQUEST;
            this.authorizationStatus = null;
        }

        public void markAuthorizing(String permissionId, AuthorizationRequirementContent content) {
            this.status = RunningToolStatus.AUTHORIZING;
            this.authorizationStatus = new AuthorizationStatus(permissionId, content);
        }

        public String authorizationId() {
            if (authorizationStatus == null) {
                return null;
            }
            return authorizationStatus.getId();
        }

        public boolean isAuthorizing() {
            return status == RunningToolStatus.AUTHORIZING;
        }

        public boolean isFinished() {
            return status == RunningToolStatus.CANCELLED ||
                    status == RunningToolStatus.COMPLETED ||
                    status == RunningToolStatus.REJECTED;
        }

        public void markContinue() {
            this.status = RunningToolStatus.CONTINUE;
            this.authorizationStatus = null;
        }

        public boolean isContinue() {
            return status == RunningToolStatus.CONTINUE;
        }
    }

    public enum RunningToolStatus {
        REQUEST,
        AUTHORIZING,
        COMPLETED,
        REJECTED,
        CANCELLED,
        /**
         * Tool was authorized for one-time execution; skip permission check on retry.
         */
        CONTINUE
    }

    @AllArgsConstructor
    @Data
    public static class AuthorizationStatus {
        String id;
        AuthorizationRequirementContent content;
    }
}
