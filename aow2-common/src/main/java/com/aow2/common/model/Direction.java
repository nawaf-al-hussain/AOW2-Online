package com.aow2.common.model;

/**
 * 8-directional facing used for unit movement and sprite rendering.
 * REF: unit_stats.md offset +404 - facing direction (0-7, 8 compass points)
 *
 * FIX LOG:
 * - Added dx() method returning X delta for this direction
 * - Added dy() method returning Y delta for this direction
 * - Added opposite() method returning the opposing direction
 */
public enum Direction {
    NORTH(0, 0, -1),
    NORTH_EAST(1, 1, -1),
    EAST(2, 1, 0),
    SOUTH_EAST(3, 1, 1),
    SOUTH(4, 0, 1),
    SOUTH_WEST(5, -1, 1),
    WEST(6, -1, 0),
    NORTH_WEST(7, -1, -1);

    private final int code;
    private final int dx;
    private final int dy;

    Direction(int code, int dx, int dy) {
        this.code = code;
        this.dx = dx;
        this.dy = dy;
    }

    public int code() {
        return code;
    }

    /**
     * Returns the X delta (column offset) for moving one cell in this direction.
     * Positive = right (east), negative = left (west), 0 = no horizontal movement.
     *
     * @return the X delta for this direction
     */
    public int dx() {
        return dx;
    }

    /**
     * Returns the Y delta (row offset) for moving one cell in this direction.
     * Positive = down (south), negative = up (north), 0 = no vertical movement.
     *
     * @return the Y delta for this direction
     */
    public int dy() {
        return dy;
    }

    /**
     * Returns the direction opposite to this one (180-degree rotation).
     *
     * @return the opposite direction
     */
    public Direction opposite() {
        return fromCode((code + 4) % 8);
    }

    public static Direction fromCode(int code) {
        return switch (code) {
            case 0 -> NORTH;
            case 1 -> NORTH_EAST;
            case 2 -> EAST;
            case 3 -> SOUTH_EAST;
            case 4 -> SOUTH;
            case 5 -> SOUTH_WEST;
            case 6 -> WEST;
            case 7 -> NORTH_WEST;
            default -> throw new IllegalArgumentException("Invalid direction code: " + code);
        };
    }
}
