package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.core.permission.Permission;
import code.chg.agent.lib.tool.shell.safety.ResourcePermission;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellPermissionHookSupport
 * @description Provides the ShellPermissionHookSupport implementation.
 */
final class ShellPermissionHookSupport {

    private ShellPermissionHookSupport() {
    }

    static ShellCommandPermissionDecision fileArgsDecision(String executableName,
                                                           List<String> commandWords,
                                                           Set<Permission> permissions,
                                                           String workDir) {
        List<String> pathArgs = collectNonOptionArguments(executableName, commandWords);
        return analyzedOrWorkdir(pathArgs, permissions, workDir);
    }

    static ShellCommandPermissionDecision directResources(List<ResourcePermission> resourcePermissions) {
        return ShellCommandPermissionDecision.analyzed(resourcePermissions);
    }

    static ShellCommandPermissionDecision directResources(Map<String, Set<Permission>> resourcePermissions) {
        List<ResourcePermission> resources = new ArrayList<>();
        for (Map.Entry<String, Set<Permission>> entry : resourcePermissions.entrySet()) {
            resources.add(new ResourcePermission(entry.getKey(), entry.getValue()));
        }
        return ShellCommandPermissionDecision.analyzed(resources);
    }

    static ShellCommandPermissionDecision workdirDecision(Set<Permission> permissions, String workDir) {
        if (workDir == null || workDir.isBlank()) {
            return ShellCommandPermissionDecision.analyzed(List.of());
        }
        return ShellCommandPermissionDecision.analyzed(List.of(
                new ResourcePermission("FILE:" + Path.of(workDir).normalize(), permissions)));
    }

    static ShellCommandPermissionDecision analyzedOrWorkdir(List<String> pathArgs,
                                                            Set<Permission> permissions,
                                                            String workDir) {
        List<ResourcePermission> resources = new ArrayList<>();
        for (String pathArg : pathArgs) {
            String absolutePath = resolveToAbsolutePath(pathArg, workDir);
            if (absolutePath != null) {
                resources.add(new ResourcePermission("FILE:" + absolutePath, permissions));
            }
        }
        if (resources.isEmpty()) {
            return workdirDecision(permissions, workDir);
        }
        return ShellCommandPermissionDecision.analyzed(resources);
    }

