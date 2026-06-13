package com.aow2.core.movement;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Processes unit movement along computed paths.
 * <p>
 * REF: pathfinding.md — "Speed & Movement Formulas"
 * Units move at their speed stat, with animation cycles per step.
 * Each tick, units with a move target advance along their path.
 * When a unit is stuck (stuckCounter >= threshold), it re-pathfinds.
 */
public final class MovementSystem {

    private static final Logger LOG = LoggerFactory.getLogger(MovementSystem.class);

    private final PathfindingSystem pathfinding;
    private final CollisionSystem collision;

    /**
     * Constructs a MovementSystem with the given pathfinding and collision subsystems.
     *
     * @param pathfinding the pathfinding system for computing paths
     * @param collision   the collision system for checking cell availability
     */
    public MovementSystem(PathfindingSystem pathfinding, CollisionSystem collision) {
        this.pathfinding = pathfinding;
        this.collision = collision;
    }

    /**
     * Process all unit movements for one tick.
     * Each tick, units with a move target advance along their path.
     * REF: pathfinding.md — units advance along path each tick, speed determines ticks per cell
     *
     * @param entities the entity manager containing all units
     * @param map      the current game map
     */
    public void processTick(EntityManager entities, GameMap map) {
        List<Unit> allUnits = entities.getAllUnits();

        for (Unit unit : allUnits) {
            if (!unit.isAlive()) {
                continue;
            }

            if (unit.getMovementState() == MovementState.IDLE ||
                unit.getMovementState() == MovementState.ARRIVED) {
                continue;
            }

            processUnitMovement(unit, entities, map);
        }
    }

    /**
     * Process movement for a single unit for one tick.
     */
    private void processUnitMovement(Unit unit, EntityManager entities, GameMap map) {
        // Check if unit should stop because enemy is in attack range
        if (shouldStopForEnemy(unit, entities)) {
            unit.setMovementState(MovementState.ATTACKING);
            return;
        }

        // Handle stuck units
        if (unit.getMovementState() == MovementState.STUCK) {
            unit.setStuckCounter(unit.getStuckCounter() + 1);
            if (unit.getStuckCounter() >= GameConstants.STUCK_THRESHOLD) {
                // Re-pathfind
                LOG.debug("Unit {} stuck for {} ticks, re-pathfinding", unit.getId(), unit.getStuckCounter());
                if (unit.getTargetPosition() != null) {
                    List<GridPosition> newPath = pathfinding.findPath(
                        unit.getPosition(), unit.getTargetPosition(), map,
                        getOccupiedCells(entities, unit.getId()));
                    if (!newPath.isEmpty()) {
                        unit.setPath(newPath);
                        unit.setMovementState(MovementState.MOVING);
                        unit.setStuckCounter(0);
                    } else {
                        // No path available, give up
                        unit.clearPath();
                        LOG.debug("Unit {} cannot find path, clearing target", unit.getId());
                    }
                } else {
                    unit.clearPath();
                }
            }
            return;
        }

        // Unit is MOVING — advance along path
        if (unit.getMovementState() != MovementState.MOVING) {
            return;
        }

        // Speed check: unit moves one cell per (speed stat) ticks
        // REF: combat_formulas.md — speed stat determines movement rate
        // Higher speed = faster movement (fewer ticks per cell)
        // ASSUMPTION: speed stat maps inversely to ticks per cell. Speed 4 = move every 4 ticks.
        int ticksPerCell = Math.max(1, unit.getStats().speed());
        unit.setMoveTickAccumulator(unit.getMoveTickAccumulator() + 1);

        if (unit.getMoveTickAccumulator() < ticksPerCell) {
            return; // Not time to move yet
        }
        unit.setMoveTickAccumulator(0);

        // Get next waypoint
        GridPosition nextWaypoint = unit.getCurrentWaypoint();
        if (nextWaypoint == null) {
            // Path exhausted — arrived at destination
            unit.setMovementState(MovementState.ARRIVED);
            LOG.debug("Unit {} arrived at destination", unit.getId());
            return;
        }

        // Check if next cell is available
        if (!collision.isCellAvailable(nextWaypoint, map, entities, unit.getId())) {
            // Cell blocked — increment stuck counter
            unit.setStuckCounter(unit.getStuckCounter() + 1);
            if (unit.getStuckCounter() >= GameConstants.STUCK_THRESHOLD) {
                unit.setMovementState(MovementState.STUCK);
                LOG.debug("Unit {} is stuck at {}, counter={}", unit.getId(), unit.getPosition(),
                    unit.getStuckCounter());
            }
            return;
        }

        // Move unit to next waypoint
        unit.setPosition(nextWaypoint);
        unit.advancePath();
        unit.setStuckCounter(0);

        // Check if path is exhausted after advancing
        if (!unit.hasPathRemaining()) {
            unit.setMovementState(MovementState.ARRIVED);
            LOG.debug("Unit {} arrived at destination {}", unit.getId(), unit.getPosition());
        }
    }

