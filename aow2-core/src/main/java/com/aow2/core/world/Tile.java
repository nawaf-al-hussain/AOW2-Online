package com.aow2.core.world;

import com.aow2.common.model.TerrainType;

/**
 * Represents a single tile on the game map.
 * Each tile has terrain type and can contain references to occupying entities.
 * Tile is immutable; use the withXxx() factory methods to create modified copies.
 * REF: map_system.md - tile properties
 */
public record Tile(
    TerrainType terrain,
    int x,
    int y,
    int occupyingEntityId,
    boolean explored,
    boolean visible
) {
    /**
     * Compact constructor validating tile fields.
     */
    public Tile {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain must not be null");
        }
        if (x < 0 || x > 127) {
            throw new IllegalArgumentException("x must be 0-127, got: " + x);
        }
        if (y < 0 || y > 127) {
            throw new IllegalArgumentException("y must be 0-127, got: " + y);
        }
    }

    /**
     * Creates a simple tile with default state: no occupant, not explored, not visible.
     *
     * @param terrain terrain type
     * @param x       x coordinate
     * @param y       y coordinate
     * @return a new Tile with default occupancy and visibility
     */
    public static Tile of(TerrainType terrain, int x, int y) {
        return new Tile(terrain, x, y, -1, false, false);
    }

    /**
     * Creates a tile with all fields specified.
     *
     * @param terrain            terrain type
     * @param x                  x coordinate
     * @param y                  y coordinate
     * @param occupyingEntityId  entity ID occupying this tile (-1 if empty)
     * @param explored           whether this tile has been explored
     * @param visible            whether this tile is currently visible
     * @return a new Tile
     */
    public static Tile of(TerrainType terrain, int x, int y,
                          int occupyingEntityId, boolean explored, boolean visible) {
        return new Tile(terrain, x, y, occupyingEntityId, explored, visible);
    }

    /**
     * Returns whether this tile is occupied by an entity.
     *
     * @return true if an entity occupies this tile
     */
    public boolean isOccupied() {
        return occupyingEntityId >= 0;
    }

    /**
     * Creates a copy of this tile with a different occupying entity.
     *
     * @param entityId the new occupying entity ID (-1 to clear)
     * @return a new Tile with the updated occupant
     */
    public Tile withOccupant(int entityId) {
        return new Tile(terrain, x, y, entityId, explored, visible);
    }

    /**
     * Creates a copy of this tile with a different terrain type.
     *
     * @param newTerrain the new terrain type
     * @return a new Tile with the updated terrain
     */
    public Tile withTerrain(TerrainType newTerrain) {
        return new Tile(newTerrain, x, y, occupyingEntityId, explored, visible);
    }

    /**
     * Creates a copy of this tile with updated visibility state.
     *
     * @param newExplored whether the tile has been explored
     * @param newVisible  whether the tile is currently visible
     * @return a new Tile with the updated visibility
     */
    public Tile withVisibility(boolean newExplored, boolean newVisible) {
        return new Tile(terrain, x, y, occupyingEntityId, newExplored, newVisible);
    }

    /**
     * Returns whether this tile is passable for ground units based on its terrain.
     *
     * @return true if the terrain is passable and the tile is not occupied
     */
    public boolean isPassable() {
        return terrain.isPassable() && !isOccupied();
    }
}
