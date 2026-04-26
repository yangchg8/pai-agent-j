package code.chg.agent.cli.render;

import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelMessageBody;
import code.chg.agent.core.channel.ChannelMessageBodyCopier;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.core.channel.body.AuthorizationRequestChannelMessage;
import code.chg.agent.core.channel.body.ThinkingChannelChunk;
import code.chg.agent.core.channel.body.TokenUsageChannelMessage;
import code.chg.agent.core.channel.body.ToolCallResponseChannelMessage;
import code.chg.agent.llm.component.AuthorizationRequirementContent;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp.Capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LessRenderer
 * @description Terminal renderer that maintains the live conversation view.
 */
public class LessRenderer implements ChannelSubscriber {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CURSOR_UP = "\u001B[%dA";
    private static final String ANSI_CLEAR_TO_END = "\u001B[J";
    private static final String ANSI_CARRIAGE_RETURN = "\r";

    private static final int DEFAULT_TERMINAL_WIDTH = 100;
    private static final int MIN_TERMINAL_WIDTH = 40;
    private static final int THINKING_MAX_LINES = 8;
    private static final int BOX_SIDE_PADDING = 1;
    private static final int RIGHT_SAFE_MARGIN = 1;
    private static final int TOOL_STAGE_WIDTH = 9;
    private static final int TOOL_NAME_WIDTH = 16;

    private final Terminal terminal;
    private final Consumer<List<String>> persistedOutput;
    private final LiveRegion liveRegion;
    private final Object renderLock = new Object();
    private final Queue<MutableChannelMessage> messages = new ConcurrentLinkedQueue<>();
    private final Map<String, MutableChannelMessage> messageIndex = new HashMap<>();
    private String activeRegionId;
    private boolean renderingPaused;
    private boolean pendingRender;
    private String lastRenderFingerprint;
    private List<String> lastRenderedLines = Collections.emptyList();

    public LessRenderer(Terminal terminal) {
        this(terminal, null, terminal == null ? LiveRegion.noop() : new AnchoredLiveRegion(terminal));
    }

    public LessRenderer(Terminal terminal, Consumer<List<String>> persistedOutput) {
        this(terminal, persistedOutput, terminal == null ? LiveRegion.noop() : new AnchoredLiveRegion(terminal));
    }

    LessRenderer(Terminal terminal, LiveRegion liveRegion) {
        this(terminal, null, liveRegion);
    }

    private LessRenderer(Terminal terminal, Consumer<List<String>> persistedOutput, LiveRegion liveRegion) {
        this.terminal = terminal;
        this.persistedOutput = persistedOutput;
        this.liveRegion = liveRegion == null ? LiveRegion.noop() : liveRegion;
    }

    @Override
    public void onMessage(ChannelMessage message) {
        synchronized (renderLock) {
            if (message == null || message.id() == null) {
                return;
            }
            MutableChannelMessage mutableChannelMessage = mergeMessage(message);
            if (renderingPaused) {
                pendingRender = true;
                return;
            }
            render(mutableChannelMessage);
        }
    }

    public void render(MutableChannelMessage mutableChannelMessage) {
        if (mutableChannelMessage == null) {
            return;
        }
        switch (mutableChannelMessage.type()) {
            case THINKING_CHUNK -> {
                activeRegionId = mutableChannelMessage.id();
                renderThinking(mutableChannelMessage.id());
            }
            case LLM_CONTENT_CHUNK -> {
                activeRegionId = mutableChannelMessage.id();
                renderWorking(mutableChannelMessage.id());
            }
            case TOOL_CALL_RESPONSE, TOOL_AUTHORIZATION_REQUEST -> {
                String workingMessageId = resolveWorkingMessageId(mutableChannelMessage);
                if (workingMessageId != null) {
                    activeRegionId = workingMessageId;
                    renderWorking(workingMessageId);
                }
            }
            default -> {
                if (activeRegionId != null) {
                    renderWorking(activeRegionId);
                }
            }
        }
    }

