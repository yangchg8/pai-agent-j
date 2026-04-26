package code.chg.agent.lib.session;

import code.chg.agent.core.session.HistoryMessageData;
import code.chg.agent.core.session.SessionData;
import code.chg.agent.core.session.SessionSummaryData;
import code.chg.agent.core.session.SessionStoreManager;
import code.chg.agent.utils.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LocalSessionStoreManager
 * @description SQLite-backed session store for metadata, history, and snapshot files.
 */
public class LocalSessionStoreManager implements SessionStoreManager {
    private static final Path ROOT_DIR = Path.of(System.getProperty("user.home"), ".pai-agent");
    private static final Path DB_PATH = ROOT_DIR.resolve("agent.db");
    private static final Path SESSIONS_DIR = ROOT_DIR.resolve("sessions");
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH.toAbsolutePath().normalize();
    private static final Map<String, ReentrantLock> SESSION_LOCKS = new ConcurrentHashMap<>();
    private static volatile boolean initialized;

    public LocalSessionStoreManager() {
        initIfNeeded();
    }

    @Override
    public SessionData getSessionData(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        ReentrantLock lock = lockForSession(sessionId);
        lock.lock();
        try {
            SessionData metadata = loadMetadata(sessionId);
            Path snapshotPath = resolveSnapshotPath(sessionId);
            if (Files.exists(snapshotPath)) {
                byte[] snapshotBytes = Files.readAllBytes(snapshotPath);
                SessionData snapshotData = JsonUtil.getObjectMapper().readValue(snapshotBytes, SessionData.class);
                return mergeSessionData(metadata, snapshotData);
            }
            return metadata;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load session: " + sessionId, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SessionData getSessionMetadata(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        ReentrantLock lock = lockForSession(sessionId);
        lock.lock();
        try {
            return loadMetadata(sessionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load session metadata: " + sessionId, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveSessionData(SessionData sessionData) {
        if (sessionData == null || sessionData.getSessionId() == null || sessionData.getSessionId().isBlank()) {
            return;
        }
        String sessionId = sessionData.getSessionId();
        ReentrantLock lock = lockForSession(sessionId);
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            if (sessionData.getCreatedAt() == null) {
                sessionData.setCreatedAt(now);
            }
            if (sessionData.getUpdatedAt() == null) {
                sessionData.setUpdatedAt(now);
            }
            if (sessionData.getLastActiveAt() == null) {
                sessionData.setLastActiveAt(now);
            }
            if (sessionData.getLatestTokenCount() == null) {
                sessionData.setLatestTokenCount(0);
            }

            saveSnapshot(sessionId, JsonUtil.getObjectMapper().writeValueAsBytes(sessionData));
            upsertMetadata(sessionData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save session: " + sessionId, e);
        } finally {
            lock.unlock();
        }

    }

    public List<SessionSummaryData> listSessions(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(limit, 1);
        String sql = "SELECT session_id, title, latest_user_message, created_at, last_active_at, latest_token_count "
                + "FROM session_metadata ORDER BY last_active_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            statement.setInt(2, (safePage - 1) * safeLimit);
            ResultSet resultSet = statement.executeQuery();
            List<SessionSummaryData> summaries = new ArrayList<>();
            while (resultSet.next()) {
                SessionSummaryData summary = new SessionSummaryData();
                summary.setSessionId(resultSet.getString("session_id"));
                summary.setTitle(resultSet.getString("title"));
                summary.setLatestUserMessage(resultSet.getString("latest_user_message"));
                summary.setCreatedAt(resultSet.getLong("created_at"));
                summary.setLastActiveAt(resultSet.getLong("last_active_at"));
                summary.setLatestTokenCount(resultSet.getInt("latest_token_count"));
                summaries.add(summary);
            }
            return summaries;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list sessions", e);
        }
    }

    public List<HistoryMessageData> listHistory(String sessionId, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(limit, 1);
        String sql = "SELECT session_id, message_id, role, channel_message_type, content, body_json, summary, render_order, completed, created_at "
                + "FROM session_channel_message WHERE session_id = ? ORDER BY render_order DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            statement.setInt(2, safeLimit);
            statement.setInt(3, (safePage - 1) * safeLimit);
            ResultSet resultSet = statement.executeQuery();
            List<HistoryMessageData> histories = new ArrayList<>();
            while (resultSet.next()) {
                HistoryMessageData history = new HistoryMessageData();
                history.setSessionId(resultSet.getString("session_id"));
                history.setMessageId(resultSet.getString("message_id"));
                history.setRole(resultSet.getString("role"));
                history.setChannelMessageType(resultSet.getString("channel_message_type"));
                history.setContent(resultSet.getString("content"));
                history.setBodyJson(resultSet.getString("body_json"));
                history.setRenderOrder(resultSet.getInt("render_order"));
                history.setCompleted(resultSet.getInt("completed") == 1);
                history.setCreatedAt(resultSet.getLong("created_at"));
                histories.add(history);
            }
            return histories;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list session history: " + sessionId, e);
        }
    }

    public void appendHistory(HistoryMessageData message) {
        if (message == null || message.getSessionId() == null || message.getMessageId() == null) {
            return;
        }
        String sql = "INSERT INTO session_channel_message(session_id, message_id, role, channel_message_type, content, body_json, summary, render_order, completed, created_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, message.getSessionId());
            statement.setString(2, message.getMessageId());
            statement.setString(3, message.getRole());
            statement.setString(4, message.getChannelMessageType());
            statement.setString(5, message.getContent());
            statement.setString(6, message.getBodyJson());
            statement.setString(7, null);
            statement.setInt(8, message.getRenderOrder() == null ? nextRenderOrder(message.getSessionId()) : message.getRenderOrder());
            statement.setInt(9, Boolean.TRUE.equals(message.getCompleted()) ? 1 : 0);
            statement.setLong(10, message.getCreatedAt() == null ? System.currentTimeMillis() : message.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append history for session: " + message.getSessionId(), e);
        }
    }

    public void saveSnapshot(String sessionId, byte[] snapshot) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Path snapshotPath = resolveSnapshotPath(sessionId);
            Files.createDirectories(snapshotPath.getParent());
            Files.write(snapshotPath, snapshot == null ? new byte[0] : snapshot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save snapshot for session: " + sessionId, e);
        }
    }

