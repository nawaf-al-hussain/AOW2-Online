package com.aow2.common.model;

/**
 * Immutable grid position on the game map.
 * Valid coordinate range: 0-127 (128×128 grid).
 * REF: map_system.md Section 1.1 — "byte[][] O = Array.newInstance(Byte.TYPE, 128, 128)"
 * FIX: Max coordinate is 127 (= 128 cells, indices 0-127).
 */
public record GridPosition(int x, int y) {
    public GridPosition {
        if (x < 0 || x > 127) {
            throw new IllegalArgumentException("X must be 0-127, got: " + x);
        }
        if (y < 0 || y > 127) {
            throw new IllegalArgumentException("Y must be 0-127, got: " + y);
        }
    }

    public double distanceTo(GridPosition other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public GridPosition offset(int dx, int dy) {
        return new GridPosition(
            Math.clamp(this.x + dx, 0, 127),
            Math.clamp(this.y + dy, 0, 127)
        );
    }
}
