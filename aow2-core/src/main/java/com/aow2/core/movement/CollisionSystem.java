package com.aow2.core.movement;

import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles collision avoidance between units using a spatial hash grid
 * for efficient O(n) broad-phase collision detection.
 * <p>
 * REF: pathfinding.md — unit_stats.md offset +1515 stuckCounter mechanism
 * REF: pathfinding.md — bW[y][x] occupancy grid tracks unit positions
 * REF: GameConstants.LARGE_UNIT_BITMASK — Fortress occupies 2 cells
 * <p>
 * Performance: The spatial hash grid partitions the map into cells of
 * {@link SpatialHashGrid#CELL_SIZE} tiles. Collision checks only consider
 * units in the same or adjacent cells, reducing broad-phase from O(n²) to
 * approximately O(n · k) where k is the average number of units per cell
 * neighborhood (typically much smaller than n).
 */
public final class CollisionSystem {

    private static final Logger LOG = LoggerFactory.getLogger(CollisionSystem.class);

    /** 8-directional deltas for pushing units apart. */
    private static final int[][] PUSH_DELTAS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** Spatial hash grid for efficient collision broad-phase. */
    private final SpatialHashGrid spatialGrid = new SpatialHashGrid();

    public CollisionSystem() {
        // Spatial grid is rebuilt each tick as needed
    }

    /**
     * Resolve collisions between units that occupy the same cell.
     * Units push apart to adjacent cells if possible.
     * Uses a spatial hash grid for efficient broad-phase collision detection.
     * REF: pathfinding.md — units advance along path, blocked cells increment stuckCounter
     *
     * @param entities the entity manager containing all units
     */
    public void resolveCollisions(EntityManager entities) {
        // Rebuild spatial grid for current unit positions
        spatialGrid.clear();
        for (Unit unit : entities.getAllUnits()) {
            if (unit.isAlive() && !unit.isGarrisoned()) {
                spatialGrid.insert(unit);
            }
        }

        List<Unit> allUnits = entities.getAllUnits();
        List<Unit[]> collisionPairs = new ArrayList<>();
        Set<Long> checkedPairs = new HashSet<>();

        // Detect collisions using spatial grid: for each unit, only check nearby units
        for (Unit a : allUnits) {
            if (!a.isAlive() || a.isGarrisoned()) continue;

            List<Unit> nearby = spatialGrid.getNearby(a.getPosition());
            for (Unit b : nearby) {
                if (b.getId() <= a.getId()) continue; // avoid duplicate pairs and self-check
                if (!b.isAlive() || b.isGarrisoned()) continue;

                if (a.getPosition().equals(b.getPosition())) {
                    // Create a unique key for this pair to avoid duplicates
                    long pairKey = ((long) a.getId() << 32) | (b.getId() & 0xFFFFFFFFL);
                    if (checkedPairs.add(pairKey)) {
                        collisionPairs.add(new Unit[]{a, b});
                    }
                }
            }
        }

        // Resolve each collision by pushing one unit to an adjacent cell
        for (Unit[] pair : collisionPairs) {
            Unit a = pair[0];
            Unit b = pair[1];

            // Prefer to push the unit that is not moving (or has lower priority)
            Unit pushUnit = selectPushCandidate(a, b);
            Unit stayUnit = (pushUnit == a) ? b : a;

            GridPosition pushTarget = findPushTarget(pushUnit, stayUnit, entities);
            if (pushTarget != null) {
                pushUnit.setPosition(pushTarget);
                LOG.debug("Pushed unit {} from collision with {} to {}",
                    pushUnit.getId(), stayUnit.getId(), pushTarget);
            } else {
                // No available adjacent cell — mark unit as stuck
                pushUnit.setStuckCounter(pushUnit.getStuckCounter() + 1);
                pushUnit.setMovementState(MovementState.STUCK);
                LOG.debug("Unit {} has no push target, marked as stuck", pushUnit.getId());
            }
        }
    }

    /**
     * Check if a cell is available for movement.
     * Considers terrain, buildings, and other units.
     * Uses the spatial hash grid if it has been built; otherwise falls back to brute-force.
     * REF: pathfinding.md — isPassable checks bW[y][x] for unit occupancy and terrain type
     *
     * @param pos           grid position to check
     * @param map           the game map
     * @param entities      entity manager for checking unit/building occupancy
     * @param excludeUnitId unit ID to exclude from occupancy check (the moving unit)
     * @return true if the cell is available for movement
     */
    public boolean isCellAvailable(GridPosition pos, GameMap map, EntityManager entities,
                                    int excludeUnitId) {
        // Terrain check
        if (!map.isInBounds(pos.x(), pos.y())) {
            return false;
        }
        if (!map.isPassable(pos.x(), pos.y())) {
            return false;
        }

        // Unit occupancy check — use spatial grid if available, otherwise brute-force
        List<Unit> candidates = spatialGrid.isEmpty()
            ? entities.getAllUnits()
            : spatialGrid.getNearby(pos);

        for (Unit unit : candidates) {
            if (!unit.isAlive() || unit.isGarrisoned()) continue;
            if (unit.getId() == excludeUnitId) continue;

            if (unit.getPosition().equals(pos)) {
                return false;
            }

            // Large unit occupies an additional cell
            // REF: GameConstants.LARGE_UNIT_BITMASK — Fortress occupies 2 cells
            if (unit.isLargeUnit()) {
                GridPosition secondCell = new GridPosition(
                    Math.min(unit.getPosition().x() + 1, 127),
                    unit.getPosition().y()
                );
                if (secondCell.equals(pos)) {
                    return false;
                }
            }
        }

        // Building occupancy check
        Building building = entities.findBuildingAt(pos);
        if (building != null && building.isAlive()) {
            // ASSUMPTION: Buildings block movement unless they're friendly and garrisonable
            // For now, all alive buildings block ground movement
            return false;
        }

        return true;
    }

    /**
     * Handle large unit collision (Fortress occupies 2 cells).
     * REF: GameConstants.LARGE_UNIT_BITMASK — bitmask 65536 for 2-cell collision units
     * Large units occupy their primary cell and the cell to the right (x+1).
     * Uses the spatial hash grid if it has been built; otherwise falls back to brute-force.
     *
     * @param pos      primary position for the large unit
     * @param map      the game map
     * @param entities entity manager for checking occupancy
     * @return true if both cells required by the large unit are available
     */
    public boolean isLargeUnitPlacementValid(GridPosition pos, GameMap map,
                                              EntityManager entities) {
        // Check primary cell
        if (!map.isInBounds(pos.x(), pos.y()) || !map.isPassable(pos.x(), pos.y())) {
            return false;
        }

        // Check secondary cell (x+1, same y)
        int secondX = pos.x() + 1;
        if (!map.isInBounds(secondX, pos.y())) {
            return false; // secondary cell out of bounds
        }
        if (!map.isPassable(secondX, pos.y())) {
            return false;
        }

        // Check for unit occupancy on both cells
        GridPosition secondCell = new GridPosition(secondX, pos.y());
        List<Unit> candidates = spatialGrid.isEmpty()
            ? entities.getAllUnits()
            : spatialGrid.getNearby(pos);

        for (Unit unit : candidates) {
            if (!unit.isAlive() || unit.isGarrisoned()) continue;

            if (unit.getPosition().equals(pos) || unit.getPosition().equals(secondCell)) {
                return false;
            }

            // If other unit is also large, check its secondary cell too
            if (unit.isLargeUnit()) {
                GridPosition otherSecond = new GridPosition(
                    Math.min(unit.getPosition().x() + 1, 127),
                    unit.getPosition().y()
                );
                if (otherSecond.equals(pos) || otherSecond.equals(secondCell)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Rebuild the spatial hash grid from the current unit positions.
     * Call this once at the start of a tick before other collision queries,
     * so that {@link #isCellAvailable} and {@link #isLargeUnitPlacementValid}
     * can benefit from spatial acceleration.
     *
     * @param entities the entity manager containing all units
     */
    public void rebuildSpatialGrid(EntityManager entities) {
        spatialGrid.clear();
        for (Unit unit : entities.getAllUnits()) {
            if (unit.isAlive() && !unit.isGarrisoned()) {
                spatialGrid.insert(unit);
            }
        }
    }

    /**
     * Select which unit to push when resolving a collision.
     * Priority: push the idle unit over a moving one, or the one with lower ID.
     *
     * @param a first unit in collision
     * @param b second unit in collision
     * @return the unit that should be pushed
     */
    private Unit selectPushCandidate(Unit a, Unit b) {
        // Push idle units over moving ones
        if (a.getMovementState() == MovementState.IDLE &&
            b.getMovementState() != MovementState.IDLE) {
            return a;
        }
        if (b.getMovementState() == MovementState.IDLE &&
            a.getMovementState() != MovementState.IDLE) {
            return b;
        }
        // Both same state: push the one with the higher ID (later-created unit)
        return a.getId() > b.getId() ? a : b;
    }

    /**
     * Find an adjacent cell to push a unit to, preferring cells near the staying unit.
     * Uses the spatial hash grid if available for occupancy checks.
     *
     * @param pushUnit the unit being pushed
     * @param stayUnit the unit staying in place
     * @param entities entity manager for occupancy checks
     * @return an available adjacent position, or null if none found
     */
    private GridPosition findPushTarget(Unit pushUnit, Unit stayUnit, EntityManager entities) {
        GridPosition current = pushUnit.getPosition();

        for (int[] delta : PUSH_DELTAS) {
            int nx = current.x() + delta[0];
            int ny = current.y() + delta[1];

            // Bounds check
            if (nx < 0 || ny < 0 || nx > 127 || ny > 127) {
                continue;
            }

            GridPosition candidate = new GridPosition(nx, ny);

            // Don't push to the stay unit's position
            if (candidate.equals(stayUnit.getPosition())) {
                continue;
            }

            // Check if any alive unit occupies this cell
            boolean occupied = false;
            List<Unit> nearby = spatialGrid.isEmpty()
                ? entities.getAllUnits()
                : spatialGrid.getNearby(candidate);

            for (Unit other : nearby) {
                if (other.isAlive() && !other.isGarrisoned() && other.getPosition().equals(candidate)) {
                    occupied = true;
                    break;
                }
            }

            if (!occupied) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Spatial hash grid for efficient broad-phase collision detection.
     * <p>
     * Partitions the map into cells of {@link #CELL_SIZE} tiles. Units are inserted
     * into cells based on their position. Collision queries only check units in the
     * same or 8 adjacent cells, reducing the broad-phase from O(n²) to O(n · k)
     * where k is the average number of units per cell neighborhood.
     * <p>
     * Cell keys are packed as: {@code ((x / cellSize) << 32) | (y / cellSize & 0xFFFFFFFFL)}
     */
    static final class SpatialHashGrid {

        /** Size of each spatial hash cell in tiles. Configurable; 8 tiles per cell. */
        static final int CELL_SIZE = 8;

        /** Grid cells mapped by packed cell coordinate key. */
        private final Map<Long, List<Unit>> cells = new HashMap<>();

        /** Whether the grid contains any entries. */
        private boolean empty = true;

        /**
         * Clears all entries from the grid. Must be called before re-populating
         * each tick or collision resolution pass.
         */
        void clear() {
            cells.clear();
            empty = true;
        }

        /**
         * Inserts a unit into the grid cell corresponding to its position.
         *
         * @param unit the unit to insert
         */
        void insert(Unit unit) {
            long key = cellKey(unit.getPosition().x(), unit.getPosition().y());
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
            empty = false;
        }

        /**
         * Returns all units in the same cell and 8 adjacent cells as the given position.
         * This is the broad-phase query: only these units need to be checked for collision.
         *
         * @param pos the grid position to query around
         * @return list of units in the same and adjacent cells (may contain duplicates
         *         if a unit appears in multiple adjacent cell lists)
         */
        List<Unit> getNearby(GridPosition pos) {
            List<Unit> result = new ArrayList<>();
            int cellX = pos.x() / CELL_SIZE;
            int cellY = pos.y() / CELL_SIZE;

            // Check same cell + 8 adjacent cells
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    long key = packKey(cellX + dx, cellY + dy);
                    List<Unit> cellUnits = cells.get(key);
                    if (cellUnits != null) {
                        result.addAll(cellUnits);
                    }
                }
            }

            return result;
        }

        /**
         * Returns whether the grid is empty (no units inserted since last clear).
         *
         * @return true if the grid has no entries
         */
        boolean isEmpty() {
            return empty;
        }

        /**
         * Computes the packed cell key for a world coordinate.
         *
         * @param x world x coordinate
         * @param y world y coordinate
         * @return packed cell key
         */
        private static long cellKey(int x, int y) {
            return packKey(x / CELL_SIZE, y / CELL_SIZE);
        }

        /**
         * Packs a cell coordinate into a single long key.
         * Key format: {@code (cellX << 32) | (cellY & 0xFFFFFFFFL)}
         *
         * @param cellX cell x index
         * @param cellY cell y index
         * @return packed key
         */
        private static long packKey(int cellX, int cellY) {
            return ((long) cellX << 32) | (cellY & 0xFFFFFFFFL);
        }
    }
}
