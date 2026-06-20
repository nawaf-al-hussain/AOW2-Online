package com.aow2.core.ai;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.common.model.UnitType;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.research.TechTree;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.FogOfWarSystem;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Master AI controller that coordinates economy, military, and research decisions.
 * <p>
 * REF: ai_analysis.md — original AI decision patterns.
 * The original AI runs in method b() of w.java, processing every 30 ticks.
 * The AI uses the same unit/building data structures as the human player.
 * <p>
 * Decision frequency depends on difficulty:
 * - Easy: every 60 ticks (1 second)
 * - Normal: every 30 ticks (0.5 seconds)
 * - Hard: every 15 ticks (0.25 seconds)
 * <p>
 * REF: ai_analysis.md — AI difficulty affects:
 * 1. Production speed: y.V[6] (game speed modifier)
 * 2. Build time: effectiveBuildTime = (baseTime * modifier * 100 / 100) * 20 / (upgradeBonus + 20)
 * 3. Credit generation: modified by y.V[2] and y.aU[player][3]
 */
public final class AISystem {

    private static final Logger LOG = LoggerFactory.getLogger(AISystem.class);

    /** The AI difficulty level. */
    private final AIDifficulty difficulty;

    /** The player ID this AI controls (0 or 1). */
    private final int playerId;

    /** The AI economy subsystem. */
    private final EconomyAI economyAI;

    /** The AI military subsystem. */
    private final MilitaryAI militaryAI;

    /** The AI research subsystem. */
    private final ResearchAI researchAI;

    /** Deterministic RNG for lockstep multiplayer compatibility.
     *  FIX (P1-H1): Replaced java.util.Random with DeterministicLCG.
     *  java.util.Random implementation varies across JDK versions, causing
     *  desyncs in lockstep multiplayer. LCG produces identical sequences everywhere. */
    private final DeterministicLCG random;

    /** The fog of war system, used to limit AI vision to visible tiles only. */
    private FogOfWarSystem fogOfWar;

    /** Tick counter for decision timing. */
    private long lastDecisionTick;

    /** Number of currently active AI tasks. */
    private int activeTaskCount;

    /** Cached TechTree instance — avoids reallocating every decision cycle.
     *  FIX (P3-M9): TechTree is immutable and reads from static data, so a single
     *  instance can be reused for the lifetime of this AISystem. Previously a new
     *  TechTree() was allocated every processResearchDecisions() call. */
    private final TechTree cachedTechTree;

    /**
     * Constructs an AI system for a specific player at a given difficulty.
     *
     * @param difficulty the AI difficulty level
     * @param playerId   the player ID this AI controls (0 or 1)
     */
    public AISystem(AIDifficulty difficulty, int playerId) {
        if (playerId < 0 || playerId > 1) {
            throw new IllegalArgumentException("playerId must be 0 or 1, got: " + playerId);
        }
        this.difficulty = difficulty;
        this.playerId = playerId;
        this.economyAI = new EconomyAI();
        this.militaryAI = new MilitaryAI();
        this.researchAI = new ResearchAI();
        // Deterministic RNG for lockstep determinism — each AI player gets a deterministic seed
        // FIX (P1-H1): Using DeterministicLCG instead of java.util.Random for cross-version consistency
        this.random = new DeterministicLCG(playerId * 31L + 42L);
        this.lastDecisionTick = -difficulty.tickInterval; // Allow immediate first decision
        this.activeTaskCount = 0;
        this.cachedTechTree = new TechTree(); // FIX (P3-M9): Cache once, reuse every cycle
    }

    /**
     * Set the fog of war system for this AI.
     * When set, the AI will only consider entities it can see.
     * When null, the AI has full information (useful for testing).
     *
     * @param fogOfWar the fog of war system, or null for full information
     */
    public void setFogOfWar(FogOfWarSystem fogOfWar) {
        this.fogOfWar = fogOfWar;
    }

