package com.aow2.core.economy;

import com.aow2.common.config.GameConstants;
import com.aow2.common.event.ResourceChangedEvent;
import com.aow2.common.model.Faction;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the game economy: credit generation, spending, and tracking.
 * <p>
 * Each player accumulates credits from Command Centres with diminishing returns
 * for each additional CC. Credits are generated every 128 ticks matching the
 * original game cycle period ((aL.ah & 127) == 127).
 * <p>
 * REF: combat_formulas.md "Credit Generation Formula"
 * REF: GameConstants.CC_DIMINISHING_RETURNS = 0.30
 */
public final class EconomySystem {

    private static final Logger LOG = LoggerFactory.getLogger(EconomySystem.class);

    /** Number of ticks between credit generation cycles. REF: combat_formulas.md — (aL.ah & 127) == 127 means 128-tick cycle. */
    public static final int CREDIT_CYCLE_TICKS = GameConstants.CREDIT_GENERATION_CYCLE;

    /** Base income per Command Centre per cycle before diminishing returns. */
    public static final int BASE_CC_INCOME = 100;

    /** Maximum number of players supported. */
    private static final int MAX_PLAYERS = GameConstants.MAX_PLAYERS_PER_MATCH;

    /**
     * Starting credits for each player.
     * REF: map_system.md Section 6.1: "Q[i] = 100; // Starting credits"
     * FIX: Changed from 500 to 100 to match original game.
     */
    public static final int STARTING_CREDITS = 100;

    /** Maximum credits a player can accumulate. REF: map_system.md Section 5.2: income clamped to [0, 30000] */
    public static final int MAX_CREDITS = 30000;

    /** Per-player credit tracking. Index = playerId (0 or 1). */
    private final int[] playerCredits;

    /** Per-player income rate cache (recalculated each cycle). */
    private final int[] playerIncomeRates;

    /** Reference to the resource generator for income calculations. */
    private final ResourceGenerator resourceGenerator;

    /**
     * Constructs an EconomySystem with default starting credits.
     *
     * @param resourceGenerator the resource generator for income calculations
     */
    public EconomySystem(ResourceGenerator resourceGenerator) {
        this.resourceGenerator = resourceGenerator;
        this.playerCredits = new int[MAX_PLAYERS];
        this.playerIncomeRates = new int[MAX_PLAYERS];
        for (int i = 0; i < MAX_PLAYERS; i++) {
            playerCredits[i] = STARTING_CREDITS;
            playerIncomeRates[i] = 0;
        }
    }

    /**
     * Process economy tick for all players.
     * Credits are generated every 128 ticks (REF: combat_formulas.md — (aL.ah & 127) == 127).
     * <p>
     * REF: combat_formulas.md "Credit Generation Formula"
     * REF: GameConstants.CREDIT_GENERATION_CYCLE = 128
     *
     * @param entities the entity manager
     * @param state    the current game state
     */
    public void processTick(EntityManager entities, GameState state) {
        long tick = state.currentTick();
        // Credit generation every 128 ticks
        // REF: combat_formulas.md — (aL.ah & 127) == 127 means generate on last tick of cycle
        if (tick > 0 && tick % CREDIT_CYCLE_TICKS == CREDIT_CYCLE_TICKS - 1) {
            for (int playerId = 0; playerId < MAX_PLAYERS; playerId++) {
                int income = calculateIncome(playerId, entities);
                playerIncomeRates[playerId] = income;
                if (income > 0) {
                    int oldCredits = playerCredits[playerId];
                    playerCredits[playerId] += income;
                    state.enqueueEvent(new ResourceChangedEvent(
                        tick, playerId, oldCredits, playerCredits[playerId],
                        com.aow2.common.event.ResourceChangedReason.INCOME
                    ));
                    LOG.debug("Player {} earned {} credits (total: {})", playerId, income, playerCredits[playerId]);
                }
            }
        }
    }

    /**
     * Calculate income for a player based on their Command Centres.
     * Base income from first CC, 30% less for each additional CC.
     * <p>
     * REF: GameConstants.CC_DIMINISHING_RETURNS = 0.30
     * REF: combat_formulas.md — "income = (baseIncome * 7) / 10" for the 70% modifier
     *
     * @param playerId the player ID (0 or 1)
     * @param entities the entity manager
     * @return the total income per cycle for this player
     */
    public int calculateIncome(int playerId, EntityManager entities) {
        return resourceGenerator.calculateCycleIncome(playerId, entities);
    }

    /**
     * Check if player can afford a cost.
     *
     * @param playerId the player ID (0 or 1)
     * @param cost     the credit cost to check
     * @return true if the player has enough credits
     */
    public boolean canAfford(int playerId, int cost) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return false;
        }
        return playerCredits[playerId] >= cost;
    }

    /**
     * Deduct credits from a player. Returns true if successful.
     * Will not deduct if the player cannot afford the cost.
     *
     * @param playerId the player ID (0 or 1)
     * @param cost     the credit cost to deduct
     * @return true if credits were successfully deducted
     */
    public boolean spendCredits(int playerId, int cost) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return false;
        }
        if (playerCredits[playerId] < cost) {
            LOG.debug("Player {} cannot afford {} credits (has {})", playerId, cost, playerCredits[playerId]);
            return false;
        }
        playerCredits[playerId] -= cost;
        LOG.debug("Player {} spent {} credits (remaining: {})", playerId, cost, playerCredits[playerId]);
        return true;
    }

    /**
     * Add credits to a player (e.g., from kill rewards).
     *
     * @param playerId the player ID (0 or 1)
     * @param amount   the amount of credits to add
     */
    public void addCredits(int playerId, int amount) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return;
        }
        // REF: map_system.md Section 5.2 - income clamped to [0, 30000]
        playerCredits[playerId] = Math.min(playerCredits[playerId] + amount, MAX_CREDITS);
        LOG.debug("Player {} gained {} credits (total: {})", playerId, amount, playerCredits[playerId]);
    }

    /**
     * Get the current credit balance for a player.
     *
     * @param playerId the player ID (0 or 1)
     * @return the player's current credits
     */
    public int getCredits(int playerId) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return 0;
        }
        return playerCredits[playerId];
    }

    /**
     * Get the income rate for a player (last calculated cycle income).
     *
     * @param playerId the player ID (0 or 1)
     * @return the player's income per cycle
     */
    public int getIncomeRate(int playerId) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return 0;
        }
        return playerIncomeRates[playerId];
    }

    /**
     * Set credits directly for a player (used for testing or game initialization).
     *
     * @param playerId the player ID (0 or 1)
     * @param amount   the credit amount to set
     */
    public void setCredits(int playerId, int amount) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return;
        }
        playerCredits[playerId] = amount;
    }

    /**
     * Map a player ID to its Faction.
     * Player 0 = CONFEDERATION, Player 1 = RESISTANCE.
     *
     * @param playerId the player ID (0 or 1)
     * @return the corresponding Faction
     */
    public static Faction playerFaction(int playerId) {
        return switch (playerId) {
            case 0 -> Faction.CONFEDERATION;
            case 1 -> Faction.RESISTANCE;
            default -> throw new IllegalArgumentException("Invalid player ID: " + playerId);
        };
    }

    /**
     * Map a Faction to its player ID.
     *
     * @param faction the faction
     * @return the corresponding player ID
     */
    public static int playerId(Faction faction) {
        return switch (faction) {
            case CONFEDERATION -> 0;
            case RESISTANCE -> 1;
            default -> throw new IllegalArgumentException("Invalid faction for player: " + faction);
        };
    }
}
