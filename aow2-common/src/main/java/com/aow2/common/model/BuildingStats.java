package com.aow2.common.model;

import java.util.List;

/**
 * Immutable stats for a building type.
 * All values from complete_building_stats.json.
 * REF: MASTER_DOCUMENTATION.md Section 6 - Building Encyclopedia
 *
 * FIX LOG:
 * - Renamed `speed` to `attackSpeed` (attack cooldown for defensive buildings, in ticks)
 * - Added `garrisonCapacity` field (0 for non-garrisonable buildings)
 * - Added `weaponType` field (NONE for non-attacking buildings)
 */
public record BuildingStats(
    BuildingType buildingType,
    int hp,
    int baseCost,
    /** Attack cooldown in ticks for defensive buildings (towers, bunkers). 0 for non-attacking buildings. */
    int attackSpeed,
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
    /** Maximum units that can be garrisoned inside this building. 0 for non-garrisonable buildings. */
    int garrisonCapacity,
    /** Weapon type used by this building for attacks. NONE for non-attacking buildings. */
    WeaponType weaponType,
    List<Integer> upgradeCosts
) {}
