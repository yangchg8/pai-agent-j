package code.chg.agent.core.channel;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChatChannel
 * @description Publisher/subscriber abstraction for streaming chat output.
 */
public interface ChatChannel {
    /**
     * Publishes a message to the channel.
     *
     * @param message the channel message
     */
    void publish(ChannelMessage message);

    /**
     * Registers a subscriber.
     *
     * @param subscriber the subscriber to add
     */
    ChatChannel subscribe(ChannelSubscriber subscriber);

    void close();

    boolean isClosed();
}
