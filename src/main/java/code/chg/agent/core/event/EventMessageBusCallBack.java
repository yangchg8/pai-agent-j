package code.chg.agent.core.event;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageBusCallBack
 * @description Defines completion callbacks for event-bus message handling.
 */
public interface EventMessageBusCallBack {
    /**
     * Marks the current message handling attempt as successful.
     */
    void onSuccess();

    /**
     * Marks the current message handling attempt as failed.
     *
     * @param errorMessage the failure summary
     * @param throwable    the underlying cause
     */
    void onFailure(String errorMessage, Throwable throwable);
}
