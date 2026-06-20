package com.aow2.core.ai;

/**
 * AI difficulty levels affecting decision frequency and strategy quality.
 * <p>
 * Higher difficulty reduces tick interval (faster decisions), increases
 * strategy quality (better choices), and allows more concurrent tasks.
 * <p>
 * REF: ai_analysis.md — difficulty affects reaction time and decision quality.
 * The original AI processes every 30 ticks at normal speed.
 * Difficulty scaling modifies production speed, build time, and credit generation.
 */
// UNVERIFIED (L-6): Tick intervals (60/30/15) are plausible but not confirmed by RE documentation.
public enum AIDifficulty {

    /**
     * Easy: slow decisions, 50% optimal choices, max 3 concurrent tasks, reduced income.
     * REF: ai_analysis.md — AI difficulty affects y.V[6] (game speed modifier)
     * REF: MASTER_DOCUMENTATION.md Section 4.5 — Easy: "lower income"
     */
    EASY(60, 0.5, 3, 0.7),

    /**
     * Normal: medium decisions, 75% optimal choices, max 5 concurrent tasks, standard income.
     * REF: ai_analysis.md — original AI processes every 30 ticks
     */
    NORMAL(30, 0.75, 5, 1.0),

    /**
     * Hard: fast decisions, 100% optimal choices, max 8 concurrent tasks, income bonus.
     * REF: ai_analysis.md — y.aU[player][5] (player-specific modifier)
     * REF: MASTER_DOCUMENTATION.md Section 4.5 — Hard: "income bonuses"
     */
    HARD(15, 1.0, 8, 1.3);

    /** Number of ticks between AI decision cycles. */
    public final int tickInterval;

    /** Probability of choosing the optimal action (0.0 to 1.0). */
    public final double strategyQuality;

    /** Maximum number of concurrent AI tasks (build orders, attacks, research). */
    public final int maxConcurrentTasks;

    /** Difficulty-based income multiplier for the RE formula playerModifier.
     * REF: MASTER_DOCUMENTATION.md Section 4.4 — "baseIncome * playerModifier"
     * ASSUMPTION: Exact values not in RE data; 0.7/1.0/1.3 are reasonable estimates.
     */
    public final double incomeModifier;

    AIDifficulty(int tickInterval, double strategyQuality, int maxConcurrentTasks, double incomeModifier) {
        this.tickInterval = tickInterval;
        this.strategyQuality = strategyQuality;
        this.maxConcurrentTasks = maxConcurrentTasks;
        this.incomeModifier = incomeModifier;
    }
}