package com.aow2.client.model;

import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TerrainType enum: IDs, passability, movement costs, fromId lookup.
 */
class TerrainTypeTest {

    @Nested
    @DisplayName("Terrain IDs")
    class TerrainIds {

        @Test
        @DisplayName("DEEP_WATER has ID 0")
        void deepWaterId() {
            assertEquals(0, TerrainType.DEEP_WATER.id());
        }

        @Test
        @DisplayName("SHALLOW_WATER has ID 1")
        void shallowWaterId() {
            assertEquals(1, TerrainType.SHALLOW_WATER.id());
        }

        @Test
        @DisplayName("GRASS has ID 3")
        void grassId() {
            assertEquals(3, TerrainType.GRASS.id());
        }

        @Test
        @DisplayName("MOUNTAIN has ID 6")
        void mountainId() {
            assertEquals(6, TerrainType.MOUNTAIN.id());
        }

        @Test
        @DisplayName("ROAD has ID 7")
        void roadId() {
            assertEquals(7, TerrainType.ROAD.id());
        }

        @Test
        @DisplayName("all IDs are non-negative")
        void allIdsNonNegative() {
            for (TerrainType t : TerrainType.values()) {
                assertTrue(t.id() >= 0,
                    () -> t.name() + " has negative ID: " + t.id());
            }
        }
    }

    @Nested
    @DisplayName("fromId Lookup")
    class FromIdLookup {

        @Test
        @DisplayName("fromId returns correct type for known IDs")
        void fromIdCorrect() {
            assertEquals(TerrainType.DEEP_WATER, TerrainType.fromId(0));
            assertEquals(TerrainType.SHALLOW_WATER, TerrainType.fromId(1));
            assertEquals(TerrainType.SAND, TerrainType.fromId(2));
            assertEquals(TerrainType.GRASS, TerrainType.fromId(3));
            assertEquals(TerrainType.FOREST, TerrainType.fromId(4));
            assertEquals(TerrainType.HILLS, TerrainType.fromId(5));
            assertEquals(TerrainType.MOUNTAIN, TerrainType.fromId(6));
            assertEquals(TerrainType.ROAD, TerrainType.fromId(7));
            assertEquals(TerrainType.BRIDGE, TerrainType.fromId(8));
            assertEquals(TerrainType.SWAMP, TerrainType.fromId(9));
            assertEquals(TerrainType.SNOW, TerrainType.fromId(10));
            assertEquals(TerrainType.RESOURCE_DEPOSIT, TerrainType.fromId(25));
        }

        @Test
        @DisplayName("fromId returns null for unknown ID")
        void fromIdUnknownReturnsNull() {
            assertNull(TerrainType.fromId(99));
        }

        @Test
        @DisplayName("fromId returns null for negative ID")
        void fromIdNegativeReturnsNull() {
            assertNull(TerrainType.fromId(-1));
        }
    }

    @Nested
    @DisplayName("Default Passability")
    class DefaultPassability {

        @Test
        @DisplayName("DEEP_WATER is not passable")
        void deepWaterNotPassable() {
            assertFalse(TerrainType.DEEP_WATER.isPassable());
        }

        @Test
        @DisplayName("SHALLOW_WATER is not passable by default")
        void shallowWaterNotPassableByDefault() {
            assertFalse(TerrainType.SHALLOW_WATER.isPassable());
        }

        @Test
        @DisplayName("MOUNTAIN is not passable")
        void mountainNotPassable() {
            assertFalse(TerrainType.MOUNTAIN.isPassable());
        }

        @Test
        @DisplayName("GRASS is passable")
        void grassPassable() {
            assertTrue(TerrainType.GRASS.isPassable());
        }

        @Test
        @DisplayName("ROAD is passable")
        void roadPassable() {
            assertTrue(TerrainType.ROAD.isPassable());
        }

        @Test
        @DisplayName("FOREST is passable")
        void forestPassable() {
            assertTrue(TerrainType.FOREST.isPassable());
        }
    }

    @Nested
    @DisplayName("Passability by Category")
    class PassabilityByCategory {

