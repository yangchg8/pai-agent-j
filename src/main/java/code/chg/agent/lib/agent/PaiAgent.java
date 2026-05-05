package code.chg.agent.lib.agent;

import code.chg.agent.config.OpenAIConfig;
import code.chg.agent.config.PaiAgentConfig;
import code.chg.agent.core.agent.Agent;
import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelMessageBody;
import code.chg.agent.core.channel.ChannelMessageBodyCopier;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.core.channel.body.AuthorizationRequestChannelMessage;
import code.chg.agent.core.channel.body.CompactNoticeChannelMessage;
import code.chg.agent.core.channel.body.TokenUsageChannelMessage;
import code.chg.agent.core.channel.body.ToolCallRejectedChannelMessage;
import code.chg.agent.core.channel.body.ToolCallResponseChannelMessage;
import code.chg.agent.core.event.SubscriptionDescriptor;
import code.chg.agent.core.event.BaseEventMessageBus;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;
import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.session.HistoryMessageData;
import code.chg.agent.core.session.SessionData;
import code.chg.agent.core.session.SessionSummaryData;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.infa.mcp.McpServerConfig;
import code.chg.agent.infa.mcp.McpToolAdapter;
import code.chg.agent.lib.event.LocalEventMessageBus;
import code.chg.agent.lib.memory.ChatMemoryRegion;
import code.chg.agent.lib.memory.McpToolMemoryRegion;
import code.chg.agent.lib.memory.PlanMemoryRegion;
import code.chg.agent.lib.memory.SkillMemoryRegion;
import code.chg.agent.lib.react.ReactAgent;
import code.chg.agent.lib.session.LocalSessionStoreManager;
import code.chg.agent.lib.session.SessionRuntimeContext;
import code.chg.agent.lib.skill.SkillMetadata;
import code.chg.agent.lib.skill.SkillsManager;
import code.chg.agent.lib.tool.file.FileTool;
import code.chg.agent.lib.tool.patch.ApplyPatchTool;
import code.chg.agent.lib.tool.shell.ShellTool;
import code.chg.agent.lib.tool.web.WebFetchTool;
import code.chg.agent.utils.JsonUtil;
import code.chg.agent.utils.MessageIdGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * PaiAgent — the full-featured agent implementation that aligns capabilities with Codex.
 * <p>
 * Built-in capabilities:
 * <ul>
 *   <li>Shell command execution with safety checks</li>
 *   <li>File read/write</li>
 *   <li>apply_patch for incremental file edits</li>
 *   <li>Plan management (update_plan tool + PlanMemoryRegion)</li>
 *   <li>Web fetch</li>
 *   <li>MCP (Model Context Protocol) tool integration</li>
 *   <li>Skill-based prompt injection</li>
 *   <li>Context compaction (BrainSummaryHook)</li>
 * </ul>
 * <p>
 *
 * @author yangchg <yangchg314@gmail.com>
 * @title PaiAgent
 * @description High-level agent facade that manages session lifecycle, runtime state, and tool configuration.
 */
public class PaiAgent implements Agent {
    private final String workDir;
    private final String userHome;
    private final SkillsManager skillsManager;
    private final LocalSessionStoreManager sessionStoreManager;
    private SessionRuntimeContext runtimeContext;
    private SessionChannelStateSubscriber channelStateSubscriber;

    public PaiAgent(String workDir) {
        this.workDir = workDir;
        this.userHome = System.getProperty("user.home");
        this.skillsManager = new SkillsManager(workDir);
        this.sessionStoreManager = new LocalSessionStoreManager();
        createSession();
    }

    @Override
    public void talk(String message, ChannelSubscriber channelSubscriber) {
        ensureRuntime();
        String sessionId = runtimeContext.getSessionId();
        SessionData sessionData = sessionStoreManager.getSessionData(sessionId);
        if (sessionData == null) {
            sessionData = new SessionData();
            sessionData.setSessionId(sessionId);
            sessionData.setCreatedAt(System.currentTimeMillis());
        }
        sessionData.setLatestUserMessage(message);
        sessionData.setLastActiveAt(System.currentTimeMillis());
        sessionData.setUpdatedAt(System.currentTimeMillis());
        if (needsGeneratedTitle(sessionData)) {
            sessionData.setTitle(buildSessionTitle(message));
        }
        runtimeContext.setTitle(resolveDisplayTitle(sessionData));
        sessionStoreManager.saveSessionData(sessionData);
        appendHumanHistory(sessionId, message);
        runtimeContext.getAgent().talk(message, composeSubscriber(channelSubscriber));
    }

