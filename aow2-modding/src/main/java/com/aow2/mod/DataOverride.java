package com.aow2.mod;

/**
 * Represents a data override from a mod.
 * Can override unit stats, building stats, tech tree, costs, etc.
 * <p>
 * REF: project_structure.md - mod system architecture
 * REF: phases.md Phase 10 - data override system
 *
 * @param targetType the type of game data being overridden ("unit", "building", "tech", "weapon")
 * @param targetId   the specific entity type name (e.g., "CONFED_INFANTRY")
 * @param field      the stat field name to override (e.g., "hp", "damage", "speed")
 * @param value      the new value to apply
 */
public record DataOverride(
    String targetType,
    String targetId,
    String field,
    Object value
) {
    /**
     * Compact constructor with validation.
     */
    public DataOverride {
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("targetType must not be null or blank");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be null or blank");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be null or blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    /**
     * Returns the value as an integer, throwing if conversion fails.
     *
     * @return integer value
     * @throws IllegalStateException if value cannot be converted to int
     */
    public int intValue() {
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Cannot convert override value to int: " + value);
        }
    }

    /**
     * Returns the value as a double, throwing if conversion fails.
     *
     * @return double value
     * @throws IllegalStateException if value cannot be converted to double
     */
    public double doubleValue() {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Cannot convert override value to double: " + value);
        }
    }

    /**
     * Returns the value as a string.
     *
     * @return string value
     */
    public String stringValue() {
        return value.toString();
    }
}
