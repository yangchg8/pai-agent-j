package code.chg.agent.lib.tool.shell.permission;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellCommandPermissionHook
 * @description Defines the ShellCommandPermissionHook contract.
 */
public interface ShellCommandPermissionHook {

    ShellCommandPermissionDecision analyze(ShellCommandPermissionContext context,
                                           ShellCommandPermissionRegistry registry);
}