    static List<String> collectNonOptionArguments(String executableName, List<String> commandWords) {
        List<String> values = new ArrayList<>();
        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if (argument.startsWith("-")) {
                if (isOptionWithValue(executableName, argument) && index + 1 < commandWords.size()) {
                    index++;
                }
                continue;
            }
            values.add(argument);
        }
        return values;
    }

    static List<ResourcePermission> extractFindResourcePaths(List<String> commandWords,
                                                             Set<Permission> permissions,
                                                             String workDir) {
        Set<String> findOptionsWithValue = COMMAND_OPTIONS_WITH_VALUE.get("find");
        List<ResourcePermission> resources = new ArrayList<>();
        int maxDepth = -1;

        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if ("-maxdepth".equals(argument) && index + 1 < commandWords.size()) {
                try {
                    maxDepth = Integer.parseInt(commandWords.get(index + 1));
                } catch (NumberFormatException ignored) {
                    maxDepth = -1;
                }
                break;
            }
        }

        String suffix;
        if (maxDepth < 0 || maxDepth >= 2) {
            suffix = "/**";
        } else if (maxDepth == 1) {
            suffix = "/*";
        } else {
            suffix = "";
        }

        for (int index = 1; index < commandWords.size(); index++) {
            String argument = commandWords.get(index);
            if (argument.startsWith("-")) {
                if (findOptionsWithValue != null && findOptionsWithValue.contains(argument)) {
                    index++;
                }
                continue;
            }

            String absolutePath = resolveToAbsolutePath(argument, workDir);
            if (absolutePath != null) {
                String resource = absolutePath + suffix;
                String prefix = suffix.isEmpty() ? "FILE:" : "DIR:";
                resources.add(new ResourcePermission(prefix + resource, permissions));
            }
        }

        if (resources.isEmpty() && workDir != null && !workDir.isBlank()) {
            resources.add(new ResourcePermission("DIR:" + Path.of(workDir).normalize() + suffix, permissions));
        }
        return resources;
    }

    static String resolveToAbsolutePath(String pathArg, String workDir) {
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
        if (workDir != null && !workDir.isBlank()) {
            return Path.of(workDir, pathArg).normalize().toString();
        }
        return null;
    }

    static boolean isOptionWithValue(String executableName, String flag) {
        if (executableName != null) {
            Set<String> commandOptions = COMMAND_OPTIONS_WITH_VALUE.get(executableName);
            if (commandOptions != null && commandOptions.contains(flag)) {
                return true;
            }
        }
        return switch (flag) {
            case "-o", "-O", "-f", "-F", "-d", "-D", "-n", "-t", "-T",
                 "-e", "-E", "-I", "-L", "-m", "-w", "-W", "-s", "-S",
                 "-C", "-c", "-k", "-K", "-b", "-B", "-u", "-g", "-G",
                 "--output", "--file", "--directory", "--format" -> true;
            default -> false;
        };
    }

    static void addResourcePermission(Map<String, Set<Permission>> resources,
                                      String resource,
                                      Set<Permission> permissions) {
        if (resource == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        resources.computeIfAbsent(resource, key -> new java.util.LinkedHashSet<>()).addAll(permissions);
    }

    static Map<String, Set<Permission>> newResourceMap() {
        return new LinkedHashMap<>();
    }

    static String fileResource(String pathArg, String workDir) {
        String absolutePath = resolveToAbsolutePath(pathArg, workDir);
        return absolutePath == null ? null : "FILE:" + absolutePath;
    }

    static String siblingFileResource(String pathArg, String siblingName, String workDir) {
        String absolutePath = resolveToAbsolutePath(pathArg, workDir);
        if (absolutePath == null) {
            return null;
        }
        Path source = Path.of(absolutePath);
        Path parent = source.getParent();
        Path sibling = parent == null ? Path.of(siblingName) : parent.resolve(siblingName);
        return "FILE:" + sibling.normalize();
    }

    private static final Map<String, Set<String>> COMMAND_OPTIONS_WITH_VALUE = Map.of(
            "find", Set.of(
                    "-name", "-iname", "-path", "-ipath",
                    "-regex", "-iregex",
                    "-type", "-xtype",
                    "-maxdepth", "-mindepth",
                    "-mtime", "-ctime", "-atime",
                    "-mmin", "-cmin", "-amin",
                    "-size", "-perm",
                    "-uid", "-gid", "-user", "-group",
                    "-links", "-inum",
                    "-printf"
            ),
            "grep", Set.of(
                    "--max-count",
                    "-A", "--after-context",
                    "-B", "--before-context",
                    "-C", "--context",
                    "--label",
                    "--include", "--exclude", "--exclude-dir",
                    "--devices", "--binary-files"
            ),
            "egrep", Set.of(
                    "--max-count",
                    "-A", "--after-context",
                    "-B", "--before-context",
                    "-C", "--context",
                    "--label",
                    "--include", "--exclude", "--exclude-dir",
                    "--devices", "--binary-files"
            ),
            "fgrep", Set.of(
                    "--max-count",
                    "-A", "--after-context",
                    "-B", "--before-context",
                    "-C", "--context",
                    "--label",
                    "--include", "--exclude", "--exclude-dir",
                    "--devices", "--binary-files"
            ),
            "rg", Set.of(
                    "-g", "--glob",
                    "-t", "--type",
                    "-T", "--type-not",
                    "-m", "--max-count",
                    "-A", "--after-context",
                    "-B", "--before-context",
                    "-C", "--context",
                    "--iglob", "--ignore-file", "--pre", "--pre-glob"
            )
    );
}