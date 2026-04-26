package code.chg.agent.llm.message;

import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolCallResponseLLMMessage
 * @description Immutable message representing a completed tool call response.
 */
public class ToolCallResponseLLMMessage implements LLMMessage {
    private final String id;
    private final String toolCallId;
    private final String content;

    public ToolCallResponseLLMMessage(String id, String toolCallId, String content) {
        this.id = id;
        this.toolCallId = toolCallId;
        this.content = content;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public MessageType type() {
        return MessageType.TOOL;
    }

    @Override
    public String toolCallId() {
        return toolCallId;
    }

    @Override
    public String content() {
        return content;
    }
}
