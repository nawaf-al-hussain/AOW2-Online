package com.aow2.core.ai;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI economy decision-making: building construction, resource management, and production.
 * <p>
 * REF: ai_analysis.md — AI build order priorities
 * <p>
 * Build priority order (from RE data):
 * 1. Command Centre (always first, generates income)
 * 2. Generator (needed to power other buildings)
 * 3. Infantry Centre (produce early units)
 * 4. Technology Centre (unlock research)
 * 5. Machine Factory (produce vehicles)
 * 6. Bunker/Tower (defensive buildings)
 * <p>
 * REF: ai_analysis.md — AI manages resources through building construction and unit production.
 * "if (playerUnitCount >= 50 || playerCredits < unitCost) { // Cannot produce - skip }"
 */
public final class EconomyAI {

    private static final Logger LOG = LoggerFactory.getLogger(EconomyAI.class);

    /** Distance threshold for triggering base defense. REF: ai_analysis.md — proximity-based defense. */
    private static final int DEFENSE_TRIGGER_DISTANCE = 20;

    /**
     * Decide what building to construct next based on build priority order.
     * <p>
     * REF: ai_analysis.md — AI build order priorities.
     * Priority: CC > Generator > Infantry Centre > Tech Centre > Machine Factory > Defensive.
     * <p>
     * ASSUMPTION: The AI follows a fixed build order but skips buildings already owned.
     *
     * @param entities the entity manager
     * @param economy  the economy system
     * @param playerId the AI player ID (0 or 1)
     * @return the BuildingType to construct, or null if nothing needed or affordable
     */
    public BuildingType decideNextBuilding(EntityManager entities, EconomySystem economy, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        // Count existing buildings by type (alive and complete)
        boolean hasHQ = false;
        boolean hasGenerator = false;
        boolean hasInfantryCentre = false;
        boolean hasTechCentre = false;
        boolean hasMachineFactory = false;
        boolean hasDefensive = false;

        for (Building building : buildings) {
            if (!building.isAlive() || building.isUnderConstruction()) {
                continue;
            }
            BuildingType type = building.getBuildingType();
            if (type.isHQ()) hasHQ = true;
            if (type.producesPower()) hasGenerator = true;
            if (type == BuildingType.CONFED_INFANTRY_CENTRE || type == BuildingType.REBEL_BARRACKS) {
                hasInfantryCentre = true;
            }
            if (type.researches()) hasTechCentre = true;
            if (type == BuildingType.CONFED_MACHINE_FACTORY || type == BuildingType.REBEL_FACTORY) {
                hasMachineFactory = true;
            }
            if (type.isDefensive()) hasDefensive = true;
        }

        // Build priority order: CC > Generator > Infantry > Tech > Factory > Defensive
        BuildingType[] buildOrder = getBuildOrder(faction);

        if (!hasHQ) return canAffordBuilding(buildOrder[0], economy, playerId) ? buildOrder[0] : null;
        if (!hasGenerator) return canAffordBuilding(buildOrder[1], economy, playerId) ? buildOrder[1] : null;
        if (!hasInfantryCentre) return canAffordBuilding(buildOrder[2], economy, playerId) ? buildOrder[2] : null;
        if (!hasTechCentre) return canAffordBuilding(buildOrder[3], economy, playerId) ? buildOrder[3] : null;
        if (!hasMachineFactory) return canAffordBuilding(buildOrder[4], economy, playerId) ? buildOrder[4] : null;
        if (!hasDefensive) return canAffordBuilding(buildOrder[5], economy, playerId) ? buildOrder[5] : null;

        // All basic buildings built — consider additional production buildings or defenses
        // ASSUMPTION: Build a second infantry centre if we have enough credits
        if (economy.canAfford(playerId, 50)) {
            // Build more production capacity
            return buildOrder[2]; // Second infantry centre
        }

        return null;
    }

