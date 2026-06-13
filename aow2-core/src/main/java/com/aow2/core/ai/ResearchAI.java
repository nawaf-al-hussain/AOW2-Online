package com.aow2.core.ai;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Building;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.research.TechTree;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AI research decision-making: which technologies to prioritize.
 * <p>
 * REF: ai_analysis.md — AI research priorities vary by difficulty and game phase.
 * The original AI uses the Technology Centre to research upgrades following
 * prerequisite chains. Research IDs 0-7 are Confederation, 8-14 are Resistance.
 * <p>
 * Research priority by game phase:
 * - Early: infantry upgrades (armour, speed) — research IDs 0 (Confed) / 8 (Rebel)
 * - Mid: vehicle upgrades (damage, armour) — research IDs 3-4 (Confed) / 11-12 (Rebel)
 * - Late: special upgrades (siege mode, economy) — research IDs 6-7 (Confed) / 14 (Rebel)
 */
public final class ResearchAI {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchAI.class);

    /** Confederation early-game research priority (infantry armour). REF: combat_formulas.md ID 0 */
    private static final int CONFED_EARLY_RESEARCH = 0;

    /** Confederation mid-game research priority (damage). REF: combat_formulas.md ID 3 */
    private static final int CONFED_MID_RESEARCH = 3;

    /** Confederation late-game research priority (vehicle propulsion). REF: combat_formulas.md ID 7 */
    private static final int CONFED_LATE_RESEARCH = 7;

    /** Resistance early-game research priority (titanium jacket). REF: combat_formulas.md ID 24 */
    private static final int REBEL_EARLY_RESEARCH = 24;

    /** Resistance mid-game research priority (sniper upgrade). REF: combat_formulas.md ID 30 */
    private static final int REBEL_MID_RESEARCH = 30;

    /** Resistance late-game research priority (siege artillery). REF: combat_formulas.md ID 38 */
    private static final int REBEL_LATE_RESEARCH = 38;

    /**
     * Decide which research to start next.
     * <p>
     * Priority depends on game phase:
     * - Early: infantry upgrades (armour, speed)
     * - Mid: vehicle upgrades (damage, armour)
     * - Late: special upgrades (siege mode, economy)
     * <p>
     * REF: ai_analysis.md — AI research priorities vary by difficulty
     *
     * @param entities the entity manager
     * @param research the research system
     * @param techTree the technology tree definition
     * @param playerId the AI player ID (0 or 1)
     * @return the research ID to start, or -1 if none available
     */
    public int decideNextResearch(EntityManager entities, ResearchSystem research,
                                  TechTree techTree, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        GamePhase phase = getGamePhase(entities, -1, playerId);

        // Check if the player has a Technology Centre
        Building techCentre = findAvailableTechCentre(entities, playerId);
        if (techCentre == null) {
            LOG.debug("Player {} has no available Tech Centre for research", playerId);
            return -1;
        }

        // Get all tech nodes for this faction
        List<TechTree.ResearchNode> nodes = techTree.getTechNodes(faction);

        // Phase-based priority research IDs
        int[] priorityResearchIds = getPhasePriorityResearch(faction, phase);

        // Try each priority research in order
        for (int researchId : priorityResearchIds) {
            if (canStartResearch(researchId, playerId, research, techTree, entities)) {
                LOG.debug("Player {} should research {} (phase={})", playerId, researchId, phase);
                return researchId;
            }
        }

        // Fall back to any available research not yet completed
        for (TechTree.ResearchNode node : nodes) {
            if (canStartResearch(node.id(), playerId, research, techTree, entities)) {
                LOG.debug("Player {} fallback research {} (phase={})", playerId, node.id(), phase);
                return node.id();
            }
        }

        LOG.debug("Player {} has no research available", playerId);
        return -1;
    }

    /**
     * Get the game phase based on building state and tick count.
     * <p>
     * Phase is determined by:
     * - Tick count (if positive)
     * - Building composition (number and types of buildings)
     * <p>
     * REF: ai_analysis.md — AI behavior changes based on game progression
     *
     * @param entities    the entity manager
     * @param currentTick the current game tick (-1 if unknown)
     * @param playerId    the AI player ID
     * @return the current game phase
     */
    public GamePhase getGamePhase(EntityManager entities, long currentTick, int playerId) {
        // Tick-based phase determination
        if (currentTick >= 0) {
            if (currentTick < GamePhase.EARLY.tickBoundary) {
                return GamePhase.EARLY;
            }
            if (currentTick < GamePhase.MID.tickBoundary) {
                return GamePhase.MID;
            }
            return GamePhase.LATE;
        }

        // Building-based phase determination (fallback when tick is unknown)
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        boolean hasTechCentre = false;
        boolean hasMachineFactory = false;
        int aliveBuildings = 0;

        for (Building building : buildings) {
            if (!building.isAlive() || building.isUnderConstruction()) {
                continue;
            }
            aliveBuildings++;
            if (building.getBuildingType().researches()) {
                hasTechCentre = true;
            }
            if (building.getBuildingType().producesUnits() && !building.getBuildingType().isHQ()) {
                // Check if it's a vehicle producer
                if (building.getBuildingType() == BuildingType.CONFED_MACHINE_FACTORY ||
                    building.getBuildingType() == BuildingType.REBEL_FACTORY) {
                    hasMachineFactory = true;
                }
            }
        }

        // Phase determination based on building state
        // ASSUMPTION: Early = only basic buildings, Mid = tech centre + vehicles, Late = full build
        if (!hasTechCentre) {
            return GamePhase.EARLY;
        }
        if (!hasMachineFactory || aliveBuildings < 5) {
            return GamePhase.MID;
        }
        return GamePhase.LATE;
    }

    /**
     * Get phase-based priority research IDs for a faction.
     * Returns research IDs in priority order for the given phase.
     *
     * @param faction the faction
     * @param phase   the game phase
     * @return array of research IDs in priority order
     */
    private int[] getPhasePriorityResearch(Faction faction, GamePhase phase) {
        return switch (faction) {
            case CONFEDERATION -> switch (phase) {
                case EARLY -> new int[]{CONFED_EARLY_RESEARCH, 1, 2};
                case MID -> new int[]{CONFED_MID_RESEARCH, 4, 5};
                case LATE -> new int[]{CONFED_LATE_RESEARCH, 6};
            };
            case RESISTANCE -> switch (phase) {
                case EARLY -> new int[]{REBEL_EARLY_RESEARCH, 25, 26};
                case MID -> new int[]{REBEL_MID_RESEARCH, 33, 34};
                case LATE -> new int[]{REBEL_LATE_RESEARCH, 39};
            };
            default -> new int[]{};
        };
    }

    /**
     * Check if a research can be started.
     * Verifies: not already completed, prerequisites met, not already active.
     *
     * @param researchId the research ID
     * @param playerId   the player ID
     * @param research   the research system
     * @param techTree   the tech tree
     * @param entities   the entity manager
     * @return true if the research can be started
     */
    private boolean canStartResearch(int researchId, int playerId, ResearchSystem research,
                                      TechTree techTree, EntityManager entities) {
        // Already completed
        if (research.hasResearch(playerId, researchId)) {
            return false;
        }
        // Prerequisites not met
        if (!research.arePrerequisitesMet(researchId, playerId)) {
            return false;
        }
        // Faction has this tech
        Faction faction = EconomySystem.playerFaction(playerId);
        TechTree.ResearchNode node = techTree.getTechNode(faction, researchId);
        if (node == null) {
            return false;
        }
        return true;
    }

    /**
     * Find an available Technology Centre that is not currently researching.
     *
     * @param entities the entity manager
     * @param playerId the player ID
     * @return an available Tech Centre, or null if none found
     */
    private Building findAvailableTechCentre(EntityManager entities, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        for (Building building : buildings) {
            if (!building.isAlive() || building.isUnderConstruction()) {
                continue;
            }
            if (!building.getBuildingType().researches()) {
                continue;
            }
            if (!building.isPowered()) {
                continue;
            }
            if (building.isResearching()) {
                continue;
            }
            return building;
        }
        return null;
    }
}
