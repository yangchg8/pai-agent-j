package code.chg.agent.core.memory.state;

import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.ToolCall;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PersistentLLMMessage
 * @description Persisted form of an LLM message used in memory snapshots.
 */
@Data
@NoArgsConstructor
public class PersistentLLMMessage implements LLMMessage {
    private String id;
    private MessageType type;
    private String toolCallId;
    private List<PersistentToolCall> toolCalls;
    private String content;
    private Map<String, String> metadata;


    public PersistentLLMMessage(LLMMessage llmMessage) {
        this.id = llmMessage.id();
        this.type = llmMessage.type();
        this.toolCallId = llmMessage.toolCallId();
        this.content = llmMessage.content();
        if (llmMessage.toolCalls() != null) {
            this.toolCalls = llmMessage.toolCalls().stream().map(PersistentToolCall::new).toList();
        }
        if (llmMessage instanceof PersistentLLMMessage persistent && persistent.getMetadata() != null) {
            this.metadata = new LinkedHashMap<>(persistent.getMetadata());
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public MessageType type() {
        return type;
    }

    @Override
    public String toolCallId() {
        return toolCallId;
    }

    @Override
    public List<ToolCall> toolCalls() {
        if (toolCalls == null) {
            return null;
        }
        return toolCalls.stream().map(item -> (ToolCall) item)
                .toList();
    }

    @Override
    public String content() {
        return content;
    }


    @NoArgsConstructor
    @Data
    private static class PersistentToolCall implements ToolCall {
        private String id;
        private String name;
        private String arguments;

        public PersistentToolCall(ToolCall toolCall) {
            this.id = toolCall.id();
            this.name = toolCall.name();
            this.arguments = toolCall.arguments();
        }

        @Override
        public String id() {
            return this.id;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public String arguments() {
            return this.arguments;
        }
    }
}
