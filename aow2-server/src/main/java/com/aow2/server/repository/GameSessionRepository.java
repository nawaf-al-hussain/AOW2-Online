package com.aow2.server.repository;

import com.aow2.server.model.GameSession;
import com.aow2.server.model.GameSession.SessionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for GameSession entity persistence.
 * FIX (H-NEW-9): Enables DB persistence of game sessions instead of in-memory-only storage.
 * Supports session history queries, crash recovery, and admin audit.
 */
@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    /**
     * Finds a session by its unique UUID.
     *
     * @param sessionUuid the session UUID
     * @return the session, or empty if not found
     */
    Optional<GameSession> findBySessionUuid(String sessionUuid);

    /**
     * Finds all sessions for a given player (as player1 or player2), ordered by creation time.
     *
     * @param playerId the player ID
     * @return list of sessions involving the player
     */
    List<GameSession> findByPlayer1IdOrPlayer2IdOrderByCreatedAtDesc(Long playerId, Long playerId2);

    /**
     * Finds all sessions in a given state, for admin/recovery purposes.
     *
     * @param state the session state to filter by
     * @return list of sessions in that state
     */
    List<GameSession> findByState(SessionState state);

    /**
     * Counts sessions in a given state.
     *
     * @param state the session state
     * @return count of sessions in that state
     */
    long countByState(SessionState state);
}