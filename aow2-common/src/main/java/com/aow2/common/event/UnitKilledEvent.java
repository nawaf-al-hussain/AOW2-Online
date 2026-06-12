package com.aow2.common.event;

import com.aow2.common.model.UnitType;

public record UnitKilledEvent(
    long tick,
    int unitId,
    UnitType unitType,
    int killerId
) implements GameEvent {}
