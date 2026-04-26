package code.chg.agent.lib.memory;

import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.lib.tool.plan.PlanTool;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.message.ContentLLMMessage;
import code.chg.agent.utils.ToolUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PlanMemoryRegion
 * @description Provides the PlanMemoryRegion implementation.
 */
public class PlanMemoryRegion implements MemoryRegion {
    private final List<Tool> tools;
    private final PlanTool planTool;

    public PlanMemoryRegion() {
        PlanTool planTool = new PlanTool();
        this.planTool = planTool;
        this.tools = ToolUtil.buildTools(PlanTool.class, planTool);
    }

    @Override
    public String getName() {
        return "PLAN_MEMORY_REGION";
    }

    @Override
    public List<Tool> tools() {
        return tools;
    }

    @Override
    public List<LLMMessage> messages() {
        if (!planTool.hasActivePlan()) {
            return Collections.emptyList();
        }
        String planText = planTool.formatPlan();
        if (planText == null || planText.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(ContentLLMMessage.of(planTool.getPlanId(), MessageType.SYSTEM, planText));
    }

    @Override
    public void restore(List<PersistentLLMMessage> llmMessages) {
        if (llmMessages == null || llmMessages.isEmpty()) {
            return;
        }
        planTool.setRecoveredFormatPlan(llmMessages.getFirst().getContent());
        planTool.setPlanId(llmMessages.getFirst().id());
    }

    @Override
    public List<PersistentLLMMessage> getState() {
        return messages().stream().map(PersistentLLMMessage::new).toList();
    }

}
