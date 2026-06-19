package com.aow2.common.util;

/**
 * Math utilities for game calculations.
 * <p>
 * This class is retained as a central utility consolidation point for
 * commonly used game math operations. Java 21 built-in Math.clamp() is
 * preferred for simple clamping; the methods here provide game-specific
 * convenience and floating-point variants.
 */
public final class MathUtils {
    private MathUtils() {}

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

    /**
     * Clamp an integer value within the specified range (inclusive).
     * Convenience wrapper when a non-Java-21 baseline is needed.
     *
     * @param value the value to clamp
     * @param min   minimum allowed value
     * @param max   maximum allowed value
     * @return the clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp a long value within the specified range (inclusive).
     *
     * @param value the value to clamp
     * @param min   minimum allowed value
     * @param max   maximum allowed value
     * @return the clamped value
     */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linear interpolation between two values.
     * Useful for smooth animations, camera movement, and UI transitions.
     *
     * @param a start value
     * @param b end value
     * @param t interpolation factor (0.0 = a, 1.0 = b)
     * @return interpolated value
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Linear interpolation between two double values.
     *
     * @param a start value
     * @param b end value
     * @param t interpolation factor (0.0 = a, 1.0 = b)
     * @return interpolated value
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Calculate Chebyshev distance between two GridPosition-like coordinate pairs,
     * returning the result as a double for floating-point range comparisons.
     *
     * @param x1 source x
     * @param y1 source y
     * @param x2 target x
     * @param y2 target y
     * @return Chebyshev distance as double
     */
    public static double chebyshevDistanceDouble(double x1, double y1, double x2, double y2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
    }
}
