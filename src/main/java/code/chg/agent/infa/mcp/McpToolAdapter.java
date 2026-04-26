package code.chg.agent.infa.mcp;

import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageBus;
import code.chg.agent.core.event.EventMessageBusCallBack;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.event.message.ToolEventMessage;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.llm.ToolParameterBaseType;
import code.chg.agent.llm.ToolParameterDefinition;
import code.chg.agent.llm.ToolParameterType;
import code.chg.agent.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.*;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title McpToolAdapter
 * @description Adapts discovered MCP tools into the local tool contract.
 */
@Slf4j
public class McpToolAdapter {

    private final McpServerConfig config;

    public McpToolAdapter(McpServerConfig config) {
        this.config = config;
    }


    public List<Tool> discoverTools() {
        McpClientSession client = null;
        try {
            client = createClient(config);
            List<McpSchema.Tool> toolDefs = client.listTools();
            if (toolDefs.isEmpty()) {
                client.close();
                return Collections.emptyList();
            }
            List<Tool> tools = new ArrayList<>(toolDefs.size());
            for (McpSchema.Tool toolDef : toolDefs) {
                tools.add(new McpTool(
                        toolDef.name(),
                        toolDef.description(),
                        parseParameters(toolDef.inputSchema()),
                        client));
            }
            log.info("Discovered {} tools from MCP server '{}'", tools.size(), config.getName());
            return tools;
        } catch (Exception e) {
            closeQuietly(client);
            log.error("Failed to discover MCP tools from server '{}'", config.getName(), e);
            return Collections.emptyList();
        }
    }

    private McpClientSession createClient(McpServerConfig config) {
        return new McpClientSession(config);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private List<ToolParameterDefinition> parseParameters(McpSchema.JsonSchema inputSchema) {
        List<ToolParameterDefinition> result = new ArrayList<>();
        if (inputSchema == null || inputSchema.properties() == null || inputSchema.properties().isEmpty()) {
            return result;
        }
        Set<String> required = new HashSet<>(Optional.ofNullable(inputSchema.required()).orElse(List.of()));
        inputSchema.properties().forEach((paramName, rawSchema) -> {
            Map<String, Object> propNode = asMap(rawSchema);
            String desc = asString(propNode.get("description"));
            ToolParameterType type = parseType(propNode);
            result.add(new McpParameterDefinition(paramName, desc, required.contains(paramName), type));
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ToolParameterType parseType(Map<String, Object> propNode) {
        String typeStr = Optional.ofNullable(asString(propNode.get("type"))).orElse("string");
        ToolParameterBaseType baseType = switch (typeStr) {
            case "boolean" -> ToolParameterBaseType.BOOLEAN;
            case "integer" -> ToolParameterBaseType.INTEGER;
            case "number" -> ToolParameterBaseType.NUMBER;
            case "array" -> ToolParameterBaseType.ARRAY;
            case "object" -> ToolParameterBaseType.OBJECT;
            default -> ToolParameterBaseType.STRING;
        };
        ToolParameterType type = new ToolParameterType(baseType);
        if (baseType == ToolParameterBaseType.OBJECT) {
            Object nestedProps = propNode.get("properties");
            if (nestedProps instanceof Map<?, ?> nestedMap) {
                Map<String, ToolParameterType> props = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                    if (entry.getKey() != null) {
                        props.put(String.valueOf(entry.getKey()), parseType(asMap(entry.getValue())));
                    }
                }
                type.setProperties(props);
            }
        } else if (baseType == ToolParameterBaseType.ARRAY) {
            Object items = propNode.get("items");
            if (items != null) {
                type.setItems(parseType(asMap(items)));
            }
        }
        return type;
    }


    private record McpParameterDefinition(
            String name,
            String description,
            boolean required,
            ToolParameterType type
    ) implements ToolParameterDefinition {
    }


    private static class McpTool implements Tool {

        private final String name;
        private final String description;
        private final List<ToolParameterDefinition> params;
        private final McpClientSession mcpClient;
        private EventMessageBus eventMessageBus;

        McpTool(String name, String description, List<ToolParameterDefinition> params, McpClientSession mcpClient) {
            this.name = name;
            this.description = description;
            this.params = params;
            this.mcpClient = mcpClient;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public List<ToolParameterDefinition> parameters() {
            return params;
        }

        @Override
        public boolean concern(EventMessage message) {
            if (message.type() != EventMessageType.TOOL_CALL_REQUEST) {
                return false;
            }
            ToolEventMessage toolMsg = (ToolEventMessage) message;
            return name.equals(toolMsg.getBody().name());
        }

        @Override
        public void onMessage(EventMessage message, EventBusContext context, EventMessageBusCallBack callBack) {
            ToolEventBody toolEventBody = (ToolEventBody) message.body();
            String result;
            try {
                String rawArgs = toolEventBody.arguments();
                Map<String, Object> arguments = (rawArgs != null && !rawArgs.isBlank())
                        ? JsonUtil.getObjectMapper().readValue(rawArgs, new TypeReference<>() {
                })
                        : Collections.emptyMap();
                result = mcpClient.callTool(name, arguments);
            } catch (Exception e) {
                log.error("MCP tool '{}' execution failed", name, e);
                result = "MCP tool error: " + e.getMessage();
            }
            eventMessageBus.publish(new ToolEventMessage(toolEventBody.getToolCall(), result));
            callBack.onSuccess();
        }

        @Override
        public void onSubscribe(EventMessageBus eventMessageBus) {
            this.eventMessageBus = eventMessageBus;
        }

        @Override
        public ToolPermissionResult checkPermission(ToolPermissionPolicy policy, Object[] arguments) {
            return ToolPermissionResultFactory.granted();
        }
    }
}
