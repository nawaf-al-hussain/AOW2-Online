package com.aow2.common.event;

public record DamageAppliedEvent(
    long tick,
    int targetId,
    int damage,
    int remainingHp,
    int attackerId
) implements GameEvent {}
