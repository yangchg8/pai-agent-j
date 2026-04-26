package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CompactNoticeChannelMessage
 * @description Provides the CompactNoticeChannelMessage implementation.
 */
@Data
public class CompactNoticeChannelMessage implements ChannelMessageBody {
    private String trigger;
    private Integer beforeTokens;
    private Integer afterTokens;
    private Integer reducedTokens;
    private String summary;

    @Override
    public void accumulate(ChannelMessageBody body) {
        if (!(body instanceof CompactNoticeChannelMessage next)) {
            return;
        }
        this.trigger = next.trigger;
        this.beforeTokens = next.beforeTokens;
        this.afterTokens = next.afterTokens;
        this.reducedTokens = next.reducedTokens;
        this.summary = next.summary;
    }
}