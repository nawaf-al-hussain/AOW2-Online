package com.aow2.core.ai;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
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
import java.util.Random;

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

    /** Random number generator for strategy quality decisions. */
    private final Random random;

    /** The fog of war system, used to limit AI vision to visible tiles only. */
    private FogOfWarSystem fogOfWar;

    /** Tick counter for decision timing. */
    private long lastDecisionTick;

    /** Number of currently active AI tasks. */
    private int activeTaskCount;

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
        // Seeded RNG for lockstep determinism — each AI player gets a deterministic seed
        this.random = new Random(playerId * 31L + 42L);
        this.lastDecisionTick = -difficulty.tickInterval; // Allow immediate first decision
        this.activeTaskCount = 0;
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
        processResearchDecisions(entities, economy, research);
        taskCompleted();
        processMilitaryDecisions(entities, map, movement);
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
                                           ResearchSystem research) {
        TechTree techTree = new TechTree();
        int nextResearch = researchAI.decideNextResearch(entities, research, techTree, playerId);
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
                                           MovementSystem movement) {
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
     * REF: ai_analysis.md — AI attack decision, units move toward target
     */
    private void executeAttack(MilitaryAction.Attack attack, EntityManager entities,
                                GameMap map, MovementSystem movement) {
        for (int unitId : attack.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                movement.issueMoveCommand(unit, attack.target(), map, entities);
                LOG.debug("AI unit {} attacking toward {}", unitId, attack.target());
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
