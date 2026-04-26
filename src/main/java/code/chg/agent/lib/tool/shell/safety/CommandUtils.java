package code.chg.agent.lib.tool.shell.safety;

import java.io.File;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CommandUtils
 * @description Utility methods for shell command processing (Unix-only).
 */
public final class CommandUtils {

    private CommandUtils() {
    }

    /**
     * Extract the executable name from a raw command path.
     * E.g., "/usr/bin/cat" -> "cat", "cat" -> "cat".
     */
    public static String executableNameLookupKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String fileName = new File(raw).getName();
        if (fileName.isEmpty()) {
            return null;
        }
        return fileName;
    }

    /**
     * Detect if a shell string represents bash/zsh/sh with -lc or -c flag.
     * Returns the script portion if detected, null otherwise.
     */
    public static String extractBashLcScript(java.util.List<String> command) {
        if (command.size() != 3) {
            return null;
        }
        String shell = command.get(0);
        String flag = command.get(1);
        String script = command.get(2);

        if (!"-lc".equals(flag) && !"-c".equals(flag)) {
            return null;
        }

        String shellName = executableNameLookupKey(shell);
        if (shellName == null) {
            return null;
        }

        if ("zsh".equals(shellName) || "bash".equals(shellName) || "sh".equals(shellName)) {
            return script;
        }
        return null;
    }
}
