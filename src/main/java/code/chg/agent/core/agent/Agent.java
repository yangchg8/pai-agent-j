package code.chg.agent.core.agent;

import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title Agent
 * @description Defines the high-level agent interaction contract.
 */
public interface Agent {
    /**
     * Sends a user message to the agent.
     */
    void talk(String message, ChannelSubscriber channelSubscriber);

    /**
     * Sends an authorization response back to the agent.
     */
    void authorize(AuthorizationResponseEventBody result, ChannelSubscriber channelSubscriber);
}
