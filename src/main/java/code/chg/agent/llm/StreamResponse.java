package code.chg.agent.llm;

import java.util.Iterator;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title StreamResponse
 * @description Defines a streaming iterator over model output chunks.
 */
public interface StreamResponse extends AutoCloseable, Iterator<LLMMessageChunk> {
    /**
     * Returns the completed message list after streaming finishes.
     */
    List<LLMMessage> completion();

    /**
     * Returns usage metadata when the provider exposes it.
     */
    default TokenUsage usage() {
        return null;
    }
}
