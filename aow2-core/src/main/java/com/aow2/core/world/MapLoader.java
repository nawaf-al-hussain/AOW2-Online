package com.aow2.core.world;

import com.aow2.common.model.TerrainType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads a {@link GameMap} from JSON data using Jackson.
 * Expected JSON format:
 * <pre>
 * {
 *   "width": 8,
 *   "height": 8,
 *   "tiles": [
 *     {"x": 0, "y": 0, "terrain": "WATER"},
 *     {"x": 1, "y": 0, "terrain": "MOUNTAIN"}
 *   ]
 * }
 * </pre>
 * Tiles not listed in the array default to GRASS.
 * <p>
 * REF: map_system.md — map data format from decrypted map files
 */
public class MapLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MapLoader.class);

    /** Shared Jackson ObjectMapper instance. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Internal DTO for map JSON deserialization.
     */
    static final class MapData {
        int width;
        int height;
        List<TileData> tiles;
        /** 2D terrain array format: terrain[row][col] = "GRASS", "FOREST", etc.
         *  FIX(PLAYTEST-4): Added support for the dense 2D terrain array format used
         *  by all campaign and custom mission maps. Previously only the sparse
         *  tiles:[{x,y,terrain}] format was supported. */
        List<List<String>> terrain;
        /** Starting positions from the map JSON. Not yet wired to spawn logic
         *  but preserved for future use. */
        List<StartingPositionData> startingPositions;

        @JsonProperty("width")
        public void setWidth(int width) { this.width = width; }

        @JsonProperty("height")
        public void setHeight(int height) { this.height = height; }

        @JsonProperty("tiles")
        public void setTiles(List<TileData> tiles) { this.tiles = tiles; }

        @JsonProperty("terrain")
        public void setTerrain(List<List<String>> terrain) { this.terrain = terrain; }

        @JsonProperty("startingPositions")
        public void setStartingPositions(List<StartingPositionData> sp) { this.startingPositions = sp; }
    }

    /** DTO for starting position entries in map JSON. */
    static final class StartingPositionData {
        int x;
        int y;
        String faction;

        @JsonProperty("x")
        public void setX(int x) { this.x = x; }

        @JsonProperty("y")
        public void setY(int y) { this.y = y; }

        @JsonProperty("faction")
        public void setFaction(String faction) { this.faction = faction; }
    }

    /**
     * Internal DTO for tile entries in the map JSON.
     */
    static final class TileData {
        int x;
        int y;
        String terrain;

        @JsonProperty("x")
        public void setX(int x) { this.x = x; }

        @JsonProperty("y")
        public void setY(int y) { this.y = y; }

        @JsonProperty("terrain")
        public void setTerrain(String terrain) { this.terrain = terrain; }
    }

    /**
     * Loads a map from a JSON file on the filesystem.
     *
     * @param path path to the JSON file
     * @return loaded GameMap
     * @throws IOException if the file cannot be read or parsed
     */
    public static GameMap loadFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return parseJson(json);
    }

    /**
     * Loads a map from a classpath resource.
     *
     * @param resourcePath classpath resource path (e.g., "data/maps/test_map.json")
     * @return loaded GameMap
     * @throws IOException if the resource cannot be read
     */
    public static GameMap loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = MapLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String json = reader.lines().collect(Collectors.joining("\n"));
                return parseJson(json);
            }
        }
    }

    /**
     * Parses a JSON string into a GameMap using Jackson.
     *
     * @param json JSON string
     * @return parsed GameMap
     */
    static GameMap parseJson(String json) {
        MapData mapData;
        try {
            mapData = MAPPER.readValue(json, MapData.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse map JSON: " + e.getMessage(), e);
        }

        if (mapData.width <= 0 || mapData.height <= 0) {
            throw new IllegalArgumentException("Invalid map dimensions: " + mapData.width + "x" + mapData.height);
        }

        GameMap map = new GameMap(mapData.width, mapData.height);

        int tileOverrides = 0;

        // FIX(PLAYTEST-4): Support the dense 2D "terrain" array format used by all
        // campaign and custom mission maps. Convert it to the same tile-setting
        // logic as the sparse "tiles" format.
        if ((mapData.tiles == null || mapData.tiles.isEmpty())
                && mapData.terrain != null && !mapData.terrain.isEmpty()) {
            List<TileData> convertedTiles = new ArrayList<>();
            for (int row = 0; row < mapData.terrain.size() && row < mapData.height; row++) {
                List<String> rowData = mapData.terrain.get(row);
                for (int col = 0; col < rowData.size() && col < mapData.width; col++) {
                    String terrainStr = rowData.get(col);
                    if (terrainStr != null && !terrainStr.isEmpty()) {
                        TileData td = new TileData();
                        td.x = col;
                        td.y = row;
                        td.terrain = terrainStr;
                        convertedTiles.add(td);
                    }
                }
            }
            LOG.info("Converted 2D terrain array ({}x{}) to {} tile entries",
                mapData.height, mapData.width, convertedTiles.size());
            // Process the converted tiles using the same logic as the sparse format
            for (TileData tileData : convertedTiles) {
                try {
                    TerrainType terrain = parseTerrainType(tileData.terrain);
                    if (map.isInBounds(tileData.x, tileData.y)) {
                        map.setTile(tileData.x, tileData.y, terrain);
                        tileOverrides++;
                    }
                } catch (IllegalArgumentException e) {
                    LOG.warn("Failed to parse terrain at ({}, {}): unknown terrain '{}'",
                        tileData.x, tileData.y, tileData.terrain);
                }
            }
        } else if (mapData.tiles != null && !mapData.tiles.isEmpty()) {
            // Original sparse tiles format (used by test_map.json)
            for (TileData tileData : mapData.tiles) {
                try {
                    TerrainType terrain = parseTerrainType(tileData.terrain);
                    if (map.isInBounds(tileData.x, tileData.y)) {
                        map.setTile(tileData.x, tileData.y, terrain);
                        tileOverrides++;
                    } else {
                        LOG.warn("Tile ({}, {}) out of bounds for map {}x{}, skipping",
                            tileData.x, tileData.y, mapData.width, mapData.height);
                    }
                } catch (IllegalArgumentException e) {
                    LOG.warn("Failed to parse tile entry at ({}, {}): unknown terrain '{}'",
                        tileData.x, tileData.y, tileData.terrain);
                }
            }
        } else {
            LOG.warn("No tiles or terrain array found in map JSON, returning empty GRASS map");
        }

        LOG.info("Loaded map {}x{} with {} tile overrides", mapData.width, mapData.height, tileOverrides);
        return map;
    }

    /**
     * Parses a terrain type string, supporting legacy aliases.
     * "WATER" maps to DEEP_WATER for backward compatibility with existing map data.
     *
     * @param name the terrain type name
     * @return the matching TerrainType
     * @throws IllegalArgumentException if the name doesn't match any terrain type
     */
    private static TerrainType parseTerrainType(String name) {
        // Support legacy "WATER" alias for DEEP_WATER
        if ("WATER".equals(name)) {
            return TerrainType.DEEP_WATER;
        }
        return TerrainType.valueOf(name);
    }
}
