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

    /**
     * Euclidean distance between two grid positions.
     * NOTE: For non-gameplay use only (e.g., UI display).
     * For gameplay distance calculations (combat range, pathfinding, etc.),
     * use {@link #distanceClass(int, int)} instead.
     */
    public double distanceTo(GridPosition other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Returns the distance class for a given dx/dy offset using a 31×31 lookup table.
     * REF: combat_formulas.md — (lookupTable[dy + 15][dx + 15] & 255) >> 3
     *
     * <p>The original game uses a 31×31 lookup table where each cell encodes
     * distance class in the upper 5 bits (value >> 3). This approximates
     * Chebyshev distance (max of |dx|, |dy|) which is the distance class
     * used for combat range checks, fog of war, etc.</p>
     *
     * @param dx x-axis offset (range -15 to 15)
     * @param dy y-axis offset (range -15 to 15)
     * @return distance class (0-15)
     */
    public static int distanceClass(int dx, int dy) {
        // Clamp to valid lookup range [-15, 15]
        int cx = Math.max(-15, Math.min(15, dx));
        int cy = Math.max(-15, Math.min(15, dy));
        // REF: combat_formulas.md — (lookupTable[dy + 15][dx + 15] & 255) >> 3
        // The upper 5 bits of the lookup table encode Chebyshev-like distance class.
        // Approximated as: Math.max(Math.abs(dx), Math.abs(dy))
        return DISTANCE_TABLE[cy + 15][cx + 15];
    }

    /**
     * 31×31 distance class lookup table.
     * REF: combat_formulas.md — original game's lookupTable, upper 5 bits >> 3.
     * Values encode Chebyshev distance: max(|dx|, |dy|) for |dx|,|dy| <= 15.
     * Index: table[dy + 15][dx + 15].
     */
    private static final int[][] DISTANCE_TABLE = {
        {15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15},
        {15,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,15},
        {15,14,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,14,15},
        {15,14,13,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,13,14,15},
        {15,14,13,12,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,12,13,14,15},
        {15,14,13,12,11,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,6,6,6,6,6,6,6,6,6,6,6,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,5,5,5,5,5,5,5,5,5,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,4,4,4,4,4,4,4,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,3,3,3,3,3,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,2,2,2,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,1,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,1,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,2,2,2,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,3,3,3,3,3,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,4,4,4,4,4,4,4,4,4,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,5,5,5,5,5,5,5,5,5,5,5,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,6,6,6,6,6,6,6,6,6,6,6,6,6,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,10,11,12,13,14,15},
        {15,14,13,12,11,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,11,12,13,14,15},
        {15,14,13,12,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,12,13,14,15},
        {15,14,13,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,13,14,15},
        {15,14,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,14,15},
        {15,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,15},
        {15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15}
    };

    public GridPosition offset(int dx, int dy) {
        return new GridPosition(
            Math.clamp(this.x + dx, 0, 127),
            Math.clamp(this.y + dy, 0, 127)
        );
    }
}
