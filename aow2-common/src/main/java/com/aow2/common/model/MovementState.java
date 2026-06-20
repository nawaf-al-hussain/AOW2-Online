package com.aow2.common.model;

/**
 * Movement state machine for unit pathfinding and movement.
 * REF: pathfinding.md — units track movement state with stuck counter at offset +1515
 * REF: pathfinding.md — stuckCounter >= 5 triggers path recalculation
 */
public enum MovementState {
    /** Unit is stationary with no movement target. */
    IDLE,

    /** Unit is actively following a path to its target. */
    MOVING,

    /** Unit cannot advance along its path; awaiting re-pathfinding. */
    STUCK,

    /** Unit has reached its destination or target is no longer valid. */
    ARRIVED,

    /** Unit has encountered an enemy within attack range and stopped to engage. */
    ATTACKING
}
