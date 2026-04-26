package code.chg.agent.lib.tool.web;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title WebFetchResult
 * @description Result returned by WebFetchTool.
 */
@Data
public class WebFetchResult {

    private boolean success;
    private String url;
    private int statusCode;
    private String content;
    private boolean truncated;
    private String errorMessage;

    private WebFetchResult() {
    }

    public static WebFetchResult ok(String url, int statusCode, String content, boolean truncated) {
        WebFetchResult r = new WebFetchResult();
        r.success = true;
        r.url = url;
        r.statusCode = statusCode;
        r.content = content;
        r.truncated = truncated;
        return r;
    }

    public static WebFetchResult error(String errorMessage) {
        WebFetchResult r = new WebFetchResult();
        r.success = false;
        r.errorMessage = errorMessage;
        r.content = errorMessage;
        return r;
    }

    @Override
    public String toString() {
        if (!success) return errorMessage;
        StringBuilder sb = new StringBuilder();
        sb.append("[HTTP ").append(statusCode).append("] ").append(url).append("\n\n");
        sb.append(content);
        return sb.toString();
    }
}
