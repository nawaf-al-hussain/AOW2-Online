package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        var stats = new UnitStats(UnitType.CONFED_SOLDIER, "Test", 100, 10, 50,
                5, 3, 1, 8, 1, 20, 50, 5, 0, 0, 0, 0);
        var unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(0, 0),
                UnitType.CONFED_SOLDIER, stats);
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
}
