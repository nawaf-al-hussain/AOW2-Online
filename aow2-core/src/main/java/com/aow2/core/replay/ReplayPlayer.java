package com.aow2.core.replay;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.core.network.CommandSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays back recorded replays by re-executing commands.
 * Supports seeking to any game tick by rebuilding state from start.
 * <p>
 * REF: phases.md Phase 11 - full replay recording and playback
 */
public final class ReplayPlayer {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayPlayer.class);

    /** The loaded replay file. */
    private ReplayFile replay;

    /** Current playback tick position. */
    private long currentTick;

    /** Whether playback is active. */
    private boolean playing;

    /** Index of the next command to process. */
    private int nextCommandIndex;

    /** Callback for command execution during playback. */
    private CommandCallback commandCallback;

    /** Interval between state snapshots for efficient seeking. */
    private static final long SNAPSHOT_INTERVAL = 1000;

    /** State snapshots: tick -> command index. Used for efficient backward seeking. */
    private final Map<Long, Integer> snapshots = new LinkedHashMap<>();

    /**
     * Callback interface for executing commands during replay playback.
     */
    @FunctionalInterface
    public interface CommandCallback {
        /**
         * Called when a command should be executed during playback.
         *
         * @param command the command to execute
         */
        void onCommand(CommandType command);
    }

    /**
     * Constructs a new ReplayPlayer.
     */
    public ReplayPlayer() {
        this.playing = false;
        this.currentTick = 0;
        this.nextCommandIndex = 0;
    }

    /**
     * Loads a replay file for playback.
     *
     * @param replay the replay file to load
     */
    public void loadReplay(ReplayFile replay) {
        this.replay = replay;
        this.currentTick = 0;
        this.nextCommandIndex = 0;
        this.playing = false;
        this.snapshots.clear();

        LOG.info("Loaded replay: {} ({} ticks, {} commands)",
            replay.mapName(), replay.totalTicks(), replay.commandCount());
    }

    /**
     * Starts playback from the beginning.
     * Resets tick counter and command index.
     */
    public void play() {
        if (replay == null) {
            LOG.warn("No replay loaded");
            return;
        }

        this.currentTick = 0;
        this.nextCommandIndex = 0;
        this.playing = true;

        LOG.info("Replay playback started from tick 0");
    }

    /**
     * Pauses playback. Current tick position is preserved.
     */
    public void pause() {
        this.playing = false;
        LOG.info("Replay playback paused at tick {}", currentTick);
    }

    /**
     * Resumes playback from the current position.
     */
    public void resume() {
        if (replay == null) {
            LOG.warn("No replay loaded");
            return;
        }
        this.playing = true;
        LOG.info("Replay playback resumed at tick {}", currentTick);
    }

    /**
     * Advances playback by one tick.
     * Executes all commands scheduled for the current tick.
     */
    public void stepForward() {
        if (replay == null || !playing) {
            return;
        }

        currentTick++;
        processCommandsForTick(currentTick);
        recordSnapshotIfNeeded();

        // Check if playback has ended
        if (currentTick >= replay.totalTicks()) {
            playing = false;
            LOG.info("Replay playback ended at tick {}", currentTick);
        }
    }

    /**
     * Seeks to a specific tick.
     * If the target is before the current position, rebuilds from the nearest snapshot.
     * If the target is after, fast-forwards by executing commands.
     *
     * @param tick target tick to seek to
     */
    public void seekTo(long tick) {
        if (replay == null) {
            LOG.warn("No replay loaded");
            return;
        }

        if (tick < 0 || tick > replay.totalTicks()) {
            throw new IllegalArgumentException(
                "Tick out of range: " + tick + " (0-" + replay.totalTicks() + ")");
        }

        if (tick < currentTick) {
            // Find the most recent snapshot before the target tick
            long bestSnapshotTick = 0;
            int bestCommandIndex = 0;
            for (Map.Entry<Long, Integer> entry : snapshots.entrySet()) {
                if (entry.getKey() <= tick) {
                    bestSnapshotTick = entry.getKey();
                    bestCommandIndex = entry.getValue();
                } else {
                    break;
                }
            }
            // Seek back to nearest snapshot instead of tick 0
            LOG.debug("Seeking backward to tick {}: using snapshot at tick {} (saving {} ticks of replay)",
                tick, bestSnapshotTick, currentTick - bestSnapshotTick);
            currentTick = bestSnapshotTick;
            nextCommandIndex = bestCommandIndex;
        }

        // Fast-forward to target tick
        while (currentTick < tick) {
            currentTick++;
            processCommandsForTick(currentTick);
            recordSnapshotIfNeeded();
        }

        LOG.info("Seeked to tick {}", currentTick);
    }

    /**
     * Records a state snapshot at the current tick if the snapshot interval has elapsed.
     * Should be called after processing commands for a tick.
     */
    private void recordSnapshotIfNeeded() {
        if (currentTick > 0 && currentTick % SNAPSHOT_INTERVAL == 0) {
            snapshots.put(currentTick, nextCommandIndex);
            LOG.debug("Replay snapshot recorded at tick {} (command index {})", currentTick, nextCommandIndex);
        }
    }

    /**
     * Processes all commands that occur at the given tick.
     *
     * @param tick the tick to process
     */
    private void processCommandsForTick(long tick) {
        if (commandCallback == null) return;

        while (nextCommandIndex < replay.commands().size()) {
            ReplayEntry entry = replay.commands().get(nextCommandIndex);
            if (entry.tick() > tick) {
                break; // Future command, stop processing
            }
            if (entry.tick() == tick) {
                CommandType command = deserializeCommand(entry);
                if (command != null) {
                    commandCallback.onCommand(command);
                }
            }
            nextCommandIndex++;
        }
    }

    /**
     * Deserializes a ReplayEntry back into a CommandType.
     * Since ReplayRecorder now stores the full CommandSerializer payload
     * (including the type byte, tick, and playerId header), the entry
     * payload can be directly passed to CommandSerializer.deserialize().
     *
     * @param entry the replay entry to deserialize
     * @return the deserialized command, or null on error
     */
    private CommandType deserializeCommand(ReplayEntry entry) {
        try {
            return CommandSerializer.deserialize(entry.payload());
        } catch (Exception e) {
            LOG.error("Failed to deserialize replay entry at tick {}: {}", entry.tick(), e.getMessage());
            return null;
        }
    }

    /**
     * Loads a replay from a binary file.
     *
     * @param filePath path to the replay file
     * @return loaded ReplayFile, or null on error
     */
    public static ReplayFile loadFromFile(Path filePath) {
        try (InputStream fis = Files.newInputStream(filePath);
             DataInputStream dis = new DataInputStream(fis)) {

            // Magic
            byte[] magic = new byte[4];
            dis.readFully(magic);
            String magicStr = new String(magic, StandardCharsets.UTF_8);
            if (!ReplayFile.MAGIC.equals(magicStr)) {
                throw new IOException("Invalid replay file magic: " + magicStr);
            }

            // Format version
            int formatVersion = dis.readShort();
            if (formatVersion != ReplayFile.FORMAT_VERSION) {
                throw new IOException("Unsupported replay format version: " + formatVersion);
            }

            // Map name
            int nameLength = dis.readInt();
            byte[] nameBytes = new byte[nameLength];
            dis.readFully(nameBytes);
            String mapName = new String(nameBytes, StandardCharsets.UTF_8);

            // Player factions
            int playerCount = dis.readInt();
            Faction[] factions = new Faction[playerCount];
            Faction[] factionValues = Faction.values();
            for (int i = 0; i < playerCount; i++) {
                int ordinal = dis.readByte();
                if (ordinal < 0 || ordinal >= factionValues.length) {
                    throw new IOException("Invalid faction ordinal: " + ordinal);
                }
                factions[i] = factionValues[ordinal];
            }

            // Total ticks
            long totalTicks = dis.readLong();

            // Commands
            int commandCount = dis.readInt();
            List<ReplayEntry> commands = new ArrayList<>(commandCount);
            for (int i = 0; i < commandCount; i++) {
                long tick = dis.readLong();
                int typeOrd = dis.readByte() & 0xFF;
                int playerId = dis.readByte() & 0xFF;
                int payloadLength = dis.readShort() & 0xFFFF;
                byte[] payload = new byte[payloadLength];
                dis.readFully(payload);
                commands.add(new ReplayEntry(tick, typeOrd, playerId, payload));
            }

            ReplayFile replay = new ReplayFile(
                mapName, factions, totalTicks,
                List.copyOf(commands), System.currentTimeMillis(), formatVersion
            );

            LOG.info("Loaded replay from {} ({}x{} ticks, {} commands)",
                filePath, mapName, totalTicks, commandCount);
            return replay;

        } catch (IOException e) {
            LOG.error("Failed to load replay: {}", filePath, e);
            return null;
        }
    }

    // --- Getters ---

    /**
     * Returns the current playback tick.
     *
     * @return current tick
     */
    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * Returns the total replay duration in ticks.
     *
     * @return total ticks, or 0 if no replay loaded
     */
    public long getTotalTicks() {
        return replay != null ? replay.totalTicks() : 0;
    }

    /**
     * Returns whether playback is currently active.
     *
     * @return true if playing
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * Returns whether a replay is loaded.
     *
     * @return true if a replay is loaded
     */
    public boolean hasReplay() {
        return replay != null;
    }

    /**
     * Returns the loaded replay file.
     *
     * @return the replay file, or null
     */
    public ReplayFile getReplay() {
        return replay;
    }

    /**
     * Sets the command callback for playback.
     *
     * @param callback the callback to invoke for each command
     */
    public void setCommandCallback(CommandCallback callback) {
        this.commandCallback = callback;
    }
}
