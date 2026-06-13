package com.aow2.common.event;

import com.aow2.common.model.UnitType;

public record UnitProducedEvent(
    long tick,
    int playerId,
    UnitType unitType,
    int producedUnitId
) implements GameEvent {}