        @Test
        @DisplayName("DEEP_WATER is impassable for all categories")
        void deepWaterImpassableAll() {
            for (UnitCategory cat : UnitCategory.values()) {
                assertFalse(TerrainType.DEEP_WATER.isPassableBy(cat));
            }
        }

        @Test
        @DisplayName("SHALLOW_WATER is passable only for infantry")
        void shallowWaterOnlyInfantry() {
            assertTrue(TerrainType.SHALLOW_WATER.isPassableBy(UnitCategory.INFANTRY));
            assertFalse(TerrainType.SHALLOW_WATER.isPassableBy(UnitCategory.VEHICLE));
            assertFalse(TerrainType.SHALLOW_WATER.isPassableBy(UnitCategory.SPECIAL_MACHINERY));
            assertFalse(TerrainType.SHALLOW_WATER.isPassableBy(UnitCategory.MINE));
        }

        @Test
        @DisplayName("MOUNTAIN is impassable for all categories")
        void mountainImpassableAll() {
            for (UnitCategory cat : UnitCategory.values()) {
                assertFalse(TerrainType.MOUNTAIN.isPassableBy(cat));
            }
        }

        @Test
        @DisplayName("SWAMP is impassable for vehicles and special machinery")
        void swampImpassableForVehicles() {
            assertFalse(TerrainType.SWAMP.isPassableBy(UnitCategory.VEHICLE));
            assertFalse(TerrainType.SWAMP.isPassableBy(UnitCategory.SPECIAL_MACHINERY));
            assertTrue(TerrainType.SWAMP.isPassableBy(UnitCategory.INFANTRY));
        }

        @Test
        @DisplayName("GRASS is passable for all categories")
        void grassPassableAll() {
            for (UnitCategory cat : UnitCategory.values()) {
                assertTrue(TerrainType.GRASS.isPassableBy(cat),
                    () -> "GRASS should be passable for " + cat);
            }
        }
    }

    @Nested
    @DisplayName("Movement Costs")
    class MovementCosts {

        @Test
        @DisplayName("ROAD has zero movement cost")
        void roadZeroCost() {
            assertEquals(0, TerrainType.ROAD.getMovementCost());
        }

        @Test
        @DisplayName("GRASS has cost 1")
        void grassCost1() {
            assertEquals(1, TerrainType.GRASS.getMovementCost());
        }

        @Test
        @DisplayName("DEEP_WATER has max movement cost")
        void deepWaterMaxCost() {
            assertEquals(Integer.MAX_VALUE, TerrainType.DEEP_WATER.getMovementCost());
        }

        @Test
        @DisplayName("MOUNTAIN has max movement cost")
        void mountainMaxCost() {
            assertEquals(Integer.MAX_VALUE, TerrainType.MOUNTAIN.getMovementCost());
        }

        @Test
        @DisplayName("SWAMP has highest non-max cost")
        void swampHighestNonMaxCost() {
            int swampCost = TerrainType.SWAMP.getMovementCost();
            assertTrue(swampCost > TerrainType.HILLS.getMovementCost());
            assertTrue(swampCost > TerrainType.FOREST.getMovementCost());
            assertEquals(4, swampCost);
        }

        @Test
        @DisplayName("all passable terrains have finite cost")
        void passableTerrainsHaveFiniteCost() {
            for (TerrainType t : TerrainType.values()) {
                if (t.isPassable()) {
                    assertTrue(t.getMovementCost() < Integer.MAX_VALUE,
                        () -> t.name() + " is passable but has max cost");
                }
            }
        }
    }

    @Nested
    @DisplayName("Enum Completeness")
    class EnumCompleteness {

        @Test
        @DisplayName("there are exactly 12 terrain types")
        void exactlyTwelveTypes() {
            assertEquals(12, TerrainType.values().length);
        }

        @Test
        @DisplayName("all IDs are unique")
        void allIdsUnique() {
            Set<Integer> ids = java.util.Arrays.stream(TerrainType.values())
                .map(TerrainType::id)
                .collect(Collectors.toSet());
            assertEquals(TerrainType.values().length, ids.size(),
                "Terrain type IDs should be unique");
        }
    }
}