    /**
     * Process AI decisions for one tick.
     * <p>
     * Decision frequency depends on difficulty:
     * - Easy: every 60 ticks (1 second)
     * - Normal: every 30 ticks (0.5 seconds)
     * - Hard: every 15 ticks (0.25 seconds)
     * <p>
     * REF: ai_analysis.md — main game tick processes all units for both players.
     * AI control points: during control phase (ac == 0), AI gives orders.
     *
     * @param entities  the entity manager
     * @param map       the game map
     * @param economy   the economy system
     * @param research  the research system
     * @param production the production system
     * @param placement the building placement system
     * @param movement  the movement system
     * @param combat    the combat system
     * @param state     the game state
     */
    public void processTick(EntityManager entities, GameMap map,
                            EconomySystem economy, ResearchSystem research,
                            ProductionSystem production, BuildingPlacementSystem placement,
                            MovementSystem movement, CombatSystem combat,
                            GameState state) {
        long currentTick = state.currentTick();

        // Check if it's time for a decision
        if (currentTick - lastDecisionTick < difficulty.tickInterval) {
            return;
        }
        lastDecisionTick = currentTick;

        // Strategy quality check: skip this decision cycle randomly based on difficulty
        // REF: ai_analysis.md — difficulty affects decision quality
        if (random.nextDouble() > difficulty.strategyQuality) {
            LOG.debug("AI player {} skipping decision (strategy quality check)", playerId);
            return;
        }

        // Check concurrent task limit
        if (activeTaskCount >= difficulty.maxConcurrentTasks) {
            LOG.debug("AI player {} at task limit ({})", playerId, activeTaskCount);
            return;
        }

        LOG.debug("AI player {} processing decision at tick {}", playerId, currentTick);

        // Increment task count at the start of each decision cycle
        // instead of resetting, so that tasks from previous cycles are preserved
        // until explicitly completed via taskCompleted().

        // Execute AI decision pipeline, passing fogOfWar for visibility-filtered decisions
        processEconomyDecisions(entities, map, economy, research, production, placement);
        taskCompleted();
        processResearchDecisions(entities, economy, research, currentTick);
        taskCompleted();
        processMilitaryDecisions(entities, map, movement, combat);
        taskCompleted();
        processSiegeDecisions(entities, combat);
        taskCompleted();
        processGarrisonDecisions(entities, map, movement);
        taskCompleted();
    }

    /**
     * Process economy decisions: building construction and unit production.
     * <p>
     * REF: ai_analysis.md — AI manages resources through building construction
     * and unit production.
     */
    private void processEconomyDecisions(EntityManager entities, GameMap map,
                                          EconomySystem economy, ResearchSystem research,
                                          ProductionSystem production,
                                          BuildingPlacementSystem placement) {
        // 1. Decide what building to construct next
        BuildingType nextBuilding = economyAI.decideNextBuilding(entities, economy, playerId);
        if (nextBuilding != null && activeTaskCount < difficulty.maxConcurrentTasks) {
            GridPosition position = economyAI.findPlacementPosition(
                nextBuilding, entities, map, placement, economy, playerId);
            if (position != null) {
                Building placed = placement.placeBuilding(
                    nextBuilding, position, playerId, entities, map, economy);
                if (placed != null) {
                    activeTaskCount++;
                    LOG.info("AI player {} placed {} at {}", playerId, nextBuilding, position);
                }
            }
        }

        // 2. Decide unit production
        Map<Integer, UnitType> productionDecisions = economyAI.decideProduction(
            entities, economy, research, playerId);
        for (var entry : productionDecisions.entrySet()) {
            int buildingId = entry.getKey();
            UnitType unitType = entry.getValue();
            Building producer = entities.getBuilding(buildingId);
            if (producer != null) {
                boolean enqueued = production.enqueueUnit(producer, unitType, playerId, economy, research);
                if (enqueued) {
                    activeTaskCount++;
                    LOG.info("AI player {} enqueued {} at building {}", playerId, unitType, buildingId);
                }
            }
        }
    }

