package code.chg.agent.core.event;

import code.chg.agent.core.channel.ChatChannel;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageBus
 * @description Aggregate root for the event bus.
 */
public interface EventMessageBus {

    /**
     * Returns the current session ID.
     *
     * @return the session ID
     */
    String sessionId();

    /**
     * Enqueues a message for processing.
     *
     * @param message the event message
     */
    void talk(EventMessage message, ChatChannel channel);

    /**
     * Publishes a message directly to the bus.
     *
     * @param message the event message
     */
    void publish(EventMessage message);

    /**
     * Registers a subscription.
     *
     * @param subscription the subscription to register
     */
    boolean subscribe(Subscription subscription);

    /**
     * UnRegisters a subscription.
     *
     * @param name the name of subscription
     */
    boolean unsubscribe(String name);

    default List<SubscriptionDescriptor> subscriptions() {
        return List.of();
    }
}
