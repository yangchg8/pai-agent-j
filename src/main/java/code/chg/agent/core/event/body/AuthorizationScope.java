package code.chg.agent.core.event.body;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AuthorizationScope
 * @description Authorization scope describing how long a grant remains valid.
 */
public enum AuthorizationScope {
    /**
     * Allow this request only, without persisting permissions.
     */
    ONCE,

    /**
     * Persist the granted permissions to the session, then execute.
     */
    GRANT,
    /**
     * Reject the request.
     */
    REJECTED,
    /**
     * Continue execution for this request while bypassing permission checks.
     */
    CONTINUE
}
