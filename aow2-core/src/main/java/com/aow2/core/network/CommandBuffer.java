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

    /** OPENRA #1: Per-frame pacing — tracks whether the local player has submitted
     * any command (or NO_OP) for each frame. A frame is ready when both
     * localCommandPresent AND opponentCommandPresent are true. */
    private final boolean[] localCommandPresent;

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
        this.localCommandPresent = new boolean[bufferSize];
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
     * <p>
     * FIX (B-9 from FULL_ANALYSIS.md): This method NO LONGER advances writeIndex.
     * Previously, both submitCommand and submitNoOp advanced writeIndex, causing
     * writeIndex to advance by N+1 per frame (N commands + 1 NoOp) while readIndex
     * only advanced by 1, eventually corrupting the ring buffer. Now writeIndex is
     * advanced exactly once per frame by {@link #submitNoOp()}, keeping it in sync
     * with readIndex. Multiple commands submitted in the same frame all target the
     * same frame slot, which is the intended design (each frame slot holds a
     * CopyOnWriteArrayList of commands).
     *
     * @param command the command to buffer
     */
    public synchronized void submitCommand(CommandType command) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        int targetFrame = (writeIndex + inputDelay) % bufferSize;
        int maxCommandsPerFrame = 50;
        if (frames[targetFrame].size() >= maxCommandsPerFrame) {
            LOG.warn("Command buffer overflow at frame {} — dropping oldest command", targetFrame);
            frames[targetFrame].remove(0);
        }
        frames[targetFrame].add(command);
        localCommandPresent[targetFrame] = true;
        // Note: writeIndex is intentionally NOT advanced here. It is advanced
        // exactly once per frame by submitNoOp() to stay in sync with readIndex.
    }

    /**
     * OPENRA #1: Submits a NO_OP (no-operation) packet for the current frame.
     * This is the per-frame pacing signal — even when the local player has no
     * commands, this method must be called once per frame so the simulation
     * can advance. Replaces the old heartbeat band-aid.
     * <p>
     * FIX (B-9): This is the ONLY method that advances writeIndex. It must be
     * called exactly once per game frame (typically at the end of
     * LockstepEngine.processFrame) to keep writeIndex in sync with readIndex
     * (which is advanced once per frame by drainFrame).
     */
    public synchronized void submitNoOp() {
        int targetFrame = (writeIndex + inputDelay) % bufferSize;
        localCommandPresent[targetFrame] = true;
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
        localCommandPresent[readIndex] = false;

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
        // OPENRA #1: Per-frame pacing — a frame is ready when BOTH players have
        // submitted their pacing packet (command or NO_OP). This replaces the old
        // model where both players needed actual commands, which caused false
        // disconnects when an opponent was idle.
        return localCommandPresent[readIndex] && opponentCommandPresent[readIndex];
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
     * <p>
     * FIX (B-5 from FULL_ANALYSIS.md): This method is now synchronized to match
     * the contract of submitCommand/submitNoOp/drainFrame. Without synchronization,
     * a concurrent reset could leave buffer pointers and per-frame flag arrays in
     * a partially-cleared state visible to other threads.
     */
    public synchronized void reset() {
        for (int i = 0; i < bufferSize; i++) {
            frames[i].clear();
            opponentCommandPresent[i] = false;
            localCommandPresent[i] = false;
        }
        writeIndex = 0;
        readIndex = 0;
        currentTick = 0;
    }
}
