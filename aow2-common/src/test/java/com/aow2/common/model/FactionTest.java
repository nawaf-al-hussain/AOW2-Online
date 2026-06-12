package com.aow2.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FactionTest {

    @Test
    @DisplayName("Should have exactly 3 faction values")
    void shouldHaveThreeFactions() {
        assertEquals(3, Faction.values().length);
    }

    @Test
    @DisplayName("Should contain CONFEDERATION, RESISTANCE, and NEUTRAL")
    void shouldContainAllFactions() {
        assertNotNull(Faction.CONFEDERATION);
        assertNotNull(Faction.RESISTANCE);
        assertNotNull(Faction.NEUTRAL);
    }
}
