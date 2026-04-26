package code.chg.agent.lib.tool.shell.safety;

import code.chg.agent.core.permission.Permission;

import java.util.*;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CommandWhitelist
 * @description Whitelist-based command registry that maps known Unix commands
 */
public final class CommandWhitelist {

    private CommandWhitelist() {
    }

    /**
     * Permission category for a whitelisted command.
     */
    public enum CommandCategory {
        READ,
        WRITE,
        EXECUTE,
        DANGEROUS
    }

    /**
     * Information about a whitelisted command's permission requirements.
     */
    public record CommandPermissionInfo(
            CommandCategory category,
            Set<Permission> requiredPermissions,
            boolean needsArgAnalysis,
            boolean hasFileArgs
    ) {
        public static CommandPermissionInfo read() {
            return new CommandPermissionInfo(CommandCategory.READ, EnumSet.of(Permission.READ), false, true);
        }

        /**
         * For commands whose arguments are never file paths (e.g. echo, printf, date).
         */
        public static CommandPermissionInfo readNoFileArgs() {
            return new CommandPermissionInfo(CommandCategory.READ, EnumSet.of(Permission.READ), false, false);
        }

        public static CommandPermissionInfo readWithArgAnalysis() {
            return new CommandPermissionInfo(CommandCategory.READ, EnumSet.of(Permission.READ), true, true);
        }

        public static CommandPermissionInfo write() {
            return new CommandPermissionInfo(CommandCategory.WRITE, EnumSet.of(Permission.WRITE), false, true);
        }

        public static CommandPermissionInfo execute() {
            return new CommandPermissionInfo(CommandCategory.EXECUTE, EnumSet.of(Permission.EXECUTE), false, true);
        }

        public static CommandPermissionInfo dangerous() {
            return new CommandPermissionInfo(CommandCategory.DANGEROUS,
                    EnumSet.of(Permission.READ, Permission.WRITE, Permission.EXECUTE), false, true);
        }
    }

    private static final Map<String, CommandPermissionInfo> REGISTRY = new HashMap<>();

    static {
        // ── READ commands (read-only) ──────────────────────────────────
        for (String cmd : List.of(
                "cat", "head", "tail", "less", "more",
                "grep", "egrep", "fgrep", "rg",
                "ls", "ll", "tree",
                "wc", "diff", "file", "stat", "du", "df",
                "sort", "uniq", "cut", "paste", "tr", "rev", "nl",
                "od", "xxd",
                "md5sum", "sha256sum", "sha1sum",
                "readlink", "realpath", "basename", "dirname",
                "jq", "yq",
                "test",
                "column", "fold", "fmt", "expand", "unexpand",
                "comm", "cmp", "csplit",
                "tac", "shuf",
                "lsof", "ps"
        )) {
            REGISTRY.put(cmd, CommandPermissionInfo.read());
        }

        // READ commands whose arguments are never file paths
        for (String cmd : List.of(
                "echo", "printf", "seq",
                "whoami", "id", "uname", "hostname", "date", "cal",
                "pwd", "env", "printenv", "locale",
                "true", "false", "expr", "bc",
                "nproc", "uptime", "free", "top",
                "dig", "nslookup", "host",
                "which", "whereis", "type", "command"
        )) {
            REGISTRY.put(cmd, CommandPermissionInfo.readNoFileArgs());
        }

        // READ commands that need deeper argument analysis
        for (String cmd : List.of("find", "sed", "awk", "xargs", "curl", "wget", "git")) {
            REGISTRY.put(cmd, CommandPermissionInfo.readWithArgAnalysis());
        }

        // ── WRITE commands ─────────────────────────────────────────────
        for (String cmd : List.of(
                "cp", "mv", "mkdir", "touch",
                "tee", "chmod", "chown", "chgrp", "ln",
                "install", "patch",
                "tar", "zip", "unzip", "gzip", "gunzip", "bzip2", "bunzip2", "xz"
        )) {
            REGISTRY.put(cmd, CommandPermissionInfo.write());
        }

        // ── EXECUTE commands ───────────────────────────────────────────
        for (String cmd : List.of(
                "bash", "sh", "zsh",
                "python", "python3", "python2",
                "node", "npx", "deno", "bun",
                "java", "javac",
                "make", "cmake",
                "npm", "yarn", "pnpm",
                "pip", "pip3", "pipenv", "poetry",
                "cargo", "rustc",
                "go",
                "mvn", "gradle", "ant",
                "ruby", "perl", "php",
                "docker", "docker-compose",
                "kubectl",
                "terraform",
                "gcc", "g++", "clang", "clang++"
        )) {
            REGISTRY.put(cmd, CommandPermissionInfo.execute());
        }

        // ── DANGEROUS commands ─────────────────────────────────────────
        for (String cmd : List.of(
                "rm", "rmdir",
                "dd", "mkfs", "fdisk", "mount", "umount",
                "kill", "killall", "pkill",
                "shutdown", "reboot", "halt", "poweroff",
                "sudo", "su",
                "chroot",
                "iptables", "ip6tables",
                "systemctl", "service"
        )) {
            REGISTRY.put(cmd, CommandPermissionInfo.dangerous());
        }
    }

    /**
     * Get the permission info for a command.
     *
     * @param cmdName the executable name (without path)
     * @return permission info if whitelisted, empty otherwise
     */
    public static Optional<CommandPermissionInfo> getCommandPermission(String cmdName) {
        if (cmdName == null || cmdName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(cmdName));
    }

    /**
     * Check if a command is in the whitelist.
     */
    public static boolean isWhitelisted(String cmdName) {
        return cmdName != null && REGISTRY.containsKey(cmdName);
    }
}
