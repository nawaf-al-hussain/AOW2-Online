package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.PowerSystem;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Upgrade commands by validating and upgrading buildings.
 * <p>
 * REF: complete_building_stats.json — upgradeCosts per building type (3 levels)
 * REF: GameConstants.BUILDING_POWER_RADIUS — power radius per Generator upgrade level
 * REF: GameConstants.CC_UPGRADE_INCOME_BONUS_PER_LEVEL — income bonus per CC upgrade level
 * <p>
 * Upgrade effects per level:
 * <ul>
 *   <li>All buildings: +20% max HP per level (upgradeMaxHpBonus)</li>
 *   <li>Generators: power radius increases (10→20→30→40→60→127)</li>
 *   <li>Command Centres: income bonus per cycle (+2 per level)</li>
 * </ul>
 * ASSUMPTION: The +20% HP per level is a reasonable design choice — the RE spec
 * does not document the exact HP bonus per upgrade level. The original game's
 * building upgrade system is described in building_stats.md but the exact
 * numeric effects are not in the RE data.
 */
public final class UpgradeCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UpgradeCommandHandler.class);

    /** HP bonus percentage per upgrade level (20% of base HP per level). */
    private static final double HP_BONUS_PER_LEVEL = 0.20;

    /**
     * Handle an Upgrade command.
     * Validates the building, checks credits, deducts cost, and applies the upgrade.
     *
     * @param cmd       the upgrade command
     * @param entities  the entity manager
     * @param economy   the economy system
     * @param powerSystem the power system (to update power grid after Generator upgrade)
     */
    public void handle(CommandType.Upgrade cmd, EntityManager entities,
                       EconomySystem economy, PowerSystem powerSystem) {
        Building building = entities.getBuilding(cmd.buildingId());
        if (building == null) {
            LOG.debug("Upgrade failed: building {} not found", cmd.buildingId());
            return;
        }
        if (!building.isAlive()) {
            LOG.debug("Upgrade failed: building {} is dead", cmd.buildingId());
            return;
        }
        if (building.isUnderConstruction()) {
            LOG.debug("Upgrade failed: building {} is under construction", cmd.buildingId());
            return;
        }

        // Ownership check: the building must belong to the commanding player
        int ownerId = EconomySystem.playerId(building.getFaction());
        if (ownerId != cmd.playerId()) {
            LOG.warn("Upgrade rejected: player {} does not own building {} (owner: {})",
                cmd.playerId(), cmd.buildingId(), ownerId);
            return;
        }

        // Check if building is already at max upgrade level (3)
        int currentLevel = building.getUpgradeLevel();
        if (currentLevel >= 3) {
            LOG.debug("Upgrade failed: building {} already at max level {}", cmd.buildingId(), currentLevel);
            return;
        }

        // Get the upgrade cost for the next level
        var upgradeCosts = building.getStats().upgradeCosts();
        if (upgradeCosts.isEmpty()) {
            LOG.debug("Upgrade failed: building {} has no upgrade costs defined", cmd.buildingId());
            return;
        }
        int costIndex = Math.min(currentLevel, upgradeCosts.size() - 1);
        int cost = upgradeCosts.get(costIndex);

        // Check if player can afford the upgrade
        if (!economy.canAfford(cmd.playerId(), cost)) {
            LOG.debug("Upgrade failed: player {} cannot afford {} credits for building {} upgrade to level {}",
                cmd.playerId(), cost, cmd.buildingId(), currentLevel + 1);
            return;
        }

        // Deduct credits
        economy.spendCredits(cmd.playerId(), cost);

        // Apply the upgrade
        int newLevel = currentLevel + 1;
        building.setUpgradeLevel(newLevel);

        // Apply HP bonus: +20% of base HP per level
        int hpBonus = (int) (building.getMaxHp() * HP_BONUS_PER_LEVEL);
        building.setUpgradeMaxHpBonus(hpBonus * newLevel);
        building.heal(hpBonus); // Heal the bonus HP immediately

        // If this is a Generator, update the power grid to reflect the new radius
        if (building.getBuildingType().producesPower() && powerSystem != null) {
            powerSystem.updatePowerGrid(entities);
        }

        LOG.info("Player {} upgraded building {} ({}) to level {} for {} credits (HP bonus: +{})",
            cmd.playerId(), cmd.buildingId(), building.getBuildingType().displayName(),
            newLevel, cost, hpBonus);
    }
}
