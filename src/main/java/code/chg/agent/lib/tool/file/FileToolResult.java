package code.chg.agent.lib.tool.file;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title FileToolResult
 * @description Result returned by FileTool operations.
 */
@Data
public class FileToolResult {

    private boolean success;
    private String content;
    private String errorMessage;
    /**
     * Line count for read results, entry count for list results.
     */
    private int count;

    private FileToolResult() {
    }

    public static FileToolResult ok(String content, int count) {
        FileToolResult r = new FileToolResult();
        r.success = true;
        r.content = content;
        r.count = count;
        return r;
    }

    public static FileToolResult error(String errorMessage) {
        FileToolResult r = new FileToolResult();
        r.success = false;
        r.errorMessage = errorMessage;
        r.content = errorMessage;
        return r;
    }

    @Override
    public String toString() {
        return success ? content : errorMessage;
    }
}
