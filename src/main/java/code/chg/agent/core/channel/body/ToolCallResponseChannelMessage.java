package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolCallResponseChannelMessage
 * @description Channel message body for a tool execution response.
 */
@Data
public class ToolCallResponseChannelMessage implements ChannelMessageBody {

    private String toolCallId;
    private String toolName;
    private String response;

    @Override
    public void accumulate(ChannelMessageBody body) {
        if (!(body instanceof ToolCallResponseChannelMessage next)) {
            return;
        }
        this.toolCallId = next.toolCallId;
        this.toolName = next.toolName;
        this.response = next.response;
    }
}
