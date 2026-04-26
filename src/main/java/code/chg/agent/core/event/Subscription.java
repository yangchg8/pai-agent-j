package code.chg.agent.core.event;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title Subscription
 * @description Subscription interface linking a subscriber to its matching rules.
 */
public interface Subscription {
    String name();

    boolean concern(EventMessage message);

    void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack);

    void onSubscribe(EventMessageBus eventMessageBus);
}
