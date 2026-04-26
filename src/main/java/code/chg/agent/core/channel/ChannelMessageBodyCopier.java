package code.chg.agent.core.channel;

import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.core.channel.body.AuthorizationRequestChannelMessage;
import code.chg.agent.core.channel.body.CompactNoticeChannelMessage;
import code.chg.agent.core.channel.body.ThinkingChannelChunk;
import code.chg.agent.core.channel.body.TokenUsageChannelMessage;
import code.chg.agent.core.channel.body.ToolCallRejectedChannelMessage;
import code.chg.agent.core.channel.body.ToolCallResponseChannelMessage;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChannelMessageBodyCopier
 * @description Provides the ChannelMessageBodyCopier implementation.
 */
public final class ChannelMessageBodyCopier {

    private ChannelMessageBodyCopier() {
    }

    public static ChannelMessageBody copy(ChannelMessageBody body) {
        if (body == null) {
            return null;
        }
        if (body instanceof AIMessageChannelChunk chunk) {
            AIMessageChannelChunk copy = new AIMessageChannelChunk();
            copy.accumulate(chunk);
            return copy;
        }
        if (body instanceof ThinkingChannelChunk chunk) {
            ThinkingChannelChunk copy = new ThinkingChannelChunk();
            copy.accumulate(chunk);
            return copy;
        }
        if (body instanceof AuthorizationRequestChannelMessage message) {
            AuthorizationRequestChannelMessage copy = new AuthorizationRequestChannelMessage();
            copy.setToolCallId(message.getToolCallId());
            copy.setPrompt(message.getPrompt());
            copy.setContent(message.getContent());
            return copy;
        }
        if (body instanceof ToolCallResponseChannelMessage message) {
            ToolCallResponseChannelMessage copy = new ToolCallResponseChannelMessage();
            copy.setToolCallId(message.getToolCallId());
            copy.setToolName(message.getToolName());
            copy.setResponse(message.getResponse());
            return copy;
        }
        if (body instanceof ToolCallRejectedChannelMessage message) {
            ToolCallRejectedChannelMessage copy = new ToolCallRejectedChannelMessage();
            copy.setToolCallId(message.getToolCallId());
            copy.setReason(message.getReason());
            return copy;
        }
        if (body instanceof TokenUsageChannelMessage message) {
            TokenUsageChannelMessage copy = new TokenUsageChannelMessage();
            copy.accumulate(message);
            return copy;
        }
        if (body instanceof CompactNoticeChannelMessage message) {
            CompactNoticeChannelMessage copy = new CompactNoticeChannelMessage();
            copy.accumulate(message);
            return copy;
        }
        return body;
    }
}