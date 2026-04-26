package code.chg.agent.core.brain.state;

import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PersistentBrainMemoryItem
 * @description Serializable representation of one brain memory region and its persisted messages.
 */
@Data
public class PersistentBrainMemoryItem {
    String brainName;
    String memoryRegionName;
    PersistentLLMMessage message;

    private static final ObjectMapper NDJSON_MAPPER = JsonUtil.getObjectMapper()
            .copy()
            .disable(SerializationFeature.INDENT_OUTPUT);

    public static List<PersistentBrainMemoryItem> decode(byte[] data) {
        if (data == null || data.length == 0) {
            return Collections.emptyList();
        }
        String content = new String(data, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return Collections.emptyList();
        }

        List<PersistentBrainMemoryItem> items = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = NDJSON_MAPPER.readTree(line);
                if (node.has("message")) {
                    items.add(NDJSON_MAPPER.treeToValue(node, PersistentBrainMemoryItem.class));
                    continue;
                }

                PersistentBrainMemoryItem item = new PersistentBrainMemoryItem();
                item.setMessage(NDJSON_MAPPER.treeToValue(node, PersistentLLMMessage.class));
                items.add(item);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decode persistent brain memory items", e);
            }
        }
        return items;
    }

    public static byte[] encode(List<PersistentBrainMemoryItem> llmMessages) {
        if (llmMessages == null || llmMessages.isEmpty()) {
            return new byte[0];
        }

        try {
            String jsonLines = llmMessages.stream()
                    .map(message -> {
                        try {
                            return NDJSON_MAPPER.writeValueAsString(message);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to encode persistent llm message", e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
            return jsonLines.getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw e;
        }
    }


}
