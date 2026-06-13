package com.aow2.common.model;

/**
 * Terrain tile types for the game map.
 * REF: map_system.md Section 3.1 - terrain types from decrypted map data
 * REF: map_system.md - 18+ terrain types with specific passability properties per unit category
 *
 * FIX LOG:
 * - Added DEEP_WATER, SHALLOW_WATER, HILLS, SWAMP, SNOW, RESOURCE_DEPOSIT
 * - Changed passability from simple boolean to per-category system
 * - Added movement cost field for pathfinding
 * - Kept WATER as alias for DEEP_WATER for backward compatibility
 */
public enum TerrainType {
    // Water terrain types
    /** ID 0 - Deep water: impassable for ground units. Alias: WATER */
    DEEP_WATER(false, 10),
    /** ID 1 - Shallow water: passable by infantry only */
    SHALLOW_WATER(false, 8),
    /** Backward-compatible alias for DEEP_WATER */
    WATER(false, 10),
    // Land terrain types
    /** ID 2 - Sand: slightly slower than grass */
    SAND(true, 2),
    /** ID 3 - Plains/Grass: baseline terrain */
    GRASS(true, 1),
    /** ID 4 - Road: fastest movement */
    ROAD(true, 0),
    /** ID 5 - Hills: slower for all units */
    HILLS(true, 3),
    /** ID 6 - Forest: provides cover, slower movement */
    FOREST(true, 2),
    /** ID 7 - Bridge: connects land across water */
    BRIDGE(true, 1),
    /** ID 8 - Mountain: impassable for all ground units */
    MOUNTAIN(false, 10),
    /** ID 9 - Swamp: very slow for vehicles, slow for infantry */
    SWAMP(true, 4),
    /** ID 10 - Snow: slow movement */
    SNOW(true, 3),
    /** ID 11 - Ruins: moderately slow */
    RUINS(true, 2),
    /** ID 25 - Resource deposit: harvestable for credits */
    RESOURCE_DEPOSIT(true, 1);

    private final boolean passableByDefault;
    private final int movementCost;

    TerrainType(boolean passableByDefault, int movementCost) {
        this.passableByDefault = passableByDefault;
        this.movementCost = movementCost;
    }

    /**
     * Returns whether this terrain is passable by default (by infantry and most units).
     * REF: map_system.md Section 3.1 - per-unit-type passability
     *
     * @return true if passable by default
     */
    public boolean isPassable() {
        return passableByDefault;
    }

    /**
     * Returns whether this terrain is passable by a specific unit category.
     * REF: map_system.md - terrain passability varies by unit type
     *
     * @param category the unit category
     * @return true if passable by this category
     */
    public boolean isPassableBy(UnitCategory category) {
        return switch (this) {
            case DEEP_WATER, WATER -> false; // No ground units can cross deep water
            case SHALLOW_WATER -> category == UnitCategory.INFANTRY; // Only infantry
            case MOUNTAIN -> false; // Impassable for all
            case SWAMP -> category != UnitCategory.VEHICLE; // Vehicles bog down
            default -> passableByDefault;
        };
    }

    /**
     * Returns the movement cost for traversing this terrain.
     * REF: map_system.md Section 3.3 - movement costs from as[14]/as[15] arrays
     * Lower = faster. 0 = road (fastest), 1 = grass baseline, higher = slower.
     *
     * @return movement cost value
     */
    public int getMovementCost() {
        return movementCost;
    }
}
