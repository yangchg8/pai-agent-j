package code.chg.agent.lib.tool.shell;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellCommandExecutor
 * @description Handles shell command building and process execution.
 */
public final class ShellCommandExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 100_000;

    private ShellCommandExecutor() {
    }

    /**
     * Build the shell command list for inline command execution.
     * Uses $SHELL environment variable with -lc flag (login shell semantics).
     * Falls back to /bin/bash if $SHELL is not set.
     */
    public static List<String> buildShellCommand(String command) {
        List<String> cmd = new ArrayList<>(3);
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isBlank()) {
            shell = "/bin/bash";
        }
        cmd.add(shell);
        cmd.add("-lc");
        cmd.add(command);
        return cmd;
    }

    /**
     * Execute a shell process with timeout handling, output buffering, and truncation management.
     *
     * @param command        the full command list (e.g. ["/bin/bash", "-lc", "ls -la"])
     * @param workdir        the working directory, may be null
     * @param timeoutMs      timeout in milliseconds, null for default
     * @param maxOutputChars maximum output characters, null for default
     * @return the execution result
     */
    public static ShellToolResult executeProcess(List<String> command, String workdir,
                                                 Long timeoutMs, Integer maxOutputChars) {
        long timeout = (timeoutMs != null && timeoutMs > 0) ? timeoutMs : DEFAULT_TIMEOUT_MS;
        int maxChars = (maxOutputChars != null && maxOutputChars > 0) ? maxOutputChars : DEFAULT_MAX_OUTPUT_CHARS;
        long startNanos = System.nanoTime();

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.put("LANG", "en_US.UTF-8");

            if (workdir != null && !workdir.isBlank()) {
                File dir = new File(workdir);
                if (dir.isDirectory()) {
                    pb.directory(dir);
                }
            }

            Process process = pb.start();
            InputStream is = process.getInputStream();

            StringBuilder output = new StringBuilder();
            int totalCharsRead = 0;
            boolean outputTruncated = false;
            boolean timedOut = false;
            long deadlineNanos = startNanos + timeout * 1_000_000L;
            byte[] buf = new byte[8192];

            while (true) {
                int avail = is.available();
                if (avail > 0) {
                    int n = is.read(buf, 0, Math.min(buf.length, avail));
                    if (n > 0) {
                        totalCharsRead += n;
                        if (!outputTruncated) {
                            String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                            if (output.length() + chunk.length() > maxChars) {
                                int remaining = maxChars - output.length();
                                if (remaining > 0) {
                                    output.append(chunk, 0, remaining);
                                }
                                outputTruncated = true;
                            } else {
                                output.append(chunk);
                            }
                        }
                    }
                }

                if (!process.isAlive()) {
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        totalCharsRead += n;
                        if (!outputTruncated) {
                            String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                            if (output.length() + chunk.length() > maxChars) {
                                int remaining = maxChars - output.length();
                                if (remaining > 0) {
                                    output.append(chunk, 0, remaining);
                                }
                                outputTruncated = true;
                            } else {
                                output.append(chunk);
                            }
                        }
                    }
                    break;
                }

                if (System.nanoTime() >= deadlineNanos) {
                    process.destroyForcibly();
                    timedOut = true;
                    break;
                }

                Thread.sleep(10);
            }

            double wallTimeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

            String outputStr = output.toString();
            if (outputStr.endsWith("\n")) {
                outputStr = outputStr.substring(0, outputStr.length() - 1);
            }

            if (outputTruncated) {
                outputStr += "\n[Output truncated: " + totalCharsRead + " chars produced, "
                        + maxChars + " chars returned]";
            }

            if (timedOut) {
                return new ShellToolResult(wallTimeSeconds, null,
                        outputStr + "\n[Process timed out after " + timeout + "ms]",
                        totalCharsRead, outputTruncated);
            }

            return new ShellToolResult(wallTimeSeconds, process.exitValue(), outputStr,
                    totalCharsRead, outputTruncated);

        } catch (IOException e) {
            double wallTimeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            return new ShellToolResult(wallTimeSeconds, -1,
                    "Error starting process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            double wallTimeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            return new ShellToolResult(wallTimeSeconds, -1,
                    "Process interrupted: " + e.getMessage());
        }
    }
}
