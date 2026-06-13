package com.aow2.core.world;

import com.aow2.common.model.TerrainType;

/**
 * 2D grid-based game map composed of {@link TerrainType} tiles.
 * The map dimensions range from 0 to 127 in each axis.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.2 — Map System
 * REF: map_system.md — terrain types from decrypted map data
 * REF: complete_building_stats.json — max_map_size: 127
 */
public class GameMap {

    private final int width;
    private final int height;
    private final TerrainType[][] tiles;

    /**
     * Constructs a new map filled with GRASS tiles.
     *
     * @param width  map width (1-127)
     * @param height map height (1-127)
     */
    public GameMap(int width, int height) {
        if (width < 1 || width > 127) {
            throw new IllegalArgumentException("Width must be 1-127, got: " + width);
        }
        if (height < 1 || height > 127) {
            throw new IllegalArgumentException("Height must be 1-127, got: " + height);
        }
        this.width = width;
        this.height = height;
        this.tiles = new TerrainType[width][height];

        // Fill with GRASS by default
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = TerrainType.GRASS;
            }
        }
    }

    /**
     * Gets the terrain type at the given coordinates.
     *
     * @param x column index
     * @param y row index
     * @return terrain type, or null if out of bounds
     */
    public TerrainType getTile(int x, int y) {
        if (!isInBounds(x, y)) {
            return null;
        }
        return tiles[x][y];
    }

    /**
     * Sets the terrain type at the given coordinates.
     *
     * @param x     column index
     * @param y     row index
     * @param terrain terrain type to set
     * @throws IndexOutOfBoundsException if coordinates are out of bounds
     */
    public void setTile(int x, int y, TerrainType terrain) {
        if (!isInBounds(x, y)) {
            throw new IndexOutOfBoundsException(
                "Tile coordinates out of bounds: (" + x + ", " + y + ") for map " + width + "x" + height);
        }
        tiles[x][y] = terrain;
    }

    /**
     * Checks whether the given tile is passable for ground units.
     *
     * @param x column index
     * @param y row index
     * @return true if the tile is in bounds and passable
     */
    public boolean isPassable(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return tiles[x][y].isPassable();
    }

    /**
     * Checks whether the given coordinates are within map bounds.
     *
     * @param x column index
     * @param y row index
     * @return true if in bounds
     */
    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * Creates a small test map with varied terrain.
     * 8x8 map with a mix of GRASS, WATER, MOUNTAIN, FOREST, and ROAD tiles.
     *
     * @return a test map
     */
    public static GameMap createTestMap() {
        GameMap map = new GameMap(8, 8);

        // River of water across the middle
        for (int x = 0; x < 8; x++) {
            map.setTile(x, 3, TerrainType.WATER);
            map.setTile(x, 4, TerrainType.WATER);
        }

        // Bridge across the river
        map.setTile(3, 3, TerrainType.BRIDGE);
        map.setTile(3, 4, TerrainType.BRIDGE);

        // Mountain range in the top-right
        map.setTile(6, 0, TerrainType.MOUNTAIN);
        map.setTile(7, 0, TerrainType.MOUNTAIN);
        map.setTile(6, 1, TerrainType.MOUNTAIN);
        map.setTile(7, 1, TerrainType.MOUNTAIN);

        // Forest patches
        map.setTile(0, 0, TerrainType.FOREST);
        map.setTile(1, 0, TerrainType.FOREST);
        map.setTile(0, 1, TerrainType.FOREST);

        // Road through the bridge
        map.setTile(3, 0, TerrainType.ROAD);
        map.setTile(3, 1, TerrainType.ROAD);
        map.setTile(3, 2, TerrainType.ROAD);
        map.setTile(3, 5, TerrainType.ROAD);
        map.setTile(3, 6, TerrainType.ROAD);
        map.setTile(3, 7, TerrainType.ROAD);

        // Sand area
        map.setTile(5, 5, TerrainType.SAND);
        map.setTile(5, 6, TerrainType.SAND);
        map.setTile(6, 5, TerrainType.SAND);

        // Ruins
        map.setTile(1, 6, TerrainType.RUINS);

        return map;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
