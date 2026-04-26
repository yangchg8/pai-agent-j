package code.chg.agent.lib.tool.shell.permission;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellCommandPermissionContext
 * @description Defines the ShellCommandPermissionContext record.
 */
public record ShellCommandPermissionContext(String executableName,
                                            List<String> commandWords,
                                            String workDir) {

    public ShellCommandPermissionContext {
        commandWords = commandWords == null ? List.of() : List.copyOf(commandWords);
    }
}