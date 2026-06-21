package com.aow2.server.service;

import com.aow2.server.model.GameSession;
import com.aow2.server.repository.GameSessionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** FIX (H-NEW-9): Spring Data JPA repository for session persistence. Injected via @Autowired. */
    @org.springframework.beans.factory.annotation.Autowired
    private GameSessionRepository sessionRepository;

    /** Active game sessions indexed by session UUID */
    private final Map<String, GameSession> activeSessions = new ConcurrentHashMap<>();

    /** Per-session locks to serialize state transitions and sync hash updates. */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /** Returns the lock object for a given session UUID, creating it if needed. */
    private Object getSessionLock(String sessionUuid) {
        return sessionLocks.computeIfAbsent(sessionUuid, k -> new Object());
    }

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
        // FIX (H-NEW-9): Recover ACTIVE sessions from DB on boot (crash recovery).
        recoverActiveSessions();
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
                    sessionLocks.remove(entry.getKey());
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
    @Transactional
    public GameSession createSession(Long player1Id, Long player2Id, String mapName) {
        GameSession session = new GameSession(player1Id, player2Id, mapName);
        session = sessionRepository.save(session);
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
    @Transactional
    public Optional<GameSession> startSession(String sessionUuid) {
        synchronized (getSessionLock(sessionUuid)) {
            GameSession session = activeSessions.get(sessionUuid);
            if (session == null || session.getState() != GameSession.SessionState.WAITING) {
                return Optional.empty();
            }
            session.setState(GameSession.SessionState.STARTING);
            session.setStartedAt(Instant.now());
            sessionRepository.save(session);
            log.info("Session starting: {}", sessionUuid);
            return Optional.of(session);
        }
    }

    /**
     * Transitions a session to ACTIVE state once both players confirm readiness.
     * REF: protocol_specification.md - GAME_STATE (type 30) sets game mode to 3 (synced)
     *
     * @param sessionUuid the session UUID
     * @return the updated session, or empty if not found
     */
    @Transactional
    public Optional<GameSession> activateSession(String sessionUuid) {
        synchronized (getSessionLock(sessionUuid)) {
            GameSession session = activeSessions.get(sessionUuid);
            if (session == null || session.getState() != GameSession.SessionState.STARTING) {
                return Optional.empty();
            }
            session.setState(GameSession.SessionState.ACTIVE);
            sessionRepository.save(session);
            log.info("Session active: {}", sessionUuid);
            return Optional.of(session);
        }
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
    @Transactional
    public Optional<GameSession> completeSession(String sessionUuid, Long winnerId, int durationSeconds) {
        synchronized (getSessionLock(sessionUuid)) {
            GameSession session = activeSessions.get(sessionUuid);
            if (session == null || session.getState() == GameSession.SessionState.COMPLETED) {
                // Already completed — idempotent guard prevents double ELO recording
                return session != null ? Optional.of(session) : Optional.empty();
            }
            session.setState(GameSession.SessionState.COMPLETED);
            session.setWinnerId(winnerId);
            session.setDurationSeconds(durationSeconds);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            log.info("Session completed: {} (winner: {}, duration: {}s)",
                    sessionUuid, winnerId, durationSeconds);
            return Optional.of(session);
        }
    }

    /**
     * Marks a session as disconnected due to player dropout.
     * REF: multiplayer_architecture.md - Disconnect detection (>3 consecutive errors)
     *
     * @param sessionUuid    the session UUID
     * @param disconnectedId the player ID who disconnected
     * @return the updated session, or empty if not found
     */
    @Transactional
    public Optional<GameSession> disconnectSession(String sessionUuid, Long disconnectedId) {
        synchronized (getSessionLock(sessionUuid)) {
            GameSession session = activeSessions.get(sessionUuid);
            if (session == null || session.getState() == GameSession.SessionState.DISCONNECTED) {
                return session != null ? Optional.of(session) : Optional.empty();
            }
            session.setState(GameSession.SessionState.DISCONNECTED);
            Long winnerId = session.getOpponentId(disconnectedId);
            session.setWinnerId(winnerId);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            log.info("Session disconnected: {} (disconnected: {}, winner: {})",
                    sessionUuid, disconnectedId, winnerId);
            return Optional.of(session);
        }
    }

    /**
     * Records a desync detection event for a specific game tick.
     * Only compares hashes submitted for the same tick number, preventing
     * false positives from comparing state at different points in time.
     * REF: multiplayer_architecture.md - Data integrity: turn sequence validation
     *
     * @param sessionUuid the session UUID
     * @param playerId    the reporting player's ID
     * @param tick        the game tick number this hash corresponds to
     * @param syncHash    the player's state hash at the given tick
     * @return true if a desync was detected between players at the same tick
     */
    public boolean reportSyncHash(String sessionUuid, Long playerId, long tick, long syncHash) {
        synchronized (getSessionLock(sessionUuid)) {
            GameSession session = activeSessions.get(sessionUuid);
            if (session == null) {
                return false;
            }

            // Only accept hashes for the current tracked tick to avoid cross-tick false comparisons
            if (session.getLastSyncTick() != null && session.getLastSyncTick() != tick) {
                // New tick — reset both hashes to compare fresh
                session.setPlayer1SyncHash(null);
                session.setPlayer2SyncHash(null);
            }
            session.setLastSyncTick(tick);

            if (playerId.equals(session.getPlayer1Id())) {
                session.setPlayer1SyncHash(syncHash);
            } else {
                session.setPlayer2SyncHash(syncHash);
            }

            // Check for desync if both hashes are present for the same tick
            if (session.getPlayer1SyncHash() != null && session.getPlayer2SyncHash() != null) {
                boolean desync = !session.getPlayer1SyncHash().equals(session.getPlayer2SyncHash());
                if (desync) {
                    session.setDesyncDetected(true);
                    // FIX (H-NEW-9): Persist desync state to DB for audit trail.
                    try {
                        sessionRepository.save(session);
                    } catch (Exception e) {
                        log.warn("Failed to persist desync state for session {}: {}", sessionUuid, e.getMessage());
                    }
                    log.warn("Desync detected in session {} at tick {} (p1: {}, p2: {})",
                            sessionUuid, tick, session.getPlayer1SyncHash(), session.getPlayer2SyncHash());
                }
                return desync;
            }
            return false;
        }
    }

    /**
     * Gets the session by its UUID.
     * FIX (M-NEW-21): Added for readyPlayers cleanup scheduling in LobbyWebSocketHandler.
     *
     * @param sessionUuid the session UUID
     * @return the session, or empty if not found
     */
    public Optional<GameSession> getSessionByUuid(String sessionUuid) {
        return Optional.ofNullable(activeSessions.get(sessionUuid));
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
     * FIX (M-NEW-22): Logs a warning if a previous session was already registered
     * for this WebSocket session ID, which may indicate a connection leak.
     *
     * @param wsSessionId the WebSocket session ID
     * @param playerId    the player's ID
     */
    public void registerWebSocketSession(String wsSessionId, Long playerId) {
        Long previous = wsSessionToPlayer.put(wsSessionId, playerId);
        if (previous != null && !previous.equals(playerId)) {
            log.warn("Overwriting WebSocket session {} mapping: was player {}, now player {}",
                    wsSessionId, previous, playerId);
        }
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
     * Recovers ACTIVE sessions from the database on application boot.
     * Sessions that were ACTIVE when the server crashed are marked as DISCONNECTED.
     * Sessions that were STARTING are reset to WAITING for re-matching.
     * FIX (H-NEW-9): Enables crash recovery for in-progress games.
     * NOTE: Called by startCleanupScheduler() — not annotated with @PostConstruct to avoid double execution.
     */
    public void recoverActiveSessions() {
        try {
            var active = sessionRepository.findByState(GameSession.SessionState.ACTIVE);
            int recovered = 0;
            for (GameSession session : active) {
                session.setState(GameSession.SessionState.DISCONNECTED);
                session.setCompletedAt(Instant.now());
                sessionRepository.save(session);
                recovered++;
                log.warn("Recovered crashed session: {} (players: {} vs {}), marked DISCONNECTED",
                        session.getSessionUuid(), session.getPlayer1Id(), session.getPlayer2Id());
            }

            var starting = sessionRepository.findByState(GameSession.SessionState.STARTING);
            for (GameSession session : starting) {
                session.setState(GameSession.SessionState.WAITING);
                session.setStartedAt(null);
                sessionRepository.save(session);
                log.info("Reset stuck STARTING session to WAITING: {}", session.getSessionUuid());
            }

            if (recovered > 0 || !starting.isEmpty()) {
                log.info("Session recovery complete: {} ACTIVE->DISCONNECTED, {} STARTING->WAITING",
                        recovered, starting.size());
            }
        } catch (Exception e) {
            log.error("Session recovery failed (non-fatal, in-memory sessions still work): {}", e.getMessage());
        }
    }

    /**
     * Removes a completed or disconnected session from active tracking.
     * Note: The session remains in the database for history/audit.
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
