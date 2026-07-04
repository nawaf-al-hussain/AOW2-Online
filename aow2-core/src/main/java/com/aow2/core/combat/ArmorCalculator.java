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
     * - ID 0: Confederation Energy Suit, +2 infantry armour (all infantry types)
     * - ID 9: Confederation Composite Armour, +2 infantry armour (heavy types 7,18,9,11,17,13,16)
     * - ID 24: Rebel Titanium Jacket, +1 infantry armour (types 0,2,4,14)
     * - ID 33: Rebel additional infantry armour, +1 (types 6,8,10,15,12)
     * <p>
     * VERIFIED (M4 from CRITICAL_ANALYSIS_REPORT.md): The RE spec for research IDs 9 and 33 lists
     * unit type IDs that include BOTH infantry types (7, 8) AND machinery types (16, 17, 18, 15, etc.).
     * The original game applies these bonuses via the Z[player][4] infantry-armor slot ONLY when
     * the target unit's bitmask matches the infantry bitmask (16447). For machinery units in the
     * affected-type list (e.g., type 16 Zeus, type 18 Rhino), the bonus is silently ignored because
     * the armor lookup method l(int i) checks isInfantry before applying Z[player][4].
     * <p>
     * REF: s1/y.java line 1673 — byte b = this.cf[2][this.ca[i + 2323]]; (base armor)
     * REF: combat_formulas.md "Armour Calculation" — baseArmour += Z[player][((isInfantry ? 0 : 1) + 4)]
     * <p>
     * Conclusion: the VEHICLE_ARMOR_RESEARCH map below is empty because NO research IDs add
     * vehicle armor through the Z[] array in the original game. Vehicle armor upgrades come
     * exclusively from per-unit upgrade levels (Building.upgradeLevel), which is a separate
     * mechanism not yet implemented (see ProjectProgress.md Phase 13).
     */
    private static final Map<Integer, Integer> INFANTRY_ARMOR_RESEARCH = Map.of(
        0, 2,
        9, 2,
        24, 1,
        33, 1
    );

    /**
     * Research IDs that add vehicle armor bonuses.
     * <p>
     * VERIFIED (M4 from CRITICAL_ANALYSIS_REPORT.md): Confirmed EMPTY — no research IDs in the RE
     * spec add vehicle armor through the Z[player][5] vehicle-armor slot. The original game's
     * research IDs 9 and 33 affect unit type lists that include both infantry and machinery types,
     * but the armor-lookup code only applies Z[player][4] (infantry slot) when isInfantry is true,
     * and Z[player][5] (vehicle slot) is never written by any research effect.
     * <p>
     * Vehicle armor upgrades in the original game come from per-unit upgrade levels
     * (cf[2][unitType] is overridden by cg[2][...] when an upgrade is active), which is a
     * separate mechanism tracked in Building.upgradeLevel — not yet wired in this project.
     */
    private static final Map<Integer, Integer> VEHICLE_ARMOR_RESEARCH = Map.of();

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

        // Step 1: Apply additive bonuses first (e.g., ID 4: +4 building armor)
        for (Map.Entry<Integer, Integer> entry : BUILDING_ARMOR_RESEARCH_ADD.entrySet()) {
            if (completedResearch.contains(entry.getKey())) {
                armor += entry.getValue();
            }
        }

        // Step 2: Apply override bonuses (e.g., IDs 16, 40 SET armor to 9).
        // REF: combat_formulas.md — "Player 0 building armour = 9" means SET, not ADD.
        // The override replaces the final calculated value entirely.
        for (Map.Entry<Integer, Integer> entry : BUILDING_ARMOR_RESEARCH_OVERRIDE.entrySet()) {
            if (completedResearch.contains(entry.getKey())) {
                armor = entry.getValue(); // SET, not add or max
                break; // only one override can apply
            }
        }

        return armor;
    }

    /**
     * Calculate armor bonus from research for a unit.
     * <p>
     * REF: combat_formulas.md research effects table:
     * - ID 0: Confederation Energy Suit, +2 infantry armor (all infantry)
     * - ID 9: Confederation Composite Armour, +2 infantry armor (heavy types)
     * - ID 24: Rebel Titanium Jacket, +1 infantry armor
     * - ID 33: Rebel additional infantry armor, +1
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

        // FIX (ANALYSIS_V2 P5): Use hardcoded maps as a fallback. The primary path
        // should be through ResearchBonusTracker which accumulates ALL research effects
        // from tech_tree.json (including ones not in these hardcoded maps). Callers
        // that have a ResearchBonusTracker should use getResearchArmorBonus(Unit, ResearchBonusTracker)
        // instead. This method is kept for backward compatibility and tests.
        if (unit.isInfantry()) {
            for (Map.Entry<Integer, Integer> entry : INFANTRY_ARMOR_RESEARCH.entrySet()) {
                if (completedResearch.contains(entry.getKey())) {
                    bonus += entry.getValue();
                }
            }
        } else if (unit.isMachinery()) {
            for (Map.Entry<Integer, Integer> entry : VEHICLE_ARMOR_RESEARCH.entrySet()) {
                if (completedResearch.contains(entry.getKey())) {
                    bonus += entry.getValue();
                }
            }
        }

        return bonus;
    }

    /**
     * FIX (ANALYSIS_V2 P5): Data-driven armor bonus using ResearchBonusTracker.
     * <p>
     * This method uses the accumulated bonus values from the ResearchBonusTracker,
     * which reflects ALL research effects from tech_tree.json — not just the hardcoded
     * IDs in the INFANTRY_ARMOR_RESEARCH / VEHICLE_ARMOR_RESEARCH maps. This ensures
     * that modders who add new armor research effects see them actually applied.
     *
     * @param unit    the unit to check
     * @param tracker the player's ResearchBonusTracker (from ResearchSystem.getBonusTracker)
     * @return total armor bonus from research
     */
    public int getResearchArmorBonus(Unit unit,
                                      com.aow2.core.research.ResearchSystem.ResearchBonusTracker tracker) {
        if (tracker == null) {
            return 0;
        }

        if (unit.isInfantry()) {
            return tracker.getInfantryArmorBonus();
        } else if (unit.isMachinery()) {
            return tracker.getVehicleArmorBonus();
        }

        return 0;  // Mines do not receive armor bonuses
    }

    /**
     * FIX (ANALYSIS_V2 P5): Data-driven effective armor calculation using ResearchBonusTracker.
     *
     * @param unit    the unit to calculate armor for
     * @param tracker the player's ResearchBonusTracker
     * @return effective armor value (base + research bonus)
     */
    public int calculateEffectiveArmor(Unit unit,
                                        com.aow2.core.research.ResearchSystem.ResearchBonusTracker tracker) {
        int baseArmor = unit.getStats().armor();
        int bonus = getResearchArmorBonus(unit, tracker);
        return baseArmor + bonus;
    }

    /**
     * Get building armor bonus for a faction (without requiring a building instance).
     * Used by CombatSystem to calculate building armor during attacks.
     * <p>
     * REF: combat_formulas.md lines 64-68 - building armor from N[] per-player array
     * <p>
     * Building armor comes from the N[] per-player array and research overrides.
     * Research IDs 4 (+4 building armour), 16 (player 0 override = 9), and 40 (player 1 override = 9)
     * affect building armor and are handled by {@link #calculateEffectiveBuildingArmor(Building, Set)}.
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
