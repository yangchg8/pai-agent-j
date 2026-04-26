package code.chg.agent.core.channel.body;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CompactNoticeChannelMessageTest
 * @description Provides the CompactNoticeChannelMessageTest implementation.
 */
public class CompactNoticeChannelMessageTest {

    @Test
    public void accumulateReplacesLatestFields() {
        CompactNoticeChannelMessage current = new CompactNoticeChannelMessage();
        current.setTrigger("AUTO");

        CompactNoticeChannelMessage next = new CompactNoticeChannelMessage();
        next.setTrigger("MANUAL");
        next.setBeforeTokens(300);
        next.setAfterTokens(150);
        next.setReducedTokens(150);
        next.setSummary("done");

        current.accumulate(next);

        assertEquals("MANUAL", current.getTrigger());
        assertEquals(Integer.valueOf(300), current.getBeforeTokens());
        assertEquals(Integer.valueOf(150), current.getAfterTokens());
        assertEquals(Integer.valueOf(150), current.getReducedTokens());
        assertEquals("done", current.getSummary());
    }
}