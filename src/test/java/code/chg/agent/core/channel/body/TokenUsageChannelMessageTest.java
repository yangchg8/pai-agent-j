package code.chg.agent.core.channel.body;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title TokenUsageChannelMessageTest
 * @description Provides the TokenUsageChannelMessageTest implementation.
 */
public class TokenUsageChannelMessageTest {

    @Test
    public void accumulateReplacesLatestFields() {
        TokenUsageChannelMessage current = new TokenUsageChannelMessage();
        current.setPromptTokens(10);
        current.setCompletionTokens(20);
        current.setTotalTokens(30);

        TokenUsageChannelMessage next = new TokenUsageChannelMessage();
        next.setPromptTokens(11);
        next.setCompletionTokens(21);
        next.setTotalTokens(31);
        next.setEstimatedContextTokens(200);
        next.setMaxTokens(1000);
        next.setSource("OPENAI_USAGE");

        current.accumulate(next);

        assertEquals(Integer.valueOf(11), current.getPromptTokens());
        assertEquals(Integer.valueOf(21), current.getCompletionTokens());
        assertEquals(Integer.valueOf(31), current.getTotalTokens());
        assertEquals(Integer.valueOf(200), current.getEstimatedContextTokens());
        assertEquals(Integer.valueOf(1000), current.getMaxTokens());
        assertEquals("OPENAI_USAGE", current.getSource());
    }
}