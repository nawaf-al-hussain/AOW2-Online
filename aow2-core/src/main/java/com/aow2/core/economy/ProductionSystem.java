package com.aow2.core.economy;

import com.aow2.common.config.GameConstants;
import com.aow2.common.config.StatsRegistry;
import com.aow2.common.event.UnitProducedEvent;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.research.ResearchSystem;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages unit production queues in production buildings.
 * <p>
 * Infantry Centre produces infantry, Machine Factory produces vehicles.
 * Production time is governed by the formula from the RE data.
 * <p>
 * REF: combat_formulas.md "Production Time Formula"
 * REF: complete_unit_stats.json — buildTime per unit type
 * REF: complete_building_stats.json — queueSlots per building
 */
public final class ProductionSystem {

    private static final Logger LOG = LoggerFactory.getLogger(ProductionSystem.class);

    // ASSUMPTION (L9): 50% refund on production cancel — RE spec doesn't document exact refund percentage
    // REF: MASTER_DOCUMENTATION.md — production cancellation exists but no refund rate specified
    // The original game may have a different refund rate or no refund at all.
    private static final double CANCEL_REFUND_PERCENT = 0.50;

    /**
     * Process all production queues for one tick.
     * Progresses production on all alive, powered production buildings.
     * Completes units when their build time is reached.
     *
     * @param entities the entity manager
     * @param state    the current game state
     * @param economy  the economy system (for income tracking on completion)
     */
    public void processTick(EntityManager entities, GameState state, EconomySystem economy) {
        for (Building building : entities.getAllBuildings()) {
            if (!building.isAlive() || building.isUnderConstruction()) {
                continue;
            }
            if (!building.getBuildingType().producesUnits()) {
                continue;
            }
            if (!building.isPowered()) {
                continue;
            }
            if (!building.isProducing()) {
                continue;
            }

            // Advance production by one tick
            building.advanceProduction();

            // Check if current production is complete
            UnitType current = building.getCurrentProduction();
            if (current != null) {
                int requiredTime = calculateProductionTime(current, building);
                if (building.getProductionProgress() >= requiredTime) {
                    UnitType completed = building.completeCurrentProduction();
                    if (completed != null) {
                        spawnUnit(completed, building, entities, state, economy);
                    }
                }
            }
        }
    }

    /**
     * Enqueue a unit for production.
     * <p>
     * Checks: building is powered, player can afford, tech requirement met, queue not full.
     *
     * @param producer  the production building
     * @param unitType  the unit type to produce
     * @param playerId  the player ID (0 or 1)
     * @param economy   the economy system
     * @param research  the research system (for tech checks)
     * @return true if the unit was enqueued successfully
     */
    public boolean enqueueUnit(Building producer, UnitType unitType, int playerId,
                               EconomySystem economy, ResearchSystem research) {
        // Check building is alive and complete
        if (!producer.isAlive() || producer.isUnderConstruction()) {
            LOG.debug("Cannot enqueue: building not ready");
            return false;
        }

        // Check building can produce units
        if (!producer.getBuildingType().producesUnits()) {
            LOG.debug("Cannot enqueue: building doesn't produce units");
            return false;
        }

        // Check building is powered
        if (!producer.isPowered()) {
            LOG.debug("Cannot enqueue: building not powered");
            return false;
        }

        // Check unit type matches building type
        if (!isValidProducerForUnit(producer.getBuildingType(), unitType)) {
            LOG.debug("Cannot enqueue: {} cannot produce {}", producer.getBuildingType(), unitType);
            return false;
        }

        // Check tech requirement
        int techReq = getUnitTechRequirement(unitType);
        if (techReq > 0 && research != null && !research.hasResearch(playerId, techReq)) {
            LOG.debug("Cannot enqueue: tech requirement {} not met for {}", techReq, unitType);
            return false;
        }

        // Check player can afford
        int cost = getUnitCost(unitType);
        if (!economy.canAfford(playerId, cost)) {
            LOG.debug("Cannot enqueue: player {} cannot afford {} credits", playerId, cost);
            return false;
        }

        // Check queue capacity
        if (!producer.enqueueProduction(unitType)) {
            LOG.debug("Cannot enqueue: production queue full");
            return false;
        }

        // Deduct credits
        economy.spendCredits(playerId, cost);

        LOG.info("Player {} enqueued {} at {} for {} credits",
            playerId, unitType.displayName(), producer.getBuildingType().displayName(), cost);
        return true;
    }

    /**
     * Cancel a production item. Refunds partial credits.
     *
     * @param producer   the production building
     * @param queueIndex the index in the production queue (0-based)
     * @param playerId   the player ID (0 or 1)
     * @param economy    the economy system
     * @return true if the production was cancelled successfully
     */
    public boolean cancelProduction(Building producer, int queueIndex, int playerId,
                                    EconomySystem economy) {
        var queue = producer.getProductionQueue();
        if (queueIndex < 0 || queueIndex >= queue.size()) {
            LOG.debug("Cannot cancel: invalid queue index {}", queueIndex);
            return false;
        }

        UnitType cancelledType = queue.get(queueIndex);
        int refund = (int) (getUnitCost(cancelledType) * CANCEL_REFUND_PERCENT);
        economy.addCredits(playerId, refund);

        // Build the remaining list after removing the cancelled item
        java.util.List<UnitType> remaining = new java.util.ArrayList<>(queue);
        remaining.remove(queueIndex);

        // Only clear active production if the cancelled item is the one currently being produced.
        // The production queue contains only pending items; the currently producing item is
        // stored separately in currentProduction. However, we check by value to handle edge cases.
        if (producer.getCurrentProduction() != null && producer.getCurrentProduction() == cancelledType) {
            producer.setCurrentProduction(null);
            producer.setProductionProgress(0);
        }

        // Rebuild the production queue: clear and re-enqueue remaining items
        producer.clearProductionQueue();
        for (UnitType type : remaining) {
            producer.enqueueProduction(type);
        }

        LOG.info("Player {} cancelled {} production, refund: {} credits", playerId, cancelledType.displayName(), refund);
        return true;
    }

