package com.aow2.common.model;

/**
 * Categories for research/technology tree nodes.
 * Each category corresponds to a branch of the tech tree.
 * REF: combat_formulas.md section "Research/Upgrade Effects" - 48 research effects
 * REF: tech_tree.json — all categories used in the research effects
 *
 * Note: The original 8-category model (INFANTRY_ARMOR through SPECIAL) has been
 * expanded to include all categories actually present in tech_tree.json.
 */
public enum ResearchCategory {
    /** Improves infantry armor rating. */
    INFANTRY_ARMOR,
    /** Improves infantry movement speed. */
    INFANTRY_SPEED,
    /** Improves infantry weapon damage. */
    INFANTRY_WEAPONRY,
    /** Improves vehicle weapon damage. */
    VEHICLE_WEAPONRY,
    /** Improves vehicle movement speed. */
    VEHICLE_SPEED,
    /** Improves building armor or unlocks building abilities. */
    BUILDING,
    /** Economic bonuses: credit generation, cost reduction, supply cap, credit limit. */
    ECONOMY,
    /** Faction-specific special research with unique effects. */
    SPECIAL,
    /** Reduces enemy player's attack range (Signal Jamming effects). */
    ENEMY_RANGE_REDUCTION,
    /** Improves unit attack speed (fire rate). */
    ATTACK_SPEED,
    /** Improves unit attack damage. */
    ATTACK_DAMAGE,
    /** Improves building armor (additive bonus). */
    BUILDING_ARMOR,
    /** Increases building power/radius. */
    BUILDING_RADIUS,
    /** Overrides building armor to a fixed value. */
    BUILDING_ARMOR_OVERRIDE,
    /** Upgrades a unit type to a different type. */
    UNIT_UPGRADE,
    /** Improves unit attack range. */
    ATTACK_RANGE,
    /** Improves production speed. */
    PRODUCTION_SPEED,
    /** Affects scoring bonuses. */
    SCORING;

    /**
     * Parse a category from a string, returning the matching enum value.
     * Case-insensitive. Returns null if no match is found.
     *
     * @param name the category string from tech_tree.json
     * @return the matching ResearchCategory, or null if not found
     */
    public static ResearchCategory fromString(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
