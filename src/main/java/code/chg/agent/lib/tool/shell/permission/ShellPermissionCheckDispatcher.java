package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.lib.tool.shell.safety.ShellAnalysisResult;
import code.chg.agent.lib.tool.shell.safety.ShellStaticAnalyzer;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellPermissionCheckDispatcher
 * @description Orchestrates shell permission checking by dispatching to registered {@link ShellPermissionHandler} strategies.
 */
public final class ShellPermissionCheckDispatcher {

    private static final List<ShellPermissionHandler> DEFAULT_HANDLERS = List.of(
            new ScriptExecutionHandler(),
            new AnalyzedCommandHandler(),
            new UnknownCommandHandler(),
            new FallbackHandler()
    );

    private final List<ShellPermissionHandler> handlers;
    private final ShellCommandPermissionRegistry permissionRegistry;

    /** Commands that are pre-approved without any user authorization (e.g. "git", "mvn"). */
    private final Set<String> defaultAllowedCommands;

    public ShellPermissionCheckDispatcher() {
        this(ShellCommandPermissionRegistry.createDefault(), Collections.emptySet());
    }

    public ShellPermissionCheckDispatcher(Set<String> defaultAllowedCommands) {
        this(ShellCommandPermissionRegistry.createDefault(), defaultAllowedCommands);
    }

    public ShellPermissionCheckDispatcher(ShellCommandPermissionRegistry permissionRegistry) {
        this(permissionRegistry, Collections.emptySet());
    }

    public ShellPermissionCheckDispatcher(ShellCommandPermissionRegistry permissionRegistry,
                                          Set<String> defaultAllowedCommands) {
        this.handlers = new java.util.ArrayList<>(DEFAULT_HANDLERS);
        this.permissionRegistry = permissionRegistry != null
                ? permissionRegistry
                : ShellCommandPermissionRegistry.createDefault();
        this.defaultAllowedCommands = defaultAllowedCommands != null ? defaultAllowedCommands : Collections.emptySet();
    }

    /**
     * Perform the permission check for a shell command.
     *
     * @param policy  the current session's permission policy (may be null)
     * @param command the shell command string
     * @param workdir the working directory
     * @return the permission check result
     */
    public ToolPermissionResult  check(ToolPermissionPolicy policy, String command, String workdir) {
        if (command == null || command.isBlank()) {
            throw new RuntimeException("check permission error, command is null or blank");
        }

        ShellAnalysisResult analysis = ShellStaticAnalyzer.analyze(command, workdir, permissionRegistry);

        // If this is a NOT_IN_WHITELIST for a single default-allowed command, auto-grant
        if (analysis.getStatus() == ShellAnalysisResult.Status.NOT_IN_WHITELIST
                && analysis.getCommandCount() == 1
                && isDefaultAllowedCommand(analysis.getUnrecognizedCommand())) {
            return ToolPermissionResultFactory.granted();
        }

        // Inject default-allowed-command permissions into policy before dispatching
        ToolPermissionPolicy effectivePolicy = injectDefaultAllowedPermissions(policy, command);

        for (ShellPermissionHandler handler : handlers) {
            if (handler.supports(analysis.getStatus())) {
                return handler.handle(effectivePolicy, analysis, command);
            }
        }

        // No handler matched — should not happen with default handlers, but be safe
        return ToolPermissionResultFactory.singleAuthorization(
                "Execute shell command: " + command + "\nSingle-use authorization required.");
    }

    /**
     * Returns true if the command (stripped of paths, just the base name) is in the default-allowed list.
     */
    private boolean isDefaultAllowedCommand(String commandName) {
        if (commandName == null || defaultAllowedCommands.isEmpty()) {
            return false;
        }
        // commandName may include the full invocation name; check just the base name
        String base = commandName.contains("/")
                ? commandName.substring(commandName.lastIndexOf('/') + 1)
                : commandName;
        return defaultAllowedCommands.contains(base);
    }

    /**
     * Create an effective policy that includes COMMAND:{name} ALL entries for each default-allowed command
     * that appears in the given command string.  This allows the downstream handlers to see these as
     * already-granted without mutating the caller's policy object.
     */
    private ToolPermissionPolicy injectDefaultAllowedPermissions(ToolPermissionPolicy policy, String command) {
        if (defaultAllowedCommands.isEmpty()) {
            return policy;
        }
        // Build a synthetic policy that adds COMMAND:{cmd} ALL for each default-allowed command
        ToolPermissionPolicy synthetic = policy != null
                ? new ToolPermissionPolicy(policy.getToolName(), policy.getPermissions(), policy.getGlobalPermissionLevels())
                : new ToolPermissionPolicy("shell");
        for (String cmd : defaultAllowedCommands) {
            synthetic.addPermission("COMMAND:" + cmd, List.of(Permission.ALL));
        }
        return synthetic;
    }
}
