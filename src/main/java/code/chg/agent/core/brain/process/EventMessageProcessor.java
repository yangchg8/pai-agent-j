package code.chg.agent.core.brain.process;

import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageProcessor
 * @description Processor contract for a stage within the event-message execution pipeline.
 */
public interface EventMessageProcessor {
    EventMessageResponse process(EventMessage message, EventBusContext context, EventMessageProcessorChain chain);
}