    /**
     * Check if an enemy unit is within attack range, causing this unit to stop moving.
     * REF: pathfinding.md — units stop when enemy enters attack range
     *
     * @param unit     the moving unit
     * @param entities entity manager for finding enemy units
     * @return true if an enemy is in attack range
     */
    private boolean shouldStopForEnemy(Unit unit, EntityManager entities) {
        if (unit.getTargetUnitRef() == null) {
            return false;
        }

        // Check if the target unit still exists and is alive
        Unit target = entities.getUnit(unit.getTargetUnitRef());
        if (target == null || !target.isAlive()) {
            return false;
        }

        // Check if enemy is in attack range
        double distance = unit.getPosition().distanceTo(target.getPosition());
        return distance <= unit.getStats().attackRange();
    }

    /**
     * Issue a move command to a unit.
     * Computes path and sets unit's movement state.
     *
     * @param unit   the unit to move
     * @param target target grid position
     * @param map    the game map
     */
    public void issueMoveCommand(Unit unit, GridPosition target, GameMap map) {
        issueMoveCommand(unit, target, map, Collections.emptySet());
    }

    /**
     * Issue a move command to a unit with awareness of occupied cells.
     *
     * @param unit     the unit to move
     * @param target   target grid position
     * @param map      the game map
     * @param occupied set of occupied cells to avoid
     */
    public void issueMoveCommand(Unit unit, GridPosition target, GameMap map,
                                  Set<GridPosition> occupied) {
        if (!unit.isAlive()) {
            LOG.debug("Cannot issue move to dead unit {}", unit.getId());
            return;
        }

        if (!map.isPassable(target.x(), target.y())) {
            LOG.debug("Cannot move unit {} to impassable position {}", unit.getId(), target);
            return;
        }

        List<GridPosition> path = pathfinding.findPath(unit.getPosition(), target, map, occupied);

        if (path.isEmpty()) {
            // No path found — maybe target is adjacent or unreachable
            if (unit.getPosition().equals(target)) {
                // Already at target
                return;
            }
            LOG.debug("No path found for unit {} to {}", unit.getId(), target);
            unit.setTargetPosition(target);
            unit.setMovementState(MovementState.STUCK);
            unit.setStuckCounter(GameConstants.STUCK_THRESHOLD); // trigger immediate re-pathfind
            return;
        }

        unit.setTargetPosition(target);
        unit.setPath(path);
        unit.setMovementState(MovementState.MOVING);
        unit.setStuckCounter(0);
        unit.setMoveTickAccumulator(0);

        LOG.debug("Unit {} moving to {}, path length {}", unit.getId(), target, path.size());
    }

    /**
     * Issue a move command to a unit, computing occupied cells from the entity manager.
     *
     * @param unit     the unit to move
     * @param target   target grid position
     * @param map      the game map
     * @param entities entity manager for finding occupied cells
     */
    public void issueMoveCommand(Unit unit, GridPosition target, GameMap map,
                                  EntityManager entities) {
        Set<GridPosition> occupied = getOccupiedCells(entities, unit.getId());
        issueMoveCommand(unit, target, map, occupied);
    }

