package code.chg.agent.llm;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMClient
 * @description Defines synchronous and streaming LLM client operations.
 */
public interface LLMClient {
    /**
     * Executes a synchronous chat request.
     */
    List<LLMMessage> chat(LLMRequest request);

    /**
     * Executes a streaming chat request.
     */
    StreamResponse streamChat(LLMRequest request);

    /**
     * Requests a structured response and maps it to the target type.
     */
    <T> T structureResponseChat(String input, Class<T> responseType);
}
