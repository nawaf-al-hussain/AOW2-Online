package com.aow2.common.model;

/**
 * Immutable stats for a unit type.
 * All values from complete_unit_stats.json.
 * REF: MASTER_DOCUMENTATION.md Section 5 - Unit Encyclopedia
 *
 * FIX LOG:
 * - Removed redundant baseCost field (costCredits is the single source of truth for unit cost)
 * - Added weaponType field for combat system projectile selection
 * - Added attackSpeed field (ticks between attacks, also known as rate of fire)
 */
public record UnitStats(
    UnitType unitType,
    String description,
    int hp,
    int damage,
    /** Movement speed — how fast the unit traverses the map (tiles per tick). */
    int speed,
    int armor,
    int attackBonus,
    int sightRange,
    int attackRange,
    WeaponType weaponType,
    /** Weapon cooldown / attack speed — ticks between successive attacks (rate of fire). */
    int attackSpeed,
    int buildTime,
    int costCredits,
    int rewardCredits,
    int extendedArmor,
    int siegeTargets,
    int upgradeLevel,
    int availabilityFlag
) {}
