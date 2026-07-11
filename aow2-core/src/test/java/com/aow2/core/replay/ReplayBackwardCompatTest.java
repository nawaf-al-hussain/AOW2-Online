package com.aow2.core.replay;

import com.aow2.common.model.Faction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for B-2 from FULL_ANALYSIS.md: ReplayPlayer rejects v1/v2 replays.
 * <p>
 * BUG (before fix): {@code ReplayPlayer.loadFromFile} rejected any replay file
 * whose format version was not exactly {@code FORMAT_VERSION} (currently 3),
 * even though:
 * <ul>
 *   <li>The {@code ReplayFile} javadoc documented that "readers accept 1 and 2".</li>
 *   <li>{@code ReplayPlayer} already contained {@code if (formatVersion >= 2)}
 *       and {@code if (formatVersion >= 3)} branches for backward-compatible
 *       reading of recordedAt and expanded metadata.</li>
 * </ul>
 * The strict equality check at line 295 made those branches unreachable dead
 * code, breaking any replay recorded before the v3 metadata bump.
 * <p>
 * FIX: The check now accepts any version in {@code [1, FORMAT_VERSION]}.
 */
@DisplayName("B-2: ReplayPlayer backward compatibility regression")
class ReplayBackwardCompatTest {

    @TempDir
    Path tempDir;

    /**
     * Writes a minimal v1 replay file (no recordedAt field, no expanded metadata).
     * Format: magic(4) + version(2) + nameLen(4) + name + playerCount(4) + factions
     * + totalTicks(8) + commandCount(4) + commands.
     */
    private Path writeV1Replay(String mapName, long totalTicks) throws Exception {
        Path file = tempDir.resolve("v1_replay.aow2rep");
        try (OutputStream os = Files.newOutputStream(file);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeShort(1);  // v1
            byte[] nameBytes = mapName.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(nameBytes.length);
            dos.write(nameBytes);
            dos.writeInt(2);  // 2 players
            dos.writeByte(Faction.CONFEDERATION.ordinal());
            dos.writeByte(Faction.RESISTANCE.ordinal());
            dos.writeLong(totalTicks);
            // v1: NO recordedAt field
            dos.writeInt(0);  // 0 commands
        }
        return file;
    }

    /**
     * Writes a minimal v2 replay file (includes recordedAt, no expanded metadata).
     */
    private Path writeV2Replay(String mapName, long totalTicks, long recordedAt) throws Exception {
        Path file = tempDir.resolve("v2_replay.aow2rep");
        try (OutputStream os = Files.newOutputStream(file);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeShort(2);  // v2
            byte[] nameBytes = mapName.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(nameBytes.length);
            dos.write(nameBytes);
            dos.writeInt(2);
            dos.writeByte(Faction.CONFEDERATION.ordinal());
            dos.writeByte(Faction.RESISTANCE.ordinal());
            dos.writeLong(totalTicks);
            dos.writeLong(recordedAt);  // v2: recordedAt added
            dos.writeInt(0);  // 0 commands
            // v2: NO expanded metadata (gameVersion, playerNames, etc.)
        }
        return file;
    }

    /**
     * Writes a minimal v3 replay file (recordedAt + expanded metadata).
     * This is what the current ReplayRecorder produces.
     */
    private Path writeV3Replay(String mapName, long totalTicks) throws Exception {
        Path file = tempDir.resolve("v3_replay.aow2rep");
        try (OutputStream os = Files.newOutputStream(file);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeShort(3);  // v3
            byte[] nameBytes = mapName.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(nameBytes.length);
            dos.write(nameBytes);
            dos.writeInt(2);
            dos.writeByte(Faction.CONFEDERATION.ordinal());
            dos.writeByte(Faction.RESISTANCE.ordinal());
            dos.writeLong(totalTicks);
            dos.writeLong(System.currentTimeMillis());  // recordedAt
            dos.writeInt(0);  // 0 commands
            // v3 expanded metadata
            byte[] gvBytes = "1.0.0-test".getBytes(StandardCharsets.UTF_8);
            dos.writeInt(gvBytes.length);
            dos.write(gvBytes);
            dos.writeInt(0);  // 0 playerNames
            dos.writeInt(-1);  // winnerPlayerId
            dos.writeLong(0);  // durationMillis
        }
        return file;
    }

