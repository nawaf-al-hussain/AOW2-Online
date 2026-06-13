package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Build commands by validating and placing buildings.
 * <p>
 * REF: complete_building_stats.json - building placement rules and costs
 * REF: protocol_specification.md - Build command
 */
public final class BuildCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BuildCommandHandler.class);

    /**
     * Handle a Build command.
     * Validates placement and deducts credits if successful.
     *
     * @param cmd       the build command
     * @param entities  the entity manager
     * @param map       the game map
     * @param economy   the economy system
     * @param placement the building placement system
     */
    public void handle(CommandType.Build cmd, EntityManager entities,
                       GameMap map, EconomySystem economy,
                       BuildingPlacementSystem placement) {
        var building = placement.placeBuilding(
            cmd.buildingType(), cmd.position(), cmd.playerId(),
            entities, map, economy);

        if (building != null) {
            LOG.info("Player {} built {} at {}", cmd.playerId(),
                cmd.buildingType().displayName(), cmd.position());
        } else {
            LOG.debug("Player {} failed to build {} at {}", cmd.playerId(),
                cmd.buildingType().displayName(), cmd.position());
        }
    }
}
