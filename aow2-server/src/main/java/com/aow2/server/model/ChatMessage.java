package com.aow2.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a chat message sent during a multiplayer match.
 * Persisted for chat history retrieval and moderation purposes.
 * REF: protocol_specification.md - Chat messages between players
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", length = 36)
    private String matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Default constructor required by JPA.
     */
    public ChatMessage() {
        this.timestamp = Instant.now();
    }

    /**
     * Constructs a ChatMessage with the given details.
     *
     * @param matchId  the match/session UUID this message belongs to
     * @param playerId the player ID who sent the message
     * @param message  the chat message content
     */
    public ChatMessage(String matchId, Long playerId, String message) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
