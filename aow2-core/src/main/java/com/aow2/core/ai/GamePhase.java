package com.aow2.core.ai;

/**
 * Represents the current phase of the game, used by the AI to adapt strategy.
 * <p>
 * REF: ai_analysis.md — AI behavior changes based on game progression.
 * Early game focuses on base building and infantry production.
 * Mid game transitions to tech research and vehicle production.
 * Late game focuses on full army composition and aggressive attacks.
 * <p>
 * Tick boundaries assume 60 ticks/second game rate:
 * - EARLY: first 600 ticks (10 seconds)
 * - MID: 600-3600 ticks (10-60 seconds)
 * - LATE: 3600+ ticks (60+ seconds)
 */
public enum GamePhase {

    /** First 600 ticks (10 seconds) — build base, produce infantry. */
    EARLY(600),

    /** 600-3600 ticks (10-60 seconds) — tech up, produce vehicles. */
    MID(3600),

    /** 3600+ ticks (60+ seconds) — full army, aggressive attacks. */
    LATE(Integer.MAX_VALUE);

    /** Upper tick boundary (exclusive) for this phase. */
    public final int tickBoundary;

    GamePhase(int tickBoundary) {
        this.tickBoundary = tickBoundary;
    }
}
