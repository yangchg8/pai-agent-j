package code.chg.agent.lib.tool.shell;

import code.chg.agent.annotation.Tool;
import code.chg.agent.annotation.ToolParameter;
import code.chg.agent.annotation.ToolPermissionChecker;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.lib.tool.shell.permission.ShellCommandPermissionRegistry;
import code.chg.agent.lib.tool.shell.permission.ShellPermissionCheckDispatcher;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellTool
 * @description Stateful shell tool whose working directory is fixed at construction time.
 */
public class ShellTool {

    private static final String SHELL_DESCRIPTION = """
            Runs a shell command on Unix and returns its output.
            - Pass the command string in the `command` parameter. This can be a simple one-liner \
            (e.g. "ls -la"), a script file execution (e.g. "bash /path/to/script.sh"), \
            or a multi-line generated shell script.
            - Multi-line scripts are supported directly: just include newlines in the command string.
            - Do not use `cd` unless absolutely necessary.
            - When searching for text or files, prefer `rg` or `rg --files` because rg is much faster than grep.
            - Do not use python scripts to attempt to output larger chunks of a file.""";

    private final String workDir;
    private final ShellPermissionCheckDispatcher permissionDispatcher;

    /** Construct with a working directory and no pre-allowed commands. */
    public ShellTool(String workDir) {
        this(workDir, Collections.emptySet(), ShellCommandPermissionRegistry.createDefault());
    }

    public ShellTool(String workDir, ShellCommandPermissionRegistry permissionRegistry) {
        this(workDir, Collections.emptySet(), permissionRegistry);
    }

    /**
     * Construct with a working directory and a set of pre-approved command names.
     * Commands in {@code defaultAllowedCommands} bypass the authorization flow entirely
     * (equivalent to the user having granted {@code COMMAND:{name} ALL} upfront).
     *
     * @param workDir              the shell's working directory
     * @param defaultAllowedCommands command base-names that are always allowed (e.g. "git", "mvn")
     */
    public ShellTool(String workDir, Set<String> defaultAllowedCommands) {
        this(workDir, defaultAllowedCommands, ShellCommandPermissionRegistry.createDefault());
    }

    public ShellTool(String workDir,
                     Set<String> defaultAllowedCommands,
                     ShellCommandPermissionRegistry permissionRegistry) {
        this.workDir = workDir;
        this.permissionDispatcher = new ShellPermissionCheckDispatcher(permissionRegistry, defaultAllowedCommands);
    }

    @Tool(name = "shell", description = SHELL_DESCRIPTION)
    public ShellToolResult shell(
            @ToolParameter(name = "command", description = """
                    The shell command or script to execute. Can be a simple one-liner \
                    (e.g. "ls -la"), a script file execution (e.g. "bash /path/to/script.sh"), \
                    or a multi-line generated shell script with newlines.""")
            String command,
            @ToolParameter(name = "timeout_ms", description = """
                    The timeout for the command in milliseconds. \
                    Defaults to 30000ms if not specified.""")
            Long timeoutMs,
            @ToolParameter(name = "max_output_chars", description = """
                    Maximum number of characters to return. \
                    Excess output will be truncated. Defaults to 100000.""")
            Integer maxOutputChars
    ) {
        if (command == null || command.isBlank()) {
            return new ShellToolResult(0, -1, "Error: command must be provided");
        }
        List<String> shellCmd = ShellCommandExecutor.buildShellCommand(command);
        return ShellCommandExecutor.executeProcess(shellCmd, workDir, timeoutMs, maxOutputChars);
    }

    @ToolPermissionChecker(toolName = "shell")
    public ToolPermissionResult shellPermissionCheck(ToolPermissionPolicy policy, Object[] arguments) {
        String command = arguments.length > 0 && arguments[0] instanceof String s ? s : null;
        return permissionDispatcher.check(policy, command, workDir);
    }
}