    /**
     * Process research decisions.
     * <p>
     * REF: ai_analysis.md — AI uses Technology Centre to research upgrades.
     */
    private void processResearchDecisions(EntityManager entities, EconomySystem economy,
                                           ResearchSystem research, long currentTick) {
        TechTree techTree = cachedTechTree; // FIX (P3-M9): Use cached instance
        int nextResearch = researchAI.decideNextResearch(entities, research, techTree, playerId, currentTick);
        if (nextResearch >= 0) {
            // Find available tech centre
            Faction faction = EconomySystem.playerFaction(playerId);
            Building techCentre = findAvailableTechCentre(entities, faction);
            if (techCentre != null) {
                boolean started = research.startResearch(techCentre, nextResearch, playerId, economy);
                if (started) {
                    activeTaskCount++;
                    LOG.info("AI player {} started research {} at building {}",
                        playerId, nextResearch, techCentre.getId());
                }
            }
        }
    }

    /**
     * Process military decisions: attack, defend, retreat, harass.
     * <p>
     * REF: ai_analysis.md — AI military priorities:
     * 1. Defend base when under attack
     * 2. Attack when military advantage > 1.5x
     * 3. Harass enemy economy when possible
     * 4. Retreat when outnumbered
     */
    private void processMilitaryDecisions(EntityManager entities, GameMap map,
                                           MovementSystem movement, CombatSystem combat) {
        MilitaryAction action = militaryAI.decideAction(entities, map, playerId, fogOfWar);

        // Execute the military action using pattern matching
        switch (action) {
            case MilitaryAction.Attack a -> executeAttack(a, entities, map, movement);
            case MilitaryAction.Defend d -> executeDefend(d, entities, map, movement);
            case MilitaryAction.Retreat r -> executeRetreat(r, entities, map, movement);
            case MilitaryAction.Harass h -> executeHarass(h, entities, map, movement);
            case MilitaryAction.HoldPosition hp -> executeHoldPosition(hp);
        }
    }

