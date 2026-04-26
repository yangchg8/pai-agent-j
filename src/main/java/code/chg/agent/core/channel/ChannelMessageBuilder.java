package code.chg.agent.core.channel;

import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.core.channel.body.AuthorizationRequestChannelMessage;
import code.chg.agent.core.channel.body.CompactNoticeChannelMessage;
import code.chg.agent.core.channel.body.ThinkingChannelChunk;
import code.chg.agent.core.channel.body.TokenUsageChannelMessage;
import code.chg.agent.core.channel.body.ToolCallRejectedChannelMessage;
import code.chg.agent.core.channel.body.ToolCallResponseChannelMessage;
import code.chg.agent.llm.component.AuthorizationRequirementContent;

import static code.chg.agent.core.channel.ChannelMessageType.*;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChannelMessageBuilder
 * @description Builder for immutable channel messages and their typed bodies.
 */
public class ChannelMessageBuilder {
    private final String id;
    private final String name;
    private final ChannelMessageType type;
    private final AuthorizationRequestChannelMessage authorizationRequestChannelMessage;
    private final AIMessageChannelChunk aiMessageChannelChunk;
    private final ToolCallRejectedChannelMessage toolCallRejectedChannelMessage;
    private final ThinkingChannelChunk thinkingChannelChunk;
    private final ToolCallResponseChannelMessage toolCallResponseChannelMessage;
    private final TokenUsageChannelMessage tokenUsageChannelMessage;
    private final CompactNoticeChannelMessage compactNoticeChannelMessage;

    public static ChannelMessageBuilder builder(String id, String name, ChannelMessageType type) {
        return new ChannelMessageBuilder(id, name, type);
    }

    private ChannelMessageBuilder(String id, String name, ChannelMessageType type) {
        this.id = id;
        this.name = name;
        this.type = type;
        if (type == LLM_CONTENT_CHUNK) {
            aiMessageChannelChunk = new AIMessageChannelChunk();
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = null;
        } else if (type == THINKING_CHUNK) {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = new ThinkingChannelChunk();
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = null;
        } else if (type == TOOL_AUTHORIZATION_REQUEST) {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = new AuthorizationRequestChannelMessage();
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = null;
        } else if (type == TOOL_CALL_REJECTED) {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = new ToolCallRejectedChannelMessage();
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = null;
        } else if (type == TOOL_CALL_RESPONSE) {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = new ToolCallResponseChannelMessage();
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = null;
        } else if (type == TOKEN_USAGE) {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = new TokenUsageChannelMessage();
            compactNoticeChannelMessage = null;
        } else if (type == COMPACT_NOTICE) {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = new CompactNoticeChannelMessage();
        } else {
            aiMessageChannelChunk = null;
            authorizationRequestChannelMessage = null;
            toolCallRejectedChannelMessage = null;
            thinkingChannelChunk = null;
            toolCallResponseChannelMessage = null;
            tokenUsageChannelMessage = null;
            compactNoticeChannelMessage = null;
        }
    }

    public ChannelMessageBuilder contentChunk(String content) {
        if (aiMessageChannelChunk == null) {
            throw new RuntimeException("type is not match");
        }
        aiMessageChannelChunk.accumulateContentChunk(content);
        return this;
    }

    public ChannelMessageBuilder toolCallChunk(int index, String callId, String name, String argument) {
        if (aiMessageChannelChunk == null) {
            throw new RuntimeException("type is not match");
        }
        aiMessageChannelChunk.accumulateToolCallChunk(index, callId, name, argument);
        return this;
    }

    /**
     * Append a chunk of thinking/reasoning content.
     */
    public ChannelMessageBuilder thinkingChunk(String thinking) {
        if (thinkingChannelChunk == null) {
            throw new RuntimeException("type is not THINKING_CHUNK");
        }
        thinkingChannelChunk.accumulateThinking(thinking);
        return this;
    }

    public ChannelMessageBuilder authorizationRequirement(String toolCallId, AuthorizationRequirementContent content) {
        if (authorizationRequestChannelMessage == null) {
            throw new RuntimeException("type is not match");
        }
        authorizationRequestChannelMessage.setToolCallId(toolCallId);
        authorizationRequestChannelMessage.setPrompt(content.getTips());
        authorizationRequestChannelMessage.setContent(content);
        return this;
    }

    public ChannelMessageBuilder toolCallRejected(String toolCallId, String reason) {
        if (toolCallRejectedChannelMessage == null) {
            throw new RuntimeException("type is not match");
        }
        toolCallRejectedChannelMessage.setToolCallId(toolCallId);
        toolCallRejectedChannelMessage.setReason(reason);
        return this;
    }

    public ChannelMessageBuilder toolCallResponse(String toolCallId, String toolName, String response) {
        if (toolCallResponseChannelMessage == null) {
            throw new RuntimeException("type is not TOOL_CALL_RESPONSE");
        }
        toolCallResponseChannelMessage.setToolCallId(toolCallId);
        toolCallResponseChannelMessage.setToolName(toolName);
        toolCallResponseChannelMessage.setResponse(response);
        return this;
    }

    public ChannelMessageBuilder tokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                            Integer estimatedContextTokens, Integer maxTokens, String source) {
        if (tokenUsageChannelMessage == null) {
            throw new RuntimeException("type is not TOKEN_USAGE");
        }
        tokenUsageChannelMessage.setPromptTokens(promptTokens);
        tokenUsageChannelMessage.setCompletionTokens(completionTokens);
        tokenUsageChannelMessage.setTotalTokens(totalTokens);
        tokenUsageChannelMessage.setEstimatedContextTokens(estimatedContextTokens);
        tokenUsageChannelMessage.setMaxTokens(maxTokens);
        tokenUsageChannelMessage.setSource(source);
        return this;
    }

    public ChannelMessageBuilder compactNotice(String trigger, Integer beforeTokens,
                                               Integer afterTokens, Integer reducedTokens, String summary) {
        if (compactNoticeChannelMessage == null) {
            throw new RuntimeException("type is not COMPACT_NOTICE");
        }
        compactNoticeChannelMessage.setTrigger(trigger);
        compactNoticeChannelMessage.setBeforeTokens(beforeTokens);
        compactNoticeChannelMessage.setAfterTokens(afterTokens);
        compactNoticeChannelMessage.setReducedTokens(reducedTokens);
        compactNoticeChannelMessage.setSummary(summary);
        return this;
    }

    public ChannelMessage build(boolean completed) {
        return new ChannelMessage() {
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
                if (type == LLM_CONTENT_CHUNK) {
                    return aiMessageChannelChunk;
                } else if (type == THINKING_CHUNK) {
                    return thinkingChannelChunk;
                } else if (type == TOOL_AUTHORIZATION_REQUEST) {
                    return authorizationRequestChannelMessage;
                } else if (type == TOOL_CALL_REJECTED) {
                    return toolCallRejectedChannelMessage;
                } else if (type == TOOL_CALL_RESPONSE) {
                    return toolCallResponseChannelMessage;
                } else if (type == TOKEN_USAGE) {
                    return tokenUsageChannelMessage;
                } else if (type == COMPACT_NOTICE) {
                    return compactNoticeChannelMessage;
                } else {
                    return null;
                }
            }

            @Override
            public boolean completed() {
                return completed;
            }
        };
    }

}
