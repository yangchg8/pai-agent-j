package code.chg.agent.llm;

import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMMessage
 * @description Defines a message exchanged with the language model.
 */
public interface LLMMessage {
    /**
     * Returns the message identifier.
     */
    String id();

    /**
     * Returns the message role.
     */
    MessageType type();

    /**
     * Returns the related tool call identifier when the message is tool-related.
     */
    default String toolCallId() {
        return null;
    }

    /**
     * Returns any tool calls emitted by the message.
     */
    default List<ToolCall> toolCalls() {
        return Collections.emptyList();
    }

    /**
     * Returns the message content.
     */
    default String content() {
        return null;
    }
}
