package com.aow2.core.ai;

/**
 * Deterministic Linear Congruential Generator for lockstep multiplayer AI.
 * <p>
 * Uses a fixed-parameter LCG (Lehmer/Park-Miller style) that produces identical
 * sequences across all Java versions and platforms, unlike {@link java.util.Random}
 * whose internal implementation may change between JDK releases.
 * <p>
 * Formula: next = (state * MULTIPLIER + INCREMENT) mod MODULUS
 * <p>
 * This ensures AI decisions are bit-identical on both clients in a lockstep
 * multiplayer game, preventing desyncs caused by divergent random sequences.
 * <p>
 * REF: multiplayer_architecture.md - Deterministic simulation requirements
 *
 * FIX (P1-H1): Replaces java.util.Random to prevent cross-version desyncs.
 */
public final class DeterministicLCG {

    /** LCG modulus (2^31 - 1, a Mersenne prime — Park-Miller standard). */
    private static final long MODULUS = 0x7FFFFFFFL; // 2147483647

    /** LCG multiplier (Park-Miller standard for 2^31-1 modulus). */
    private static final long MULTIPLIER = 48271L;

    /** Current state of the generator. Must be in range [1, MODULUS-1]. */
    private long state;

    /**
     * Constructs a new LCG with the given seed.
     * The seed is processed to ensure state is in valid range [1, MODULUS-1].
     *
     * @param seed the initial seed value
     */
    public DeterministicLCG(long seed) {
        this.state = (seed % (MODULUS - 1)) + 1;
    }

    /**
     * Returns the next random integer in the range [0, Integer.MAX_VALUE].
     * Uses the Park-Miller algorithm for cross-platform determinism.
     *
     * @return a pseudo-random integer
     */
    public int nextInt() {
        state = (state * MULTIPLIER) % MODULUS;
        return (int) state;
    }

    /**
     * Returns the next random integer in the range [0, bound).
     *
     * @param bound the upper bound (exclusive)
     * @return a pseudo-random integer in [0, bound)
     * @throws IllegalArgumentException if bound is not positive
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, got: " + bound);
        }
        return nextInt() % bound;
    }

    /**
     * Returns the next random double value in the range [0.0, 1.0).
     *
     * @return a pseudo-random double in [0.0, 1.0)
     */
    public double nextDouble() {
        return (double) nextInt() / (double) MODULUS;
    }

    /**
     * Returns the next random boolean value.
     *
     * @return a pseudo-random boolean
     */
    public boolean nextBoolean() {
        return nextDouble() < 0.5;
    }
}
