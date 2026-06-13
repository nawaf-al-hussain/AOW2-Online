package com.aow2.core.network;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SyncChecker desync detection component.
 * Verifies state hash computation and desync detection.
 * REF: multiplayer_architecture.md - Data integrity: turn sequence validation
 */
class SyncCheckerTest {

    private SyncChecker checker;
    private GameState state;
    private EntityManager entities;

    @BeforeEach
    void setUp() {
        checker = new SyncChecker(10);
        state = new GameState();
        entities = new EntityManager();
    }

    @Test
    @DisplayName("Should check at correct intervals")
    void shouldCheckIntervals() {
        assertTrue(checker.shouldCheck(0));
        assertFalse(checker.shouldCheck(1));
        assertFalse(checker.shouldCheck(9));
        assertTrue(checker.shouldCheck(10));
        assertTrue(checker.shouldCheck(20));
    }

    @Test
    @DisplayName("State hash includes tick count")
    void hashIncludesTick() {
        long hash1 = checker.computeStateHash(state, entities);
        state.advanceTick();
        long hash2 = checker.computeStateHash(state, entities);
        assertFalse(hash1 == hash2, "Hash should change after tick advance");
    }

    @Test
    @DisplayName("State hash includes unit data")
    void hashIncludesUnits() {
        long hash1 = checker.computeStateHash(state, entities);

        var stats = new UnitStats(UnitType.CONFED_SOLDIER, "Test", 100, 10, 50,
                5, 3, 1, 8, 1, 20, 50, 5, 0, 0, 0, 0);
        var unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                UnitType.CONFED_SOLDIER, stats);
        entities.addUnit(unit);

        long hash2 = checker.computeStateHash(state, entities);
        assertFalse(hash1 == hash2, "Hash should change after adding a unit");
    }

    @Test
    @DisplayName("Matching hashes do not trigger desync")
    void matchingHashesNoDesync() {
        checker.setLocalHash(12345L);
        boolean desync = checker.setRemoteHash(12345L);
        assertFalse(desync);
        assertEquals(0, checker.getDesyncCount());
    }

    @Test
    @DisplayName("Mismatched hashes trigger desync")
    void mismatchedHashesDesync() {
        checker.setLocalHash(12345L);
        boolean desync = checker.setRemoteHash(67890L);
        assertTrue(desync);
        assertEquals(1, checker.getDesyncCount());
    }

    @Test
    @DisplayName("Multiple desyncs increment counter")
    void multipleDesyncs() {
        checker.setLocalHash(1L);
        checker.setRemoteHash(2L);
        checker.setLocalHash(3L);
        checker.setRemoteHash(4L);
        assertEquals(2, checker.getDesyncCount());
    }

    @Test
    @DisplayName("Check count tracks setLocalHash calls")
    void checkCount() {
        assertEquals(0, checker.getCheckCount());
        checker.setLocalHash(1L);
        assertEquals(1, checker.getCheckCount());
        checker.setLocalHash(2L);
        assertEquals(2, checker.getCheckCount());
    }

    @Test
    @DisplayName("Reset clears all tracking state")
    void resetClearsState() {
        checker.setLocalHash(1L);
        checker.setRemoteHash(2L);
        assertEquals(1, checker.getDesyncCount());

        checker.reset();
        assertEquals(0, checker.getDesyncCount());
        assertEquals(0, checker.getCheckCount());
        assertEquals(0L, checker.getLocalHash());
        assertEquals(0L, checker.getRemoteHash());
    }

    @Test
    @DisplayName("Invalid sync interval throws")
    void invalidInterval() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new SyncChecker(0));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new SyncChecker(-1));
    }

    @Test
    @DisplayName("Deterministic: same state produces same hash")
    void deterministicHash() {
        var stats = new UnitStats(UnitType.CONFED_SOLDIER, "Test", 100, 10, 50,
                5, 3, 1, 8, 1, 20, 50, 5, 0, 0, 0, 0);
        entities.addUnit(new Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                UnitType.CONFED_SOLDIER, stats));

        long hash1 = checker.computeStateHash(state, entities);
        long hash2 = checker.computeStateHash(state, entities);
        assertEquals(hash1, hash2, "Same state must produce same hash");
    }
}
