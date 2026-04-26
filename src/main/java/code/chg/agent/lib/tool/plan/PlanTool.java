package code.chg.agent.lib.tool.plan;

import code.chg.agent.annotation.Tool;
import code.chg.agent.annotation.ToolParameter;
import code.chg.agent.utils.MessageIdGenerator;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PlanTool
 * @description Plan management tool — maintains a task plan list with step-level status tracking.
 */
@Data
public class PlanTool {

    private static final String DESCRIPTION = """
            Updates the current task plan.
            Provide an explanation and a list of plan items, each with a step description and a status.
            Status values: pending | in_progress | completed.
            At most one step may be in_progress at a time.
            Use this tool to:
            - Create an initial plan after receiving a complex task
            - Update step statuses as you make progress
            - Mark steps completed as you finish them
            Skip using the plan tool for simple, single-step tasks.""";
    private String planId;
    private String recoveredFormatPlan = null;
    private List<PlanItem> plan = Collections.emptyList();
    private String explanation = null;

    @Tool(name = "update_plan", description = DESCRIPTION)
    public PlanToolResult updatePlan(
            @ToolParameter(name = "explanation", description = "Brief explanation of why the plan is being updated.")
            String explanation,
            @ToolParameter(name = "plan", description = """
                    JSON array of plan items. Each item must have:
                    - "step": string description of the step
                    - "status": one of pending | in_progress | completed""")
            String planJson
    ) {
        if (planJson == null || planJson.isBlank()) {
            return PlanToolResult.error("Error: plan must not be empty");
        }
        try {
            List<PlanItem> items = PlanItemParser.parse(planJson);
            long inProgressCount = items.stream()
                    .filter(i -> "in_progress".equals(i.status())).count();
            if (inProgressCount > 1) {
                return PlanToolResult.error("Error: at most one step may be in_progress at a time");
            }
            update(explanation, items);
            return PlanToolResult.ok(explanation, items);
        } catch (Exception e) {
            return PlanToolResult.error("Error parsing plan: " + e.getMessage());
        }
    }


    public void update(String explanation, List<PlanItem> items) {
        this.explanation = explanation;
        this.plan = new CopyOnWriteArrayList<>(items);
        this.recoveredFormatPlan = null;
        this.planId = MessageIdGenerator.generateWithPrefix("plan_");
    }

    public boolean hasActivePlan() {
        return plan != null && !plan.isEmpty();
    }


    public String formatPlan() {
        if (recoveredFormatPlan != null) {
            return recoveredFormatPlan;
        }
        if (plan == null || plan.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<system_reminder>\n");
        if (explanation != null && !explanation.isBlank()) {
            sb.append("## Current Task Plan\n").append(explanation).append("\n\n");
        } else {
            sb.append("## Current Task Plan\n");
        }
        for (int i = 0; i < plan.size(); i++) {
            PlanItem item = plan.get(i);
            sb.append(item.statusEmoji()).append(" ").append(i + 1).append(". ").append(item.step()).append("\n");
        }
        sb.append("\n</system_reminder>");
        return sb.toString();
    }

}
