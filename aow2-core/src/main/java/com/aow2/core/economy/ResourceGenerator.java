package com.aow2.core.economy;

import com.aow2.common.config.GameConstants;
import com.aow2.common.config.StatsRegistry;
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
 * Credit generation follows the RE formula:
 * <pre>
 *   incomePerCycle = (baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20)
 * </pre>
 * Where:
 * <ul>
 *   <li>{@code baseIncome} — sum of per-CC income with diminishing returns (30% less per additional CC)</li>
 *   <li>{@code playerModifier} — difficulty-based income scaling (0.7 easy, 1.0 normal, 1.3 hard)</li>
 *   <li>{@code upgradeBonus} — sum of CC upgrade levels × {@link GameConstants#CC_UPGRADE_INCOME_BONUS_PER_LEVEL}</li>
 * </ul>
 * Additionally, the Resistance faction receives a 15% income bonus per
 * {@link GameConstants#RESISTANCE_INCOME_MULTIPLIER}.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 4.4 — "Credit Generation Formula"
 * REF: MASTER_DOCUMENTATION.md Section 4.4 — "Resistance collects resources faster than Confederation"
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
     * Calculate total income per cycle for a player using the full RE formula.
     * <p>
     * Steps:
     * <ol>
     *   <li>Compute base income from all CCs with diminishing returns (30% per additional CC)</li>
     *   <li>Compute upgradeBonus from the sum of all CC upgrade levels</li>
     *   <li>Apply RE formula: {@code (baseIncome * playerModifier) * 20 / (upgradeBonus + 20)}</li>
     *   <li>Apply faction multiplier for Resistance</li>
     * </ol>
     * <p>
     * REF: MASTER_DOCUMENTATION.md Section 4.4:
     *   "incomePerCycle = (baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20)"
     *
     * @param playerId      the player ID (0 or 1)
     * @param entities      the entity manager
     * @param playerModifier difficulty-based income modifier (e.g. 1.0 for normal)
     * @return the total income per cycle
     */
    public int calculateCycleIncome(int playerId, EntityManager entities, double playerModifier) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);

        // Step 1: Compute base income with diminishing returns, and sum upgrade levels
        int ccIndex = 0;
        int totalBaseIncome = 0;
        int totalUpgradeBonus = 0;
        double diminishingFactor = 1.0 - GameConstants.CC_DIMINISHING_RETURNS;
        double currentIncome = BASE_CC_INCOME;

        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction()
                    && (building.getBuildingType().isHQ() || isIncomeBuilding(building.getBuildingType()))) {
                // Diminishing returns: each additional CC gives 30% less
                if (ccIndex > 0) {
                    currentIncome *= diminishingFactor;
                }
                totalBaseIncome += (int) currentIncome;
                totalUpgradeBonus += building.getUpgradeLevel() * GameConstants.CC_UPGRADE_INCOME_BONUS_PER_LEVEL;
                ccIndex++;
            }
        }

        if (ccIndex == 0) {
            return 0;
        }

        // Step 2: Apply RE formula
        // REF: MASTER_DOCUMENTATION.md Section 4.4
        // incomePerCycle = (baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20)
        // The "* 100 / 100" is an integer math artifact from the RE; simplified here.
        int income = (int) ((totalBaseIncome * playerModifier) * 20.0 / (totalUpgradeBonus + 20));

        // Step 3 (H-6): Apply faction income differential
        // REF: MASTER_DOCUMENTATION.md — "Resistance collects resources faster than Confederation (confirmed by Gear Games)"
        // ASSUMPTION: Exact multiplier is not documented; 15% assumed.
        if (faction == Faction.RESISTANCE) {
            // FIX (CI verification): Use Math.round instead of (int) cast to avoid
            // floating-point truncation. 1.15 in double is 1.149999... so 100*1.15
            // = 114.999... which (int) truncates to 114 instead of 115.
            income = (int) Math.round(income * GameConstants.RESISTANCE_INCOME_MULTIPLIER);
        }

        return Math.max(income, 0);
    }

    /**
     * Calculate total income per cycle for a player with default playerModifier (1.0).
     * Convenience overload for callers that don't use difficulty-based modifiers.
     *
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @return the total income per cycle
     */
    public int calculateCycleIncome(int playerId, EntityManager entities) {
        return calculateCycleIncome(playerId, entities, GameConstants.NORMAL_INCOME_MODIFIER);
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
     * REF: StatsRegistry
     *
     * @param unitType the unit type
     * @return the credit cost of the unit
     */
    private int getUnitCost(UnitType unitType) {
        return StatsRegistry.getInstance().getUnitCost(unitType); // REF: StatsRegistry
    }
}