    /**
     * Execute an attack order: move units to the attack target.
     * H-11: Uses per-unit targeting preferences when possible — each unit
     * gets the best target for its type rather than all moving to the same position.
     * Falls back to the group target if per-unit targeting finds no better option.
     * REF: ai_analysis.md — AI attack decision, units move toward target
     */
    private void executeAttack(MilitaryAction.Attack attack, EntityManager entities,
                                GameMap map, MovementSystem movement) {
        for (int unitId : attack.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                // H-11: Use per-unit targeting preference to find the best target for this unit
                GridPosition preferredTarget = militaryAI.findBestTargetForUnit(
                    unit, entities, playerId, fogOfWar);
                if (preferredTarget == null) {
                    preferredTarget = attack.target();
                }
                movement.issueMoveCommand(unit, preferredTarget, map, entities);
                LOG.debug("AI unit {} attacking toward {} (preference target)",
                    unitId, preferredTarget);
            }
        }
    }

    /**
     * Execute a defend order: move units to the defense point.
     * REF: ai_analysis.md — defend base when under attack
     */
    private void executeDefend(MilitaryAction.Defend defend, EntityManager entities,
                                GameMap map, MovementSystem movement) {
        for (int unitId : defend.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                movement.issueMoveCommand(unit, defend.defendPoint(), map, entities);
                LOG.debug("AI unit {} defending at {}", unitId, defend.defendPoint());
            }
        }
    }

    /**
     * Execute a retreat order: move units to the rally point.
     * REF: ai_analysis.md — retreat when outnumbered
     */
    private void executeRetreat(MilitaryAction.Retreat retreat, EntityManager entities,
                                 GameMap map, MovementSystem movement) {
        for (int unitId : retreat.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                movement.issueMoveCommand(unit, retreat.rallyPoint(), map, entities);
                LOG.debug("AI unit {} retreating to {}", unitId, retreat.rallyPoint());
            }
        }
    }

    /**
     * Execute a harassment order: send small group to enemy economy.
     * REF: ai_analysis.md — light machinery for scouts/raids, hit-and-run
     */
    private void executeHarass(MilitaryAction.Harass harass, EntityManager entities,
                                GameMap map, MovementSystem movement) {
        for (int unitId : harass.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                movement.issueMoveCommand(unit, harass.target(), map, entities);
                LOG.debug("AI unit {} harassing toward {}", unitId, harass.target());
            }
        }
    }

    /**
     * Execute a hold position order.
     * Units stay in place and wait for further orders.
     */
    private void executeHoldPosition(MilitaryAction.HoldPosition holdPosition) {
        LOG.debug("AI holding {} units in position", holdPosition.unitIds().size());
    }

    /**
     * Process siege mode decisions for AI units.
     * <p>
     * H-12: Units with siege capability auto-enter siege mode when enemies are nearby,
     * and exit siege mode when no enemies are within sight range (for movement).
     * REF: ai_analysis.md — siege mode auto-activation for siege-capable units.
     */
    private void processSiegeDecisions(EntityManager entities, CombatSystem combat) {
        List<MilitaryAI.SiegeDecision> decisions = militaryAI.findSiegeDecisions(
            entities, playerId, fogOfWar);
        for (MilitaryAI.SiegeDecision decision : decisions) {
            Unit unit = entities.getUnit(decision.unitId());
            if (unit != null && unit.isAlive()) {
                if (decision.enableSiege()) {
                    combat.enterSiegeMode(unit);
                } else {
                    combat.exitSiegeMode(unit);
                }
            }
        }
    }

    /**
     * Process garrison decisions for idle AI infantry.
     * <p>
     * H-13: Garrisons idle infantry into nearby bunkers with available capacity.
     * This is a lower-priority action — only garrison when not actively attacking/defending.
     * REF: ai_analysis.md — AI garrisons units in bunkers/towers.
     */
    private void processGarrisonDecisions(EntityManager entities, GameMap map,
                                            MovementSystem movement) {
        List<MilitaryAI.GarrisonDecision> decisions = militaryAI.findGarrisonDecisions(
            entities, playerId);
        for (MilitaryAI.GarrisonDecision decision : decisions) {
            Unit unit = entities.getUnit(decision.unitId());
            Building building = entities.getBuilding(decision.buildingId());
            if (unit == null || !unit.isAlive()) continue;
            if (building == null || !building.isAlive()) continue;
            if (building.getGarrisonedUnitRef() != null) continue;

            // Move unit close to bunker first if not within garrison range (2 tiles)
            double dist = unit.getPosition().distanceTo(building.getPosition());
            if (dist > 2) {
                movement.issueMoveCommand(unit, building.getPosition(), map, entities);
                LOG.debug("AI unit {} moving toward bunker {} for garrison",
                    decision.unitId(), decision.buildingId());
            } else {
                // Within range — perform garrison directly
                building.setGarrisonedUnitRef(decision.unitId());
                unit.setPosition(building.getPosition());
                unit.setMovementState(MovementState.IDLE);
                unit.clearPath();
                unit.setGarrisonedBuildingId(decision.buildingId());
                LOG.info("AI unit {} garrisoned in bunker {} at {}",
                    decision.unitId(), decision.buildingId(), building.getPosition());
            }
        }
    }

    /**
     * Find an available Technology Centre for research.
     *
     * @param entities the entity manager
     * @param faction  the faction
     * @return an available Tech Centre, or null if none found
     */
    private Building findAvailableTechCentre(EntityManager entities, Faction faction) {
        List<Building> buildings = entities.getBuildingsForPlayer(faction);
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction() &&
                building.getBuildingType().researches() && building.isPowered() &&
                !building.isResearching()) {
                return building;
            }
        }
        return null;
    }

    /**
     * Decrease the active task count (called when a task completes).
     */
    public void taskCompleted() {
        if (activeTaskCount > 0) {
            activeTaskCount--;
        }
    }

    /**
     * Resets the active task count at the start of each decision cycle.
     * This prevents the counter from growing unboundedly due to tasks
     * that were started but never decremented.
     */
    public void resetTaskCount() {
        activeTaskCount = 0;
    }

    // --- Getters ---

    public AIDifficulty getDifficulty() {
        return difficulty;
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public long getLastDecisionTick() {
        return lastDecisionTick;
    }
}
