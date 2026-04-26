package code.chg.agent.core.tool;

import code.chg.agent.core.permission.ToolPermissionPolicy;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PermissionChecker
 * @description Defines a permission checker for tool execution.
 */
public interface PermissionChecker {
    /**
     * Evaluates whether the provided arguments are permitted under the policy.
     */
    ToolPermissionResult check(ToolPermissionPolicy policy, Object[] arguments);
}
