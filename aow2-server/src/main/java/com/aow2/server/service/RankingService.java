package com.aow2.server.service;

import com.aow2.server.model.MatchResult;
import com.aow2.server.model.Player;
import com.aow2.server.repository.MatchResultRepository;
import com.aow2.server.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Business logic for ELO calculation and leaderboard management.
 * Implements the standard ELO rating system with K-factor adjustments.
 * REF: protocol_specification.md - Type 70 RANK_DATA
 * REF: multiplayer_architecture.md - ELO-based pairing in matchmaking
 * ASSUMPTION: K-factor of 32 for new players (<30 games), 16 for experienced players
 */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    /** K-factor for players with fewer than 30 games */
    private static final int K_FACTOR_NEW = 32;
    /** K-factor for experienced players (30+ games) */
    private static final int K_FACTOR_EXPERIENCED = 16;
    /** Game count threshold for K-factor transition */
    private static final int EXPERIENCED_THRESHOLD = 30;
    /** Maximum leaderboard entries to return */
    private static final int MAX_LEADERBOARD_ENTRIES = 100;

    private final PlayerRepository playerRepository;
    private final MatchResultRepository matchResultRepository;

    /**
     * Constructs the RankingService with required repositories.
     *
     * @param playerRepository    repository for player data
     * @param matchResultRepository repository for match results
     */
    public RankingService(PlayerRepository playerRepository,
                          MatchResultRepository matchResultRepository) {
        this.playerRepository = playerRepository;
        this.matchResultRepository = matchResultRepository;
    }

    /**
     * Calculates new ELO ratings for both players after a match and persists the result.
     * Uses the standard ELO formula:
     * <pre>
     *   expectedScore = 1 / (1 + 10^((opponentRating - playerRating) / 400))
     *   newRating = oldRating + K * (actualScore - expectedScore)
     * </pre>
     *
     * @param player1Id ID of player 1
     * @param player2Id ID of player 2
     * @param winnerId  ID of the winning player, or null for a draw
     * @param mapName   the map name
     * @param durationSeconds match duration in seconds
     * @return the persisted MatchResult with ELO changes
     */
    @Transactional
    public MatchResult recordMatchResult(Long player1Id, Long player2Id, Long winnerId,
                                          String mapName, int durationSeconds) {
        Player player1 = playerRepository.findById(player1Id)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + player1Id));
        Player player2 = playerRepository.findById(player2Id)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + player2Id));

        int elo1Before = player1.getEloRating();
        int elo2Before = player2.getEloRating();

        // Calculate expected scores
        double expected1 = calculateExpectedScore(elo1Before, elo2Before);
        double expected2 = calculateExpectedScore(elo2Before, elo1Before);

        // Determine actual scores
        double actual1;
        double actual2;
        if (winnerId == null) {
            actual1 = 0.5;
            actual2 = 0.5;
        } else if (winnerId.equals(player1Id)) {
            actual1 = 1.0;
            actual2 = 0.0;
        } else {
            actual1 = 0.0;
            actual2 = 1.0;
        }

        // Calculate new ratings with K-factor
        int k1 = getKFactor(player1.getGamesPlayed());
        int k2 = getKFactor(player2.getGamesPlayed());

        int elo1After = Math.max(0, (int) Math.round(elo1Before + k1 * (actual1 - expected1)));
        int elo2After = Math.max(0, (int) Math.round(elo2Before + k2 * (actual2 - expected2)));

        // Update players
        player1.setEloRating(elo1After);
        player1.recordGameResult(winnerId != null && winnerId.equals(player1Id));
        player2.setEloRating(elo2After);
        player2.recordGameResult(winnerId != null && winnerId.equals(player2Id));

        playerRepository.save(player1);
        playerRepository.save(player2);

        // Create match result record
        MatchResult result = new MatchResult();
        result.setPlayer1Id(player1Id);
        result.setPlayer2Id(player2Id);
        result.setWinnerId(winnerId);
        result.setPlayer1Score(winnerId != null && winnerId.equals(player1Id) ? 1 : 0);
        result.setPlayer2Score(winnerId != null && winnerId.equals(player2Id) ? 1 : 0);
        result.setPlayer1EloBefore(elo1Before);
        result.setPlayer2EloBefore(elo2Before);
        result.setPlayer1EloAfter(elo1After);
        result.setPlayer2EloAfter(elo2After);
        result.setMapName(mapName);
        result.setDurationSeconds(durationSeconds);
        result = matchResultRepository.save(result);

        log.info("Match result recorded: p1({} → {}) vs p2({} → {}), winner: {}",
                elo1Before, elo1After, elo2Before, elo2After, winnerId);

        return result;
    }

    /**
     * Gets the top players on the leaderboard.
     *
     * @param limit maximum number of entries to return (capped at 100)
     * @return list of leaderboard entries
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLeaderboard(int limit) {
        int effectiveLimit = Math.min(limit, MAX_LEADERBOARD_ENTRIES);
        List<Map<String, Object>> leaderboard = new ArrayList<>();

        Iterable<Player> players = playerRepository.findAllByOrderByEloRatingDesc();
        int rank = 1;
        for (Player player : players) {
            if (rank > effectiveLimit) break;
            leaderboard.add(Map.of(
                    "rank", rank,
                    "id", player.getId(),
                    "username", player.getUsername(),
                    "eloRating", player.getEloRating(),
                    "gamesPlayed", player.getGamesPlayed(),
                    "gamesWon", player.getGamesWon(),
                    "winRate", player.getGamesPlayed() > 0
                            ? String.format("%.1f", (double) player.getGamesWon() / player.getGamesPlayed() * 100)
                            : "0.0"
            ));
            rank++;
        }
        return leaderboard;
    }

    /**
     * Gets the ranking information for a specific player.
     *
     * @param playerId the player's ID
     * @return a map with the player's ranking data
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlayerRanking(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        // Calculate approximate rank by counting players with higher ELO
        int rank = 1;
        Iterable<Player> allPlayers = playerRepository.findAllByOrderByEloRatingDesc();
        for (Player p : allPlayers) {
            if (p.getId().equals(playerId)) break;
            rank++;
        }

        return Map.of(
                "rank", rank,
                "id", player.getId(),
                "username", player.getUsername(),
                "eloRating", player.getEloRating(),
                "gamesPlayed", player.getGamesPlayed(),
                "gamesWon", player.getGamesWon(),
                "winRate", player.getGamesPlayed() > 0
                        ? String.format("%.1f", (double) player.getGamesWon() / player.getGamesPlayed() * 100)
                        : "0.0"
        );
    }

    /**
     * Calculates the expected score for a player given both ELO ratings.
     *
     * @param playerRating   the player's ELO rating
     * @param opponentRating the opponent's ELO rating
     * @return the expected score (0.0 to 1.0)
     */
    double calculateExpectedScore(int playerRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - playerRating) / 400.0));
    }

    /**
     * Returns the K-factor based on the player's experience level.
     *
     * @param gamesPlayed the number of games the player has played
     * @return the K-factor (32 for new, 16 for experienced)
     */
    int getKFactor(int gamesPlayed) {
        return gamesPlayed < EXPERIENCED_THRESHOLD ? K_FACTOR_NEW : K_FACTOR_EXPERIENCED;
    }
}
