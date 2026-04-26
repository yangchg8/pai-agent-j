package code.chg.agent.lib.brain;

import code.chg.agent.config.OpenAIConfig;
import code.chg.agent.core.brain.AbstractBrain;
import code.chg.agent.core.brain.BrainHook;
import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.lib.memory.ChatMemoryRegion;
import code.chg.agent.lib.prompt.CompactPrompts;
import code.chg.agent.llm.LLMClient;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.LLMRequest;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.message.ContentLLMMessage;
import code.chg.agent.utils.MessageIdGenerator;
import code.chg.agent.utils.MessageTokenUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title BrainSummaryHook
 * @description Brain hook that compacts chat history once the context window threshold is reached.
 */
@Slf4j
public class BrainSummaryHook implements BrainHook {

    @Override
    public void cleanUp(AbstractBrain brain, EventBusContext context) {
        cleanUp(brain, context, false);
    }

    public void cleanUp(AbstractBrain brain, EventBusContext context, boolean force) {
        List<MemoryRegion> memoryRegions = brain.getMemoryRegions();
        int beforeTokens = MessageTokenUtil.estimateTokenCount(memoryRegions);
        if (!needCleanUp(memoryRegions) && !force) {
            return;
        }
        ChatMemoryRegion chatMemoryRegion = memoryRegions.stream()
                .filter(item -> item instanceof ChatMemoryRegion)
                .map(item -> (ChatMemoryRegion) item)
                .findFirst().orElse(null);
        if (chatMemoryRegion == null) {
            return;
        }
        if (chatMemoryRegion.messages() == null || chatMemoryRegion.messages().isEmpty()) {
            publishCompactNotice(context, "MANUAL", beforeTokens, beforeTokens, 0,
                    "There is no chat history available to compact.");
            return;
        }
        LLMClient client = brain.client();

        List<LLMMessage> history = chatMemoryRegion.messages();

        List<LLMMessage> userMessages = collectUserMessages(history);

        SummaryLLMMessage oldSummary = findOldSummary(history);

        List<LLMMessage> summarizationMessages = new ArrayList<>(history);
        summarizationMessages.add(ContentLLMMessage.of(MessageIdGenerator.generateWithPrefix("compact-prompt"),
                MessageType.HUMAN,
                CompactPrompts.SUMMARIZATION_PROMPT));
        LLMRequest request = new LLMRequest(summarizationMessages);
        List<LLMMessage> summaryResponse = client.chat(request);
        String summaryText = extractSummaryText(summaryResponse);
        if (summaryText == null || summaryText.isEmpty()) {
            log.warn("LLM returned empty summary, skipping compaction");
            return;
        }
        log.info("Summary generated, length: {} chars", summaryText.length());

        List<LLMMessage> compactedHistory = buildCompactedHistory(
                userMessages, summaryText, oldSummary, history);

        history.clear();
        history.addAll(compactedHistory);
        int afterTokens = MessageTokenUtil.estimateTokenCount(memoryRegions);
        String trigger = force ? "MANUAL" : "AUTO";
        publishCompactNotice(context, trigger, beforeTokens, afterTokens,
                Math.max(beforeTokens - afterTokens, 0),
                (force ? "Manual" : "Automatic") + " compaction completed. Context usage dropped from "
                        + beforeTokens + " to " + afterTokens + " tokens.");
        log.info("History compacted: {} messages in new history", compactedHistory.size());
    }

    private void publishCompactNotice(EventBusContext context, String trigger, int beforeTokens,
                                      int afterTokens, int reducedTokens, String summary) {
        if (context == null || context.chat() == null) {
            return;
        }
        context.chat().publish(ChannelMessageBuilder.builder(
                        MessageIdGenerator.generateWithPrefix("compact-notice"),
                        "system",
                        ChannelMessageType.COMPACT_NOTICE)
                .compactNotice(trigger, beforeTokens, afterTokens, reducedTokens, summary)
                .build(true));
    }


    private static List<LLMMessage> collectUserMessages(List<LLMMessage> history) {
        List<LLMMessage> userMessages = new ArrayList<>();
        for (LLMMessage message : history) {
            if (message.type() == MessageType.HUMAN && !(message instanceof SummaryLLMMessage)) {
                userMessages.add(message);
            }
        }
        return userMessages;
    }


