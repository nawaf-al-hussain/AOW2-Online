package com.aow2.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an active or completed game session.
 * Tracks the lifecycle of a multiplayer match from matchmaking to completion.
 * REF: session_lifecycle.md - Session states: LOBBY, MATCHMAKING, ACTIVE, COMPLETED
 * REF: protocol_specification.md - Type 4 SESSION_INIT, Type 12 MATCH_START
 */
@Entity
@Table(name = "game_sessions")
public class GameSession {

    /**
     * Lifecycle states for a game session.
     * REF: session_lifecycle.md - State machine transitions
     */
    public enum SessionState {
        /** Session created, waiting for players */
        WAITING,
        /** Both players found, game is initializing */
        STARTING,
        /** Game is actively running */
        ACTIVE,
        /** Game has concluded normally */
        COMPLETED,
        /** Game ended due to player disconnection */
        DISCONNECTED,
        /** Game ended due to synchronization error */
        DESYNC
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_uuid", unique = true, nullable = false)
    private String sessionUuid;

    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    @Column(name = "map_name")
    private String mapName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private SessionState state;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "player1_sync_hash")
    private Long player1SyncHash;

    @Column(name = "player2_sync_hash")
    private Long player2SyncHash;

    @Column(name = "desync_detected")
    private boolean desyncDetected = false;

    /** The last game tick for which sync hashes were reported. */
    @Column(name = "last_sync_tick")
    private Long lastSyncTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Default constructor required by JPA.
     */
    public GameSession() {
        this.sessionUuid = UUID.randomUUID().toString();
        this.state = SessionState.WAITING;
        this.createdAt = Instant.now();
    }

    /**
     * Constructs a GameSession for two matched players.
     *
     * @param player1Id ID of player 1 (Confederation)
     * @param player2Id ID of player 2 (Resistance)
     * @param mapName   name of the map for this session
     */
    public GameSession(Long player1Id, Long player2Id, String mapName) {
        this.sessionUuid = UUID.randomUUID().toString();
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.mapName = mapName;
        this.state = SessionState.WAITING;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionUuid() { return sessionUuid; }
    public void setSessionUuid(String sessionUuid) { this.sessionUuid = sessionUuid; }

    public Long getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(Long player1Id) { this.player1Id = player1Id; }

    public Long getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(Long player2Id) { this.player2Id = player2Id; }

    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }

    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Long getPlayer1SyncHash() { return player1SyncHash; }
    public void setPlayer1SyncHash(Long player1SyncHash) { this.player1SyncHash = player1SyncHash; }

    public Long getPlayer2SyncHash() { return player2SyncHash; }
    public void setPlayer2SyncHash(Long player2SyncHash) { this.player2SyncHash = player2SyncHash; }

    public boolean isDesyncDetected() { return desyncDetected; }
    public void setDesyncDetected(boolean desyncDetected) { this.desyncDetected = desyncDetected; }

    public Long getLastSyncTick() { return lastSyncTick; }
    public void setLastSyncTick(Long lastSyncTick) { this.lastSyncTick = lastSyncTick; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    /**
     * Checks if the given player ID is a participant in this session.
     *
     * @param playerId the player ID to check
     * @return true if the player is in this session
     */
    public boolean isParticipant(Long playerId) {
        return playerId.equals(player1Id) || playerId.equals(player2Id);
    }

    /**
     * Returns the opponent's player ID for the given player.
     *
     * @param playerId the player ID whose opponent to find
     * @return the opponent's player ID, or null if not a participant
     */
    public Long getOpponentId(Long playerId) {
        if (playerId.equals(player1Id)) return player2Id;
        if (playerId.equals(player2Id)) return player1Id;
        return null;
    }
}