    @Override
    public void authorize(AuthorizationResponseEventBody result, ChannelSubscriber channelSubscriber) {
        ensureRuntime();
        runtimeContext.getAgent().authorize(result, composeSubscriber(channelSubscriber));
    }

    public void compact(ChannelSubscriber channelSubscriber) {
        ensureRuntime();
        runtimeContext.getAgent().compact(composeSubscriber(channelSubscriber));
    }

    public SessionRuntimeContext currentSession() {
        ensureRuntime();
        return runtimeContext;
    }

    public SessionRuntimeContext createSession() {
        String sessionId = MessageIdGenerator.generateWithPrefix("session");
        SessionData sessionData = new SessionData();
        long now = System.currentTimeMillis();
        sessionData.setSessionId(sessionId);
        sessionData.setTitle("");
        sessionData.setCreatedAt(now);
        sessionData.setUpdatedAt(now);
        sessionData.setLastActiveAt(now);
        sessionData.setLatestTokenCount(0);
        runtimeContext = buildRuntime(sessionId, sessionData);
        return runtimeContext;
    }

    public SessionRuntimeContext resetCurrentSession() {
        ensureRuntime();
        String currentSessionId = runtimeContext.getSessionId();
        if (sessionStoreManager.sessionExists(currentSessionId)) {
            sessionStoreManager.deleteSession(currentSessionId);
        }
        return createSession();
    }

