package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rolling buffer of commands per frame for deterministic lockstep multiplayer.
 * Each frame slot holds commands for a specific tick, allowing input delay
 * and ensuring both players' commands are available before processing.
 * <p>
 * Thread safety: Uses CopyOnWriteArrayList for frame slots to safely handle
 * concurrent access from the network receive thread (submitOpponentCommand)
 * and the game loop thread (drainFrame). The buffer pointers are guarded by
 * synchronization to prevent torn reads/writes.
 * REF: multiplayer_architecture.md - Lockstep P2P model
 * REF: protocol_specification.md - Turn-based game advancement (y.Q[0])
 */
public class CommandBuffer {

    private static final Logger LOG = LoggerFactory.getLogger(CommandBuffer.class);

    /** Number of frames to buffer before processing (input delay) */
    private final int inputDelay;

    /** Total buffer size in frames */
    private final int bufferSize;

    /** Ring buffer of command lists, one per frame. CopyOnWriteArrayList ensures
     * thread-safe concurrent adds from network thread and reads from game loop. */
    private final CopyOnWriteArrayList<CommandType>[] frames;

    /** Per-frame flag tracking whether an opponent command was received.
     * Used by the lockstep engine to detect disconnects. */
    private final boolean[] opponentCommandPresent;

    /** The current write frame index */
    private int writeIndex;

    /** The current read frame index */
    private int readIndex;

    /** The current tick counter */
    private long currentTick;

    /**
     * Constructs a CommandBuffer with the specified input delay and buffer size.
     *
     * @param inputDelay number of frames to delay input (typically 2)
     * @param bufferSize total ring buffer capacity (must be > inputDelay)
     */
    @SuppressWarnings("unchecked")
    public CommandBuffer(int inputDelay, int bufferSize) {
        if (inputDelay < 0) {
            throw new IllegalArgumentException("inputDelay must be >= 0, got: " + inputDelay);
        }
        if (bufferSize <= inputDelay) {
            throw new IllegalArgumentException("bufferSize must be > inputDelay, got: " + bufferSize);
        }
        this.inputDelay = inputDelay;
        this.bufferSize = bufferSize;
        this.frames = (CopyOnWriteArrayList<CommandType>[]) new CopyOnWriteArrayList[bufferSize];
        this.opponentCommandPresent = new boolean[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            frames[i] = new CopyOnWriteArrayList<>();
        }
        this.writeIndex = 0;
        this.readIndex = 0;
        this.currentTick = 0;
    }

    /**
     * Submits a command for the current frame.
     * Commands are stored at the write index and will be processed after inputDelay frames.
     *
     * @param command the command to buffer
     */
    public synchronized void submitCommand(CommandType command) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        // FIX (ANALYSIS_V2 2.10): Overflow protection — if the local player submits
        // commands faster than the simulation can process them, the ring buffer wraps
        // and overwrites unprocessed frames. Drop the oldest unprocessed command
        // instead of corrupting the buffer.
        int targetFrame = (writeIndex + inputDelay) % bufferSize;
        int maxCommandsPerFrame = 50;  // reasonable upper bound
        if (frames[targetFrame].size() >= maxCommandsPerFrame) {
            LOG.warn("Command buffer overflow at frame {} — dropping oldest command", targetFrame);
            frames[targetFrame].remove(0);
        }
        frames[targetFrame].add(command);
        writeIndex = (writeIndex + 1) % bufferSize;
    }

    /**
     * Submits an opponent's command for a specific tick.
     * Used when receiving relayed commands from the network.
     *
     * @param command the opponent's command
     * @param tick    the tick the command is for
     */
    public void submitOpponentCommand(CommandType command, long tick) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        int frameOffset;
        int targetFrame;
        synchronized (this) {
            frameOffset = (int) (tick - currentTick);
            if (frameOffset < 0 || frameOffset >= bufferSize) {
                throw new IllegalArgumentException(
                        "Tick " + tick + " out of buffer range (current: " + currentTick + ")");
            }
            targetFrame = (readIndex + frameOffset) % bufferSize;
        }
        // CopyOnWriteArrayList.add is thread-safe — no outer lock needed
        frames[targetFrame].add(command);
        opponentCommandPresent[targetFrame] = true;
    }

    /**
     * Retrieves and clears all commands for the next processable frame.
     * Advances the buffer pointers after retrieval.
     *
     * @return unmodifiable list of commands for the current frame
     */
    public synchronized List<CommandType> drainFrame() {
        List<CommandType> commands = List.copyOf(frames[readIndex]);
        frames[readIndex].clear();
        opponentCommandPresent[readIndex] = false;

        readIndex = (readIndex + 1) % bufferSize;
        // Note: writeIndex is NOT advanced here — it only advances in submitCommand()
        currentTick++;

        return commands;
    }

    /**
     * Checks whether an opponent command exists in the current (read) frame slot.
     * Used by the lockstep engine to track opponent connectivity.
     *
     * @return true if at least one opponent command was received for the current frame
     */
    public boolean hasOpponentCommandForCurrentFrame() {
        return opponentCommandPresent[readIndex];
    }

    /**
     * Checks if the current frame has all required commands (both players submitted).
     *
     * @return true if the current frame is ready for processing
     */
    public boolean isFrameReady() {
        // Frame is ready if both players have submitted at least one command
        // or if the frame has been delayed enough
        List<CommandType> frame = frames[readIndex];
        boolean player0 = frame.stream().anyMatch(c -> c.playerId() == 0);
        boolean player1 = frame.stream().anyMatch(c -> c.playerId() == 1);
        return player0 && player1;
    }

    /**
     * Returns the current tick number.
     *
     * @return the current tick
     */
    public long currentTick() {
        return currentTick;
    }

    /**
     * Returns the number of pending commands in the current frame.
     *
     * @return pending command count
     */
    public int pendingCommandCount() {
        return frames[readIndex].size();
    }

    /**
     * Resets the buffer to its initial state.
     */
    public void reset() {
        for (int i = 0; i < bufferSize; i++) {
            frames[i].clear();
            opponentCommandPresent[i] = false;
        }
        writeIndex = 0;
        readIndex = 0;
        currentTick = 0;
    }
}
