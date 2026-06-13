package com.aow2.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameConstantsTest {

    @Test
    @DisplayName("Tick rate should be 60 TPS")
    void shouldHaveCorrectTickRate() {
        assertEquals(60, GameConstants.TICK_RATE);
    }

    @Test
    @DisplayName("Map size should be 0-127")
    void shouldHaveCorrectMapSize() {
        assertEquals(0, GameConstants.MIN_MAP_SIZE);
        assertEquals(127, GameConstants.MAX_MAP_SIZE);
    }

    @Test
    @DisplayName("Armor divisor should be 10 matching combat formula")
    void shouldHaveCorrectArmorDivisor() {
        // REF: combat_formulas.md - damage = weaponDamage * (10 - targetArmour) / 10
        assertEquals(10, GameConstants.ARMOR_DIVISOR);
    }

    @Test
    @DisplayName("Max units per player should be 50")
    void shouldHaveCorrectMaxUnits() {
        assertEquals(50, GameConstants.MAX_UNITS_PER_PLAYER);
    }

    @Test
    @DisplayName("Death animation arrays should have 5 elements each")
    void shouldHaveCorrectDeathAnimArrays() {
        assertEquals(5, GameConstants.DEATH_ANIM_BASE.length);
        assertEquals(5, GameConstants.DEATH_ANIM_RANGE.length);
    }
}
