package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.entity.Building;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Produce commands by enqueuing unit production at buildings.
 * <p>
 * REF: combat_formulas.md - Production Time Formula
 * REF: complete_unit_stats.json - buildTime per unit type
 * REF: protocol_specification.md - Produce command
 */
public final class ProduceCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProduceCommandHandler.class);

    /**
     * Handle a Produce command.
     * Validates that the producer building can produce the unit type and enqueues.
     *
     * @param cmd        the produce command
     * @param entities   the entity manager
     * @param economy    the economy system
     * @param production the production system
     * @param research   the research system (for tech requirements)
     */
    public void handle(CommandType.Produce cmd, EntityManager entities,
                       EconomySystem economy, ProductionSystem production,
                       ResearchSystem research) {
        Building producer = entities.getBuilding(cmd.producerId());
        if (producer == null) {
            LOG.warn("Producer building {} not found", cmd.producerId());
            return;
        }

        // FIX (ANALYSIS_V2 P2): Ownership check — only the building's owner can produce from it
        int ownerId = com.aow2.core.economy.EconomySystem.playerId(producer.getFaction());
        if (ownerId != cmd.playerId()) {
            LOG.warn("Player {} attempted to produce from building {} owned by player {}",
                cmd.playerId(), cmd.producerId(), ownerId);
            return;
        }

        boolean enqueued = production.enqueueUnit(
            producer, cmd.unitType(), cmd.playerId(), economy, research);

        if (enqueued) {
            LOG.info("Player {} enqueued {} at building {}",
                cmd.playerId(), cmd.unitType().displayName(), cmd.producerId());
        } else {
            LOG.debug("Player {} failed to enqueue {} at building {}",
                cmd.playerId(), cmd.unitType().displayName(), cmd.producerId());
        }
    }
}
