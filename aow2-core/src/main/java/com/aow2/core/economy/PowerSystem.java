package com.aow2.core.economy;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the Generator power grid system.
 * <p>
 * Buildings must be powered to function (produce units, research, attack).
 * A Generator provides power to all buildings within its radius. The radius
 * depends on the Generator's upgrade level, using the BUILDING_POWER_RADIUS array.
 * <p>
 * REF: GameConstants.BUILDING_POWER_RADIUS = [10, 20, 30, 40, 60, 127]
 * REF: GameConstants.POWER_UPGRADE_BONUS_PERCENT = 0.30
 * REF: combat_formulas.md — building radius values from RE data
 */
public final class PowerSystem {

    private static final Logger LOG = LoggerFactory.getLogger(PowerSystem.class);

    /**
     * Check if a building is powered (within Generator radius).
     * Power radius depends on Generator upgrade level.
     * A building is powered if it is within the radius of any alive, completed
     * Generator owned by the same faction.
     * <p>
     * ASSUMPTION: Command Centre and Generator itself are always considered powered.
     *
     * @param building the building to check
     * @param entities the entity manager
     * @return true if the building is powered
     */
    public boolean isBuildingPowered(Building building, EntityManager entities) {
        // Command Centres / HQs are always powered
        if (building.getBuildingType().isHQ()) {
            return true;
        }
        // Generators power themselves
        if (building.getBuildingType().producesPower()) {
            return building.isAlive() && !building.isUnderConstruction();
        }

        int playerId = EconomySystem.playerId(building.getFaction());
        return isPositionPowered(building.getPosition(), playerId, entities);
    }

    /**
     * Get all buildings powered by a specific Generator.
     * Returns all alive, completed buildings within the Generator's power radius
     * that belong to the same faction.
     *
     * @param generator the Generator building
     * @param entities  the entity manager
     * @return list of buildings powered by this Generator
     */
    public List<Building> getPoweredBuildings(Building generator, EntityManager entities) {
        List<Building> powered = new ArrayList<>();
        if (!generator.isAlive() || generator.isUnderConstruction() || !generator.getBuildingType().producesPower()) {
            return powered;
        }

        int radius = getPowerRadius(generator);
        GridPosition genPos = generator.getPosition();

        List<Building> allBuildings = entities.getBuildingsForPlayer(generator.getFaction());
        for (Building building : allBuildings) {
            if (building.getId() == generator.getId()) {
                continue; // Skip the generator itself
            }
            if (!building.isAlive() || building.isUnderConstruction()) {
                continue;
            }
            if (genPos.distanceTo(building.getPosition()) <= radius) {
                powered.add(building);
            }
        }

        return powered;
    }

    /**
     * Get the power radius for a Generator at its current upgrade level.
     * <p>
     * REF: GameConstants.BUILDING_POWER_RADIUS = [10, 20, 30, 40, 60, 127]
     * Level 0 = radius 10, Level 1 = radius 20, etc.
     * Max level = 5 = radius 127 (full map coverage)
     * <p>
     * ASSUMPTION: Generator upgrade level is derived from construction completion
     * and building stats. For now, uses a simple level calculation based on
     * the building's upgrade state (tracked via construction progress milestones).
     *
     * @param generator the Generator building
     * @return the power radius in grid cells
     */
    public int getPowerRadius(Building generator) {
        int level = getUpgradeLevel(generator);
        int index = Math.min(level, GameConstants.BUILDING_POWER_RADIUS.length - 1);
        return GameConstants.BUILDING_POWER_RADIUS[index];
    }

    /**
     * Update power state of all buildings for all players.
     * Called when a Generator is built, destroyed, or upgraded.
     * Iterates through all buildings and recalculates their powered state.
     *
     * @param entities the entity manager
     */
    public void updatePowerGrid(EntityManager entities) {
        for (Building building : entities.getAllBuildings()) {
            if (!building.isAlive()) {
                continue;
            }
            boolean wasPowered = building.isPowered();
            boolean nowPowered = isBuildingPowered(building, entities);
            building.setPowered(nowPowered);

            if (wasPowered && !nowPowered) {
                LOG.debug("Building {} lost power", building.getId());
            } else if (!wasPowered && nowPowered) {
                LOG.debug("Building {} gained power", building.getId());
            }
        }
    }

    /**
     * Check if a position is within any Generator's power radius for a player.
     *
     * @param pos      the grid position to check
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @return true if the position is within at least one Generator's radius
     */
    public boolean isPositionPowered(GridPosition pos, int playerId, EntityManager entities) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        for (Building building : buildings) {
            if (!building.isAlive() || building.isUnderConstruction()) {
                continue;
            }
            if (!building.getBuildingType().producesPower()) {
                continue;
            }
            int radius = getPowerRadius(building);
            if (building.getPosition().distanceTo(pos) <= radius) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the upgrade level of a building.
     * <p>
     * ASSUMPTION: upgrade level is stored via the building's stats upgrade costs.
     * Currently returns 0 as base level until the upgrade system is fully implemented.
     * The level will be determined by how many upgrade costs have been paid.
     *
     * @param building the building to check
     * @return the upgrade level (0-based)
     */
    private int getUpgradeLevel(Building building) {
        // FIX: Return the actual upgrade level from the building entity.
        // Building.upgradeLevel is 0 (base) to 3 (max upgraded).
        // REF: Building.java — getUpgradeLevel() / setUpgradeLevel(int)
        return building.getUpgradeLevel();
    }
}
