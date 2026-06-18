package com.aow2.common.event;

/**
 * Emitted when a player's credit balance changes.
 * FIX (L3): Changed reason field from String to ResourceChangedReason enum
 * for type-safe event processing.
 */
public record ResourceChangedEvent(
    long tick,
    int playerId,
    int oldCredits,
    int newCredits,
    ResourceChangedReason reason
) implements GameEvent {}