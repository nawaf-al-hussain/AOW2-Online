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
import java.util.HashMap;
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

    /** Per-player accumulated research bonuses. Index = playerId. */
    private final ResearchBonusTracker[] bonusTrackers;

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
        this.bonusTrackers = new ResearchBonusTracker[MAX_PLAYERS];
        this.activeResearchMap = new ConcurrentHashMap<>();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            completedResearch[i] = new HashSet<>();
            bonusTrackers[i] = new ResearchBonusTracker();
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
     * Get the bonus tracker for a player, containing all accumulated research
     * bonuses from completed research.
     *
     * @param playerId the player ID (0 or 1)
     * @return the bonus tracker for the player
     * @throws IndexOutOfBoundsException if playerId is out of range
     */
    public ResearchBonusTracker getBonusTracker(int playerId) {
        return bonusTrackers[playerId];
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
     * Reads the raw effects from {@link ResearchRegistry} and mutates the
     * player's {@link ResearchBonusTracker}. Other systems (ArmorCalculator,
     * CombatSystem, BuildingSystem, etc.) query the tracker via
     * {@link #getBonusTracker(int)} to obtain accumulated bonus values.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects" — 48 research IDs (0-47)
     * REF: TechTree.java — canonical ID-to-faction mapping
     * REF: tech_tree.json — effect key definitions
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

        // Load effects from the ResearchRegistry (tech_tree.json)
        ResearchRegistry.ResearchEffect registryEffect = ResearchRegistry.getInstance().getResearchEffect(researchId);
        if (registryEffect == null || registryEffect.effects() == null) {
            LOG.warn("Player {} ({}) completed research ID {} ({}): no registry effects found",
                playerId, faction, researchId, techName);
            return;
        }

        Map<String, Object> effects = registryEffect.effects();
        ResearchBonusTracker tracker = bonusTrackers[playerId];

        // --- Armor bonuses ---
        applyIntBonus(effects, "infantryArmorBonus", tracker::addInfantryArmorBonus);
        applyIntBonus(effects, "vehicleArmorBonus", tracker::addVehicleArmorBonus);
        applyIntBonus(effects, "buildingArmorBonus", tracker::addBuildingArmorBonus);

        // --- Building armor override ---
        Object overrideVal = effects.get("buildingArmorOverride");
        Object isOverrideVal = effects.get("isOverride");
        if (overrideVal instanceof Number && isOverrideVal instanceof Boolean && (Boolean) isOverrideVal) {
            tracker.setBuildingArmorOverride(((Number) overrideVal).intValue());
        }

        // --- Building radius bonus ---
        applyIntBonus(effects, "buildingRadiusBonus", tracker::addBuildingRadiusBonus);

        // --- Attack damage bonus (global or per-type) ---
        Object dmgVal = effects.get("attackDamageBonus");
        if (dmgVal instanceof Number dmgNum) {
            int dmg = dmgNum.intValue();
            List<Integer> affectedTypes = parseAffectedTypes(effects);
            if (affectedTypes != null) {
                for (int type : affectedTypes) {
                    tracker.addAttackDamageBonusForType(type, dmg);
                }
            } else {
                tracker.addAttackDamageBonus(dmg);
            }
        }

        // --- Attack speed bonus (int, Map, or per-type via affectedUnitTypes) ---
        Object spdVal = effects.get("attackSpeedBonus");
        if (spdVal instanceof Number spdNum) {
            int spd = spdNum.intValue();
            List<Integer> affectedTypes = parseAffectedTypes(effects);
            if (affectedTypes != null) {
                for (int type : affectedTypes) {
                    tracker.addAttackSpeedBonusForType(type, spd);
                }
            } else {
                tracker.addGlobalAttackSpeedBonus(spd);
            }
        } else if (spdVal instanceof Map) {
            // Map of "typeN" -> value (e.g., {"type11": 5, "type13": 8})
            @SuppressWarnings("unchecked")
            Map<String, Object> spdMap = (Map<String, Object>) spdVal;
            for (var entry : spdMap.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.startsWith("type") && val instanceof Number) {
                    int typeId = Integer.parseInt(key.substring(4));
                    tracker.addAttackSpeedBonusForType(typeId, ((Number) val).intValue());
                }
            }
        }

        // --- Attack range bonus (per-type via affectedUnitTypes or affectedRangeUnitTypes) ---
        Object rangeVal = effects.get("attackRangeBonus");
        if (rangeVal instanceof Number rangeNum) {
            int range = rangeNum.intValue();
            // Use affectedRangeUnitTypes first, then fall back to affectedUnitTypes
            List<Integer> rangeTypes = parseIntList(effects.get("affectedRangeUnitTypes"));
            if (rangeTypes == null) {
                rangeTypes = parseAffectedTypes(effects);
            }
            if (rangeTypes != null) {
                for (int type : rangeTypes) {
                    tracker.addAttackRangeBonusForType(type, range);
                }
            } else {
                // Global range bonus (not per-type) — apply to a special "all" key
                tracker.addAttackRangeBonusForType(-1, range);
            }
        }

        // --- Player-specific range reduction divisors ---
        applyRangeReduction(effects, "player0RangeReductionDivisor", 0, tracker);
        applyRangeReduction(effects, "player1RangeReductionDivisor", 1, tracker);

        // --- Player-specific economy effects ---
        applyPlayerIntOverride(effects, "player0SupplyCap", 0, tracker::setSupplyCap);
        applyPlayerIntOverride(effects, "player1SupplyCap", 1, tracker::setSupplyCap);
        applyPlayerIntOverride(effects, "player0CreditLimit", 0, tracker::setCreditLimit);
        applyPlayerIntOverride(effects, "player1CreditLimit", 1, tracker::setCreditLimit);

        // --- Unit limit bonus ---
        applyIntBonus(effects, "unitLimitBonus", tracker::addUnitLimitBonus);

        // --- Production speed override (int or Map with "slot"/"value") ---
        Object prodSpeedVal = effects.get("productionSpeedOverride");
        if (prodSpeedVal instanceof Number prodSpeedNum) {
            tracker.setProductionSpeedOverride(prodSpeedNum.intValue());
        } else if (prodSpeedVal instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prodSpeedMap = (Map<String, Object>) prodSpeedVal;
            Object slotObj = prodSpeedMap.get("slot");
            Object valueObj = prodSpeedMap.get("value");
            if (slotObj instanceof Number && valueObj instanceof Number) {
                tracker.setProductionSpeedSlotOverride(((Number) slotObj).intValue(), ((Number) valueObj).intValue());
            }
        }

        // --- Production bonuses (per-type production damage) ---
        Object prodBonusesVal = effects.get("productionBonuses");
        if (prodBonusesVal instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prodBonusesMap = (Map<String, Object>) prodBonusesVal;
            for (var entry : prodBonusesMap.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.startsWith("type") && val instanceof Number) {
                    int typeId = Integer.parseInt(key.substring(4));
                    tracker.addProductionDamageBonusForType(typeId, ((Number) val).intValue());
                }
            }
        }

        // --- Global production damage bonus ---
        applyIntBonus(effects, "productionDamageBonus", tracker::addGlobalProductionDamageBonus);

        // --- Player-specific scoring effects ---
        applyPlayerIntBonus(effects, "player0ScoreBonus", 0, tracker::addScoreBonus);
        applyPlayerIntBonus(effects, "player1ScoreBonus", 1, tracker::addScoreBonus);
        applyPlayerIntBonus(effects, "player0DisplayBonus", 0, tracker::addDisplayBonus);
        applyPlayerIntBonus(effects, "player1DisplayBonus", 1, tracker::addDisplayBonus);

        // --- Unit upgrades (fromType -> toType) ---
        Object upgradeFrom = effects.get("upgradeUnitType");
        Object upgradeTo = effects.get("upgradeToType");
        if (upgradeFrom instanceof Number && upgradeTo instanceof Number) {
            tracker.setUnitUpgrade(((Number) upgradeFrom).intValue(), ((Number) upgradeTo).intValue());
        }

        // --- Siege upgrades (unitType -> siegeValue) ---
        Object siegeVal = effects.get("siegeUpgrade");
        Object siegeUnitType = effects.get("upgradeUnitType");
        if (siegeVal instanceof Number && siegeUnitType instanceof Number) {
            tracker.setSiegeUpgrade(((Number) siegeUnitType).intValue(), ((Number) siegeVal).intValue());
        }

        LOG.info("Player {} ({}) completed research: {} (id={}) — effects applied to bonus tracker",
            playerId, faction, techName, researchId);
    }

    // =========================================================================
    // Helper methods for reading effects from the registry map
    // =========================================================================

    /**
     * Apply an integer bonus from the effects map to the tracker via the given consumer.
     */
    private static void applyIntBonus(Map<String, Object> effects, String key, java.util.function.IntConsumer consumer) {
        Object val = effects.get(key);
        if (val instanceof Number) {
            consumer.accept(((Number) val).intValue());
        }
    }

    /**
     * Parse affectedUnitTypes from the effects map.
     *
     * @return list of unit type IDs, or null if not present
     */
    @SuppressWarnings("unchecked")
    private static List<Integer> parseAffectedTypes(Map<String, Object> effects) {
        Object obj = effects.get("affectedUnitTypes");
        return parseIntList(obj);
    }

    /**
     * Parse an object as a list of integers.
     *
     * @return list of integers, or null if the object is not a list
     */
    @SuppressWarnings("unchecked")
    private static List<Integer> parseIntList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream()
                .filter(Number.class::isInstance)
                .map(n -> ((Number) n).intValue())
                .toList();
        }
        return null;
    }

    /**
     * Apply a range reduction divisor from a player-specific effect key.
     * Stores in the RESEARCHER's tracker keyed by the target player ID.
     */
    private static void applyRangeReduction(Map<String, Object> effects, String key,
                                             int targetPlayerId, ResearchBonusTracker tracker) {
        Object val = effects.get(key);
        if (val instanceof Number) {
            tracker.setRangeReductionDivisor(targetPlayerId, ((Number) val).intValue());
        }
    }

    /**
     * Apply a player-specific override (supply cap, credit limit) to the tracker.
     * The prefix in the key (player0/player1) identifies the target player whose
     * stats are affected.
     */
    private static void applyPlayerIntOverride(Map<String, Object> effects, String key,
                                                int keyPlayerId, java.util.function.IntConsumer setter) {
        Object val = effects.get(key);
        if (val instanceof Number) {
            setter.accept(((Number) val).intValue());
        }
    }

    /**
     * Apply a player-specific additive bonus (score, display) to the tracker.
     * The prefix in the key (player0/player1) identifies the target player whose
     * stats are affected.
     */
    private static void applyPlayerIntBonus(Map<String, Object> effects, String key,
                                             int keyPlayerId, java.util.function.IntConsumer adder) {
        Object val = effects.get(key);
        if (val instanceof Number) {
            adder.accept(((Number) val).intValue());
        }
    }

    // =========================================================================
    // ResearchBonusTracker — per-player accumulated research bonuses
    // =========================================================================

    /**
     * Per-player accumulated research bonuses. Stores the total effect of all
     * completed research for a player, computed incrementally as research completes.
     * Other systems query this (e.g., ArmorCalculator, CombatSystem, BuildingSystem).
     */
    public static final class ResearchBonusTracker {
        // Armor bonuses
        private int infantryArmorBonus;
        private int vehicleArmorBonus;
        private Integer buildingArmorOverride; // null = no override

        // Combat bonuses
        private int attackDamageBonus; // global
        private final Map<Integer, Integer> attackDamageBonusByType = new HashMap<>(); // typeId -> bonus
        private final Map<Integer, Integer> attackSpeedBonusByType = new HashMap<>(); // typeId -> bonus
        private int globalAttackSpeedBonus; // for effects without affectedUnitTypes
        private final Map<Integer, Integer> attackRangeBonusByType = new HashMap<>();
        private final Map<Integer, Integer> rangeReductionDivisor = new HashMap<>(); // targetPlayerId -> divisor

        // Building bonuses
        private int buildingRadiusBonus;
        private int buildingArmorBonus;

        // Economy
        private Integer supplyCap; // null = use default
        private Integer creditLimit; // null = use default
        private int unitLimitBonus;
        private Integer productionSpeedOverride; // null = use default; or Map<slot, value>
        private final Map<Integer, Integer> productionSpeedSlotOverrides = new HashMap<>();
        private final Map<Integer, Integer> productionDamageBonusByType = new HashMap<>();
        private int globalProductionDamageBonus;

        // Scoring
        private int scoreBonus;
        private int displayBonus;

        // Unit upgrades: fromType -> toType
        private final Map<Integer, Integer> unitUpgrades = new HashMap<>();

        // Siege upgrades: unitType -> siegeValue
        private final Map<Integer, Integer> siegeUpgrades = new HashMap<>();

        // ---- Getters (all public) ----

        public int getInfantryArmorBonus() { return infantryArmorBonus; }
        public int getVehicleArmorBonus() { return vehicleArmorBonus; }
        public Integer getBuildingArmorOverride() { return buildingArmorOverride; }
        public int getBuildingArmorBonus() { return buildingArmorBonus; }
        public int getAttackDamageBonus() { return attackDamageBonus; }
        public int getAttackDamageBonusForType(int typeId) { return attackDamageBonusByType.getOrDefault(typeId, 0); }
        public int getAttackSpeedBonusForType(int typeId) { return attackSpeedBonusByType.getOrDefault(typeId, 0); }
        public int getGlobalAttackSpeedBonus() { return globalAttackSpeedBonus; }
        public int getAttackRangeBonusForType(int typeId) { return attackRangeBonusByType.getOrDefault(typeId, 0); }
        public int getRangeReductionDivisor(int targetPlayerId) { return rangeReductionDivisor.getOrDefault(targetPlayerId, 1); }
        public int getBuildingRadiusBonus() { return buildingRadiusBonus; }
        public Integer getSupplyCap() { return supplyCap; }
        public Integer getCreditLimit() { return creditLimit; }
        public int getUnitLimitBonus() { return unitLimitBonus; }
        public Integer getProductionSpeedOverride() { return productionSpeedOverride; }
        public int getProductionSpeedSlotOverride(int slot) { return productionSpeedSlotOverrides.getOrDefault(slot, -1); }
        public int getProductionDamageBonusForType(int typeId) { return productionDamageBonusByType.getOrDefault(typeId, 0); }
        public int getGlobalProductionDamageBonus() { return globalProductionDamageBonus; }
        public int getScoreBonus() { return scoreBonus; }
        public int getDisplayBonus() { return displayBonus; }
        public Integer getUnitUpgrade(int fromTypeId) { return unitUpgrades.get(fromTypeId); }
        public Integer getSiegeUpgrade(int unitTypeId) { return siegeUpgrades.get(unitTypeId); }

        // ---- Package-private mutators (called by ResearchSystem.applyResearchEffect) ----

        void addInfantryArmorBonus(int bonus) { this.infantryArmorBonus += bonus; }
        void addVehicleArmorBonus(int bonus) { this.vehicleArmorBonus += bonus; }
        void setBuildingArmorOverride(int value) { this.buildingArmorOverride = value; }
        void addBuildingArmorBonus(int bonus) { this.buildingArmorBonus += bonus; }
        void addAttackDamageBonus(int bonus) { this.attackDamageBonus += bonus; }
        void addAttackDamageBonusForType(int typeId, int bonus) {
            attackDamageBonusByType.merge(typeId, bonus, Integer::sum);
        }
        void addAttackSpeedBonusForType(int typeId, int bonus) {
            attackSpeedBonusByType.merge(typeId, bonus, Integer::sum);
        }
        void addGlobalAttackSpeedBonus(int bonus) { this.globalAttackSpeedBonus += bonus; }
        void addAttackRangeBonusForType(int typeId, int bonus) {
            attackRangeBonusByType.merge(typeId, bonus, Integer::sum);
        }
        void setRangeReductionDivisor(int targetPlayerId, int divisor) {
            rangeReductionDivisor.put(targetPlayerId, divisor);
        }
        void addBuildingRadiusBonus(int bonus) { this.buildingRadiusBonus += bonus; }
        void setSupplyCap(int value) { this.supplyCap = value; }
        void setCreditLimit(int value) { this.creditLimit = value; }
        void addUnitLimitBonus(int bonus) { this.unitLimitBonus += bonus; }
        void setProductionSpeedOverride(int value) { this.productionSpeedOverride = value; }
        void setProductionSpeedSlotOverride(int slot, int value) {
            productionSpeedSlotOverrides.put(slot, value);
        }
        void addProductionDamageBonusForType(int typeId, int bonus) {
            productionDamageBonusByType.merge(typeId, bonus, Integer::sum);
        }
        void addGlobalProductionDamageBonus(int bonus) { this.globalProductionDamageBonus += bonus; }
        void addScoreBonus(int bonus) { this.scoreBonus += bonus; }
        void addDisplayBonus(int bonus) { this.displayBonus += bonus; }
        void setUnitUpgrade(int fromTypeId, int toTypeId) {
            unitUpgrades.put(fromTypeId, toTypeId);
        }
        void setSiegeUpgrade(int unitTypeId, int siegeValue) {
            siegeUpgrades.put(unitTypeId, siegeValue);
        }
    }
}
