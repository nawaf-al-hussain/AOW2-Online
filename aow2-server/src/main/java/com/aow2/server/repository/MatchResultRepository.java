package com.aow2.server.repository;

import com.aow2.server.model.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for MatchResult entity persistence and querying.
 * Supports replay browsing and player match history.
 * REF: protocol_specification.md - Type 33 GAME_RESULT (scores)
 */
@Repository
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    /**
     * Finds all match results involving the given player, ordered by most recent first.
     *
     * @param player1Id player ID to search as player1
     * @param player2Id player ID to search as player2
     * @return list of match results involving the player
     */
    List<MatchResult> findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(Long player1Id, Long player2Id);

    /**
     * Finds all match results ordered by most recent first, for replay browsing.
     *
     * @return list of all match results ordered by play date descending
     */
    List<MatchResult> findAllByOrderByPlayedAtDesc();

    /**
     * Counts the number of matches won by a specific player.
     *
     * @param winnerId the winning player's ID
     * @return the count of matches won
     */
    long countByWinnerId(Long winnerId);
}
