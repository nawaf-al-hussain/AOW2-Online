package com.aow2.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity recording the result of a completed multiplayer match.
 * Used for leaderboard ranking, replay indexing, and player statistics.
 * REF: protocol_specification.md - Type 33 GAME_RESULT (player scores)
 * REF: multiplayer_architecture.md - ELO calculation for ranked matches
 */
@Entity
@Table(name = "match_results")
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "player1_score")
    private Integer player1Score;

    @Column(name = "player2_score")
    private Integer player2Score;

    @Column(name = "player1_elo_before")
    private Integer player1EloBefore;

    @Column(name = "player2_elo_before")
    private Integer player2EloBefore;

    @Column(name = "player1_elo_after")
    private Integer player1EloAfter;

    @Column(name = "player2_elo_after")
    private Integer player2EloAfter;

    @Column(name = "map_name", length = 64)
    private String mapName;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "replay_file_path")
    private String replayFilePath;

    @Column(name = "desync_detected", nullable = false)
    private boolean desyncDetected = false;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    /**
     * Default constructor required by JPA.
     */
    public MatchResult() {
        this.playedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(Long player1Id) { this.player1Id = player1Id; }

    public Long getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(Long player2Id) { this.player2Id = player2Id; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public Integer getPlayer1Score() { return player1Score; }
    public void setPlayer1Score(Integer player1Score) { this.player1Score = player1Score; }

    public Integer getPlayer2Score() { return player2Score; }
    public void setPlayer2Score(Integer player2Score) { this.player2Score = player2Score; }

    public Integer getPlayer1EloBefore() { return player1EloBefore; }
    public void setPlayer1EloBefore(Integer player1EloBefore) { this.player1EloBefore = player1EloBefore; }

    public Integer getPlayer2EloBefore() { return player2EloBefore; }
    public void setPlayer2EloBefore(Integer player2EloBefore) { this.player2EloBefore = player2EloBefore; }

    public Integer getPlayer1EloAfter() { return player1EloAfter; }
    public void setPlayer1EloAfter(Integer player1EloAfter) { this.player1EloAfter = player1EloAfter; }

    public Integer getPlayer2EloAfter() { return player2EloAfter; }
    public void setPlayer2EloAfter(Integer player2EloAfter) { this.player2EloAfter = player2EloAfter; }

    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getReplayFilePath() { return replayFilePath; }
    public void setReplayFilePath(String replayFilePath) { this.replayFilePath = replayFilePath; }

    public boolean isDesyncDetected() { return desyncDetected; }
    public void setDesyncDetected(boolean desyncDetected) { this.desyncDetected = desyncDetected; }

    public Instant getPlayedAt() { return playedAt; }
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }
}
