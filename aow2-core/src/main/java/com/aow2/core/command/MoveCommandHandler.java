package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.entity.Unit;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Move commands by issuing move orders to units.
 * <p>
 * REF: pathfinding.md — movement and pathfinding system
 * REF: protocol_specification.md - Move command
 */
public final class MoveCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MoveCommandHandler.class);

    /**
     * Handle a Move command.
     * Issues move orders to all specified units toward the target position.
     * If multiple units are selected, uses group move for formation preservation.
     *
     * @param cmd       the move command
     * @param entities  the entity manager
     * @param map       the game map
     * @param movement  the movement system
     */
    public void handle(CommandType.Move cmd, EntityManager entities,
                       GameMap map, MovementSystem movement) {
        if (cmd.unitIds().length == 1) {
            Unit unit = entities.getUnit(cmd.unitIds()[0]);
            if (unit != null && unit.isAlive()) {
                movement.issueMoveCommand(unit, cmd.target(), map, entities);
                LOG.debug("Unit {} moving to {}", cmd.unitIds()[0], cmd.target());
            }
        } else if (cmd.unitIds().length > 1) {
            // Collect alive units from IDs
            java.util.List<Unit> units = new java.util.ArrayList<>();
            for (int unitId : cmd.unitIds()) {
                Unit unit = entities.getUnit(unitId);
                if (unit != null && unit.isAlive()) {
                    units.add(unit);
                }
            }
            if (!units.isEmpty()) {
                movement.issueGroupMoveCommand(units, cmd.target(), map, entities);
                LOG.debug("{} units moving to {}", units.size(), cmd.target());
            }
        }
    }
}
