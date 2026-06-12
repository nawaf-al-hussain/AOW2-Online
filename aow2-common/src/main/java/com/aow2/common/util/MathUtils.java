package com.aow2.common.util;

/**
 * Math utilities for game calculations.
 */
public final class MathUtils {
    private MathUtils() {}

    /**
     * Clamp value between min and max (inclusive).
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Calculate Manhattan distance between two grid positions.
     */
    public static int manhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    /**
     * Calculate Chebyshev distance (max of axis distances) - used for 8-directional movement.
     */
    public static int chebyshevDistance(int x1, int y1, int x2, int y2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
    }

    /**
     * Calculate Euclidean distance squared (avoids sqrt for comparisons).
     */
    public static int distanceSquared(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
