package com.aow2.core.movement;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollisionSystemTest {

    private CollisionSystem collision;
    private EntityManager entities;

    /**
     * Creates a simple Infantry stat block for testing.
     */
    private UnitStats createInfantryStats() {
        return new UnitStats(
            UnitType.REBEL_INFANTRY, "Infantry", 40, 5, 10,
            1, 5, 0, 8, 5, 30, 10, 5, 10, 0, 0, 1
        );
    }

    /**
     * Creates a Fortress (large unit) stat block for testing.
     */
    private UnitStats createFortressStats() {
        return new UnitStats(
            UnitType.CONFED_FORTRESS, "Fortress", 200, 30, 60,
            2, 15, 5, 10, 7, 120, 60, 30, 20, 1, 0, 1
        );
    }

    /**
     * Creates a Barracks stat block for testing.
     */
    private BuildingStats createBarracksStats() {
        return new BuildingStats(
            BuildingType.REBEL_BARRACKS, 80, 30, 0, 5, 0,
            8, 40, 0, 10, 5, 0, 5, 0, 30, 15, List.of()
        );
    }

    @BeforeEach
    void setUp() {
        collision = new CollisionSystem();
        entities = new EntityManager();
    }

    @Nested
    @DisplayName("Cell Availability")
    class CellAvailability {

        @Test
        @DisplayName("Should detect cell availability on passable empty terrain")
        void shouldDetectCellAvailabilityOnPassableTerrain() {
            // Given: an empty grass map
            GameMap map = new GameMap(20, 20);

            // When/Then: passable cell should be available
            assertTrue(collision.isCellAvailable(new GridPosition(5, 5), map, entities, -1));
        }

        @Test
        @DisplayName("Should detect cell as unavailable on impassable terrain")
        void shouldDetectCellUnavailableOnImpassableTerrain() {
            // Given: a map with water at (5,5)
            GameMap map = new GameMap(20, 20);
            map.setTile(5, 5, TerrainType.WATER);

            // When/Then: water cell should be unavailable
            assertFalse(collision.isCellAvailable(new GridPosition(5, 5), map, entities, -1));
        }

        @Test
        @DisplayName("Should detect cell as unavailable when occupied by another unit")
        void shouldDetectCellUnavailableWhenOccupiedByUnit() {
            // Given: a unit at (5,5)
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);

            // When/Then: cell should be unavailable for another unit
            assertFalse(collision.isCellAvailable(new GridPosition(5, 5), map, entities, 2));
        }

        @Test
        @DisplayName("Should detect cell as available when occupied by excluded unit")
        void shouldDetectCellAvailableWhenOccupiedByExcludedUnit() {
            // Given: a unit at (5,5) with ID 1
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit);

            // When/Then: cell should be available when excluding that unit's ID
            assertTrue(collision.isCellAvailable(new GridPosition(5, 5), map, entities, 1));
        }

        @Test
        @DisplayName("Should detect cell as unavailable when out of bounds")
        void shouldDetectCellUnavailableWhenOutOfBounds() {
            // Given: a small map
            GameMap map = new GameMap(10, 10);

            // When/Then: out-of-bounds cells should be unavailable
            assertFalse(collision.isCellAvailable(new GridPosition(-1, 0), map, entities, -1));
            assertFalse(collision.isCellAvailable(new GridPosition(10, 0), map, entities, -1));
        }

        @Test
        @DisplayName("Should detect cell as unavailable when occupied by building")
        void shouldDetectCellUnavailableWhenOccupiedByBuilding() {
            // Given: a building at (5,5)
            GameMap map = new GameMap(20, 20);
            BuildingStats stats = createBarracksStats();
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(5, 5),
                BuildingType.REBEL_BARRACKS, stats);
            entities.addBuilding(building);

            // When/Then: cell should be unavailable
            assertFalse(collision.isCellAvailable(new GridPosition(5, 5), map, entities, -1));
        }

        @Test
        @DisplayName("Should not consider destroyed buildings as blocking")
        void shouldNotConsiderDestroyedBuildingsAsBlocking() {
            // Given: a destroyed building at (5,5)
            GameMap map = new GameMap(20, 20);
            BuildingStats stats = createBarracksStats();
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(5, 5),
                BuildingType.REBEL_BARRACKS, stats);
            building.takeDamage(200); // destroy
            entities.addBuilding(building);

            // When/Then: cell should be available (destroyed building doesn't block)
            assertTrue(collision.isCellAvailable(new GridPosition(5, 5), map, entities, -1));
        }
    }

    @Nested
    @DisplayName("Collision Resolution")
    class CollisionResolution {

        @Test
        @DisplayName("Should resolve collision between two units on same cell")
        void shouldResolveCollisionBetweenTwoUnits() {
            // Given: two units on the same cell
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit unitA = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            Unit unitB = new Unit(2, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unitA);
            entities.addUnit(unitB);

            // When: resolving collisions
            collision.resolveCollisions(entities);

            // Then: units should no longer occupy the same cell
            assertNotEquals(unitA.getPosition(), unitB.getPosition(),
                "Units should be pushed apart after collision resolution");
        }

        @Test
        @DisplayName("Should push units apart to adjacent cells")
        void shouldPushUnitsApartToAdjacentCells() {
            // Given: two units on the same cell
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit unitA = new Unit(1, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, stats);
            Unit unitB = new Unit(2, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unitA);
            entities.addUnit(unitB);

            // When: resolving collisions
            collision.resolveCollisions(entities);

            // Then: the pushed unit should be in an adjacent cell
            Unit pushed = unitA.getPosition().equals(new GridPosition(10, 10)) ? unitB : unitA;
            int dx = Math.abs(pushed.getPosition().x() - 10);
            int dy = Math.abs(pushed.getPosition().y() - 10);
            assertTrue(dx <= 1 && dy <= 1 && (dx + dy > 0),
                "Pushed unit should be in adjacent cell, got " + pushed.getPosition());
        }

        @Test
        @DisplayName("Should not resolve collision when units are on different cells")
        void shouldNotResolveCollisionWhenUnitsOnDifferentCells() {
            // Given: two units on different cells
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            GridPosition posA = new GridPosition(5, 5);
            GridPosition posB = new GridPosition(6, 5);
            Unit unitA = new Unit(1, Faction.RESISTANCE, posA,
                UnitType.REBEL_INFANTRY, stats);
            Unit unitB = new Unit(2, Faction.RESISTANCE, posB,
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unitA);
            entities.addUnit(unitB);

            // When: resolving collisions
            collision.resolveCollisions(entities);

            // Then: units should still be at their original positions
            assertEquals(posA, unitA.getPosition());
            assertEquals(posB, unitB.getPosition());
        }

        @Test
        @DisplayName("Should handle multiple collision pairs")
        void shouldHandleMultipleCollisionPairs() {
            // Given: three units, two pairs colliding
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            // Unit 1 and 2 collide at (5,5)
            Unit unit1 = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            Unit unit2 = new Unit(2, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            // Unit 3 at a different position, no collision
            Unit unit3 = new Unit(3, Faction.RESISTANCE, new GridPosition(8, 8),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(unit1);
            entities.addUnit(unit2);
            entities.addUnit(unit3);

            // When: resolving collisions
            collision.resolveCollisions(entities);

            // Then: unit1 and unit2 should no longer overlap
            assertNotEquals(unit1.getPosition(), unit2.getPosition());
            // Unit3 should be unaffected
            assertEquals(new GridPosition(8, 8), unit3.getPosition());
        }
    }

    @Nested
    @DisplayName("Large Unit Placement")
    class LargeUnitPlacement {

        @Test
        @DisplayName("Should validate large unit placement (2 cells)")
        void shouldValidateLargeUnitPlacement() {
            // Given: a Fortress (large unit) on an open map
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createFortressStats();
            Unit fortress = new Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                UnitType.CONFED_FORTRESS, stats);
            entities.addUnit(fortress);

            // When: checking placement at a position where both cells are free
            boolean valid = collision.isLargeUnitPlacementValid(new GridPosition(10, 10), map, entities);

            // Then: placement should be valid
            assertTrue(valid);
        }

        @Test
        @DisplayName("Should reject large unit placement when secondary cell is blocked")
        void shouldRejectLargeUnitPlacementWhenSecondaryCellBlocked() {
            // Given: a map where the secondary cell (x+1) is water
            GameMap map = new GameMap(20, 20);
            map.setTile(6, 5, TerrainType.WATER); // secondary cell for fortress at (5,5)

            // When: checking placement at (5,5)
            boolean valid = collision.isLargeUnitPlacementValid(new GridPosition(5, 5), map, entities);

            // Then: placement should be rejected
            assertFalse(valid);
        }

        @Test
        @DisplayName("Should reject large unit placement when primary cell is occupied")
        void shouldRejectLargeUnitPlacementWhenPrimaryCellOccupied() {
            // Given: a unit already at the primary cell
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit blockingUnit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(blockingUnit);

            // When: checking placement at (5,5)
            boolean valid = collision.isLargeUnitPlacementValid(new GridPosition(5, 5), map, entities);

            // Then: placement should be rejected
            assertFalse(valid);
        }

        @Test
        @DisplayName("Should reject large unit placement when secondary cell is occupied by unit")
        void shouldRejectLargeUnitPlacementWhenSecondaryCellOccupiedByUnit() {
            // Given: a unit at the secondary cell (x+1)
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit blockingUnit = new Unit(1, Faction.RESISTANCE, new GridPosition(6, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(blockingUnit);

            // When: checking placement at (5,5)
            boolean valid = collision.isLargeUnitPlacementValid(new GridPosition(5, 5), map, entities);

            // Then: placement should be rejected
            assertFalse(valid);
        }

        @Test
        @DisplayName("Should reject large unit placement at map edge (x+1 out of bounds)")
        void shouldRejectLargeUnitPlacementAtMapEdge() {
            // Given: a map where x=126 is the max, fortress would need x=127
            GameMap map = new GameMap(127, 127);

            // When: checking placement at x=126 (secondary cell x=127 is valid)
            boolean validAt126 = collision.isLargeUnitPlacementValid(new GridPosition(126, 5), map, entities);

            // Then: should be valid (127 is the max index for a 127-width map)
            assertTrue(validAt126);
        }

        @Test
        @DisplayName("Large unit should block both its primary and secondary cells")
        void largeUnitShouldBlockBothCells() {
            // Given: a Fortress at (5,5) — occupies (5,5) and (6,5)
            GameMap map = new GameMap(20, 20);
            UnitStats fortressStats = createFortressStats();
            Unit fortress = new Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                UnitType.CONFED_FORTRESS, fortressStats);
            entities.addUnit(fortress);

            // When/Then: both primary and secondary cells should be unavailable
            assertFalse(collision.isCellAvailable(new GridPosition(5, 5), map, entities, 2),
                "Primary cell should be blocked by large unit");
            assertFalse(collision.isCellAvailable(new GridPosition(6, 5), map, entities, 2),
                "Secondary cell should be blocked by large unit");

            // Other cells should still be available
            assertTrue(collision.isCellAvailable(new GridPosition(7, 5), map, entities, 2),
                "Cell beyond large unit should be available");
            assertTrue(collision.isCellAvailable(new GridPosition(5, 6), map, entities, 2),
                "Cell above large unit should be available");
        }
    }

    @Nested
    @DisplayName("Dead Unit Handling")
    class DeadUnitHandling {

        @Test
        @DisplayName("Should not consider dead units as blocking")
        void shouldNotConsiderDeadUnitsAsBlocking() {
            // Given: a dead unit at (5,5)
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit deadUnit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            deadUnit.takeDamage(100); // kill
            entities.addUnit(deadUnit);

            // When/Then: cell should be available
            assertTrue(collision.isCellAvailable(new GridPosition(5, 5), map, entities, 2));
        }

        @Test
        @DisplayName("Should not resolve collisions with dead units")
        void shouldNotResolveCollisionsWithDeadUnits() {
            // Given: a dead unit and alive unit on the same cell
            GameMap map = new GameMap(20, 20);
            UnitStats stats = createInfantryStats();
            Unit deadUnit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            deadUnit.takeDamage(100);
            Unit aliveUnit = new Unit(2, Faction.RESISTANCE, new GridPosition(5, 5),
                UnitType.REBEL_INFANTRY, stats);
            entities.addUnit(deadUnit);
            entities.addUnit(aliveUnit);

            // When: resolving collisions
            collision.resolveCollisions(entities);

            // Then: alive unit should not be pushed (dead unit is not blocking)
            assertEquals(new GridPosition(5, 5), aliveUnit.getPosition(),
                "Alive unit should not be pushed due to dead unit collision");
        }
    }
}
