package code.chg.agent.core.event;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title StateSubscription
 * @description Defines a subscription that can persist and restore its state.
 */
public interface StateSubscription extends Subscription {
    /**
     * Runs before state is collected from the subscription.
     */
    void onIntercept();

    /**
     * Restores the subscription state from a serialized payload.
     *
     * @param state the serialized state bytes
     */
    void restore(byte[] state);

    /**
     * Returns the serialized state for this subscription.
     *
     * @return the serialized state bytes
     */
    byte[] getState();
}
