package com.aow2.core.economy;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles building placement validation and construction.
 * <p>
 * Buildings must be within CC radius, not overlap terrain/buildings, and the
 * player must have sufficient credits and meet tech requirements.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 4 — Building placement rules
 * REF: complete_building_stats.json — building costs and tech requirements
 */
public final class BuildingPlacementSystem {

    private static final Logger LOG = LoggerFactory.getLogger(BuildingPlacementSystem.class);

    /** Maximum distance from a Command Centre for building placement. */
    public static final int CC_PLACEMENT_RADIUS = 20;

    /** Maximum number of buildings per player. REF: GameConstants.MAX_BUILDINGS_PER_PLAYER */
    private static final int MAX_BUILDINGS = 50;

    /**
     * Check if a building can be placed at the given position.
     * <p>
     * Rules:
     * 1. Must be within Command Centre radius
     * 2. Must not overlap impassable terrain
     * 3. Must not overlap existing buildings
     * 4. Must have sufficient credits
     * 5. Must meet tech requirements
     *
     * @param type     the building type to place
     * @param pos      the grid position for placement
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @param map      the game map
     * @param economy  the economy system
     * @return true if the building can be placed
     */
    public boolean canPlace(BuildingType type, GridPosition pos, int playerId,
                            EntityManager entities, GameMap map, EconomySystem economy) {
        Faction faction = EconomySystem.playerFaction(playerId);

        // Rule 1: Must be within Command Centre radius (unless placing a CC itself)
        if (!type.isHQ() && !isWithinCCRadius(pos, playerId, entities)) {
            LOG.debug("Placement rejected: position {} outside CC radius for player {}", pos, playerId);
            return false;
        }

        // Rule 2: Must not overlap impassable terrain
        if (!isTerrainBuildable(pos, map)) {
            LOG.debug("Placement rejected: position {} has impassable terrain", pos);
            return false;
        }

        // Rule 3: Must not overlap existing buildings
        if (entities.findBuildingAt(pos) != null) {
            LOG.debug("Placement rejected: position {} overlaps existing building", pos);
            return false;
        }

        // Rule 4: Must have sufficient credits
        int cost = getBuildingCost(type);
        if (!economy.canAfford(playerId, cost)) {
            LOG.debug("Placement rejected: player {} cannot afford {} credits", playerId, cost);
            return false;
        }

        // Rule 5: Must meet tech requirements
        if (!meetsTechRequirement(type, playerId, entities)) {
            LOG.debug("Placement rejected: player {} doesn't meet tech requirement for {}", playerId, type);
            return false;
        }

        // Additional: Check building count limit
        List<Building> existingBuildings = entities.getBuildingsForPlayer(faction);
        long aliveBuildings = existingBuildings.stream().filter(Building::isAlive).count();
        if (aliveBuildings >= MAX_BUILDINGS) {
            LOG.debug("Placement rejected: player {} has reached building limit", playerId);
            return false;
        }

        return true;
    }

