package com.aow2.common.model;

import java.util.Arrays;

/**
 * Represents the stat modification that a research node applies when completed.
 * Each research effect modifies a specific stat by a given value, affecting
 * a defined set of unit types.
 * REF: combat_formulas.md section "Research/Upgrade Effects"
 * REF: complete_unit_stats.json technologies section - asymmetric per faction
 *
 * @param statName          the stat field name that this research modifies (e.g., "armor", "damage", "speed")
 * @param value             the integer modification value to apply to the stat
 * @param affectedUnitTypes array of UnitType ordinals that are affected by this research
 */
public record ResearchEffect(
    String statName,
    int value,
    int[] affectedUnitTypes
) {
    /**
     * Compact constructor validating research effect fields.
     */
    public ResearchEffect {
        if (statName == null || statName.isBlank()) {
            throw new IllegalArgumentException("statName must not be null or blank");
        }
        if (affectedUnitTypes == null) {
            affectedUnitTypes = new int[0];
        }
    }

    /**
     * Checks whether the given unit type ordinal is affected by this research.
     *
     * @param unitTypeOrdinal the ordinal of the UnitType to check
     * @return true if this research affects the specified unit type
     */
    public boolean affectsUnitType(int unitTypeOrdinal) {
        for (int type : affectedUnitTypes) {
            if (type == unitTypeOrdinal) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResearchEffect that)) return false;
        return value == that.value
            && statName.equals(that.statName)
            && Arrays.equals(affectedUnitTypes, that.affectedUnitTypes);
    }

    @Override
    public int hashCode() {
        int result = statName.hashCode();
        result = 31 * result + value;
        result = 31 * result + Arrays.hashCode(affectedUnitTypes);
        return result;
    }

    @Override
    public String toString() {
        return "ResearchEffect{statName='" + statName + "', value=" + value +
               ", affectedUnitTypes=" + Arrays.toString(affectedUnitTypes) + "}";
    }
}
