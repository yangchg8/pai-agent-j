package code.chg.agent.core.brain.process;

import code.chg.agent.core.brain.AbstractBrain;
import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.channel.ChatChannel;
import code.chg.agent.core.event.EventBusContext;
import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.config.OpenAIConfig;
import code.chg.agent.llm.*;
import code.chg.agent.utils.MessageIdGenerator;
import code.chg.agent.utils.MessageTokenUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMCallProcessor
 * @description Processor responsible for invoking the LLM with the assembled memory state.
 */
@Slf4j
public class LLMCallProcessor implements EventMessageProcessor {
    private final AbstractBrain brain;

    public LLMCallProcessor(AbstractBrain brain) {
        this.brain = brain;
    }

    @Override
    public EventMessageResponse process(EventMessage message, EventBusContext context, EventMessageProcessorChain chain) {
        log.debug("start to call model, agent name:{}, event message type:{}", brain.getName(), message.type());
        List<MemoryRegion> memoryRegions = brain.getMemoryRegions();
        List<LLMMessage> messages = new ArrayList<>();
        Map<String, ToolDefinition> toolDefinitions = new LinkedHashMap<>();
        for (MemoryRegion memoryRegion : memoryRegions) {
            List<LLMMessage> llmMessages = memoryRegion.messages();
            if (llmMessages != null && !llmMessages.isEmpty()) {
                messages.addAll(llmMessages);
            }
            List<Tool> regionTools = memoryRegion.tools();
            if (regionTools != null && !regionTools.isEmpty()) {
                for (ToolDefinition toolDefinition : regionTools) {
                    if (toolDefinition != null && toolDefinition.name() != null) {
                        toolDefinitions.putIfAbsent(toolDefinition.name(), toolDefinition);
                    }
                }
            }
        }
        if (messages.isEmpty()) {
            return new EventMessageResponse(null, false, "memory is empty");
        }

        LLMRequest request = new LLMRequest(messages, new ArrayList<>(toolDefinitions.values()));
        List<LLMMessage> llmMessages = streamChat(request, context);
        return new EventMessageResponse(llmMessages, true, null);
    }

    private List<LLMMessage> streamChat(LLMRequest request, EventBusContext context) {
        LLMClient llmClient = brain.client();
        ChatChannel channel = context.chat();
        boolean thinkingCompleted = false;
        try (StreamResponse response = llmClient.streamChat(request)) {
            while (response.hasNext()) {
                LLMMessageChunk chunk = response.next();

                String thinkingContent = chunk.thinkingContent();
                String content = chunk.content();
                if (thinkingContent != null && !thinkingContent.isEmpty()) {
                    ChannelMessageBuilder thinkingBuilder = ChannelMessageBuilder
                            .builder(chunk.id(), brain.name(), ChannelMessageType.THINKING_CHUNK)
                            .thinkingChunk(thinkingContent);
                    channel.publish(thinkingBuilder.build(false));
                } else if (content != null && !content.isEmpty() && !thinkingCompleted) {
                    ChannelMessageBuilder thinkingBuilder = ChannelMessageBuilder
                            .builder(chunk.id(), brain.name(), ChannelMessageType.THINKING_CHUNK)
                            .thinkingChunk("");
                    channel.publish(thinkingBuilder.build(true));
                    thinkingCompleted = true;
                }

                List<ToolCallChunk> toolCalls = chunk.toolCalls();
                if ((content == null || content.isEmpty()) && (toolCalls == null || toolCalls.isEmpty())) {
                    continue;
                }
                ChannelMessageBuilder builder = ChannelMessageBuilder.builder(chunk.id(), brain.name(), ChannelMessageType.LLM_CONTENT_CHUNK)
                        .contentChunk(content);

                if (toolCalls != null) {
                    for (ToolCallChunk toolCallChunk : toolCalls) {
                        builder.toolCallChunk(toolCallChunk.index(), toolCallChunk.id(), toolCallChunk.name(), toolCallChunk.arguments());
                    }
                }
                channel.publish(builder.build(!response.hasNext()));
            }
            List<LLMMessage> completion = response.completion();
            TokenUsage usage = response.usage();
            int estimatedContextTokens = MessageTokenUtil.estimateTokenCount(brain.getMemoryRegions());
            logTokenUsage(usage, estimatedContextTokens);
            channel.publish(ChannelMessageBuilder.builder(
                            completion == null || completion.isEmpty() ? MessageIdGenerator.generateWithPrefix("token-usage") : completion.getFirst().id(),
                            brain.name(),
                            ChannelMessageType.TOKEN_USAGE)
                    .tokenUsage(
                            usage == null ? null : usage.getPromptTokens(),
                            usage == null ? null : usage.getCompletionTokens(),
                            usage == null ? null : usage.getTotalTokens(),
                            estimatedContextTokens,
                            OpenAIConfig.getToken(),
                            usage == null ? "ESTIMATED" : "OPENAI_USAGE")
                    .build(true));
            return completion;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void logTokenUsage(TokenUsage usage, int estimatedContextTokens) {
        if (usage == null) {
            log.info("llm usage | inputTokens=N/A | outputTokens=N/A | cacheHitRate=N/A | totalContextTokens={} | source=ESTIMATED",
                    estimatedContextTokens);
            return;
        }
        int inputTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int outputTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        int cachedPromptTokens = usage.getCachedPromptTokens() == null ? 0 : usage.getCachedPromptTokens();
        double cacheHitRate = inputTokens <= 0 ? 0D : (cachedPromptTokens * 100.0D) / inputTokens;
        log.info("llm usage | inputTokens={} | outputTokens={} | cachedPromptTokens={} | cacheHitRate={} | totalTokens={} | totalContextTokens={} | source=OPENAI_USAGE",
                inputTokens,
                outputTokens,
                cachedPromptTokens,
                String.format(Locale.ROOT, "%.2f%%", cacheHitRate),
                usage.getTotalTokens() == null ? 0 : usage.getTotalTokens(),
                estimatedContextTokens);
    }

}