    public SessionRuntimeContext loadSession(String sessionId) {
        if (!sessionStoreManager.sessionExists(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        runtimeContext = buildRuntime(sessionId, sessionStoreManager.getSessionData(sessionId));
        return runtimeContext;
    }

    public List<SessionSummaryData> listSessions(int page, int limit) {
        return sessionStoreManager.listSessions(page, limit);
    }

    public List<HistoryMessageData> history(String sessionId, int page, int limit) {
        return sessionStoreManager.listHistory(sessionId, page, limit);
    }

    public List<SkillMetadata> skills() {
        return skillsManager.skills();
    }

    public List<McpServerView> listMcpServers() {
        ensureRuntime();
        List<McpServerView> views = new ArrayList<>();
        for (PaiAgentConfig.McpServerSection section : PaiAgentConfig.get().getMcp().getServers()) {
            boolean loaded = runtimeContext.getMcpToolMemoryRegion().isActive(section.getName());
            views.add(new McpServerView(section.getName(), section.getTransport(), Boolean.TRUE.equals(section.getAutoLoad()), loaded));
        }
        views.sort((left, right) -> {
            if (left.loaded() != right.loaded()) {
                return left.loaded() ? -1 : 1;
            }
            return left.name().compareToIgnoreCase(right.name());
        });
        return views;
    }

    public void loadMcp(String mcpName) {
        ensureRuntime();
        loadMcpIntoRuntime(runtimeContext, mcpName);
        if (sessionStoreManager.sessionExists(runtimeContext.getSessionId())
                && runtimeContext.getAgent().getMessageBus() instanceof BaseEventMessageBus bus) {
            bus.saveStateNow();
        }
        saveRuntimeMetadata();
    }

    public List<SubscriptionDescriptor> subscriptions() {
        ensureRuntime();
        return runtimeContext.getAgent().getMessageBus().subscriptions();
    }

    public String statusLine() {
        ensureRuntime();
        String title = runtimeContext.getTitle() == null || runtimeContext.getTitle().isBlank()
                ? "Untitled Session" : runtimeContext.getTitle();
        return "Session: " + runtimeContext.getSessionId()
                + " | Title: " + title
                + " | Tokens: " + runtimeContext.getEstimatedTokens() + "/" + OpenAIConfig.getToken()
                + " | MCP: " + runtimeContext.getMcpToolMemoryRegion().mcpCount();
    }

    public List<String> drainPendingSystemNotices() {
        ensureRuntime();
        return channelStateSubscriber.drainSystemNotices();
    }

    private static String defaultSystemPrompt(String workDir, String userHome) {
        String globalSkillDir = userHome + "/.pai-agent/skills";
        String workspaceSkillDir = workDir + "/.pai-agent/skills";
        return """
                You are PaiAgent, a powerful AI coding and task-execution agent.
                
                ## Core Capabilities
                You can reason about complex tasks, write and edit code, run shell commands,
                manage files, apply patches, fetch web content, and track task progress with plans.
                
                ## Skills
                - Skills are local instruction packages stored in `SKILL.md` files.
                - Global skills directory: `%s`
                - Workspace skills directory: `%s`
                - The available skills for the current session are listed in the "Skills" section of your context.
                - If the user names a skill or the task clearly matches a skill description, use that skill by loading its `SKILL.md` with `read_file`.
                - Resolve any relative paths in a skill relative to its directory.
                - Do not carry a skill across turns unless the user asks for it again.
                
                ## Guidelines
                - Break complex tasks into a plan using the update_plan tool.
                - Use shell for running commands; prefer rg over grep for search.
                - Use the `apply_patch` tool to edit files; only use `write_file` for new files or full rewrites.
                - Do not waste tokens by re-reading files after calling `apply_patch`; if the call failed, fix the patch and retry.
                - Use `read_file` with start_line/end_line to avoid reading unnecessarily large files.
                - If a tool says its result was redirected to a file in `temp/`, use `read_file` on that path and prefer line ranges.
                - Always set the workdir parameter when invoking the shell tool.
                - When searching for text or files, prefer `rg` or `rg --files`.
                - Prefer targeted, minimal changes that accomplish the task without unnecessary side effects.
                - After completing a task, summarize what was done and suggest next steps when appropriate.
                """.formatted(globalSkillDir, workspaceSkillDir);
    }

    private SessionRuntimeContext buildRuntime(String sessionId, SessionData sessionData) {
        SkillMemoryRegion skillMemoryRegion = new SkillMemoryRegion(skillsManager);
        ChatMemoryRegion chatMemoryRegion = new ChatMemoryRegion();
        PlanMemoryRegion planMemoryRegion = new PlanMemoryRegion();
        LocalEventMessageBus localEventMessageBus = new LocalEventMessageBus(sessionId, Executors.newVirtualThreadPerTaskExecutor());
        McpToolMemoryRegion mcpToolMemoryRegion = new McpToolMemoryRegion(localEventMessageBus);

        ReactAgent agent = ReactAgent.builder("PaiAgent")
                .systemPrompt(defaultSystemPrompt(workDir, userHome))
                .globalPermissionLevel(Permission.READ)
                .globalPermission("FILE:" + workDir + "/**", List.of(Permission.ALL))
                .globalPermission("DIR:" + workDir + "/**", List.of(Permission.ALL))
                .globalPermission("DIR:" + userHome + "/.pai-agent/skills/**", List.of(Permission.ALL))
                .globalPermission("FILE:" + userHome + "/.pai-agent/skills/**", List.of(Permission.ALL))
                .globalPermission("DIR:" + workDir + "/.pai-agent/skills/**", List.of(Permission.ALL))
                .globalPermission("FILE:" + workDir + "/.pai-agent/skills/**", List.of(Permission.ALL))
                .bindTools(new ShellTool(workDir))
                .bindTools(FileTool.class)
                .bindTools(ApplyPatchTool.class)
                .bindTools(WebFetchTool.class)
                .bindMemoryRegion(skillMemoryRegion)
                .bindMemoryRegion(chatMemoryRegion)
                .bindMemoryRegion(planMemoryRegion)
                .bindMemoryRegion(mcpToolMemoryRegion)
                .messageBus(localEventMessageBus)
                .build();
        localEventMessageBus.restoreAfterSubscribe(sessionData);
        SessionRuntimeContext context = new SessionRuntimeContext();
        context.setSessionId(sessionId);
        context.setAgent(agent);
        context.setMcpToolMemoryRegion(mcpToolMemoryRegion);
        context.setTitle(resolveDisplayTitle(sessionData));
        context.setEstimatedTokens(sessionData.getLatestTokenCount() != null ? sessionData.getLatestTokenCount() : 0);
        context.setLatestModelTokens(0);
        channelStateSubscriber = new SessionChannelStateSubscriber(context, sessionStoreManager);
        for (PaiAgentConfig.McpServerSection section : PaiAgentConfig.get().getMcp().getServers()) {
            if (Boolean.TRUE.equals(section.getAutoLoad())) {
                loadMcpIntoRuntime(context, section.getName());
            }
        }
        return context;
    }

    private void loadMcpIntoRuntime(SessionRuntimeContext context, String mcpName) {
        PaiAgentConfig.McpServerSection section = PaiAgentConfig.get().getMcp().getServers().stream()
                .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(mcpName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MCP not found: " + mcpName));

        McpServerConfig config = McpServerConfig.builder()
                .name(section.getName())
                .transport(section.getTransport())
                .command(section.getCommand())
                .args(section.getArgs())
                .env(section.getEnv())
                .url(section.getUrl())
                .timeoutMs(section.getTimeoutMs() == null ? 30_000L : section.getTimeoutMs())
                .build();
        List<Tool> discoveredTools = new McpToolAdapter(config).discoverTools();
        context.getMcpToolMemoryRegion().activate(mcpName, discoveredTools);
    }

    private void ensureRuntime() {
        if (runtimeContext == null) {
            createSession();
        }
    }

    private ChannelSubscriber composeSubscriber(ChannelSubscriber userSubscriber) {
        return message -> {
            channelStateSubscriber.onMessage(message);
            if (userSubscriber != null) {
                userSubscriber.onMessage(message);
            }
        };
    }

    private void appendHumanHistory(String sessionId, String content) {
        HistoryMessageData history = new HistoryMessageData();
        history.setSessionId(sessionId);
        history.setMessageId(MessageIdGenerator.generateWithPrefix("human-history"));
        history.setRole("USER");
        history.setChannelMessageType("HUMAN_MESSAGE");
        history.setContent(content);
        history.setCompleted(true);
        history.setCreatedAt(System.currentTimeMillis());
        sessionStoreManager.appendHistory(history);
    }

    private void saveRuntimeMetadata() {
        SessionData sessionData = sessionStoreManager.getSessionData(runtimeContext.getSessionId());
        if (sessionData == null) {
            return;
        }
        sessionData.setTitle(runtimeContext.getTitle() == null ? "" : runtimeContext.getTitle().strip());
        sessionData.setLatestTokenCount(runtimeContext.getEstimatedTokens());
        sessionData.setUpdatedAt(System.currentTimeMillis());
        sessionData.setLastActiveAt(System.currentTimeMillis());
        sessionStoreManager.saveSessionData(sessionData);
    }

    private String buildSessionTitle(String input) {
        String normalized = input == null ? "" : input.strip();
        if (normalized.length() <= 15) {
            return normalized;
        }
        return normalized.substring(0, 15) + "...";
    }

    private boolean needsGeneratedTitle(SessionData sessionData) {
        if (sessionData == null) {
            return true;
        }
        String title = sessionData.getTitle();
        return title == null || title.isBlank() || title.equals(sessionData.getSessionId());
    }

    private String resolveDisplayTitle(SessionData sessionData) {
        if (sessionData == null) {
            return "";
        }
        String title = sessionData.getTitle();
        if (title != null && !title.isBlank() && !title.equals(sessionData.getSessionId())) {
            return title;
        }
        String latestUserMessage = sessionData.getLatestUserMessage();
        if (latestUserMessage != null && !latestUserMessage.isBlank()) {
            return buildSessionTitle(latestUserMessage);
        }
        return "";
    }

    public record McpServerView(String name, String transport, boolean autoLoad, boolean loaded) {
    }

    private static class SessionChannelStateSubscriber implements ChannelSubscriber {
        private final SessionRuntimeContext runtimeContext;
        private final LocalSessionStoreManager sessionStoreManager;
        private final Map<String, MergedChannelMessage> mergedMessages = new LinkedHashMap<>();
        private final LinkedList<String> pendingSystemNotices = new LinkedList<>();

        private SessionChannelStateSubscriber(SessionRuntimeContext runtimeContext, LocalSessionStoreManager sessionStoreManager) {
            this.runtimeContext = runtimeContext;
            this.sessionStoreManager = sessionStoreManager;
        }

        @Override
        public synchronized void onMessage(ChannelMessage message) {
            if (message == null || message.id() == null) {
                return;
            }
            String key = message.type() + ":" + message.id();
            MergedChannelMessage merged = mergedMessages.get(key);
            if (merged == null) {
                merged = new MergedChannelMessage(message);
                mergedMessages.put(key, merged);
            } else {
                merged.accumulate(message);
            }
            if (message.completed()) {
                persistMergedMessage(merged);
                mergedMessages.remove(key);
            }
        }

        public synchronized List<String> drainSystemNotices() {
            List<String> notices = new ArrayList<>(pendingSystemNotices);
            pendingSystemNotices.clear();
            return notices;
        }

        private void persistMergedMessage(MergedChannelMessage merged) {
            ChannelMessageType type = merged.type();
            ChannelMessageBody body = merged.body();
            if (type == ChannelMessageType.TOKEN_USAGE && body instanceof TokenUsageChannelMessage tokenUsage) {
                int usedTokens = tokenUsage.getEstimatedContextTokens() != null
                        ? tokenUsage.getEstimatedContextTokens()
                        : tokenUsage.getTotalTokens() == null ? 0 : tokenUsage.getTotalTokens();
                runtimeContext.setEstimatedTokens(usedTokens);
                runtimeContext.setLatestModelTokens(tokenUsage.getTotalTokens() == null ? 0 : tokenUsage.getTotalTokens());
                sessionStoreManager.updateTokenUsage(runtimeContext.getSessionId(), usedTokens);
                appendHistory("SYSTEM", type.name(), "Tokens: " + usedTokens + "/" + OpenAIConfig.getToken(), body);
                return;
            }
            if (type == ChannelMessageType.COMPACT_NOTICE && body instanceof CompactNoticeChannelMessage compactNotice) {
                int afterTokens = compactNotice.getAfterTokens() == null ? runtimeContext.getEstimatedTokens() : compactNotice.getAfterTokens();
                runtimeContext.setEstimatedTokens(afterTokens);
                sessionStoreManager.updateTokenUsage(runtimeContext.getSessionId(), afterTokens);
                String summary = compactNotice.getSummary() == null ? "Conversation context compaction completed." : compactNotice.getSummary();
                pendingSystemNotices.add(summary);
                appendHistory("SYSTEM", type.name(), summary, body);
                return;
            }
            if (type == ChannelMessageType.LLM_CONTENT_CHUNK && body instanceof AIMessageChannelChunk aiChunk) {
                appendHistory("AI", type.name(), aiChunk.getContentChunk().toString(), body);
                return;
            }
            if (type == ChannelMessageType.TOOL_CALL_RESPONSE && body instanceof ToolCallResponseChannelMessage response) {
                appendHistory("TOOL", type.name(), response.getResponse(), body);
                return;
            }
            if (type == ChannelMessageType.TOOL_AUTHORIZATION_REQUEST && body instanceof AuthorizationRequestChannelMessage auth) {
                appendHistory("SYSTEM", type.name(), auth.getPrompt(), body);
                return;
            }
            if (type == ChannelMessageType.TOOL_CALL_REJECTED && body instanceof ToolCallRejectedChannelMessage rejected) {
                appendHistory("SYSTEM", type.name(), rejected.getReason(), body);
            }
        }

        private void appendHistory(String role, String type, String content, ChannelMessageBody body) {
            HistoryMessageData history = new HistoryMessageData();
            history.setSessionId(runtimeContext.getSessionId());
            history.setMessageId(MessageIdGenerator.generateWithPrefix("history"));
            history.setRole(role);
            history.setChannelMessageType(type);
            history.setContent(content);
            history.setBodyJson(JsonUtil.toJson(body));
            history.setCompleted(true);
            history.setCreatedAt(System.currentTimeMillis());
            sessionStoreManager.appendHistory(history);
        }

        private static class MergedChannelMessage implements ChannelMessage {
            private final String id;
            private final String name;
            private final ChannelMessageType type;
            private final ChannelMessageBody body;
            private boolean completed;

            private MergedChannelMessage(ChannelMessage message) {
                this.id = message.id();
                this.name = message.name();
                this.type = message.type();
                this.body = ChannelMessageBodyCopier.copy(message.body());
                this.completed = message.completed();
            }

            private void accumulate(ChannelMessage message) {
                if (body != null && message.body() != null) {
                    body.accumulate(message.body());
                }
                completed = message.completed();
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public ChannelMessageType type() {
                return type;
            }

            @Override
            public ChannelMessageBody body() {
                return body;
            }

            @Override
            public boolean completed() {
                return completed;
            }
        }
    }
}
