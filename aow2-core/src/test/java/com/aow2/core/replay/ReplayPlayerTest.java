package com.aow2.core.replay;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReplayPlayer: playback, seeking, loading.
 * Naming: shouldXxxWhenYyy, Given-When-Then.
 */
class ReplayPlayerTest {

    private ReplayPlayer player;

    @BeforeEach
    void setUp() {
        player = new ReplayPlayer();
    }

    private ReplayFile createTestReplay(long totalTicks, int commandCount) {
        List<ReplayEntry> commands = new ArrayList<>();
        for (int i = 0; i < commandCount; i++) {
            long tick = (i + 1) * 100L;
            // Use CommandSerializer format: [typeId:1][tick:8][playerId:4][count:4][x:4][y:4]
            // Minimum valid Move command payload for 0 unitIds and target (0,0)
            byte[] payload = new byte[]{0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            commands.add(new ReplayEntry(tick, 1, i % 2, payload));
        }
        return new ReplayFile(
            "test_map",
            new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE},
            totalTicks,
            commands,
            System.currentTimeMillis(),
            ReplayFile.FORMAT_VERSION,
            "unknown", null, -1, 0
        );
    }

    @Nested
    @DisplayName("Replay Loading")
    class ReplayLoading {

        @Test
        @DisplayName("shouldLoadReplayFile")
        void shouldLoadReplayFile() {
            // Given: a test replay
            ReplayFile replay = createTestReplay(1000, 10);

            // When: loading
            player.loadReplay(replay);

            // Then: replay should be loaded
            assertTrue(player.hasReplay());
            assertEquals(1000, player.getTotalTicks());
        }

