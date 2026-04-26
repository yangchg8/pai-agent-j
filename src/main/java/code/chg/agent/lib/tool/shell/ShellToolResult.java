package code.chg.agent.lib.tool.shell;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellToolResult
 * @description Shell command execution result.
 */
@Data
public class ShellToolResult {

    /**
     * Elapsed wall time spent waiting for output in seconds.
     */
    private double wallTimeSeconds;

    /**
     * Process exit code when the command finished.
     * Null if the process was killed due to timeout.
     */
    private Integer exitCode;

    /**
     * Command output text, possibly truncated.
     * Contains merged stdout and stderr when redirectErrorStream is enabled.
     */
    private String output;

    /**
     * Original output character count before truncation was applied.
     * Useful for the caller to know how much content was produced.
     */
    private int originalOutputLength;

    /**
     * Whether the output was truncated due to exceeding max output length.
     */
    private boolean truncated;

    public ShellToolResult(double wallTimeSeconds, Integer exitCode, String output) {
        this.wallTimeSeconds = wallTimeSeconds;
        this.exitCode = exitCode;
        this.output = output;
        this.originalOutputLength = output != null ? output.length() : 0;
        this.truncated = false;
    }

    public ShellToolResult(double wallTimeSeconds, Integer exitCode, String output,
                           int originalOutputLength, boolean truncated) {
        this.wallTimeSeconds = wallTimeSeconds;
        this.exitCode = exitCode;
        this.output = output;
        this.originalOutputLength = originalOutputLength;
        this.truncated = truncated;
    }
}
