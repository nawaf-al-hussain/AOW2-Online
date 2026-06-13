package com.aow2.core.replay;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Records all player commands for replay playback.
 * Captures the full command stream during a game session.
 * <p>
 * REF: phases.md Phase 11 - full replay recording and playback
 */
public final class ReplayRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayRecorder.class);

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
     * The command is serialized into a ReplayEntry and added to the command list.
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
     * Serializes a CommandType into a ReplayEntry.
     * Uses the command's tick, type ordinal, player ID, and a compact
     * binary payload representation.
     *
     * @param command the command to serialize
     * @return serialized replay entry
     */
    private ReplayEntry serializeCommand(CommandType command) {
        int typeOrd = switch (command) {
            case CommandType.Move m -> 0;
            case CommandType.Attack a -> 1;
            case CommandType.Build b -> 2;
            case CommandType.Produce p -> 3;
            case CommandType.Research r -> 4;
            case CommandType.Garrison g -> 5;
            case CommandType.Ungarrison u -> 6;
            case CommandType.Cancel c -> 7;
            case CommandType.SiegeMode s -> 8;
            case CommandType.Stop st -> 9;
            case CommandType.Patrol pt -> 10;
        };

        byte[] payload = encodePayload(command);
        return new ReplayEntry(command.tick(), typeOrd, command.playerId(), payload);
    }

    /**
     * Encodes a command into a compact binary payload.
     * ASSUMPTION: Simple encoding; production version would use CommandSerializer.
     */
    private byte[] encodePayload(CommandType command) {
        return switch (command) {
            case CommandType.Move m -> {
                ByteBuffer buf = ByteBuffer.allocate(8 + m.unitIds().length * 4 + 8);
                buf.putInt(m.unitIds().length);
                for (int id : m.unitIds()) buf.putInt(id);
                buf.putInt(m.target().x());
                buf.putInt(m.target().y());
                yield compactBuffer(buf);
            }
            case CommandType.Attack a -> {
                ByteBuffer buf = ByteBuffer.allocate(4 + a.unitIds().length * 4 + 4);
                buf.putInt(a.unitIds().length);
                for (int id : a.unitIds()) buf.putInt(id);
                buf.putInt(a.targetId());
                yield compactBuffer(buf);
            }
            case CommandType.Build b -> {
                ByteBuffer buf = ByteBuffer.allocate(4 + 8);
                buf.putInt(b.buildingType().ordinal());
                buf.putInt(b.position().x());
                buf.putInt(b.position().y());
                yield compactBuffer(buf);
            }
            case CommandType.Produce p -> {
                ByteBuffer buf = ByteBuffer.allocate(8);
                buf.putInt(p.producerId());
                buf.putInt(p.unitType().ordinal());
                yield compactBuffer(buf);
            }
            case CommandType.Research r -> {
                ByteBuffer buf = ByteBuffer.allocate(8);
                buf.putInt(r.techCentreId());
                buf.putInt(r.researchId());
                yield compactBuffer(buf);
            }
            case CommandType.Garrison g -> {
                ByteBuffer buf = ByteBuffer.allocate(4 + g.unitIds().length * 4 + 4);
                buf.putInt(g.unitIds().length);
                for (int id : g.unitIds()) buf.putInt(id);
                buf.putInt(g.buildingId());
                yield compactBuffer(buf);
            }
            case CommandType.Ungarrison u -> {
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.putInt(u.buildingId());
                yield compactBuffer(buf);
            }
            case CommandType.Cancel c -> {
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.putInt(c.entityId());
                yield compactBuffer(buf);
            }
            case CommandType.SiegeMode s -> {
                ByteBuffer buf = ByteBuffer.allocate(5);
                buf.putInt(s.unitId());
                buf.put(s.enabled() ? (byte) 1 : (byte) 0);
                yield compactBuffer(buf);
            }
            case CommandType.Stop st -> {
                ByteBuffer buf = ByteBuffer.allocate(4 + st.unitIds().length * 4);
                buf.putInt(st.unitIds().length);
                for (int id : st.unitIds()) buf.putInt(id);
                yield compactBuffer(buf);
            }
            case CommandType.Patrol pt -> {
                ByteBuffer buf = ByteBuffer.allocate(4 + pt.unitIds().length * 4 + 8);
                buf.putInt(pt.unitIds().length);
                for (int id : pt.unitIds()) buf.putInt(id);
                buf.putInt(pt.waypoint().x());
                buf.putInt(pt.waypoint().y());
                yield compactBuffer(buf);
            }
        };
    }

    /**
     * Returns a compact byte array from the buffer (only the written portion).
     */
    private byte[] compactBuffer(ByteBuffer buf) {
        byte[] result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }
}
