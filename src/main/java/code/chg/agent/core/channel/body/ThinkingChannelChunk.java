package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ThinkingChannelChunk
 * @description Channel message body carrying streaming thinking/reasoning content.
 */
@Data
public class ThinkingChannelChunk implements ChannelMessageBody {

    private final StringBuilder thinking = new StringBuilder();

    public void accumulateThinking(String content) {
        if (content != null) {
            thinking.append(content);
        }
    }

    public String getThinking() {
        return thinking.toString();
    }

    @Override
    public void accumulate(ChannelMessageBody body) {
        if (body instanceof ThinkingChannelChunk other) {
            thinking.append(other.getThinking());
        }
    }
}
