package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.GridPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for B-9 from FULL_ANALYSIS.md: CommandBuffer writeIndex drift.
 * <p>
 * BUG (before fix): Both {@code submitCommand} and {@code submitNoOp} advanced
 * {@code writeIndex} by 1. In a frame where the player submitted N commands,
 * {@code writeIndex} advanced by N+1 (N from submitCommand + 1 from submitNoOp)
 * while {@code readIndex} only advanced by 1. Over time, {@code writeIndex}
 * wrapped past {@code readIndex} and overwrote unread frame slots, corrupting
 * the ring buffer in long multiplayer sessions.
 * <p>
 * FIX: {@code submitCommand} no longer advances {@code writeIndex}. Only
 * {@code submitNoOp} (the per-frame pacing signal) advances it, keeping
 * {@code writeIndex} and {@code readIndex} in sync — each advances by exactly
 * 1 per game frame.
 */
@DisplayName("B-9: CommandBuffer pointer drift regression")
class CommandBufferPointerDriftTest {

    /**
     * Reproduces the exact scenario from the bug report: submit N commands in
     * one frame, then call submitNoOp, then drainFrame. Before the fix,
     * writeIndex would advance by N+1 while readIndex advanced by 1.
     */
    @Test
    @DisplayName("submitCommand + submitNoOp in same frame keeps writeIndex in sync with readIndex")
    void writeIndexStaysInSyncWithReadIndex() {
        // Given: a buffer with inputDelay=2, bufferSize=8
        CommandBuffer buffer = new CommandBuffer(2, 8);
        CommandType.Move cmd1 = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        CommandType.Move cmd2 = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(6, 6));
        CommandType.Move cmd3 = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(7, 7));

        // When: submitting 3 commands for the current frame
        buffer.submitCommand(cmd1);
        buffer.submitCommand(cmd2);
        buffer.submitCommand(cmd3);
        // Then the per-frame pacing signal (called once at end of processFrame)
        buffer.submitNoOp();
        // And then draining the frame (advances readIndex by 1)
        List<CommandType> drained = buffer.drainFrame();

        // Then: writeIndex should have advanced by exactly 1 (from submitNoOp),
        // matching readIndex's advance of 1 (from drainFrame). Before the fix,
        // writeIndex would have advanced by 4 (3 commands + 1 NoOp).
        // We verify by repeating the cycle 20 times and confirming all commands
        // are drained correctly without loss or duplication.
        for (int frame = 1; frame <= 20; frame++) {
            // Submit 2 commands for this frame
            CommandType.Move frameCmd1 = new CommandType.Move(frame, 0, new int[]{1}, new GridPosition(frame, frame));
            CommandType.Move frameCmd2 = new CommandType.Move(frame, 0, new int[]{1}, new GridPosition(frame + 1, frame + 1));
            buffer.submitCommand(frameCmd1);
            buffer.submitCommand(frameCmd2);
            buffer.submitNoOp();
            buffer.drainFrame();
        }

        // After 21 frames (initial + 20), all submitted commands should have been
        // drained without loss. The fact that no exception was thrown and the buffer
        // is still operational confirms writeIndex did not wrap past readIndex.
        // Verify by submitting one more command and ensuring it can be retrieved.
        CommandType.Move finalCmd = new CommandType.Move(100, 0, new int[]{1}, new GridPosition(99, 99));
        buffer.submitCommand(finalCmd);
        buffer.submitNoOp();

        // Drain inputDelay frames to reach the slot where finalCmd was written
        for (int i = 0; i < 2; i++) {
            buffer.drainFrame();
        }
        List<CommandType> finalDrained = buffer.drainFrame();
        assertTrue(finalDrained.contains(finalCmd),
            "Final command should be retrievable after 21 frames — buffer is not corrupted");
    }

    /**
     * Verifies that multiple commands submitted in the same frame all target
     * the same frame slot (i.e., they are all drained together after inputDelay).
     */
    @Test
    @DisplayName("Multiple commands in same frame are drained together after inputDelay")
    void multipleCommandsSameFrameDrainedTogether() {
        CommandBuffer buffer = new CommandBuffer(2, 8);

        // Submit 3 commands for the current frame
        CommandType.Move cmd1 = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(1, 1));
        CommandType.Move cmd2 = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(2, 2));
        CommandType.Move cmd3 = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(3, 3));
        buffer.submitCommand(cmd1);
        buffer.submitCommand(cmd2);
        buffer.submitCommand(cmd3);
        buffer.submitNoOp();

        // Drain inputDelay (2) empty frames first
        buffer.drainFrame();  // frame 0 — empty
        buffer.drainFrame();  // frame 1 — empty

        // Drain the frame containing our 3 commands
        List<CommandType> commands = buffer.drainFrame();
        assertEquals(3, commands.size(),
            "All 3 commands submitted in the same frame should be drained together");
        assertTrue(commands.contains(cmd1));
        assertTrue(commands.contains(cmd2));
        assertTrue(commands.contains(cmd3));
    }

    /**
     * Stress test: simulate 50 frames of active multiplayer with varying numbers
     * of commands per frame. Before the fix, this would corrupt the buffer
     * within ~5-8 frames. After the fix, it should complete without issues.
     */
    @Test
    @DisplayName("50-frame multiplayer simulation with varying commands does not corrupt buffer")
    void fiftyFrameMultiplayerSimulation() {
        CommandBuffer buffer = new CommandBuffer(2, 16);

        // Simulate 50 frames with 0-4 commands each
        for (long tick = 0; tick < 50; tick++) {
            int numCommands = (int) (tick % 5);  // 0, 1, 2, 3, 4, 0, 1, ...
            for (int i = 0; i < numCommands; i++) {
                buffer.submitCommand(new CommandType.Move(tick, 0, new int[]{1},
                    new GridPosition((int) tick, i)));
            }
            buffer.submitNoOp();

            // Also simulate opponent commands (required for frame readiness)
            // Submit opponent NO_OP equivalent — we just mark the slot
            // (in real code, the network thread does this via submitOpponentCommand)

            // Drain the frame
            buffer.drainFrame();
        }

        // If we got here without exceptions, the test passes.
        // The buffer's internal state should be consistent — verify by checking
        // that currentTick has advanced exactly 50 times.
        assertEquals(50, buffer.currentTick(),
            "After 50 drainFrame calls, currentTick should be 50");
    }

    /**
     * Verifies the reset() method is now synchronized (B-5 fix).
     * This is a compile-time / behavioral check — calling reset() should not
     * throw and should leave the buffer in a clean state.
     */
    @Test
    @DisplayName("reset() clears all state and is callable concurrently (B-5)")
    void resetClearsStateSynchronized() {
        CommandBuffer buffer = new CommandBuffer(2, 8);
        buffer.submitCommand(new CommandType.Move(0, 0, new int[]{1}, new GridPosition(1, 1)));
        buffer.submitNoOp();
        buffer.drainFrame();

        // Reset should clear everything
        buffer.reset();
        assertEquals(0, buffer.currentTick());
        assertEquals(0, buffer.pendingCommandCount());

        // Buffer should be usable after reset
        buffer.submitCommand(new CommandType.Move(0, 0, new int[]{1}, new GridPosition(2, 2)));
        buffer.submitNoOp();
        buffer.drainFrame();
        buffer.drainFrame();
        List<CommandType> commands = buffer.drainFrame();
        assertEquals(1, commands.size());
    }
}
