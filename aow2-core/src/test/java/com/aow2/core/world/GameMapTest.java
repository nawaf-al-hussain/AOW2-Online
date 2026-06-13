package com.aow2.core.world;

import com.aow2.common.model.TerrainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameMapTest {

    @Nested
    @DisplayName("Map Dimensions")
    class Dimensions {

        @Test
        @DisplayName("Should create map with valid dimensions")
        void shouldCreateMapWithValidDimensions() {
            GameMap map = new GameMap(10, 8);
            assertEquals(10, map.getWidth());
            assertEquals(8, map.getHeight());
        }

        @Test
        @DisplayName("Should reject width below 1")
        void shouldRejectZeroWidth() {
            assertThrows(IllegalArgumentException.class, () -> new GameMap(0, 8));
        }

        @Test
        @DisplayName("Should reject height above 128")
        void shouldRejectHeightAboveMax() {
            assertThrows(IllegalArgumentException.class, () -> new GameMap(10, 129));
        }

        @Test
        @DisplayName("Should accept maximum valid dimensions 128x128")
        void shouldAcceptMaxDimensions() {
            GameMap map = new GameMap(GameMap.MAX_MAP_SIZE, GameMap.MAX_MAP_SIZE);
            assertEquals(GameMap.MAX_MAP_SIZE, map.getWidth());
            assertEquals(GameMap.MAX_MAP_SIZE, map.getHeight());
        }
    }

    @Nested
    @DisplayName("Tile Get/Set")
    class TileOperations {

        @Test
        @DisplayName("Should default all tiles to GRASS")
        void shouldDefaultToGrass() {
            GameMap map = new GameMap(5, 5);
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    assertEquals(TerrainType.GRASS, map.getTile(x, y));
                }
            }
        }

        @Test
        @DisplayName("Should set and get tile terrain")
        void shouldSetAndGetTile() {
            GameMap map = new GameMap(5, 5);

            map.setTile(2, 3, TerrainType.DEEP_WATER);
            assertEquals(TerrainType.DEEP_WATER, map.getTile(2, 3));

            map.setTile(0, 0, TerrainType.MOUNTAIN);
            assertEquals(TerrainType.MOUNTAIN, map.getTile(0, 0));

            map.setTile(4, 4, TerrainType.FOREST);
            assertEquals(TerrainType.FOREST, map.getTile(4, 4));
        }

        @Test
        @DisplayName("Should throw when setting tile out of bounds")
        void shouldThrowWhenSettingOutOfBounds() {
            GameMap map = new GameMap(5, 5);
            assertThrows(IndexOutOfBoundsException.class,
                () -> map.setTile(5, 0, TerrainType.DEEP_WATER));
            assertThrows(IndexOutOfBoundsException.class,
                () -> map.setTile(0, 5, TerrainType.DEEP_WATER));
            assertThrows(IndexOutOfBoundsException.class,
                () -> map.setTile(-1, 0, TerrainType.DEEP_WATER));
        }
    }

    @Nested
    @DisplayName("Passability")
    class Passability {

        @Test
        @DisplayName("GRASS should be passable")
        void grassPassable() {
            GameMap map = new GameMap(5, 5);
            assertTrue(map.isPassable(0, 0)); // default GRASS
        }

        @Test
        @DisplayName("WATER should not be passable")
        void waterNotPassable() {
            GameMap map = new GameMap(5, 5);
            map.setTile(2, 2, TerrainType.DEEP_WATER);
            assertFalse(map.isPassable(2, 2));
        }

        @Test
        @DisplayName("MOUNTAIN should not be passable")
        void mountainNotPassable() {
            GameMap map = new GameMap(5, 5);
            map.setTile(1, 1, TerrainType.MOUNTAIN);
            assertFalse(map.isPassable(1, 1));
        }

        @Test
        @DisplayName("FOREST should be passable")
        void forestPassable() {
            GameMap map = new GameMap(5, 5);
            map.setTile(3, 3, TerrainType.FOREST);
            assertTrue(map.isPassable(3, 3));
        }

        @Test
        @DisplayName("ROAD should be passable")
        void roadPassable() {
            GameMap map = new GameMap(5, 5);
            map.setTile(0, 0, TerrainType.ROAD);
            assertTrue(map.isPassable(0, 0));
        }

        @Test
        @DisplayName("Out of bounds should not be passable")
        void outOfBoundsNotPassable() {
            GameMap map = new GameMap(5, 5);
            assertFalse(map.isPassable(-1, 0));
            assertFalse(map.isPassable(0, -1));
            assertFalse(map.isPassable(5, 0));
            assertFalse(map.isPassable(0, 5));
        }
    }

    @Nested
    @DisplayName("Bounds Checking")
    class BoundsChecking {

        @Test
        @DisplayName("Should report in-bounds coordinates correctly")
        void shouldReportInBounds() {
            GameMap map = new GameMap(10, 8);
            assertTrue(map.isInBounds(0, 0));
            assertTrue(map.isInBounds(9, 7));
            assertTrue(map.isInBounds(5, 4));
        }

        @Test
        @DisplayName("Should report out-of-bounds coordinates correctly")
        void shouldReportOutOfBounds() {
            GameMap map = new GameMap(10, 8);
            assertFalse(map.isInBounds(-1, 0));
            assertFalse(map.isInBounds(0, -1));
            assertFalse(map.isInBounds(10, 0));
            assertFalse(map.isInBounds(0, 8));
        }

        @Test
        @DisplayName("Should return null for out-of-bounds getTile")
        void shouldReturnNullForOutOfBoundsGet() {
            GameMap map = new GameMap(5, 5);
            assertNull(map.getTile(-1, 0));
            assertNull(map.getTile(0, -1));
            assertNull(map.getTile(5, 0));
        }
    }

    @Nested
    @DisplayName("Test Map Creation")
    class TestMap {

        @Test
        @DisplayName("Should create a test map with varied terrain")
        void shouldCreateTestMap() {
            GameMap map = GameMap.createTestMap();

            assertEquals(8, map.getWidth());
            assertEquals(8, map.getHeight());

            // River across middle
            assertEquals(TerrainType.DEEP_WATER, map.getTile(0, 3));
            assertEquals(TerrainType.DEEP_WATER, map.getTile(0, 4));

            // Bridge
            assertEquals(TerrainType.BRIDGE, map.getTile(3, 3));
            assertEquals(TerrainType.BRIDGE, map.getTile(3, 4));

            // Mountains
            assertEquals(TerrainType.MOUNTAIN, map.getTile(6, 0));
            assertEquals(TerrainType.MOUNTAIN, map.getTile(7, 1));

            // Forest
            assertEquals(TerrainType.FOREST, map.getTile(0, 0));
            assertEquals(TerrainType.FOREST, map.getTile(1, 0));

            // Road
            assertEquals(TerrainType.ROAD, map.getTile(3, 0));
            assertEquals(TerrainType.ROAD, map.getTile(3, 7));

            // Passability checks
            assertFalse(map.isPassable(0, 3));  // WATER
            assertTrue(map.isPassable(3, 3));   // BRIDGE
            assertFalse(map.isPassable(6, 0));  // MOUNTAIN
            assertTrue(map.isPassable(3, 0));   // ROAD
        }
    }
}
