package code.chg.agent.core.brain.process;

import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageProcessorChain
 * @description Chain abstraction used to advance to the next event-message processor.
 */
public interface EventMessageProcessorChain {
    EventMessageResponse next(EventMessage message, EventBusContext context);
}
