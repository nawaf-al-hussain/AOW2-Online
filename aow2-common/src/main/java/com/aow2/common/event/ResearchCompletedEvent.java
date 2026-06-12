package com.aow2.common.event;

import com.aow2.common.model.Faction;

public record ResearchCompletedEvent(
    long tick,
    int playerId,
    Faction faction,
    int techId,
    String techName
) implements GameEvent {}
