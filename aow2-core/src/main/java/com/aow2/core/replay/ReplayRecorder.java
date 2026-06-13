package com.aow2.core.replay;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.core.network.CommandSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Records all player commands for replay playback.
 * Captures the full command stream during a game session.
 * Uses CommandSerializer for wire format compatibility with ReplayPlayer.
 * <p>
 * REF: phases.md Phase 11 - full replay recording and playback
 */
public final class ReplayRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayRecorder.class);

    /** Command type IDs matching CommandSerializer wire protocol (0x01-0x0B). */
    private static final int TYPE_MOVE = 0x01;
    private static final int TYPE_ATTACK = 0x02;
    private static final int TYPE_BUILD = 0x03;
    private static final int TYPE_PRODUCE = 0x04;
    private static final int TYPE_RESEARCH = 0x05;
    private static final int TYPE_GARRISON = 0x06;
    private static final int TYPE_UNGARRISON = 0x07;
    private static final int TYPE_CANCEL = 0x08;
    private static final int TYPE_SIEGE_MODE = 0x09;
    private static final int TYPE_STOP = 0x0A;
    private static final int TYPE_PATROL = 0x0B;

    /** Whether recording is active. */
    private boolean recording;

    /** The current replay being recorded. */
    private ReplayFile currentReplay;

    /** Mutable command list for the current recording. */
    private List<ReplayEntry> currentCommands;

    /** Map name for the current recording. */
    private String currentMapName;

    /** Player factions for the current recording. */
    private Faction[] currentPlayerFactions;

    /**
     * Constructs a new ReplayRecorder.
     */
    public ReplayRecorder() {
        this.recording = false;
    }

    /**
     * Starts recording a new replay.
     *
     * @param mapName        name of the map
     * @param playerFactions array of player factions
     */
    public void startRecording(String mapName, Faction[] playerFactions) {
        if (recording) {
            LOG.warn("Already recording, stopping previous recording first");
            stopRecording();
        }

        this.currentMapName = mapName;
        this.currentPlayerFactions = playerFactions;
        this.currentCommands = new ArrayList<>();
        this.recording = true;

        LOG.info("Started recording replay: map={}, players={}", mapName, playerFactions.length);
    }

    /**
     * Records a command during the current game session.
     * The command is serialized using CommandSerializer and stored as a ReplayEntry.
     *
     * @param command the command to record
     */
    public void recordCommand(CommandType command) {
        if (!recording) {
            return;
        }

        ReplayEntry entry = serializeCommand(command);
        currentCommands.add(entry);
    }

    /**
     * Stops recording and returns the completed replay file.
     *
     * @return the completed replay file, or null if not recording
     */
    public ReplayFile stopRecording() {
        if (!recording) {
            LOG.warn("Not recording, nothing to stop");
            return null;
        }

        recording = false;

        // Calculate total ticks from last command
        long totalTicks = 0;
        if (!currentCommands.isEmpty()) {
            totalTicks = currentCommands.getLast().tick();
        }

        currentReplay = new ReplayFile(
            currentMapName,
            currentPlayerFactions,
            totalTicks,
            List.copyOf(currentCommands),
            System.currentTimeMillis(),
            ReplayFile.FORMAT_VERSION
        );

        LOG.info("Stopped recording: {} commands, {} ticks", currentCommands.size(), totalTicks);
        return currentReplay;
    }

    /**
     * Saves the current replay to a binary file.
     * Format:
     * <pre>
     * [4 bytes]  Magic: "AOW2"
     * [2 bytes]  Format version
     * [4 bytes]  Map name length
     * [N bytes]  Map name (UTF-8)
     * [4 bytes]  Number of players
     * [N bytes]  Player faction ordinals (1 byte each)
     * [8 bytes]  Total ticks
     * [4 bytes]  Number of commands
     * [Variable] Command entries
     * </pre>
     *
     * @param filePath path to save the replay file
     * @return true if save succeeded
     */
    public boolean saveReplay(Path filePath) {
        ReplayFile replay = currentReplay;
        if (replay == null) {
            LOG.warn("No replay to save");
            return false;
        }

        try (OutputStream fos = Files.newOutputStream(filePath);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // Magic
            dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));

            // Format version
            dos.writeShort(replay.formatVersion());

            // Map name
            byte[] nameBytes = replay.mapName().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(nameBytes.length);
            dos.write(nameBytes);

            // Player factions
            dos.writeInt(replay.playerFactions().length);
            for (Faction faction : replay.playerFactions()) {
                dos.writeByte(faction.ordinal());
            }

            // Total ticks
            dos.writeLong(replay.totalTicks());

            // Commands
            dos.writeInt(replay.commands().size());
            for (ReplayEntry entry : replay.commands()) {
                dos.writeLong(entry.tick());
                dos.writeByte(entry.typeOrd());
                dos.writeByte(entry.playerId());
                dos.writeShort(entry.payload().length);
                dos.write(entry.payload());
            }

            LOG.info("Replay saved to {} ({} bytes, {} commands)",
                filePath, dos.size(), replay.commands().size());
            return true;

        } catch (IOException e) {
            LOG.error("Failed to save replay: {}", filePath, e);
            return false;
        }
    }

    /**
     * Returns whether the recorder is currently recording.
     *
     * @return true if recording
     */
    public boolean isRecording() {
        return recording;
    }

    /**
     * Returns the number of commands recorded so far.
     *
     * @return command count
     */
    public int recordedCommandCount() {
        return currentCommands != null ? currentCommands.size() : 0;
    }

    /**
     * Returns the last completed replay file, or null.
     *
     * @return the last replay file
     */
    public ReplayFile getCurrentReplay() {
        return currentReplay;
    }

    /**
     * Serializes a CommandType into a ReplayEntry using CommandSerializer.
     * The payload is the FULL serialized command from CommandSerializer
     * (including the type byte, tick, playerId header), so it can be
     * directly passed to CommandSerializer.deserialize() during playback.
     *
     * @param command the command to serialize
     * @return serialized replay entry
     */
    private ReplayEntry serializeCommand(CommandType command) {
        int typeOrd = switch (command) {
            case CommandType.Move m -> TYPE_MOVE;
            case CommandType.Attack a -> TYPE_ATTACK;
            case CommandType.Build b -> TYPE_BUILD;
            case CommandType.Produce p -> TYPE_PRODUCE;
            case CommandType.Research r -> TYPE_RESEARCH;
            case CommandType.Garrison g -> TYPE_GARRISON;
            case CommandType.Ungarrison u -> TYPE_UNGARRISON;
            case CommandType.Cancel c -> TYPE_CANCEL;
            case CommandType.SiegeMode s -> TYPE_SIEGE_MODE;
            case CommandType.Stop st -> TYPE_STOP;
            case CommandType.Patrol pt -> TYPE_PATROL;
        };

        byte[] fullPayload = CommandSerializer.serialize(command);
        return new ReplayEntry(command.tick(), typeOrd, command.playerId(), fullPayload);
    }
}
