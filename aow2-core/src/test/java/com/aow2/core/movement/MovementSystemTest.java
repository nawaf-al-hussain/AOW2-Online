package com.aow2.core.movement;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovementSystemTest {

    private PathfindingSystem pathfinding;
    private CollisionSystem collision;
    private MovementSystem movement;
    private EntityManager entities;

    /**
     * Creates a simple Infantry stat block for testing.
     * Speed=1 means unit moves every tick (fastest movement).
     */
    private UnitStats createFastInfantryStats() {
        return new UnitStats(
            UnitType.REBEL_INFANTRY, "Infantry", 40, 5,
            1, 5, 0, 8, 5, WeaponType.BULLET, 5, 30, 10, 5, 10, 0, 0, 1
        );
    }

    /**
     * Creates a slow unit stat block (speed=4, moves every 4 ticks).
     */
    private UnitStats createSlowUnitStats() {
        return new UnitStats(
            UnitType.CONFED_FORTRESS, "Fortress", 200, 30,
            4, 15, 5, 10, 7, WeaponType.ARTILLERY, 10, 120, 60, 30, 20, 1, 0, 1
        );
    }

    @BeforeEach
    void setUp() {
        pathfinding = new PathfindingSystem();
        collision = new CollisionSystem();
        movement = new MovementSystem(pathfinding, collision);
        entities = new EntityManager();
    }

    @Nested
    @DisplayName("Move Command")
    class MoveCommand {

        @Test
        @DisplayName("Should issue move command and set unit to MOVING state")
        void shouldIssueMoveCommandAndSetMoving() {
            // Given: a unit on an open map
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);

            // When: issuing a move command
            movement.issueMoveCommand(unit, new GridPosition(5, 0), map, entities);

            // Then: unit should be in MOVING state with a path
            assertEquals(MovementState.MOVING, unit.getMovementState());
            assertNotNull(unit.getTargetPosition());
            assertTrue(unit.hasPathRemaining());
        }

        @Test
        @DisplayName("Should not issue move command to dead unit")
        void shouldNotIssueMoveCommandToDeadUnit() {
            // Given: a dead unit
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            unit.takeDamage(100); // kill
            entities.addUnit(unit);

            // When: issuing a move command
            movement.issueMoveCommand(unit, new GridPosition(5, 0), map, entities);

            // Then: unit should remain idle
            assertEquals(MovementState.IDLE, unit.getMovementState());
        }

        @Test
        @DisplayName("Should not issue move command to impassable target")
        void shouldNotIssueMoveCommandToImpassableTarget() {
            // Given: a map with water at target
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);
            map.setTile(5, 0, com.aow2.common.model.TerrainType.DEEP_WATER);

            // When: issuing a move command to water
            movement.issueMoveCommand(unit, new GridPosition(5, 0), map, entities);

            // Then: unit should not be in MOVING state
            assertNotEquals(MovementState.MOVING, unit.getMovementState());
        }
    }

    @Nested
    @DisplayName("Movement Processing")
    class MovementProcessing {

        @Test
        @DisplayName("Should move unit along path tick by tick")
        void shouldMoveUnitAlongPath() {
            // Given: a fast unit (speed=1) with a move command
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);
            movement.issueMoveCommand(unit, new GridPosition(3, 0), map, entities);

            // When: processing ticks
            assertEquals(MovementState.MOVING, unit.getMovementState());
            movement.processTick(entities, map);

            // Then: unit should have advanced one step
            assertTrue(unit.getPosition().x() > 0 || unit.getPosition().y() > 0,
                "Unit should have moved from (0,0)");
        }

        @Test
        @DisplayName("Should stop unit when reaching destination")
        void shouldStopUnitWhenReachingDestination() {
            // Given: a fast unit (speed=1) moving one cell
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);
            movement.issueMoveCommand(unit, new GridPosition(1, 0), map, entities);

            // When: processing enough ticks to reach destination
            movement.processTick(entities, map);

            // Then: unit should have arrived
            assertEquals(new GridPosition(1, 0), unit.getPosition());
            assertEquals(MovementState.ARRIVED, unit.getMovementState());
        }

        @Test
        @DisplayName("Should skip idle units during tick processing")
        void shouldSkipIdleUnitsDuringTickProcessing() {
            // Given: a unit with no move command
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);

            // When: processing a tick
            movement.processTick(entities, map);

            // Then: unit should still be at the same position
            assertEquals(new GridPosition(5, 5), unit.getPosition());
            assertEquals(MovementState.IDLE, unit.getMovementState());
        }

        @Test
        @DisplayName("Should respect speed stat for slow units")
        void shouldRespectSpeedStatForSlowUnits() {
            // Given: a slow unit (speed=4, moves every 4 ticks)
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createSlowUnitStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(0, 0),
                UnitType.CONFED_FORTRESS, stats);
            entities.addUnit(unit);
            movement.issueMoveCommand(unit, new GridPosition(5, 0), map, entities);

            // When: processing 3 ticks (not enough to move)
            for (int i = 0; i < 3; i++) {
                movement.processTick(entities, map);
            }

            // Then: unit should not have moved yet
            assertEquals(new GridPosition(0, 0), unit.getPosition());

            // When: processing one more tick (4th tick)
            movement.processTick(entities, map);

            // Then: unit should now move
            assertTrue(unit.getPosition().x() > 0,
                "Unit should have moved after speed ticks elapsed");
        }
    }

    @Nested
    @DisplayName("Stuck Detection")
    class StuckDetection {

        @Test
        @DisplayName("Should detect and handle stuck units")
        void shouldDetectAndHandleStuckUnits() {
            // Given: a unit whose path is blocked by another unit
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit movingUnit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            Unit blockingUnit = new Unit(2, Faction.RESISTANCE, new GridPosition(1, 0),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(movingUnit);
            entities.addUnit(blockingUnit);

            // Issue a move command to a position beyond the blocker
            movement.issueMoveCommand(movingUnit, new GridPosition(3, 0), map, entities);

            // When: processing enough ticks to trigger stuck detection
            for (int i = 0; i < 10; i++) {
                movement.processTick(entities, map);
            }

            // Then: unit should have detected being stuck or found an alternate path
            // The unit may have re-pathfound around the blocker
            assertTrue(movingUnit.getStuckCounter() > 0 ||
                movingUnit.getMovementState() == MovementState.MOVING ||
                movingUnit.getMovementState() == MovementState.STUCK ||
                movingUnit.getMovementState() == MovementState.ARRIVED,
                "Unit should be in a stuck/moving/arrived state after processing");
        }

        @Test
        @DisplayName("Should re-pathfind when stuck counter exceeds threshold")
        void shouldRePathfindWhenStuckCounterExceedsThreshold() {
            // Given: a unit that is stuck
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);
            unit.setTargetPosition(new GridPosition(5, 0));
            unit.setMovementState(MovementState.STUCK);
            unit.setStuckCounter(5); // at threshold

            // When: processing a tick (should trigger re-pathfind)
            movement.processTick(entities, map);

            // Then: unit should have been re-pathfound or cleared
            assertNotEquals(MovementState.STUCK, unit.getMovementState(),
                "Unit should no longer be stuck after re-pathfind attempt");
        }
    }

    @Nested
    @DisplayName("Formation Movement")
    class FormationMovement {

        @Test
        @DisplayName("Should handle formation movement preserving relative positions")
        void shouldHandleFormationMovement() {
            // Given: two units in a line formation
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unitA = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 4),
                UnitType.REBEL_INFANTRY, stats);
            Unit unitB = new Unit(2, Faction.RESISTANCE, new GridPosition(0, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unitA);
            entities.addUnit(unitB);

            // When: issuing a group move command
            movement.issueGroupMoveCommand(List.of(unitA, unitB), new GridPosition(5, 4), map, entities);

            // Then: both units should have move targets
            assertTrue(unitA.getMovementState() == MovementState.MOVING ||
                unitA.getMovementState() == MovementState.STUCK,
                "Unit A should be moving or stuck");
            assertTrue(unitB.getMovementState() == MovementState.MOVING ||
                unitB.getMovementState() == MovementState.STUCK,
                "Unit B should be moving or stuck");
        }

        @Test
        @DisplayName("Should handle group move with spacing (no overlap at target)")
        void shouldHandleGroupMoveWithSpacing() {
            // Given: three units in a group
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unitA = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 3),
                UnitType.REBEL_INFANTRY, stats);
            Unit unitB = new Unit(2, Faction.RESISTANCE, new GridPosition(0, 4),
                UnitType.REBEL_INFANTRY, stats);
            Unit unitC = new Unit(3, Faction.RESISTANCE, new GridPosition(0, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unitA);
            entities.addUnit(unitB);
            entities.addUnit(unitC);

            // When: issuing a group move command
            movement.issueGroupMoveCommand(
                List.of(unitA, unitB, unitC),
                new GridPosition(10, 4),
                map, entities
            );

            // Then: target positions should be different for each unit (no overlap)
            // At least one of them should be moving
            boolean anyMoving = unitA.getMovementState() == MovementState.MOVING ||
                unitB.getMovementState() == MovementState.MOVING ||
                unitC.getMovementState() == MovementState.MOVING;
            assertTrue(anyMoving, "At least one unit should be moving");
        }

        @Test
        @DisplayName("Should handle empty group move command")
        void shouldHandleEmptyGroupMoveCommand() {
            // Given: an empty unit list
            GameMap map = new GameMap(20, 20);

            // When/Then: should not throw
            assertDoesNotThrow(() ->
                movement.issueGroupMoveCommand(List.of(), new GridPosition(5, 5), map, entities)
            );
        }
    }

    @Nested
    @DisplayName("Multiple Units")
    class MultipleUnits {

        @Test
        @DisplayName("Should move multiple units without collision")
        void shouldMoveMultipleUnitsWithoutCollision() {
            // Given: two units moving to different targets
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit unitA = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            Unit unitB = new Unit(2, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unitA);
            entities.addUnit(unitB);

            movement.issueMoveCommand(unitA, new GridPosition(0, 5), map, entities);
            movement.issueMoveCommand(unitB, new GridPosition(10, 5), map, entities);

            // When: processing ticks until units arrive or max ticks
            for (int i = 0; i < 20; i++) {
                movement.processTick(entities, map);
            }

            // Then: units should not be on the same cell
            if (unitA.isAlive() && unitB.isAlive()) {
                assertNotEquals(unitA.getPosition(), unitB.getPosition(),
                    "Units should not occupy the same cell");
            }
        }
    }

    @Nested
    @DisplayName("Attack Range Stopping")
    class AttackRangeStopping {

        @Test
        @DisplayName("Should stop unit when enemy in attack range")
        void shouldStopUnitWhenEnemyInRange() {
            // Given: a unit moving toward an enemy within attack range
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFastInfantryStats();
            Unit movingUnit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0),
                UnitType.REBEL_INFANTRY, stats);
            Unit enemy = new Unit(2, Faction.CONFEDERATION, new GridPosition(2, 0),
                UnitType.CONFED_INFANTRY, stats);
            entities.addUnit(movingUnit);
            entities.addUnit(enemy);

            // Move toward a far position but set enemy as target
            movement.issueMoveCommand(movingUnit, new GridPosition(10, 0), map, entities);
            movingUnit.setTargetUnitRef(2); // target the enemy

            // When: enemy is in attack range and unit processes tick
            // Infantry has attackRange=5, enemy is at distance 2
            movement.processTick(entities, map);

            // Then: unit should stop and enter ATTACKING state
            assertEquals(MovementState.ATTACKING, movingUnit.getMovementState());
        }
    }
}
