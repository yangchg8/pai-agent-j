package code.chg.agent.core.brain;

import code.chg.agent.core.brain.process.*;
import code.chg.agent.core.brain.state.PersistentBrainMemoryItem;
import code.chg.agent.core.event.*;
import code.chg.agent.core.event.message.ToolEventMessage;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.llm.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AbstractBrain
 * @description Base implementation for stateful brains that coordinate the reasoning pipeline.
 */
@Slf4j
@Getter
public abstract class AbstractBrain implements StateSubscription {
    private final String name;
    private final BrainRunningState brainRunningState;
    private EventMessageBus eventMessageBus;
    private final EventMessageExecutor executor;

    public AbstractBrain(String name) {
        this.name = name;
        this.brainRunningState = new BrainRunningState();
        this.executor = new EventMessageExecutor();
        registerDefaultProcessors();
    }

    protected void registerDefaultProcessors() {

        executor.register(new MemoryRegionProcessor(this));
        executor.register(new BrainRunningStateProcessor(this));
        executor.register(new MemoryCleanUpProcessor(this));
        executor.register(new LLMCallProcessor(this));
    }

    public abstract BrainHook brainHook();

    public abstract LLMClient client();

    public abstract List<MemoryRegion> getMemoryRegions();


    @Override
    public String name() {
        return name;
    }

    @Override
    public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
        try {
            executor.execute(message, context);
            if (callBack != null) {
                callBack.onSuccess();
            }
        } catch (Exception e) {
            if (callBack != null) {
                callBack.onFailure(e.getMessage(), e);
            }
        }
    }

    @Override
    public void onSubscribe(EventMessageBus eventMessageBus) {
        this.eventMessageBus = eventMessageBus;
    }

    public void publish(ToolEventMessage message) {
        if (this.eventMessageBus == null) {
            throw new RuntimeException("eventMessageBus is null");
        }
        this.eventMessageBus.publish(message);
    }

    @Override
    public void onIntercept() {
        return;
    }

    @Override
    public void restore(byte[] state) {
        List<MemoryRegion> memoryRegions = getMemoryRegions();
        if (memoryRegions == null || memoryRegions.isEmpty()) {
            return;
        }
        List<PersistentBrainMemoryItem> persistentBrainMemoryItems = PersistentBrainMemoryItem.decode(state);
        persistentBrainMemoryItems = persistentBrainMemoryItems.stream()
                .filter(item -> this.name.equals(item.getBrainName()))
                .toList();
        if (persistentBrainMemoryItems.isEmpty()) {
            return;
        }
        Map<String, List<PersistentBrainMemoryItem>> memoryRegionToItems = persistentBrainMemoryItems.stream()
                .filter(item -> item.getMemoryRegionName() != null)
                .collect(Collectors.groupingBy(PersistentBrainMemoryItem::getMemoryRegionName));
        for (MemoryRegion memoryRegion : memoryRegions) {
            List<PersistentBrainMemoryItem> items = memoryRegionToItems.get(memoryRegion.getName());
            if (items == null || items.isEmpty()) {
                continue;
            }
            memoryRegion.restore(items.stream()
                    .map(PersistentBrainMemoryItem::getMessage)
                    .filter(Objects::nonNull)
                    .toList());
        }

    }

    @Override
    public byte[] getState() {
        List<MemoryRegion> memoryRegions = getMemoryRegions();
        if (memoryRegions == null || memoryRegions.isEmpty()) {
            return null;
        }
        List<PersistentBrainMemoryItem> items = memoryRegions.stream()
                .flatMap(region -> region.getState().stream()
                        .map(message -> {
                            PersistentBrainMemoryItem item = new PersistentBrainMemoryItem();
                            item.setBrainName(this.name);
                            item.setMemoryRegionName(region.getName());
                            item.setMessage(message);
                            return item;
                        }))
                .toList();
        return PersistentBrainMemoryItem.encode(items);
    }
}
