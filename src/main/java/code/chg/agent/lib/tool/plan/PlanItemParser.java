package code.chg.agent.lib.tool.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PlanItemParser
 * @description Parses a JSON array of plan items.
 */
class PlanItemParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<PlanItem> parse(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        if (!root.isArray()) {
            throw new IllegalArgumentException("plan must be a JSON array");
        }
        List<PlanItem> items = new ArrayList<>();
        for (JsonNode node : root) {
            String step = node.path("step").asText(null);
            String status = node.path("status").asText("pending");
            if (step == null || step.isBlank()) {
                throw new IllegalArgumentException("Each plan item must have a non-empty 'step'");
            }
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Invalid status '" + status + "' — must be: pending | in_progress | completed");
            }
            items.add(new PlanItem(step, status));
        }
        return items;
    }
}
