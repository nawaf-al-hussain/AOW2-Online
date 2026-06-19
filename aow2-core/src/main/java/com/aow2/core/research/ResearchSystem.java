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
                TechTree.TechTreeNode node = techTree.getTechNode(faction, researchId);
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

        // Guard against multiple researches on the same TechCentre (race condition)
        if (activeResearchMap.containsKey(techCentre.getId())) {
            LOG.debug("Cannot start research: Tech Centre {} already has an active research entry", techCentre.getId());
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
        TechTree.TechTreeNode node = techTree.getTechNode(faction, researchId);
        if (node == null) {
            LOG.debug("Cannot start research: research {} not available for faction {}", researchId, faction);
            return false;
        }

        // Determine cost and duration
        // FIX: Try loading from ResearchRegistry (tech_tree.json) first, then fall back to TechTree node.
        // The formula from RE spec: cost = (unitBuildCost * productionModifier) / 10 * 20 / (upgradeBonus + 20)
        // ASSUMPTION: When ResearchRegistry provides data, use its cost/duration directly.
        // When falling back, the existing TechTree node costs/durations already vary per tech ID.
        // ASSUMPTION for scaling fallback: if node costs are all equal, apply scaling.
        int cost = node.cost();
        int duration = node.duration();

        // Try to get cost/duration from ResearchRegistry if available
        ResearchRegistry.ResearchEffect registryEffect = ResearchRegistry.getInstance().getResearchEffect(researchId);
        if (registryEffect != null && registryEffect.effects() != null) {
            Object costObj = registryEffect.effects().get("cost");
            Object durationObj = registryEffect.effects().get("duration");
            if (costObj instanceof Number) {
                cost = ((Number) costObj).intValue();
            }
            if (durationObj instanceof Number) {
                duration = ((Number) durationObj).intValue();
            }
        }

        // Check player can afford
        if (!economy.canAfford(playerId, cost)) {
            LOG.debug("Cannot start research: player {} cannot afford {} credits", playerId, cost);
            return false;
        }

        // Deduct credits
        economy.spendCredits(playerId, cost);

        // Start research
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
     * Returns all active research entries across all players.
     *
     * @return unmodifiable collection of active research entries
     */
    public List<ActiveResearch> getActiveResearchEntries() {
        return List.copyOf(activeResearchMap.values());
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
     * Research effects are implemented as "mark as completed" — other systems
     * (ArmorCalculator, CombatSystem, ProductionSystem, etc.) query
     * {@link #hasResearch(int, int)} to apply the actual stat modifications lazily.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects" — detailed effects per research ID
     * <p>
     * Confederation research IDs 0-7:
     * <ul>
     *   <li>ID 0: Energy suit → infantry armor +2 (ArmorCalculator checks hasResearch)</li>
     *   <li>ID 1: Bio suit → infantry HP recovery x3</li>
     *   <li>ID 2: Enhanced firing rate → Fortress attack speed reduced by 50%</li>
     *   <li>ID 3: Armour-piercing bullet → Fortress damage +40%</li>
     *   <li>ID 4: Heavy shells → Hammer damage +40%</li>
     *   <li>ID 5: Forced light missiles → unlocks Flame Assault (ProductionSystem checks hasResearch)</li>
     *   <li>ID 6: Lava flame fuel → Fortress can use flame weapon</li>
     *   <li>ID 7: Volcano flame gun → Zeus and Hammer damage +25%</li>
     * </ul>
     * <p>
     * Rebels research IDs 8-15:
     * <ul>
     *   <li>ID 8: Titanium jacket → infantry armor +1 (ArmorCalculator checks hasResearch)</li>
     *   <li>ID 9: First-aid kit → infantry HP recovery x3</li>
     *   <li>ID 10: Enhanced fire rate → infantry firing rate +50%</li>
     *   <li>ID 11: Doping → infantry speed +1</li>
     *   <li>ID 12: Snipers → unlocks Sniper unit (ProductionSystem checks hasResearch)</li>
     *   <li>ID 13: Rifle 'Hornet-10' → Sniper sight+2, range+2, damage+20%</li>
     *   <li>ID 14: Heavy machine gun → Coyote/Armadillo damage +15%</li>
     *   <li>ID 15: Reinforced engine → vehicle speed +1, unlocks Rhino (ProductionSystem checks hasResearch)</li>
     * </ul>
     *
     * @param researchId the completed research ID
     * @param playerId   the player who completed it
     * @param entities   the entity manager
     */
    public void applyResearchEffect(int researchId, int playerId, EntityManager entities) {
        Faction faction = EconomySystem.playerFaction(playerId);

        TechTree.TechTreeNode node = techTree.getTechNode(faction, researchId);
        String techName = node != null ? node.name() : "Research-" + researchId;

        // Research is already stored in completedResearch[playerId] by processTick().
        // Other systems query hasResearch(playerId, researchId) to apply effects lazily.

        // REF: combat_formulas.md — specific effects per research ID
        switch (researchId) {
            // === Confederation research (IDs 0-7) ===
            case 0 -> {
                // Energy suit → infantry armor +2
                // ArmorCalculator.getResearchArmorBonus() checks hasResearch(playerId, 0)
                LOG.info("Player {} ({}) completed Energy Suit: infantry armor +2", playerId, faction);
            }
            case 1 -> {
                // Bio suit → infantry HP recovery x3
                // HP recovery system checks hasResearch(playerId, 1)
                LOG.info("Player {} ({}) completed Bio Suit: infantry HP recovery x3", playerId, faction);
            }
            case 2 -> {
                // Enhanced firing rate → Fortress attack speed reduced by 50%
                // CombatSystem checks hasResearch(playerId, 2) for Fortress attack speed
                LOG.info("Player {} ({}) completed Enhanced Firing Rate: Fortress attack speed -50%", playerId, faction);
            }
            case 3 -> {
                // Armour-piercing bullet → Fortress damage +40%
                // CombatSystem checks hasResearch(playerId, 3) for Fortress damage bonus
                LOG.info("Player {} ({}) completed Armour-Piercing Bullet: Fortress damage +40%", playerId, faction);
            }
            case 4 -> {
                // Heavy shells → Hammer damage +40%
                // CombatSystem checks hasResearch(playerId, 4) for Hammer damage bonus
                LOG.info("Player {} ({}) completed Heavy Shells: Hammer damage +40%", playerId, faction);
            }
            case 5 -> {
                // Forced light missiles → unlocks Flame Assault
                // ProductionSystem checks hasResearch(playerId, 5) for unit availability
                LOG.info("Player {} ({}) completed Forced Light Missiles: Flame Assault unlocked", playerId, faction);
            }
            case 6 -> {
                // Lava flame fuel → Fortress can use flame weapon
                // CombatSystem checks hasResearch(playerId, 6) for Fortress flame weapon
                LOG.info("Player {} ({}) completed Lava Flame Fuel: Fortress flame weapon enabled", playerId, faction);
            }
            case 7 -> {
                // Volcano flame gun → Zeus and Hammer damage +25%
                // CombatSystem checks hasResearch(playerId, 7) for Zeus/Hammer damage bonus
                LOG.info("Player {} ({}) completed Volcano Flame Gun: Zeus & Hammer damage +25%", playerId, faction);
            }

            // === Rebels research (IDs 8-15) ===
            case 8 -> {
                // Titanium jacket → infantry armor +1
                // ArmorCalculator.getResearchArmorBonus() checks hasResearch(playerId, 8)
                LOG.info("Player {} ({}) completed Titanium Jacket: infantry armor +1", playerId, faction);
            }
            case 9 -> {
                // First-aid kit → infantry HP recovery x3
                // HP recovery system checks hasResearch(playerId, 9)
                LOG.info("Player {} ({}) completed First-Aid Kit: infantry HP recovery x3", playerId, faction);
            }
            case 10 -> {
                // Enhanced fire rate → infantry firing rate +50%
                // CombatSystem checks hasResearch(playerId, 10) for infantry attack speed
                LOG.info("Player {} ({}) completed Enhanced Fire Rate: infantry firing rate +50%", playerId, faction);
            }
            case 11 -> {
                // Doping → infantry speed +1
                // MovementSystem checks hasResearch(playerId, 11) for infantry speed bonus
                LOG.info("Player {} ({}) completed Doping: infantry speed +1", playerId, faction);
            }
            case 12 -> {
                // Snipers → unlocks Sniper unit
                // ProductionSystem checks hasResearch(playerId, 12) for unit availability
                LOG.info("Player {} ({}) completed Snipers: Sniper unit unlocked", playerId, faction);
            }
            case 13 -> {
                // Rifle 'Hornet-10' → Sniper sight+2, range+2, damage+20%
                // CombatSystem checks hasResearch(playerId, 13) for Sniper stat bonuses
                LOG.info("Player {} ({}) completed Rifle Hornet-10: Sniper sight+2, range+2, damage+20%", playerId, faction);
            }
            case 14 -> {
                // Heavy machine gun → Coyote/Armadillo damage +15%
                // CombatSystem checks hasResearch(playerId, 14) for vehicle damage bonus
                LOG.info("Player {} ({}) completed Heavy Machine Gun: Coyote/Armadillo damage +15%", playerId, faction);
            }
            case 15 -> {
                // Reinforced engine → vehicle speed +1, unlocks Rhino
                // ProductionSystem checks hasResearch(playerId, 15) for unit availability
                // MovementSystem checks hasResearch(playerId, 15) for vehicle speed bonus
                LOG.info("Player {} ({}) completed Reinforced Engine: vehicle speed +1, Rhino unlocked", playerId, faction);
            }

            // === Extended research IDs (16-47) ===
            // REF: combat_formulas.md — additional research effects for advanced gameplay
            case 16 -> {
                // Player 0 building armor override = 9
                // ArmorCalculator.calculateEffectiveBuildingArmor() checks hasResearch(playerId, 16)
                LOG.info("Player {} ({}) completed building armor override = 9", playerId, faction);
            }
            case 40 -> {
                // Player 1 building armor override = 9
                // ArmorCalculator.calculateEffectiveBuildingArmor() checks hasResearch(playerId, 40)
                LOG.info("Player {} ({}) completed building armor override = 9", playerId, faction);
            }
            case 24 -> {
                // Additional infantry armor +1
                // ArmorCalculator.getResearchArmorBonus() checks hasResearch(playerId, 24)
                LOG.info("Player {} ({}) completed additional infantry armor +1", playerId, faction);
            }
            case 33 -> {
                // Additional vehicle armor +1
                // ArmorCalculator.getResearchArmorBonus() checks hasResearch(playerId, 33)
                LOG.info("Player {} ({}) completed additional vehicle armor +1", playerId, faction);
            }
            default -> {
                // Other research effects: building radius, power, etc.
                // These are handled by other systems querying hasResearch()
                LOG.info("Player {} ({}) completed research ID {}: {}", playerId, faction, researchId, techName);
            }
        }
    }
}
