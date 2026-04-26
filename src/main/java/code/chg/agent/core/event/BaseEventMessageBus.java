package code.chg.agent.core.event;

import code.chg.agent.core.session.SessionData;
import code.chg.agent.core.session.SessionStoreManager;
import code.chg.agent.core.session.SubscriptionSessionData;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import code.chg.agent.core.channel.ChatChannel;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.event.message.ToolEventMessage;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title BaseEventMessageBus
 * @description Base implementation for the event bus, including routing, retries, and persistence hooks.
 *
 * <p>Concurrency model:
 * <ul>
 *   <li>A single virtual-thread consumer loop drains the {@code retryableQueue}.</li>
 *   <li>Tool execution is dispatched to a virtual-thread-per-task executor.</li>
 *   <li>{@code retryableQueue} is a {@link ConcurrentLinkedQueue} — lock-free and thread-safe.</li>
 *   <li>{@link LockSupport#park()}/{@link LockSupport#unpark(Thread)} coordinates the consumer
 *       loop with tool completion and publish events (no explicit locks needed).</li>
 *   <li>State persistence uses a separate {@code stateLock} to avoid contention with the consumer loop.</li>
 * </ul>
 */
@Slf4j
@Getter
public abstract class BaseEventMessageBus implements EventMessageBus {
    private static final int MAX_RETRY_COUNT = 3;

    private final String sessionId;
    private final BlockingQueue<DelayChannelEventMessage> delayMessageQueue;
    private final ConcurrentLinkedQueue<RetryableEventMessage> retryableQueue;
    private final Map<String, Subscription> subscriptions;
    private final int maxRetryCount;
    private final ExecutorService toolExecutor;
    private final AtomicInteger runningMessageCount;
    private final AtomicBoolean consumeLoopRunning;
    private volatile Thread consumeLoop;

    private final ReentrantLock stateLock = new ReentrantLock();

    public BaseEventMessageBus(String sessionId, ExecutorService executorService) {
        this.sessionId = sessionId;
        this.retryableQueue = new ConcurrentLinkedQueue<>();
        this.subscriptions = new ConcurrentHashMap<>();
        this.delayMessageQueue = new LinkedBlockingQueue<>();
        this.runningMessageCount = new AtomicInteger(0);
        this.toolExecutor = executorService;
        this.consumeLoopRunning = new AtomicBoolean(false);
        this.consumeLoop = null;
        this.maxRetryCount = MAX_RETRY_COUNT;
    }

    public void restoreAfterSubscribe(SessionData sessionData) {
        stateLock.lock();
        try {
            List<SubscriptionSessionData> subscriptionSessionData = sessionData.getSubscriptionSessionData();
            if (subscriptionSessionData == null || subscriptionSessionData.isEmpty()) {
                return;
            }
            for (SubscriptionSessionData data : subscriptionSessionData) {
                String subscriptionName = data.getSubscriptionName();
                byte[] stateData = data.getData();
                if (subscriptionName == null || stateData == null || !subscriptions.containsKey(subscriptionName)) {
                    continue;
                }

                Subscription subscription = subscriptions.get(subscriptionName);
                if (subscription instanceof StateSubscription) {
                    ((StateSubscription) subscription).restore(stateData);
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    public void saveStateNow() {
        stateLock.lock();
        try {
            SessionStoreManager store = getSessionStoreManager();
            if (store == null) {
                return;
            }
            SessionData sessionData = store.getSessionMetadata(sessionId);
            if (sessionData == null) {
                return;
            }
            sessionData.setSessionId(sessionId);
            sessionData.setUpdatedAt(System.currentTimeMillis());
            List<SubscriptionSessionData> stateData = new ArrayList<>();
            for (Map.Entry<String, Subscription> entry : subscriptions.entrySet()) {
                Subscription subscription = entry.getValue();
                if (!(subscription instanceof StateSubscription stateSubscription)) {
                    continue;
                }
                SubscriptionSessionData data = new SubscriptionSessionData();
                data.setSubscriptionName(entry.getKey());
                data.setData(stateSubscription.getState());
                stateData.add(data);
            }
            sessionData.setSubscriptionSessionData(stateData);
            store.saveSessionData(sessionData);
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public List<SubscriptionDescriptor> subscriptions() {
        List<SubscriptionDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<String, Subscription> entry : subscriptions.entrySet()) {
            Subscription subscription = entry.getValue();
            descriptors.add(new SubscriptionDescriptor(entry.getKey(), subscription.getClass().getSimpleName()));
        }
        return descriptors;
    }

    private void startAsyncConsumer() {
        if (this.consumeLoopRunning.compareAndSet(false, true)) {
            this.consumeLoop = Thread.ofVirtual()
                    .name("event-bus-consumer-" + sessionId)
                    .start(this::consumeMessagesInLoop);
        }
    }

    public abstract SessionStoreManager getSessionStoreManager();

    @Override
    public void publish(EventMessage message) {
        if (message == null) {
            log.debug("publish message is null");
            return;
        }
        log.debug("publish message[{}],type: {} ,content {}", message.hashCode(), message.type(), message.body());
        retryableQueue.add(new RetryableEventMessage(message));
        LockSupport.unpark(consumeLoop);
    }

    @Override
    public boolean subscribe(Subscription subscription) {
        if (subscription == null) {
            throw new RuntimeException("subscription is null");
        }
        String name = subscription.name();
        if (name == null) {
            throw new RuntimeException("subscription name is null");
        }
        if (subscriptions.containsKey(name)) {
            return false;
        }
        subscriptions.put(name, subscription);
        subscription.onSubscribe(this);
        return true;
    }

    @Override
    public boolean unsubscribe(String name) {
        if (!subscriptions.containsKey(name)) {
            return false;
        }
        return subscriptions.remove(name) != null;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public void talk(EventMessage message, ChatChannel channel) {
        startAsyncConsumer();
        this.delayMessageQueue.add(new DelayChannelEventMessage(message, channel));
    }

    /**
     * Main consumer loop. Runs on a virtual thread.
     *
     * <p>Phase 1: saves state, closes the current channel, then blocks
     * on {@code delayMessageQueue.take()} waiting for the next conversation turn.
     *
     * <p>Phase 2: drains the retryableQueue, dispatching each message
     * to tool executors. When the queue is empty but tools are still running,
     * parks via {@link LockSupport#park()}. When both the queue is empty and
     * all tools have finished, returns to Phase 1.
     */
    public void consumeMessagesInLoop() {
        ChatChannel chatChannel = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // === Phase 1: wait for next conversation turn ===
                if (chatChannel != null) {
                    saveStateNow();
                    chatChannel.close();
                    chatChannel = null;
                }

                DelayChannelEventMessage delayChannelEventMessage = this.delayMessageQueue.take();
                EventMessage message = delayChannelEventMessage.getMessage();
                if (message == null) {
                    continue;
                }
                chatChannel = delayChannelEventMessage.getChannel();

                // === Phase 2: drain retryableQueue (lock-free) ===
                retryableQueue.add(new RetryableEventMessage(message));
                EventBusContext context = new DefaultEventBusContext(chatChannel);

                while (true) {
                    // Drain all queued messages
                    RetryableEventMessage retryableMessage;
                    while ((retryableMessage = retryableQueue.poll()) != null) {
                        EventMessage eventMessage = retryableMessage.getMessage();
                        log.debug("start consume message[{}],type: {},retryCount: {}", eventMessage.hashCode(),
                                eventMessage.type(), retryableMessage.getRetryCount());
                        startConsumeMessage(retryableMessage, context);
                    }

                    // All queued messages dispatched — check if tools are still running
                    if (this.runningMessageCount.get() == 0 && retryableQueue.isEmpty()) {
                        break; // All done, return to Phase 1
                    }
                    // Tools still running — park until signalled by tool completion or publish
                    LockSupport.park();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        consumeLoopRunning.set(false);
    }

    private void signalConsumer() {
        LockSupport.unpark(consumeLoop);
    }

    private void startConsumeMessage(RetryableEventMessage retryableMessage, EventBusContext context) {
        this.runningMessageCount.incrementAndGet();
        toolExecutor.execute(() -> {
            try {
                consumeMessage(retryableMessage, context);
            } finally {
                this.runningMessageCount.decrementAndGet();
                signalConsumer();
            }
        });
    }

    private void consumeMessage(RetryableEventMessage retryableMessage, EventBusContext context) {
        EventMessage eventMessage = retryableMessage.getMessage();
        boolean isRetry = retryableMessage.isRetry();
        if (isRetry) {
            consumeRetryMessage(retryableMessage, eventMessage, context);
        } else {
            consumeNewMessage(retryableMessage, eventMessage, context);
        }
    }

    private void consumeRetryMessage(RetryableEventMessage retryableMessage, EventMessage eventMessage,
                                     EventBusContext context) {
        String consumedBy = retryableMessage.getConsumedBy();
        Subscription subscription = subscriptions.get(consumedBy);
        if (subscription == null || !subscription.concern(eventMessage)) {
            return;
        }
        log.debug("subscription [{}] retry consume message[{}], retryCount: {}", consumedBy,
                eventMessage.hashCode(), retryableMessage.getRetryCount());
        try {
            subscription.onMessage(eventMessage, context, new EventMessageBusCallBack() {
                @Override
                public void onSuccess() {
                    log.debug("subscription [{}] retry consume message[{}] success", consumedBy,
                            eventMessage.hashCode());
                }

                @Override
                public void onFailure(String errorMessage, Throwable throwable) {
                    log.error("subscription [{}] retry consume message[{}] failed", consumedBy,
                            eventMessage.hashCode());
                    BaseEventMessageBus.this.onConsumeFailure(retryableMessage, consumedBy, errorMessage,
                            throwable);
                }
            });
        } catch (Exception e) {
            log.error("subscription [{}] retry consume message[{}] exception", consumedBy, eventMessage.hashCode(),
                    e);
            onConsumeFailure(retryableMessage, consumedBy, e.getMessage(), e);
        }
    }

    private void consumeNewMessage(RetryableEventMessage retryableMessage, EventMessage eventMessage,
                                   EventBusContext context) {
        for (Map.Entry<String, Subscription> entry : subscriptions.entrySet()) {
            String name = entry.getKey();
            Subscription subscription = entry.getValue();
            if (!subscription.concern(eventMessage)) {
                continue;
            }
            log.debug("subscription [{}] consume message[{}]", name, eventMessage.hashCode());
            try {
                subscription.onMessage(eventMessage, context, new EventMessageBusCallBack() {
                    @Override
                    public void onSuccess() {
                        log.debug("subscription [{}] consume message[{}] success", name, eventMessage.hashCode());
                    }

                    @Override
                    public void onFailure(String errorMessage, Throwable throwable) {
                        log.error("subscription [{}] consume message[{}] failed:{}", name, eventMessage.hashCode(), throwable.getMessage(), throwable);
                        BaseEventMessageBus.this.onConsumeFailure(retryableMessage, name, errorMessage,
                                throwable);
                    }
                });
            } catch (Exception e) {
                log.error("subscription [{}] consume message[{}] exception", name, eventMessage.hashCode(), e);
                onConsumeFailure(retryableMessage, name, e.getMessage(), e);
            }
        }
    }

    private boolean canRetry(RetryableEventMessage retryableMessage) {
        return retryableMessage.getRetryCount() < this.maxRetryCount;
    }

    private void onConsumeFailure(RetryableEventMessage retryableMessage, String consumedBy, String errorMessage,
                                  Throwable throwable) {
        RetryableEventMessage retryMessage = retryableMessage.nextRetry(consumedBy);
        if (canRetry(retryMessage)) {
            retryableQueue.add(retryMessage);
            LockSupport.unpark(consumeLoop);
        }
        if (retryableMessage.getMessage().type() == EventMessageType.TOOL_CALL_REQUEST) {
            publishToolErrorResponse(retryableMessage.getMessage(), errorMessage, throwable);
        }
    }

    private void publishToolErrorResponse(EventMessage originalMessage, String errorMessage, Throwable throwable) {
        ToolEventBody toolEventBody = (ToolEventBody) originalMessage.body();
        publish(new ToolEventMessage(toolEventBody.getToolCall(), errorMessage));
    }

    @Getter
    private static class RetryableEventMessage {
        private final EventMessage message;
        @Setter
        private int retryCount;
        private final String consumedBy;

        public RetryableEventMessage(EventMessage message) {
            this(message, 0, null);
        }

        public RetryableEventMessage(EventMessage message, int retryCount, String consumedBy) {
            this.message = message;
            this.retryCount = retryCount;
            this.consumedBy = consumedBy;
        }

        public boolean isRetry() {
            return retryCount > 0 && consumedBy != null;
        }

        public RetryableEventMessage nextRetry(String consumedBy) {
            return new RetryableEventMessage(this.message, this.retryCount + 1, consumedBy);
        }
    }

    @Getter
    private static class DelayChannelEventMessage {
        private final EventMessage message;
        private final ChatChannel channel;

        public DelayChannelEventMessage(EventMessage message, ChatChannel channel) {
            this.message = message;
            this.channel = channel;
        }
    }

}
