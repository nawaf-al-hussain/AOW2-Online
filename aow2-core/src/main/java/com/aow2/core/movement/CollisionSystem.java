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
import java.util.List;

/**
 * Handles collision avoidance between units.
 * <p>
 * REF: pathfinding.md — unit_stats.md offset +1515 stuckCounter mechanism
 * REF: pathfinding.md — bW[y][x] occupancy grid tracks unit positions
 * REF: GameConstants.LARGE_UNIT_BITMASK — Fortress occupies 2 cells
 */
public final class CollisionSystem {

    private static final Logger LOG = LoggerFactory.getLogger(CollisionSystem.class);

    /** 8-directional deltas for pushing units apart. */
    private static final int[][] PUSH_DELTAS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    public CollisionSystem() {
        // Stateless system
    }

    /**
     * Resolve collisions between units that occupy the same cell.
     * Units push apart to adjacent cells if possible.
     * REF: pathfinding.md — units advance along path, blocked cells increment stuckCounter
     */
    public void resolveCollisions(EntityManager entities) {
        List<Unit> allUnits = entities.getAllUnits();
        List<Unit[]> collisionPairs = new ArrayList<>();

        // Detect collisions: find pairs of alive units on the same cell
        for (int i = 0; i < allUnits.size(); i++) {
            Unit a = allUnits.get(i);
            if (!a.isAlive()) continue;

            for (int j = i + 1; j < allUnits.size(); j++) {
                Unit b = allUnits.get(j);
                if (!b.isAlive()) continue;

                if (a.getPosition().equals(b.getPosition())) {
                    collisionPairs.add(new Unit[]{a, b});
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

        // Unit occupancy check
        for (Unit unit : entities.getAllUnits()) {
            if (!unit.isAlive()) continue;
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
        if (secondX > 127) {
            return false; // secondary cell out of bounds
        }
        if (!map.isPassable(secondX, pos.y())) {
            return false;
        }

        // Check for unit occupancy on both cells
        GridPosition secondCell = new GridPosition(secondX, pos.y());
        for (Unit unit : entities.getAllUnits()) {
            if (!unit.isAlive()) continue;

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
            for (Unit other : entities.getAllUnits()) {
                if (other.isAlive() && other.getPosition().equals(candidate)) {
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
}
