package code.chg.agent.lib.session;

import code.chg.agent.lib.memory.McpToolMemoryRegion;
import code.chg.agent.lib.react.ReactAgent;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionRuntimeContext
 * @description Provides the SessionRuntimeContext implementation.
 */
@Data
public class SessionRuntimeContext {
    private String sessionId;
    private ReactAgent agent;
    private int estimatedTokens;
    private int latestModelTokens;
    private String title;
    private McpToolMemoryRegion mcpToolMemoryRegion;
}