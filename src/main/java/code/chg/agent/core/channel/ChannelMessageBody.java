package code.chg.agent.core.channel;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChannelMessageBody
 * @description Defines a mutable body for channel messages.
 */
public interface ChannelMessageBody {
    /**
     * Merges another body into the current body instance.
     *
     * @param body the body to accumulate
     */
    void accumulate(ChannelMessageBody body);
}
