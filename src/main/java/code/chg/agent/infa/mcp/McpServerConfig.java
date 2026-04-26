package code.chg.agent.infa.mcp;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title McpServerConfig
 * @description Stores configuration for an MCP server connection.
 */
@Data
@Builder
public class McpServerConfig {

    /**
     * Logical name for this MCP server (used in logging/error messages).
     */
    private String name;

    /**
     * Transport type: "stdio", "sse", or "streamable-http". Default: stdio.
     */
    @Builder.Default
    private String transport = "stdio";

    /**
     * Command to launch the MCP server process (stdio transport only).
     */
    private String command;

    /**
     * Arguments passed to the MCP server command.
     */
    @Builder.Default
    private List<String> args = List.of();

    /**
     * Environment variables to pass to the MCP server process.
     */
    @Builder.Default
    private Map<String, String> env = Map.of();

    /**
     * Remote MCP endpoint URL for HTTP transports. Accepts either a base URL or a full endpoint URL.
     */
    private String url;

    /**
     * Request timeout in milliseconds. Default: 30000.
     */
    @Builder.Default
    private long timeoutMs = 30_000;
}
