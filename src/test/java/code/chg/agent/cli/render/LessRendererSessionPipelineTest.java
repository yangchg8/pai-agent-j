package code.chg.agent.cli.render;

import code.chg.agent.core.channel.ChannelMessage;
import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import code.chg.agent.core.channel.body.AIMessageChannelChunk;
import code.chg.agent.lib.session.LocalSessionStoreManager;
import code.chg.agent.lib.session.SessionRuntimeContext;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LessRendererSessionPipelineTest
 * @description Provides the LessRendererSessionPipelineTest implementation.
 */
public class LessRendererSessionPipelineTest {

    @Test
    public void sessionSubscriberAndRendererPipelineShouldNotDuplicateChunkContent() throws Exception {
        SessionRuntimeContext context = new SessionRuntimeContext();
        context.setSessionId("renderer-session-pipeline-test");

        LocalSessionStoreManager storeManager = new LocalSessionStoreManager();
        Class<?> subscriberClass = Class.forName("code.chg.agent.lib.agent.PaiAgent$SessionChannelStateSubscriber");
        Constructor<?> subscriberConstructor = subscriberClass.getDeclaredConstructor(SessionRuntimeContext.class, LocalSessionStoreManager.class);
        subscriberConstructor.setAccessible(true);
        Object subscriber = subscriberConstructor.newInstance(context, storeManager);
        Method onMessage = subscriberClass.getDeclaredMethod("onMessage", code.chg.agent.core.channel.ChannelMessage.class);
        onMessage.setAccessible(true);

        LessRenderer renderer = new LessRenderer(null, LessRenderer.LiveRegion.noop());

        ChannelMessage first = ChannelMessageBuilder.builder("msg-1", "assistant", ChannelMessageType.LLM_CONTENT_CHUNK)
                .contentChunk("Hel")
                .build(false);
        ChannelMessage second = ChannelMessageBuilder.builder("msg-1", "assistant", ChannelMessageType.LLM_CONTENT_CHUNK)
                .contentChunk("lo")
                .build(true);

        onMessage.invoke(subscriber, first);
        renderer.onMessage(first);
        onMessage.invoke(subscriber, second);
        renderer.onMessage(second);

        Field messageIndexField = LessRenderer.class.getDeclaredField("messageIndex");
        messageIndexField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> messageIndex = (Map<String, Object>) messageIndexField.get(renderer);
        Object mutableMessage = messageIndex.get("LLM_CONTENT_CHUNK:msg-1");
        assertTrue(mutableMessage != null);

        Method bodyMethod = mutableMessage.getClass().getDeclaredMethod("body");
        bodyMethod.setAccessible(true);
        AIMessageChannelChunk mergedBody = (AIMessageChannelChunk) bodyMethod.invoke(mutableMessage);
        assertEquals("Hello", mergedBody.getContentChunk().toString());
    }
}