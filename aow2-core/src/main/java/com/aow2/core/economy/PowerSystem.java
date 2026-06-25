package com.aow2.core.economy;

import com.aow2.common.config.GameConfig;
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
            // REF: combat_formulas.md — power radius uses distanceClass (Chebyshev), not Euclidean
            int dx = building.getPosition().x() - genPos.x();
            int dy = building.getPosition().y() - genPos.y();
            if (GridPosition.distanceClass(dx, dy) <= radius) {
                powered.add(building);
            }
        }

        return powered;
    }

    /**
     * Get the power radius for a Generator at its current upgrade level.
     * <p>
     * FIX (L2 from CRITICAL_ANALYSIS_REPORT.md): Migrated from the deprecated
     * GameConstants.BUILDING_POWER_RADIUS array to GameConfig.getInstance().getBuildingPowerRadius()
     * as the single source of truth.
     * <p>
     * Level 0 = radius 10, Level 1 = radius 20, etc.
     * Max level = 5 = radius 127 (full map coverage)
     *
     * @param generator the Generator building
     * @return the power radius in grid cells
     */
    public int getPowerRadius(Building generator) {
        int level = getUpgradeLevel(generator);
        int[] radii = GameConfig.getInstance().getBuildingPowerRadius();
        int index = Math.min(level, radii.length - 1);
        return radii[index];
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
            // REF: combat_formulas.md — power radius uses distanceClass (Chebyshev), not Euclidean
            int dx = pos.x() - building.getPosition().x();
            int dy = pos.y() - building.getPosition().y();
            if (GridPosition.distanceClass(dx, dy) <= radius) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the upgrade level of a building.
     * <p>
     * ASSUMPTION: upgrade level is stored via the building's stats upgrade costs.
     * Currently returns the actual upgrade level reported by the Building entity
     * (0 = base, 3 = max). The Building.upgradeLevel field is incremented when the
     * player pays the upgrade cost — but the upgrade-payment flow is NOT yet wired
     * (see <code>ProjectProgress.md</code> Phase 5 FIX-M5).
     * <p>
     * FIX (M5 from CRITICAL_ANALYSIS_REPORT.md): Previously the code comment said
     * "Currently returns 0 as base level until the upgrade system is fully implemented"
     * but the implementation already delegated to {@link Building#getUpgradeLevel()}.
     * The original comment was stale — the delegation was already correct. The actual
     * gap is that nothing INCREMENTS the upgrade level after construction completes.
     * That work is tracked as Phase 13 "Polish & Optimization" — building upgrades
     * require credit deduction + animation + re-validation of power grid coverage.
     * <p>
     * Until that lands, all generators use level 0 → radius 10. This is acceptable
     * for v0.1.x because the only way to gain levels is via the not-yet-wired upgrade
     * command.
     *
     * @param building the building to check
     * @return the upgrade level (0-based; max 3)
     */
    private int getUpgradeLevel(Building building) {
        // Delegate to the building entity. When the upgrade-payment flow is implemented
        // (Phase 13), Building.upgradeLevel will be incremented via a new UpgradeCommand
        // and this method will automatically reflect the new level.
        int level = building.getUpgradeLevel();
        if (level < 0 || level > 3) {
            LOG.warn("Building {} reported out-of-range upgrade level {} — clamping to [0, 3]",
                building.getId(), level);
            return Math.max(0, Math.min(3, level));
        }
        return level;
    }
}
