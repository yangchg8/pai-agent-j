package code.chg.agent.lib.agent;

import code.chg.agent.core.session.HistoryMessageData;
import code.chg.agent.core.session.SessionData;
import code.chg.agent.lib.session.LocalSessionStoreManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PaiAgentSessionLifecycleTest
 * @description Provides the PaiAgentSessionLifecycleTest implementation.
 */
public class PaiAgentSessionLifecycleTest {

    @Test
    public void createSessionShouldNotPersistUntilFirstUserMessage() {
        PaiAgent agent = new PaiAgent(System.getProperty("user.dir"));
        LocalSessionStoreManager storeManager = new LocalSessionStoreManager();
        String sessionId = agent.currentSession().getSessionId();

        assertFalse(storeManager.sessionExists(sessionId));
    }

    @Test
    public void resetShouldDeleteCurrentSessionDataAndCreateFreshSession() {
        LocalSessionStoreManager storeManager = new LocalSessionStoreManager();
        String persistedSessionId = "test-reset-" + System.nanoTime();

        SessionData sessionData = new SessionData();
        long now = System.currentTimeMillis();
        sessionData.setSessionId(persistedSessionId);
        sessionData.setTitle("reset-test");
        sessionData.setCreatedAt(now);
        sessionData.setUpdatedAt(now);
        sessionData.setLastActiveAt(now);
        sessionData.setLatestUserMessage("hello");
        sessionData.setLatestTokenCount(10);
        storeManager.saveSessionData(sessionData);

        HistoryMessageData history = new HistoryMessageData();
        history.setSessionId(persistedSessionId);
        history.setMessageId("history-" + persistedSessionId);
        history.setRole("USER");
        history.setChannelMessageType("HUMAN_MESSAGE");
        history.setContent("hello");
        history.setCompleted(true);
        history.setCreatedAt(now);
        storeManager.appendHistory(history);

        PaiAgent agent = new PaiAgent(System.getProperty("user.dir"));
        agent.loadSession(persistedSessionId);

        String newSessionId = agent.resetCurrentSession().getSessionId();

        assertNotEquals(persistedSessionId, newSessionId);
        assertFalse(storeManager.sessionExists(persistedSessionId));
        assertTrue(storeManager.listHistory(persistedSessionId, 1, 10).isEmpty());
        assertNull(storeManager.loadSnapshot(persistedSessionId));
        assertFalse(storeManager.sessionExists(newSessionId));
    }
}