package com.aow2.common.event;

/**
 * Base interface for all game events.
 * Events are fired through the EventBus when game state changes occur.
 */
public sealed interface GameEvent permits
    UnitKilledEvent,
    BuildingDestroyedEvent,
    ResearchCompletedEvent,
    ResourceChangedEvent,
    UnitProducedEvent,
    BuildingCompletedEvent,
    DamageAppliedEvent {
    long tick();
}
