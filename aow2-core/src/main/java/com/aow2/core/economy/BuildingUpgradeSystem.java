package com.aow2.core.economy;

import com.aow2.common.config.StatsRegistry;
import com.aow2.common.model.BuildingType;
import com.aow2.core.entity.Building;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles building upgrades (3 levels per building).
 * REF: MASTER_DOCUMENTATION.md — building upgrade system
 * REF: building_stats.md — 1x3 Upgrade levels, Shield bonus, Resource type / upgrade level
 * REF: complete_building_stats.json — upgradeCosts per building
 *
 * Buildings can be upgraded up to 3 times. Each upgrade increases:
 * - HP (construction HP cap increases)
 * - Production speed (reduced training/build time)
 * - Attack power for defensive buildings
 *
 * Upgrade costs are defined in BuildingStats.upgradeCosts().
 * Production speed formula: effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)
 */
public final class BuildingUpgradeSystem {

    private static final Logger LOG = LoggerFactory.getLogger(BuildingUpgradeSystem.class);

    /** Maximum upgrade level for any building. REF: building_stats.md — 1x3 Upgrade levels */
    public static final int MAX_UPGRADE_LEVEL = 3;

    public BuildingUpgradeSystem() {
        // Stateless system — all data comes from Building and StatsRegistry
    }

    /**
     * Attempt to upgrade a building to the next level.
     * The player must have enough credits, and the building must not already be at max level.
     *
     * REF: building_stats.md — upgrade levels and costs
     *
     * @param building the building to upgrade
     * @param playerId the player who owns the building
     * @param economy  the economy system for credit deduction
     * @return true if the upgrade was successful
     */
    public boolean upgradeBuilding(Building building, int playerId, EconomySystem economy) {
        if (!building.isAlive()) {
            LOG.debug("Cannot upgrade: building {} is destroyed", building.getId());
            return false;
        }

        if (building.isUnderConstruction()) {
            LOG.debug("Cannot upgrade: building {} is still under construction", building.getId());
            return false;
        }

        int currentLevel = building.getUpgradeLevel();
        if (currentLevel >= MAX_UPGRADE_LEVEL) {
            LOG.debug("Cannot upgrade: building {} already at max level {}", building.getId(), currentLevel);
            return false;
        }

        // Get the upgrade cost for the next level
        int upgradeCost = getUpgradeCost(building, currentLevel + 1);
        if (upgradeCost <= 0) {
            LOG.debug("Cannot upgrade: no cost defined for building {} level {}", building.getId(), currentLevel + 1);
            return false;
        }

        // Check if the player can afford the upgrade
        if (!economy.canAfford(playerId, upgradeCost)) {
            LOG.debug("Cannot upgrade: player {} cannot afford {} credits", playerId, upgradeCost);
            return false;
        }

        // Deduct credits
        economy.spendCredits(playerId, upgradeCost);

        // Apply the upgrade
        building.setUpgradeLevel(currentLevel + 1);

        // REF: building_stats.md — upgrade increases HP cap and production speed
        // Increase HP by 20% per level (applied to base maxHp), accumulated across levels
        int hpIncrease = (int)(building.getStats().maxHp() * 0.20);
        building.setUpgradeMaxHpBonus(building.getUpgradeMaxHpBonus() + hpIncrease);
        building.heal(hpIncrease); // Also heal the HP increase

        LOG.info("Building {} upgraded to level {} for {} credits (HP+{})",
            building.getId(), currentLevel + 1, upgradeCost, hpIncrease);

        return true;
    }

    /**
     * Get the cost to upgrade a building to a specific level.
     *
     * @param building    the building
     * @param targetLevel the target upgrade level (1-3)
     * @return the credit cost, or 0 if not available
     */
    public int getUpgradeCost(Building building, int targetLevel) {
        if (targetLevel < 1 || targetLevel > MAX_UPGRADE_LEVEL) {
            return 0;
        }

        var stats = building.getStats();
        var costs = stats.upgradeCosts();
        if (costs == null || costs.isEmpty() || targetLevel > costs.size()) {
            return 0;
        }

        return costs.get(targetLevel - 1);
    }

    /**
     * Get the production speed modifier for a building at its current upgrade level.
     * REF: building_stats.md — production time formula:
     * effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)
     *
     * @param building the building
     * @return the speed modifier (higher = faster production)
     */
    public double getProductionSpeedModifier(Building building) {
        int level = building.getUpgradeLevel();
        // REF: building_stats.md — speed modifier = 300 / ((Y[player].upgrade_bonus + 20))
        // Each upgrade level adds 5 to the upgrade bonus
        int upgradeBonus = level * 5;
        return 300.0 / (upgradeBonus + 20);
    }

    /**
     * Check if a building can be upgraded further.
     *
     * @param building the building to check
     * @return true if the building is not at max upgrade level
     */
    public boolean canUpgrade(Building building) {
        return building.isAlive() && !building.isUnderConstruction()
            && building.getUpgradeLevel() < MAX_UPGRADE_LEVEL;
    }
}
