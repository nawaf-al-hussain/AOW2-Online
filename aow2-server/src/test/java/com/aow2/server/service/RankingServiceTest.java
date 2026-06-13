package com.aow2.server.service;

import com.aow2.server.model.MatchResult;
import com.aow2.server.model.Player;
import com.aow2.server.repository.MatchResultRepository;
import com.aow2.server.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the RankingService ELO calculation and leaderboard management.
 * Verifies ELO rating changes, K-factor adjustments, and leaderboard queries.
 * REF: multiplayer_architecture.md - ELO-based ranking for competitive play
 */
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchResultRepository matchResultRepository;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(playerRepository, matchResultRepository);
    }

    private Player createPlayer(Long id, int elo, int gamesPlayed) {
        Player p = new Player("player" + id, "hash");
        p.setId(id);
        p.setEloRating(elo);
        p.setGamesPlayed(gamesPlayed);
        return p;
    }

    @Test
    @DisplayName("ELO expected score calculation: equal ratings = 0.5")
    void expectedScoreEqual() {
        double score = rankingService.calculateExpectedScore(1000, 1000);
        assertEquals(0.5, score, 0.001);
    }

    @Test
    @DisplayName("ELO expected score: higher rated player favored")
    void expectedScoreHigherRated() {
        double score = rankingService.calculateExpectedScore(1200, 1000);
        assertTrue(score > 0.5, "Higher rated player should have expected score > 0.5");
        assertTrue(score < 1.0, "Expected score should be < 1.0");
    }

    @Test
    @DisplayName("ELO expected score: lower rated player underdog")
    void expectedScoreLowerRated() {
        double score = rankingService.calculateExpectedScore(800, 1000);
        assertTrue(score < 0.5, "Lower rated player should have expected score < 0.5");
        assertTrue(score > 0.0, "Expected score should be > 0.0");
    }

    @Test
    @DisplayName("K-factor is 32 for new players (<30 games)")
    void kFactorNewPlayer() {
        assertEquals(32, rankingService.getKFactor(0));
        assertEquals(32, rankingService.getKFactor(29));
    }

    @Test
    @DisplayName("K-factor is 16 for experienced players (30+ games)")
    void kFactorExperienced() {
        assertEquals(16, rankingService.getKFactor(30));
        assertEquals(16, rankingService.getKFactor(100));
    }

    @Test
    @DisplayName("Record match result updates ELO for both players")
    void recordMatchResult() {
        Player p1 = createPlayer(1L, 1000, 0);
        Player p2 = createPlayer(2L, 1000, 0);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(matchResultRepository.save(any(MatchResult.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchResult result = rankingService.recordMatchResult(1L, 2L, 1L, "test_map", 300);

        assertNotNull(result);
        assertEquals(1L, result.getPlayer1Id());
        assertEquals(2L, result.getPlayer2Id());
        assertEquals(1L, result.getWinnerId());
        assertTrue(result.getPlayer1EloAfter() > 1000, "Winner ELO should increase");
        assertTrue(result.getPlayer2EloAfter() < 1000, "Loser ELO should decrease");
    }

    @Test
    @DisplayName("ELO change is larger for upset victories")
    void upsetVictoryLargerChange() {
        Player weak = createPlayer(1L, 800, 0);
        Player strong = createPlayer(2L, 1200, 0);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(weak));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(strong));
        when(matchResultRepository.save(any(MatchResult.class))).thenAnswer(inv -> inv.getArgument(0));

        // Weak player beats strong player
        MatchResult result = rankingService.recordMatchResult(1L, 2L, 1L, "test_map", 300);

        int weakGain = result.getPlayer1EloAfter() - result.getPlayer1EloBefore();
        // Compare with expected gain if equal players
        Player equal1 = createPlayer(3L, 1000, 0);
        Player equal2 = createPlayer(4L, 1000, 0);
        when(playerRepository.findById(3L)).thenReturn(Optional.of(equal1));
        when(playerRepository.findById(4L)).thenReturn(Optional.of(equal2));
        MatchResult equalResult = rankingService.recordMatchResult(3L, 4L, 3L, "test_map", 300);
        int equalGain = equalResult.getPlayer1EloAfter() - equalResult.getPlayer1EloBefore();

        assertTrue(weakGain > equalGain,
                "Upset victory should yield more ELO than equal-rated win");
    }

    @Test
    @DisplayName("Leaderboard returns players sorted by ELO")
    void leaderboardSortedByElo() {
        Player p1 = createPlayer(1L, 1500, 30);
        Player p2 = createPlayer(2L, 1200, 30);
        Player p3 = createPlayer(3L, 1800, 30);
        when(playerRepository.findAllByOrderByEloRatingDesc()).thenReturn(List.of(p3, p1, p2));

        List<Map<String, Object>> leaderboard = rankingService.getLeaderboard(10);
        assertEquals(3, leaderboard.size());
        assertEquals(1, leaderboard.get(0).get("rank"));
        assertEquals(3L, leaderboard.get(0).get("id"));
    }

    @Test
    @DisplayName("Player ranking calculates correctly")
    void playerRanking() {
        Player p = createPlayer(2L, 1200, 30);
        Player p1 = createPlayer(1L, 1800, 30);
        Player p3 = createPlayer(3L, 1000, 30);
        when(playerRepository.findById(2L)).thenReturn(Optional.of(p));
        when(playerRepository.findAllByOrderByEloRatingDesc()).thenReturn(List.of(p1, p, p3));

        Map<String, Object> ranking = rankingService.getPlayerRanking(2L);
        assertEquals(2, ranking.get("rank"));
        assertEquals(1200, ranking.get("eloRating"));
    }

    @Test
    @DisplayName("ELO cannot drop below 0")
    void eloFloorAtZero() {
        Player p1 = createPlayer(1L, 10, 0);
        Player p2 = createPlayer(2L, 2000, 0);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(matchResultRepository.save(any(MatchResult.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchResult result = rankingService.recordMatchResult(1L, 2L, 2L, "test_map", 60);
        assertTrue(result.getPlayer1EloAfter() >= 0, "ELO should not go below 0");
    }
}
