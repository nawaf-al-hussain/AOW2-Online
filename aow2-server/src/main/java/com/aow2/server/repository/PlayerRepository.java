package com.aow2.server.repository;

import com.aow2.server.model.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for Player entity persistence and querying.
 * Provides lookup by username for authentication and by ELO for leaderboard ranking.
 * REF: protocol_specification.md - Player ID validation via S() - 12321
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /**
     * Finds a player by their unique username.
     *
     * @param username the username to search for
     * @return an Optional containing the player if found
     */
    Optional<Player> findByUsername(String username);

    /**
     * Checks whether a player with the given username exists.
     *
     * @param username the username to check
     * @return true if a player with this username exists
     */
    boolean existsByUsername(String username);

    /**
     * Finds all players ordered by ELO rating descending, for leaderboard.
     * Uses the database index on elo_rating.
     *
     * @return iterable of players sorted by ELO descending
     */
    Iterable<Player> findAllByOrderByEloRatingDesc();

    /**
     * Finds players ordered by ELO rating descending, with pagination support.
     * Uses the database index on elo_rating for efficient querying.
     *
     * @param pageable pagination information (page number and size)
     * @return a page of players sorted by ELO descending
     */
    Page<Player> findAllByOrderByEloRatingDesc(Pageable pageable);

    /**
     * Counts the number of players with a strictly higher ELO rating.
     * Used for efficient rank calculation without loading all players into memory.
     *
     * @param eloRating the ELO rating to compare against
     * @return count of players with a higher ELO rating
     */
    long countByEloRatingGreaterThan(int eloRating);

    /**
     * Counts players who registered at or after the given timestamp.
     * <p>
     * FIX (H8 from CRITICAL_ANALYSIS_REPORT.md): Used by StatsController to compute
     * "new players today / this week" for the web dashboard.
     *
     * @param after the inclusive lower-bound timestamp
     * @return number of players whose createdAt is at or after {@code after}
     */
    long countByCreatedAtAfter(Instant after);
}
