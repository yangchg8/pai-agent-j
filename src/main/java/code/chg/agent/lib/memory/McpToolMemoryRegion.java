package code.chg.agent.lib.memory;

import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.lib.event.LocalEventMessageBus;
import code.chg.agent.llm.LLMMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title McpStateMemoryRegion
 * @description Provides the McpStateMemoryRegion implementation.
 */
public class McpToolMemoryRegion implements MemoryRegion {
    private final LocalEventMessageBus messageBus;

    private final Map<String, List<Tool>> mcpTools;

    public McpToolMemoryRegion(LocalEventMessageBus localEventMessageBus) {
        this.messageBus = localEventMessageBus;
        this.mcpTools = new ConcurrentHashMap<>();
    }

    @Override
    public String getName() {
        return "MCP_STATE_MEMORY_REGION";
    }

    @Override
    public List<LLMMessage> messages() {
        return Collections.emptyList();
    }

    @Override
    public List<Tool> tools() {
        return this.mcpTools.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(item -> item.getValue().stream())
                .toList();
    }

    public void activate(String mcpName, List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        for (Tool tool : tools) {
            this.messageBus.subscribe(tool);
        }
        mcpTools.put(mcpName, tools);
    }

    public void deactivate(String mcpName) {
        if (!mcpTools.containsKey(mcpName)) {
            return;
        }
        List<Tool> tools = mcpTools.get(mcpName);
        for (Tool tool : tools) {
            this.messageBus.unsubscribe(tool.name());
        }
    }

    public int mcpCount() {
        return mcpTools.size();
    }

    public boolean isActive(String mcpName) {
        return mcpTools.containsKey(mcpName);
    }

}