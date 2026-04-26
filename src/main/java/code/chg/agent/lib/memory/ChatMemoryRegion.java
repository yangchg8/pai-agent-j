package code.chg.agent.lib.memory;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.body.HumanEventBody;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.memory.MemoryRegionHook;
import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.llm.*;
import code.chg.agent.llm.message.ToolCallResponseLLMMessage;
import code.chg.agent.utils.MessageIdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChatMemoryRegion
 * @description Chat history memory region.
 */
public class ChatMemoryRegion implements MemoryRegion {
    private List<LLMMessage> history = new ArrayList<>();

    @Override
    public String getName() {
        return "CHAT_HISTORY";
    }

    @Override
    public List<LLMMessage> messages() {
        return history;
    }

    @Override
    public void restore(List<PersistentLLMMessage> llmMessages) {
        if (llmMessages == null) {
            return;
        }
        this.history = new ArrayList<>(llmMessages.stream().map(item -> (LLMMessage) item)
                .toList());
        repairInterruptedToolCalls();
    }

    @Override
    public List<PersistentLLMMessage> getState() {
        if (history == null) {
            return null;
        }
        return history.stream().map(PersistentLLMMessage::new).toList();
    }

    @Override
    public MemoryRegionHook getHook() {
        return new MemoryRegionHook() {

            @Override
            public void onEventMessage(EventMessage message) {
                switch (message.type()) {
                    case HUMAN_MESSAGE -> history.add(new LLMMessage() {

                        @Override
                        public String content() {
                            return ((HumanEventBody) message.body()).getContent();
                        }

                        @Override
                        public String id() {
                            return message.id();
                        }

                        @Override
                        public MessageType type() {
                            return MessageType.HUMAN;
                        }
                    });
                    case TOOL_CALL_RESPONSE -> history.add(new ToolCallResponseLLMMessage(message.id(),
                            ((ToolEventBody) message.body()).toolCallId(),
                            ((ToolEventBody) message.body()).response()));
                    default -> {
                    }
                }
            }

            @Override
            public void afterEventMessage(List<LLMMessage> messages) {
                history.addAll(messages);
            }
        };
    }

    private void repairInterruptedToolCalls() {
        Set<String> requestedToolCallIds = new LinkedHashSet<>();
        Set<String> respondedToolCallIds = new LinkedHashSet<>();
        for (LLMMessage message : history) {
            if (message == null) {
                continue;
            }
            List<ToolCall> toolCalls = message.toolCalls();
            if (toolCalls != null) {
                for (ToolCall toolCall : toolCalls) {
                    if (toolCall != null && toolCall.id() != null) {
                        requestedToolCallIds.add(toolCall.id());
                    }
                }
            }
            if (message.type() == MessageType.TOOL && message.toolCallId() != null) {
                respondedToolCallIds.add(message.toolCallId());
            }
        }
        for (String toolCallId : requestedToolCallIds) {
            if (respondedToolCallIds.contains(toolCallId)) {
                continue;
            }
            history.add(new ToolCallResponseLLMMessage(
                    MessageIdGenerator.generateWithPrefix("tool-interrupt"),
                    toolCallId,
                    "Tool execution was interrupted before a result was returned."));
        }
    }


}
