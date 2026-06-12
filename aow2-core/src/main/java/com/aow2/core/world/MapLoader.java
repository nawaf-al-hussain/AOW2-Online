package com.aow2.core.world;

import com.aow2.common.model.TerrainType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Loads a {@link GameMap} from JSON data.
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

    /**
     * Loads a map from a JSON file on the filesystem.
     *
     * @param path path to the JSON file
     * @return loaded GameMap
     * @throws IOException if the file cannot be read
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
     * Parses a JSON string into a GameMap.
     * Uses simple string parsing — no external JSON library dependency.
     *
     * @param json JSON string
     * @return parsed GameMap
     */
    static GameMap parseJson(String json) {
        // Extract width
        int width = extractIntValue(json, "width");
        int height = extractIntValue(json, "height");

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid map dimensions: " + width + "x" + height);
        }

        GameMap map = new GameMap(width, height);

        // Find the tiles array and parse each tile entry
        int tilesStart = json.indexOf("\"tiles\"");
        if (tilesStart < 0) {
            LOG.warn("No tiles array found in map JSON, returning empty GRASS map");
            return map;
        }

        int arrayStart = json.indexOf('[', tilesStart);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            LOG.warn("Malformed tiles array in map JSON");
            return map;
        }

        String tilesContent = json.substring(arrayStart + 1, arrayEnd);

        // Split on } to get individual tile objects
        String[] tileObjects = tilesContent.split("\\}");
        for (String tileObj : tileObjects) {
            String trimmed = tileObj.trim();
            if (trimmed.isEmpty() || trimmed.equals(",")) {
                continue;
            }

            try {
                int x = extractIntValue(trimmed, "x");
                int y = extractIntValue(trimmed, "y");
                String terrainName = extractStringValue(trimmed, "terrain");

                TerrainType terrain = TerrainType.valueOf(terrainName);
                if (map.isInBounds(x, y)) {
                    map.setTile(x, y, terrain);
                } else {
                    LOG.warn("Tile ({}, {}) out of bounds for map {}x{}, skipping", x, y, width, height);
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Failed to parse tile entry: {}", trimmed, e);
            }
        }

        LOG.info("Loaded map {}x{} with {} tile overrides", width, height, tileObjects.length);
        return map;
    }

    /**
     * Extracts an integer value for the given key from a JSON fragment.
     */
    private static int extractIntValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            throw new IllegalArgumentException("Key not found: " + key);
        }

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex < 0) {
            throw new IllegalArgumentException("Colon not found after key: " + key);
        }

        // Skip whitespace after colon
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        // Read digits (and optional minus sign)
        int valueEnd = valueStart;
        if (valueEnd < json.length() && json.charAt(valueEnd) == '-') {
            valueEnd++;
        }
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }

        return Integer.parseInt(json.substring(valueStart, valueEnd).trim());
    }

    /**
     * Extracts a string value for the given key from a JSON fragment.
     */
    private static String extractStringValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            throw new IllegalArgumentException("Key not found: " + key);
        }

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex < 0) {
            throw new IllegalArgumentException("Colon not found after key: " + key);
        }

        // Find opening quote
        int openQuote = json.indexOf('"', colonIndex + 1);
        if (openQuote < 0) {
            throw new IllegalArgumentException("Opening quote not found for key: " + key);
        }

        // Find closing quote
        int closeQuote = json.indexOf('"', openQuote + 1);
        if (closeQuote < 0) {
            throw new IllegalArgumentException("Closing quote not found for key: " + key);
        }

        return json.substring(openQuote + 1, closeQuote);
    }
}
