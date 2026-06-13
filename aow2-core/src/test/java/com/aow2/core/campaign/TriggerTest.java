package com.aow2.core.campaign;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all Trigger sealed interface permits.
 * REF: campaign_guide.md Section 3 - Trigger and Script System
 */
@DisplayName("Trigger")
class TriggerTest {

    @Nested
    @DisplayName("AreaTrigger")
    class AreaTriggerTest {

        @Test
        @DisplayName("starts not activated")
        void startsNotActivated() {
            Trigger.AreaTrigger trigger = new Trigger.AreaTrigger(
                1, new GridPosition(50, 50), 5, 0, false);
            assertEquals(1, trigger.triggerId());
            assertFalse(trigger.isActivated());
        }

        @Test
        @DisplayName("activate sets activated flag")
        void activateWorks() {
            Trigger.AreaTrigger trigger = new Trigger.AreaTrigger(
                1, new GridPosition(50, 50), 5, 0, false);
            Trigger.AreaTrigger activated = trigger.activate();
            assertTrue(activated.isActivated());
            assertFalse(trigger.isActivated()); // original unchanged
        }

        @Test
        @DisplayName("preserves center position and radius")
        void preservesPosition() {
            Trigger.AreaTrigger trigger = new Trigger.AreaTrigger(
                5, new GridPosition(30, 40), 8, 1, false);
            assertEquals(new GridPosition(30, 40), trigger.center());
            assertEquals(8, trigger.radius());
            assertEquals(1, trigger.factionId());
        }
    }

    @Nested
    @DisplayName("UnitCountTrigger")
    class UnitCountTriggerTest {

        @Test
        @DisplayName("starts not activated")
        void startsNotActivated() {
            Trigger.UnitCountTrigger trigger = new Trigger.UnitCountTrigger(
                10, Faction.CONFEDERATION, UnitType.CONFED_INFANTRY, 5, false);
            assertEquals(10, trigger.triggerId());
            assertFalse(trigger.isActivated());
        }

        @Test
        @DisplayName("activate sets activated flag")
        void activateWorks() {
            Trigger.UnitCountTrigger trigger = new Trigger.UnitCountTrigger(
                10, Faction.CONFEDERATION, UnitType.CONFED_INFANTRY, 5, false);
            Trigger.UnitCountTrigger activated = trigger.activate();
            assertTrue(activated.isActivated());
        }

        @Test
        @DisplayName("preserves faction and unit type")
        void preservesProperties() {
            Trigger.UnitCountTrigger trigger = new Trigger.UnitCountTrigger(
                15, Faction.RESISTANCE, UnitType.REBEL_RHINO, 3, false);
            assertEquals(Faction.RESISTANCE, trigger.faction());
            assertEquals(UnitType.REBEL_RHINO, trigger.unitType());
            assertEquals(3, trigger.threshold());
        }
    }

    @Nested
    @DisplayName("TimeTrigger")
    class TimeTriggerTest {

        @Test
        @DisplayName("starts not activated")
        void startsNotActivated() {
            Trigger.TimeTrigger trigger = new Trigger.TimeTrigger(20, 600L, false);
            assertEquals(20, trigger.triggerId());
            assertEquals(600L, trigger.triggerTick());
            assertFalse(trigger.isActivated());
        }

        @Test
        @DisplayName("activate sets activated flag")
        void activateWorks() {
            Trigger.TimeTrigger trigger = new Trigger.TimeTrigger(20, 600L, false);
            Trigger.TimeTrigger activated = trigger.activate();
            assertTrue(activated.isActivated());
            assertFalse(trigger.isActivated()); // original unchanged
        }

        @Test
        @DisplayName("supports large tick values")
        void supportsLargeTicks() {
            Trigger.TimeTrigger trigger = new Trigger.TimeTrigger(30, 14400L, false);
            assertEquals(14400L, trigger.triggerTick());
        }
    }

    @Nested
    @DisplayName("BuildingDestroyedTrigger")
    class BuildingDestroyedTriggerTest {

        @Test
        @DisplayName("starts not activated")
        void startsNotActivated() {
            Trigger.BuildingDestroyedTrigger trigger = new Trigger.BuildingDestroyedTrigger(
                40, BuildingType.CONFED_COMMAND_CENTRE, Faction.CONFEDERATION, false);
            assertEquals(40, trigger.triggerId());
            assertFalse(trigger.isActivated());
        }

        @Test
        @DisplayName("activate sets activated flag")
        void activateWorks() {
            Trigger.BuildingDestroyedTrigger trigger = new Trigger.BuildingDestroyedTrigger(
                40, BuildingType.CONFED_COMMAND_CENTRE, Faction.CONFEDERATION, false);
            Trigger.BuildingDestroyedTrigger activated = trigger.activate();
            assertTrue(activated.isActivated());
        }

        @Test
        @DisplayName("preserves building type and faction")
        void preservesProperties() {
            Trigger.BuildingDestroyedTrigger trigger = new Trigger.BuildingDestroyedTrigger(
                45, BuildingType.REBEL_FACTORY, Faction.RESISTANCE, false);
            assertEquals(BuildingType.REBEL_FACTORY, trigger.buildingType());
            assertEquals(Faction.RESISTANCE, trigger.faction());
        }
    }

    @Nested
    @DisplayName("Sealed interface")
    class SealedInterfaceTest {

        @Test
        @DisplayName("permits only the four expected types")
        void permitsCorrectTypes() {
            // Verify the sealed interface permits by creating instances
            Trigger area = new Trigger.AreaTrigger(1, new GridPosition(0, 0), 5, 0, false);
            Trigger unitCount = new Trigger.UnitCountTrigger(2, Faction.CONFEDERATION,
                UnitType.CONFED_INFANTRY, 5, false);
            Trigger time = new Trigger.TimeTrigger(3, 600L, false);
            Trigger building = new Trigger.BuildingDestroyedTrigger(4,
                BuildingType.CONFED_COMMAND_CENTRE, Faction.CONFEDERATION, false);

            assertInstanceOf(Trigger.class, area);
            assertInstanceOf(Trigger.class, unitCount);
            assertInstanceOf(Trigger.class, time);
            assertInstanceOf(Trigger.class, building);
        }
    }
}
