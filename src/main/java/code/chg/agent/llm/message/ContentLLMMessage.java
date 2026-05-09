package code.chg.agent.llm.message;

import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ContentLLMMessage
 * @description Mutable persisted message implementation for plain content messages.
 */
@Data
@NoArgsConstructor
public class ContentLLMMessage implements LLMMessage {
    String id;
    MessageType type;
    String content;

    public static ContentLLMMessage of(String id, MessageType type, String content) {
        ContentLLMMessage message = new ContentLLMMessage();
        message.id = id;
        message.type = type;
        message.content = content;
        return message;
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
    public String content() {
        return content;
    }
}
