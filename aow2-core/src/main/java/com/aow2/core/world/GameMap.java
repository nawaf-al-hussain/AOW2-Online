package com.aow2.core.world;

import com.aow2.common.model.TerrainType;

/**
 * 2D grid-based game map composed of {@link TerrainType} tiles.
 * The map is a 128×128 grid with valid coordinates 0-127 in each axis.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.2 — Map System
 * REF: map_system.md Section 1.1 — "byte[][] O = Array.newInstance(Byte.TYPE, 128, 128)"
 * REF: map_system.md Section 1.1 — "short[][] P = Array.newInstance(Short.TYPE, 128, 128)"
 *
 * FIX LOG: Max dimension changed from 127 to 128 to match original 128×128 grid.
 * The RE uses byte[128][128] meaning indices 0-127, which IS 128 cells.
 */
public class GameMap {

    /** Maximum map dimension. REF: map_system.md — 128×128 grid (indices 0-127). */
    public static final int MAX_MAP_SIZE = 128;

    private final int width;
    private final int height;
    private final TerrainType[][] tiles;

    /**
     * Constructs a new map filled with GRASS tiles.
     *
     * @param width  map width (1-128)
     * @param height map height (1-128)
     */
    public GameMap(int width, int height) {
        if (width < 1 || width > MAX_MAP_SIZE) {
            throw new IllegalArgumentException("Width must be 1-" + MAX_MAP_SIZE + ", got: " + width);
        }
        if (height < 1 || height > MAX_MAP_SIZE) {
            throw new IllegalArgumentException("Height must be 1-" + MAX_MAP_SIZE + ", got: " + height);
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
