package code.chg.agent.core.channel;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChannelSubscriber
 * @description Subscriber interface for chat channel messages.
 */
public interface ChannelSubscriber {

    /**
     * Receives a channel message.
     *
     * @param message the channel message
     */
    void onMessage(ChannelMessage message);
}
