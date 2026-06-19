package com.aow2.core.world;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Fog of war system controlling tile visibility per player.
 * Tiles can be: UNEXPLORED (never seen), EXPLORED (previously seen, now dark), VISIBLE (currently in sight).
 * Units and buildings have sight ranges that reveal tiles.
 * <p>
 * REF: unit_stats.md - sightRange per unit type
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Map visibility
 */
public final class FogOfWarSystem {

    private static final Logger LOG = LoggerFactory.getLogger(FogOfWarSystem.class);

    /** Maximum players supported. */
    private static final int MAX_PLAYERS = GameConstants.MAX_PLAYERS_PER_MATCH;

    /**
     * Tile visibility states.
     * UNEXPLORED: never seen by this player (rendered as solid black).
     * EXPLORED: previously seen but not currently visible (dimmed terrain visible).
     * VISIBLE: currently in line of sight of a unit or building.
     */
    public enum TileVisibility { UNEXPLORED, EXPLORED, VISIBLE }

    /** Per-player visibility grid: playerId -> (x,y) -> TileVisibility. */
    private final Map<Integer, TileVisibility[][]> visibilityGrids;

    /** Per-player reference to the map dimensions. */
    private int mapWidth;
    private int mapHeight;

    /**
     * Constructs a FogOfWarSystem.
     * Visibility grids are initialized lazily when {@link #initialize(GameMap)} is called.
     */
    public FogOfWarSystem() {
        this.visibilityGrids = new HashMap<>();
        this.mapWidth = 0;
        this.mapHeight = 0;
    }

    /**
     * Initialize the fog of war system with map dimensions.
     * All tiles start as UNEXPLORED.
     *
     * @param map the game map
     */
    public void initialize(GameMap map) {
        this.mapWidth = map.getWidth();
        this.mapHeight = map.getHeight();
        visibilityGrids.clear();

        for (int playerId = 0; playerId < MAX_PLAYERS; playerId++) {
            TileVisibility[][] grid = new TileVisibility[mapWidth][mapHeight];
            for (int x = 0; x < mapWidth; x++) {
                for (int y = 0; y < mapHeight; y++) {
                    grid[x][y] = TileVisibility.UNEXPLORED;
                }
            }
            visibilityGrids.put(playerId, grid);
        }

        LOG.info("FogOfWarSystem initialized for {}x{} map", mapWidth, mapHeight);
    }

    /**
     * Update visibility for a player based on their units' and buildings' sight ranges.
     * Previously VISIBLE tiles become EXPLORED. Tiles within sight range become VISIBLE.
     * <p>
     * REF: unit_stats.md - sightRange per unit type
     * REF: complete_building_stats.json - sightRange per building type
     *
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @param map      the game map
     */
    public void updateVisibility(int playerId, EntityManager entities, GameMap map) {
        if (mapWidth == 0 || mapHeight == 0) {
            initialize(map);
        }

        TileVisibility[][] grid = visibilityGrids.get(playerId);
        if (grid == null) {
            return;
        }

        // Step 1: Downgrade all VISIBLE tiles to EXPLORED
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                if (grid[x][y] == TileVisibility.VISIBLE) {
                    grid[x][y] = TileVisibility.EXPLORED;
                }
            }
        }

        // Step 2: Reveal tiles around each alive unit
        Faction faction = EconomySystem.playerFaction(playerId);
        for (Unit unit : entities.getAliveUnitsForPlayer(faction)) {
            revealArea(grid, unit.getPosition(), unit.getStats().sightRange());
        }

        // Step 3: Reveal tiles around each alive building
        for (Building building : entities.getBuildingsForPlayer(faction)) {
            if (building.isAlive() && !building.isUnderConstruction()) {
                int sightRange = building.getStats().sightRange();
                revealArea(grid, building.getPosition(), sightRange);
            }
        }
    }

    /**
     * Get visibility state of a tile for a player.
     *
     * @param playerId the player ID (0 or 1)
     * @param pos      the grid position
     * @return the tile visibility state, or UNEXPLORED if out of bounds
     */
    public TileVisibility getVisibility(int playerId, GridPosition pos) {
        TileVisibility[][] grid = visibilityGrids.get(playerId);
        if (grid == null) {
            return TileVisibility.UNEXPLORED;
        }
        if (pos.x() < 0 || pos.x() >= mapWidth || pos.y() < 0 || pos.y() >= mapHeight) {
            return TileVisibility.UNEXPLORED;
        }
        return grid[pos.x()][pos.y()];
    }

    /**
     * Check if a tile is currently visible to a player.
     *
     * @param playerId the player ID (0 or 1)
     * @param pos      the grid position
     * @return true if the tile is VISIBLE
     */
    public boolean isVisible(int playerId, GridPosition pos) {
        return getVisibility(playerId, pos) == TileVisibility.VISIBLE;
    }

    /**
     * Check if a tile has ever been seen by a player.
     *
     * @param playerId the player ID (0 or 1)
     * @param pos      the grid position
     * @return true if the tile is EXPLORED or VISIBLE
     */
    public boolean isExplored(int playerId, GridPosition pos) {
        TileVisibility vis = getVisibility(playerId, pos);
        return vis == TileVisibility.EXPLORED || vis == TileVisibility.VISIBLE;
    }

    /**
     * Reveal tiles within sight range of a position using Chebyshev distance.
     * This matches the original game's circular reveal pattern.
     *
     * TODO(M-30): The current implementation reveals all tiles within Chebyshev distance
     * without any line-of-sight (LOS) blocking. The original game uses ray-casting to
     * block visibility behind terrain obstacles (mountains, buildings, etc.). This should
     * be implemented using a Bresenham or DDA ray-cast from the observer to each tile
     * on the perimeter of the sight range, marking tiles along each ray as VISIBLE
     * until an opaque terrain type (MOUNTAIN, BUILDING) blocks further visibility.
     * Without this, players can see through mountains and buildings.
     *
     * @param grid       the visibility grid for a player
     * @param center     the center position of the revealer
     * @param sightRange the sight range in tiles
     */
    private void revealArea(TileVisibility[][] grid, GridPosition center, int sightRange) {
        int minX = Math.max(0, center.x() - sightRange);
        int maxX = Math.min(mapWidth - 1, center.x() + sightRange);
        int minY = Math.max(0, center.y() - sightRange);
        int maxY = Math.min(mapHeight - 1, center.y() + sightRange);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                // Chebyshev distance for square reveal pattern (matches original game)
                int dist = Math.max(Math.abs(x - center.x()), Math.abs(y - center.y()));
                if (dist <= sightRange) {
                    grid[x][y] = TileVisibility.VISIBLE;
                }
            }
        }
    }

    /**
     * Reset all visibility for a player (e.g., when starting a new game).
     *
     * @param playerId the player ID (0 or 1)
     */
    public void reset(int playerId) {
        TileVisibility[][] grid = visibilityGrids.get(playerId);
        if (grid != null) {
            for (int x = 0; x < mapWidth; x++) {
                for (int y = 0; y < mapHeight; y++) {
                    grid[x][y] = TileVisibility.UNEXPLORED;
                }
            }
        }
    }

    /**
     * Get the map width.
     *
     * @return map width
     */
    public int getMapWidth() {
        return mapWidth;
    }

    /**
     * Get the map height.
     *
     * @return map height
     */
    public int getMapHeight() {
        return mapHeight;
    }
}
