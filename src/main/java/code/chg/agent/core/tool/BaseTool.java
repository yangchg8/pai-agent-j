package code.chg.agent.core.tool;

import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.llm.ToolDefinition;

import java.lang.reflect.Method;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title BaseTool
 * @description Default concrete tool implementation built from a reflected method definition.
 */
public class BaseTool extends AbstractTool {
    private final PermissionChecker permissionChecker;

    public BaseTool(ToolDefinition toolDefinition, Object object, Method method, PermissionChecker permissionChecker) {
        super(toolDefinition, object, method);
        if (permissionChecker != null) {
            this.permissionChecker = permissionChecker;
        } else {
            this.permissionChecker = (_, _) -> ToolPermissionResultFactory.granted();
        }
    }

    @Override
    public ToolPermissionResult checkPermission(ToolPermissionPolicy policy, Object[] arguments) {
        return permissionChecker.check(policy, arguments);
    }
}
