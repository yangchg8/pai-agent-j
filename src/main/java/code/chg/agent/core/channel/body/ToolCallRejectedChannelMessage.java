package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolCallRejectedChannelMessage
 * @description Channel message body describing a rejected tool call.
 */
@Data
public class ToolCallRejectedChannelMessage implements ChannelMessageBody {

    private String toolCallId;
    private String reason;

    @Override
    public void accumulate(ChannelMessageBody body) {
        if (!(body instanceof ToolCallRejectedChannelMessage next)) {
            return;
        }
        this.toolCallId = next.toolCallId;
        this.reason = next.reason;
    }
}
