package com.aow2.common.event;

import com.aow2.common.model.BuildingType;

public record BuildingCompletedEvent(
    long tick,
    int playerId,
    BuildingType buildingType,
    int buildingId
) implements GameEvent {}
