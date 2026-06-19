package com.aow2.server.service;

import com.aow2.server.model.GameSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Business logic for game session lifecycle management and signaling.
 * Manages session creation, state transitions, and WebSocket session tracking.
 * REF: session_lifecycle.md - Complete session lifecycle (LOBBY → MATCHMAKING → ACTIVE → COMPLETED)
 * REF: protocol_specification.md - Type 4 SESSION_INIT, Type 12 MATCH_START, Type 30 GAME_STATE
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Active game sessions indexed by session UUID */
    private final Map<String, GameSession> activeSessions = new ConcurrentHashMap<>();

    /** Player ID to session UUID mapping for quick lookups */
    private final Map<Long, String> playerSessions = new ConcurrentHashMap<>();

    /** WebSocket session IDs mapped to player IDs, for signaling */
    private final Map<String, Long> wsSessionToPlayer = new ConcurrentHashMap<>();

    /** Reverse lookup: player ID to WebSocket session ID, for O(1) player-to-session resolution */
    private final Map<Long, String> playerToWsSession = new ConcurrentHashMap<>();

    /** Player IDs mapped to their opponent's WebSocket session ID, for P2P relay */
    private final Map<Long, String> playerToOpponentWs = new ConcurrentHashMap<>();

    /** Scheduled executor for periodic cleanup of expired sessions. */
    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-cleanup");
                t.setDaemon(true);
                return t;
            });

    /**
     * Starts the scheduled session cleanup task.
     * Runs every 5 minutes and removes completed/disconnected sessions older than 10 minutes.
     */
    @PostConstruct
    public void startCleanupScheduler() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
        log.info("Session cleanup scheduler started (interval=5min, expiry=10min)");
    }

    /**
     * Shuts down the cleanup executor on application shutdown.
     */
    @PreDestroy
    public void stopCleanupScheduler() {
        cleanupExecutor.shutdownNow();
        log.info("Session cleanup scheduler stopped");
    }

    /**
     * Removes completed or disconnected sessions that have been inactive for more than 10 minutes.
     */
    private void cleanupExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        long expiryMs = TimeUnit.MINUTES.toMillis(10);
        int cleaned = 0;

        Iterator<Map.Entry<String, GameSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            GameSession session = entry.getValue();
            GameSession.SessionState state = session.getState();

            if (state == GameSession.SessionState.COMPLETED || state == GameSession.SessionState.DISCONNECTED) {
                Instant completedAt = session.getCompletedAt();
                if (completedAt != null && (now - completedAt.toEpochMilli()) > expiryMs) {
                    it.remove();
                    playerSessions.values().remove(entry.getKey());
                    playerToOpponentWs.remove(session.getPlayer1Id());
                    playerToOpponentWs.remove(session.getPlayer2Id());
                    // FIX (H12): Also clean up stale wsSessionToPlayer entries for both players.
                    // These accumulate when WebSocket disconnects bypass unregisterWebSocketSession().
                    wsSessionToPlayer.entrySet().removeIf(e ->
                        e.getValue().equals(session.getPlayer1Id()) ||
                        e.getValue().equals(session.getPlayer2Id()));
                    cleaned++;
                    log.info("Cleaned up expired session: {} (state={}, completedAt={})",
                            entry.getKey(), state, completedAt);
                }
            }
        }

        if (cleaned > 0) {
            log.info("Session cleanup: removed {} expired sessions, {} active remaining",
                    cleaned, activeSessions.size());
        }
    }

    /**
     * Creates a new game session for two matched players.
     * Sets the initial state to WAITING.
     *
     * @param player1Id ID of player 1 (Confederation)
     * @param player2Id ID of player 2 (Resistance)
     * @param mapName   the map for this session
     * @return the created GameSession
     */
    public GameSession createSession(Long player1Id, Long player2Id, String mapName) {
        GameSession session = new GameSession(player1Id, player2Id, mapName);
        activeSessions.put(session.getSessionUuid(), session);
        playerSessions.put(player1Id, session.getSessionUuid());
        playerSessions.put(player2Id, session.getSessionUuid());
        log.info("Session created: {} (players: {} vs {}, map: {})",
                session.getSessionUuid(), player1Id, player2Id, mapName);
        return session;
    }

    /**
     * Transitions a session from WAITING to STARTING.
     * REF: session_lifecycle.md - MATCH_START triggers transition to screen 31
     *
     * @param sessionUuid the session UUID
     * @return the updated session, or empty if not found or invalid state
     */
    public Optional<GameSession> startSession(String sessionUuid) {
        GameSession session = activeSessions.get(sessionUuid);
        if (session == null || session.getState() != GameSession.SessionState.WAITING) {
            return Optional.empty();
        }
        session.setState(GameSession.SessionState.STARTING);
        session.setStartedAt(Instant.now());
        log.info("Session starting: {}", sessionUuid);
        return Optional.of(session);
    }

    /**
     * Transitions a session to ACTIVE state once both players confirm readiness.
     * REF: protocol_specification.md - GAME_STATE (type 30) sets game mode to 3 (synced)
     *
     * @param sessionUuid the session UUID
     * @return the updated session, or empty if not found
     */
    public Optional<GameSession> activateSession(String sessionUuid) {
        GameSession session = activeSessions.get(sessionUuid);
        if (session == null || session.getState() != GameSession.SessionState.STARTING) {
            return Optional.empty();
        }
        session.setState(GameSession.SessionState.ACTIVE);
        log.info("Session active: {}", sessionUuid);
        return Optional.of(session);
    }

    /**
     * Completes a session with a winner.
     * REF: protocol_specification.md - Type 33 GAME_RESULT
     *
     * @param sessionUuid     the session UUID
     * @param winnerId        the winning player's ID
     * @param durationSeconds the match duration in seconds
     * @return the completed session, or empty if not found
     */
    public Optional<GameSession> completeSession(String sessionUuid, Long winnerId, int durationSeconds) {
        GameSession session = activeSessions.get(sessionUuid);
        if (session == null) {
            return Optional.empty();
        }
        session.setState(GameSession.SessionState.COMPLETED);
        session.setWinnerId(winnerId);
        session.setDurationSeconds(durationSeconds);
        session.setCompletedAt(Instant.now());
        log.info("Session completed: {} (winner: {}, duration: {}s)",
                sessionUuid, winnerId, durationSeconds);
        return Optional.of(session);
    }

    /**
     * Marks a session as disconnected due to player dropout.
     * REF: multiplayer_architecture.md - Disconnect detection (>3 consecutive errors)
     *
     * @param sessionUuid    the session UUID
     * @param disconnectedId the player ID who disconnected
     * @return the updated session, or empty if not found
     */
    public Optional<GameSession> disconnectSession(String sessionUuid, Long disconnectedId) {
        GameSession session = activeSessions.get(sessionUuid);
        if (session == null) {
            return Optional.empty();
        }
        session.setState(GameSession.SessionState.DISCONNECTED);
        Long winnerId = session.getOpponentId(disconnectedId);
        session.setWinnerId(winnerId);
        session.setCompletedAt(Instant.now());
        log.info("Session disconnected: {} (disconnected: {}, winner: {})",
                sessionUuid, disconnectedId, winnerId);
        return Optional.of(session);
    }

    /**
     * Records a desync detection event.
     * REF: multiplayer_architecture.md - Data integrity: turn sequence validation
     *
     * @param sessionUuid the session UUID
     * @param playerId    the reporting player's ID
     * @param syncHash    the player's state hash
     * @return true if a desync was detected between players
     */
    public boolean reportSyncHash(String sessionUuid, Long playerId, long syncHash) {
        GameSession session = activeSessions.get(sessionUuid);
        if (session == null) {
            return false;
        }

        if (playerId.equals(session.getPlayer1Id())) {
            session.setPlayer1SyncHash(syncHash);
        } else {
            session.setPlayer2SyncHash(syncHash);
        }

        // Check for desync if both hashes are present
        if (session.getPlayer1SyncHash() != null && session.getPlayer2SyncHash() != null) {
            boolean desync = !session.getPlayer1SyncHash().equals(session.getPlayer2SyncHash());
            if (desync) {
                session.setDesyncDetected(true);
                log.warn("Desync detected in session {} (p1: {}, p2: {})",
                        sessionUuid, session.getPlayer1SyncHash(), session.getPlayer2SyncHash());
            }
            return desync;
        }
        return false;
    }

    /**
     * Gets the session a player is currently participating in.
     *
     * @param playerId the player's ID
     * @return the session, or empty if not in a session
     */
    public Optional<GameSession> getSessionForPlayer(Long playerId) {
        String sessionUuid = playerSessions.get(playerId);
        if (sessionUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeSessions.get(sessionUuid));
    }

    /**
     * Registers a WebSocket session for a player.
     *
     * @param wsSessionId the WebSocket session ID
     * @param playerId    the player's ID
     */
    public void registerWebSocketSession(String wsSessionId, Long playerId) {
        wsSessionToPlayer.put(wsSessionId, playerId);
        playerToWsSession.put(playerId, wsSessionId);
    }

    /**
     * Unregisters a WebSocket session.
     *
     * @param wsSessionId the WebSocket session ID to remove
     */
    public void unregisterWebSocketSession(String wsSessionId) {
        Long playerId = wsSessionToPlayer.remove(wsSessionId);
        if (playerId != null) {
            playerToWsSession.remove(playerId);
        }
        playerToOpponentWs.values().remove(wsSessionId);
    }

    /**
     * Gets the player ID associated with a WebSocket session.
     *
     * @param wsSessionId the WebSocket session ID
     * @return the player ID, or null if not registered
     */
    public Long getPlayerForWsSession(String wsSessionId) {
        return wsSessionToPlayer.get(wsSessionId);
    }

    /**
     * Gets the WebSocket session ID for a given player.
     * Provides O(1) reverse lookup without scanning all sessions.
     *
     * @param playerId the player's ID
     * @return the WebSocket session ID, or null if not registered
     */
    public String getWsSessionForPlayer(Long playerId) {
        return playerToWsSession.get(playerId);
    }

    /**
     * Links two players' WebSocket sessions for P2P relay.
     *
     * @param player1Id player 1's ID
     * @param player2Id player 2's ID
     * @param ws1       player 1's WebSocket session ID
     * @param ws2       player 2's WebSocket session ID
     */
    public void linkPlayers(Long player1Id, Long player2Id, String ws1, String ws2) {
        playerToOpponentWs.put(player1Id, ws2);
        playerToOpponentWs.put(player2Id, ws1);
    }

    /**
     * Gets the opponent's WebSocket session ID for a given player.
     *
     * @param playerId the player's ID
     * @return the opponent's WebSocket session ID, or null
     */
    public String getOpponentWsSession(Long playerId) {
        return playerToOpponentWs.get(playerId);
    }

    /**
     * Removes a completed or disconnected session from active tracking.
     *
     * @param sessionUuid the session UUID to remove
     */
    public void removeSession(String sessionUuid) {
        GameSession session = activeSessions.remove(sessionUuid);
        if (session != null) {
            playerSessions.remove(session.getPlayer1Id());
            playerSessions.remove(session.getPlayer2Id());
            playerToOpponentWs.remove(session.getPlayer1Id());
            playerToOpponentWs.remove(session.getPlayer2Id());
            playerToWsSession.remove(session.getPlayer1Id());
            playerToWsSession.remove(session.getPlayer2Id());
        }
    }
}
