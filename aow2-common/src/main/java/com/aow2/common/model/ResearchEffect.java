package com.aow2.common.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the stat modification that a research node applies when completed.
 * Each research effect modifies a specific stat by a given value, affecting
 * a defined set of unit types.
 * REF: combat_formulas.md section "Research/Upgrade Effects"
 * REF: complete_unit_stats.json technologies section - asymmetric per faction
 *
 * FIX LOG:
 * - Changed affectedUnitTypes from int[] to Set<UnitType> for type safety
 * - Changed affectsUnitType from int ordinal to UnitType parameter
 * - Changed statName from String to StatType enum for compile-time safety
 *
 * @param statType          the stat that this research modifies
 * @param value             the integer modification value to apply to the stat
 * @param affectedUnitTypes set of UnitType values that are affected by this research
 */
public record ResearchEffect(
    StatType statType,
    int value,
    Set<UnitType> affectedUnitTypes
) {
    /**
     * Types of stats that research can modify.
     */
    public enum StatType {
        ARMOR,
        DAMAGE,
        SPEED,
        ATTACK_RANGE,
        SIGHT_RANGE,
        HP,
        COST
    }

    /**
     * Compact constructor validating research effect fields.
     */
    public ResearchEffect {
        if (statType == null) {
            throw new IllegalArgumentException("statType must not be null");
        }
        if (affectedUnitTypes == null) {
            affectedUnitTypes = Collections.emptySet();
        } else {
            // Defensive copy using unmodifiable set
            affectedUnitTypes = Collections.unmodifiableSet(EnumSet.copyOf(affectedUnitTypes));
        }
    }

    /**
     * Checks whether the given unit type is affected by this research.
     *
     * @param type the UnitType to check
     * @return true if this research affects the specified unit type
     */
    public boolean affectsUnitType(UnitType type) {
        return affectedUnitTypes.contains(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResearchEffect that)) return false;
        return value == that.value
            && statType == that.statType
            && affectedUnitTypes.equals(that.affectedUnitTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statType, value, affectedUnitTypes);
    }

    @Override
    public String toString() {
        return "ResearchEffect{statType=" + statType + ", value=" + value +
               ", affectedUnitTypes=" + affectedUnitTypes + "}";
    }
}
