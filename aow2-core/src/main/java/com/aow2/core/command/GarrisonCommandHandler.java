package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Garrison and Ungarrison commands.
 * <p>
 * Garrisons place units inside buildings (bunkers) for protection and
 * defensive attacks. Ungarrison removes them.
 * <p>
 * REF: combat_formulas.md - bunker garrison attack with range bonus
 * REF: protocol_specification.md - Garrison/Ungarrison commands
 * <p>
 * Garrison flow:
 * 1. Unit must be within 2 cells of the building to garrison (otherwise the
 *    caller should issue a move command first).
 * 2. When garrisoned, the unit's position is set to the building position,
 *    its path is cleared, and it is marked with garrisonedBuildingId.
 * 3. Garrisoned units are excluded from spatial queries and army strength
 *    calculations by EntityManager.
 * <p>
 * Ungarrison flow:
 * 1. A valid adjacent passable tile is found near the building.
 * 2. The unit's position is updated to that tile.
 * 3. The garrisonedBuildingId is cleared.
 */
public final class GarrisonCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GarrisonCommandHandler.class);

    /** Maximum distance (Chebyshev) a unit can be from the building to garrison. */
    private static final int GARRISON_RANGE = 2;

    /**
     * Handle a Garrison command.
     * Places the first specified unit inside the target building.
     * Only one unit can be garrisoned per building in the original game.
     * <p>
     * The unit must be within {@link #GARRISON_RANGE} cells of the building.
     * If not, the garrison is rejected (the caller should move the unit first).
     *
     * @param cmd       the garrison command
     * @param entities  the entity manager
     */
    public void handleGarrison(CommandType.Garrison cmd, EntityManager entities) {
        // FIX (ANALYSIS_V2 P2): Ownership check — only the owning player can garrison units
        for (int unitId : cmd.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                int ownerId = com.aow2.core.economy.EconomySystem.playerId(unit.getFaction());
                if (ownerId != cmd.playerId()) {
                    LOG.warn("Player {} attempted to garrison unit {} owned by player {}",
                        cmd.playerId(), unitId, ownerId);
                    return;
                }
            }
        }
        Building building = entities.getBuilding(cmd.buildingId());
        if (building == null || !building.isAlive()) {
            LOG.warn("Garrison target building {} not found or destroyed", cmd.buildingId());
            return;
        }

        if (building.getGarrisonedUnitRef() != null) {
            LOG.debug("Building {} already has a garrisoned unit", cmd.buildingId());
            return;
        }

        // Garrison the first alive, non-garrisoned unit from the list
        for (int unitId : cmd.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive() && !unit.isGarrisoned()) {
                // Check if the unit is within garrison range of the building
                double distance = unit.getPosition().distanceTo(building.getPosition());
                if (distance > GARRISON_RANGE) {
                    LOG.debug("Unit {} too far from building {} (distance={}), move closer first",
                        unitId, cmd.buildingId(), String.format("%.1f", distance));
                    continue;
                }

                // Perform garrison: hide the unit inside the building
                building.setGarrisonedUnitRef(unitId);
                unit.setPosition(building.getPosition());
                unit.setMovementState(MovementState.IDLE);
                unit.clearPath();
                unit.setGarrisonedBuildingId(cmd.buildingId());
                LOG.info("Unit {} garrisoned in building {} at {}",
                    unitId, cmd.buildingId(), building.getPosition());
                return;
            }
        }

        LOG.debug("No eligible units to garrison in building {}", cmd.buildingId());
    }

    /**
     * Handle an Ungarrison command.
     * Removes the garrisoned unit from the building and places it on an
     * adjacent passable tile.
     *
     * @param cmd       the ungarrison command
     * @param entities  the entity manager
     */
    public void handleUngarrison(CommandType.Ungarrison cmd, EntityManager entities) {
        handleUngarrison(cmd, entities, null);
    }

    /**
     * Handle an Ungarrison command with map context for passability checking.
     * Removes the garrisoned unit from the building and places it on an
     * adjacent passable tile.
     *
     * @param cmd       the ungarrison command
     * @param entities  the entity manager
     * @param map       the game map (may be null; if null, uses simple offset)
     */
    public void handleUngarrison(CommandType.Ungarrison cmd, EntityManager entities, GameMap map) {
        Building building = entities.getBuilding(cmd.buildingId());
        if (building == null || !building.isAlive()) {
            LOG.warn("Ungarrison building {} not found or destroyed", cmd.buildingId());
            return;
        }

        Integer garrisonedId = building.getGarrisonedUnitRef();
        if (garrisonedId == null) {
            LOG.debug("Building {} has no garrisoned unit", cmd.buildingId());
            return;
        }

        Unit unit = entities.getUnit(garrisonedId);
        if (unit != null && unit.isAlive()) {
            // Find a valid adjacent spawn position
            GridPosition spawnPos = findSpawnPosition(building.getPosition(), entities, map);
            unit.setPosition(spawnPos);
            unit.clearPath();
            unit.setGarrisonedBuildingId(null);
            LOG.info("Unit {} ungarrisoned from building {} at {}",
                garrisonedId, cmd.buildingId(), spawnPos);
        } else {
            // Unit is dead or missing — just clear the building ref
            LOG.warn("Garrisoned unit {} in building {} is dead or missing", garrisonedId, cmd.buildingId());
        }

        building.setGarrisonedUnitRef(null);
    }

    /**
     * Find a valid spawn position adjacent to the building.
     * Searches in a spiral pattern around the building for the first
     * passable, unoccupied tile.
     *
     * @param buildingPos the building position
     * @param entities    the entity manager (to check for unit occupation)
     * @param map         the game map (may be null; if null, only checks bounds)
     * @return a valid spawn position
     */
    private GridPosition findSpawnPosition(GridPosition buildingPos,
                                            EntityManager entities, GameMap map) {
        // Search in expanding rings around the building, starting at distance 1
        int[] dxOffsets = {1, 0, -1, 0, 1, 1, -1, -1};
        int[] dyOffsets = {0, 1, 0, -1, 1, -1, 1, -1};

        // Check adjacent cells (distance 1)
        for (int i = 0; i < dxOffsets.length; i++) {
            int nx = buildingPos.x() + dxOffsets[i];
            int ny = buildingPos.y() + dyOffsets[i];
            if (isValidSpawn(nx, ny, entities, map)) {
                return new GridPosition(nx, ny);
            }
        }

        // Check distance 2 if no adjacent cell found
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) continue; // already checked
                int nx = buildingPos.x() + dx;
                int ny = buildingPos.y() + dy;
                if (isValidSpawn(nx, ny, entities, map)) {
                    return new GridPosition(nx, ny);
                }
            }
        }

        // Fallback: just offset by (1,0) — the original behavior
        LOG.warn("No valid spawn position found near building at {}, using offset (1,0)", buildingPos);
        return buildingPos.offset(1, 0);
    }

    /**
     * Check if a position is valid for spawning a unit.
     *
     * @param x      the x coordinate
     * @param y      the y coordinate
     * @param entities the entity manager
     * @param map     the game map (may be null)
     * @return true if the position is valid for spawning
     */
    private boolean isValidSpawn(int x, int y, EntityManager entities, GameMap map) {
        // Check bounds (GridPosition will throw if out of 0-127 range)
        if (x < 0 || x > 127 || y < 0 || y > 127) {
            return false;
        }
        // Check map passability if map is available
        if (map != null && !map.isPassable(x, y)) {
            return false;
        }
        // Check if another unit occupies this position
        GridPosition pos = new GridPosition(x, y);
        if (entities.findUnitAt(pos) != null) {
            return false;
        }
        return true;
    }
}