    public byte[] loadSnapshot(String sessionId) {
        try {
            Path snapshotPath = resolveSnapshotPath(sessionId);
            if (!Files.exists(snapshotPath)) {
                return null;
            }
            return Files.readAllBytes(snapshotPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load snapshot for session: " + sessionId, e);
        }
    }

    public void updateTokenUsage(String sessionId, int totalTokens) {
        updateMetadataFields(sessionId, "UPDATE session_metadata SET latest_token_count = ?, updated_at = ?, last_active_at = ? WHERE session_id = ?",
                statement -> {
                    long now = System.currentTimeMillis();
                    statement.setInt(1, totalTokens);
                    statement.setLong(2, now);
                    statement.setLong(3, now);
                    statement.setString(4, sessionId);
                });
    }

    public boolean sessionExists(String sessionId) {
        String sql = "SELECT 1 FROM session_metadata WHERE session_id = ? LIMIT 1";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check session existence: " + sessionId, e);
        }
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockForSession(sessionId);
        lock.lock();
        try (Connection connection = openConnection()) {
            try (PreparedStatement deleteHistory = connection.prepareStatement(
                    "DELETE FROM session_channel_message WHERE session_id = ?");
                 PreparedStatement deleteMetadata = connection.prepareStatement(
                         "DELETE FROM session_metadata WHERE session_id = ?")) {
                deleteHistory.setString(1, sessionId);
                deleteHistory.executeUpdate();
                deleteMetadata.setString(1, sessionId);
                deleteMetadata.executeUpdate();
            }
            deleteSnapshot(sessionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete session: " + sessionId, e);
        } finally {
            lock.unlock();
        }
    }

    private static void initIfNeeded() {
        if (initialized) {
            return;
        }
        synchronized (LocalSessionStoreManager.class) {
            if (initialized) {
                return;
            }
            try {
                Files.createDirectories(ROOT_DIR);
                Files.createDirectories(SESSIONS_DIR);
                try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS session_metadata ("
                            + "session_id TEXT PRIMARY KEY,"
                            + "title TEXT NOT NULL,"
                            + "status TEXT NOT NULL,"
                            + "created_at INTEGER NOT NULL,"
                            + "updated_at INTEGER NOT NULL,"
                            + "last_active_at INTEGER NOT NULL,"
                            + "latest_user_message TEXT,"
                            + "latest_token_count INTEGER DEFAULT 0,"
                            + "snapshot_path TEXT,"
                            + "snapshot_version INTEGER DEFAULT 1,"
                            + "archived INTEGER DEFAULT 0) ");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS session_channel_message ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "session_id TEXT NOT NULL,"
                            + "message_id TEXT NOT NULL,"
                            + "role TEXT NOT NULL,"
                            + "channel_message_type TEXT NOT NULL,"
                            + "content TEXT,"
                            + "body_json TEXT,"
                            + "summary TEXT,"
                            + "render_order INTEGER NOT NULL,"
                            + "completed INTEGER DEFAULT 1,"
                            + "created_at INTEGER NOT NULL) ");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_session_channel_message_session_order ON session_channel_message(session_id, render_order DESC)");
                }
                initialized = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize local session store", e);
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    private static ReentrantLock lockForSession(String sessionId) {
        return SESSION_LOCKS.computeIfAbsent(sessionId, key -> new ReentrantLock());
    }

    private Path resolveSnapshotPath(String sessionId) {
        return SESSIONS_DIR.resolve(sessionId).resolve("snapshot.bin");
    }

    private void deleteSnapshot(String sessionId) throws Exception {
        Path sessionDir = SESSIONS_DIR.resolve(sessionId);
        if (!Files.exists(sessionDir)) {
            return;
        }
        try (var walk = Files.walk(sessionDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private SessionData loadMetadata(String sessionId) {
        String sql = "SELECT session_id, title, created_at, updated_at, last_active_at, latest_user_message, latest_token_count FROM session_metadata WHERE session_id = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            SessionData sessionData = new SessionData();
            sessionData.setSessionId(resultSet.getString("session_id"));
            sessionData.setTitle(resultSet.getString("title"));
            sessionData.setCreatedAt(resultSet.getLong("created_at"));
            sessionData.setUpdatedAt(resultSet.getLong("updated_at"));
            sessionData.setLastActiveAt(resultSet.getLong("last_active_at"));
            sessionData.setLatestUserMessage(resultSet.getString("latest_user_message"));
            sessionData.setLatestTokenCount(resultSet.getInt("latest_token_count"));
            return sessionData;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load session metadata: " + sessionId, e);
        }
    }

    private SessionData mergeSessionData(SessionData metadata, SessionData snapshotData) {
        if (metadata == null) {
            return snapshotData;
        }
        if (snapshotData == null) {
            return metadata;
        }
        if (metadata.getTitle() != null) {
            snapshotData.setTitle(metadata.getTitle());
        }
        if (metadata.getCreatedAt() != null) {
            snapshotData.setCreatedAt(metadata.getCreatedAt());
        }
        if (metadata.getUpdatedAt() != null) {
            snapshotData.setUpdatedAt(metadata.getUpdatedAt());
        }
        if (metadata.getLastActiveAt() != null) {
            snapshotData.setLastActiveAt(metadata.getLastActiveAt());
        }
        if (metadata.getLatestUserMessage() != null) {
            snapshotData.setLatestUserMessage(metadata.getLatestUserMessage());
        }
        if (metadata.getLatestTokenCount() != null) {
            snapshotData.setLatestTokenCount(metadata.getLatestTokenCount());
        }
        return snapshotData;
    }

    private void upsertMetadata(SessionData sessionData) throws SQLException {
        String sql = "INSERT INTO session_metadata(session_id, title, status, created_at, updated_at, last_active_at, latest_user_message, latest_token_count, snapshot_path, snapshot_version, archived) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0) "
                + "ON CONFLICT(session_id) DO UPDATE SET title=excluded.title, updated_at=excluded.updated_at, last_active_at=excluded.last_active_at, latest_user_message=excluded.latest_user_message, latest_token_count=excluded.latest_token_count, snapshot_path=excluded.snapshot_path";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionData.getSessionId());
            statement.setString(2, sessionData.getTitle() == null ? "" : sessionData.getTitle().strip());
            statement.setString(3, "ACTIVE");
            statement.setLong(4, sessionData.getCreatedAt() == null ? System.currentTimeMillis() : sessionData.getCreatedAt());
            statement.setLong(5, sessionData.getUpdatedAt() == null ? System.currentTimeMillis() : sessionData.getUpdatedAt());
            statement.setLong(6, sessionData.getLastActiveAt() == null ? System.currentTimeMillis() : sessionData.getLastActiveAt());
            statement.setString(7, sessionData.getLatestUserMessage());
            statement.setInt(8, sessionData.getLatestTokenCount() == null ? 0 : sessionData.getLatestTokenCount());
            statement.setString(9, resolveSnapshotPath(sessionData.getSessionId()).toAbsolutePath().normalize().toString());
            statement.executeUpdate();
        }
    }

    private int nextRenderOrder(String sessionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(render_order), 0) + 1 FROM session_channel_message WHERE session_id = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next() ? resultSet.getInt(1) : 1;
        }
    }

    private void updateMetadataFields(String sessionId, String sql, SqlConsumer consumer) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            consumer.accept(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update session metadata: " + sessionId, e);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(PreparedStatement statement) throws SQLException;

    }
}