    /**
     * Get valid placement positions for a building type (for ghost preview).
     * Returns all positions within CC radius that are on buildable terrain
     * with no existing buildings.
     *
     * @param type     the building type
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @param map      the game map
     * @return set of valid positions for placement
     */
    public Set<GridPosition> getValidPositions(BuildingType type, int playerId,
                                                EntityManager entities, GameMap map) {
        Set<GridPosition> valid = new HashSet<>();
        Faction faction = EconomySystem.playerFaction(playerId);

        // Get all Command Centres for this player
        List<Building> ccBuildings = entities.getBuildingsForPlayer(faction).stream()
            .filter(b -> b.isAlive() && b.getBuildingType().isHQ())
            .toList();

        if (ccBuildings.isEmpty() && !type.isHQ()) {
            return valid; // No CCs, can't place anything except another CC
        }

        // Scan all map positions within CC radii
        int radius = type.isHQ() ? Math.max(map.getWidth(), map.getHeight()) : CC_PLACEMENT_RADIUS;
        for (Building cc : ccBuildings) {
            GridPosition ccPos = cc.getPosition();
            int minX = Math.max(0, ccPos.x() - radius);
            int maxX = Math.min(map.getWidth() - 1, ccPos.x() + radius);
            int minY = Math.max(0, ccPos.y() - radius);
            int maxY = Math.min(map.getHeight() - 1, ccPos.y() + radius);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    GridPosition pos = new GridPosition(x, y);
                    if (ccPos.distanceTo(pos) <= radius &&
                        isTerrainBuildable(pos, map) &&
                        entities.findBuildingAt(pos) == null) {
                        valid.add(pos);
                    }
                }
            }
        }

        return valid;
    }

    /**
     * Place a building and start construction.
     * Deducts credits and creates the building entity in under-construction state.
     *
     * @param type     the building type to place
     * @param pos      the grid position for placement
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @param map      the game map
     * @param economy  the economy system
     * @return the newly placed Building, or null if placement failed
     */
    public Building placeBuilding(BuildingType type, GridPosition pos, int playerId,
                                  EntityManager entities, GameMap map, EconomySystem economy) {
        if (!canPlace(type, pos, playerId, entities, map, economy)) {
            return null;
        }

        Faction faction = EconomySystem.playerFaction(playerId);
        int cost = getBuildingCost(type);
        BuildingStats stats = createBuildingStats(type);

        // Deduct credits
        economy.spendCredits(playerId, cost);

        // Create the building
        int entityId = entities.allocateEntityId();
        Building building = new Building(entityId, faction, pos, type, stats);

        // Register the building
        entities.addBuilding(building);

        LOG.info("Player {} placed {} at {} for {} credits", playerId, type.displayName(), pos, cost);
        return building;
    }

    /**
     * Check if a position is within any Command Centre's placement radius.
     *
     * @param pos      the position to check
     * @param playerId the player ID
     * @param entities the entity manager
     * @return true if within CC radius
     */
    private boolean isWithinCCRadius(GridPosition pos, int playerId, EntityManager entities) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        for (Building building : buildings) {
            if (building.isAlive() && building.getBuildingType().isHQ()) {
                if (building.getPosition().distanceTo(pos) <= CC_PLACEMENT_RADIUS) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if the terrain at a position is suitable for building.
     * Buildings can only be placed on passable terrain (not water or mountain).
     *
     * @param pos the position to check
     * @param map the game map
     * @return true if the terrain is buildable
     */
    private boolean isTerrainBuildable(GridPosition pos, GameMap map) {
        if (!map.isInBounds(pos.x(), pos.y())) {
            return false;
        }
        TerrainType terrain = map.getTile(pos.x(), pos.y());
        if (terrain == null) {
            return false;
        }
        // ASSUMPTION: Only GRASS, SAND, FOREST, ROAD, RUINS, BRIDGE are buildable
        return terrain.isPassable() && terrain != TerrainType.WATER && terrain != TerrainType.MOUNTAIN;
    }

    /**
     * Check if the player meets the tech requirement for a building.
     * <p>
     * ASSUMPTION: Most buildings require no tech (techRequirement=0).
     * Advanced buildings may require specific research to be completed.
     *
     * @param type     the building type
     * @param playerId the player ID
     * @param entities the entity manager
     * @return true if the tech requirement is met
     */
    private boolean meetsTechRequirement(BuildingType type, int playerId, EntityManager entities) {
        // ASSUMPTION: All basic buildings have no tech requirement.
        // When ResearchSystem is integrated, this will check completed research.
        return true;
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
            // Confederation
            case CONFED_COMMAND_CENTRE -> 100;
            case CONFED_GENERATOR -> 20;
            case CONFED_INFANTRY_CENTRE -> 30;
            case CONFED_MACHINE_FACTORY -> 50;
            case CONFED_TECH_CENTRE -> 60;
            case CONFED_BUNKER -> 15;
            case CONFED_LOCATOR -> 40;
            case CONFED_ROCKET_LAUNCHER -> 45;
            // Resistance
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
     * Create BuildingStats for a building type.
     * <p>
     * ASSUMPTION: Using representative stat values from RE data.
     * Full integration with a stats registry will replace this.
     *
     * @param type the building type
     * @return the building stats
     */
    private BuildingStats createBuildingStats(BuildingType type) {
        return switch (type) {
            case CONFED_COMMAND_CENTRE -> new BuildingStats(type, 120, 100, 0, 10, 0, 10, 60, 0, 15, 0, 0, 5, 0, 100, 50, List.of(100, 200, 300));
            case CONFED_GENERATOR -> new BuildingStats(type, 60, 20, 0, 3, 0, 6, 30, 0, 5, 0, 10, 0, 0, 20, 10, List.of());
            case CONFED_INFANTRY_CENTRE -> new BuildingStats(type, 80, 30, 0, 5, 0, 8, 40, 0, 10, 5, 0, 5, 0, 30, 15, List.of(50, 100, 150));
            case CONFED_MACHINE_FACTORY -> new BuildingStats(type, 100, 50, 0, 7, 0, 8, 50, 0, 12, 8, 0, 5, 0, 50, 25, List.of(75, 150, 225));
            case CONFED_TECH_CENTRE -> new BuildingStats(type, 70, 60, 0, 4, 0, 8, 45, 0, 8, 5, 0, 1, 0, 60, 30, List.of());
            case CONFED_BUNKER -> new BuildingStats(type, 150, 15, 0, 12, 0, 7, 25, 5, 18, 3, 0, 1, 0, 15, 8, List.of());
            case CONFED_LOCATOR -> new BuildingStats(type, 50, 40, 0, 2, 0, 15, 35, 8, 5, 3, 0, 0, 0, 40, 20, List.of());
            case CONFED_ROCKET_LAUNCHER -> new BuildingStats(type, 90, 45, 0, 8, 0, 9, 40, 7, 12, 5, 0, 0, 0, 45, 22, List.of(50, 100));
            case REBEL_HEADQUARTERS -> new BuildingStats(type, 120, 100, 0, 10, 0, 10, 60, 0, 15, 0, 0, 5, 0, 100, 50, List.of(100, 200, 300));
            case REBEL_POWERPLANT -> new BuildingStats(type, 60, 20, 0, 3, 0, 6, 30, 0, 5, 0, 10, 0, 0, 20, 10, List.of());
            case REBEL_BARRACKS -> new BuildingStats(type, 80, 30, 0, 5, 0, 8, 40, 0, 10, 5, 0, 5, 0, 30, 15, List.of(50, 100, 150));
            case REBEL_FACTORY -> new BuildingStats(type, 100, 50, 0, 7, 0, 8, 50, 0, 12, 8, 0, 5, 0, 50, 25, List.of(75, 150, 225));
            case REBEL_LABORATORY -> new BuildingStats(type, 70, 60, 0, 4, 0, 8, 45, 0, 8, 5, 0, 1, 0, 60, 30, List.of());
            case REBEL_BUNKER -> new BuildingStats(type, 150, 15, 0, 12, 0, 7, 25, 5, 18, 3, 0, 1, 0, 15, 8, List.of());
            case REBEL_TOWER -> new BuildingStats(type, 90, 45, 0, 8, 0, 9, 40, 7, 12, 5, 0, 0, 0, 45, 22, List.of(50, 100));
            case REBEL_WALL -> new BuildingStats(type, 200, 10, 0, 15, 0, 0, 10, 0, 20, 0, 0, 0, 0, 10, 5, List.of());
        };
    }
}
