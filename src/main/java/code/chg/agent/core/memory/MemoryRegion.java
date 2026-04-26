package code.chg.agent.core.memory;

import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.llm.LLMMessage;

import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MemoryRegion
 * @description Memory region abstraction.
 */
public interface MemoryRegion {
    /**
     * Returns the memory region name.
     */
    String getName();


    /**
     * Returns the region as a list of LLM messages.
     */
    List<LLMMessage> messages();

    /**
     * Restores the region from persisted messages.
     */
    default void restore(List<PersistentLLMMessage> llmMessages) {

    }

    /**
     * Returns the persisted state for this region.
     */
    default List<PersistentLLMMessage> getState() {
        return Collections.emptyList();
    }

    /**
     * Returns the tools bound to this region.
     */
    default List<Tool> tools() {
        return Collections.emptyList();
    }


    /**
     * Returns the lifecycle hook for this region, if any.
     */
    default MemoryRegionHook getHook() {
        return null;
    }
}
