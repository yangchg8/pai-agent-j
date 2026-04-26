package code.chg.agent.core.event;

import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.channel.ChatChannel;
import code.chg.agent.core.event.body.EventBody;
import code.chg.agent.core.session.SessionStoreManager;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/**
 * Tests for {@link BaseEventMessageBus} concurrency model.
 * <p>
 * Uses hand-written mocks (no Mockito). All concurrency tests use
 * {@link CountDownLatch} for synchronization — never {@code Thread.sleep} in assertions.
 */
public class BaseEventMessageBusTest {

    // ────────────────────── Custom Mock Classes ──────────────────────

    /**
     * Concrete subclass of the abstract BaseEventMessageBus for testing.
     * Returns null for SessionStoreManager since persistence is not under test here.
     */
    private static class TestEventMessageBus extends BaseEventMessageBus {
        public TestEventMessageBus(String sessionId) {
            super(sessionId, Executors.newVirtualThreadPerTaskExecutor());
        }

        @Override
        public SessionStoreManager getSessionStoreManager() {
            return null;
        }
    }

    /**
     * Minimal ChatChannel that tracks close() calls via a CountDownLatch.
     */
    private static class LatchChatChannel implements ChatChannel {
        private final CountDownLatch closeLatch = new CountDownLatch(1);
        private volatile boolean closed = false;

        @Override
        public void publish(ChannelMessage message) { }

        @Override
        public ChatChannel subscribe(ChannelSubscriber subscriber) { return this; }

        @Override
        public void close() {
            closed = true;
            closeLatch.countDown();
        }

        @Override
        public boolean isClosed() { return closed; }

        public boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException {
            return closeLatch.await(timeout, unit);
        }
    }

    /**
     * Minimal EventMessage with configurable type and body.
     */
    private record SimpleEventMessage(String id, EventMessageType type, EventBody body) implements EventMessage { }

    /**
     * Subscription that records all received messages.
     * Uses CountDownLatch to signal when the expected number of messages arrive.
     */
    private static class RecordingSubscription implements Subscription {
        private final String name;
        private final Predicate<EventMessage> concernPredicate;
        private final CopyOnWriteArrayList<EventMessage> receivedMessages = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;

        RecordingSubscription(String name, Predicate<EventMessage> concernPredicate, int expectedMessages) {
            this.name = name;
            this.concernPredicate = concernPredicate;
            this.latch = new CountDownLatch(expectedMessages);
        }

        @Override
        public String name() { return name; }

        @Override
        public boolean concern(EventMessage message) { return concernPredicate.test(message); }

        @Override
        public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
            receivedMessages.add(message);
            callBack.onSuccess();
            latch.countDown();
        }

        @Override
        public void onSubscribe(EventMessageBus eventMessageBus) { }

        public List<EventMessage> messages() { return receivedMessages; }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    /**
     * Subscription that fails the first N invocations, then succeeds.
     * Records total invocation count.
     */
    private static class FailingSubscription implements Subscription {
        private final String name;
        private final int failCount;
        private final AtomicInteger invocations = new AtomicInteger(0);
        private final CountDownLatch successLatch;

        FailingSubscription(String name, int failCount, int expectedSuccesses) {
            this.name = name;
            this.failCount = failCount;
            this.successLatch = new CountDownLatch(expectedSuccesses);
        }

        @Override
        public String name() { return name; }

        @Override
        public boolean concern(EventMessage message) { return true; }

        @Override
        public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
            int count = invocations.incrementAndGet();
            if (count <= failCount) {
                callBack.onFailure("deliberate failure #" + count, new RuntimeException("test"));
            } else {
                callBack.onSuccess();
                successLatch.countDown();
            }
        }

        @Override
        public void onSubscribe(EventMessageBus eventMessageBus) { }

        public int totalInvocations() { return invocations.get(); }