    /**
     * Find a valid placement position for a building near existing base.
     * <p>
     * Searches positions around the player's Command Centre in expanding rings.
     * Positions must be: within CC radius, on buildable terrain, not overlapping buildings.
     *
     * @param type      the building type to place
     * @param entities  the entity manager
     * @param map       the game map
     * @param placement the building placement system
     * @param economy   the economy system
     * @param playerId  the AI player ID
     * @return a valid GridPosition for placement, or null if none found
     */
    public GridPosition findPlacementPosition(BuildingType type, EntityManager entities,
                                               GameMap map, BuildingPlacementSystem placement,
                                               EconomySystem economy, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        // Find the primary Command Centre
        GridPosition ccPos = null;
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().isHQ()) {
                ccPos = building.getPosition();
                break;
            }
        }

        if (ccPos == null) {
            LOG.debug("Player {} has no CC for placement reference", playerId);
            return null;
        }

        // Search in expanding rings around the CC
        for (int radius = 1; radius <= BuildingPlacementSystem.CC_PLACEMENT_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    // Only check the ring at this radius
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }

                    int x = ccPos.x() + dx;
                    int y = ccPos.y() + dy;
                    if (x < 0 || y < 0 || x > 127 || y > 127) {
                        continue;
                    }

                    GridPosition candidate = new GridPosition(x, y);
                    // Use the placement system for validation
                    if (placement.canPlace(type, candidate, playerId, entities, map, economy)) {
                        return candidate;
                    }
                }
            }
        }

        LOG.debug("No valid placement position found for {} for player {}", type, playerId);
        return null;
    }

    /**
     * Decide what units to produce across all production buildings.
     * <p>
     * REF: ai_analysis.md — "if (playerUnitCount >= 50 || playerCredits < unitCost) skip"
     * <p>
     * Production priority depends on game phase:
     * - Early: infantry (cheap, fast to produce)
     * - Mid: mix of infantry and vehicles
     * - Late: vehicle-heavy composition
     *
     * @param entities the entity manager
     * @param economy  the economy system
     * @param research the research system
     * @param playerId the AI player ID
     * @return a map of producer building entity ID -> unit type to produce
     */
    public Map<Integer, UnitType> decideProduction(EntityManager entities, EconomySystem economy,
                                                     ResearchSystem research, int playerId) {
        Map<Integer, UnitType> productionDecisions = new LinkedHashMap<>();
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Unit> aliveUnits = entities.getAliveUnitsForPlayer(faction);

        // REF: ai_analysis.md — unit cap of 50 per player
        if (aliveUnits.size() >= 50) {
            LOG.debug("Player {} at unit cap, no production", playerId);
            return productionDecisions;
        }

        // Count unit categories
        long infantryCount = aliveUnits.stream().filter(Unit::isInfantry).count();
        long vehicleCount = aliveUnits.stream().filter(Unit::isVehicle).count();

        // Find idle production buildings
        List<Building> producers = entities.getBuildingsForPlayer(faction).stream()
            .filter(b -> b.isAlive() && !b.isUnderConstruction() && b.isPowered()
                && b.getBuildingType().producesUnits() && !b.isProducing())
            .toList();

        for (Building producer : producers) {
            UnitType unitToProduce = decideUnitForProducer(
                producer, faction, economy, research, playerId, infantryCount, vehicleCount);
            if (unitToProduce != null) {
                productionDecisions.put(producer.getId(), unitToProduce);
            }
        }

        return productionDecisions;
    }

    /**
     * Check if base needs defensive buildings.
     * Triggers when enemy units are within the defense trigger distance of base.
     * <p>
     * REF: ai_analysis.md — AI builds defensive buildings in response to threats.
     * DEFENSE_TRIGGER_DISTANCE = 20 cells.
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @return true if defensive buildings should be constructed
     */
    public boolean needsDefense(EntityManager entities, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        Faction enemyFaction = faction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;

        // Find the player's Command Centre
        List<Building> buildings = entities.getBuildingsForPlayer(faction);
        GridPosition ccPos = null;
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().isHQ()) {
                ccPos = building.getPosition();
                break;
            }
        }

        if (ccPos == null) {
            return false; // No base to defend
        }

        // Check for enemy units near base
        List<Unit> enemyUnits = entities.getAliveUnitsForPlayer(enemyFaction);
        for (Unit enemy : enemyUnits) {
            if (enemy.getPosition().distanceTo(ccPos) <= DEFENSE_TRIGGER_DISTANCE) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the build order for a faction.
     * Returns building types in priority order.
     *
     * @param faction the faction
     * @return array of building types in priority order
     */
    private BuildingType[] getBuildOrder(Faction faction) {
        return switch (faction) {
            case CONFEDERATION -> new BuildingType[]{
                BuildingType.CONFED_COMMAND_CENTRE,
                BuildingType.CONFED_GENERATOR,
                BuildingType.CONFED_INFANTRY_CENTRE,
                BuildingType.CONFED_TECH_CENTRE,
                BuildingType.CONFED_MACHINE_FACTORY,
                BuildingType.CONFED_BUNKER
            };
            case RESISTANCE -> new BuildingType[]{
                BuildingType.REBEL_HEADQUARTERS,
                BuildingType.REBEL_POWERPLANT,
                BuildingType.REBEL_BARRACKS,
                BuildingType.REBEL_LABORATORY,
                BuildingType.REBEL_FACTORY,
                BuildingType.REBEL_BUNKER
            };
            default -> new BuildingType[]{};
        };
    }

    /**
     * Check if the player can afford a building type.
     * Uses the BuildingPlacementSystem's cost mapping.
     *
     * @param type     the building type
     * @param economy  the economy system
     * @param playerId the player ID
     * @return true if the player can afford the building
     */
    private boolean canAffordBuilding(BuildingType type, EconomySystem economy, int playerId) {
        int cost = getBuildingCost(type);
        return economy.canAfford(playerId, cost);
    }

    /**
     * Get the credit cost for a building type.
     * <p>
     * REF: complete_building_stats.json — costCredits per building type
     *
     * @param type the building type
     * @return the credit cost
     */
    private int getBuildingCost(BuildingType type) {
        return switch (type) {
            case CONFED_COMMAND_CENTRE -> 100;
            case CONFED_GENERATOR -> 20;
            case CONFED_INFANTRY_CENTRE -> 30;
            case CONFED_MACHINE_FACTORY -> 50;
            case CONFED_TECH_CENTRE -> 60;
            case CONFED_BUNKER -> 15;
            case CONFED_LOCATOR -> 40;
            case CONFED_ROCKET_LAUNCHER -> 45;
            case REBEL_HEADQUARTERS -> 100;
            case REBEL_POWERPLANT -> 20;
            case REBEL_BARRACKS -> 30;
            case REBEL_FACTORY -> 50;
            case REBEL_LABORATORY -> 60;
            case REBEL_BUNKER -> 15;
            case REBEL_TOWER -> 45;
            case REBEL_WALL -> 10;
        };
    }

    /**
     * Decide which unit to produce at a specific building.
     * <p>
     * REF: ai_analysis.md — AI prioritizes: vehicles > infantry, healthy > damaged
     * Production is balanced based on current army composition.
     *
     * @param producer       the production building
     * @param faction        the faction
     * @param economy        the economy system
     * @param research       the research system
     * @param playerId       the player ID
     * @param infantryCount  current infantry count
     * @param vehicleCount   current vehicle count
     * @return the unit type to produce, or null if nothing affordable
     */
    private UnitType decideUnitForProducer(Building producer, Faction faction,
                                            EconomySystem economy, ResearchSystem research,
                                            int playerId, long infantryCount, long vehicleCount) {
        boolean isInfantryProducer = producer.getBuildingType() == BuildingType.CONFED_INFANTRY_CENTRE ||
                                      producer.getBuildingType() == BuildingType.REBEL_BARRACKS;
        boolean isVehicleProducer = producer.getBuildingType() == BuildingType.CONFED_MACHINE_FACTORY ||
                                     producer.getBuildingType() == BuildingType.REBEL_FACTORY;

        if (isInfantryProducer) {
            return decideInfantryProduction(faction, economy, playerId, research);
        }
        if (isVehicleProducer) {
            return decideVehicleProduction(faction, economy, playerId, research);
        }
        return null;
    }

    /**
     * Decide which infantry unit to produce.
     * <p>
     * Priority: basic infantry first, then grenadiers, then special infantry.
     * REF: ai_analysis.md — infantry types 1,2,3 target other infantry first
     */
    private UnitType decideInfantryProduction(Faction faction, EconomySystem economy,
                                               int playerId, ResearchSystem research) {
        UnitType[] infantryOrder = switch (faction) {
            case CONFEDERATION -> new UnitType[]{
                UnitType.CONFED_INFANTRY,
                UnitType.CONFED_GRENADIER
            };
            case RESISTANCE -> new UnitType[]{
                UnitType.REBEL_INFANTRY,
                UnitType.REBEL_GRENADIER,
                UnitType.REBEL_SNIPER
            };
            default -> new UnitType[]{};
        };

        for (UnitType type : infantryOrder) {
            int cost = getUnitCost(type);
            if (economy.canAfford(playerId, cost)) {
                // Check tech requirement
                int techReq = getUnitTechRequirement(type);
                if (techReq == 0 || (research != null && research.hasResearch(playerId, techReq))) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Decide which vehicle unit to produce.
     * <p>
     * Priority: light vehicles first, then heavy vehicles.
     * REF: ai_analysis.md — machinery targets machinery first
     */
    private UnitType decideVehicleProduction(Faction faction, EconomySystem economy,
                                              int playerId, ResearchSystem research) {
        UnitType[] vehicleOrder = switch (faction) {
            case CONFEDERATION -> new UnitType[]{
                UnitType.CONFED_HAMMER,
                UnitType.CONFED_ZEUS,
                UnitType.CONFED_FLAME_ASSAULT,
                UnitType.CONFED_FORTRESS,
                UnitType.CONFED_TORRENT
            };
            case RESISTANCE -> new UnitType[]{
                UnitType.REBEL_COYOTE,
                UnitType.REBEL_RHINO,
                UnitType.REBEL_ARMADILLO,
                UnitType.REBEL_PORCUPINE
            };
            default -> new UnitType[]{};
        };

        for (UnitType type : vehicleOrder) {
            int cost = getUnitCost(type);
            if (economy.canAfford(playerId, cost)) {
                int techReq = getUnitTechRequirement(type);
                if (techReq == 0 || (research != null && research.hasResearch(playerId, techReq))) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Get the credit cost of a unit type.
     * REF: complete_unit_stats.json — costCredits per unit
     */
    private int getUnitCost(UnitType type) {
        return switch (type) {
            case CONFED_INFANTRY -> 10;
            case CONFED_GRENADIER -> 15;

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
     * REF: combat_formulas.md — research IDs for unit unlocks
     */
    private int getUnitTechRequirement(UnitType type) {
        return switch (type) {
            case CONFED_FLAME_ASSAULT -> 6;
            case CONFED_TORRENT -> 14;
            case REBEL_RHINO -> 12;
            case REBEL_PORCUPINE -> 38;
            default -> 0;
        };
    }
}
