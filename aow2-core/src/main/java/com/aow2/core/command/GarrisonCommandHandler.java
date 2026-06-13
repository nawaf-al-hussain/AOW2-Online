package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.MovementState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;

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
 */
public final class GarrisonCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GarrisonCommandHandler.class);

    /**
     * Handle a Garrison command.
     * Places the first specified unit inside the target building.
     * Only one unit can be garrisoned per building in the original game.
     *
     * @param cmd       the garrison command
     * @param entities  the entity manager
     */
    public void handleGarrison(CommandType.Garrison cmd, EntityManager entities) {
        Building building = entities.getBuilding(cmd.buildingId());
        if (building == null || !building.isAlive()) {
            LOG.warn("Garrison target building {} not found or destroyed", cmd.buildingId());
            return;
        }

        if (building.getGarrisonedUnitRef() != null) {
            LOG.debug("Building {} already has a garrisoned unit", cmd.buildingId());
            return;
        }

        // Garrison the first alive unit from the list
        for (int unitId : cmd.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                building.setGarrisonedUnitRef(unitId);
                // Move unit off the map conceptually (hide it)
                unit.setMovementState(MovementState.IDLE);
                unit.clearPath();
                LOG.info("Unit {} garrisoned in building {}", unitId, cmd.buildingId());
                return;
            }
        }

        LOG.debug("No alive units to garrison in building {}", cmd.buildingId());
    }

    /**
     * Handle an Ungarrison command.
     * Removes the garrisoned unit from the building and places it adjacent.
     *
     * @param cmd       the ungarrison command
     * @param entities  the entity manager
     */
    public void handleUngarrison(CommandType.Ungarrison cmd, EntityManager entities) {
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
            // Place unit adjacent to the building
            var spawnPos = building.getPosition().offset(1, 0);
            unit.clearPath();
            LOG.info("Unit {} ungarrisoned from building {} at {}",
                garrisonedId, cmd.buildingId(), spawnPos);
        }

        building.setGarrisonedUnitRef(null);
    }
}
