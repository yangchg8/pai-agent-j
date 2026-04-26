package code.chg.agent.core.brain.process;

import code.chg.agent.core.brain.AbstractBrain;
import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.memory.MemoryRegionHook;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MemoryRegionProcessor
 * @description Processor that updates memory-region hooks before and after model interaction.
 */
public class MemoryRegionProcessor implements EventMessageProcessor {
    private final AbstractBrain brain;

    public MemoryRegionProcessor(AbstractBrain brain) {
        this.brain = brain;
    }

    @Override
    public EventMessageResponse process(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        List<MemoryRegion> memoryRegions = Objects.requireNonNullElse(brain.getMemoryRegions(),
                Collections.emptyList());
        for (MemoryRegion memoryRegion : memoryRegions) {
            MemoryRegionHook hook = memoryRegion.getHook();
            if (hook != null) {
                hook.onEventMessage(message);
            }
        }
        EventMessageResponse response = chain.next(message, context);
        if (response != null && response.getMessages() != null) {
            for (MemoryRegion memoryRegion : memoryRegions) {
                MemoryRegionHook hook = memoryRegion.getHook();
                if (hook != null) {
                    hook.afterEventMessage(response.getMessages());
                }
            }
        }
        return response;
    }
}