        @Test
        @DisplayName("shouldNotBePlayingInitiallyAfterLoad")
        void shouldNotBePlayingInitiallyAfterLoad() {
            // Given: a loaded replay
            player.loadReplay(createTestReplay(1000, 5));

            // Then: should not be playing
            assertFalse(player.isPlaying());
            assertEquals(0, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldResetPositionWhenLoadingNewReplay")
        void shouldResetPositionWhenLoadingNewReplay() {
            // Given: a player that has played some ticks
            player.loadReplay(createTestReplay(1000, 5));
            player.play();
            player.stepForward();
            assertTrue(player.getCurrentTick() > 0);

            // When: loading a new replay
            player.loadReplay(createTestReplay(2000, 10));

            // Then: position should reset
            assertEquals(0, player.getCurrentTick());
            assertFalse(player.isPlaying());
        }
    }

    @Nested
    @DisplayName("Playback Control")
    class PlaybackControl {

        @BeforeEach
        void loadReplay() {
            player.loadReplay(createTestReplay(1000, 10));
        }

        @Test
        @DisplayName("shouldStartPlayback")
        void shouldStartPlayback() {
            // When: starting playback
            player.play();

            // Then: should be playing
            assertTrue(player.isPlaying());
            assertEquals(0, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldPausePlayback")
        void shouldPausePlayback() {
            // Given: playing
            player.play();
            player.stepForward();

            // When: pausing
            player.pause();

            // Then: should be paused but position preserved
            assertFalse(player.isPlaying());
            assertEquals(1, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldResumePlayback")
        void shouldResumePlayback() {
            // Given: paused playback
            player.play();
            player.stepForward();
            player.pause();
            long tickBeforeResume = player.getCurrentTick();

            // When: resuming
            player.resume();

            // Then: should be playing and position preserved
            assertTrue(player.isPlaying());
            assertEquals(tickBeforeResume, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldAdvanceOneTickOnStepForward")
        void shouldAdvanceOneTickOnStepForward() {
            // Given: playing
            player.play();

            // When: stepping forward
            player.stepForward();

            // Then: tick should advance by 1
            assertEquals(1, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldNotStepForwardWhenNotPlaying")
        void shouldNotStepForwardWhenNotPlaying() {
            // Given: not playing
            assertFalse(player.isPlaying());

            // When: trying to step forward
            player.stepForward();

            // Then: tick should not advance
            assertEquals(0, player.getCurrentTick());
        }
    }

    @Nested
    @DisplayName("Seeking")
    class Seeking {

        @BeforeEach
        void loadReplay() {
            player.loadReplay(createTestReplay(1000, 10));
            player.play();
        }

        @Test
        @DisplayName("shouldSeekForward")
        void shouldSeekForward() {
            // When: seeking forward
            player.seekTo(500);

            // Then: current tick should be 500
            assertEquals(500, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldSeekBackwardByRebuildingFromStart")
        void shouldSeekBackwardByRebuildingFromStart() {
            // Given: at tick 500
            player.seekTo(500);
            assertEquals(500, player.getCurrentTick());

            // When: seeking backward
            player.seekTo(200);

            // Then: should be at tick 200
            assertEquals(200, player.getCurrentTick());
        }

        @Test
        @DisplayName("shouldThrowWhenSeekingOutOfRange")
        void shouldThrowWhenSeekingOutOfRange() {
            assertThrows(IllegalArgumentException.class, () -> player.seekTo(-1));
            assertThrows(IllegalArgumentException.class, () -> player.seekTo(2000));
        }

        @Test
        @DisplayName("shouldSeekToZero")
        void shouldSeekToZero() {
            // Given: at a later tick
            player.seekTo(500);

            // When: seeking to 0
            player.seekTo(0);

            // Then: should be at tick 0
            assertEquals(0, player.getCurrentTick());
        }
    }

    @Nested
    @DisplayName("Replay End")
    class ReplayEnd {

        @Test
        @DisplayName("shouldStopPlaybackWhenReplayEnds")
        void shouldStopPlaybackWhenReplayEnds() {
            // Given: a short replay
            ReplayFile shortReplay = new ReplayFile(
                "short_map",
                new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE},
                5,
                List.of(),
                System.currentTimeMillis(),
                ReplayFile.FORMAT_VERSION,
                "unknown", null, -1, 0
            );
            player.loadReplay(shortReplay);
            player.play();

            // When: advancing past the end
            for (int i = 0; i < 10; i++) {
                player.stepForward();
            }

            // Then: playback should have stopped
            assertFalse(player.isPlaying());
        }
    }

    @Nested
    @DisplayName("File Loading")
    class FileLoading {

        @Test
        @DisplayName("shouldLoadReplayFromBinaryFile") 
        void shouldLoadReplayFromBinaryFile() throws Exception {
            // Given: a saved replay file
            ReplayRecorder recorder = new ReplayRecorder();
            recorder.startRecording("test_map", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});
            recorder.recordCommand(new CommandType.Move(100, 0, new int[]{1}, new GridPosition(5, 5)));
            recorder.recordCommand(new CommandType.Attack(200, 1, new int[]{2}, 3));
            ReplayFile original = recorder.stopRecording();

            Path tempFile = Files.createTempFile("test_replay", ".aow2rep");
            try {
                recorder.saveReplay(tempFile);

                // When: loading from file
                ReplayFile loaded = ReplayPlayer.loadFromFile(tempFile);

                // Then: should have correct data
                assertNotNull(loaded);
                assertEquals("test_map", loaded.mapName());
                assertEquals(2, loaded.playerCount());
                assertEquals(2, loaded.commandCount());
                assertEquals(Faction.CONFEDERATION, loaded.playerFactions()[0]);
                assertEquals(Faction.RESISTANCE, loaded.playerFactions()[1]);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("shouldReturnNullForInvalidFile")
        void shouldReturnNullForInvalidFile() throws Exception {
            // Given: a file with invalid content
            Path tempFile = Files.createTempFile("invalid_replay", ".aow2rep");
            try {
                Files.writeString(tempFile, "not a replay file");

                // When: trying to load
                ReplayFile loaded = ReplayPlayer.loadFromFile(tempFile);

                // Then: should return null
                assertNull(loaded);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    @DisplayName("ReplayFile Record")
    class ReplayFileRecord {

        @Test
        @DisplayName("shouldCreateReplayFileWithValidData")
        void shouldCreateReplayFileWithValidData() {
            ReplayFile file = ReplayFile.createNew("test", new Faction[]{Faction.CONFEDERATION});
            assertEquals("test", file.mapName());
            assertEquals(0, file.totalTicks());
            assertEquals(0, file.commandCount());
            assertEquals(ReplayFile.FORMAT_VERSION, file.formatVersion());
        }

        @Test
        @DisplayName("shouldRejectBlankMapName")
        void shouldRejectBlankMapName() {
            assertThrows(IllegalArgumentException.class,
                () -> new ReplayFile("", new Faction[]{Faction.CONFEDERATION},
                    0, List.of(), System.currentTimeMillis(), 1, "unknown", null, -1, 0));
        }

        @Test
        @DisplayName("shouldRejectEmptyPlayerFactions")
        void shouldRejectEmptyPlayerFactions() {
            assertThrows(IllegalArgumentException.class,
                () -> new ReplayFile("test", new Faction[]{},
                    0, List.of(), System.currentTimeMillis(), 1, "unknown", null, -1, 0));
        }

        @Test
        @DisplayName("shouldCalculateDurationInSeconds")
        void shouldCalculateDurationInSeconds() {
            ReplayFile file = new ReplayFile("test",
                new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE},
                900, List.of(), System.currentTimeMillis(), 1, "unknown", null, -1, 0);

            // FIX (B-3 from FULL_ANALYSIS.md): durationSeconds now divides by
            // TICK_RATE (10), not 30. 900 ticks / 10 TPS = 90 seconds.
            assertEquals(90, file.durationSeconds());
        }
    }

    @Nested
    @DisplayName("ReplayEntry Record")
    class ReplayEntryRecord {

        @Test
        @DisplayName("shouldCreateReplayEntryWithValidData")
        void shouldCreateReplayEntryWithValidData() {
            ReplayEntry entry = new ReplayEntry(100, 1, 0, new byte[]{1, 2, 3});
            assertEquals(100, entry.tick());
            assertEquals(1, entry.typeOrd());
            assertEquals(0, entry.playerId());
            assertArrayEquals(new byte[]{1, 2, 3}, entry.getPayload());
        }

        @Test
        @DisplayName("shouldRejectNegativeTick")
        void shouldRejectNegativeTick() {
            assertThrows(IllegalArgumentException.class,
                () -> new ReplayEntry(-1, 1, 0, new byte[]{}));
        }

        @Test
        @DisplayName("shouldRejectInvalidTypeOrd")
        void shouldRejectInvalidTypeOrd() {
            // FIX (C2): typeOrd 12 (TYPE_ATTACK_MOVE) and 13 (TYPE_UPGRADE) are now valid. Use 14 to test rejection.
            assertThrows(IllegalArgumentException.class,
                () -> new ReplayEntry(0, 14, 0, new byte[]{}));
        }

        @Test
        @DisplayName("shouldRejectInvalidPlayerId")
        void shouldRejectInvalidPlayerId() {
            assertThrows(IllegalArgumentException.class,
                () -> new ReplayEntry(0, 0, 2, new byte[]{}));
        }

        @Test
        @DisplayName("shouldRejectNullPayload")
        void shouldRejectNullPayload() {
            assertThrows(IllegalArgumentException.class,
                () -> new ReplayEntry(0, 1, 0, null));
        }

        @Test
        @DisplayName("shouldReturnCopyOfPayload")
        void shouldReturnCopyOfPayload() {
            byte[] original = {1, 2, 3};
            ReplayEntry entry = new ReplayEntry(0, 1, 0, original);

            // Modifying the returned array should not affect the entry
            byte[] copy = entry.getPayload();
            copy[0] = 99;
            assertArrayEquals(new byte[]{1, 2, 3}, entry.getPayload());
        }
    }
}
