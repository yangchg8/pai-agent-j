package code.chg.agent.core.session;

import lombok.Data;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionData
 * @description Persisted session snapshot and metadata container.
 */
@Data
public class SessionData {
    String sessionId;
    String title;
    Long createdAt;
    Long updatedAt;
    Long lastActiveAt;
    String latestUserMessage;
    Integer latestTokenCount;
    List<SubscriptionSessionData> subscriptionSessionData;
}
