package code.chg.agent.core.tool;

import code.chg.agent.llm.component.AuthorizationRequirementContent;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolPermissionResultFactory
 * @description Factory and base class for ToolPermissionResult instances.
 */
public abstract class ToolPermissionResultFactory implements ToolPermissionResult {

    // ── Static factory methods ──────────────────────────────────────────

    /**
     * Permission is already granted (session policy satisfies all requirements).
     */
    public static ToolPermissionResultFactory granted() {
        return new GrantedResult();
    }

    /**
     * Requires rule-based (direct) authorization with structured content.
     */
    public static ToolPermissionResultFactory requireDirectAuthorization(AuthorizationRequirementContent content) {
        return new RuleBasedResult(content);
    }

    /**
     * Requires LLM-based authorization with a prompt string.
     */
    public static ToolPermissionResultFactory requireLLMAuthorization(String prompt) {
        return new LLMBasedResult(prompt);
    }

    /**
     * Authorization is rejected with a reason.
     */
    public static ToolPermissionResultFactory rejected(String reason) {
        return new RejectedResult(reason);
    }

    /**
     * Convenience: rule-based authorization built from structured content.
     * Alias for {@link #requireDirectAuthorization(AuthorizationRequirementContent)}.
     */
    public static ToolPermissionResultFactory resourceAuthorization(AuthorizationRequirementContent content) {
        return requireDirectAuthorization(content);
    }

    /**
     * Convenience: single-use authorization with only a tips string.
     * Creates a minimal AuthorizationRequirementContent with singleUseOnly=true.
     */
    public static ToolPermissionResultFactory singleAuthorization(String tips) {
        AuthorizationRequirementContent content = new AuthorizationRequirementContent();
        content.setTips(tips);
        return new RuleBasedResult(content);
    }

    // ── Instance convenience methods ────────────────────────────────────

    /**
     * Returns true if permission is already granted (no authorization needed).
     */
    public boolean isGranted() {
        return hasPermission();
    }

    /**
     * Returns true if this result requires rule-based (direct) authorization.
     */
    public boolean isDirectMode() {
        return !hasPermission()
                && authorizationMode() != null
                && authorizationMode().getType() == ToolAuthModeType.RULE_BASED
                && ((ToolRuleBasedAuthorizationMode) authorizationMode()).authorizationContent() != null;
    }

    /**
     * Returns true if authorization was rejected.
     */
    public boolean isRejected() {
        return !hasPermission()
                && authorizationMode() != null
                && authorizationMode().getType() == ToolAuthModeType.REJECTED;
    }

    /**
     * Get the direct authorization content (only valid when isDirectMode() is true).
     */
    public AuthorizationRequirementContent getDirectContent() {
        if (!hasPermission()
                && authorizationMode() != null
                && authorizationMode().getType() == ToolAuthModeType.RULE_BASED) {
            return ((ToolRuleBasedAuthorizationMode) authorizationMode()).authorizationContent();
        }
        return null;
    }

    /**
     * Get the LLM prompt (only valid for LLM_BASED mode).
     */
    public String getPrompt() {
        if (!hasPermission()
                && authorizationMode() != null
                && authorizationMode().getType() == ToolAuthModeType.LLM_BASED) {
            return ((ToolLLMBasedAuthorizationMode) authorizationMode()).getPrompt();
        }
        return null;
    }

    /**
     * Get the rejection reason (only valid when isRejected() is true).
     */
    public String getRejectReason() {
        if (isRejected()) {
            return ((ToolRejectedAuthorizationMode) authorizationMode()).rejectReason();
        }
        return null;
    }

    // ── Inner result classes ────────────────────────────────────────────

    private static class GrantedResult extends ToolPermissionResultFactory {
        @Override
        public boolean hasPermission() {
            return true;
        }

        @Override
        public ToolAuthorizationMode authorizationMode() {
            return null;
        }
    }

    private static class RuleBasedResult extends ToolPermissionResultFactory {
        private final AuthorizationRequirementContent content;

        RuleBasedResult(AuthorizationRequirementContent content) {
            this.content = content;
        }

        @Override
        public boolean hasPermission() {
            return false;
        }

        @Override
        public ToolAuthorizationMode authorizationMode() {
            return new ToolRuleBasedAuthorizationMode(content);
        }
    }

    private static class LLMBasedResult extends ToolPermissionResultFactory {
        private final String prompt;

        LLMBasedResult(String prompt) {
            this.prompt = prompt;
        }

        @Override
        public boolean hasPermission() {
            return false;
        }

        @Override
        public ToolAuthorizationMode authorizationMode() {
            return new ToolLLMBasedAuthorizationMode() {
                @Override
                public String getPrompt() {
                    return prompt;
                }
            };
        }
    }

    private static class RejectedResult extends ToolPermissionResultFactory {
        private final String reason;

        RejectedResult(String reason) {
            this.reason = reason;
        }

        @Override
        public boolean hasPermission() {
            return false;
        }

        @Override
        public ToolAuthorizationMode authorizationMode() {
            return new ToolRejectedAuthorizationMode() {
                @Override
                public String rejectReason() {
                    return reason;
                }
            };
        }
    }
}
