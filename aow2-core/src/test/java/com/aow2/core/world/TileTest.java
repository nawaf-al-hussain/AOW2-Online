package com.aow2.core.world;

import com.aow2.common.model.TerrainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileTest {

    @Nested
    @DisplayName("Tile Creation")
    class Creation {

        @Test
        @DisplayName("Should create tile with all fields using canonical constructor")
        void shouldCreateTileWithAllFields() {
            Tile tile = new Tile(TerrainType.GRASS, 5, 10, 3, true, true);
            assertEquals(TerrainType.GRASS, tile.terrain());
            assertEquals(5, tile.x());
            assertEquals(10, tile.y());
            assertEquals(3, tile.occupyingEntityId());
            assertTrue(tile.explored());
            assertTrue(tile.visible());
        }

        @Test
        @DisplayName("Should create simple tile with default state using factory method")
        void shouldCreateSimpleTileWithFactory() {
            Tile tile = Tile.of(TerrainType.DEEP_WATER, 3, 7);
            assertEquals(TerrainType.DEEP_WATER, tile.terrain());
            assertEquals(3, tile.x());
            assertEquals(7, tile.y());
            assertEquals(-1, tile.occupyingEntityId());
            assertFalse(tile.explored());
            assertFalse(tile.visible());
        }

        @Test
        @DisplayName("Should create tile with full state using factory method")
        void shouldCreateFullTileWithFactory() {
            Tile tile = Tile.of(TerrainType.FOREST, 5, 10, 42, true, false);
            assertEquals(TerrainType.FOREST, tile.terrain());
            assertEquals(42, tile.occupyingEntityId());
            assertTrue(tile.explored());
            assertFalse(tile.visible());
        }

        @Test
        @DisplayName("Should reject null terrain")
        void shouldRejectNullTerrain() {
            assertThrows(IllegalArgumentException.class,
                () -> new Tile(null, 0, 0, -1, false, false));
        }

        @Test
        @DisplayName("Should reject x coordinate below 0")
        void shouldRejectXBelowZero() {
            assertThrows(IllegalArgumentException.class,
                () -> new Tile(TerrainType.GRASS, -1, 0, -1, false, false));
        }

        @Test
        @DisplayName("Should reject x coordinate above 127")
        void shouldRejectXAbove127() {
            assertThrows(IllegalArgumentException.class,
                () -> new Tile(TerrainType.GRASS, 128, 0, -1, false, false));
        }

        @Test
        @DisplayName("Should reject y coordinate below 0")
        void shouldRejectYBelowZero() {
            assertThrows(IllegalArgumentException.class,
                () -> new Tile(TerrainType.GRASS, 0, -1, -1, false, false));
        }

        @Test
        @DisplayName("Should reject y coordinate above 127")
        void shouldRejectYAbove127() {
            assertThrows(IllegalArgumentException.class,
                () -> new Tile(TerrainType.GRASS, 0, 128, -1, false, false));
        }

        @Test
        @DisplayName("Should accept boundary coordinates 0 and 127")
        void shouldAcceptBoundaryCoordinates() {
            assertDoesNotThrow(() -> Tile.of(TerrainType.GRASS, 0, 0));
            assertDoesNotThrow(() -> Tile.of(TerrainType.GRASS, 127, 127));
        }
    }

    @Nested
    @DisplayName("Occupancy")
    class Occupancy {

        @Test
        @DisplayName("Should report occupied when entity ID >= 0")
        void shouldReportOccupiedWhenEntityPresent() {
            Tile occupied = Tile.of(TerrainType.GRASS, 0, 0, 5, false, false);
            assertTrue(occupied.isOccupied());
        }

        @Test
        @DisplayName("Should report not occupied when entity ID is -1")
        void shouldReportNotOccupiedWhenEmpty() {
            Tile empty = Tile.of(TerrainType.GRASS, 0, 0, -1, false, false);
            assertFalse(empty.isOccupied());
        }

        @Test
        @DisplayName("Should report not occupied when using simple factory")
        void shouldReportNotOccupiedFromSimpleFactory() {
            Tile tile = Tile.of(TerrainType.GRASS, 0, 0);
            assertFalse(tile.isOccupied());
        }
    }

    @Nested
    @DisplayName("With Methods (Immutable Copies)")
    class WithMethods {

        @Test
        @DisplayName("Should create copy with different occupant")
        void shouldCreateCopyWithOccupant() {
            Tile original = Tile.of(TerrainType.GRASS, 5, 10);
            Tile modified = original.withOccupant(42);

            assertEquals(-1, original.occupyingEntityId());
            assertEquals(42, modified.occupyingEntityId());
            assertEquals(original.terrain(), modified.terrain());
            assertEquals(original.x(), modified.x());
            assertEquals(original.y(), modified.y());
        }

        @Test
        @DisplayName("Should create copy with cleared occupant")
        void shouldCreateCopyWithClearedOccupant() {
            Tile occupied = Tile.of(TerrainType.GRASS, 5, 10, 42, true, true);
            Tile cleared = occupied.withOccupant(-1);

            assertEquals(42, occupied.occupyingEntityId());
            assertEquals(-1, cleared.occupyingEntityId());
            assertTrue(cleared.explored()); // other fields preserved
        }

        @Test
        @DisplayName("Should create copy with different terrain")
        void shouldCreateCopyWithTerrain() {
            Tile original = Tile.of(TerrainType.GRASS, 5, 10, 3, true, true);
            Tile modified = original.withTerrain(TerrainType.DEEP_WATER);

            assertEquals(TerrainType.GRASS, original.terrain());
            assertEquals(TerrainType.DEEP_WATER, modified.terrain());
            assertEquals(3, modified.occupyingEntityId());
            assertTrue(modified.explored());
        }

        @Test
        @DisplayName("Should create copy with different visibility")
        void shouldCreateCopyWithVisibility() {
            Tile original = Tile.of(TerrainType.GRASS, 5, 10, -1, false, false);
            Tile modified = original.withVisibility(true, true);

            assertFalse(original.explored());
            assertFalse(original.visible());
            assertTrue(modified.explored());
            assertTrue(modified.visible());
            assertEquals(original.terrain(), modified.terrain());
            assertEquals(original.occupyingEntityId(), modified.occupyingEntityId());
        }

        @Test
        @DisplayName("Should not modify original when creating copies")
        void shouldNotModifyOriginal() {
            Tile original = Tile.of(TerrainType.GRASS, 5, 10);
            original.withOccupant(1);
            original.withTerrain(TerrainType.DEEP_WATER);
            original.withVisibility(true, true);

            assertEquals(-1, original.occupyingEntityId());
            assertEquals(TerrainType.GRASS, original.terrain());
            assertFalse(original.explored());
            assertFalse(original.visible());
        }
    }

    @Nested
    @DisplayName("Passability")
    class Passability {

        @Test
        @DisplayName("Should be passable when terrain is passable and not occupied")
        void shouldBePassableWhenTerrainPassableAndNotOccupied() {
            Tile tile = Tile.of(TerrainType.GRASS, 0, 0);
            assertTrue(tile.isPassable());
        }

        @Test
        @DisplayName("Should not be passable when terrain is impassable")
        void shouldNotBePassableWhenTerrainImpassable() {
            Tile tile = Tile.of(TerrainType.DEEP_WATER, 0, 0);
            assertFalse(tile.isPassable());
        }

        @Test
        @DisplayName("Should be passable when terrain is passable even if occupied (use isOccupied() for occupancy)")
        void shouldBePassableEvenWhenOccupied() {
            Tile tile = Tile.of(TerrainType.GRASS, 0, 0, 5, false, false);
            assertTrue(tile.isPassable()); // terrain-only check
            assertTrue(tile.isOccupied()); // occupancy is separate
        }

        @Test
        @DisplayName("FOREST should be passable when unoccupied")
        void forestShouldBePassable() {
            Tile tile = Tile.of(TerrainType.FOREST, 0, 0);
            assertTrue(tile.isPassable());
        }

        @Test
        @DisplayName("ROAD should be passable when unoccupied")
        void roadShouldBePassable() {
            Tile tile = Tile.of(TerrainType.ROAD, 0, 0);
            assertTrue(tile.isPassable());
        }

        @Test
        @DisplayName("MOUNTAIN should not be passable")
        void mountainShouldNotBePassable() {
            Tile tile = Tile.of(TerrainType.MOUNTAIN, 0, 0);
            assertFalse(tile.isPassable());
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class RecordEquality {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            Tile a = new Tile(TerrainType.GRASS, 5, 10, -1, false, false);
            Tile b = new Tile(TerrainType.GRASS, 5, 10, -1, false, false);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            Tile a = new Tile(TerrainType.GRASS, 5, 10, -1, false, false);
            Tile b = new Tile(TerrainType.GRASS, 5, 10, 1, false, false);
            assertNotEquals(a, b);
        }
    }
}