    @Test
    @DisplayName("v1 replay (no recordedAt, no metadata) loads successfully")
    void v1ReplayLoadsSuccessfully() throws Exception {
        Path v1File = writeV1Replay("v1_test_map", 1000);

        ReplayFile loaded = ReplayPlayer.loadFromFile(v1File);

        assertNotNull(loaded, "v1 replay should load successfully");
        assertEquals("v1_test_map", loaded.mapName());
        assertEquals(2, loaded.playerCount());
        assertEquals(1000, loaded.totalTicks());
        assertEquals(Faction.CONFEDERATION, loaded.playerFactions()[0]);
        assertEquals(Faction.RESISTANCE, loaded.playerFactions()[1]);
        assertEquals(0, loaded.commandCount());
        // v1 files have no recordedAt in the file; loader falls back to file mtime (>= 0)
        assertTrue(loaded.recordedAt() >= 0, "v1 recordedAt should fall back to file mtime (>= 0)");
        // v1 files have no expanded metadata; loader should use defaults
        assertEquals("unknown", loaded.gameVersion());
        assertEquals(-1, loaded.winnerPlayerId());
    }

    @Test
    @DisplayName("v2 replay (recordedAt, no expanded metadata) loads successfully")
    void v2ReplayLoadsSuccessfully() throws Exception {
        long testRecordedAt = 1700000000000L;
        Path v2File = writeV2Replay("v2_test_map", 2000, testRecordedAt);

        ReplayFile loaded = ReplayPlayer.loadFromFile(v2File);

        assertNotNull(loaded, "v2 replay should load successfully");
        assertEquals("v2_test_map", loaded.mapName());
        assertEquals(2000, loaded.totalTicks());
        assertEquals(testRecordedAt, loaded.recordedAt(),
            "v2 recordedAt should be read from the file (not file mtime)");
        assertEquals("unknown", loaded.gameVersion());
        assertEquals(-1, loaded.winnerPlayerId());
    }

    @Test
    @DisplayName("v3 replay (full metadata) still loads (no regression)")
    void v3ReplayStillLoads() throws Exception {
        Path v3File = writeV3Replay("v3_test_map", 3000);

        ReplayFile loaded = ReplayPlayer.loadFromFile(v3File);

        assertNotNull(loaded, "v3 replay should still load (no regression)");
        assertEquals("v3_test_map", loaded.mapName());
        assertEquals(3000, loaded.totalTicks());
        assertEquals("1.0.0-test", loaded.gameVersion());
    }

    @Test
    @DisplayName("Version 0 is rejected (below supported range)")
    void version0Rejected() throws Exception {
        Path file = tempDir.resolve("v0_replay.aow2rep");
        try (OutputStream os = Files.newOutputStream(file);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeShort(0);  // v0 — invalid
            // Don't write anything else — the version check should reject before reading more
        }

        ReplayFile loaded = ReplayPlayer.loadFromFile(file);
        assertNull(loaded, "v0 replay should be rejected (below supported range [1, 3])");
    }

    @Test
    @DisplayName("Version 99 is rejected (above supported range)")
    void version99Rejected() throws Exception {
        Path file = tempDir.resolve("v99_replay.aow2rep");
        try (OutputStream os = Files.newOutputStream(file);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeShort(99);  // v99 — invalid
        }

        ReplayFile loaded = ReplayPlayer.loadFromFile(file);
        assertNull(loaded, "v99 replay should be rejected (above supported range [1, 3])");
    }

    @Test
    @DisplayName("Invalid magic bytes are rejected")
    void invalidMagicRejected() throws Exception {
        Path file = tempDir.resolve("bad_magic.aow2rep");
        try (OutputStream os = Files.newOutputStream(file);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.write("XXXX".getBytes(StandardCharsets.UTF_8));  // Wrong magic
            dos.writeShort(3);
        }

        ReplayFile loaded = ReplayPlayer.loadFromFile(file);
        assertNull(loaded, "Replay with wrong magic should be rejected");
    }
}
