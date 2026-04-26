package code.chg.agent.lib.tool.web;

import code.chg.agent.annotation.Tool;
import code.chg.agent.annotation.ToolParameter;
import code.chg.agent.annotation.ToolPermissionChecker;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title WebFetchTool
 * @description Fetches a web page and returns its content as plain text.
 */
public class WebFetchTool {

    private static final String DESCRIPTION = """
            Fetch the content of a URL and return it as plain text.
            - Only HTTP/HTTPS URLs are supported.
            - HTML tags are stripped to return readable text.
            - Set max_chars to limit the returned content length (default: 50000).
            - Use this to read documentation, READMEs, API references, or web search results.""";

    private static final int DEFAULT_MAX_CHARS = 50_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Matches common HTML tags for stripping
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[ \t]+");
    private static final Pattern MULTI_NEWLINE_PATTERN = Pattern.compile("\n{3,}");

    @Tool(name = "web_fetch", description = DESCRIPTION)
    public static WebFetchResult webFetch(
            @ToolParameter(name = "url", description = "The HTTP or HTTPS URL to fetch.")
            String url,
            @ToolParameter(name = "max_chars", description = "Maximum characters to return. Defaults to 50000.")
            Integer maxChars
    ) {
        if (url == null || url.isBlank()) {
            return WebFetchResult.error("Error: url must be provided");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return WebFetchResult.error("Error: only http:// and https:// URLs are supported");
        }
        int limit = (maxChars != null && maxChars > 0) ? maxChars : DEFAULT_MAX_CHARS;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .header("User-Agent", "PaiAgent/1.0 (Java HttpClient)")
                    .header("Accept", "text/html,text/plain,application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body();
            if (body == null) body = "";

            String text = stripHtml(body);
            boolean truncated = text.length() > limit;
            if (truncated) {
                text = text.substring(0, limit) + "\n... [content truncated]";
            }
            return WebFetchResult.ok(url, statusCode, text, truncated);
        } catch (Exception e) {
            return WebFetchResult.error("Error fetching URL: " + e.getMessage());
        }
    }

    @ToolPermissionChecker(toolName = "web_fetch")
    public static ToolPermissionResult webFetchPermissionCheck(ToolPermissionPolicy policy, Object[] arguments) {
        String url = arguments.length > 0 && arguments[0] instanceof String s ? s : null;
        if (url == null) {
            return ToolPermissionResultFactory.rejected("web_fetch requires a url argument");
        }
        // Only allow http/https
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolPermissionResultFactory.rejected("web_fetch only allows HTTP/HTTPS URLs");
        }
        return ToolPermissionResultFactory.granted();
    }

    private static String stripHtml(String html) {
        // Decode basic HTML entities
        String text = html
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        // Remove script/style blocks
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        // Strip remaining HTML tags
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        // Collapse whitespace
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ");
        text = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        text = MULTI_NEWLINE_PATTERN.matcher(text).replaceAll("\n\n");
        return text;
    }
}
