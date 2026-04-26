package code.chg.agent.lib.tool.shell.safety;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.lib.tool.shell.permission.ShellCommandPermissionContext;
import code.chg.agent.lib.tool.shell.permission.ShellCommandPermissionDecision;
import code.chg.agent.lib.tool.shell.permission.ShellCommandPermissionHook;
import code.chg.agent.lib.tool.shell.permission.ShellCommandPermissionRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellStaticAnalyzer
 * @description Static analyzer for shell commands using a conservative literal parser.
 */
public final class ShellStaticAnalyzer {

    private static final ShellCommandPermissionRegistry DEFAULT_PERMISSION_REGISTRY =
            ShellCommandPermissionRegistry.createDefault();

    private static final Set<String> SCRIPT_INTERPRETERS = Set.of(
            "bash", "sh", "zsh", "dash", "ksh", "fish",
            "python", "python3", "python2",
            "node", "npx", "deno", "bun", "ts-node",
            "java", "kotlin", "groovy", "scala",
            "ruby", "perl", "php", "lua", "Rscript"
    );

    private ShellStaticAnalyzer() {
    }

    public static ShellAnalysisResult analyze(String command, String workdir) {
        return analyze(command, workdir, DEFAULT_PERMISSION_REGISTRY);
    }

    public static ShellAnalysisResult analyze(String command,
                                              String workdir,
                                              ShellCommandPermissionRegistry permissionRegistry) {
        if (command == null || command.isBlank()) {
            return ShellAnalysisResult.unparseable(command);
        }

        ShellCommandParser.ParsedShell shell = ShellCommandParser.tryParseShell(command);
        if (shell == null) {
            return ShellAnalysisResult.unparseable(command);
        }

        List<List<String>> commandSequences = ShellCommandParser.tryParseWordOnlyCommandsSequence(shell, command);
        if (commandSequences == null || commandSequences.isEmpty()) {
            return ShellAnalysisResult.unparseable(command);
        }

        List<ResourcePermission> scriptPermissions = tryAnalyzeAsScriptExecution(commandSequences, workdir);
        if (scriptPermissions != null) {
            return ShellAnalysisResult.scriptExecution(scriptPermissions, command, commandSequences);
        }

        ShellCommandPermissionRegistry effectiveRegistry =
                permissionRegistry != null ? permissionRegistry : DEFAULT_PERMISSION_REGISTRY;
        List<ResourcePermission> allPermissions = new ArrayList<>();

        for (List<String> commandWords : commandSequences) {
            if (commandWords.isEmpty()) {
                continue;
            }

            String executableName = CommandUtils.executableNameLookupKey(commandWords.getFirst());
            if (executableName == null) {
                return ShellAnalysisResult.unparseable(command, commandSequences);
            }

            Optional<ShellCommandPermissionHook> hookOpt = effectiveRegistry.findHook(executableName);
            if (hookOpt.isEmpty()) {
                return ShellAnalysisResult.notInWhitelist(executableName, command, commandSequences.size(), commandSequences);
            }

            ShellCommandPermissionDecision decision = hookOpt.get().analyze(
                    new ShellCommandPermissionContext(executableName, commandWords, workdir),
                    effectiveRegistry);
            if (decision == null || decision.getStatus() != ShellCommandPermissionDecision.Status.ANALYZED) {
                return ShellAnalysisResult.unparseable(command, commandSequences);
            }

            allPermissions.addAll(decision.getResourcePermissions());
        }

        return ShellAnalysisResult.analyzed(mergeResourcePermissions(allPermissions), command, commandSequences);
    }

    private static List<ResourcePermission> mergeResourcePermissions(List<ResourcePermission> permissions) {
        if (permissions.size() <= 1) {
            return permissions;
        }

        Map<String, Set<Permission>> merged = new LinkedHashMap<>();
        for (ResourcePermission permission : permissions) {
            merged.computeIfAbsent(permission.getAbsolutePath(), key -> EnumSet.noneOf(Permission.class))
                    .addAll(permission.getPermissions());
        }

        List<ResourcePermission> result = new ArrayList<>(merged.size());
        for (Map.Entry<String, Set<Permission>> entry : merged.entrySet()) {
            result.add(new ResourcePermission(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static List<ResourcePermission> tryAnalyzeAsScriptExecution(List<List<String>> commandSequences,
                                                                        String workdir) {
        if (commandSequences.size() != 1) {
            return null;
        }

        List<String> commandWords = commandSequences.getFirst();
        if (commandWords.size() < 2) {
            return null;
        }

        String executableName = CommandUtils.executableNameLookupKey(commandWords.getFirst());
        if (executableName == null || !SCRIPT_INTERPRETERS.contains(executableName)) {
            return null;
        }

        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if ("-c".equals(argument) || "-e".equals(argument) || "--eval".equals(argument) || "--command".equals(argument)) {
                String resource = workdir != null ? "FILE:" + Path.of(workdir).normalize() : "INLINE:" + executableName;
                return List.of(new ResourcePermission(resource, EnumSet.of(Permission.EXECUTE)));
            }
        }

        String scriptPath = null;
        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if (!argument.startsWith("-")) {
                scriptPath = argument;
                break;
            }
        }

        if (scriptPath == null) {
            return null;
        }

        String absoluteScriptPath = resolveToAbsolutePath(scriptPath, workdir);
        if (absoluteScriptPath == null) {
            return null;
        }

        return List.of(new ResourcePermission(
                "FILE:" + absoluteScriptPath,
                EnumSet.of(Permission.READ, Permission.EXECUTE)));
    }

    private static String resolveToAbsolutePath(String pathArg, String workdir) {
        if (pathArg == null || pathArg.isEmpty()) {
            return null;
        }
        if (pathArg.startsWith("/")) {
            return Path.of(pathArg).normalize().toString();
        }
        if (pathArg.startsWith("~")) {
            String home = System.getProperty("user.home");
            if (home != null) {
                return Path.of(home + pathArg.substring(1)).normalize().toString();
            }
        }
        if (workdir != null && !workdir.isBlank()) {
            return Path.of(workdir, pathArg).normalize().toString();
        }
        return null;
    }
}
