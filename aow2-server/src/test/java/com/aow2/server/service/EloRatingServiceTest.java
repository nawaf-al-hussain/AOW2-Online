package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the EloRatingService ELO calculation logic.
 * Verifies rating changes, K-factor adjustments, and database updates.
 * REF: multiplayer_architecture.md - ELO-based ranking for competitive play
 */
@ExtendWith(MockitoExtension.class)
class EloRatingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private EloRatingService eloRatingService;

    @BeforeEach
    void setUp() {
        eloRatingService = new EloRatingService(playerRepository);
    }

    private Player createPlayer(Long id, int elo, int gamesPlayed) {
        Player p = new Player("player" + id, "hash");
        p.setId(id);
        p.setEloRating(elo);
        p.setGamesPlayed(gamesPlayed);
        return p;
    }

    @Test
    @DisplayName("Equal rated players: 1000 vs 1000 → winner gets ~1016, loser gets ~984")
    void equalRatedPlayers() {
        int[] newRatings = eloRatingService.calculateNewRatings(1000, 1000);

        // With K=32 and expected score 0.5:
        // winner: 1000 + 32 * (1.0 - 0.5) = 1000 + 16 = 1016
        // loser:  1000 + 32 * (0.0 - 0.5) = 1000 - 16 = 984
        assertEquals(1016, newRatings[0], "Winner should gain ~16 ELO");
        assertEquals(984, newRatings[1], "Loser should lose ~16 ELO");
    }

    @Test
    @DisplayName("Winner gains ELO, loser loses ELO")
    void winnerGainsLoserLoses() {
        int[] newRatings = eloRatingService.calculateNewRatings(1200, 1000);

        assertTrue(newRatings[0] > 1200, "Winner should gain ELO");
        assertTrue(newRatings[1] < 1000, "Loser should lose ELO");
    }

    @Test
    @DisplayName("Upset: lower rated beats higher rated yields larger change")
    void upsetVictoryLargerChange() {
        // Lower rated (800) beats higher rated (1200)
        int[] upsetRatings = eloRatingService.calculateNewRatings(800, 1200);
        int upsetGain = upsetRatings[0] - 800;

        // Equal rated (1000) beats equal rated (1000)
        int[] equalRatings = eloRatingService.calculateNewRatings(1000, 1000);
        int equalGain = equalRatings[0] - 1000;

        assertTrue(upsetGain > equalGain,
                "Upset victory should yield more ELO than equal-rated win");
    }

    @Test
    @DisplayName("Higher rated beating lower rated yields smaller change")
    void expectedVictorySmallerChange() {
        // Higher rated (1200) beats lower rated (800)
        int[] expectedRatings = eloRatingService.calculateNewRatings(1200, 800);
        int expectedGain = expectedRatings[0] - 1200;

        // Equal rated (1000) beats equal rated (1000)
        int[] equalRatings = eloRatingService.calculateNewRatings(1000, 1000);
        int equalGain = equalRatings[0] - 1000;

        assertTrue(expectedGain < equalGain,
                "Expected victory should yield less ELO than equal-rated win");
    }

    @Test
    @DisplayName("K-factor is 32 for new players (<30 games)")
    void kFactorNewPlayer() {
        assertEquals(32, eloRatingService.getKFactor(0));
        assertEquals(32, eloRatingService.getKFactor(29));
    }

    @Test
    @DisplayName("K-factor is 24 for experienced players (30+ games)")
    void kFactorExperienced() {
        assertEquals(24, eloRatingService.getKFactor(30));
        assertEquals(24, eloRatingService.getKFactor(100));
        assertEquals(24, eloRatingService.getKFactor(500));
    }

    @Test
    @DisplayName("K-factor change at 30 games affects rating changes")
    void kFactorChangesAt30Games() {
        // New player (0 games, K=32) vs experienced player (50 games, K=24)
        int[] mixedRatings = eloRatingService.calculateNewRatings(1000, 1000, 0, 50);

        // Winner (K=32): 1000 + 32 * 0.5 = 1016
        // Loser (K=24): 1000 - 24 * 0.5 = 988
        assertEquals(1016, mixedRatings[0], "New player winner gains K=32 amount");
        assertEquals(988, mixedRatings[1], "Experienced player loser loses K=24 amount");
    }

    @Test
    @DisplayName("ELO expected score: equal ratings = 0.5")
    void expectedScoreEqual() {
        double score = eloRatingService.calculateExpectedScore(1000, 1000);
        assertEquals(0.5, score, 0.001);
    }

    @Test
    @DisplayName("ELO expected score: higher rated player favored")
    void expectedScoreHigherRated() {
        double score = eloRatingService.calculateExpectedScore(1200, 1000);
        assertTrue(score > 0.5, "Higher rated player should have expected score > 0.5");
        assertTrue(score < 1.0, "Expected score should be < 1.0");
    }

    @Test
    @DisplayName("ELO expected score: lower rated player underdog")
    void expectedScoreLowerRated() {
        double score = eloRatingService.calculateExpectedScore(800, 1000);
        assertTrue(score < 0.5, "Lower rated player should have expected score < 0.5");
        assertTrue(score > 0.0, "Expected score should be > 0.0");
    }

    @Test
    @DisplayName("Update ratings after match persists to database")
    void updateRatingsAfterMatch() {
        Player winner = createPlayer(1L, 1000, 0);
        Player loser = createPlayer(2L, 1000, 0);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(loser));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        eloRatingService.updateRatingsAfterMatch(1L, 2L);

        // Winner should have gained ELO
        assertTrue(winner.getEloRating() > 1000, "Winner ELO should increase");
        // Loser should have lost ELO
        assertTrue(loser.getEloRating() < 1000, "Loser ELO should decrease");
        // Games played should increment
        assertEquals(1, winner.getGamesPlayed(), "Winner games played should increment");
        assertEquals(1, loser.getGamesPlayed(), "Loser games played should increment");
        // Win tracking
        assertEquals(1, winner.getGamesWon(), "Winner should have 1 win");
        assertEquals(0, loser.getGamesWon(), "Loser should have 0 wins");
    }

    @Test
    @DisplayName("Update ratings with non-existent player throws exception")
    void updateRatingsPlayerNotFound() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> eloRatingService.updateRatingsAfterMatch(999L, 1L)
        );
    }

    @Test
    @DisplayName("ELO cannot drop below 0")
    void eloFloorAtZero() {
        int[] newRatings = eloRatingService.calculateNewRatings(10, 2000);
        assertTrue(newRatings[0] >= 0, "Winner ELO should be >= 0");
        assertTrue(newRatings[1] >= 0, "Loser ELO should be >= 0");
    }

    @Test
    @DisplayName("Starting ELO is 1000")
    void startingElo() {
        assertEquals(1000, EloRatingService.STARTING_ELO);
    }
}
