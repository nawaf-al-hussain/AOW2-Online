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
 * - FIX: Corrected terrain IDs to match RE spec Section 3.1:
 *        0=Deep Water, 1=Shallow Water, 2=Sand, 3=Plains/Grass, 4=Forest,
 *        5=Hills, 6=Mountains, 7=Road, 8=Bridge, 9=Swamp, 10=Snow
 * - FIX: Removed DIRT(2), ICE(9), RUINS(15) — not in RE spec, were fabricated
 * - FIX: Updated movement costs: Deep Water=MAX, Shallow Water=MAX, Sand=2,
 *        Grass=1, Forest=2, Hills=3, Mountain=MAX, Road=0, Bridge=1, Swamp=4, Snow=3
 * - FIX: Updated passability: Deep Water=false, Shallow Water=false, Mountain=false, all others true
 */
public enum TerrainType {
    // Water terrain types
    /** Deep water: impassable for all ground units. RE terrain ID 0 */
    DEEP_WATER(0, false, Integer.MAX_VALUE),
    /** Shallow water: passable by infantry only. RE terrain ID 1 */
    SHALLOW_WATER(1, false, Integer.MAX_VALUE),
    // Land terrain types
    /** Sand: slightly slower than grass. RE terrain ID 2 */
    SAND(2, true, 2),
    /** Plains/Grass: baseline terrain. RE terrain ID 3 */
    GRASS(3, true, 1),
    /** Forest: provides cover, slower movement. RE terrain ID 4 */
    FOREST(4, true, 2),
    /** Hills: slower for all units. RE terrain ID 5 */
    HILLS(5, true, 3),
    /** Mountain: impassable for all ground units. RE terrain ID 6 */
    MOUNTAIN(6, false, Integer.MAX_VALUE),
    /** Road: fastest movement. RE terrain ID 7 */
    ROAD(7, true, 0),
    /** Bridge: connects land across water. RE terrain ID 8 */
    BRIDGE(8, true, 1),
    /** Swamp: very slow for vehicles, slow for infantry. RE terrain ID 9 */
    SWAMP(9, true, 4),
    /** Snow: slow movement. RE terrain ID 10 */
    SNOW(10, true, 3),
    /** Resource deposit: harvestable for credits. RE terrain ID 25 */
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
            // FIX (F-26): SHALLOW_WATER was passableBy(INFANTRY)=true but getMovementCost()=MAX_VALUE,
            // creating a contradiction in the pathfinder (A* open set used isPassableBy but
            // g-cost used getMovementCost, yielding infinite cost for a "passable" tile).
            // Now SHALLOW_WATER is impassable for ALL units, consistent with the constructor
            // (passable=false, movementCost=MAX_VALUE) and getMovementCost().
            case SHALLOW_WATER -> false;
            case MOUNTAIN -> false; // Impassable for all
            case SWAMP -> category != UnitCategory.VEHICLE && category != UnitCategory.SPECIAL_MACHINERY; // Vehicles and SPECIAL_MACHINERY bog down
            default -> passableByDefault;
        };
    }

    /**
     * Returns the movement cost for traversing this terrain.
     * REF: map_system.md Section 3.3 - movement costs from as[14]/as[15] arrays
     * Lower = faster. 0 = road (fastest), 1 = grass baseline, higher = slower.
     * Integer.MAX_VALUE = impassable (except SHALLOW_WATER for infantry via isPassableBy).
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
