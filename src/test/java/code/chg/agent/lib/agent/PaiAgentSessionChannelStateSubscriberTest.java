package code.chg.agent.lib.agent;

import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.core.session.SessionData;
import code.chg.agent.lib.session.LocalSessionStoreManager;
import code.chg.agent.lib.session.SessionRuntimeContext;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PaiAgentSessionChannelStateSubscriberTest
 * @description Provides the PaiAgentSessionChannelStateSubscriberTest implementation.
 */
public class PaiAgentSessionChannelStateSubscriberTest {

    @Test
    public void firstAiChunkShouldNotBeAccumulatedTwice() throws Exception {
        SessionRuntimeContext context = new SessionRuntimeContext();
        context.setSessionId("session-channel-state-test");

        LocalSessionStoreManager storeManager = new LocalSessionStoreManager();
        Class<?> subscriberClass = Class.forName("code.chg.agent.lib.agent.PaiAgent$SessionChannelStateSubscriber");
        Constructor<?> constructor = subscriberClass.getDeclaredConstructor(SessionRuntimeContext.class, LocalSessionStoreManager.class);
        constructor.setAccessible(true);
        Object subscriber = constructor.newInstance(context, storeManager);

        ChannelMessage message = ChannelMessageBuilder.builder("msg-1", "assistant", ChannelMessageType.LLM_CONTENT_CHUNK)
                .contentChunk("Hello")
                .build(false);

        Method onMessage = subscriberClass.getDeclaredMethod("onMessage", ChannelMessage.class);
        onMessage.setAccessible(true);
        onMessage.invoke(subscriber, message);

        AIMessageChannelChunk body = (AIMessageChannelChunk) message.body();
        assertEquals("Hello", body.getContentChunk().toString());
    }

    @Test
    public void compactNoticeShouldRefreshEstimatedTokensImmediately() throws Exception {
        SessionRuntimeContext context = new SessionRuntimeContext();
        context.setSessionId("session-compact-token-test");
        context.setEstimatedTokens(68535);

        LocalSessionStoreManager storeManager = new LocalSessionStoreManager();
        SessionData sessionData = new SessionData();
        sessionData.setSessionId(context.getSessionId());
        sessionData.setCreatedAt(System.currentTimeMillis());
        sessionData.setUpdatedAt(System.currentTimeMillis());
        sessionData.setLastActiveAt(System.currentTimeMillis());
        sessionData.setLatestTokenCount(68535);
        storeManager.saveSessionData(sessionData);

        Class<?> subscriberClass = Class.forName("code.chg.agent.lib.agent.PaiAgent$SessionChannelStateSubscriber");
        Constructor<?> constructor = subscriberClass.getDeclaredConstructor(SessionRuntimeContext.class, LocalSessionStoreManager.class);
        constructor.setAccessible(true);
        Object subscriber = constructor.newInstance(context, storeManager);

        ChannelMessage message = ChannelMessageBuilder.builder("compact-1", "system", ChannelMessageType.COMPACT_NOTICE)
                .compactNotice("MANUAL", 68535, 3563, 64972, "Manual compaction completed")
                .build(true);

        Method onMessage = subscriberClass.getDeclaredMethod("onMessage", ChannelMessage.class);
        onMessage.setAccessible(true);
        onMessage.invoke(subscriber, message);

        assertEquals(3563, context.getEstimatedTokens());
        assertEquals(Integer.valueOf(3563), storeManager.getSessionData(context.getSessionId()).getLatestTokenCount());

        storeManager.deleteSession(context.getSessionId());
    }
}