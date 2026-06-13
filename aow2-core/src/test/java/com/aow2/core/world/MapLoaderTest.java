package com.aow2.core.world;

import com.aow2.common.model.TerrainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MapLoaderTest {

    @Nested
    @DisplayName("JSON Parsing")
    class JsonParsing {

        @Test
        @DisplayName("Should parse valid map JSON with Jackson")
        void shouldParseValidMapJson() {
            String json = """
                {
                  "width": 8,
                  "height": 8,
                  "tiles": [
                    {"x": 0, "y": 0, "terrain": "WATER"},
                    {"x": 1, "y": 0, "terrain": "MOUNTAIN"},
                    {"x": 2, "y": 2, "terrain": "FOREST"}
                  ]
                }
                """;

            GameMap map = MapLoader.parseJson(json);

            assertEquals(8, map.getWidth());
            assertEquals(8, map.getHeight());
            assertEquals(TerrainType.WATER, map.getTile(0, 0));
            assertEquals(TerrainType.MOUNTAIN, map.getTile(1, 0));
            assertEquals(TerrainType.FOREST, map.getTile(2, 2));
            assertEquals(TerrainType.GRASS, map.getTile(3, 3)); // default
        }

        @Test
        @DisplayName("Should return GRASS map when tiles array is missing")
        void shouldReturnGrassMapWhenNoTiles() {
            String json = """
                {
                  "width": 4,
                  "height": 4,
                  "tiles": []
                }
                """;

            GameMap map = MapLoader.parseJson(json);

            assertEquals(4, map.getWidth());
            assertEquals(4, map.getHeight());
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    assertEquals(TerrainType.GRASS, map.getTile(x, y));
                }
            }
        }

        @Test
        @DisplayName("Should reject invalid map dimensions")
        void shouldRejectInvalidDimensions() {
            String json = """
                {
                  "width": 0,
                  "height": 5,
                  "tiles": []
                }
                """;

            assertThrows(IllegalArgumentException.class, () -> MapLoader.parseJson(json));
        }

        @Test
        @DisplayName("Should skip out-of-bounds tiles")
        void shouldSkipOutOfBoundsTiles() {
            String json = """
                {
                  "width": 4,
                  "height": 4,
                  "tiles": [
                    {"x": 0, "y": 0, "terrain": "WATER"},
                    {"x": 10, "y": 10, "terrain": "FOREST"}
                  ]
                }
                """;

            GameMap map = MapLoader.parseJson(json);

            assertEquals(TerrainType.WATER, map.getTile(0, 0));
            assertNull(map.getTile(10, 10)); // out of bounds
        }

        @Test
        @DisplayName("Should skip tiles with unknown terrain")
        void shouldSkipUnknownTerrain() {
            String json = """
                {
                  "width": 4,
                  "height": 4,
                  "tiles": [
                    {"x": 0, "y": 0, "terrain": "WATER"},
                    {"x": 1, "y": 1, "terrain": "INVALID_TERRAIN"}
                  ]
                }
                """;

            GameMap map = MapLoader.parseJson(json);

            assertEquals(TerrainType.WATER, map.getTile(0, 0));
            assertEquals(TerrainType.GRASS, map.getTile(1, 1)); // not overridden
        }

        @Test
        @DisplayName("Should handle all terrain types")
        void shouldHandleAllTerrainTypes() {
            StringBuilder tilesBuilder = new StringBuilder();
            TerrainType[] types = TerrainType.values();
            for (int i = 0; i < types.length; i++) {
                if (i > 0) tilesBuilder.append(",\n");
                tilesBuilder.append("    {\"x\": ").append(i).append(", \"y\": 0, \"terrain\": \"")
                    .append(types[i].name()).append("\"}");
            }

            String json = """
                {
                  "width": 10,
                  "height": 1,
                  "tiles": [
                %s
                  ]
                }
                """.formatted(tilesBuilder.toString());

            GameMap map = MapLoader.parseJson(json);

            for (int i = 0; i < types.length; i++) {
                assertEquals(types[i], map.getTile(i, 0));
            }
        }
    }

    @Nested
    @DisplayName("File Loading")
    class FileLoading {

        @Test
        @DisplayName("Should load map from filesystem file")
        void shouldLoadFromFilesystem() throws IOException {
            String json = """
                {
                  "width": 5,
                  "height": 5,
                  "tiles": [
                    {"x": 0, "y": 0, "terrain": "SAND"},
                    {"x": 4, "y": 4, "terrain": "RUINS"}
                  ]
                }
                """;
            Path tempFile = Files.createTempFile("test_map", ".json");
            try {
                Files.writeString(tempFile, json);
                GameMap map = MapLoader.loadFromFile(tempFile);

                assertEquals(5, map.getWidth());
                assertEquals(5, map.getHeight());
                assertEquals(TerrainType.SAND, map.getTile(0, 0));
                assertEquals(TerrainType.RUINS, map.getTile(4, 4));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("Should load map from classpath resource")
        void shouldLoadFromClasspath() throws IOException {
            GameMap map = MapLoader.loadFromResource("data/maps/test_map.json");

            assertNotNull(map);
            assertEquals(16, map.getWidth());
            assertEquals(16, map.getHeight());
            assertEquals(TerrainType.WATER, map.getTile(0, 7));
            assertEquals(TerrainType.BRIDGE, map.getTile(7, 7));
        }

        @Test
        @DisplayName("Should throw when resource not found")
        void shouldThrowWhenResourceNotFound() {
            assertThrows(IOException.class,
                () -> MapLoader.loadFromResource("nonexistent_map.json"));
        }
    }
}
