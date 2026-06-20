package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the LockstepEngine deterministic multiplayer engine.
 * Verifies command submission, frame processing, and desync detection.
 * REF: multiplayer_architecture.md - Lockstep P2P model
 * REF: protocol_specification.md - Server-authoritative lockstep
 */
class LockstepEngineTest {

    private LockstepEngine engine;
    private GameState state;
    private EntityManager entities;
    private List<byte[]> sentData;

    @BeforeEach
    void setUp() {
        engine = new LockstepEngine(2, 8, 5);
        state = new GameState();
        entities = new EntityManager();
        sentData = new ArrayList<>();
        engine.start(sentData::add);
    }

    @Test
    @DisplayName("Engine starts in running state")
    void startState() {
        assertTrue(engine.isRunning());
        assertEquals(0, engine.getLockstepFrame());
    }

    @Test
    @DisplayName("Stop sets running to false")
    void stopEngine() {
        engine.stop();
        assertFalse(engine.isRunning());
    }

    @Test
    @DisplayName("Submit command sends serialized data via callback")
    void submitCommandSendsData() {
        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        engine.submitCommand(cmd);
        assertEquals(1, sentData.size());
    }

    @Test
    @DisplayName("Submit command when stopped does nothing")
    void submitWhenStopped() {
        engine.stop();
        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        engine.submitCommand(cmd);
        assertEquals(0, sentData.size());
    }

    @Test
    @DisplayName("Receive command deserializes and buffers")
    void receiveCommand() {
        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        byte[] data = CommandSerializer.serialize(cmd);
        engine.receiveCommand(data);
        // Command should be buffered without error
    }

    @Test
    @DisplayName("Process frame advances lockstep frame")
    void processFrameAdvances() {
        engine.processFrame(state, entities);
        assertEquals(1, engine.getLockstepFrame());
        engine.processFrame(state, entities);
        assertEquals(2, engine.getLockstepFrame());
    }

    @Test
    @DisplayName("Process frame when stopped returns empty list")
    void processFrameWhenStopped() {
        engine.stop();
        var commands = engine.processFrame(state, entities);
        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("Desync callback is triggered on hash mismatch")
    void desyncCallback() {
        List<Long> desyncFrames = new ArrayList<>();
        engine.setDesyncCallback(desyncFrames::add);

        // Process some frames to trigger sync check
        for (int i = 0; i < 5; i++) {
            engine.processFrame(state, entities);
        }

        // Report a mismatched remote hash
        boolean desync = engine.reportRemoteSyncHash(99999L);
        assertTrue(desync);
        assertEquals(1, desyncFrames.size());
    }

    @Test
    @DisplayName("Matching remote hash does not trigger desync")
    void matchingHashNoDesync() {
        List<Long> desyncFrames = new ArrayList<>();
        engine.setDesyncCallback(desyncFrames::add);

        // Process frames to get a local hash
        for (int i = 0; i < 5; i++) {
            engine.processFrame(state, entities);
        }

        long localHash = engine.getSyncChecker().getLocalHash();
        boolean desync = engine.reportRemoteSyncHash(localHash);
        assertFalse(desync);
        assertTrue(desyncFrames.isEmpty());
    }

    @Test
    @DisplayName("Move command sets unit target position")
    void moveCommandApplied() {
        var stats = new UnitStats(UnitType.CONFED_INFANTRY, "Test", 100, 10,
                5, 3, 1, 8, 1, WeaponType.BULLET, 5, 20, 50, 5, 0, 0, 0, 0);
        var unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(0, 0),
                UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(10, 10));
        engine.submitCommand(cmd);

        // Drain delay frames
        engine.processFrame(state, entities);
        engine.processFrame(state, entities);

        // Process the frame with the command
        engine.processFrame(state, entities);

        assertEquals(new GridPosition(10, 10), unit.getTargetPosition());
    }

    @Test
    @DisplayName("Reset clears all engine state")
    void resetEngine() {
        engine.processFrame(state, entities);
        engine.processFrame(state, entities);
        assertEquals(2, engine.getLockstepFrame());

        engine.reset();
        assertFalse(engine.isRunning());
        assertEquals(0, engine.getLockstepFrame());
    }

    // --- Disconnect / Reconnect tests ---

    @Test
    @DisplayName("Engine pauses after DISCONNECT_TIMEOUT_TICKS frames without opponent commands")
    void enginePausesAfterDisconnectTimeout() {
        // Use a large buffer to avoid ring-buffer wrapping issues over many frames
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        // Process frames without any opponent commands
        // Pause triggers when lockstepFrame - lastOpponentCommandTick > DISCONNECT_TIMEOUT_TICKS
        // lastOpponentCommandTick starts at 0, so after DISCONNECT_TIMEOUT_TICKS + 2 calls
        // the lockstepFrame at check time will be DISCONNECT_TIMEOUT_TICKS + 1
        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }

