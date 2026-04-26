package code.chg.agent.core.channel;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChannelMessage
 * @description Value object representing a message transmitted through a chat channel.
 */
public interface ChannelMessage {

    /**
     * Returns the message ID.
     */
    String id();

    /**
     * Returns the message sender.
     */
    String name();

    /**
     * Returns the message type.
     */
    ChannelMessageType type();

    /**
     * Returns the message body.
     */
    ChannelMessageBody body();

    /**
     * Indicates whether the message is complete.
     * For streamed output, this marks the final chunk.
     */
    boolean completed();

}
