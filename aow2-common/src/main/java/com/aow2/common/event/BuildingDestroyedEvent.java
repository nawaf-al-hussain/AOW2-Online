package com.aow2.common.event;

import com.aow2.common.model.BuildingType;

public record BuildingDestroyedEvent(
    long tick,
    int buildingId,
    BuildingType buildingType,
    int destroyerId
) implements GameEvent {}
