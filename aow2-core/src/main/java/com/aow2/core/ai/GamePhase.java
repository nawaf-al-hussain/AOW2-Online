package com.aow2.core.ai;

/**
 * Represents the current phase of the game, used by the AI to adapt strategy.
 * <p>
 * REF: ai_analysis.md — AI behavior changes based on game progression.
 * Early game focuses on base building and infantry production.
 * Mid game transitions to tech research and vehicle production.
 * Late game focuses on full army composition and aggressive attacks.
 * <p>
 * Tick boundaries assume 10 ticks/second game rate:
 * - EARLY: first 300 ticks (30 seconds)
 * - MID: 300-1800 ticks (30 seconds - 3 minutes)
 * - LATE: 1800+ ticks (3+ minutes)
 */
public enum GamePhase {

    /** First 300 ticks (30 seconds) — build base, produce infantry. */
    EARLY(300),

    /** 300-1800 ticks (30 seconds - 3 minutes) — tech up, produce vehicles. */
    MID(1800),

    /** 1800+ ticks (3+ minutes) — full army, aggressive attacks. */
    LATE(Integer.MAX_VALUE);

    /** Upper tick boundary (exclusive) for this phase. */
    public final int tickBoundary;

    GamePhase(int tickBoundary) {
        this.tickBoundary = tickBoundary;
    }
}
