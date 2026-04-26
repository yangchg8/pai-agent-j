package code.chg.agent.llm;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMMessageChunk
 * @description Defines a streamed fragment of model output.
 */
public interface LLMMessageChunk {
    /**
     * Returns the chunk identifier.
     */
    String id();

    /**
     * Returns any streamed tool call fragments.
     */
    List<ToolCallChunk> toolCalls();

    /**
     * Returns the streamed content fragment.
     */
    String content();

    /**
     * Returns the thinking/reasoning content for models that expose chain-of-thought
     * (e.g. OpenAI o-series reasoning_content, Anthropic thinking blocks).
     * Returns null when the model does not produce thinking content.
     */
    default String thinkingContent() {
        return null;
    }
}
