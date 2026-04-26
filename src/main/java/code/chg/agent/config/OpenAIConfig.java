package code.chg.agent.config;

import java.nio.file.Path;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title OpenAIConfig
 * @description OpenAI configuration accessors backed by ~/.pai-agent/config.yml.
 */
public final class OpenAIConfig {

    private OpenAIConfig() {
    }

    public static String getApiKey() {
        return PaiAgentConfig.get().getOpenai().getApi().getKey();
    }


    public static String getBaseUrl() {
        return PaiAgentConfig.get().getOpenai().getApi().getBaseUrl();
    }

    public static String getDefaultModel() {
        return PaiAgentConfig.get().getOpenai().getModel().getName();
    }

    public static boolean isParallelToolCalls() {
        return PaiAgentConfig.get().getOpenai().getTool().isParallelCalls();
    }

    public static int getMaxInlineToolResponseChars() {
        return PaiAgentConfig.get().getOpenai().getTool().getMaxInlineResponseChars();
    }

    public static String getOversizedToolResponseDir() {
        return PaiAgentConfig.get().getOpenai().getTool().getOversizedResponseDir();
    }

    public static int getToken() {
        Integer token = PaiAgentConfig.get().getOpenai().getModel().getToken();
        return token == null ? 128_000 : token;
    }

    public static int getContextWindowTokens() {
        int configured = PaiAgentConfig.get().getMemory().getCompaction().getContextWindowTokens();
        return configured > 0 ? configured : getToken();
    }

    public static double getCompactionTriggerThreshold() {
        return PaiAgentConfig.get().getMemory().getCompaction().getTriggerThreshold();
    }

    public static int getRetainedUserMessageMaxTokens() {
        return PaiAgentConfig.get().getMemory().getCompaction().getRetainedUserMessageMaxTokens();
    }

    public static Path getConfigPath() {
        return PaiAgentConfig.resolveConfigPath();
    }

    static void reload() {
        PaiAgentConfig.reload();
    }
}

