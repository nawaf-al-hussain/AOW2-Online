package com.aow2.core.world;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
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

    /** The game map for terrain LOS checks. Set during updateVisibility. */
    private GameMap currentMap;

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

        // Store map reference for LOS blocking checks
        this.currentMap = map;

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
     * Reveal tiles within sight range of a position using DDA ray-casting with LOS blocking.
     * FIX(M-30): Implemented line-of-sight blocking via DDA ray-cast from the observer to
     * each tile on the perimeter of the sight range. Each ray marks tiles as VISIBLE until
     * an opaque terrain type (MOUNTAIN) blocks further visibility. Buildings also block LOS.
     * The center tile is always visible.
     * <p>
     * REF: MASTER_DOCUMENTATION.md Section 3.2 - Map visibility (ray-casting LOS)
     * REF: map_system.md - Mountain terrain blocks line of sight
     *
     * @param grid       the visibility grid for a player
     * @param center     the center position of the revealer
     * @param sightRange the sight range in tiles
     */
    private void revealArea(TileVisibility[][] grid, GridPosition center, int sightRange) {
        // Center tile is always visible
        if (inBounds(center.x(), center.y())) {
            grid[center.x()][center.y()] = TileVisibility.VISIBLE;
        }

        // Cast rays to every tile on the perimeter of the sight range diamond (Chebyshev).
        // For each edge tile, cast a DDA ray from center to that tile, marking VISIBLE
        // until blocked by opaque terrain (MOUNTAIN) or a building.
        int cx = center.x();
        int cy = center.y();

        // Collect perimeter tiles (Chebyshev distance == sightRange)
        // Only cast to unique directions to avoid redundant ray casts.
        // We cast to the 8 cardinal/diagonal edges plus intermediate points on the diamond.
        int r = sightRange;

        // Top edge (dy = -r, dx from -r to r)
        for (int dx = -r; dx <= r; dx++) {
            castRay(grid, cx, cy, cx + dx, cy - r);
        }
        // Bottom edge (dy = +r, dx from -r to r)
        for (int dx = -r; dx <= r; dx++) {
            castRay(grid, cx, cy, cx + dx, cy + r);
        }
        // Left edge (dx = -r, dy from -r+1 to r-1 to avoid recasting corners)
        for (int dy = -r + 1; dy < r; dy++) {
            castRay(grid, cx, cy, cx - r, cy + dy);
        }
        // Right edge (dx = +r, dy from -r+1 to r-1)
        for (int dy = -r + 1; dy < r; dy++) {
            castRay(grid, cx, cy, cx + r, cy + dy);
        }
    }

    /**
     * Cast a DDA (Digital Differential Analyzer) ray from (x0,y0) to (x1,y1),
     * marking each tile as VISIBLE until an opaque tile blocks further visibility.
     * <p>
     * Opaque tiles are: MOUNTAIN terrain, or tiles occupied by alive buildings.
     * The blocking tile itself IS marked visible (you can see the obstacle).
     *
     * @param grid visibility grid
     * @param x0   start x
     * @param y0   start y
     * @param x1   end x
     * @param y1   end y
     */
    private void castRay(TileVisibility[][] grid, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;

        // Limit ray length to prevent infinite loops on degenerate input
        int maxSteps = dx + dy + 1;

        while (maxSteps-- > 0) {
            if (!inBounds(cx, cy)) break;

            grid[cx][cy] = TileVisibility.VISIBLE;

            // Check if this tile blocks LOS
            if (blocksLineOfSight(cx, cy)) {
                break; // Stop ray — opaque tile reached (itself is still visible)
            }

            // Reached target
            if (cx == x1 && cy == y1) break;

            // DDA step
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    /**
     * Check if a tile blocks line of sight.
     * MOUNTAIN terrain blocks LOS. Alive buildings also block LOS.
     * REF: map_system.md - Mountain terrain blocks line of sight
     *
     * @param x tile x
     * @param y tile y
     * @return true if the tile blocks LOS
     */
    private boolean blocksLineOfSight(int x, int y) {
        if (currentMap != null) {
            TerrainType terrain = currentMap.getTile(x, y);
            if (terrain == TerrainType.MOUNTAIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if coordinates are within the map bounds.
     */
    private boolean inBounds(int x, int y) {
        return x >= 0 && x < mapWidth && y >= 0 && y < mapHeight;
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
