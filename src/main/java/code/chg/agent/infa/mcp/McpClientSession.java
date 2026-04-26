package code.chg.agent.infa.mcp;

import code.chg.agent.utils.JsonUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title McpClientSession
 * @description Provides the McpClientSession implementation.
 */
public class McpClientSession implements Closeable {

    private final McpSyncClient client;

    public McpClientSession(McpServerConfig config) {
        McpJsonMapper mapper = new JacksonMcpJsonMapper(JsonUtil.getObjectMapper());
        McpClientTransport transport = createTransport(config, mapper);
        Duration timeout = Duration.ofMillis(Math.max(1L, config.getTimeoutMs()));
        this.client = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .clientInfo(new McpSchema.Implementation("PaiAgent", "1.0.0"))
                .build();
        this.client.initialize();
    }

    public List<McpSchema.Tool> listTools() {
        return client.listTools().tools();
    }

    public String callTool(String toolName, Map<String, Object> arguments) throws Exception {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        String rendered = renderContent(result.content());
        if (rendered.isBlank() && result.structuredContent() != null) {
            rendered = JsonUtil.getObjectMapper().writeValueAsString(result.structuredContent());
        }
        if ((rendered == null || rendered.isBlank()) && Boolean.TRUE.equals(result.isError())) {
            return "MCP tool error";
        }
        return rendered == null ? "" : rendered;
    }

    private String renderContent(List<McpSchema.Content> content) throws Exception {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (McpSchema.Content item : content) {
            if (item instanceof McpSchema.TextContent textContent) {
                builder.append(textContent.text());
            } else if (item instanceof McpSchema.ImageContent imageContent) {
                builder.append("[image: ").append(imageContent.mimeType()).append(']');
            } else {
                builder.append(JsonUtil.getObjectMapper().writeValueAsString(item));
            }
        }
        return builder.toString();
    }

    private McpClientTransport createTransport(McpServerConfig config, McpJsonMapper mapper) {
        String transport = config.getTransport() == null ? "stdio" : config.getTransport().trim().toLowerCase();
        return switch (transport) {
            case "stdio" -> createStdioTransport(config, mapper);
            case "sse" -> createSseTransport(config, mapper);
            case "streamable-http" -> createStreamableHttpTransport(config, mapper);
            default -> throw new IllegalArgumentException("Unsupported MCP transport: " + config.getTransport());
        };
    }

    private McpClientTransport createStdioTransport(McpServerConfig config, McpJsonMapper mapper) {
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException("MCP stdio transport requires command");
        }
        ServerParameters params = ServerParameters.builder(config.getCommand())
                .args(config.getArgs() == null ? List.of() : config.getArgs())
                .env(config.getEnv() == null ? Map.of() : config.getEnv())
                .build();
        return new StdioClientTransport(params, mapper);
    }

    private McpClientTransport createSseTransport(McpServerConfig config, McpJsonMapper mapper) {
        HttpEndpoint endpoint = parseEndpoint(config.getUrl(), "/sse");
        return HttpClientSseClientTransport.builder(endpoint.baseUri())
                .sseEndpoint(endpoint.path())
                .jsonMapper(mapper)
                .connectTimeout(Duration.ofMillis(Math.max(1L, config.getTimeoutMs())))
                .build();
    }

    private McpClientTransport createStreamableHttpTransport(McpServerConfig config, McpJsonMapper mapper) {
        HttpEndpoint endpoint = parseEndpoint(config.getUrl(), "/mcp");
        return HttpClientStreamableHttpTransport.builder(endpoint.baseUri())
                .endpoint(endpoint.path())
                .jsonMapper(mapper)
                .connectTimeout(Duration.ofMillis(Math.max(1L, config.getTimeoutMs())))
                .build();
    }

    private HttpEndpoint parseEndpoint(String rawUrl, String defaultPath) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("MCP HTTP transport requires url");
        }
        URI uri = URI.create(rawUrl);
        if (uri.getScheme() == null || uri.getAuthority() == null) {
            throw new IllegalArgumentException("Invalid MCP url: " + rawUrl);
        }
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = defaultPath;
        }
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            path = path + "?" + uri.getQuery();
        }
        return new HttpEndpoint(baseUri, path);
    }

    @Override
    public void close() {
        client.closeGracefully();
    }

    private record HttpEndpoint(String baseUri, String path) {
    }
}