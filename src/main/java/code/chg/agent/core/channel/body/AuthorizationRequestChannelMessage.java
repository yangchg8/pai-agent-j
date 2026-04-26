package code.chg.agent.core.channel.body;

import code.chg.agent.core.channel.ChannelMessageBody;
import code.chg.agent.llm.component.AuthorizationRequirementContent;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AuthorizationRequestChannelMessage
 * @description Channel message body describing a tool authorization request.
 */
@Data
public class AuthorizationRequestChannelMessage implements ChannelMessageBody {
    String toolCallId;
    String prompt;
    AuthorizationRequirementContent content;

    @Override
    public void accumulate(ChannelMessageBody body) {
        if (!(body instanceof AuthorizationRequestChannelMessage next)) {
            return;
        }
        this.toolCallId = next.toolCallId;
        this.prompt = next.prompt;
        this.content = next.content;
    }
}
