package code.chg.agent.lib.tool.plan;

import lombok.Data;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PlanToolResult
 * @description Result returned by PlanTool.
 */
@Data
public class PlanToolResult {

    private boolean success;
    private String explanation;
    private List<PlanItem> items;
    private String errorMessage;

    private PlanToolResult() {
    }

    public static PlanToolResult ok(String explanation, List<PlanItem> items) {
        PlanToolResult r = new PlanToolResult();
        r.success = true;
        r.explanation = explanation;
        r.items = items;
        return r;
    }

    public static PlanToolResult error(String errorMessage) {
        PlanToolResult r = new PlanToolResult();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    @Override
    public String toString() {
        if (!success) return errorMessage;
        StringBuilder sb = new StringBuilder();
        if (explanation != null && !explanation.isBlank()) {
            sb.append(explanation).append("\n\n");
        }
        sb.append("Plan:\n");
        for (int i = 0; i < items.size(); i++) {
            PlanItem item = items.get(i);
            sb.append(item.statusEmoji()).append(" ").append(i + 1).append(". ").append(item.step()).append("\n");
        }
        return sb.toString().trim();
    }
}
