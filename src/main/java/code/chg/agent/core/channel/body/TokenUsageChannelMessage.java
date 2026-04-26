package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title TokenUsageChannelMessage
 * @description Provides the TokenUsageChannelMessage implementation.
 */
@Data
public class TokenUsageChannelMessage implements ChannelMessageBody {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer estimatedContextTokens;
    private Integer maxTokens;
    private String source;

    @Override
    public void accumulate(ChannelMessageBody body) {
        if (!(body instanceof TokenUsageChannelMessage next)) {
            return;
        }
        this.promptTokens = next.promptTokens;
        this.completionTokens = next.completionTokens;
        this.totalTokens = next.totalTokens;
        this.estimatedContextTokens = next.estimatedContextTokens;
        this.maxTokens = next.maxTokens;
        this.source = next.source;
    }
}