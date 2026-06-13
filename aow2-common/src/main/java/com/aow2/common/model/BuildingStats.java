package com.aow2.common.model;

import java.util.List;

/**
 * Immutable stats for a building type.
 * All values from complete_building_stats.json.
 * REF: MASTER_DOCUMENTATION.md Section 6 - Building Encyclopedia
 */
public record BuildingStats(
    BuildingType buildingType,
    int hp,
    int baseCost,
    int speed,
    int armor,
    int attackBonus,
    int sightRange,
    int buildTime,
    int attackRange,
    int extendedArmor,
    int powerConsume,
    int powerProduce,
    int queueSlots,
    int techRequirement,
    int costCredits,
    int rewardCredits,
    List<Integer> upgradeCosts
) {}
