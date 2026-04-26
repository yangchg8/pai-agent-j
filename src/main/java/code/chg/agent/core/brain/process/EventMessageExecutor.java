package code.chg.agent.core.brain.process;

import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageExecutor
 * @description Executes the ordered processor chain for a single event message.
 */
public class EventMessageExecutor {
    List<EventMessageProcessor> processors;

    public EventMessageExecutor() {
        processors = new ArrayList<>();
    }

    public void register(EventMessageProcessor processor) {
        processors.add(processor);
    }

    public void execute(EventMessage message, EventBusContext context) {
        new InnerEventMessageProcessorChain(processors).next(message, context);
    }

    private static class InnerEventMessageProcessorChain implements EventMessageProcessorChain {
        private final List<EventMessageProcessor> processors;
        private int lastProcessorIndex;

        public InnerEventMessageProcessorChain(List<EventMessageProcessor> processors) {
            this.processors = processors;
            this.lastProcessorIndex = -1;
        }

        @Override
        public EventMessageResponse next(EventMessage message, EventBusContext context) {
            if (lastProcessorIndex + 1 < processors.size()) {
                lastProcessorIndex++;
                return processors.get(lastProcessorIndex).process(message, context, this);
            }
            return null;
        }
    }
}
