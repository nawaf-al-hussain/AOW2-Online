package com.aow2.common.model;

/**
 * 8-directional facing used for unit movement and sprite rendering.
 * REF: unit_stats.md offset +404 - facing direction (0-7, 8 compass points)
 */
public enum Direction {
    NORTH(0),
    NORTH_EAST(1),
    EAST(2),
    SOUTH_EAST(3),
    SOUTH(4),
    SOUTH_WEST(5),
    WEST(6),
    NORTH_WEST(7);

    private final int code;

    Direction(int code) {
        this.code = code;
    }

    public int code() {
        return code;
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
