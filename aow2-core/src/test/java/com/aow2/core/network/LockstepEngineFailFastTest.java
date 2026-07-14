package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.GridPosition;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for B-8 from FULL_ANALYSIS.md: LockstepEngine inline fallback
 * silently drops 7 command types.
 * <p>
 * BUG (before fix): When {@code economySystem == null} (i.e., {@code setGameSystems()}
 * was never called), the inline fallback in {@code applyCommand} only handled 7
 * command types (Move, Attack, AttackMove, Stop, Hold, SiegeMode, Patrol). The
 * other 7 (Build, Produce, Research, Garrison, Ungarrison, Cancel, Upgrade) hit
 * a {@code default} branch that only logged a warning and silently dropped the
 * command.
 * <p>
 * FIX: The {@code default} branch now throws {@code IllegalStateException} so
 * misconfiguration is detected immediately (fail-fast) rather than silently
 * losing commands.
 * <p>
 * Note: These tests use {@code submitImmediate} to bypass the input-delay buffer
 * so the command reaches {@code applyCommand} on the very next {@code processFrame}
 * call.
 */
@DisplayName("B-8: LockstepEngine fail-fast on missing game systems")
class LockstepEngineFailFastTest {

    @Test
    @DisplayName("Build command throws IllegalStateException when game systems not injected")
    void buildCommandThrowsWhenNoGameSystems() {
        LockstepEngine engine = new LockstepEngine(2, 8, 5);
        GameState state = new GameState();
        EntityManager entities = new EntityManager();
        List<byte[]> sentData = new ArrayList<>();
        engine.start(sentData::add);

        var cmd = new CommandType.Build(0, 0,
            com.aow2.common.model.BuildingType.CONFED_GENERATOR,
            new GridPosition(5, 5));
        engine.submitImmediate(cmd);

        assertThrows(IllegalStateException.class,
            () -> engine.processFrame(state, entities),
            "B-8: Build command should throw IllegalStateException when game systems not injected");
    }

    @Test
    @DisplayName("Produce command throws IllegalStateException when game systems not injected")
    void produceCommandThrowsWhenNoGameSystems() {
        LockstepEngine engine = new LockstepEngine(2, 8, 5);
        GameState state = new GameState();
        EntityManager entities = new EntityManager();
        List<byte[]> sentData = new ArrayList<>();
        engine.start(sentData::add);

        var cmd = new CommandType.Produce(0, 0, 1,
            com.aow2.common.model.UnitType.CONFED_INFANTRY);
        engine.submitImmediate(cmd);

        assertThrows(IllegalStateException.class,
            () -> engine.processFrame(state, entities),
            "B-8: Produce command should throw IllegalStateException when game systems not injected");
    }

    @Test
    @DisplayName("Research command throws IllegalStateException when game systems not injected")
    void researchCommandThrowsWhenNoGameSystems() {
        LockstepEngine engine = new LockstepEngine(2, 8, 5);
        GameState state = new GameState();
        EntityManager entities = new EntityManager();
        List<byte[]> sentData = new ArrayList<>();
        engine.start(sentData::add);

        var cmd = new CommandType.Research(0, 0, 1, 0);
        engine.submitImmediate(cmd);

        assertThrows(IllegalStateException.class,
            () -> engine.processFrame(state, entities),
            "B-8: Research command should throw IllegalStateException when game systems not injected");
    }

    @Test
    @DisplayName("Ungarrison command throws IllegalStateException when game systems not injected")
    void ungarrisonCommandThrowsWhenNoGameSystems() {
        LockstepEngine engine = new LockstepEngine(2, 8, 5);
        GameState state = new GameState();
        EntityManager entities = new EntityManager();
        List<byte[]> sentData = new ArrayList<>();
        engine.start(sentData::add);

        var cmd = new CommandType.Ungarrison(0, 0, 1);
        engine.submitImmediate(cmd);

        assertThrows(IllegalStateException.class,
            () -> engine.processFrame(state, entities),
            "B-8: Ungarrison command should throw IllegalStateException when game systems not injected");
    }

    @Test
    @DisplayName("Move command does NOT throw (handled by inline fallback) when game systems not injected")
    void moveCommandDoesNotThrowWithoutGameSystems() {
        LockstepEngine engine = new LockstepEngine(2, 8, 5);
        GameState state = new GameState();
        EntityManager entities = new EntityManager();
        List<byte[]> sentData = new ArrayList<>();
        engine.start(sentData::add);

        // Move IS handled by the inline fallback — should not throw.
        // Use submitImmediate so it reaches applyCommand on the next processFrame.
        var cmd = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(5, 5));
        engine.submitImmediate(cmd);

        assertDoesNotThrow(() -> engine.processFrame(state, entities),
            "B-8: Move command should NOT throw — it's handled by the inline fallback");
    }
}