    /**
     * Issue a group move command with formation preservation.
     * Maintains relative positions between units in the group.
     * REF: pathfinding.md — original game supports group selection and move
     *
     * @param units  list of units in the group
     * @param center center target position for the group
     * @param map    the game map
     * @param entities entity manager for collision checks
     */
    public void issueGroupMoveCommand(List<Unit> units, GridPosition center, GameMap map,
                                       EntityManager entities) {
        if (units.isEmpty()) {
            return;
        }

        // Calculate the center of mass of the group
        int sumX = 0;
        int sumY = 0;
        int aliveCount = 0;
        for (Unit unit : units) {
            if (unit.isAlive()) {
                sumX += unit.getPosition().x();
                sumY += unit.getPosition().y();
                aliveCount++;
            }
        }

        if (aliveCount == 0) {
            return;
        }

        int centerOfMassX = sumX / aliveCount;
        int centerOfMassY = sumY / aliveCount;

        // Calculate offset from center of mass to target center
        int offsetX = center.x() - centerOfMassX;
        int offsetY = center.y() - centerOfMassY;

        // Collect all occupied cells by units not in this group
        Set<Integer> groupIds = new HashSet<>();
        for (Unit unit : units) {
            groupIds.add(unit.getId());
        }
        Set<GridPosition> occupied = new HashSet<>();
        for (Unit other : entities.getAllUnits()) {
            if (other.isAlive() && !groupIds.contains(other.getId())) {
                occupied.add(other.getPosition());
            }
        }

        // Issue move commands preserving relative positions
        Set<GridPosition> claimedTargets = new HashSet<>();
        for (Unit unit : units) {
            if (!unit.isAlive()) {
                continue;
            }

            // Calculate individual target preserving relative offset
            int relX = unit.getPosition().x() - centerOfMassX;
            int relY = unit.getPosition().y() - centerOfMassY;
            int targetX = Math.clamp(center.x() + relX, 0, map.getWidth() - 1);
            int targetY = Math.clamp(center.y() + relY, 0, map.getHeight() - 1);

            GridPosition individualTarget = new GridPosition(targetX, targetY);

            // If target is already claimed or impassable, find nearest available
            if (claimedTargets.contains(individualTarget) || !map.isPassable(targetX, targetY)) {
                individualTarget = findNearestAvailable(individualTarget, map, occupied,
                    claimedTargets, 5);
            }

            if (individualTarget != null) {
                claimedTargets.add(individualTarget);
                Set<GridPosition> pathOccupied = new HashSet<>(occupied);
                pathOccupied.addAll(claimedTargets);
                pathOccupied.remove(individualTarget); // allow pathfinding to our own target

                issueMoveCommand(unit, individualTarget, map, pathOccupied);
            }
        }
    }

    /**
     * Find the nearest available cell to the given position.
     * Searches in expanding rings around the preferred position.
     *
     * @param preferred   the preferred target position
     * @param map         the game map
     * @param occupied    set of occupied cells
     * @param claimed     set of already-claimed target cells
     * @param maxRadius   maximum search radius
     * @return the nearest available position, or null if none found within radius
     */
    private GridPosition findNearestAvailable(GridPosition preferred, GameMap map,
                                               Set<GridPosition> occupied,
                                               Set<GridPosition> claimed, int maxRadius) {
        if (map.isPassable(preferred.x(), preferred.y()) &&
            !occupied.contains(preferred) && !claimed.contains(preferred)) {
            return preferred;
        }

        // Search in expanding Chebyshev distance rings
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue; // Only consider the ring at distance r
                    }
                    int nx = preferred.x() + dx;
                    int ny = preferred.y() + dy;
                    if (nx < 0 || ny < 0 || nx > 127 || ny > 127) {
                        continue;
                    }
                    GridPosition candidate = new GridPosition(nx, ny);
                    if (map.isPassable(nx, ny) && !occupied.contains(candidate) &&
                        !claimed.contains(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get all grid positions occupied by alive units, excluding the given unit ID.
     *
     * @param entities      entity manager
     * @param excludeUnitId unit ID to exclude (the moving unit itself)
     * @return set of occupied grid positions
     */
    private Set<GridPosition> getOccupiedCells(EntityManager entities, int excludeUnitId) {
        Set<GridPosition> occupied = new HashSet<>();
        for (Unit other : entities.getAllUnits()) {
            if (other.isAlive() && other.getId() != excludeUnitId) {
                occupied.add(other.getPosition());
            }
        }
        return occupied;
    }
}
