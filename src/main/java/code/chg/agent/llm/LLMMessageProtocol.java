package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMMessageProtocol
 * @description Enumerates supported LLM message protocols.
 */
public enum LLMMessageProtocol {
    /**
     * OpenAI-compatible protocol.
     */
    OPEN_AI("openai"),
    /**
     * Default protocol fallback.
     */
    DEFAULT("default");

    /**
     * Protocol identifier used in configuration and serialization.
     */
    final String protocol;

    LLMMessageProtocol(String protocol) {
        this.protocol = protocol;
    }
}
