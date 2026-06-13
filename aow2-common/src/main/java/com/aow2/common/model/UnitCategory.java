package com.aow2.common.model;

/**
 * Unit classification categories based on the original game's bitmask system.
 * REF: unit_stats.md - bitmask 16447 for infantry, 16256 for machinery
 * Infantry bitmask: 0x403F = bits 0,1,2,3,5,14
 * Machinery bitmask: 0x3F80 = bits 7-13
 */
public enum UnitCategory {
    INFANTRY,
    VEHICLE,
    MINE
}
