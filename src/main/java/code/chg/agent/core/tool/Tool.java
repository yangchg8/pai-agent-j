package code.chg.agent.core.tool;

import code.chg.agent.core.event.Subscription;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.llm.ToolDefinition;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title Tool
 * @description Tool definition interface.
 */
public interface Tool extends Subscription, ToolDefinition {
    ToolPermissionResult checkPermission(ToolPermissionPolicy policy, Object[] arguments);
}
