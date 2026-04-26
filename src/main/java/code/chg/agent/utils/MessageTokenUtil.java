package code.chg.agent.utils;

import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.ToolCall;
import code.chg.agent.llm.ToolDefinition;
import code.chg.agent.llm.ToolParameterDefinition;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MessageTokenUtil
 * @description Utility methods for estimating token usage across memory and messages.
 */
public class MessageTokenUtil {

    /**
     * Average characters per token - standard approximation for GPT-family tokenizers.
     */
    private static final double CHARS_PER_TOKEN = 4.0;

    /**
     * Overhead tokens per message for the chat format (role label, delimiters, priming).
     */
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    /**
     * Overhead tokens per tool definition in the function-calling schema.
     */
    private static final int TOOL_DEFINITION_OVERHEAD_TOKENS = 8;

    /**
     * Overhead tokens per tool parameter entry.
     */
    private static final int TOOL_PARAMETER_OVERHEAD_TOKENS = 3;

    /**
     * Overhead tokens per tool call within a message.
     */
    private static final int TOOL_CALL_OVERHEAD_TOKENS = 3;

    /**
     * Base priming tokens added once per chat completion request.
     */
    private static final int REQUEST_BASE_TOKENS = 3;

    /**
     * Estimates the total token count across all memory regions by combining
     * message content tokens (including tool calls in LLM responses) with
     * tool definition tokens.
     *
     * <p>The estimation uses a character-based heuristic (~4 characters per token)
     * plus fixed overhead constants that model the chat-completion wire format.</p>
     *
     * @param memoryRegions the list of memory regions to analyze
     * @return the estimated total token count
     */
    public static int estimateTokenCount(List<MemoryRegion> memoryRegions) {
        if (memoryRegions == null || memoryRegions.isEmpty()) {
            return 0;
        }
        int totalTokens = REQUEST_BASE_TOKENS;
        for (MemoryRegion region : memoryRegions) {
            totalTokens += estimateMessagesTokenCount(region.messages());
            totalTokens += estimateToolDefinitionsTokenCount(region.tools());
        }
        return totalTokens;
    }

    /**
     * Estimates the token count for a list of LLM messages.
     */
    private static int estimateMessagesTokenCount(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (LLMMessage message : messages) {
            tokens += estimateMessageTokenCount(message);
        }
        return tokens;
    }


    private static int estimateMessageTokenCount(LLMMessage message) {
        int tokens = MESSAGE_OVERHEAD_TOKENS;

        String content = message.content();
        if (content != null && !content.isEmpty()) {
            tokens += estimateStringTokens(content);
        }

        List<ToolCall> toolCalls = message.toolCalls();
        if (toolCalls != null) {
            for (ToolCall toolCall : toolCalls) {
                tokens += estimateToolCallTokens(toolCall);
            }
        }

        String toolCallId = message.toolCallId();
        if (toolCallId != null && !toolCallId.isEmpty()) {
            tokens += estimateStringTokens(toolCallId);
        }

        return tokens;
    }


    /**
     * Estimates the token count for a single tool call (function name + JSON arguments).
     */
    private static int estimateToolCallTokens(ToolCall toolCall) {
        int tokens = TOOL_CALL_OVERHEAD_TOKENS;
        if (toolCall.name() != null) {
            tokens += estimateStringTokens(toolCall.name());
        }
        if (toolCall.arguments() != null) {
            tokens += estimateStringTokens(toolCall.arguments());
        }
        return tokens;
    }

    /**
     * Estimates the token count for tool definitions (function schemas) that are
     * sent alongside messages in the request.
     */
    private static int estimateToolDefinitionsTokenCount(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (ToolDefinition tool : tools) {
            tokens += TOOL_DEFINITION_OVERHEAD_TOKENS;
            if (tool.name() != null) {
                tokens += estimateStringTokens(tool.name());
            }
            if (tool.description() != null) {
                tokens += estimateStringTokens(tool.description());
            }
            List<ToolParameterDefinition> params = tool.parameters();
            if (params != null) {
                for (ToolParameterDefinition param : params) {
                    tokens += TOOL_PARAMETER_OVERHEAD_TOKENS;
                    if (param.name() != null) {
                        tokens += estimateStringTokens(param.name());
                    }
                    if (param.description() != null) {
                        tokens += estimateStringTokens(param.description());
                    }
                }
            }
        }
        return tokens;
    }

    /**
     * Estimates the number of tokens in a string using the standard approximation
     * of ~4 characters per token for GPT-family tokenizers.
     *
     * @param text the text to estimate
     * @return the estimated token count, at least 0
     */
    public static int estimateStringTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
