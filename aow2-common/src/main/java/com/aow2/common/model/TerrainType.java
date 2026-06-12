package com.aow2.common.model;

/**
 * Terrain tile types for the game map.
 * REF: map_system.md - terrain types from decrypted map data
 */
public enum TerrainType {
    GRASS(true),
    SAND(true),
    WATER(false),
    MOUNTAIN(false),
    FOREST(true),
    ROAD(true),
    BRIDGE(true),
    RUINS(true);

    private final boolean passable;

    TerrainType(boolean passable) {
        this.passable = passable;
    }

    public boolean isPassable() {
        return passable;
    }
}
