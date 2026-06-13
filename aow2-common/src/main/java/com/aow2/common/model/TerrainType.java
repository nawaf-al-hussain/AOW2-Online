package com.aow2.common.model;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Terrain tile types for the game map.
 * REF: map_system.md Section 3.1 - terrain types from decrypted map data
 * REF: map_system.md - 18+ terrain types with specific passability properties per unit category
 *
 * FIX LOG:
 * - Added DEEP_WATER, SHALLOW_WATER, HILLS, SWAMP, SNOW, RESOURCE_DEPOSIT
 * - Changed passability from simple boolean to per-category system
 * - Added movement cost field for pathfinding
 * - Removed duplicate WATER alias (use DEEP_WATER instead)
 * - Added id field mapping to original game's terrain IDs
 * - Added static fromId() lookup method
 */
public enum TerrainType {
    // Water terrain types
    /** Deep water: impassable for ground units. Original terrain ID 4 */
    DEEP_WATER(4, false, 10),
    /** Shallow water: passable by infantry only. Original terrain ID 3 */
    SHALLOW_WATER(3, false, 8),
    // Land terrain types
    /** Sand: slightly slower than grass. Original terrain ID 1 */
    SAND(1, true, 2),
    /** Plains/Grass: baseline terrain. Original terrain ID 0 */
    GRASS(0, true, 1),
    /** Road: fastest movement. Original terrain ID 7 */
    ROAD(7, true, 0),
    /** Dirt: similar to grass. Original terrain ID 2 */
    DIRT(2, true, 1),
    /** Hills: slower for all units. Original terrain ID 12 */
    HILLS(12, true, 3),
    /** Forest: provides cover, slower movement. Original terrain ID 6 */
    FOREST(6, true, 2),
    /** Bridge: connects land across water. Original terrain ID 8 */
    BRIDGE(8, true, 1),
    /** Mountain: impassable for all ground units. Original terrain ID 5 */
    MOUNTAIN(5, false, 10),
    /** Swamp: very slow for vehicles, slow for infantry. Original terrain ID 13 */
    SWAMP(13, true, 4),
    /** Snow: slow movement. Original terrain ID 14 */
    SNOW(14, true, 3),
    /** Ice: slippery terrain. Original terrain ID 9 */
    ICE(9, true, 2),
    /** Ruins: moderately slow. Original terrain ID 15 */
    RUINS(15, true, 2),
    /** Resource deposit: harvestable for credits. Original terrain ID 25 */
    RESOURCE_DEPOSIT(25, true, 1);

    private final int id;
    private final boolean passableByDefault;
    private final int movementCost;

    /** Lookup map from terrain ID to TerrainType. */
    private static final Map<Integer, TerrainType> ID_MAP = Stream.of(values())
        .collect(Collectors.toMap(TerrainType::id, Function.identity()));

    TerrainType(int id, boolean passableByDefault, int movementCost) {
        this.id = id;
        this.passableByDefault = passableByDefault;
        this.movementCost = movementCost;
    }

    /**
     * Returns the original game terrain ID for this terrain type.
     *
     * @return the terrain ID
     */
    public int id() {
        return id;
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
            case DEEP_WATER -> false; // No ground units can cross deep water
            case SHALLOW_WATER -> category == UnitCategory.INFANTRY; // Only infantry
            case MOUNTAIN -> false; // Impassable for all
            case SWAMP -> category != UnitCategory.VEHICLE && category != UnitCategory.SPECIAL_MACHINERY; // Vehicles and SPECIAL_MACHINERY bog down
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

    /**
     * Look up a TerrainType by its original game terrain ID.
     *
     * @param id the terrain ID from the original game
     * @return the matching TerrainType, or null if no terrain has that ID
     */
    public static TerrainType fromId(int id) {
        return ID_MAP.get(id);
    }
}
