package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.GridPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the CommandBuffer rolling buffer implementation.
 * Verifies command submission, frame draining, and input delay behavior.
 * REF: multiplayer_architecture.md - Lockstep P2P model with input delay
 */
class CommandBufferTest {

    private CommandBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new CommandBuffer(2, 8);
    }

    @Test
    @DisplayName("New buffer starts at tick 0 with no pending commands")
    void initialState() {
        assertEquals(0, buffer.currentTick());
        assertEquals(0, buffer.pendingCommandCount());
    }

    @Test
    @DisplayName("Submit and drain single player command")
    void submitAndDrain() {
        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        buffer.submitCommand(cmd);

        // After 2 input delay frames, drain empty frames first
        buffer.drainFrame(); // tick 0
        buffer.drainFrame(); // tick 1

        // The command should now be at the read position
        var commands = buffer.drainFrame(); // tick 2
        assertEquals(1, commands.size());
        assertEquals(cmd, commands.getFirst());
    }

    @Test
    @DisplayName("Submit null command throws exception")
    void rejectNullCommand() {
        assertThrows(IllegalArgumentException.class, () -> buffer.submitCommand(null));
    }

    @Test
    @DisplayName("Submit opponent command for specific tick")
    void submitOpponentCommand() {
        var cmd = new CommandType.Move(0, 1, new int[]{2}, new GridPosition(3, 3));
        buffer.submitOpponentCommand(cmd, 0);
        assertEquals(1, buffer.pendingCommandCount());
    }

    @Test
    @DisplayName("Submit opponent command for out-of-range tick throws")
    void rejectOutOfRangeTick() {
        var cmd = new CommandType.Move(100, 1, new int[]{2}, new GridPosition(3, 3));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.submitOpponentCommand(cmd, 100));
    }

    @Test
    @DisplayName("Frame readiness requires both players (per-frame pacing model)")
    void frameReadiness() {
        // OPENRA #1: Per-frame pacing — both local AND opponent must have submitted
        // for the SAME frame. With inputDelay=2, submitCommand writes to frame
        // (writeIndex + 2) % bufferSize = frame 2. submitOpponentCommand for tick 2
        // also writes to frame 2. After draining frames 0 and 1 (empty), frame 2
        // is at readIndex and isFrameReady should be true.

        // Submit local pacing for frame 2
        buffer.submitNoOp();  // writeIndex=0 -> targetFrame = (0+2)%bufSize = 2
        // Submit opponent command for tick 2
        var cmd = new CommandType.Move(2, 1, new int[]{1}, new GridPosition(5, 5));
        buffer.submitOpponentCommand(cmd, 2);  // frameOffset = 2-0 = 2, targetFrame = (0+2)%bufSize = 2

        // Drain frames 0 and 1 (not ready — no submissions)
        assertFalse(buffer.isFrameReady(), "Frame 0 not ready");
        buffer.drainFrame();  // advance to frame 1
        assertFalse(buffer.isFrameReady(), "Frame 1 not ready");
        buffer.drainFrame();  // advance to frame 2

        // Frame 2 should be ready — both local and opponent submitted
        assertTrue(buffer.isFrameReady(), "Frame 2 ready: both submitted");
    }

    @Test
    @DisplayName("Drain frame advances tick counter")
    void drainAdvancesTick() {
        buffer.drainFrame();
        assertEquals(1, buffer.currentTick());
        buffer.drainFrame();
        assertEquals(2, buffer.currentTick());
    }

    @Test
    @DisplayName("Reset clears all state")
    void resetClearsState() {
        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        buffer.submitCommand(cmd);
        buffer.drainFrame();

        buffer.reset();
        assertEquals(0, buffer.currentTick());
        assertEquals(0, buffer.pendingCommandCount());
    }

    @Test
    @DisplayName("Invalid constructor parameters throw")
    void invalidConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new CommandBuffer(-1, 8));
        assertThrows(IllegalArgumentException.class, () -> new CommandBuffer(2, 2));
    }
}
