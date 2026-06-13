package com.aow2.common.model;

/**
 * Categories for research/technology tree nodes.
 * Each category corresponds to a branch of the tech tree.
 * REF: combat_formulas.md section "Research/Upgrade Effects" - 8 research categories per faction
 * REF: complete_unit_stats.json technologies section
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
    /** Economic bonuses: credit generation, cost reduction. */
    ECONOMY,
    /** Faction-specific special research with unique effects. */
    SPECIAL
}
