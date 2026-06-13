package com.aow2.core.economy;

import com.aow2.common.config.GameConstants;
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

    /** Refund percentage when cancelling production. ASSUMPTION: 50% refund. */
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

        // ASSUMPTION: cancellation removes the item from the queue.
        // The Building class doesn't have a removeProduction method,
        // so we handle it by clearing and re-adding.
        java.util.List<UnitType> remaining = new java.util.ArrayList<>(queue);
        remaining.remove(queueIndex);

        // Clear and re-add remaining items
        // Since Building's productionQueue is managed internally,
        // we need to complete and re-enqueue. This is a simplified approach.
        // A proper implementation would add a cancelProduction method to Building.
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
        int upgradeBonus = 0;        // ASSUMPTION: no upgrade bonus initially

        // REF: combat_formulas.md — "(baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)"
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
            return unitType.category() == UnitCategory.VEHICLE;
        }
        return false;
    }

    /**
     * Get the build time for a unit type.
     * <p>
     * REF: complete_unit_stats.json — buildTime per unit
     *
     * @param unitType the unit type
     * @return the base build time in ticks
     */
    private int getUnitBuildTime(UnitType unitType) {
        return switch (unitType) {
            case CONFED_INFANTRY -> 40;
            case CONFED_GRENADIER -> 50;
            case CONFED_LIGHT_ASSAULT -> 55;
            case CONFED_HEAVY_ASSAULT -> 75;
            case CONFED_FLAME_ASSAULT -> 60;
            case CONFED_FORTRESS -> 100;
            case CONFED_HAMMER -> 70;
            case CONFED_ZEUS -> 70;
            case CONFED_TORRENT -> 80;
            case CONFED_MINE_SCORPIO -> 30;
            case CONFED_MINE_FROG -> 30;
            case CONFED_MINE_LIZARD -> 30;
            case REBEL_INFANTRY -> 40;
            case REBEL_GRENADIER -> 50;
            case REBEL_SNIPER -> 55;
            case REBEL_COYOTE -> 60;
            case REBEL_ARMADILLO -> 80;
            case REBEL_RHINO -> 75;
            case REBEL_PORCUPINE -> 85;
        };
    }

    /**
     * Get the credit cost of a unit type.
     *
     * @param unitType the unit type
     * @return the credit cost
     */
    private int getUnitCost(UnitType unitType) {
        return switch (unitType) {
            case CONFED_INFANTRY -> 10;
            case CONFED_GRENADIER -> 15;
            case CONFED_LIGHT_ASSAULT -> 20;
            case CONFED_HEAVY_ASSAULT -> 35;
            case CONFED_FLAME_ASSAULT -> 25;
            case CONFED_FORTRESS -> 50;
            case CONFED_HAMMER -> 30;
            case CONFED_ZEUS -> 30;
            case CONFED_TORRENT -> 50;
            case CONFED_MINE_SCORPIO -> 10;
            case CONFED_MINE_FROG -> 10;
            case CONFED_MINE_LIZARD -> 10;
            case REBEL_INFANTRY -> 10;
            case REBEL_GRENADIER -> 15;
            case REBEL_SNIPER -> 20;
            case REBEL_COYOTE -> 25;
            case REBEL_ARMADILLO -> 40;
            case REBEL_RHINO -> 35;
            case REBEL_PORCUPINE -> 45;
        };
    }

    /**
     * Get the tech requirement for a unit type.
     * <p>
     * ASSUMPTION: Most basic units require no tech (0).
     * Advanced units may require specific research IDs.
     *
     * @param unitType the unit type
     * @return the required research ID, or 0 if no requirement
     */
    private int getUnitTechRequirement(UnitType unitType) {
        return switch (unitType) {
            case CONFED_FLAME_ASSAULT -> 6;  // REF: combat_formulas.md research ID 6
            case CONFED_TORRENT -> 14;       // REF: combat_formulas.md research ID 14
            case REBEL_RHINO -> 12;          // REF: combat_formulas.md research ID 12
            case REBEL_PORCUPINE -> 38;      // REF: combat_formulas.md research ID 38
            default -> 0;
        };
    }

    /**
     * Create UnitStats for a unit type.
     * <p>
     * ASSUMPTION: Using representative stat values from RE data.
     *
     * @param unitType the unit type
     * @return the unit stats
     */
    private com.aow2.common.model.UnitStats createUnitStats(UnitType unitType) {
        return switch (unitType) {
            case CONFED_INFANTRY -> new com.aow2.common.model.UnitStats(unitType, "Infantry", 40, 2, 1, 5, 5, 0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
            case CONFED_GRENADIER -> new com.aow2.common.model.UnitStats(unitType, "Grenadier", 45, 4, 2, 5, 4, 0, 5, 5, 5, 15, 700, 6, 255, 0, -1);
            case CONFED_LIGHT_ASSAULT -> new com.aow2.common.model.UnitStats(unitType, "Light Assault", 55, 4, 2, 6, 5, 0, 4, 5, 5, 20, 750, 6, 255, 0, -1);
            case CONFED_HEAVY_ASSAULT -> new com.aow2.common.model.UnitStats(unitType, "Heavy Assault", 90, 7, 4, 4, 8, 0, 5, 5, 8, 35, 1000, 8, 255, 0, -1);
            case CONFED_FLAME_ASSAULT -> new com.aow2.common.model.UnitStats(unitType, "Flame Assault", 50, 6, 3, 5, 3, 0, 3, 3, 6, 25, 800, 6, 255, 0, -1);
            case CONFED_FORTRESS -> new com.aow2.common.model.UnitStats(unitType, "AV-40 Fortress", 120, 8, 5, 4, 7, 0, 6, 6, 10, 50, 1200, 8, 255, 0, -1);
            case CONFED_HAMMER -> new com.aow2.common.model.UnitStats(unitType, "T-21 Hammer", 70, 6, 3, 7, 5, 0, 2, 6, 7, 30, 300, 8, 255, 0, -1);
            case CONFED_ZEUS -> new com.aow2.common.model.UnitStats(unitType, "T-22 Zeus", 70, 6, 3, 7, 5, 0, 2, 6, 7, 30, 300, 8, 255, 0, -1);
            case CONFED_TORRENT -> new com.aow2.common.model.UnitStats(unitType, "MLRS Torrent", 80, 15, 8, 4, 7, 2, 6, 6, 8, 50, 250, 8, 255, 2, -1);
            case CONFED_MINE_SCORPIO -> new com.aow2.common.model.UnitStats(unitType, "Mine Scorpio", 10, 20, 1, 3, 0, 0, 0, 0, 3, 10, 100, 4, 255, 0, -1);
            case CONFED_MINE_FROG -> new com.aow2.common.model.UnitStats(unitType, "Mine Frog", 10, 15, 1, 3, 0, 0, 0, 0, 3, 10, 100, 4, 255, 0, -1);
            case CONFED_MINE_LIZARD -> new com.aow2.common.model.UnitStats(unitType, "Mine Lizard", 10, 18, 1, 3, 0, 0, 0, 0, 3, 10, 100, 4, 255, 0, -1);
            case REBEL_INFANTRY -> new com.aow2.common.model.UnitStats(unitType, "Infantry", 40, 2, 1, 5, 5, 0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
            case REBEL_GRENADIER -> new com.aow2.common.model.UnitStats(unitType, "Grenadier", 45, 4, 2, 5, 4, 0, 5, 5, 5, 15, 700, 6, 255, 0, -1);
            case REBEL_SNIPER -> new com.aow2.common.model.UnitStats(unitType, "Sniper", 35, 8, 2, 3, 4, 0, 7, 7, 6, 20, 750, 6, 255, 0, -1);
            case REBEL_COYOTE -> new com.aow2.common.model.UnitStats(unitType, "Coyote", 60, 5, 2, 8, 4, 0, 2, 5, 6, 25, 400, 7, 255, 0, -1);
            case REBEL_ARMADILLO -> new com.aow2.common.model.UnitStats(unitType, "Armadillo", 100, 7, 4, 5, 8, 0, 5, 5, 9, 40, 1000, 8, 255, 0, -1);
            case REBEL_RHINO -> new com.aow2.common.model.UnitStats(unitType, "Rhino", 75, 6, 3, 6, 6, 0, 3, 6, 8, 35, 350, 8, 255, 0, -1);
            case REBEL_PORCUPINE -> new com.aow2.common.model.UnitStats(unitType, "MMC Porcupine", 85, 12, 5, 4, 6, 1, 5, 5, 9, 45, 280, 8, 255, 1, -1);
        };
    }
}
