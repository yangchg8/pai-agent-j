package code.chg.agent.lib.tool.plan;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PlanItem
 * @description A single plan step with its current status.
 */
public record PlanItem(String step, String status) {

    public static final String PENDING = "pending";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public boolean isInProgress() {
        return IN_PROGRESS.equals(status);
    }

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }

    public String statusEmoji() {
        return switch (status) {
            case IN_PROGRESS -> "⏳";
            case COMPLETED -> "✅";
            default -> "🔲";
        };
    }
}
