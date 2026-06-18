package com.aow2.common.event;

/**
 * Reason codes for resource (credit) changes.
 * FIX (L3): Replaces String reason in ResourceChangedEvent with type-safe enum.
 * Every credit change is now categorized, making event processing and logging deterministic.
 */
public enum ResourceChangedReason {
    /** Periodic income from Command Centre. */
    INCOME,
    /** Spending credits on unit production. */
    UNIT_PRODUCTION,
    /** Refund from cancelling unit production. */
    PRODUCTION_CANCEL,
    /** Spending credits on building construction. */
    BUILDING_CONSTRUCTION,
    /** Refund from building demolition/cancel. */
    BUILDING_CANCEL,
    /** Spending credits on research. */
    RESEARCH,
    /** Credits received from killing enemy units. */
    KILL_REWARD,
    /** Credits received from rank promotion. */
    RANK_REWARD,
    /** Credits received from capturing a resource point. */
    RESOURCE_CAPTURE,
    /** Initial starting credits. */
    INITIAL,
    /** Cheat/debug credit adjustment. */
    DEBUG,
    /** Any other reason not explicitly categorized. */
    OTHER
}