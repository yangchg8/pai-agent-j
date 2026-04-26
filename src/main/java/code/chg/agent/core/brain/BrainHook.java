package code.chg.agent.core.brain;

import code.chg.agent.core.event.EventBusContext;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title BrainHook
 * @description Hook contract for brain-level cleanup and lifecycle extensions.
 */
public interface BrainHook {
    void cleanUp(AbstractBrain brain, EventBusContext context);
}
