package com.aow2.common.model;

import java.time.Instant;

/**
 * Immutable record for chat message transfer between server and client.
 * Used as the DTO for chat messages in REST and WebSocket communication.
 * REF: protocol_specification.md - Chat messages
 *
 * @param matchId   the match/session UUID this message belongs to
 * @param playerId  the player ID who sent the message
 * @param message   the chat message content
 * @param timestamp the instant when the message was sent
 */
public record ChatMessageRecord(
        String matchId,
        long playerId,
        String message,
        Instant timestamp
) {
}
