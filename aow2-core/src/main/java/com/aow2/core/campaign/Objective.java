package com.aow2.core.campaign;

import com.aow2.common.model.GridPosition;

/**
 * Mission objective types for campaign missions.
 * Each objective tracks a specific win/loss condition that must be met.
 * REF: campaign_guide.md - objective types: destroy, defend, escort, timed, capture
 * REF: campaign_guide.md Section 3.4 - Victory condition: no buildings + 7 or fewer units
 */
public sealed interface Objective permits
    Objective.DestroyObjective, Objective.DefendObjective,
    Objective.EscortObjective, Objective.TimedObjective,
    Objective.CaptureObjective {

    /**
     * Returns the display name of this objective.
     *
     * @return objective name
     */
    String name();

    /**
     * Returns whether this objective has been completed.
     *
     * @return true if completed
     */
    boolean isCompleted();

    /**
     * Returns whether this objective has been failed.
     *
     * @return true if failed
     */
    boolean isFailed();

    /**
     * Destroy a specified number of enemy units or buildings.
     * REF: campaign_guide.md Section 3.4 - destroying enemy building: +200 score
     */
    record DestroyObjective(
        String name,
        int targetCount,
        int currentCount
    ) implements Objective {

        @Override
        public boolean isCompleted() {
            return currentCount >= targetCount;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        /**
         * Creates a new DestroyObjective with an incremented current count.
         *
         * @return new objective with currentCount + 1
         */
        public DestroyObjective increment() {
            return new DestroyObjective(name, targetCount, currentCount + 1);
        }
    }

    /**
     * Defend a specific entity for a duration (in game ticks).
     * Fails if the entity is destroyed before the duration expires.
     * REF: campaign_guide.md Section 3.4 - building must survive
     */
    record DefendObjective(
        String name,
        int entityId,
        long durationTicks,
        long elapsedTicks,
        boolean entityDestroyed
    ) implements Objective {

        @Override
        public boolean isCompleted() {
            return elapsedTicks >= durationTicks && !entityDestroyed;
        }

        @Override
        public boolean isFailed() {
            return entityDestroyed && elapsedTicks < durationTicks;
        }

        /**
         * Creates a new DefendObjective with an incremented elapsed tick count.
         *
         * @return new objective with elapsedTicks + 1
         */
        public DefendObjective tick() {
            return new DefendObjective(name, entityId, durationTicks, elapsedTicks + 1, entityDestroyed);
        }

        /**
         * Creates a new DefendObjective with the entity marked as destroyed.
         *
         * @return new objective with entityDestroyed = true
         */
        public DefendObjective destroyEntity() {
            return new DefendObjective(name, entityId, durationTicks, elapsedTicks, true);
        }
    }

    /**
     * Escort a specific unit to a destination position.
     * Fails if the escorted unit is killed before reaching the destination.
     */
    record EscortObjective(
        String name,
        int unitId,
        GridPosition destination,
        boolean unitAlive,
        boolean reachedDestination
    ) implements Objective {

        @Override
        public boolean isCompleted() {
            return reachedDestination;
        }

        @Override
        public boolean isFailed() {
            return !unitAlive && !reachedDestination;
        }

        /**
         * Creates a new EscortObjective with the unit marked as having reached its destination.
         *
         * @return new objective with reachedDestination = true
         */
        public EscortObjective arrive() {
            return new EscortObjective(name, unitId, destination, unitAlive, true);
        }

        /**
         * Creates a new EscortObjective with the unit marked as dead.
         *
         * @return new objective with unitAlive = false
         */
        public EscortObjective killUnit() {
            return new EscortObjective(name, unitId, destination, false, reachedDestination);
        }
    }

    /**
     * Complete the objective within a time limit (in game ticks).
     * Fails if the timer expires before other objectives are met.
     * REF: campaign_guide.md Section 3.5 - time-based victory with tick thresholds
     */
    record TimedObjective(
        String name,
        long durationTicks,
        long elapsedTicks,
        boolean otherObjectivesMet
    ) implements Objective {

        @Override
        public boolean isCompleted() {
            return otherObjectivesMet && elapsedTicks <= durationTicks;
        }

        @Override
        public boolean isFailed() {
            return elapsedTicks > durationTicks && !otherObjectivesMet;
        }

        /**
         * Creates a new TimedObjective with an incremented elapsed tick count.
         *
         * @return new objective with elapsedTicks + 1
         */
        public TimedObjective tick() {
            return new TimedObjective(name, durationTicks, elapsedTicks + 1, otherObjectivesMet);
        }

        /**
         * Creates a new TimedObjective with the other-objectives-met flag set.
         *
         * @param met whether the other objectives have been met
         * @return new objective with the updated flag
         */
        public TimedObjective withOtherObjectivesMet(boolean met) {
            return new TimedObjective(name, durationTicks, elapsedTicks, met);
        }
    }

    /**
     * Capture a specific position on the map (move a unit to the target position).
     * REF: campaign_guide.md - area-based triggers with position and radius
     */
    record CaptureObjective(
        String name,
        GridPosition targetPosition,
        boolean captured
    ) implements Objective {

        @Override
        public boolean isCompleted() {
            return captured;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        /**
         * Creates a new CaptureObjective with the captured flag set.
         *
         * @return new objective with captured = true
         */
        public CaptureObjective capture() {
            return new CaptureObjective(name, targetPosition, true);
        }
    }
}
