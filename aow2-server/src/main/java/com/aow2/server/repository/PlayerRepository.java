package com.aow2.server.repository;

import com.aow2.server.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
