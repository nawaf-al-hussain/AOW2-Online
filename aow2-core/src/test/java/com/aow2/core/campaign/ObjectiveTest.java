package com.aow2.core.campaign;

import com.aow2.common.model.GridPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all Objective sealed interface permits.
 * REF: campaign_guide.md Section 3.4 - Victory conditions
 */
@DisplayName("Objective")
class ObjectiveTest {

    @Nested
    @DisplayName("DestroyObjective")
    class DestroyObjectiveTest {

        @Test
        @DisplayName("starts incomplete with zero current count")
        void startsIncomplete() {
            Objective.DestroyObjective obj = new Objective.DestroyObjective(
                "Destroy enemies", 10, 0);
            assertFalse(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("completes when current count reaches target")
        void completesAtTarget() {
            Objective.DestroyObjective obj = new Objective.DestroyObjective(
                "Destroy enemies", 5, 5);
            assertTrue(obj.isCompleted());
        }

        @Test
        @DisplayName("completes when current count exceeds target")
        void completesPastTarget() {
            Objective.DestroyObjective obj = new Objective.DestroyObjective(
                "Destroy enemies", 3, 7);
            assertTrue(obj.isCompleted());
        }

        @Test
        @DisplayName("increment creates new instance with incremented count")
        void incrementWorks() {
            Objective.DestroyObjective obj = new Objective.DestroyObjective(
                "Destroy enemies", 3, 1);
            Objective.DestroyObjective incremented = obj.increment();
            assertEquals(2, incremented.currentCount());
            assertEquals(1, obj.currentCount()); // original unchanged
        }

        @Test
        @DisplayName("never fails - destroy objectives are always achievable")
        void neverFails() {
            Objective.DestroyObjective obj = new Objective.DestroyObjective(
                "Destroy enemies", 100, 0);
            assertFalse(obj.isFailed());
        }
    }

    @Nested
    @DisplayName("DefendObjective")
    class DefendObjectiveTest {

        @Test
        @DisplayName("starts incomplete")
        void startsIncomplete() {
            Objective.DefendObjective obj = new Objective.DefendObjective(
                "Defend HQ", 1, 6000L, 0L, false);
            assertFalse(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("completes when duration expires and entity is alive")
        void completesOnDuration() {
            Objective.DefendObjective obj = new Objective.DefendObjective(
                "Defend HQ", 1, 6000L, 6000L, false);
            assertTrue(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("fails when entity destroyed before duration")
        void failsOnDestruction() {
            Objective.DefendObjective obj = new Objective.DefendObjective(
                "Defend HQ", 1, 6000L, 3000L, true);
            assertFalse(obj.isCompleted());
            assertTrue(obj.isFailed());
        }

        @Test
        @DisplayName("does not fail when entity destroyed after duration")
        void noFailAfterDuration() {
            Objective.DefendObjective obj = new Objective.DefendObjective(
                "Defend HQ", 1, 6000L, 7000L, true);
            assertTrue(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("tick increments elapsed ticks")
        void tickWorks() {
            Objective.DefendObjective obj = new Objective.DefendObjective(
                "Defend HQ", 1, 6000L, 0L, false);
            Objective.DefendObjective ticked = obj.tick();
            assertEquals(1, ticked.elapsedTicks());
        }

        @Test
        @DisplayName("destroyEntity marks entity as destroyed")
        void destroyEntityWorks() {
            Objective.DefendObjective obj = new Objective.DefendObjective(
                "Defend HQ", 1, 6000L, 0L, false);
            Objective.DefendObjective destroyed = obj.destroyEntity();
            assertTrue(destroyed.entityDestroyed());
            assertFalse(obj.entityDestroyed()); // original unchanged
        }
    }

    @Nested
    @DisplayName("EscortObjective")
    class EscortObjectiveTest {

        @Test
        @DisplayName("starts incomplete with unit alive")
        void startsIncomplete() {
            Objective.EscortObjective obj = new Objective.EscortObjective(
                "Escort VIP", 100, new GridPosition(50, 50), true, false);
            assertFalse(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("completes when unit reaches destination")
        void completesOnArrival() {
            Objective.EscortObjective obj = new Objective.EscortObjective(
                "Escort VIP", 100, new GridPosition(50, 50), true, true);
            assertTrue(obj.isCompleted());
        }

        @Test
        @DisplayName("fails when unit dies before reaching destination")
        void failsOnDeath() {
            Objective.EscortObjective obj = new Objective.EscortObjective(
                "Escort VIP", 100, new GridPosition(50, 50), false, false);
            assertFalse(obj.isCompleted());
            assertTrue(obj.isFailed());
        }

        @Test
        @DisplayName("does not fail if unit died after reaching destination")
        void noFailIfArrivedFirst() {
            Objective.EscortObjective obj = new Objective.EscortObjective(
                "Escort VIP", 100, new GridPosition(50, 50), false, true);
            assertTrue(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("arrive marks destination as reached")
        void arriveWorks() {
            Objective.EscortObjective obj = new Objective.EscortObjective(
                "Escort VIP", 100, new GridPosition(50, 50), true, false);
            Objective.EscortObjective arrived = obj.arrive();
            assertTrue(arrived.reachedDestination());
        }

        @Test
        @DisplayName("killUnit marks unit as dead")
        void killUnitWorks() {
            Objective.EscortObjective obj = new Objective.EscortObjective(
                "Escort VIP", 100, new GridPosition(50, 50), true, false);
            Objective.EscortObjective killed = obj.killUnit();
            assertFalse(killed.unitAlive());
        }
    }

    @Nested
    @DisplayName("TimedObjective")
    class TimedObjectiveTest {

        @Test
        @DisplayName("starts incomplete")
        void startsIncomplete() {
            Objective.TimedObjective obj = new Objective.TimedObjective(
                "Beat the clock", 6000L, 0L, false);
            assertFalse(obj.isCompleted());
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("completes when other objectives met within time")
        void completesInTime() {
            Objective.TimedObjective obj = new Objective.TimedObjective(
                "Beat the clock", 6000L, 3000L, true);
            assertTrue(obj.isCompleted());
        }

        @Test
        @DisplayName("fails when time expires and objectives not met")
        void failsOnTimeout() {
            Objective.TimedObjective obj = new Objective.TimedObjective(
                "Beat the clock", 6000L, 7000L, false);
            assertFalse(obj.isCompleted());
            assertTrue(obj.isFailed());
        }

        @Test
        @DisplayName("does not fail if objectives met even past time")
        void noFailWithMetObjectives() {
            Objective.TimedObjective obj = new Objective.TimedObjective(
                "Beat the clock", 6000L, 7000L, true);
            // Completed because other objectives are met (even though past duration)
            assertTrue(obj.isCompleted());
        }

        @Test
        @DisplayName("tick increments elapsed ticks")
        void tickWorks() {
            Objective.TimedObjective obj = new Objective.TimedObjective(
                "Beat the clock", 6000L, 0L, false);
            Objective.TimedObjective ticked = obj.tick();
            assertEquals(1, ticked.elapsedTicks());
        }

        @Test
        @DisplayName("withOtherObjectivesMet updates flag")
        void withOtherObjectivesMetWorks() {
            Objective.TimedObjective obj = new Objective.TimedObjective(
                "Beat the clock", 6000L, 0L, false);
            Objective.TimedObjective updated = obj.withOtherObjectivesMet(true);
            assertTrue(updated.otherObjectivesMet());
        }
    }

    @Nested
    @DisplayName("CaptureObjective")
    class CaptureObjectiveTest {

        @Test
        @DisplayName("starts incomplete")
        void startsIncomplete() {
            Objective.CaptureObjective obj = new Objective.CaptureObjective(
                "Capture point", new GridPosition(64, 64), false);
            assertFalse(obj.isCompleted());
        }

        @Test
        @DisplayName("completes when captured flag is set")
        void completesOnCapture() {
            Objective.CaptureObjective obj = new Objective.CaptureObjective(
                "Capture point", new GridPosition(64, 64), true);
            assertTrue(obj.isCompleted());
        }

        @Test
        @DisplayName("never fails - capture objectives are always achievable")
        void neverFails() {
            Objective.CaptureObjective obj = new Objective.CaptureObjective(
                "Capture point", new GridPosition(64, 64), false);
            assertFalse(obj.isFailed());
        }

        @Test
        @DisplayName("capture sets captured flag")
        void captureWorks() {
            Objective.CaptureObjective obj = new Objective.CaptureObjective(
                "Capture point", new GridPosition(64, 64), false);
            Objective.CaptureObjective captured = obj.capture();
            assertTrue(captured.captured());
            assertFalse(obj.captured()); // original unchanged
        }
    }
}
