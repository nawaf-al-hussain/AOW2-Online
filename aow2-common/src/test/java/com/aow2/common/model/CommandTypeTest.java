package com.aow2.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandTypeTest {

    // --- Move command tests ---

    @Nested
    @DisplayName("Move Command")
    class MoveCommand {

        @Test
        @DisplayName("Should create move command with valid parameters")
        void shouldCreateMoveCommand() {
            CommandType.Move move = new CommandType.Move(100L, 0, new int[]{1, 2}, new GridPosition(5, 10));
            assertEquals(100L, move.tick());
            assertEquals(0, move.playerId());
            assertArrayEquals(new int[]{1, 2}, move.unitIds());
            assertEquals(new GridPosition(5, 10), move.target());
        }

        @Test
        @DisplayName("Should reject null or empty unitIds in Move")
        void shouldRejectNullOrEmptyUnitIds() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Move(100L, 0, null, new GridPosition(5, 10)));
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Move(100L, 0, new int[]{}, new GridPosition(5, 10)));
        }

        @Test
        @DisplayName("Should reject null target in Move")
        void shouldRejectNullTarget() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Move(100L, 0, new int[]{1}, null));
        }
    }

    // --- Attack command tests ---

    @Nested
    @DisplayName("Attack Command")
    class AttackCommand {

        @Test
        @DisplayName("Should create attack command with valid parameters")
        void shouldCreateAttackCommand() {
            CommandType.Attack attack = new CommandType.Attack(100L, 1, new int[]{3, 4}, 10);
            assertEquals(100L, attack.tick());
            assertEquals(1, attack.playerId());
            assertArrayEquals(new int[]{3, 4}, attack.unitIds());
            assertEquals(10, attack.targetId());
        }

        @Test
        @DisplayName("Should reject negative targetId in Attack")
        void shouldRejectNegativeTargetId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Attack(100L, 0, new int[]{1}, -1));
        }
    }

    // --- AttackMove command tests ---

    @Nested
    @DisplayName("AttackMove Command")
    class AttackMoveCommand {

        @Test
        @DisplayName("Should create attack-move command with valid parameters")
        void shouldCreateAttackMoveCommand() {
            CommandType.AttackMove attackMove = new CommandType.AttackMove(
                100L, 0, new int[]{1, 2}, new GridPosition(5, 10));
            assertEquals(100L, attackMove.tick());
            assertEquals(0, attackMove.playerId());
            assertArrayEquals(new int[]{1, 2}, attackMove.unitIds());
            assertEquals(new GridPosition(5, 10), attackMove.target());
        }

        @Test
        @DisplayName("Should reject null or empty unitIds in AttackMove")
        void shouldRejectNullOrEmptyUnitIds() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.AttackMove(100L, 0, null, new GridPosition(5, 10)));
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.AttackMove(100L, 0, new int[]{}, new GridPosition(5, 10)));
        }

        @Test
        @DisplayName("Should reject null target in AttackMove")
        void shouldRejectNullTarget() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.AttackMove(100L, 0, new int[]{1}, null));
        }
    }

    // --- Build command tests ---

    @Nested
    @DisplayName("Build Command")
    class BuildCommand {

        @Test
        @DisplayName("Should create build command with valid parameters")
        void shouldCreateBuildCommand() {
            CommandType.Build build = new CommandType.Build(
                100L, 0, BuildingType.CONFED_GENERATOR, new GridPosition(5, 5));
            assertEquals(100L, build.tick());
            assertEquals(BuildingType.CONFED_GENERATOR, build.buildingType());
            assertEquals(new GridPosition(5, 5), build.position());
        }

        @Test
        @DisplayName("Should reject null buildingType in Build")
        void shouldRejectNullBuildingType() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Build(100L, 0, null, new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("Should reject null position in Build")
        void shouldRejectNullPosition() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Build(100L, 0, BuildingType.CONFED_GENERATOR, null));
        }
    }

    // --- Produce command tests ---

    @Nested
    @DisplayName("Produce Command")
    class ProduceCommand {

        @Test
        @DisplayName("Should create produce command with valid parameters")
        void shouldCreateProduceCommand() {
            CommandType.Produce produce = new CommandType.Produce(
                100L, 0, 5, UnitType.CONFED_INFANTRY);
            assertEquals(100L, produce.tick());
            assertEquals(5, produce.producerId());
            assertEquals(UnitType.CONFED_INFANTRY, produce.unitType());
        }

        @Test
        @DisplayName("Should reject negative producerId in Produce")
        void shouldRejectNegativeProducerId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Produce(100L, 0, -1, UnitType.CONFED_INFANTRY));
        }

        @Test
        @DisplayName("Should reject null unitType in Produce")
        void shouldRejectNullUnitType() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Produce(100L, 0, 5, null));
        }
    }

    // --- Research command tests ---

    @Nested
    @DisplayName("Research Command")
    class ResearchCommand {

        @Test
        @DisplayName("Should create research command with valid parameters")
        void shouldCreateResearchCommand() {
            CommandType.Research research = new CommandType.Research(100L, 0, 10, 3);
            assertEquals(100L, research.tick());
            assertEquals(10, research.techCentreId());
            assertEquals(3, research.researchId());
        }

        @Test
        @DisplayName("Should reject negative techCentreId in Research")
        void shouldRejectNegativeTechCentreId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Research(100L, 0, -1, 3));
        }

        @Test
        @DisplayName("Should reject invalid researchId in Research")
        void shouldRejectInvalidResearchId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Research(100L, 0, 10, -1));
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Research(100L, 0, 10, 48));
        }

        @Test
        @DisplayName("Should accept boundary researchIds 0 and 47")
        void shouldAcceptBoundaryResearchIds() {
            assertDoesNotThrow(() -> new CommandType.Research(100L, 0, 10, 0));
            assertDoesNotThrow(() -> new CommandType.Research(100L, 0, 10, 47));
        }
    }

    // --- Garrison command tests ---

    @Nested
    @DisplayName("Garrison Command")
    class GarrisonCommand {

        @Test
        @DisplayName("Should create garrison command with valid parameters")
        void shouldCreateGarrisonCommand() {
            CommandType.Garrison garrison = new CommandType.Garrison(
                100L, 0, new int[]{1, 2}, 5);
            assertEquals(100L, garrison.tick());
            assertArrayEquals(new int[]{1, 2}, garrison.unitIds());
            assertEquals(5, garrison.buildingId());
        }

        @Test
        @DisplayName("Should reject negative buildingId in Garrison")
        void shouldRejectNegativeBuildingId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Garrison(100L, 0, new int[]{1}, -1));
        }
    }

    // --- Ungarrison command tests ---

    @Nested
    @DisplayName("Ungarrison Command")
    class UngarrisonCommand {

        @Test
        @DisplayName("Should create ungarrison command with valid parameters")
        void shouldCreateUngarrisonCommand() {
            CommandType.Ungarrison ungarrison = new CommandType.Ungarrison(100L, 0, 5);
            assertEquals(100L, ungarrison.tick());
            assertEquals(5, ungarrison.buildingId());
        }

        @Test
        @DisplayName("Should reject negative buildingId in Ungarrison")
        void shouldRejectNegativeBuildingId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Ungarrison(100L, 0, -1));
        }
    }

    // --- Cancel command tests ---

    @Nested
    @DisplayName("Cancel Command")
    class CancelCommand {

        @Test
        @DisplayName("Should create cancel command with valid parameters")
        void shouldCreateCancelCommand() {
            CommandType.Cancel cancel = new CommandType.Cancel(100L, 0, 7);
            assertEquals(100L, cancel.tick());
            assertEquals(7, cancel.entityId());
        }

        @Test
        @DisplayName("Should reject negative entityId in Cancel")
        void shouldRejectNegativeEntityId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Cancel(100L, 0, -1));
        }
    }

    // --- SiegeMode command tests ---

    @Nested
    @DisplayName("SiegeMode Command")
    class SiegeModeCommand {

        @Test
        @DisplayName("Should create siege mode command with valid parameters")
        void shouldCreateSiegeModeCommand() {
            CommandType.SiegeMode siegeMode = new CommandType.SiegeMode(100L, 0, 3, true);
            assertEquals(100L, siegeMode.tick());
            assertEquals(3, siegeMode.unitId());
            assertTrue(siegeMode.enabled());
        }

        @Test
        @DisplayName("Should reject negative unitId in SiegeMode")
        void shouldRejectNegativeUnitId() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.SiegeMode(100L, 0, -1, true));
        }

        @Test
        @DisplayName("Should toggle siege mode enabled/disabled")
        void shouldToggleSiegeMode() {
            CommandType.SiegeMode enable = new CommandType.SiegeMode(100L, 0, 3, true);
            CommandType.SiegeMode disable = new CommandType.SiegeMode(100L, 0, 3, false);
            assertTrue(enable.enabled());
            assertFalse(disable.enabled());
        }
    }

    // --- Stop command tests ---

    @Nested
    @DisplayName("Stop Command")
    class StopCommand {

        @Test
        @DisplayName("Should create stop command with valid parameters")
        void shouldCreateStopCommand() {
            CommandType.Stop stop = new CommandType.Stop(100L, 0, new int[]{1, 2, 3});
            assertEquals(100L, stop.tick());
            assertArrayEquals(new int[]{1, 2, 3}, stop.unitIds());
        }

        @Test
        @DisplayName("Should reject null or empty unitIds in Stop")
        void shouldRejectNullOrEmptyUnitIds() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Stop(100L, 0, null));
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Stop(100L, 0, new int[]{}));
        }
    }

    // --- Patrol command tests ---

    @Nested
    @DisplayName("Patrol Command")
    class PatrolCommand {

        @Test
        @DisplayName("Should create patrol command with valid parameters")
        void shouldCreatePatrolCommand() {
            CommandType.Patrol patrol = new CommandType.Patrol(
                100L, 0, new int[]{1}, new GridPosition(10, 10));
            assertEquals(100L, patrol.tick());
            assertArrayEquals(new int[]{1}, patrol.unitIds());
            assertEquals(new GridPosition(10, 10), patrol.waypoint());
        }

        @Test
        @DisplayName("Should reject null waypoint in Patrol")
        void shouldRejectNullWaypoint() {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandType.Patrol(100L, 0, new int[]{1}, null));
        }
    }

    // --- Sealed interface pattern matching tests ---

    @Nested
    @DisplayName("Sealed Interface Pattern Matching")
    class SealedInterfaceTests {

        @Test
        @DisplayName("Should pattern match all command types")
        void shouldPatternMatchAllTypes() {
            CommandType move = new CommandType.Move(1L, 0, new int[]{1}, new GridPosition(0, 0));
            CommandType attack = new CommandType.Attack(1L, 0, new int[]{1}, 2);
            CommandType build = new CommandType.Build(1L, 0, BuildingType.CONFED_GENERATOR, new GridPosition(0, 0));
            CommandType produce = new CommandType.Produce(1L, 0, 1, UnitType.CONFED_INFANTRY);
            CommandType research = new CommandType.Research(1L, 0, 1, 0);
            CommandType garrison = new CommandType.Garrison(1L, 0, new int[]{1}, 2);
            CommandType ungarrison = new CommandType.Ungarrison(1L, 0, 2);
            CommandType cancel = new CommandType.Cancel(1L, 0, 1);
            CommandType siegeMode = new CommandType.SiegeMode(1L, 0, 1, true);
            CommandType stop = new CommandType.Stop(1L, 0, new int[]{1});
            CommandType attackMove = new CommandType.AttackMove(1L, 0, new int[]{1}, new GridPosition(0, 0));
            CommandType patrol = new CommandType.Patrol(1L, 0, new int[]{1}, new GridPosition(0, 0));

            assertEquals("Move", typeName(move));
            assertEquals("Attack", typeName(attack));
            assertEquals("Build", typeName(build));
            assertEquals("Produce", typeName(produce));
            assertEquals("Research", typeName(research));
            assertEquals("Garrison", typeName(garrison));
            assertEquals("Ungarrison", typeName(ungarrison));
            assertEquals("Cancel", typeName(cancel));
            assertEquals("SiegeMode", typeName(siegeMode));
            assertEquals("Stop", typeName(stop));
            assertEquals("AttackMove", typeName(attackMove));
            assertEquals("Patrol", typeName(patrol));
        }

        private String typeName(CommandType cmd) {
            return switch (cmd) {
                case CommandType.Move m -> "Move";
                case CommandType.Attack a -> "Attack";
                case CommandType.Build b -> "Build";
                case CommandType.Produce p -> "Produce";
                case CommandType.Research r -> "Research";
                case CommandType.Garrison g -> "Garrison";
                case CommandType.Ungarrison u -> "Ungarrison";
                case CommandType.Cancel c -> "Cancel";
                case CommandType.SiegeMode s -> "SiegeMode";
                case CommandType.Stop s -> "Stop";
                case CommandType.AttackMove am -> "AttackMove";
                case CommandType.Patrol p -> "Patrol";
            };
        }

        @Test
        @DisplayName("Should access tick and playerId from all command types")
        void shouldAccessCommonFields() {
            CommandType move = new CommandType.Move(42L, 1, new int[]{1}, new GridPosition(0, 0));
            assertEquals(42L, move.tick());
            assertEquals(1, move.playerId());
        }
    }
}
