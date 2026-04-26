package code.chg.agent.core.memory;

import code.chg.agent.core.tool.Tool;
import code.chg.agent.llm.*;
import code.chg.agent.utils.MessageIdGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SystemMemoryRegion
 * @description Memory region that provides the system prompt and static tool definitions.
 */
public class SystemMemoryRegion implements MemoryRegion {
    private final String systemPrompt;
    private final List<Tool> tools;
    private final String messageId;


    public SystemMemoryRegion(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        this.tools = new ArrayList<>();
        this.messageId = MessageIdGenerator.generateWithPrefix("system-memory-region");
    }

    @Override
    public String getName() {
        return "SYSTEM_MEMORY_REGION";
    }

    @Override
    public List<Tool> tools() {
        return tools;
    }

    @Override
    public List<LLMMessage> messages() {
        if (systemPrompt == null) {
            return Collections.emptyList();
        }
        return List.of(new LLMMessage() {
            @Override
            public String content() {
                return systemPrompt;
            }

            @Override
            public String id() {
                return messageId;
            }

            @Override
            public MessageType type() {
                return MessageType.SYSTEM;
            }
        });
    }

    public void bindTool(Tool tool) {
        this.tools.add(tool);
    }


}