    public void renderThinking(String thinkingMessageId) {
        MutableChannelMessage thinkingMessage = findMessage(thinkingMessageId, ChannelMessageType.THINKING_CHUNK);
        if (thinkingMessage == null || thinkingMessage.type() != ChannelMessageType.THINKING_CHUNK) {
            return;
        }
        String thinking = thinkingContent(thinkingMessage);
        List<String> thinkingLines = limitToLastLines(wrapBlock(thinking, contentWidth()), THINKING_MAX_LINES);

        List<String> lines = new ArrayList<>();
        lines.add(styleHeader("PAI AGENT", "reasoning", "Inspecting the latest internal reasoning stream", ANSI_CYAN));
        lines.add("");
        lines.addAll(colorizeBox(buildBox("thinking", thinkingLines), ANSI_CYAN));
        lines.add("");
        lines.add(styleHint("Waiting for the final response..."));
        if (!shouldRedraw(thinkingMessage.id(), ChannelMessageType.THINKING_CHUNK, lines)) {
            return;
        }
        redraw(lines);
    }

    public void renderWorking(String thinkingMessageId) {
        MutableChannelMessage workingMessage = findMessage(thinkingMessageId, ChannelMessageType.LLM_CONTENT_CHUNK);
        if (workingMessage == null || workingMessage.type() != ChannelMessageType.LLM_CONTENT_CHUNK) {
            return;
        }
        AIMessageChannelChunk body = (AIMessageChannelChunk) workingMessage.body();
        String answerBody = body.getContentChunk().toString();
        List<String> toolLines = collectToolLines(workingMessage, body);
        boolean toolFlowActive = !toolLines.isEmpty();
        String statusText = workingMessage.completed() ? "Response complete" : (toolFlowActive ? "Using tools" : "Streaming answer");
        List<String> answerLines = new ArrayList<>(wrapBlock(answerBody.isBlank() ? "Preparing response..." : answerBody, contentWidth()));
        String tokenBadge = latestTokenUsageBadge();
        applyBottomRightBadge(answerLines, tokenBadge, contentWidth());

        List<String> lines = new ArrayList<>();
        lines.add(styleHeader("PAI AGENT", toolFlowActive ? "tool workflow" : "assistant reply", statusText,
                toolFlowActive ? ANSI_YELLOW : ANSI_GREEN));
        lines.add("");
        lines.addAll(colorizeBox(buildBox("assistant", answerLines), ANSI_GREEN));
        if (!toolLines.isEmpty()) {
            lines.add("");
            lines.addAll(colorizeBox(buildBox("tool activity", toolLines), ANSI_YELLOW));
        }
        lines.add("");
        lines.add(styleHint(workingMessage.completed() ? "Type your next request below." : "Streaming output updates in place."));
        if (!shouldRedraw(workingMessage.id(), ChannelMessageType.LLM_CONTENT_CHUNK, lines)) {
            return;
        }
        redraw(lines);
    }

    public void clear() {
        synchronized (renderLock) {
            messages.clear();
            messageIndex.clear();
            activeRegionId = null;
            renderingPaused = false;
            pendingRender = false;
            lastRenderFingerprint = null;
            lastRenderedLines = Collections.emptyList();
            liveRegion.reset();
        }
    }

    public Terminal terminal() {
        return terminal;
    }

    public void markRenderAnchor() {
        synchronized (renderLock) {
            liveRegion.markAnchor();
        }
    }

    public void pauseRendering() {
        synchronized (renderLock) {
            renderingPaused = true;
            liveRegion.suspend();
        }
    }

