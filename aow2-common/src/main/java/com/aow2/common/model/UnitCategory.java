package com.aow2.common.model;

/**
 * Unit classification categories based on the original game's bitmask system.
 * REF: unit_stats.md - bitmask 16447 for infantry, 16256 for machinery
 * Infantry bitmask: 0x403F = bits 0,1,2,3,5,14
 * Machinery bitmask: 0x3F80 = bits 7-13
 *
 * SPECIAL_MACHINERY: Units like Flame Assault that have infantry-scale stats
 * but NO infantry bit set in the bitmask. The engine treats them as machinery
 * for combat calculations (death animation, damage modifiers).
 * REF: unit_stats.md - Type 8 (Flame Assault) has no infantry bit set
 */
public enum UnitCategory {
    INFANTRY,
    VEHICLE,
    MINE,
    SPECIAL_MACHINERY
}
