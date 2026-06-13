package com.aow2.common.event;

public record ResourceChangedEvent(
    long tick,
    int playerId,
    int oldCredits,
    int newCredits,
    String reason
) implements GameEvent {}
