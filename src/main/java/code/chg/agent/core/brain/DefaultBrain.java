package code.chg.agent.core.brain;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;
import code.chg.agent.core.event.body.AuthorizationRequestEventBody;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.infa.openai.OpenAIClientFactory;
import code.chg.agent.llm.LLMClient;
import code.chg.agent.llm.LLMClientFactory;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title DefaultBrain
 * @description Default brain implementation backed by the configured LLM client.
 */
public class DefaultBrain extends AbstractBrain {
    private final LLMClientFactory factory;
    @Setter
    private List<MemoryRegion> memoryRegions;
    @Setter
    private BrainHook brainHook;

    public DefaultBrain(String name) {
        super(name);
        this.factory = new OpenAIClientFactory();
        this.brainHook = null;
    }

    @Override
    public BrainHook brainHook() {
        return brainHook;
    }

    @Override
    public boolean concern(EventMessage message) {
        if (message.type() == EventMessageType.HUMAN_MESSAGE) {
            return true;
        }
        if (message.type() == EventMessageType.TOOL_CALL_RESPONSE) {
            String id = ((ToolEventBody) message.body()).toolCallId();
            return !this.getBrainRunningState().isFinishedTool(id);
        }
        if (message.type() == EventMessageType.TOOL_AUTHORIZATION_REQUEST) {
            String id = ((AuthorizationRequestEventBody) message.body()).getToolCallId();
            return !this.getBrainRunningState().isFinishedTool(id);
        }
        if (message.type() == EventMessageType.TOOL_AUTHORIZATION_RESPONSE) {
            String id = ((AuthorizationResponseEventBody) message.body()).authorizationId();
            return this.getBrainRunningState().isAuthorizingStatus(id);
        }
        return false;
    }


    @Override
    public LLMClient client() {
        return factory.getClient();
    }

    @Override
    public List<MemoryRegion> getMemoryRegions() {
        if (memoryRegions == null) {
            return Collections.emptyList();
        }
        return memoryRegions;
    }
}
