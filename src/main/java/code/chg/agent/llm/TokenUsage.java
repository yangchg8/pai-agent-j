package code.chg.agent.llm;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title TokenUsage
 * @description Provides the TokenUsage implementation.
 */
@Data
public class TokenUsage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer cachedPromptTokens;
}