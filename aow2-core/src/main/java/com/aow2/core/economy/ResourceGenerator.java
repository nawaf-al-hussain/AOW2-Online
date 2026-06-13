package com.aow2.core.economy;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import java.util.List;

/**
 * Generates resources for players based on their buildings.
 * <p>
 * Credit generation follows the diminishing returns formula where each
 * additional Command Centre produces 30% less than the previous one.
 * Kill rewards are calculated based on unit cost and distance to enemy base.
 * <p>
 * REF: combat_formulas.md "Credit Generation Formula"
 * REF: combat_formulas.md "Unit Cost & Reward Calculations"
 */
public final class ResourceGenerator {

    /** Base income per Command Centre per cycle before diminishing returns. */
    public static final int BASE_CC_INCOME = 100;

    /**
     * Count income-generating buildings for a player.
     * REF: map_system.md Section 5.2: "data % 3 == 0" = income-generating buildings
     * This includes Command Centres (HQ) AND Supply Centers.
     *
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @return the number of income-generating buildings owned by the player
     */
    public int countCommandCentres(int playerId, EntityManager entities) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);
        int count = 0;
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction()) {
                // REF: map_system.md Section 5.2 - income buildings: HQ + Supply Centers
                if (building.getBuildingType().isHQ() || isIncomeBuilding(building.getBuildingType())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Check if a building type generates income (Supply Center equivalent).
     * REF: map_system.md Section 5.2: "data % 3 == 0" = income-generating
     * In the original game, both Command Centres and Supply Centers generate income.
     *
     * @param type the building type
     * @return true if this building generates income
     */
    private boolean isIncomeBuilding(BuildingType type) {
        // REF: map_system.md - income buildings include Command Centre and Supply Center
        // Command Centre is already covered by isHQ().
        // Supply Center is a dedicated income building not yet in the BuildingType enum.
        // For now, only HQ buildings generate income until Supply Center is added.
        return false;
    }

    /**
     * Calculate total income per cycle for a player.
     * Uses diminishing returns formula for multiple CCs.
     * <p>
     * First CC produces full base income.
     * Each subsequent CC produces 30% less than the previous one.
     * <p>
     * REF: combat_formulas.md — income formula:
     * "income = (baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20)"
     * Simplified: each CC after the first produces (1 - CC_DIMINISHING_RETURNS) * previous
     *
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @return the total income per cycle
     */
    public int calculateCycleIncome(int playerId, EntityManager entities) {
        int ccCount = countCommandCentres(playerId, entities);
        if (ccCount == 0) {
            return 0;
        }

        int totalIncome = 0;
        double currentIncome = BASE_CC_INCOME;
        // REF: combat_formulas.md — "(baseIncome * 7) / 10" = 70% of base
        // First CC gives full income; each additional CC gives 30% less
        double diminishingFactor = 1.0 - GameConstants.CC_DIMINISHING_RETURNS;

        for (int i = 0; i < ccCount; i++) {
            totalIncome += (int) currentIncome;
            currentIncome *= diminishingFactor;
        }

        return totalIncome;
    }

    /**
     * Get credit reward for killing a unit.
     * <p>
     * REF: combat_formulas.md "Unit Cost & Reward Calculations"
     * Formula: killReward = (unitCost * 3 * distanceToEnemyBase) / (baseDistance * 2)
     *
     * @param killedUnit      the type of unit that was killed
     * @param killerPos       position of the killing unit
     * @param enemyBase       position of the killed unit's base (closest enemy CC)
     * @param baseDistance     the baseline distance between the two starting bases
     * @return the credit reward for the kill
     */
    public int getKillReward(UnitType killedUnit, GridPosition killerPos,
                             GridPosition enemyBase, int baseDistance) {
        if (baseDistance <= 0) {
            // ASSUMPTION: if baseDistance is 0 or negative, return minimum reward
            return 1;
        }

        int unitCost = getUnitCost(killedUnit);
        double distanceToEnemyBase = killerPos.distanceTo(enemyBase);

        // REF: combat_formulas.md — "killReward = (unitCost * 3 * distanceToEnemyBase) / (baseDistance * 2)"
        int killReward = (int) ((unitCost * 3 * distanceToEnemyBase) / (baseDistance * 2.0));

        // Minimum reward is 1
        return Math.max(killReward, 1);
    }

    /**
     * Get the credit cost of a unit type.
     * <p>
     * REF: complete_unit_stats.json — costCredits field per unit type
     * ASSUMPTION: using approximate values from RE data until full stats registry is implemented
     *
     * @param unitType the unit type
     * @return the credit cost of the unit
     */
    private int getUnitCost(UnitType unitType) {
        return switch (unitType) {
            // Confederation infantry
            case CONFED_INFANTRY -> 10;
            case CONFED_GRENADIER -> 15;
            case CONFED_FLAME_ASSAULT -> 25;
            // Confederation vehicles
            case CONFED_FORTRESS -> 50;
            case CONFED_HAMMER -> 30;
            case CONFED_ZEUS -> 30;
            case CONFED_TORRENT -> 50;
            // Confederation mines
            case CONFED_MINE_SCORPIO -> 10;
            case CONFED_MINE_FROG -> 10;
            case CONFED_MINE_LIZARD -> 10;
            // Resistance infantry
            case REBEL_INFANTRY -> 10;
            case REBEL_GRENADIER -> 15;
            case REBEL_SNIPER -> 20;
            // Resistance vehicles
            case REBEL_COYOTE -> 25;
            case REBEL_ARMADILLO -> 40;
            case REBEL_RHINO -> 35;
            case REBEL_PORCUPINE -> 45;
        };
    }
}
