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
     * REF: combat_formulas.md "Research/Upgrade Effects" — 48 research IDs (0-47)
     * REF: TechTree.java — canonical ID-to-faction mapping
     * <p>
     * <b>Global 48-ID mapping (canonical):</b>
     * <ul>
     *   <li>Confederation: IDs 0–23 and 43 (25 nodes total)</li>
     *   <li>Resistance: IDs 24–47 excluding 43 (23 nodes total)</li>
     * </ul>
     *
     * @param researchId the completed research ID (0-47)
     * @param playerId   the player who completed it
     * @param entities   the entity manager
     */
    public void applyResearchEffect(int researchId, int playerId, EntityManager entities) {
        Faction faction = EconomySystem.playerFaction(playerId);

        TechTree.TechTreeNode node = techTree.getTechNode(faction, researchId);
        String techName = node != null ? node.name() : "Research-" + researchId;

        // Research is already stored in completedResearch[playerId] by processTick().
        // Other systems query hasResearch(playerId, researchId) to apply effects lazily.

        // REF: TechTree.java + combat_formulas.md — effects per research ID
        // Confederation: IDs 0-23, 43 | Resistance: IDs 24-47 (excluding 43)
        switch (researchId) {
            // =====================================================================
            // CONFEDERATION research (IDs 0–23)
            // =====================================================================

            // --- Infantry Chain (Tier 1-2) ---
            case 0 -> {
                // Energy Suit — Infantry armour +2, Sniper armour +2, Light armour +2
                // ArmorCalculator checks hasResearch(playerId, 0)
                LOG.info("Player {} ({}) completed Energy Suit: infantry armour +2, sniper armour +2, light armour +2", playerId, faction);
            }
            case 1 -> {
                // Advanced Targeting — Enemy attack range reduction /3
                // CombatSystem checks hasResearch(playerId, 1)
                LOG.info("Player {} ({}) completed Advanced Targeting: enemy attack range /3", playerId, faction);
            }
            case 2 -> {
                // Rapid Fire — Attack speed -2 (faster) for specific unit types
                // CombatSystem checks hasResearch(playerId, 2)
                LOG.info("Player {} ({}) completed Rapid Fire: attack speed -2 (faster) for specific unit types", playerId, faction);
            }
            case 3 -> {
                // Enhanced Munitions — Attack damage +2, Production damage +2
                // CombatSystem checks hasResearch(playerId, 3)
                LOG.info("Player {} ({}) completed Enhanced Munitions: attack damage +2, production damage +2", playerId, faction);
            }

            // --- Building Defence Chain ---
            case 4 -> {
                // Fortified Structures — Building armour +4, Production armour +4
                // ArmorCalculator checks hasResearch(playerId, 4)
                LOG.info("Player {} ({}) completed Fortified Structures: building armour +4, production armour +4", playerId, faction);
            }

            // --- Building Radius / Economy Chain ---
            case 5 -> {
                // Power Grid Expansion — Building radius +1
                // BuildingSystem checks hasResearch(playerId, 5)
                LOG.info("Player {} ({}) completed Power Grid Expansion: building radius +1", playerId, faction);
            }

            // --- Infantry Chain (Tier 3) ---
            case 6 -> {
                // Rhino Mk.II Upgrade — Upgrades Rhino (type 18) → Heavy Assault (type 7)
                // ProductionSystem checks hasResearch(playerId, 6) for unit upgrade
                LOG.info("Player {} ({}) completed Rhino Mk.II Upgrade: upgrades Rhino to Heavy Assault variant", playerId, faction);
            }
            case 7 -> {
                // Vehicle Propulsion — Attack speed +5 for type 11, +8 for type 13; Production bonuses
                // CombatSystem checks hasResearch(playerId, 7)
                LOG.info("Player {} ({}) completed Vehicle Propulsion: attack speed +5 for type 11, +8 for type 13; production bonuses", playerId, faction);
            }

            // --- Heavy Machinery Chain (Tier 4) ---
            case 8 -> {
                // Heavy Artillery Upgrade — Attack range -1 for types 7,18,9,11,17,13,16; Building radius +1
                // CombatSystem checks hasResearch(playerId, 8)
                LOG.info("Player {} ({}) completed Heavy Artillery Upgrade: attack range -1 for heavy units; building radius +1", playerId, faction);
            }

            // --- Heavy Machinery Chain (Tier 5) ---
            case 9 -> {
                // Composite Armour II — Infantry armour +2 for types 7,18,9,11,17,13,16
                // ArmorCalculator checks hasResearch(playerId, 9)
                LOG.info("Player {} ({}) completed Composite Armour II: infantry armour +2 for heavy unit types", playerId, faction);
            }
            case 10 -> {
                // Signal Jamming — Player 1 attack range reduction /3
                // CombatSystem checks hasResearch(playerId, 10)
                LOG.info("Player {} ({}) completed Signal Jamming: enemy attack range /3", playerId, faction);
            }

            // --- Heavy Machinery Chain (Tier 6) ---
            case 11 -> {
                // Quick Reload — Attack speed -2 (faster) for types 11, 13
                // CombatSystem checks hasResearch(playerId, 11)
                LOG.info("Player {} ({}) completed Quick Reload: attack speed -2 (faster) for types 11, 13", playerId, faction);
            }
            case 12 -> {
                // Hammer Mk.II Upgrade — Upgrades Hammer (type 17) → Mine Scorpio (type 11)
                // ProductionSystem checks hasResearch(playerId, 12) for unit upgrade
                LOG.info("Player {} ({}) completed Hammer Mk.II Upgrade: upgrades Hammer to Mine Scorpio variant", playerId, faction);
            }

            // --- Heavy Machinery Chain (Tier 7) ---
            case 13 -> {
                // Power Network — Building radius +1
                // BuildingSystem checks hasResearch(playerId, 13)
                LOG.info("Player {} ({}) completed Power Network: building radius +1", playerId, faction);
            }

            // --- Heavy Machinery Chain (Tier 8 - finale) ---
            case 14 -> {
                // Siege Artillery — Attack damage +10 for type 21, Range +2; Production +2 for type 16, +5 for type 13
                // CombatSystem checks hasResearch(playerId, 14)
                LOG.info("Player {} ({}) completed Siege Artillery: attack damage +10 for type 21, range +2; production bonuses", playerId, faction);
            }

            // --- Production / Economy Chain ---
            case 15 -> {
                // Supply Logistics — Supply cap = 8
                // EconomySystem checks hasResearch(playerId, 15)
                LOG.info("Player {} ({}) completed Supply Logistics: supply cap = 8", playerId, faction);
            }
            case 16 -> {
                // Building Armour Override — Building armour override = 9
                // ArmorCalculator checks hasResearch(playerId, 16)
                LOG.info("Player {} ({}) completed Building Armour Override: building armour = 9", playerId, faction);
            }
            case 17 -> {
                // Enhanced Economy — Unit limit +2; Production +1 for type 15; Production speed = 20
                // EconomySystem checks hasResearch(playerId, 17)
                LOG.info("Player {} ({}) completed Enhanced Economy: unit limit +2; production +1 for type 15; speed = 20", playerId, faction);
            }
            case 18 -> {
                // Advanced Building Radius — Building radius +1
                // BuildingSystem checks hasResearch(playerId, 18)
                LOG.info("Player {} ({}) completed Advanced Building Radius: building radius +1", playerId, faction);
            }
            case 19 -> {
                // Fast Infantry Training — Production P[1] = 7
                // ProductionSystem checks hasResearch(playerId, 19)
                LOG.info("Player {} ({}) completed Fast Infantry Training: production P[1] = 7", playerId, faction);
            }
            case 20 -> {
                // Upgraded Assembly Line — Production P[2] = 7
                // ProductionSystem checks hasResearch(playerId, 20)
                LOG.info("Player {} ({}) completed Upgraded Assembly Line: production P[2] = 7", playerId, faction);
            }
            case 21 -> {
                // Finance Department — Credit limit = 120
                // EconomySystem checks hasResearch(playerId, 21)
                LOG.info("Player {} ({}) completed Finance Department: credit limit = 120", playerId, faction);
            }
            case 22 -> {
                // Incentive System — Score bonus = 30
                // ScoreSystem checks hasResearch(playerId, 22)
                LOG.info("Player {} ({}) completed Incentive System: score bonus = 30", playerId, faction);
            }
            case 23 -> {
                // Communications System — Display bonus = 25
                // ScoreSystem checks hasResearch(playerId, 23)
                LOG.info("Player {} ({}) completed Communications System: display bonus = 25", playerId, faction);
            }

            // =====================================================================
            // CONFEDERATION research (ID 43 — out-of-sequence)
            // =====================================================================
            case 43 -> {
                // Advanced Credits — Production P[4] = 7
                // ProductionSystem checks hasResearch(playerId, 43)
                LOG.info("Player {} ({}) completed Advanced Credits: production P[4] = 7", playerId, faction);
            }

            // =====================================================================
            // RESISTANCE research (IDs 24–47, excluding 43)
            // =====================================================================

            // --- Infantry Chain (Tier 1) ---
            case 24 -> {
                // Titanium Jacket — Infantry armour +1 for types 0, 2, 4, 14
                // ArmorCalculator checks hasResearch(playerId, 24)
                LOG.info("Player {} ({}) completed Titanium Jacket: infantry armour +1 for types 0, 2, 4, 14", playerId, faction);
            }
            case 25 -> {
                // Signal Jamming — Enemy attack range reduction /3
                // CombatSystem checks hasResearch(playerId, 25)
                LOG.info("Player {} ({}) completed Signal Jamming: enemy attack range /3", playerId, faction);
            }

            // --- Infantry Chain (Tier 2) ---
            case 26 -> {
                // Infantry Combat Drill — Attack speed +1 for types 0, 2, 3; Production +1 for types 0, 4
                // CombatSystem checks hasResearch(playerId, 26)
                LOG.info("Player {} ({}) completed Infantry Combat Drill: attack speed +1 for types 0, 2, 3; production +1 for types 0, 4", playerId, faction);
            }
            case 27 -> {
                // Infantry Range Upgrade — Attack range -1 for types 0, 2, 4, 14
                // CombatSystem checks hasResearch(playerId, 27)
                LOG.info("Player {} ({}) completed Infantry Range Upgrade: attack range -1 for types 0, 2, 4, 14", playerId, faction);
            }

            // --- Light Vehicle Chain (Tier 3) ---
            case 28 -> {
                // Coyote Range Upgrade — Attack range +1 for type 15; Production +1 for types 2
                // CombatSystem checks hasResearch(playerId, 28)
                LOG.info("Player {} ({}) completed Coyote Range Upgrade: attack range +1 for type 15; production +1 for types 2", playerId, faction);
            }

            // --- Machinery Merge Point (Tier 4) ---
            case 29 -> {
                // Building Radius Expansion — Building radius +1
                // BuildingSystem checks hasResearch(playerId, 29)
                LOG.info("Player {} ({}) completed Building Radius Expansion: building radius +1", playerId, faction);
            }

            // --- Machinery Chain A (Tier 5) ---
            case 30 -> {
                // Sniper Upgrade — Attack speed +2, Range +2 for Sniper (type 3); Production +2 for types 14, 4
                // CombatSystem checks hasResearch(playerId, 30)
                LOG.info("Player {} ({}) completed Sniper Upgrade: attack speed +2, range +2 for Sniper (type 3); production +2 for types 14, 4", playerId, faction);
            }

            // --- Machinery Chain B (Tier 5) ---
            case 31 -> {
                // Light Vehicle Speed Upgrade — Attack speed +1 for types 4, 5; Production +1 for types 6, 8
                // CombatSystem checks hasResearch(playerId, 31)
                LOG.info("Player {} ({}) completed Light Vehicle Speed Upgrade: attack speed +1 for types 4, 5; production +1 for types 6, 8", playerId, faction);
            }

            // --- Heavy Machinery Merge Point (Tier 6) ---
            case 32 -> {
                // Heavy Machinery Range Adjust — Attack range -1 for types 6, 8, 10, 15, 12; Building radius +1
                // CombatSystem checks hasResearch(playerId, 32)
                LOG.info("Player {} ({}) completed Heavy Machinery Range Adjust: attack range -1 for types 6, 8, 10, 15, 12; building radius +1", playerId, faction);
            }

            // --- Heavy Machinery Upgrades (Tier 7) ---
            case 33 -> {
                // Machinery Armour Upgrade — Infantry armour +1 for types 6, 8, 10, 15, 12
                // ArmorCalculator checks hasResearch(playerId, 33)
                LOG.info("Player {} ({}) completed Machinery Armour Upgrade: infantry armour +1 for types 6, 8, 10, 15, 12", playerId, faction);
            }
            case 34 -> {
                // Advanced Signal Jamming — Enemy attack range reduction /3
                // CombatSystem checks hasResearch(playerId, 34)
                LOG.info("Player {} ({}) completed Advanced Signal Jamming: enemy attack range /3", playerId, faction);
            }

            // --- Advanced Weapons (Tier 8) ---
            case 35 -> {
                // Rapid Reload — Attack speed -2 (faster) for types 12, 14
                // CombatSystem checks hasResearch(playerId, 35)
                LOG.info("Player {} ({}) completed Rapid Reload: attack speed -2 (faster) for types 12, 14", playerId, faction);
            }
            case 36 -> {
                // Mine Lizard Siege Mode — Unit type 10 siege upgrade = 15
                // ProductionSystem checks hasResearch(playerId, 36)
                LOG.info("Player {} ({}) completed Mine Lizard Siege Mode: unit type 10 siege upgrade = 15", playerId, faction);
            }

            // --- Advanced Weapons Merge (Tier 9) ---
            case 37 -> {
                // Advanced Building Radius — Building radius +1
                // BuildingSystem checks hasResearch(playerId, 37)
                LOG.info("Player {} ({}) completed Advanced Building Radius: building radius +1", playerId, faction);
            }

            // --- Artillery Finale (Tier 10) ---
            case 38 -> {
                // MLRS Torrent Upgrade — Attack damage +2 for type 20, Range +2; Production +2 for type 12
                // CombatSystem checks hasResearch(playerId, 38)
                LOG.info("Player {} ({}) completed MLRS Torrent Upgrade: attack damage +2 for type 20, range +2; production +2 for type 12", playerId, faction);
            }

            // --- Economy Chain ---
            case 39 -> {
                // Supply Logistics — Supply cap = 8
                // EconomySystem checks hasResearch(playerId, 39)
                LOG.info("Player {} ({}) completed Supply Logistics: supply cap = 8", playerId, faction);
            }
            case 40 -> {
                // Building Armour Override — Building armour override = 9
                // ArmorCalculator checks hasResearch(playerId, 40)
                LOG.info("Player {} ({}) completed Building Armour Override: building armour = 9", playerId, faction);
            }
            case 41 -> {
                // Enhanced Building Radius — Building radius +1
                // BuildingSystem checks hasResearch(playerId, 41)
                LOG.info("Player {} ({}) completed Enhanced Building Radius: building radius +1", playerId, faction);
            }
            case 42 -> {
                // Cumulative Building Radius — Building radius +1 (cumulative)
                // BuildingSystem checks hasResearch(playerId, 42)
                LOG.info("Player {} ({}) completed Cumulative Building Radius: building radius +1 (cumulative)", playerId, faction);
            }

            // --- Advanced Production ---
            case 44 -> {
                // Advanced Production — Production P[5] = 7
                // ProductionSystem checks hasResearch(playerId, 44)
                LOG.info("Player {} ({}) completed Advanced Production: production P[5] = 7", playerId, faction);
            }

            // --- Scoring Chain ---
            case 45 -> {
                // Finance Department — Credit limit = 120
                // EconomySystem checks hasResearch(playerId, 45)
                LOG.info("Player {} ({}) completed Finance Department: credit limit = 120", playerId, faction);
            }
            case 46 -> {
                // Incentive System — Score bonus = 30
                // ScoreSystem checks hasResearch(playerId, 46)
                LOG.info("Player {} ({}) completed Incentive System: score bonus = 30", playerId, faction);
            }
            case 47 -> {
                // Communications System — Display bonus = 25
                // ScoreSystem checks hasResearch(playerId, 47)
                LOG.info("Player {} ({}) completed Communications System: display bonus = 25", playerId, faction);
            }

            default -> {
                // Unknown research ID — should not happen with valid tech tree data
                LOG.warn("Player {} ({}) completed unknown research ID {}: {}", playerId, faction, researchId, techName);
            }
        }
    }
}
