package com.aow2.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a registered player account.
 * Tracks authentication credentials and competitive ranking data.
 * REF: protocol_specification.md - Player ID derived from stored value minus 12321
 * REF: multiplayer_architecture.md - ELO-based ranking system
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "elo_rating", nullable = false)
    private int eloRating = 1000;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed = 0;

    @Column(name = "games_won", nullable = false)
    private int gamesWon = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    public Player() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Constructs a new Player with the given username and password hash.
     *
     * @param username     the player's unique username
     * @param passwordHash the bcrypt-hashed password
     */
    public Player(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.eloRating = 1000;
        this.gamesPlayed = 0;
        this.gamesWon = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getEloRating() { return eloRating; }
    public void setEloRating(int eloRating) { this.eloRating = eloRating; }

    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }

    public int getGamesWon() { return gamesWon; }
    public void setGamesWon(int gamesWon) { this.gamesWon = gamesWon; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Records a game result, updating win/loss statistics.
     *
     * @param won true if this player won the match
     */
    public void recordGameResult(boolean won) {
        this.gamesPlayed++;
        if (won) {
            this.gamesWon++;
        }
        this.updatedAt = Instant.now();
    }
}
