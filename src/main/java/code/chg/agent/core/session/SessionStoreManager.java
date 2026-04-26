package code.chg.agent.core.session;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionStoreManager
 * @description Defines persistence operations for session snapshots.
 */
public interface SessionStoreManager {
    /**
     * Loads persisted data for a session.
     *
     * @param sessionId the session identifier
     * @return the persisted session data, or {@code null} when it does not exist
     */
    SessionData getSessionData(String sessionId);

    /**
     * Loads persisted metadata for a session.
     *
     * @param sessionId the session identifier
     * @return the persisted session metadata, or {@code null} when it does not exist
     */
    default SessionData getSessionMetadata(String sessionId) {
        return getSessionData(sessionId);
    }

    /**
     * Saves the complete persisted state for a session.
     *
     * @param sessionData the session data to save
     */
    void saveSessionData(SessionData sessionData);
}
