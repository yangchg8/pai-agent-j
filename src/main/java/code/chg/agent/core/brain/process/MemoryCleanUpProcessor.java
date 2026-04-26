package code.chg.agent.core.brain.process;

import code.chg.agent.core.brain.AbstractBrain;
import code.chg.agent.core.brain.BrainHook;
import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MemoryCleanUpProcessor
 * @description Processor that runs brain-level cleanup hooks before the next stage.
 */
public class MemoryCleanUpProcessor implements EventMessageProcessor {
    private final AbstractBrain brain;

    public MemoryCleanUpProcessor(AbstractBrain brain) {
        this.brain = brain;
    }

    @Override
    public EventMessageResponse process(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        BrainHook brainHook = brain.brainHook();
        if (brainHook != null) {
            brainHook.cleanUp(brain, context);
        }
        return chain.next(message, context);
    }
}
