package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Building;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Research commands by starting research projects at Technology Centres.
 * <p>
 * REF: combat_formulas.md - Research/Upgrade Effects
 * REF: complete_unit_stats.json - 48 research IDs (0-47)
 * REF: protocol_specification.md - Research command
 */
public final class ResearchCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchCommandHandler.class);

    /**
     * Handle a Research command.
     * Validates that the Tech Centre can research the project and starts it.
     *
     * @param cmd       the research command
     * @param entities  the entity manager
     * @param economy   the economy system
     * @param research  the research system
     */
    public void handle(CommandType.Research cmd, EntityManager entities,
                       EconomySystem economy, ResearchSystem research) {
        Building techCentre = entities.getBuilding(cmd.techCentreId());
        if (techCentre == null) {
            LOG.warn("Tech Centre {} not found", cmd.techCentreId());
            return;
        }

        // FIX (ANALYSIS_V2 P2): Ownership check — only the building's owner can research
        int ownerId = com.aow2.core.economy.EconomySystem.playerId(techCentre.getFaction());
        if (ownerId != cmd.playerId()) {
            LOG.warn("Player {} attempted to research at building {} owned by player {}",
                cmd.playerId(), cmd.techCentreId(), ownerId);
            return;
        }

        boolean started = research.startResearch(
            techCentre, cmd.researchId(), cmd.playerId(), economy);

        if (started) {
            LOG.info("Player {} started research {} at building {}",
                cmd.playerId(), cmd.researchId(), cmd.techCentreId());
        } else {
            LOG.debug("Player {} failed to start research {} at building {}",
                cmd.playerId(), cmd.researchId(), cmd.techCentreId());
        }
    }
}