    /**
     * Calculate production time for a unit type.
     * <p>
     * REF: combat_formulas.md — production time formula:
     * effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)
     * <p>
     * ASSUMPTION: productionModifier defaults to 10, upgradeBonus defaults to 0
     * until the upgrade system is fully implemented.
     *
     * @param unitType the unit type
     * @param producer the producing building
     * @return the number of ticks required to produce the unit
     */
    public int calculateProductionTime(UnitType unitType, Building producer) {
        int baseBuildTime = getUnitBuildTime(unitType);
        int productionModifier = 10; // ASSUMPTION: base modifier = 10

        // FIX: Use actual building upgrade level instead of hardcoded 0.
        // ASSUMPTION: each upgrade level adds +5 to the upgrade bonus.
        // The upgradeLevel is tracked on the Building entity (0 = base, 1-3 = upgraded).
        // REF: combat_formulas.md — "(baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)"
        int upgradeLevel = producer.getUpgradeLevel();
        int upgradeBonus = upgradeLevel * 5; // ASSUMPTION: +5 per upgrade level

        int effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20);

        return Math.max(effectiveBuildTime, 1);
    }

    /**
     * Spawn a completed unit at the producing building's position.
     *
     * @param unitType the completed unit type
     * @param producer the building that produced it
     * @param entities the entity manager
     * @param state    the game state
     * @param economy  the economy system
     */
    private void spawnUnit(UnitType unitType, Building producer, EntityManager entities,
                           GameState state, EconomySystem economy) {
        int playerId = EconomySystem.playerId(producer.getFaction());
        Faction faction = producer.getFaction();

        // Check unit cap
        List<Unit> aliveUnits = entities.getAliveUnitsForPlayer(faction);
        if (aliveUnits.size() >= GameConstants.MAX_UNITS_PER_PLAYER) {
            LOG.debug("Cannot spawn {}: player {} at unit cap", unitType, playerId);
            return;
        }

        // Determine spawn position (at building or waypoint)
        com.aow2.common.model.GridPosition spawnPos = producer.getWaypoint();
        if (spawnPos == null) {
            spawnPos = producer.getPosition().offset(1, 0);
        }

        // Create the unit
        int entityId = entities.allocateEntityId();
        com.aow2.common.model.UnitStats stats = createUnitStats(unitType);
        Unit unit = new Unit(entityId, faction, spawnPos, unitType, stats);
        entities.addUnit(unit);

        // Fire event
        state.enqueueEvent(new UnitProducedEvent(
            state.currentTick(), playerId, unitType, entityId
        ));

        LOG.info("Player {} produced {} (id={}) at {}",
            playerId, unitType.displayName(), entityId, producer.getBuildingType().displayName());
    }

    /**
     * Check if a building type can produce a given unit type.
     * Infantry Centres produce infantry, Machine Factories produce vehicles.
     *
     * @param buildingType the building type
     * @param unitType     the unit type
     * @return true if the building can produce the unit
     */
    private boolean isValidProducerForUnit(BuildingType buildingType, UnitType unitType) {
        boolean isInfantryBuilding = buildingType == BuildingType.CONFED_INFANTRY_CENTRE ||
                                      buildingType == BuildingType.REBEL_BARRACKS;
        boolean isVehicleBuilding = buildingType == BuildingType.CONFED_MACHINE_FACTORY ||
                                     buildingType == BuildingType.REBEL_FACTORY;

        if (isInfantryBuilding) {
            return unitType.category() == UnitCategory.INFANTRY || unitType.category() == UnitCategory.MINE;
        }
        if (isVehicleBuilding) {
            // ASSUMPTION (M13): Flame Assault (SPECIAL_MACHINERY) is built in the vehicle factory.
            // RE data has a category conflict: Flame Assault has typeId=10 which the RE spec
            // lists under "special_machinery", but its availability flag suggests it may be
            // built from the Infantry Centre in the original game. We place it in the
            // vehicle factory until confirmed otherwise.
            return unitType.category() == UnitCategory.VEHICLE || unitType.category() == UnitCategory.SPECIAL_MACHINERY;
        }
        return false;
    }

    /**
     * Get the build time for a unit type.
     * REF: StatsRegistry
     *
     * @param unitType the unit type
     * @return the base build time in ticks
     */
    private int getUnitBuildTime(UnitType unitType) {
        return StatsRegistry.getInstance().getUnitBuildTime(unitType); // REF: StatsRegistry
    }

    /**
     * Get the credit cost of a unit type.
     * REF: StatsRegistry
     *
     * @param unitType the unit type
     * @return the credit cost
     */
    private int getUnitCost(UnitType unitType) {
        return StatsRegistry.getInstance().getUnitCost(unitType); // REF: StatsRegistry
    }

    /**
     * Get the tech requirement for a unit type.
     * REF: StatsRegistry
     *
     * @param unitType the unit type
     * @return the required research ID, or 0 if no requirement
     */
    private int getUnitTechRequirement(UnitType unitType) {
        return StatsRegistry.getInstance().getUnitTechRequirement(unitType); // REF: StatsRegistry
    }

    /**
     * Create UnitStats for a unit type.
     * REF: StatsRegistry
     *
     * @param unitType the unit type
     * @return the unit stats
     */
    private com.aow2.common.model.UnitStats createUnitStats(UnitType unitType) {
        return StatsRegistry.getInstance().getUnitStats(unitType); // REF: StatsRegistry
    }
}
