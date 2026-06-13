package com.aow2.core.combat;

import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;

import java.util.Map;
import java.util.Set;

/**
 * Calculates effective armor values with research bonuses.
 * <p>
 * The original game stores per-player armor bonuses in the Z[player][slot] array:
 * Z[player][4] = infantry armor bonus, Z[player][5] = vehicle armor bonus.
 * Multiple researches that affect the same category stack additively.
 * <p>
 * REF: combat_formulas.md "Armour Calculation" - l(int i) method
 * REF: combat_formulas.md "Research/Upgrade Effects" - research IDs 0-47
 */
public final class ArmorCalculator {

    ArmorCalculator() {}

    /**
     * Research IDs that add infantry armor bonuses.
     * REF: combat_formulas.md - Z[player][4] infantry armor slot
     * - ID 0: Confederation Energy Suit, +2 infantry armor
     * - ID 8: Resistance Titanium Jacket, +1 infantry armor
     * - ID 24: Additional infantry armor, +1 for infantry types
     */
    private static final Map<Integer, Integer> INFANTRY_ARMOR_RESEARCH = Map.of(
        0, 2,
        8, 1,
        24, 1
    );

    /**
     * Research IDs that add vehicle armor bonuses.
     * REF: combat_formulas.md - Z[player][5] vehicle armor slot
     * REF: combat_formulas.md line 303 - ID 0 also adds "Light armour +2" (vehicle)
     * - ID 0: Also adds +2 vehicle (Sniper/Light Armour) armor
     * - ID 9: Resistance Composite Armour, +2 vehicle armor
     * - ID 33: Additional vehicle armor, +1 for vehicle types
     */
    private static final Map<Integer, Integer> VEHICLE_ARMOR_RESEARCH = Map.of(
        0, 2,
        9, 2,
        33, 1
    );

    /**
     * Research IDs that add building armor bonuses.
     * REF: combat_formulas.md - N[] array per-player building armor
     * - ID 4: Fortified Structures, +4 building armor
     * - ID 16: Player 0 building armour = 9 (overrides, not additive)
     * - ID 40: Player 1 building armour = 9 (overrides, not additive)
     */
    private static final Map<Integer, Integer> BUILDING_ARMOR_RESEARCH_ADD = Map.of(
        4, 4
    );

    /** Building armor override research IDs. REF: combat_formulas.md - IDs 16 and 40 */
    private static final Map<Integer, Integer> BUILDING_ARMOR_RESEARCH_OVERRIDE = Map.of(
        16, 9,
        40, 9
    );

    /**
     * Get effective armor for a unit, including research bonuses.
     * <p>
     * REF: combat_formulas.md - getArmour method
     * {@code baseArmour += Z[player][((isInfantry ? 0 : 1) + 4)]}
     *
     * @param unit               the unit to calculate armor for
     * @param completedResearch  set of completed research IDs for the unit's owner
     * @return effective armor value (base + research bonus)
     */
    public int calculateEffectiveArmor(Unit unit, Set<Integer> completedResearch) {
        int baseArmor = unit.getStats().armor();
        int bonus = getResearchArmorBonus(unit, completedResearch);
        return baseArmor + bonus;
    }

    /**
     * Get effective armor for a building.
     * <p>
     * Buildings have 0 base armor in the original game. Building armor from research
     * is handled separately through the N[] per-player array and is not applied here.
     * <p>
     * REF: combat_formulas.md - "Buildings have 0 base armour (use construction HP)"
     * REF: combat_formulas.md - {@code if (unitRef > 100) return 0;}
     *
     * @param building the building to calculate armor for
     * @return 0 (buildings have no base armor)
     */
    public int calculateEffectiveArmor(Building building) {
        return 0;
    }

    /**
     * Calculate building armor with research bonuses.
     * <p>
     * Unlike unit armor, building armor can be overridden (not just added to).
     * Research IDs 16 and 40 set building armor to 9 for each player respectively.
     * ID 4 adds +4 building armor additively.
     * <p>
     * REF: combat_formulas.md - N[(buildingRef - 1) / 50] per-player building armor
     *
     * @param building           the building
     * @param completedResearch  set of completed research IDs for the building's owner
     * @return effective building armor
     */
    public int calculateEffectiveBuildingArmor(Building building, Set<Integer> completedResearch) {
        int armor = 0;

        // Check for override research first (IDs 16, 40 set armor to 9)
        for (Map.Entry<Integer, Integer> entry : BUILDING_ARMOR_RESEARCH_OVERRIDE.entrySet()) {
            if (completedResearch.contains(entry.getKey())) {
                armor = Math.max(armor, entry.getValue());
            }
        }

        // Additive bonuses (ID 4: +4)
        for (Map.Entry<Integer, Integer> entry : BUILDING_ARMOR_RESEARCH_ADD.entrySet()) {
            if (completedResearch.contains(entry.getKey())) {
                armor += entry.getValue();
            }
        }

        return armor;
    }

    /**
     * Calculate armor bonus from research for a unit.
     * <p>
     * Confederation: Energy Suit research (ID 0) adds +2 infantry armor.
     * Resistance: Titanium Jacket research (ID 8) adds +1 infantry armor.
     * Resistance: Composite Armour research (ID 9) adds +2 vehicle armor.
     * Additional researches (IDs 24, 33) add further bonuses.
     * Bonuses stack additively within each category.
     * <p>
     * REF: combat_formulas.md - {@code baseArmour += Z[player][((isInfantry ? 0 : 1) + 4)]}
     *
     * @param unit               the unit to check
     * @param completedResearch  set of completed research IDs
     * @return total armor bonus from research
     */
    public int getResearchArmorBonus(Unit unit, Set<Integer> completedResearch) {
        if (completedResearch == null || completedResearch.isEmpty()) {
            return 0;
        }

        int bonus = 0;

        if (unit.isInfantry()) {
            for (Map.Entry<Integer, Integer> entry : INFANTRY_ARMOR_RESEARCH.entrySet()) {
                if (completedResearch.contains(entry.getKey())) {
                    bonus += entry.getValue();
                }
            }
        } else if (unit.isVehicle()) {
            for (Map.Entry<Integer, Integer> entry : VEHICLE_ARMOR_RESEARCH.entrySet()) {
                if (completedResearch.contains(entry.getKey())) {
                    bonus += entry.getValue();
                }
            }
        }

        // ASSUMPTION: Mines do not receive armor bonuses from research
        return bonus;
    }

    /**
     * Get building armor bonus for a faction (without requiring a building instance).
     * Used by CombatSystem to calculate building armor during attacks.
     * <p>
     * REF: combat_formulas.md lines 64-68 - building armor from N[] per-player array
     * <p>
     * Building armor comes from upgrade levels, not research.
     * Confederation research ID 0 (Energy suit) gives +2 infantry armor but does NOT affect buildings.
     * The full building armor calculation (with research overrides) is in
     * {@link #calculateEffectiveBuildingArmor(Building, Set)}.
     *
     * @param faction the faction to get building armor for
     * @return building armor bonus
     */
    public int getBuildingArmorBonus(com.aow2.common.model.Faction faction) {
        // ASSUMPTION: Building armor bonus from upgrades not yet implemented, returns 0 until upgrade system is in place
        // Building armor comes from the building's own upgrade levels (StatsRegistry stores base stats).
        // Research IDs 4, 16, 40 affect building armor but are handled by calculateEffectiveBuildingArmor().
        return 0;
    }
}
