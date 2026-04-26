package code.chg.agent.core.channel.subscriber;

import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelMessageBody;
import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.core.channel.body.AuthorizationRequestChannelMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LogSubscriber
 * @description Channel subscriber that logs rendered messages for debugging.
 */
@Slf4j
public class LogSubscriber implements ChannelSubscriber {

    @Override
    public void onMessage(ChannelMessage message) {
        log.info("Received channel message,messageId: {}, messageType: {}, content: {}",
                message.id(), message.type(), content(message));
    }

    private static String content(ChannelMessage message) {
        ChannelMessageBody channelMessageBody = message.body();
        if (channelMessageBody instanceof AIMessageChannelChunk) {
            return channelMessageBody.toString();
        } else if (channelMessageBody instanceof AuthorizationRequestChannelMessage authorizationRequestChannelMessage) {
            return authorizationRequestChannelMessage.toString();
        } else {
            log.warn("Unsupported message body type: {}", channelMessageBody.getClass().getName());
        }
        return null;
    }

}