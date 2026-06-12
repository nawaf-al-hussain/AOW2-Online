package com.aow2.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridPositionTest {

    @Test
    @DisplayName("Should create valid grid position")
    void shouldCreateValidPosition() {
        var pos = new GridPosition(10, 20);
        assertEquals(10, pos.x());
        assertEquals(20, pos.y());
    }

    @Test
    @DisplayName("Should reject negative coordinates")
    void shouldRejectNegativeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new GridPosition(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new GridPosition(0, -1));
    }

    @Test
    @DisplayName("Should reject coordinates exceeding max map size (127)")
    void shouldRejectExceedingMaxMapSize() {
        assertThrows(IllegalArgumentException.class, () -> new GridPosition(128, 0));
        assertThrows(IllegalArgumentException.class, () -> new GridPosition(0, 128));
    }

    @Test
    @DisplayName("Should accept boundary coordinates 0 and 127")
    void shouldAcceptBoundaryCoordinates() {
        assertDoesNotThrow(() -> new GridPosition(0, 0));
        assertDoesNotThrow(() -> new GridPosition(127, 127));
    }

    @Test
    @DisplayName("Should calculate distance between two positions")
    void shouldCalculateDistance() {
        var a = new GridPosition(0, 0);
        var b = new GridPosition(3, 4);
        assertEquals(5.0, a.distanceTo(b), 0.001);
    }

    @Test
    @DisplayName("Should offset position within bounds")
    void shouldOffsetWithinBounds() {
        var pos = new GridPosition(10, 20);
        var offset = pos.offset(5, -5);
        assertEquals(15, offset.x());
        assertEquals(15, offset.y());
    }

    @Test
    @DisplayName("Should clamp offset to map bounds")
    void shouldClampOffsetToBounds() {
        var pos = new GridPosition(125, 5);
        var offset = pos.offset(10, -10);
        assertEquals(127, offset.x()); // clamped
        assertEquals(0, offset.y()); // clamped but would be -5, so 0
    }
}
