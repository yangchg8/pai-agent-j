package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import lombok.Data;

import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AIMessageChannelChunk
 * @description Mutable channel body for streamed assistant content.
 */
@Data
public class AIMessageChannelChunk implements ChannelMessageBody {

    private final StringBuilder contentChunk;
    private final Map<Integer, AIToolCallChunk> toolCallChunks;

    public AIMessageChannelChunk() {
        this.contentChunk = new StringBuilder();
        this.toolCallChunks = new java.util.HashMap<>();
    }


    @Override
    public void accumulate(ChannelMessageBody body) {
        if (!(body instanceof AIMessageChannelChunk chunk)) {
            throw new IllegalArgumentException("Cannot accumulate non-AIChannelMessageChunk body");
        }
        this.contentChunk.append(chunk.getContentChunk());
        Map<Integer, AIToolCallChunk> toolCallChunks = chunk.getToolCallChunks();
        if (toolCallChunks == null || toolCallChunks.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, AIToolCallChunk> entry : chunk.getToolCallChunks().entrySet()) {
            AIToolCallChunk toolCallChunk = entry.getValue();
            String toolCallId = toolCallChunk.getToolCallId() == null ? null : toolCallChunk.getToolCallId().toString();
            String toolCallName = toolCallChunk.getToolCallName() == null ? null : toolCallChunk.getToolCallName().toString();
            String toolCallArgument = toolCallChunk.getToolCallArguments() == null ? null : toolCallChunk.getToolCallArguments().toString();
            this.accumulateToolCallChunk(entry.getKey(), toolCallId, toolCallName, toolCallArgument);
        }
    }

    public void accumulateContentChunk(String content) {
        if (content != null) {
            this.contentChunk.append(content);
        }
    }

    public void accumulateToolCallChunk(int index, String toolCallId, String toolCallName, String toolCallArguments) {
        this.toolCallChunks.compute(index, (existing, existChunk) -> {
            if (existChunk == null) {
                existChunk = new AIToolCallChunk();
            }
            if (toolCallId != null) {
                existChunk.getToolCallId().append(toolCallId);
            }
            if (toolCallName != null) {
                existChunk.getToolCallName().append(toolCallName);
            }
            if (toolCallArguments != null) {
                existChunk.getToolCallArguments().append(toolCallArguments);
            }
            return existChunk;
        });
    }

    @Data
    public static class AIToolCallChunk {
        private final StringBuilder toolCallId;
        private final StringBuilder toolCallName;
        private final StringBuilder toolCallArguments;

        public AIToolCallChunk() {
            this.toolCallId = new StringBuilder();
            this.toolCallName = new StringBuilder();
            this.toolCallArguments = new StringBuilder();
        }
    }
}
