package code.chg.agent.core.event;

import code.chg.agent.core.channel.ChatChannel;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventBusContext
 * @description Provides contextual services during event processing.
 */
public interface EventBusContext {
    /**
     * Returns the chat channel associated with the current event flow.
     */
    ChatChannel chat();
}
