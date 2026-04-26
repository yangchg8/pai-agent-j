package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMClientFactory
 * @description Defines a factory for creating LLM clients.
 */
public interface LLMClientFactory {
    /**
     * Creates an LLM client instance.
     *
     * @return the created LLM client
     */
    LLMClient getClient();
}
