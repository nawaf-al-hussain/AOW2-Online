package com.aow2.core.replay;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReplayRecorder: recording, saving.
 * Naming: shouldXxxWhenYyy, Given-When-Then.
 */
class ReplayRecorderTest {

    private ReplayRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ReplayRecorder();
    }

    @Nested
    @DisplayName("Recording Lifecycle")
    class RecordingLifecycle {

        @Test
        @DisplayName("shouldNotBeRecordingInitially")
        void shouldNotBeRecordingInitially() {
            assertFalse(recorder.isRecording());
        }

        @Test
        @DisplayName("shouldStartRecording")
        void shouldStartRecording() {
            // When: starting recording
            recorder.startRecording("test_map", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});

            // Then: should be recording
            assertTrue(recorder.isRecording());
        }

        @Test
        @DisplayName("shouldStopRecordingAndReturnReplay")
        void shouldStopRecordingAndReturnReplay() {
            // Given: an active recording
            recorder.startRecording("test_map", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});

            // When: stopping
            ReplayFile replay = recorder.stopRecording();

            // Then: should return a replay file and stop recording
            assertNotNull(replay);
            assertFalse(recorder.isRecording());
            assertEquals("test_map", replay.mapName());
            assertEquals(2, replay.playerFactions().length);
        }

        @Test
        @DisplayName("shouldReturnNullWhenStoppingWithoutRecording")
        void shouldReturnNullWhenStoppingWithoutRecording() {
            // When: stopping without having started
            ReplayFile replay = recorder.stopRecording();

            // Then: should return null
            assertNull(replay);
        }

        @Test
        @DisplayName("shouldStopPreviousRecordingWhenStartingNew")
        void shouldStopPreviousRecordingWhenStartingNew() {
            // Given: an active recording
            recorder.startRecording("map1", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});

            // When: starting a new recording
            recorder.startRecording("map2", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});

            // Then: should still be recording with new map name
            assertTrue(recorder.isRecording());
        }
    }

    @Nested
    @DisplayName("Command Recording")
    class CommandRecording {

        @BeforeEach
        void startRecording() {
            recorder.startRecording("test_map", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});
        }

        @Test
        @DisplayName("shouldRecordMoveCommand")
        void shouldRecordMoveCommand() {
            // Given: a move command
            CommandType.Move move = new CommandType.Move(100, 0, new int[]{1, 2}, new GridPosition(10, 20));

            // When: recording it
            recorder.recordCommand(move);

            // Then: command count should be 1
            assertEquals(1, recorder.recordedCommandCount());
        }

        @Test
        @DisplayName("shouldRecordAttackCommand")
        void shouldRecordAttackCommand() {
            // Given: an attack command
            CommandType.Attack attack = new CommandType.Attack(200, 1, new int[]{3}, 5);

            // When: recording it
            recorder.recordCommand(attack);

            // Then: command count should be 1
            assertEquals(1, recorder.recordedCommandCount());
        }

        @Test
        @DisplayName("shouldRecordBuildCommand")
        void shouldRecordBuildCommand() {
            // Given: a build command
            CommandType.Build build = new CommandType.Build(50, 0, BuildingType.CONFED_GENERATOR, new GridPosition(5, 5));

            // When: recording it
            recorder.recordCommand(build);

            // Then: command count should be 1
            assertEquals(1, recorder.recordedCommandCount());
        }

        @Test
        @DisplayName("shouldRecordProduceCommand")
        void shouldRecordProduceCommand() {
            // Given: a produce command
            CommandType.Produce produce = new CommandType.Produce(300, 0, 10, UnitType.CONFED_INFANTRY);

            // When: recording it
            recorder.recordCommand(produce);

            // Then: command count should be 1
            assertEquals(1, recorder.recordedCommandCount());
        }

        @Test
        @DisplayName("shouldRecordMultipleCommands")
        void shouldRecordMultipleCommands() {
            // Given: multiple commands
            recorder.recordCommand(new CommandType.Move(100, 0, new int[]{1}, new GridPosition(5, 5)));
            recorder.recordCommand(new CommandType.Attack(200, 1, new int[]{2}, 3));
            recorder.recordCommand(new CommandType.Build(300, 0, BuildingType.CONFED_GENERATOR, new GridPosition(10, 10)));

            // When: checking command count
            assertEquals(3, recorder.recordedCommandCount());
        }

        @Test
        @DisplayName("shouldNotRecordWhenNotRecording")
        void shouldNotRecordWhenNotRecording() {
            // Given: a stopped recorder
            recorder.stopRecording();

            // When: trying to record
            recorder.recordCommand(new CommandType.Move(100, 0, new int[]{1}, new GridPosition(5, 5)));

            // Then: command count should remain 0
            assertEquals(0, recorder.recordedCommandCount());
        }

        @Test
        @DisplayName("shouldCaptureTotalTicksFromLastCommand")
        void shouldCaptureTotalTicksFromLastCommand() {
            // Given: commands at different ticks
            recorder.recordCommand(new CommandType.Move(100, 0, new int[]{1}, new GridPosition(5, 5)));
            recorder.recordCommand(new CommandType.Move(500, 0, new int[]{1}, new GridPosition(6, 6)));
            recorder.recordCommand(new CommandType.Move(300, 0, new int[]{1}, new GridPosition(7, 7)));

            // When: stopping recording
            ReplayFile replay = recorder.stopRecording();

            // Then: total ticks should be from the last recorded command
            assertEquals(300, replay.totalTicks());
        }

        @Test
        @DisplayName("shouldRecordAllCommandTypes")
        void shouldRecordAllCommandTypes() {
            // Given: one of each command type
            recorder.recordCommand(new CommandType.Move(1, 0, new int[]{1}, new GridPosition(5, 5)));
            recorder.recordCommand(new CommandType.Attack(2, 0, new int[]{1}, 2));
            recorder.recordCommand(new CommandType.Build(3, 0, BuildingType.CONFED_GENERATOR, new GridPosition(5, 5)));
            recorder.recordCommand(new CommandType.Produce(4, 0, 1, UnitType.CONFED_INFANTRY));
            recorder.recordCommand(new CommandType.Research(5, 0, 1, 0));
            recorder.recordCommand(new CommandType.Garrison(6, 0, new int[]{1}, 2));
            recorder.recordCommand(new CommandType.Ungarrison(7, 0, 2));
            recorder.recordCommand(new CommandType.Cancel(8, 0, 1));
            recorder.recordCommand(new CommandType.SiegeMode(9, 0, 1, true));
            recorder.recordCommand(new CommandType.Stop(10, 0, new int[]{1}));
            recorder.recordCommand(new CommandType.Patrol(11, 0, new int[]{1}, new GridPosition(5, 5)));

            // When: stopping
            ReplayFile replay = recorder.stopRecording();

            // Then: all 11 commands should be recorded
            assertEquals(11, replay.commandCount());
        }
    }

    @Nested
    @DisplayName("Replay Save")
    class ReplaySave {

        @Test
        @DisplayName("shouldSaveReplayToBinaryFile") 
        void shouldSaveReplayToBinaryFile() throws Exception {
            // Given: a completed recording
            recorder.startRecording("test_map", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});
            recorder.recordCommand(new CommandType.Move(100, 0, new int[]{1}, new GridPosition(5, 5)));
            ReplayFile replay = recorder.stopRecording();

            Path tempFile = Files.createTempFile("test_replay", ".aow2rep");

            try {
                // When: saving
                boolean saved = recorder.saveReplay(tempFile);

                // Then: should succeed and file should exist
                assertTrue(saved);
                assertTrue(Files.size(tempFile) > 0);

                // Verify magic bytes
                byte[] header = Files.readAllBytes(tempFile);
                assertEquals('A', header[0]);
                assertEquals('O', header[1]);
                assertEquals('W', header[2]);
                assertEquals('2', header[3]);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("shouldReturnFalseWhenSavingWithoutReplay")
        void shouldReturnFalseWhenSavingWithoutReplay() {
            assertFalse(recorder.saveReplay(Path.of("test.aow2rep")));
        }
    }
}