    public void resumeRendering() {
        synchronized (renderLock) {
            renderingPaused = false;
            liveRegion.restore();
            if (!pendingRender || activeRegionId == null) {
                pendingRender = false;
                return;
            }
            pendingRender = false;
            MutableChannelMessage workingMessage = findMessage(activeRegionId, ChannelMessageType.LLM_CONTENT_CHUNK);
            if (workingMessage != null) {
                renderWorking(activeRegionId);
                return;
            }
            MutableChannelMessage thinkingMessage = findMessage(activeRegionId, ChannelMessageType.THINKING_CHUNK);
            if (thinkingMessage != null) {
                renderThinking(activeRegionId);
            }
        }
    }

    public void persistCurrentView() {
        synchronized (renderLock) {
            liveRegion.persist(lastRenderedLines, persistedOutput);
        }
    }

    public List<AuthorizationRequestView> authorizationRequests() {
        synchronized (renderLock) {
            List<AuthorizationRequestView> requests = new ArrayList<>();
            for (MutableChannelMessage message : messages) {
                if (message.type() != ChannelMessageType.TOOL_AUTHORIZATION_REQUEST) {
                    continue;
                }
                AuthorizationRequestChannelMessage body = (AuthorizationRequestChannelMessage) message.body();
                requests.add(new AuthorizationRequestView(
                        rawMessageId(message.id()),
                        toolNameForCall(findWorkingMessage(body.getToolCallId()), body.getToolCallId()),
                        body.getToolCallId(),
                        body.getPrompt(),
                        body.getContent(),
                        canSaveAuthorization(body.getContent())));
            }
            return requests;
        }
    }

    private synchronized MutableChannelMessage mergeMessage(ChannelMessage message) {
        String messageId = message.type() + ":" + message.id();
        MutableChannelMessage existing = messageIndex.get(messageId);
        if (existing == null) {
            MutableChannelMessage newMessage = new MutableChannelMessage(message);
            messages.offer(newMessage);
            messageIndex.put(newMessage.id(), newMessage);
            return newMessage;
        }
        existing.accumulate(message);
        return existing;
    }

    private static boolean canSaveAuthorization(AuthorizationRequirementContent content) {
        return content != null && content.getItems() != null && !content.getItems().isEmpty();
    }

    private static String rawMessageId(String messageId) {
        int separator = messageId.indexOf(':');
        if (separator < 0 || separator == messageId.length() - 1) {
            return messageId;
        }
        return messageId.substring(separator + 1);
    }

