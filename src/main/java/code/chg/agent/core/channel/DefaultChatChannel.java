package code.chg.agent.core.channel;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title DefaultChatChannel
 * @description Provides the DefaultChatChannel implementation.
 */
@Slf4j
public class DefaultChatChannel implements ChatChannel {


    private final List<ChannelSubscriber> subscribers = new CopyOnWriteArrayList<>();

    private final CountDownLatch isComplete;

    private final AtomicBoolean isClosed;


    public DefaultChatChannel() {
        this.isComplete = new CountDownLatch(1);
        this.isClosed = new AtomicBoolean(false);
    }

    @Override
    public void publish(ChannelMessage message) {
        if (message == null) {
            log.warn("publish channel message is null");
            return;
        }
        if (isClosed()) {
            log.warn("channel is closed, cannot publish message,  message: {}", message);
            return;
        }
        for (ChannelSubscriber subscriber : subscribers) {
            try {
                subscriber.onMessage(message);
            } catch (Exception e) {
                log.error("failed to publish message to subscriber, message: {}, subscriber: {}", message, subscriber, e);
            }
        }
    }

    @Override
    public ChatChannel subscribe(ChannelSubscriber subscriber) {
        subscribers.add(subscriber);
        return this;
    }

    @Override
    public void close() {
        if (this.isClosed.compareAndSet(false, true)) {
            this.isComplete.countDown();
        }
    }

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    public void await() {
        try {
            isComplete.await();
        } catch (InterruptedException e) {
            log.error("await channel complete error: ", e);
            Thread.currentThread().interrupt();
        }
    }


    public boolean await(long timeout, TimeUnit unit) {
        try {
            return isComplete.await(timeout, unit);
        } catch (InterruptedException e) {
            log.error("await channel complete error, channelId:", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
