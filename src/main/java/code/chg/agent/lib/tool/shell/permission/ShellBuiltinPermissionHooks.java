package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.lib.tool.shell.safety.CommandUtils;
import code.chg.agent.lib.tool.shell.safety.ResourcePermission;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellBuiltinPermissionHooks
 * @description Provides the ShellBuiltinPermissionHooks implementation.
 */
final class ShellBuiltinPermissionHooks {

    private static final Set<String> GIT_READ_SUBCOMMANDS = Set.of(
            "status", "log", "diff", "show", "branch", "tag", "remote", "config"
    );

    private static final Set<String> GIT_WRITE_SUBCOMMANDS = Set.of(
            "add", "commit", "push", "pull", "merge", "rebase", "checkout", "switch",
            "stash", "reset", "fetch", "clone", "init", "restore", "cherry-pick"
    );

    private ShellBuiltinPermissionHooks() {
    }

    static void registerDefaults(ShellCommandPermissionRegistry registry) {
        ShellCommandPermissionHook readFileArgs = (context, unused) ->
                ShellPermissionHookSupport.fileArgsDecision(
                        context.executableName(),
                        context.commandWords(),
                        EnumSet.of(Permission.READ),
                        context.workDir());

        ShellCommandPermissionHook readWorkdir = (context, unused) ->
                ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.READ), context.workDir());

        ShellCommandPermissionHook writeFileArgs = (context, unused) ->
                ShellPermissionHookSupport.fileArgsDecision(
                        context.executableName(),
                        context.commandWords(),
                        EnumSet.of(Permission.WRITE),
                        context.workDir());

        ShellCommandPermissionHook executeWorkdir = (context, unused) ->
                ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.EXECUTE), context.workDir());

        ShellCommandPermissionHook dangerousFileArgs = (context, unused) ->
                ShellPermissionHookSupport.fileArgsDecision(
                        context.executableName(),
                        context.commandWords(),
                        EnumSet.of(Permission.READ, Permission.WRITE, Permission.EXECUTE),
                        context.workDir());

        registry.registerAll(List.of(
                "cat", "head", "tail", "less", "more",
                "ls", "ll", "tree",
                "wc", "diff", "file", "stat", "du", "df",
                "sort", "uniq", "cut", "paste", "tr", "rev", "nl",
                "od", "xxd",
                "md5sum", "sha256sum", "sha1sum",
                "readlink", "realpath", "basename", "dirname",
                "jq", "yq",
                "column", "fold", "fmt", "expand", "unexpand",
                "comm", "cmp", "csplit",
                "tac", "shuf", "lsof", "ps"
        ), readFileArgs);

        registry.registerAll(List.of("echo", "printf", "seq", "pwd", "whoami", "id", "uname",
                "hostname", "date", "cal", "env", "printenv", "locale", "true", "false",
                "expr", "bc", "nproc", "uptime", "free", "top", "dig", "nslookup", "host",
                "which", "whereis", "type", "command", "test"), readWorkdir);

        registry.registerAll(List.of(
                "mkdir", "touch", "chmod", "chown", "chgrp", "patch"
        ), writeFileArgs);

        registry.registerAll(List.of(
                "bash", "sh", "zsh", "python", "python3", "python2", "node", "npx", "deno",
                "bun", "java", "javac", "make", "cmake", "npm", "yarn", "pnpm", "pip",
                "pip3", "pipenv", "poetry", "cargo", "rustc", "go", "mvn", "gradle", "ant",
                "ruby", "perl", "php", "docker", "docker-compose", "kubectl", "terraform",
                "gcc", "g++", "clang", "clang++"
        ), executeWorkdir);

        registry.registerAll(List.of(
                "rm", "rmdir", "dd", "mkfs", "fdisk", "mount", "umount", "kill", "killall",
                "pkill", "shutdown", "reboot", "halt", "poweroff", "sudo", "su", "chroot",
                "iptables", "ip6tables", "systemctl", "service"
        ), dangerousFileArgs);

        ShellCommandPermissionHook grepLikeHook = (context, unused) -> {
            List<String> values = ShellPermissionHookSupport.collectNonOptionArguments(
                    context.executableName(), context.commandWords());
            if (!values.isEmpty()) {
                values = values.subList(1, values.size());
            }
            return ShellPermissionHookSupport.analyzedOrWorkdir(
                    values,
                    EnumSet.of(Permission.READ),
                    context.workDir());
        };
        registry.registerAll(List.of("grep", "egrep", "fgrep", "rg"), grepLikeHook);

        registry.register("find", (context, unused) -> {
            for (String argument : context.commandWords()) {
                if (Set.of("-exec", "-execdir", "-ok", "-okdir", "-delete", "-fls",
                        "-fprint", "-fprint0", "-fprintf").contains(argument)) {
                    return ShellCommandPermissionDecision.unanalyzable("find uses unsafe option: " + argument);
                }
            }
            return ShellPermissionHookSupport.directResources(
                    ShellPermissionHookSupport.extractFindResourcePaths(
                            context.commandWords(),
                            EnumSet.of(Permission.READ),
                            context.workDir()));
        });

        registry.register("sed", (context, unused) -> {
            boolean inPlace = false;
            for (String argument : context.commandWords()) {
                if ("-i".equals(argument) || argument.startsWith("-i")) {
                    inPlace = true;
                    break;
                }
            }

            List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("sed", context.commandWords());
            if (!values.isEmpty()) {
                values = values.subList(1, values.size());
            }
            return ShellPermissionHookSupport.analyzedOrWorkdir(
                    values,
                    inPlace ? EnumSet.of(Permission.WRITE) : EnumSet.of(Permission.READ),
                    context.workDir());
        });

        registry.register("awk", (context, unused) -> {
            List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("awk", context.commandWords());
            if (!values.isEmpty()) {
                values = values.subList(1, values.size());
            }
            return ShellPermissionHookSupport.analyzedOrWorkdir(
                    values,
                    EnumSet.of(Permission.READ),
                    context.workDir());
        });

        registry.register("git", ShellBuiltinPermissionHooks::analyzeGit);
        registry.register("cp", ShellBuiltinPermissionHooks::analyzeCopy);
        registry.register("mv", ShellBuiltinPermissionHooks::analyzeMove);
        registry.register("tee", ShellBuiltinPermissionHooks::analyzeTee);
        registry.register("ln", ShellBuiltinPermissionHooks::analyzeLink);
        registry.register("install", ShellBuiltinPermissionHooks::analyzeInstall);
        registry.register("zip", ShellBuiltinPermissionHooks::analyzeZip);
        registry.register("unzip", ShellBuiltinPermissionHooks::analyzeUnzip);
        registry.register("tar", ShellBuiltinPermissionHooks::analyzeTar);
        registry.register("gzip", ShellBuiltinPermissionHooks::analyzeGzipFamily);
        registry.register("gunzip", ShellBuiltinPermissionHooks::analyzeGunzipFamily);
        registry.register("bzip2", ShellBuiltinPermissionHooks::analyzeBzip2Family);
        registry.register("bunzip2", ShellBuiltinPermissionHooks::analyzeBunzip2Family);
        registry.register("xz", ShellBuiltinPermissionHooks::analyzeXzFamily);

        registry.register("curl", (context, unused) -> {
            Set<String> complexFlags = Set.of("-X", "--request", "-d", "--data", "--data-raw",
                    "--data-binary", "--data-urlencode", "-F", "--form", "-T", "--upload-file",
                    "-o", "--output", "-O", "--remote-name");
            for (String argument : context.commandWords()) {
                if (complexFlags.contains(argument)) {
                    return ShellCommandPermissionDecision.unanalyzable("curl uses stateful option: " + argument);
                }
            }
            return ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.READ), context.workDir());
        });

        registry.register("wget", (context, unused) -> {
            for (String argument : context.commandWords()) {
                if ("--spider".equals(argument) || "--dry-run".equals(argument)) {
                    return ShellPermissionHookSupport.workdirDecision(
                            EnumSet.of(Permission.READ), context.workDir());
                }
            }
            return ShellCommandPermissionDecision.unanalyzable("wget downloads files by default");
        });

        registry.register("xargs", (context, nestedRegistry) -> {
            List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("xargs", context.commandWords());
            if (values.isEmpty()) {
                return ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.READ), context.workDir());
            }
            String nestedCommand = CommandUtils.executableNameLookupKey(values.getFirst());
            if (nestedCommand == null || !nestedRegistry.hasHook(nestedCommand)) {
                return ShellCommandPermissionDecision.unanalyzable("xargs nested command is not registered");
            }
            return ShellPermissionHookSupport.directResources(List.of(
                    new ResourcePermission("COMMAND:" + nestedCommand, EnumSet.of(Permission.ALL))));
        });
    }

    private static ShellCommandPermissionDecision analyzeGit(ShellCommandPermissionContext context,
                                                             ShellCommandPermissionRegistry unused) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("git", context.commandWords());
        if (values.isEmpty()) {
            return ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.READ), context.workDir());
        }

        String subcommand = values.getFirst();
        Set<Permission> permissions;
        if (GIT_READ_SUBCOMMANDS.contains(subcommand)) {
            permissions = EnumSet.of(Permission.READ);
        } else if (GIT_WRITE_SUBCOMMANDS.contains(subcommand)) {
            permissions = EnumSet.of(Permission.WRITE, Permission.EXECUTE);
        } else {
            return ShellCommandPermissionDecision.unanalyzable("git subcommand is not registered: " + subcommand);
        }

        List<String> pathArgs = new ArrayList<>();
        for (int index = 1; index < values.size(); index++) {
            String value = values.get(index);
            if (!value.startsWith("-")) {
                pathArgs.add(value);
            }
        }
        return ShellPermissionHookSupport.analyzedOrWorkdir(pathArgs, permissions, context.workDir());
    }

    private static ShellCommandPermissionDecision analyzeCopy(ShellCommandPermissionContext context,
                                                              ShellCommandPermissionRegistry unused) {
        return analyzeSourceTargetCommand(context, EnumSet.of(Permission.READ), EnumSet.of(Permission.WRITE));
    }

    private static ShellCommandPermissionDecision analyzeMove(ShellCommandPermissionContext context,
                                                              ShellCommandPermissionRegistry unused) {
        return analyzeSourceTargetCommand(context,
                EnumSet.of(Permission.READ, Permission.WRITE),
                EnumSet.of(Permission.WRITE));
    }

    private static ShellCommandPermissionDecision analyzeTee(ShellCommandPermissionContext context,
                                                             ShellCommandPermissionRegistry unused) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("tee", context.commandWords());
        if (values.isEmpty()) {
            return ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.WRITE), context.workDir());
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        for (String value : values) {
            String resource = ShellPermissionHookSupport.fileResource(value, context.workDir());
            ShellPermissionHookSupport.addResourcePermission(resources, resource, EnumSet.of(Permission.WRITE));
        }
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeLink(ShellCommandPermissionContext context,
                                                              ShellCommandPermissionRegistry unused) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("ln", context.commandWords());
        if (values.size() < 2) {
            return ShellCommandPermissionDecision.unanalyzable("ln requires source and target paths");
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        ShellPermissionHookSupport.addResourcePermission(resources,
                ShellPermissionHookSupport.fileResource(values.get(values.size() - 2), context.workDir()),
                EnumSet.of(Permission.READ));
        ShellPermissionHookSupport.addResourcePermission(resources,
                ShellPermissionHookSupport.fileResource(values.getLast(), context.workDir()),
                EnumSet.of(Permission.WRITE));
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeInstall(ShellCommandPermissionContext context,
                                                                 ShellCommandPermissionRegistry unused) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("install", context.commandWords());
        if (values.isEmpty()) {
            return ShellPermissionHookSupport.workdirDecision(EnumSet.of(Permission.WRITE), context.workDir());
        }
        if (values.size() == 1) {
            return ShellPermissionHookSupport.analyzedOrWorkdir(values, EnumSet.of(Permission.WRITE), context.workDir());
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        for (int index = 0; index < values.size() - 1; index++) {
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.fileResource(values.get(index), context.workDir()),
                    EnumSet.of(Permission.READ));
        }
        ShellPermissionHookSupport.addResourcePermission(resources,
                ShellPermissionHookSupport.fileResource(values.getLast(), context.workDir()),
                EnumSet.of(Permission.WRITE));
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeZip(ShellCommandPermissionContext context,
                                                             ShellCommandPermissionRegistry unused) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("zip", context.commandWords());
        if (values.isEmpty()) {
            return ShellCommandPermissionDecision.unanalyzable("zip requires an archive path");
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        ShellPermissionHookSupport.addResourcePermission(resources,
                ShellPermissionHookSupport.fileResource(values.getFirst(), context.workDir()),
                EnumSet.of(Permission.WRITE));
        for (int index = 1; index < values.size(); index++) {
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.fileResource(values.get(index), context.workDir()),
                    EnumSet.of(Permission.READ));
        }
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeUnzip(ShellCommandPermissionContext context,
                                                               ShellCommandPermissionRegistry unused) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments("unzip", context.commandWords());
        if (values.isEmpty()) {
            return ShellCommandPermissionDecision.unanalyzable("unzip requires an archive path");
        }

        String archiveResource = ShellPermissionHookSupport.fileResource(values.getFirst(), context.workDir());
        String outputResource = extractOptionValue(context.commandWords(), "-d", "--directory");
        if (outputResource != null) {
            outputResource = ShellPermissionHookSupport.fileResource(outputResource, context.workDir());
        } else if (context.workDir() != null && !context.workDir().isBlank()) {
            outputResource = "FILE:" + java.nio.file.Path.of(context.workDir()).normalize();
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        ShellPermissionHookSupport.addResourcePermission(resources, archiveResource, EnumSet.of(Permission.READ));
        ShellPermissionHookSupport.addResourcePermission(resources, outputResource, EnumSet.of(Permission.WRITE));
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeTar(ShellCommandPermissionContext context,
                                                             ShellCommandPermissionRegistry unused) {
        TarMode tarMode = parseTarMode(context.commandWords());
        if (tarMode == null || tarMode.archivePath() == null) {
            return ShellCommandPermissionDecision.unanalyzable("tar mode or archive path cannot be determined");
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        String archiveResource = ShellPermissionHookSupport.fileResource(tarMode.archivePath(), context.workDir());
        switch (tarMode.mode()) {
            case CREATE, APPEND, UPDATE -> {
                ShellPermissionHookSupport.addResourcePermission(resources, archiveResource, EnumSet.of(Permission.WRITE));
                for (String input : tarMode.payloadPaths()) {
                    ShellPermissionHookSupport.addResourcePermission(resources,
                            ShellPermissionHookSupport.fileResource(input, context.workDir()),
                            EnumSet.of(Permission.READ));
                }
            }
            case EXTRACT -> {
                ShellPermissionHookSupport.addResourcePermission(resources, archiveResource, EnumSet.of(Permission.READ));
                String outputDir = tarMode.changeDirectory() != null ? tarMode.changeDirectory() : context.workDir();
                String outputResource = outputDir == null ? null : ShellPermissionHookSupport.fileResource(outputDir, context.workDir());
                if (outputResource != null) {
                    ShellPermissionHookSupport.addResourcePermission(resources,
                            outputResource,
                            EnumSet.of(Permission.WRITE));
                }
            }
            case LIST ->
                    ShellPermissionHookSupport.addResourcePermission(resources, archiveResource, EnumSet.of(Permission.READ));
        }
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeGzipFamily(ShellCommandPermissionContext context,
                                                                    ShellCommandPermissionRegistry unused) {
        return analyzeCompressionFamily(context, Set.of("-d", "--decompress"), ".gz");
    }

    private static ShellCommandPermissionDecision analyzeGunzipFamily(ShellCommandPermissionContext context,
                                                                      ShellCommandPermissionRegistry unused) {
        return analyzeDecompressionOnly(context, ".gz");
    }

    private static ShellCommandPermissionDecision analyzeBzip2Family(ShellCommandPermissionContext context,
                                                                     ShellCommandPermissionRegistry unused) {
        return analyzeCompressionFamily(context, Set.of("-d", "--decompress"), ".bz2");
    }

    private static ShellCommandPermissionDecision analyzeBunzip2Family(ShellCommandPermissionContext context,
                                                                       ShellCommandPermissionRegistry unused) {
        return analyzeDecompressionOnly(context, ".bz2");
    }

    private static ShellCommandPermissionDecision analyzeXzFamily(ShellCommandPermissionContext context,
                                                                  ShellCommandPermissionRegistry unused) {
        return analyzeCompressionFamily(context, Set.of("-d", "--decompress", "--uncompress"), ".xz");
    }

    private static ShellCommandPermissionDecision analyzeCompressionFamily(ShellCommandPermissionContext context,
                                                                           Set<String> decompressFlags,
                                                                           String suffix) {
        for (String argument : context.commandWords()) {
            if (decompressFlags.contains(argument)) {
                return analyzeDecompressionOnly(context, suffix);
            }
        }

        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments(context.executableName(), context.commandWords());
        if (values.isEmpty()) {
            return ShellCommandPermissionDecision.unanalyzable(context.executableName() + " requires an input path");
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        for (String input : values) {
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.fileResource(input, context.workDir()),
                    EnumSet.of(Permission.READ));
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.siblingFileResource(input, input + suffix, context.workDir()),
                    EnumSet.of(Permission.WRITE));
        }
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeDecompressionOnly(ShellCommandPermissionContext context,
                                                                           String suffix) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments(context.executableName(), context.commandWords());
        if (values.isEmpty()) {
            return ShellCommandPermissionDecision.unanalyzable(context.executableName() + " requires an input path");
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        for (String input : values) {
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.fileResource(input, context.workDir()),
                    EnumSet.of(Permission.READ));
            String outputName = deriveDecompressedName(input, suffix);
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.siblingFileResource(input, outputName, context.workDir()),
                    EnumSet.of(Permission.WRITE));
        }
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static ShellCommandPermissionDecision analyzeSourceTargetCommand(ShellCommandPermissionContext context,
                                                                             Set<Permission> sourcePermissions,
                                                                             Set<Permission> targetPermissions) {
        List<String> values = ShellPermissionHookSupport.collectNonOptionArguments(context.executableName(), context.commandWords());
        if (values.size() < 2) {
            return ShellCommandPermissionDecision.unanalyzable(context.executableName() + " requires source and target paths");
        }

        Map<String, Set<Permission>> resources = ShellPermissionHookSupport.newResourceMap();
        for (int index = 0; index < values.size() - 1; index++) {
            ShellPermissionHookSupport.addResourcePermission(resources,
                    ShellPermissionHookSupport.fileResource(values.get(index), context.workDir()),
                    sourcePermissions);
        }
        ShellPermissionHookSupport.addResourcePermission(resources,
                ShellPermissionHookSupport.fileResource(values.getLast(), context.workDir()),
                targetPermissions);
        return ShellPermissionHookSupport.directResources(resources);
    }

    private static String extractOptionValue(List<String> commandWords, String... flags) {
        Set<String> acceptedFlags = Set.of(flags);
        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if (acceptedFlags.contains(argument) && index + 1 < commandWords.size()) {
                return commandWords.get(index + 1);
            }
        }
        return null;
    }

    private static String deriveDecompressedName(String input, String suffix) {
        if (input.endsWith(suffix) && input.length() > suffix.length()) {
            return input.substring(0, input.length() - suffix.length());
        }
        return input + ".out";
    }

    private static TarMode parseTarMode(List<String> commandWords) {
        TarAction action = null;
        String archivePath = null;
        String changeDirectory = null;
        List<String> payloadPaths = new ArrayList<>();

        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if (argument.startsWith("--file=")) {
                archivePath = argument.substring("--file=".length());
                continue;
            }
            if (argument.startsWith("--directory=")) {
                changeDirectory = argument.substring("--directory=".length());
                continue;
            }
            if ("-f".equals(argument) || "--file".equals(argument)) {
                if (index + 1 < commandWords.size()) {
                    archivePath = commandWords.get(++index);
                }
                continue;
            }
            if ("-C".equals(argument) || "--directory".equals(argument)) {
                if (index + 1 < commandWords.size()) {
                    changeDirectory = commandWords.get(++index);
                }
                continue;
            }
            if (argument.startsWith("-")) {
                if (argument.contains("x")) {
                    action = TarAction.EXTRACT;
                } else if (argument.contains("c")) {
                    action = TarAction.CREATE;
                } else if (argument.contains("r")) {
                    action = TarAction.APPEND;
                } else if (argument.contains("u")) {
                    action = TarAction.UPDATE;
                } else if (argument.contains("t")) {
                    action = TarAction.LIST;
                }
                if (argument.contains("f") && index + 1 < commandWords.size()) {
                    archivePath = commandWords.get(++index);
                }
                continue;
            }
            payloadPaths.add(argument);
        }

        if (action == null) {
            return null;
        }
        return new TarMode(action, archivePath, changeDirectory, payloadPaths);
    }

    private enum TarAction {
        CREATE,
        APPEND,
        UPDATE,
        EXTRACT,
        LIST
    }

    private record TarMode(TarAction mode, String archivePath, String changeDirectory, List<String> payloadPaths) {
    }
}