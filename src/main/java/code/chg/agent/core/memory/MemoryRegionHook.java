package code.chg.agent.core.memory;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.llm.LLMMessage;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MemoryRegionHook
 * @description Lifecycle hook for a memory region.
 */
public interface MemoryRegionHook {

    void onEventMessage(EventMessage message);

    void afterEventMessage(List<LLMMessage> messages);

}
