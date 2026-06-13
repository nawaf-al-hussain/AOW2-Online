package com.aow2.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectionTest {

    @Test
    @DisplayName("Should have 8 directions")
    void shouldHaveEightDirections() {
        assertEquals(8, Direction.values().length);
    }

    @Test
    @DisplayName("Should convert from code to direction")
    void shouldConvertFromCode() {
        assertEquals(Direction.NORTH, Direction.fromCode(0));
        assertEquals(Direction.EAST, Direction.fromCode(2));
        assertEquals(Direction.SOUTH, Direction.fromCode(4));
        assertEquals(Direction.WEST, Direction.fromCode(6));
    }

    @Test
    @DisplayName("Should reject invalid direction codes")
    void shouldRejectInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> Direction.fromCode(8));
        assertThrows(IllegalArgumentException.class, () -> Direction.fromCode(-1));
    }
}
