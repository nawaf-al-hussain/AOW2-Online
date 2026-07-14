package com.aow2.core.replay;

import com.aow2.common.model.Faction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for B-3 and B-16 from FULL_ANALYSIS.md.
 * <p>
 * B-3: {@code ReplayFile.durationSeconds()} divided {@code totalTicks} by 30
 * instead of {@code TICK_RATE} (10), making reported durations 3× too short.
 * <p>
 * B-16: {@code durationSeconds()} ignored the {@code durationMillis} field
 * stored in v3 metadata, recomputing from ticks instead of using the recorded
 * wall-clock duration.
 */
@DisplayName("B-3/B-16: ReplayFile.durationSeconds() correctness")
class ReplayDurationTest {

    private ReplayFile createReplay(long totalTicks, long durationMillis) {
        return new ReplayFile(
            "test_map",
            new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE},
            totalTicks,
            List.of(),
            System.currentTimeMillis(),
            ReplayFile.FORMAT_VERSION,
            "unknown", null, -1,
            durationMillis
        );
    }

    @Test
    @DisplayName("B-3: v1/v2 replay (durationMillis=0) uses totalTicks/TICK_RATE, not /30")
    void v1v2DurationUsesCorrectDivisor() {
        // Given: a replay with 1800 ticks (180 seconds at 10 TPS) and no durationMillis
        ReplayFile replay = createReplay(1800, 0);

        // When: computing durationSeconds
        long seconds = replay.durationSeconds();

        // Then: should be 180 (1800/10), not 60 (1800/30)
        assertEquals(180, seconds,
            "B-3: 1800 ticks at 10 TPS = 180 seconds. If 60, the old /30 divisor was used.");
    }

    @Test
    @DisplayName("B-16: v3 replay (durationMillis>0) prefers durationMillis over tick calculation")
    void v3DurationPrefersDurationMillis() {
        // Given: a replay with 1800 ticks but durationMillis=240000 (4 minutes wall-clock)
        // This happens when the game was paused for 1 minute during the match.
        ReplayFile replay = createReplay(1800, 240_000);

        // When: computing durationSeconds
        long seconds = replay.durationSeconds();

        // Then: should be 240 (240000/1000), not 180 (1800/10)
        // The wall-clock duration is more accurate than tick-based for paused games.
        assertEquals(240, seconds,
            "B-16: v3 replay should use durationMillis (240s), not totalTicks/TICK_RATE (180s).");
    }

    @Test
    @DisplayName("B-3: 30-minute replay (18000 ticks) shows 1800s, not 600s")
    void thirtyMinuteReplayCorrect() {
        // Given: a 30-minute replay = 18000 ticks at 10 TPS
        ReplayFile replay = createReplay(18_000, 0);

        // When: computing durationSeconds
        long seconds = replay.durationSeconds();

        // Then: should be 1800 seconds (30 minutes), not 600 (10 minutes)
        assertEquals(1800, seconds,
            "B-3: 18000 ticks at 10 TPS = 1800 seconds. If 600, the old /30 divisor was used.");
    }
}
