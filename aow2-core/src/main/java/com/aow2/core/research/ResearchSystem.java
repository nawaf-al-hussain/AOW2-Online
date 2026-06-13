package com.aow2.core.research;

import com.aow2.common.config.GameConstants;
import com.aow2.common.event.ResearchCompletedEvent;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import com.aow2.core.economy.EconomySystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the technology research system.
 * <p>
 * Technology Centres research one tech at a time. Each research has a cost,
 * a duration, prerequisites, and applies specific stat modifications when
 * completed.
 * <p>
 * REF: combat_formulas.md "Research/Upgrade Effects" — 48 research IDs (0-47)
 * REF: complete_unit_stats.json technologies section — 8 per faction base
 * REF: combat_formulas.md — research cost formula:
 * "(unitBuildCost * productionModifier) / 10 * 20 / (upgradeBonus + 20)"
 */
public final class ResearchSystem {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchSystem.class);

    /** Maximum number of players. */
    private static final int MAX_PLAYERS = GameConstants.MAX_PLAYERS_PER_MATCH;

    /** Per-player completed research tracking. Index = playerId. */
    private final Set<Integer>[] completedResearch;

    /** Per-player active research tracking: techCentreId -> active research ID. */
    private final Map<Integer, ActiveResearch> activeResearchMap;

    /** The technology tree definition. */
    private final TechTree techTree;

    /**
     * Records an active research project at a Tech Centre.
     *
     * @param researchId   the research being conducted
     * @param techCentreId the building ID of the Tech Centre
     * @param playerId     the player conducting the research
     * @param progress     ticks of progress so far
     * @param duration     total ticks required
     */
    public record ActiveResearch(
        int researchId,
        int techCentreId,
        int playerId,
        int progress,
        int duration
    ) {
        /**
         * Create a new active research with incremented progress.
         *
         * @return a new ActiveResearch with progress+1
         */
        public ActiveResearch tick() {
            return new ActiveResearch(researchId, techCentreId, playerId, progress + 1, duration);
        }

        /**
         * Check if the research is complete.
         *
         * @return true if progress has reached the duration
         */
        public boolean isComplete() {
            return progress >= duration;
        }
    }

    /**
     * Constructs a ResearchSystem with a default tech tree.
     */
    public ResearchSystem() {
        this(new TechTree());
    }

    /**
     * Constructs a ResearchSystem with the given tech tree.
     *
     * @param techTree the technology tree definition
     */
    @SuppressWarnings("unchecked")
    public ResearchSystem(TechTree techTree) {
        this.techTree = techTree;
        this.completedResearch = new HashSet[MAX_PLAYERS];
        this.activeResearchMap = new ConcurrentHashMap<>();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            completedResearch[i] = new HashSet<>();
        }
    }

    /**
     * Process research for one tick.
     * Advances research progress on all active Technology Centres.
     * Completes research and applies effects when progress reaches duration.
     *
     * @param entities the entity manager
     * @param state    the current game state
     */
    public void processTick(EntityManager entities, GameState state) {
        // Process each active research
        Set<Integer> completedIds = new HashSet<>();

        for (var entry : activeResearchMap.entrySet()) {
            ActiveResearch research = entry.getValue();
            ActiveResearch updated = research.tick();
            activeResearchMap.put(entry.getKey(), updated);

            if (updated.isComplete()) {
                // Complete the research
                int playerId = updated.playerId();
                int researchId = updated.researchId();
                completedResearch[playerId].add(researchId);

                // Apply the research effect
                applyResearchEffect(researchId, playerId, entities);

                // Fire event
                Faction faction = EconomySystem.playerFaction(playerId);
                TechTree.ResearchNode node = techTree.getTechNode(faction, researchId);
                String techName = node != null ? node.name() : "Research-" + researchId;
                state.enqueueEvent(new ResearchCompletedEvent(
                    state.currentTick(), playerId, faction, researchId, techName
                ));

                // Clear the Tech Centre's research state
                Building techCentre = entities.getBuilding(updated.techCentreId());
                if (techCentre != null) {
                    techCentre.setResearchId(null);
                }

                completedIds.add(entry.getKey());
                LOG.info("Player {} completed research: {} (id={})", playerId, techName, researchId);
            }
        }

        // Remove completed research entries
        completedIds.forEach(activeResearchMap::remove);
    }

    /**
     * Start a research at a Technology Centre.
     * <p>
     * Only one research at a time per Tech Centre.
     * Must meet prerequisites and have credits.
     *
     * @param techCentre the Technology Centre building
     * @param researchId the research ID to start (0-47)
     * @param playerId   the player ID (0 or 1)
     * @param economy    the economy system
     * @return true if the research was started successfully
     */
    public boolean startResearch(Building techCentre, int researchId, int playerId,
                                 EconomySystem economy) {
        // Check building is alive, complete, and is a research building
        if (!techCentre.isAlive() || techCentre.isUnderConstruction()) {
            LOG.debug("Cannot start research: Tech Centre not ready");
            return false;
        }
        if (!techCentre.getBuildingType().researches()) {
            LOG.debug("Cannot start research: building is not a Tech Centre");
            return false;
        }

        // Check building is powered
        if (!techCentre.isPowered()) {
            LOG.debug("Cannot start research: Tech Centre not powered");
            return false;
        }

        // Check Tech Centre isn't already researching
        if (techCentre.isResearching()) {
            LOG.debug("Cannot start research: Tech Centre already researching");
            return false;
        }

        // Check research not already completed
        if (hasResearch(playerId, researchId)) {
            LOG.debug("Cannot start research: already completed");
            return false;
        }

        // Check prerequisites
        if (!arePrerequisitesMet(researchId, playerId)) {
            LOG.debug("Cannot start research: prerequisites not met for research {}", researchId);
            return false;
        }

        // Check faction match
        Faction faction = EconomySystem.playerFaction(playerId);
        TechTree.ResearchNode node = techTree.getTechNode(faction, researchId);
        if (node == null) {
            LOG.debug("Cannot start research: research {} not available for faction {}", researchId, faction);
            return false;
        }

        // Check player can afford
        int cost = node.cost();
        if (!economy.canAfford(playerId, cost)) {
            LOG.debug("Cannot start research: player {} cannot afford {} credits", playerId, cost);
            return false;
        }

        // Deduct credits
        economy.spendCredits(playerId, cost);

        // Start research
        int duration = node.duration();
        ActiveResearch active = new ActiveResearch(researchId, techCentre.getId(), playerId, 0, duration);
        activeResearchMap.put(techCentre.getId(), active);
        techCentre.setResearchId(String.valueOf(researchId));

        LOG.info("Player {} started research: {} (id={}) for {} credits, duration: {} ticks",
            playerId, node.name(), researchId, cost, duration);
        return true;
    }

    /**
     * Check if a research is completed for a player.
     *
     * @param playerId   the player ID (0 or 1)
     * @param researchId the research ID to check
     * @return true if the player has completed this research
     */
    public boolean hasResearch(int playerId, int researchId) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return false;
        }
        return completedResearch[playerId].contains(researchId);
    }

    /**
     * Get all completed researches for a player.
     *
     * @param playerId the player ID (0 or 1)
     * @return unmodifiable set of completed research IDs
     */
    public Set<Integer> getCompletedResearch(int playerId) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return Set.of();
        }
        return Collections.unmodifiableSet(completedResearch[playerId]);
    }

    /**
     * Check if a research's prerequisites are met.
     *
     * @param researchId the research ID to check
     * @param playerId   the player ID
     * @return true if all prerequisites have been completed
     */
    public boolean arePrerequisitesMet(int researchId, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Integer> prereqs = techTree.getPrerequisites(faction, researchId);
        if (prereqs == null || prereqs.isEmpty()) {
            return true;
        }
        for (int prereq : prereqs) {
            if (!hasResearch(playerId, prereq)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply research effect to the game state.
     * Called when research completes.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects" — detailed effects per research ID
     *
     * @param researchId the completed research ID
     * @param playerId   the player who completed it
     * @param entities   the entity manager
     */
    public void applyResearchEffect(int researchId, int playerId, EntityManager entities) {
        Faction faction = EconomySystem.playerFaction(playerId);

        // REF: combat_formulas.md — research effects are applied as stat modifications
        // The specific effect depends on the research ID.
        // ASSUMPTION: Effects are applied by modifying unit/building stats at runtime.
        // For now, we log the effect. Full implementation will modify entity stats
        // via a stat modifier system.
        TechTree.ResearchNode node = techTree.getTechNode(faction, researchId);
        if (node != null) {
            LOG.debug("Applied research effect: {} for player {} ({})",
                node.name(), playerId, node.description());
        }

        // REF: combat_formulas.md — specific effects per research ID
        switch (researchId) {
            case 5, 8, 13, 18, 29, 32, 37, 41, 42 -> {
                // Building radius +1 effects
                // ASSUMPTION: These are handled by the PowerSystem querying completed research
                LOG.debug("Building radius upgrade applied for player {}", playerId);
            }
            case 0, 9, 24, 33 -> {
                // Infantry armour upgrades
                LOG.debug("Infantry armour upgrade applied for player {}", playerId);
            }
            case 4 -> {
                // Building armour +4
                LOG.debug("Building armour upgrade applied for player {}", playerId);
            }
            case 6 -> {
                // Upgrades unit type 18 -> type 7 (Rhino upgrade)
                LOG.debug("Unit type upgrade applied for player {}", playerId);
            }
            case 12 -> {
                // Upgrades unit type 17 -> type 11 (Hammer upgrade)
                LOG.debug("Unit type upgrade applied for player {}", playerId);
            }
            default -> {
                LOG.debug("Research effect {} applied for player {}", researchId, playerId);
            }
        }
    }
}