        assertTrue(disconnectEngine.isPaused());
        // Engine should have paused before incrementing the last frame,
        // so lockstepFrame = DISCONNECT_TIMEOUT_TICKS + 1
        assertEquals(LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1, disconnectEngine.getLockstepFrame());
    }

    @Test
    @DisplayName("Engine does not pause before DISCONNECT_TIMEOUT_TICKS frames")
    void engineDoesNotPauseBeforeTimeout() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        // Process exactly DISCONNECT_TIMEOUT_TICKS frames — should NOT pause yet
        for (int i = 0; i < LockstepEngine.DISCONNECT_TIMEOUT_TICKS; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }

        assertFalse(disconnectEngine.isPaused());
    }

    @Test
    @DisplayName("Reconnect resumes a paused engine")
    void reconnectResumesPausedEngine() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        // Drive engine into paused state
        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }
        assertTrue(disconnectEngine.isPaused());

        // Reconnect
        List<byte[]> newSentData = new ArrayList<>();
        disconnectEngine.reconnect(newSentData::add);

        assertFalse(disconnectEngine.isPaused());
        // After reconnect, processFrame should advance again
        long frameBefore = disconnectEngine.getLockstepFrame();
        disconnectEngine.processFrame(freshState, freshEntities);
        assertEquals(frameBefore + 1, disconnectEngine.getLockstepFrame(),
            "After reconnect, frame should advance by 1 from wherever it paused");
    }

    @Test
    @DisplayName("Reconnect does nothing when engine is not running")
    void reconnectWhenNotRunning() {
        engine.stop();
        assertFalse(engine.isRunning());
        engine.reconnect(b -> {}); // should not throw
        assertFalse(engine.isRunning());
    }

    @Test
    @DisplayName("Pause callback is invoked on disconnect timeout")
    void pauseCallbackInvoked() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        AtomicBoolean pauseCalled = new AtomicBoolean(false);
        disconnectEngine.setPauseCallback(() -> pauseCalled.set(true));

        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }

        assertTrue(pauseCalled.get());
    }

    @Test
    @DisplayName("Resume callback is invoked on reconnect when paused")
    void resumeCallbackInvoked() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        AtomicBoolean resumeCalled = new AtomicBoolean(false);
        disconnectEngine.setResumeCallback(() -> resumeCalled.set(true));

        // Drive into paused state
        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }

        assertFalse(resumeCalled.get());

        // Reconnect
        disconnectEngine.reconnect(b -> {});
        assertTrue(resumeCalled.get());
    }

    @Test
    @DisplayName("Resume callback is NOT invoked on reconnect when not paused")
    void resumeCallbackNotInvokedWhenNotPaused() {
        AtomicBoolean resumeCalled = new AtomicBoolean(false);
        engine.setResumeCallback(() -> resumeCalled.set(true));

        engine.reconnect(b -> {});
        assertFalse(resumeCalled.get());
    }

    @Test
    @DisplayName("ProcessFrame does not advance tick when paused")
    void processFrameDoesNotAdvanceWhenPaused() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        // Drive into paused state
        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }
        long frameAtPause = disconnectEngine.getLockstepFrame();

        // Process many more frames while paused — frame should not advance
        for (int i = 0; i < 10; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }
        assertEquals(frameAtPause, disconnectEngine.getLockstepFrame());
    }

    @Test
    @DisplayName("ProcessFrame returns empty list when paused")
    void processFrameReturnsEmptyWhenPaused() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }

        var commands = disconnectEngine.processFrame(freshState, freshEntities);
        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("isPaused returns false initially")
    void isPausedFalseInitially() {
        assertFalse(engine.isPaused());
    }

    @Test
    @DisplayName("Reset clears pause and reconnect state")
    void resetClearsDisconnectState() {
        LockstepEngine disconnectEngine = new LockstepEngine(2, 256, 1000);
        GameState freshState = new GameState();
        EntityManager freshEntities = new EntityManager();
        disconnectEngine.start(b -> {});

        AtomicBoolean pauseCalled = new AtomicBoolean(false);
        AtomicBoolean resumeCalled = new AtomicBoolean(false);
        disconnectEngine.setPauseCallback(() -> pauseCalled.set(true));
        disconnectEngine.setResumeCallback(() -> resumeCalled.set(true));

        // Drive into paused state
        for (int i = 0; i <= LockstepEngine.DISCONNECT_TIMEOUT_TICKS + 1; i++) {
            disconnectEngine.processFrame(freshState, freshEntities);
        }
        assertTrue(disconnectEngine.isPaused());

        disconnectEngine.reset();
        assertFalse(disconnectEngine.isPaused());
        assertFalse(disconnectEngine.isRunning());
        assertEquals(0, disconnectEngine.getLockstepFrame());
    }
}