        public boolean awaitSuccess(long timeout, TimeUnit unit) throws InterruptedException {
            return successLatch.await(timeout, unit);
        }
    }

    /**
     * Subscription that sleeps during onMessage to simulate slow tool execution.
     */
    private static class SlowSubscription implements Subscription {
        private final String name;
        private final long delayMs;
        private final CopyOnWriteArrayList<EventMessage> receivedMessages = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;

        SlowSubscription(String name, long delayMs, int expectedMessages) {
            this.name = name;
            this.delayMs = delayMs;
            this.latch = new CountDownLatch(expectedMessages);
        }

        @Override
        public String name() { return name; }

        @Override
        public boolean concern(EventMessage message) { return true; }

        @Override
        public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            receivedMessages.add(message);
            callBack.onSuccess();
            latch.countDown();
        }

        @Override
        public void onSubscribe(EventMessageBus eventMessageBus) { }

        public List<EventMessage> messages() { return receivedMessages; }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    // ────────────────────── Helper ──────────────────────

    private static EventMessage humanMessage(String id) {
        return new SimpleEventMessage(id, EventMessageType.HUMAN_MESSAGE, null);
    }

    private static EventMessage toolResponseMessage(String id) {
        return new SimpleEventMessage(id, EventMessageType.TOOL_CALL_RESPONSE, null);
    }

    // ────────────────────── Tests ──────────────────────

    @Test
    public void publishAndConsumeOneMessage() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-1");
        RecordingSubscription sub = new RecordingSubscription("recorder", msg -> true, 1);
        bus.subscribe(sub);

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("msg-1"), channel);

        assertTrue("Subscription should receive 1 message within 5s", sub.await(5, TimeUnit.SECONDS));
        assertTrue("Channel should close after processing", channel.awaitClose(5, TimeUnit.SECONDS));
        assertEquals(1, sub.messages().size());
        assertEquals("msg-1", sub.messages().get(0).id());
    }

    @Test
    public void publishRoutesToAllMatchingSubscriptions() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-2");
        RecordingSubscription sub1 = new RecordingSubscription("recorder-1",
                msg -> msg.type() == EventMessageType.HUMAN_MESSAGE, 1);
        RecordingSubscription sub2 = new RecordingSubscription("recorder-2",
                msg -> msg.type() == EventMessageType.HUMAN_MESSAGE, 1);
        bus.subscribe(sub1);
        bus.subscribe(sub2);

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("msg-broadcast"), channel);

        assertTrue("Sub1 should receive message", sub1.await(5, TimeUnit.SECONDS));
        assertTrue("Sub2 should receive message", sub2.await(5, TimeUnit.SECONDS));
        assertTrue("Channel should close", channel.awaitClose(5, TimeUnit.SECONDS));
        assertEquals(1, sub1.messages().size());
        assertEquals(1, sub2.messages().size());
    }

    @Test
    public void publishIgnoresNonMatchingSubscriptions() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-3");
        // Only concerns TOOL_CALL_RESPONSE — should NOT receive HUMAN_MESSAGE
        RecordingSubscription nonMatching = new RecordingSubscription("non-matching",
                msg -> msg.type() == EventMessageType.TOOL_CALL_RESPONSE, 1);
        // Matching subscription to ensure the bus processes the message
        RecordingSubscription matching = new RecordingSubscription("matching",
                msg -> msg.type() == EventMessageType.HUMAN_MESSAGE, 1);
        bus.subscribe(nonMatching);
        bus.subscribe(matching);

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("msg-filtered"), channel);

        assertTrue("Matching sub should receive message", matching.await(5, TimeUnit.SECONDS));
        assertTrue("Channel should close", channel.awaitClose(5, TimeUnit.SECONDS));
        assertEquals(0, nonMatching.messages().size());
    }

    @Test
    public void retryOnFailureAndEventualSuccess() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-4");
        // Fails first 2 times, succeeds on 3rd
        FailingSubscription sub = new FailingSubscription("flaky", 2, 1);
        bus.subscribe(sub);

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("msg-retry"), channel);

        assertTrue("Should eventually succeed", sub.awaitSuccess(5, TimeUnit.SECONDS));
        assertTrue("Channel should close", channel.awaitClose(5, TimeUnit.SECONDS));
        assertEquals(3, sub.totalInvocations()); // 1 initial + 2 retries
    }

    @Test
    public void retryStopsAfterMaxCount() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-5");
        // Always fails — should stop after max retries (3)
        // Also add a matching subscription that succeeds to ensure the bus finishes
        FailingSubscription alwaysFails = new FailingSubscription("always-fails", Integer.MAX_VALUE, 0);
        RecordingSubscription passing = new RecordingSubscription("passing", msg -> true, 1);
        bus.subscribe(alwaysFails);
        bus.subscribe(passing);

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("msg-max-retry"), channel);

        assertTrue("Channel should close eventually", channel.awaitClose(5, TimeUnit.SECONDS));
        // Initial call + up to 3 retries = 4 max
        assertTrue("Should not exceed 4 invocations", alwaysFails.totalInvocations() <= 4);
    }

    @Test
    public void concurrentPublishDoesNotLoseMessages() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-6");
        int concurrentCount = 9;
        int totalMessages = 1 + concurrentCount;

        // First message processing takes 100ms, keeping the consumer in Phase 2
        // so concurrent publish() calls arrive while the turn is active.
        CopyOnWriteArrayList<EventMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch allReceived = new CountDownLatch(totalMessages);
        AtomicBoolean firstMessage = new AtomicBoolean(true);

        bus.subscribe(new Subscription() {
            @Override
            public String name() { return "collector"; }

            @Override
            public boolean concern(EventMessage message) { return true; }

            @Override
            public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
                if (firstMessage.compareAndSet(true, false)) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                received.add(message);
                callBack.onSuccess();
                allReceived.countDown();
            }

            @Override
            public void onSubscribe(EventMessageBus eventMessageBus) { }
        });

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("init"), channel);

        // Small delay to ensure consumer is in Phase 2 processing "init"
        Thread.sleep(20);

        // Fire concurrent publishes while consumer is still active
        CountDownLatch startGun = new CountDownLatch(1);
        for (int i = 1; i <= concurrentCount; i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                try { startGun.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                bus.publish(humanMessage("concurrent-" + idx));
            });
        }
        startGun.countDown();

        assertTrue("All messages should be received within 5s", allReceived.await(5, TimeUnit.SECONDS));
        assertEquals(totalMessages, received.size());
    }

    @Test
    public void noLostWakeUpUnderContention() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-7");
        // Slow subscription simulates a tool that takes 200ms
        SlowSubscription slowSub = new SlowSubscription("slow-tool", 200, 2);
        bus.subscribe(slowSub);

        LatchChatChannel channel1 = new LatchChatChannel();
        bus.talk(humanMessage("msg-A"), channel1);

        // Wait briefly for processing to start, then publish msg-B while msg-A is still processing
        Thread.sleep(50);
        bus.publish(humanMessage("msg-B"));

        assertTrue("Both messages should be delivered", slowSub.await(5, TimeUnit.SECONDS));
        assertEquals(2, slowSub.messages().size());
    }

    @Test
    public void subscribeAndUnsubscribe() {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-8");
        RecordingSubscription sub = new RecordingSubscription("temp", msg -> true, 0);

        assertTrue(bus.subscribe(sub));
        assertFalse("Duplicate subscribe should return false", bus.subscribe(sub));
        assertEquals(1, bus.subscriptions().size());

        assertTrue(bus.unsubscribe("temp"));
        assertFalse("Unsubscribe non-existent should return false", bus.unsubscribe("temp"));
        assertEquals(0, bus.subscriptions().size());
    }

    @Test
    public void virtualThreadsUsed() throws Exception {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-9");
        AtomicBoolean wasVirtual = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribe(new Subscription() {
            @Override
            public String name() { return "vt-checker"; }

            @Override
            public boolean concern(EventMessage message) { return true; }

            @Override
            public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
                wasVirtual.set(Thread.currentThread().isVirtual());
                callBack.onSuccess();
                latch.countDown();
            }

            @Override
            public void onSubscribe(EventMessageBus eventMessageBus) { }
        });

        LatchChatChannel channel = new LatchChatChannel();
        bus.talk(humanMessage("vt-test"), channel);

        assertTrue("Should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Tool execution should run on virtual thread", wasVirtual.get());
    }

    @Test
    public void publishNullMessageIsIgnored() {
        TestEventMessageBus bus = new TestEventMessageBus("test-session-10");
        // Should not throw
        bus.publish(null);
    }
}
