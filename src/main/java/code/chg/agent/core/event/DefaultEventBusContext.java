package code.chg.agent.core.event;

import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.channel.ChatChannel;

import java.util.Objects;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title DefaultEventBusContext
 * @description Default event-bus context backed by a chat channel.
 */
public class DefaultEventBusContext implements EventBusContext {

    private final ChatChannel chatChannel;

    public DefaultEventBusContext(ChatChannel channel) {
        this.chatChannel = Objects.requireNonNullElseGet(channel, EmptyChatChannel::instance);
    }

    @Override
    public ChatChannel chat() {
        return chatChannel;
    }

    private static class EmptyChatChannel implements ChatChannel {
        private static final EmptyChatChannel EMPTY = new EmptyChatChannel();

        private EmptyChatChannel() {

        }

        public static EmptyChatChannel instance() {
            return EMPTY;
        }


        @Override
        public void publish(ChannelMessage message) {

        }

        @Override
        public ChatChannel subscribe(ChannelSubscriber subscriber) {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean isClosed() {
            return true;
        }
    }

}
