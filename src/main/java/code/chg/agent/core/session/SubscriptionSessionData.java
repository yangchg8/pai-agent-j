package code.chg.agent.core.session;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SubscriptionSessionData
 * @description Persisted state blob for a single subscription.
 */
@Data
public class SubscriptionSessionData {
    String subscriptionName;
    byte[] data;
}
