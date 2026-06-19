package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ELO rating service for competitive matchmaking.
 * Implements the standard ELO formula with variable K-factor based on player experience.
 * REF: multiplayer_architecture.md - ELO-based ranking for competitive play
 *
 * @deprecated This service duplicates functionality already provided by {@link RankingService},
 *             which is the canonical ELO implementation. The K-factor for experienced players
 *             was incorrectly set to 24 here (should be 16 per canonical source).
 *             Prefer using {@link RankingService#recordMatchResult} instead.
 */
@Deprecated
@Service
public class EloRatingService {

    private static final Logger log = LoggerFactory.getLogger(EloRatingService.class);

    /** Starting ELO rating for all new players */
    public static final int STARTING_ELO = 1000;

    /** K-factor for new players (fewer than 30 games) */
    static final int K_FACTOR_NEW = 32;

    /** K-factor for experienced players (30+ games) — must match RankingService canonical value */
    static final int K_FACTOR_EXPERIENCED = 16;

    /** Game count threshold for K-factor transition */
    static final int EXPERIENCED_THRESHOLD = 30;

    private final PlayerRepository playerRepository;

    /**
     * Constructs the EloRatingService.
     *
     * @param playerRepository repository for player data
     */
    public EloRatingService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Calculates new ELO ratings for both players after a match.
     * Uses the standard ELO formula with variable K-factor.
     *
     * @param winnerRating the winner's current ELO rating
     * @param loserRating  the loser's current ELO rating
     * @return an int[2] where [0] is the winner's new rating and [1] is the loser's new rating
     */
    public int[] calculateNewRatings(int winnerRating, int loserRating) {
        return calculateNewRatings(winnerRating, loserRating, 0, 0);
    }

    /**
     * Calculates new ELO ratings for both players after a match, with experience-based K-factor.
     *
     * @param winnerRating    the winner's current ELO rating
     * @param loserRating     the loser's current ELO rating
     * @param winnerGamesPlayed the winner's total games played
     * @param loserGamesPlayed  the loser's total games played
     * @return an int[2] where [0] is the winner's new rating and [1] is the loser's new rating
     */
    public int[] calculateNewRatings(int winnerRating, int loserRating,
                                      int winnerGamesPlayed, int loserGamesPlayed) {
        // Calculate expected scores
        double expectedWinner = calculateExpectedScore(winnerRating, loserRating);
        double expectedLoser = calculateExpectedScore(loserRating, winnerRating);

        // Determine K-factors based on experience
        int kWinner = getKFactor(winnerGamesPlayed);
        int kLoser = getKFactor(loserGamesPlayed);

        // Winner actual score = 1.0, loser actual score = 0.0
        int newWinnerRating = Math.max(0, (int) Math.round(winnerRating + kWinner * (1.0 - expectedWinner)));
        int newLoserRating = Math.max(0, (int) Math.round(loserRating + kLoser * (0.0 - expectedLoser)));

        return new int[]{newWinnerRating, newLoserRating};
    }

    /**
     * Updates ELO ratings in the database after a match is completed.
     * Looks up both players by ID, calculates new ratings, and persists the changes.
     *
     * @param winnerId the winning player's ID
     * @param loserId  the losing player's ID
     * @throws IllegalArgumentException if either player is not found
     */
    @Transactional
    public void updateRatingsAfterMatch(Long winnerId, Long loserId) {
        Player winner = playerRepository.findById(winnerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + winnerId));
        Player loser = playerRepository.findById(loserId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + loserId));

        int[] newRatings = calculateNewRatings(
                winner.getEloRating(), loser.getEloRating(),
                winner.getGamesPlayed(), loser.getGamesPlayed()
        );

        int winnerEloBefore = winner.getEloRating();
        int loserEloBefore = loser.getEloRating();

        winner.setEloRating(newRatings[0]);
        winner.recordGameResult(true);
        loser.setEloRating(newRatings[1]);
        loser.recordGameResult(false);

        playerRepository.save(winner);
        playerRepository.save(loser);

        log.info("ELO updated after match: winner={} ({} → {}), loser={} ({} → {})",
                winnerId, winnerEloBefore, newRatings[0],
                loserId, loserEloBefore, newRatings[1]);
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
     * @return the K-factor (32 for new, 24 for experienced)
     */
    int getKFactor(int gamesPlayed) {
        return gamesPlayed < EXPERIENCED_THRESHOLD ? K_FACTOR_NEW : K_FACTOR_EXPERIENCED;
    }
}
