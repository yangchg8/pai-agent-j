package code.chg.agent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PaiAgentConfig
 * @description Central YAML configuration loader for pai-agent.
 */
public final class PaiAgentConfig {

    static final String CONFIG_PATH_PROPERTY = "pai.agent.config.path";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static volatile RootConfig cachedConfig;

    private PaiAgentConfig() {
    }

    public static RootConfig get() {
        RootConfig config = cachedConfig;
        if (config != null) {
            return config;
        }
        synchronized (PaiAgentConfig.class) {
            if (cachedConfig == null) {
                cachedConfig = load();
            }
            return cachedConfig;
        }
    }

    static void reload() {
        synchronized (PaiAgentConfig.class) {
            cachedConfig = null;
        }
    }

    public static Path resolveConfigPath() {
        String configuredPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".pai-agent", "config.yml");
    }

    private static RootConfig load() {
        Path configPath = resolveConfigPath();
        if (!Files.exists(configPath)) {
            throw new RuntimeException("Configuration file not found: " + configPath
                    + ". Please create ~/.pai-agent/config.yml or copy ./config.yml.");
        }
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            RootConfig config = YAML_MAPPER.readValue(inputStream, RootConfig.class);
            config.applyDefaults();
            validate(config, configPath);
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration: " + configPath, e);
        }
    }

    private static void validate(RootConfig config, Path configPath) {
        if (isBlank(config.getOpenai().getApi().getKey())) {
            throw new RuntimeException("openai.api.key is not configured in " + configPath);
        }
        if (isBlank(config.getOpenai().getApi().getBaseUrl())) {
            throw new RuntimeException("openai.api.baseUrl is not configured in " + configPath);
        }
        if (isBlank(config.getOpenai().getModel().getName())) {
            throw new RuntimeException("openai.model.name is not configured in " + configPath);
        }
        if (config.getOpenai().getModel().getToken() == null || config.getOpenai().getModel().getToken() <= 0) {
            throw new RuntimeException("openai.model.token must be greater than 0 in " + configPath);
        }
        if (config.getMemory().getCompaction().getContextWindowTokens() <= 0) {
            throw new RuntimeException("memory.compaction.contextWindowTokens must be greater than 0 in " + configPath);
        }
        double triggerThreshold = config.getMemory().getCompaction().getTriggerThreshold();
        if (triggerThreshold <= 0 || triggerThreshold > 1) {
            throw new RuntimeException("memory.compaction.triggerThreshold must be in (0, 1] in " + configPath);
        }
        if (config.getMemory().getCompaction().getRetainedUserMessageMaxTokens() <= 0) {
            throw new RuntimeException("memory.compaction.retainedUserMessageMaxTokens must be greater than 0 in " + configPath);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class RootConfig {
        private OpenAISection openai = new OpenAISection();
        private MemorySection memory = new MemorySection();
        private McpSection mcp = new McpSection();

        public void applyDefaults() {
            if (openai == null) {
                openai = new OpenAISection();
            }
            if (memory == null) {
                memory = new MemorySection();
            }
            if (mcp == null) {
                mcp = new McpSection();
            }
            openai.applyDefaults();
            memory.applyDefaults();
            mcp.applyDefaults();
        }
    }

    @Data
    public static class OpenAISection {
        private ApiSection api = new ApiSection();
        private ModelSection model = new ModelSection();
        private ToolSection tool = new ToolSection();

        public void applyDefaults() {
            if (api == null) {
                api = new ApiSection();
            }
            if (model == null) {
                model = new ModelSection();
            }
            if (tool == null) {
                tool = new ToolSection();
            }
            api.applyDefaults();
            model.applyDefaults();
            tool.applyDefaults();
        }
    }

    @Data
    public static class ApiSection {
        private String key;
        private String baseUrl;

        public void applyDefaults() {
        }
    }

    @Data
    public static class ModelSection {
        private String name;
        private Integer token = 128_000;

        public void applyDefaults() {
            if (token == null || token <= 0) {
                token = 128_000;
            }
        }
    }

    @Data
    public static class ToolSection {
        private boolean parallelCalls = true;
        private int maxInlineResponseChars = 12_000;
        private String oversizedResponseDir;

        public void applyDefaults() {
            if (maxInlineResponseChars <= 0) {
                maxInlineResponseChars = 12_000;
            }
            if (oversizedResponseDir == null || oversizedResponseDir.isBlank()) {
                Path path = Path.of(System.getProperty("java.io.tmpdir"), "tool_results")
                        .toAbsolutePath()
                        .normalize();
                try {
                    Files.createDirectories(path);
                    oversizedResponseDir = path.toString();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create default oversizedResponseDir: " + path, e);
                }
            }
        }
    }

    @Data
    public static class MemorySection {
        private CompactionSection compaction = new CompactionSection();

        public void applyDefaults() {
            if (compaction == null) {
                compaction = new CompactionSection();
            }
            compaction.applyDefaults();
        }
    }

    @Data
    public static class CompactionSection {
        private int contextWindowTokens = 128_000;
        private double triggerThreshold = 0.9D;
        private int retainedUserMessageMaxTokens = 20_000;

        public void applyDefaults() {
        }
    }

    @Data
    public static class McpSection {
        private java.util.List<McpServerSection> servers = new java.util.ArrayList<>();

        public void applyDefaults() {
            if (servers == null) {
                servers = new java.util.ArrayList<>();
            }
            servers.forEach(McpServerSection::applyDefaults);
        }
    }

    @Data
    public static class McpServerSection {
        private String name;
        private String transport = "stdio";
        private String command;
        private java.util.List<String> args = new java.util.ArrayList<>();
        private java.util.Map<String, String> env = new java.util.LinkedHashMap<>();
        private String url;
        private Long timeoutMs = 30_000L;
        private Boolean autoLoad = Boolean.FALSE;

        public void applyDefaults() {
            if (transport == null || transport.isBlank()) {
                transport = "stdio";
            }
            if (args == null) {
                args = new java.util.ArrayList<>();
            }
            if (env == null) {
                env = new java.util.LinkedHashMap<>();
            }
            if (timeoutMs == null || timeoutMs <= 0) {
                timeoutMs = 30_000L;
            }
            if (autoLoad == null) {
                autoLoad = Boolean.FALSE;
            }
        }
    }
}