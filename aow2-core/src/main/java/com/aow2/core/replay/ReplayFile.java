package com.aow2.core.replay;

import com.aow2.common.model.Faction;

import java.util.List;

/**
 * Replay file data structure: compact binary with version header.
 * <p>
 * Binary format:
 * <pre>
 * [4 bytes]  Magic: "AOW2"
 * [2 bytes]  Format version
 * [4 bytes]  Map name length + map name bytes
 * [4 bytes]  Number of players
 * [N bytes]  Player faction data
 * [8 bytes]  Total ticks
 * [8 bytes]  recordedAt (epoch millis; v2+ only — absent in v1, reader falls back to file mtime)
 * [Variable] Command entries: [tick(8)] [type(1)] [playerId(1)] [payload(variable)]
 * </pre>
 * <p>
 * REF: phases.md Phase 11 - replay file format
 *
 * @param mapName        name of the map played
 * @param playerFactions array of player factions
 * @param totalTicks     total game duration in ticks
 * @param commands       list of recorded command entries
 * @param recordedAt     epoch millisecond timestamp when recording started
 * @param formatVersion  replay format version
 */
public record ReplayFile(
    String mapName,
    Faction[] playerFactions,
    long totalTicks,
    List<ReplayEntry> commands,
    long recordedAt,
    int formatVersion,
    // OPENRA #12: Expanded metadata for self-describing replays
    String gameVersion,      // e.g. "0.2.0-ALPHA" — identifies the build that recorded this
    String[] playerNames,    // display names of the players (null for campaign)
    int winnerPlayerId,      // -1 = unknown/incomplete, 0 = player 0, 1 = player 1
    long durationMillis      // game duration in milliseconds (0 if unknown)
) {
    /** Current replay format version.
     *  <p>
     *  Version history:
     *  <ul>
     *    <li>1 — original format (no recordedAt in file; loaded from file mtime)</li>
     *    <li>2 — FIX (H6 from CRITICAL_ANALYSIS_REPORT.md): adds recordedAt (8 bytes)
     *           after totalTicks so the original recording timestamp survives
     *           load/save round-trips.</li>
     *    <li>3 — OPENRA #12: expanded metadata (gameVersion, playerNames,
     *           winnerPlayerId, durationMillis) appended after the commands list.</li>
     *  </ul>
     *  Writers always emit the latest version; readers accept 1, 2, and 3.
     *  FIX (B-2 from FULL_ANALYSIS.md): The version-range check in ReplayPlayer
     *  was previously a strict equality check (only accepted 3), which made v1/v2
     *  replays unloadable. It now accepts any version in [1, FORMAT_VERSION].
     */
    public static final int FORMAT_VERSION = 3;  // OPENRA #12: bumped for expanded metadata

    /** Magic bytes identifying an AOW2 replay file. */
    public static final String MAGIC = "AOW2";

    /**
     * Compact constructor with validation.
     */
    public ReplayFile {
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("mapName must not be null or blank");
        }
        if (playerFactions == null || playerFactions.length == 0) {
            throw new IllegalArgumentException("playerFactions must not be null or empty");
        }
        if (totalTicks < 0) {
            throw new IllegalArgumentException("totalTicks must not be negative, got: " + totalTicks);
        }
        if (commands == null) {
            throw new IllegalArgumentException("commands must not be null");
        }
        if (formatVersion <= 0) {
            throw new IllegalArgumentException("formatVersion must be positive, got: " + formatVersion);
        }
    }

    /**
     * Creates a new ReplayFile with the current format version and current timestamp.
     *
     * @param mapName        map name
     * @param playerFactions player factions
     * @return new replay file with empty command list
     */
    public static ReplayFile createNew(String mapName, Faction[] playerFactions) {
        return new ReplayFile(
            mapName,
            playerFactions,
            0,
            List.of(),
            System.currentTimeMillis(),
            FORMAT_VERSION,
            "0.2.0-ALPHA",   // OPENRA #12: gameVersion
            null,             // playerNames (set later)
            -1,               // winnerPlayerId (unknown at creation)
            0                 // durationMillis (0 = unknown)
        );
    }

    /**
     * Returns the number of players in the replay.
     *
     * @return player count
     */
    public int playerCount() {
        return playerFactions.length;
    }

    /**
     * Returns the number of commands in the replay.
     *
     * @return command count
     */
    public int commandCount() {
        return commands.size();
    }

    /**
     * Returns the duration in seconds.
     * <p>
     * FIX (B-3 from FULL_ANALYSIS.md): The previous implementation divided
     * {@code totalTicks} by 30, but the game runs at {@code TICK_RATE = 10}
     * ticks per second, so the reported duration was 3× too short.
     * <p>
     * FIX (B-16 from FULL_ANALYSIS.md): For v3 replays, the file stores an
     * explicit {@code durationMillis} field (written by {@code ReplayRecorder}
     * and read by {@code ReplayPlayer}). This field is preferred when non-zero
     * because it captures wall-clock duration (including any pause time),
     * whereas {@code totalTicks / TICK_RATE} only reflects simulation time.
     * For v1/v2 replays (where {@code durationMillis == 0}), we fall back to
     * the tick-based calculation with the correct divisor.
     *
     * @return duration in seconds
     */
    public long durationSeconds() {
        // B-16: Prefer the explicit durationMillis field when available (v3+ replays).
        if (durationMillis > 0) {
            return durationMillis / 1000;
        }
        // B-3: Fallback for v1/v2 replays — use totalTicks / TICK_RATE (was / 30).
        return totalTicks / com.aow2.common.config.GameConstants.TICK_RATE;
    }
}