    private MutableChannelMessage findMessage(String messageId, ChannelMessageType type) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        MutableChannelMessage direct = messageIndex.get(messageId);
        if (direct != null) {
            return direct;
        }
        return messageIndex.get(type + ":" + messageId);
    }

    private boolean shouldRedraw(String regionId, ChannelMessageType type, List<String> lines) {
        String fingerprint = (regionId == null ? "" : regionId)
                + "|"
                + type
                + "|"
                + String.join("\n", lines == null ? List.of() : lines);
        if (fingerprint.equals(lastRenderFingerprint)) {
            return false;
        }
        lastRenderFingerprint = fingerprint;
        return true;
    }

    private List<String> collectToolLines(MutableChannelMessage workingMessage, AIMessageChannelChunk body) {
        List<String> lines = new ArrayList<>();
        if (body.getToolCallChunks() != null && !body.getToolCallChunks().isEmpty()) {
            body.getToolCallChunks().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        AIMessageChannelChunk.AIToolCallChunk chunk = entry.getValue();
                        lines.add(formatSingleLine(
                                "QUEUED",
                                chunk.getToolCallName().toString(),
                                compactInline(chunk.getToolCallArguments().toString()),
                                true));
                    });
        }

        for (MutableChannelMessage message : messages) {
            if (!workingMessage.id().equals(resolveWorkingMessageId(message))) {
                continue;
            }
            if (message.type() == ChannelMessageType.TOOL_CALL_RESPONSE) {
                ToolCallResponseChannelMessage response = (ToolCallResponseChannelMessage) message.body();
                lines.add(formatSingleLine(
                        "DONE",
                        response.getToolName(),
                        compactInline(response.getResponse()),
                        false));
            } else if (message.type() == ChannelMessageType.TOOL_AUTHORIZATION_REQUEST) {
                AuthorizationRequestChannelMessage authorization = (AuthorizationRequestChannelMessage) message.body();
                lines.add(formatSingleLine(
                        "AUTH",
                        toolNameForCall(workingMessage, authorization.getToolCallId()),
                        compactInline(authorization.getPrompt()),
                        false));
            }
        }

        return lines;
    }

    private String latestTokenUsageBadge() {
        MutableChannelMessage tokenMessage = latestTokenUsageMessage();
        if (tokenMessage == null || !(tokenMessage.body() instanceof TokenUsageChannelMessage tokenUsage)) {
            return null;
        }
        int totalContextTokens = tokenUsage.getEstimatedContextTokens() == null
                ? (tokenUsage.getTotalTokens() == null ? 0 : tokenUsage.getTotalTokens())
                : tokenUsage.getEstimatedContextTokens();
        String maxTokens = tokenUsage.getMaxTokens() == null ? "-" : String.valueOf(tokenUsage.getMaxTokens());
        return "Tokens: " + totalContextTokens + "/" + maxTokens;
    }

    private MutableChannelMessage latestTokenUsageMessage() {
        MutableChannelMessage latest = null;
        for (MutableChannelMessage message : messages) {
            if (message.type() == ChannelMessageType.TOKEN_USAGE) {
                latest = message;
            }
        }
        return latest;
    }

    private String resolveWorkingMessageId(MutableChannelMessage message) {
        if (message == null) {
            return null;
        }
        return switch (message.type()) {
            case LLM_CONTENT_CHUNK, THINKING_CHUNK -> message.id();
            case TOOL_CALL_RESPONSE ->
                    findWorkingMessageIdByToolCallId(((ToolCallResponseChannelMessage) message.body()).getToolCallId());
            case TOOL_AUTHORIZATION_REQUEST ->
                    findWorkingMessageIdByToolCallId(((AuthorizationRequestChannelMessage) message.body()).getToolCallId());
            default -> null;
        };
    }

    private String findWorkingMessageIdByToolCallId(String toolCallId) {
        if (toolCallId == null || toolCallId.isEmpty()) {
            return null;
        }
        for (MutableChannelMessage message : messages) {
            if (message.type() != ChannelMessageType.LLM_CONTENT_CHUNK) {
                continue;
            }
            AIMessageChannelChunk body = (AIMessageChannelChunk) message.body();
            if (body.getToolCallChunks() == null) {
                continue;
            }
            for (AIMessageChannelChunk.AIToolCallChunk chunk : body.getToolCallChunks().values()) {
                if (toolCallId.equals(chunk.getToolCallId().toString())) {
                    return message.id();
                }
            }
        }
        return null;
    }

    private MutableChannelMessage findWorkingMessage(String toolCallId) {
        String workingMessageId = findWorkingMessageIdByToolCallId(toolCallId);
        if (workingMessageId == null) {
            return null;
        }
        return findMessage(workingMessageId, ChannelMessageType.LLM_CONTENT_CHUNK);
    }

    private String toolNameForCall(MutableChannelMessage workingMessage, String toolCallId) {
        if (workingMessage == null || toolCallId == null) {
            return "tool";
        }
        AIMessageChannelChunk body = (AIMessageChannelChunk) workingMessage.body();
        if (body.getToolCallChunks() == null) {
            return "tool";
        }
        for (AIMessageChannelChunk.AIToolCallChunk chunk : body.getToolCallChunks().values()) {
            if (toolCallId.equals(chunk.getToolCallId().toString())) {
                String toolName = chunk.getToolCallName().toString();
                return toolName == null || toolName.isBlank() ? "tool" : toolName;
            }
        }
        return "tool";
    }

    private String thinkingContent(MutableChannelMessage message) {
        ChannelMessageBody messageBody = message.body();
        if (!(messageBody instanceof ThinkingChannelChunk thinkingBody)) {
            return "";
        }
        return thinkingBody.getThinking();
    }

    private List<String> buildBox(String title, List<String> contentLines) {
        int innerWidth = contentWidth();
        List<String> lines = new ArrayList<>();
        lines.add(buildBorder(title, innerWidth, true));
        if (contentLines == null || contentLines.isEmpty()) {
            lines.add(buildBoxLine("", innerWidth));
        } else {
            for (String contentLine : contentLines) {
                lines.add(buildBoxLine(contentLine, innerWidth));
            }
        }
        lines.add(buildBorder("", innerWidth, false));
        return lines;
    }

    private String buildBorder(String title, int innerWidth, boolean topBorder) {
        int totalInnerWidth = innerWidth + BOX_SIDE_PADDING * 2;
        if (!topBorder || title == null || title.isBlank()) {
            return "+" + repeat('-', totalInnerWidth) + "+";
        }
        String normalizedTitle = " " + title + " ";
        int remain = Math.max(0, totalInnerWidth - visualWidth(normalizedTitle));
        return "+" + normalizedTitle + repeat('-', remain) + "+";
    }

    private String buildBoxLine(String content, int innerWidth) {
        String safeContent = content == null ? "" : content;
        int padding = Math.max(0, innerWidth - visualWidth(safeContent));
        return "|" + repeat(' ', BOX_SIDE_PADDING) + safeContent + repeat(' ', padding) + repeat(' ', BOX_SIDE_PADDING) + "|";
    }

    private void applyBottomRightBadge(List<String> contentLines, String badge, int innerWidth) {
        if (badge == null || badge.isBlank()) {
            return;
        }
        if (contentLines == null || contentLines.isEmpty()) {
            return;
        }
        String safeBadge = fitToWidth(badge, innerWidth);
        int badgeWidth = visualWidth(safeBadge);
        if (badgeWidth <= 0) {
            return;
        }
        int lastIndex = contentLines.size() - 1;
        String content = contentLines.get(lastIndex);
        String safeContent = content == null ? "" : content;
        int availableContentWidth = Math.max(0, innerWidth - badgeWidth - 1);
        String fittedContent = fitToWidth(safeContent, availableContentWidth);
        int spacing = Math.max(1, innerWidth - visualWidth(fittedContent) - badgeWidth);
        contentLines.set(lastIndex, fittedContent + repeat(' ', spacing) + safeBadge);
    }

    private List<String> wrapBlock(String text, int width) {
        List<String> lines = new ArrayList<>();
        String normalizedText = text == null ? "" : text.replace("\r", "");
        String[] segments = normalizedText.split("\n", -1);
        for (String segment : segments) {
            lines.addAll(wrapLine(segment, width));
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private List<String> wrapLine(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int charWidth = charWidth(ch);
            if (currentWidth + charWidth > width && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }
            current.append(ch);
            currentWidth += charWidth;
        }
        if (current.length() > 0 || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String formatSingleLine(String stage, String toolName, String detail, boolean streamingDetail) {
        String safeStage = fitToWidth(stage == null ? "" : stage, TOOL_STAGE_WIDTH);
        String safeToolName = fitToWidth(toolName == null || toolName.isBlank() ? "tool" : toolName, TOOL_NAME_WIDTH);
        String safeDetail = detail == null ? "" : detail;
        String prefix = padRight(safeStage, TOOL_STAGE_WIDTH)
                + "  "
                + padRight(safeToolName, TOOL_NAME_WIDTH)
                + "  ";
        int maxWidth = contentWidth();
        if (visualWidth(prefix) >= maxWidth) {
            return trimToWidth(prefix, maxWidth);
        }
        int detailWidth = maxWidth - visualWidth(prefix);
        return prefix + (streamingDetail
                ? streamingFitToWidth(safeDetail, detailWidth)
                : fitToWidth(safeDetail, detailWidth));
    }

    private void redraw(List<String> lines) {
        if (terminal == null) {
            return;
        }
        lastRenderedLines = List.copyOf(lines);
        liveRegion.update(lastRenderedLines);
    }

    private int contentWidth() {
        return Math.max(MIN_TERMINAL_WIDTH, terminalWidth() - 4 - RIGHT_SAFE_MARGIN);
    }

    private int terminalWidth() {
        if (terminal == null || terminal.getWidth() <= 0) {
            return DEFAULT_TERMINAL_WIDTH;
        }
        return Math.max(MIN_TERMINAL_WIDTH, terminal.getWidth());
    }

    private String fitToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        if (visualWidth(text) <= width) {
            return text;
        }
        if (width <= 3) {
            return trimToWidth(text, width);
        }
        return trimToWidth(text, width - 3) + "...";
    }

    private String streamingFitToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        if (visualWidth(text) <= width) {
            return text;
        }
        if (width <= 3) {
            return tailFitToWidth(text, width);
        }
        return "..." + tailFitToWidth(text, width - 3);
    }

    private String tailFitToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        if (visualWidth(text) <= width) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int currentWidth = 0;
        for (int index = text.length() - 1; index >= 0; index--) {
            char ch = text.charAt(index);
            int charWidth = charWidth(ch);
            if (currentWidth + charWidth > width) {
                break;
            }
            builder.insert(0, ch);
            currentWidth += charWidth;
        }
        return builder.toString();
    }

    private String padRight(String text, int width) {
        String safeText = text == null ? "" : text;
        int padding = Math.max(0, width - visualWidth(safeText));
        return safeText + repeat(' ', padding);
    }

    private List<String> limitToLastLines(List<String> lines, int maxLines) {
        if (lines == null || lines.isEmpty() || maxLines <= 0 || lines.size() <= maxLines) {
            return lines == null || lines.isEmpty() ? List.of("") : lines;
        }
        List<String> limited = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
        limited.set(0, "... " + limited.get(0));
        return limited;
    }

    private List<String> colorizeBox(List<String> lines, String tone) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> styled = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index == 0 || index == lines.size() - 1) {
                styled.add(style(tone + ANSI_BOLD, line));
            } else {
                styled.add(line);
            }
        }
        return styled;
    }

    private String styleHeader(String brand, String section, String status, String tone) {
        String left = style(ANSI_BOLD + ANSI_BLUE, brand)
                + style(ANSI_DIM, " / ")
                + style(ANSI_BOLD + tone, section);
        String right = status == null || status.isBlank() ? "" : style(ANSI_DIM, status);
        int totalWidth = terminalWidth() - RIGHT_SAFE_MARGIN;
        int padding = Math.max(1, totalWidth - visualWidth(stripAnsi(left)) - visualWidth(stripAnsi(right)));
        return left + repeat(' ', padding) + right;
    }

    private String styleHint(String content) {
        return style(ANSI_DIM, content == null ? "" : content);
    }

    private String style(String ansi, String content) {
        return ansi + content + ANSI_RESET;
    }

    private String stripAnsi(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private String trimToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int currentWidth = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            int charWidth = charWidth(ch);
            if (currentWidth + charWidth > width) {
                break;
            }
            builder.append(ch);
            currentWidth += charWidth;
        }
        return builder.toString();
    }

    private int visualWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            width += charWidth(text.charAt(index));
        }
        return width;
    }

    private static int charWidth(char ch) {
        Character.UnicodeScript script = Character.UnicodeScript.of(ch);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA ? 2 : 1;
    }

    private static String repeat(char value, int count) {
        if (count <= 0) {
            return "";
        }
        return String.valueOf(value).repeat(count);
    }

    private static String compactInline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\r", "").replace("\n", "");
    }

    private static final class MutableChannelMessage implements ChannelMessage {

        private final String id;
        private final String name;
        private final ChannelMessageType type;
        private final ChannelMessageBody body;
        private boolean completed;

        private MutableChannelMessage(ChannelMessage message) {
            this.id = message.type() + ":" + message.id();
            this.name = message.name();
            this.type = message.type();
            this.body = ChannelMessageBodyCopier.copy(message.body());
            this.completed = message.completed();
        }

        private void accumulate(ChannelMessage message) {
            if (body != null && message.body() != null) {
                body.accumulate(message.body());
            }
            completed = completed || message.completed();
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
        public code.chg.agent.core.channel.ChannelMessageType type() {
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

    public record AuthorizationRequestView(
            String id,
            String toolName,
            String toolCallId,
            String prompt,
            AuthorizationRequirementContent content,
            boolean allowSave) {
    }

    interface LiveRegion {
        void markAnchor();

        void update(List<String> lines);

        void suspend();

        void restore();

        void persist(List<String> lines, Consumer<List<String>> persistedOutput);

        void reset();

        static LiveRegion noop() {
            return new LiveRegion() {
                @Override
                public void markAnchor() {
                }

                @Override
                public void update(List<String> lines) {
                }

                @Override
                public void suspend() {
                }

                @Override
                public void restore() {
                }

                @Override
                public void persist(List<String> lines, Consumer<List<String>> persistedOutput) {
                }

                @Override
                public void reset() {
                }
            };
        }
    }

    private static final class AnchoredLiveRegion implements LiveRegion {

        private final Terminal terminal;
        private boolean anchorMarked;
        private int renderedRowCount;

        private AnchoredLiveRegion(Terminal terminal) {
            this.terminal = terminal;
        }

        @Override
        public void markAnchor() {
            anchorMarked = true;
            renderedRowCount = 0;
        }

        @Override
        public void update(List<String> lines) {
            if (terminal == null) {
                return;
            }
            if (!anchorMarked) {
                markAnchor();
            }
            List<String> safeLines = lines == null ? Collections.emptyList() : List.copyOf(lines);
            moveToAnchor();
            clearToEndOfScreen();
            for (String line : safeLines) {
                terminal.writer().println(line == null ? "" : line);
            }
            terminal.writer().flush();
            renderedRowCount = countDisplayRows(safeLines);
        }

        @Override
        public void suspend() {
        }

        @Override
        public void restore() {
        }

        @Override
        public void persist(List<String> lines, Consumer<List<String>> persistedOutput) {
            if (terminal == null) {
                return;
            }
            terminal.writer().flush();
            anchorMarked = false;
            renderedRowCount = 0;
        }

        @Override
        public void reset() {
            anchorMarked = false;
            renderedRowCount = 0;
        }

        private void moveToAnchor() {
            if (renderedRowCount <= 0) {
                return;
            }
            if (!terminal.puts(Capability.parm_up_cursor, renderedRowCount)) {
                terminal.writer().print(ANSI_CURSOR_UP.formatted(renderedRowCount));
            }
            if (!terminal.puts(Capability.carriage_return)) {
                terminal.writer().print(ANSI_CARRIAGE_RETURN);
            }
        }

        private void clearToEndOfScreen() {
            if (!terminal.puts(Capability.clr_eos)) {
                terminal.writer().print(ANSI_CLEAR_TO_END);
            }
        }

        private int countDisplayRows(List<String> lines) {
            int columns = terminal.getWidth() > 0 ? terminal.getWidth() : DEFAULT_TERMINAL_WIDTH;
            if (lines == null || lines.isEmpty()) {
                return 0;
            }
            int rowCount = 0;
            for (String line : lines) {
                AttributedString attributed = AttributedString.fromAnsi(line == null ? "" : line, terminal);
                List<AttributedString> split = attributed.columnSplitLength(columns);
                rowCount += (split == null || split.isEmpty()) ? 1 : split.size();
            }
            return rowCount;
        }
    }
}