    private static SummaryLLMMessage findOldSummary(List<LLMMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof SummaryLLMMessage summary) {
                return summary;
            }
        }
        return null;
    }

    private static String extractSummaryText(List<LLMMessage> response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        for (LLMMessage message : response) {
            if (message.type() == MessageType.AI && message.content() != null) {
                return message.content();
            }
        }
        return null;
    }

    private static List<LLMMessage> buildCompactedHistory(
            List<LLMMessage> userMessages,
            String summaryText,
            SummaryLLMMessage oldSummary,
            List<LLMMessage> allOriginalMessages) {

        int remainingTokens = OpenAIConfig.getRetainedUserMessageMaxTokens();
        List<LLMMessage> selectedMessages = new ArrayList<>();

        for (int i = userMessages.size() - 1; i >= 0; i--) {
            LLMMessage msg = userMessages.get(i);
            String content = msg.content();
            if (content == null || content.isEmpty()) {
                continue;
            }
            int tokenCount = MessageTokenUtil.estimateStringTokens(content);
            if (tokenCount <= remainingTokens) {
                selectedMessages.addFirst(msg);
                remainingTokens -= tokenCount;
            } else if (selectedMessages.isEmpty()) {
                String truncated = truncateText(content, remainingTokens);
                selectedMessages.addFirst(createTruncatedMessage(msg, truncated));
                remainingTokens = 0;
            } else {
                break;
            }
        }

        List<LLMMessage> summariedMessages = new ArrayList<>();
        for (LLMMessage msg : allOriginalMessages) {
            if (!selectedMessages.contains(msg)) {
                summariedMessages.add(msg);
            }
        }

        SummaryLLMMessage summaryMessage = new SummaryLLMMessage();
        summaryMessage.setContent(CompactPrompts.SUMMARY_PREFIX + "\n" + summaryText);
        summaryMessage.setOldSummary(oldSummary);
        summaryMessage.setSummariedMessages(summariedMessages);

        List<LLMMessage> compacted = new ArrayList<>(selectedMessages);
        compacted.add(summaryMessage);
        return compacted;
    }


    private static String truncateText(String text, int maxTokens) {
        int maxChars = maxTokens * 4;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    /**
     * Creates a new LLM message that preserves the original metadata but uses truncated content.
     */
    private static LLMMessage createTruncatedMessage(LLMMessage original, String truncatedContent) {
        return new LLMMessage() {
            @Override
            public String id() {
                return original.id();
            }

            @Override
            public MessageType type() {
                return original.type();
            }

            @Override
            public String content() {
                return truncatedContent;
            }
        };
    }

    private static boolean needCleanUp(List<MemoryRegion> memoryRegions) {
        if (memoryRegions == null || memoryRegions.isEmpty()) {
            return false;
        }
        boolean hasChatMemoryRegion = false;
        for (MemoryRegion memoryRegion : memoryRegions) {
            if (memoryRegion instanceof ChatMemoryRegion) {
                hasChatMemoryRegion = true;
                break;
            }
        }
        if (!(hasChatMemoryRegion)) {
            return false;
        }
        int estimatedTokens = MessageTokenUtil.estimateTokenCount(memoryRegions);
        int autoCompactTokenLimit = (int) (OpenAIConfig.getContextWindowTokens()
                * OpenAIConfig.getCompactionTriggerThreshold());
        if (estimatedTokens < autoCompactTokenLimit) {
            return false;
        }
        log.info("Context usage reached the compaction threshold; generating a summary.");
        return true;
    }

    private static class SummaryLLMMessage implements LLMMessage {
        private final String id;
        @Setter
        private SummaryLLMMessage oldSummary;
        @Setter
        private String content;
        @Setter
        private List<LLMMessage> summariedMessages;

        public SummaryLLMMessage() {
            this.id = MessageIdGenerator.generateWithPrefix("summary");
        }


        @Override
        public String id() {
            return id;
        }

        @Override
        public MessageType type() {
            return MessageType.HUMAN;
        }

        @Override
        public String content() {
            return content;
        }
    }
}
