package com.aow2.common.model;

/**
 * Immutable stats for a unit type.
 * All values from complete_unit_stats.json.
 * REF: MASTER_DOCUMENTATION.md Section 5 - Unit Encyclopedia
 */
public record UnitStats(
    UnitType unitType,
    String description,
    int hp,
    int damage,
    int baseCost,
    int speed,
    int armor,
    int attackBonus,
    int sightRange,
    int attackRange,
    int buildTime,
    int costCredits,
    int rewardCredits,
    int extendedArmor,
    int siegeTargets,
    int upgradeLevel,
    int availabilityFlag
) {}
