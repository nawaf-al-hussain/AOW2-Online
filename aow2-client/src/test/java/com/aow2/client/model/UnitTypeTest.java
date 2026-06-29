package com.aow2.client.model;

import com.aow2.common.model.Faction;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UnitType enum: faction, category, siege capability, type ID uniqueness.
 */
class UnitTypeTest {

    @Nested
    @DisplayName("Type ID Uniqueness")
    class TypeIdUniqueness {

        @Test
        @DisplayName("all type IDs are unique within each faction")
        void allTypeIdsUniquePerFaction() {
            Set<Integer> confedIds = java.util.Arrays.stream(UnitType.values())
                .filter(u -> u.faction() == Faction.CONFEDERATION)
                .map(UnitType::typeId)
                .collect(Collectors.toSet());

            Set<Integer> rebelIds = java.util.Arrays.stream(UnitType.values())
                .filter(u -> u.faction() == Faction.RESISTANCE)
                .map(UnitType::typeId)
                .collect(Collectors.toSet());

            // All IDs should be positive
            for (UnitType type : UnitType.values()) {
                assertTrue(type.typeId() > 0,
                    () -> type.name() + " has non-positive typeId: " + type.typeId());
            }
        }
    }

    @Nested
    @DisplayName("Faction Assignment")
    class FactionAssignment {

        @ParameterizedTest(name = "{0} is CONFEDERATION unit")
        @EnumSource(value = UnitType.class, mode = EnumSource.Mode.MATCH_ANY,
            names = {"CONFED_.*"})
        @DisplayName("CONFED_ prefix units belong to CONFEDERATION")
        void confedUnitsBelongToConfederation(UnitType type) {
            assertEquals(Faction.CONFEDERATION, type.faction(),
                () -> type.name() + " should be CONFEDERATION");
        }

        @ParameterizedTest(name = "{0} is RESISTANCE unit")
        @EnumSource(value = UnitType.class, mode = EnumSource.Mode.MATCH_ANY,
            names = {"REBEL_.*"})
        @DisplayName("REBEL_ prefix units belong to RESISTANCE")
        void rebelUnitsBelongToResistance(UnitType type) {
            assertEquals(Faction.RESISTANCE, type.faction(),
                () -> type.name() + " should be RESISTANCE");
        }
    }

    @Nested
    @DisplayName("Category Checks")
    class CategoryChecks {

        @Test
        @DisplayName("infantry units are correctly identified")
        void infantryIdentification() {
            assertTrue(UnitType.CONFED_INFANTRY.isInfantry());
            assertTrue(UnitType.REBEL_INFANTRY.isInfantry());
            assertTrue(UnitType.CONFED_GRENADIER.isInfantry());
            assertTrue(UnitType.REBEL_GRENADIER.isInfantry());
            assertTrue(UnitType.REBEL_SNIPER.isInfantry());
        }

        @Test
        @DisplayName("vehicle units are correctly identified")
        void vehicleIdentification() {
            assertTrue(UnitType.CONFED_FORTRESS.isVehicle());
            assertTrue(UnitType.CONFED_HAMMER.isVehicle());
            assertTrue(UnitType.REBEL_COYOTE.isVehicle());
            assertTrue(UnitType.REBEL_RHINO.isVehicle());
        }

        @Test
        @DisplayName("mine units are correctly identified")
        void mineIdentification() {
            assertTrue(UnitType.CONFED_MINE_SCORPIO.isMine());
            assertTrue(UnitType.CONFED_MINE_FROG.isMine());
            assertTrue(UnitType.CONFED_MINE_LIZARD.isMine());
        }

        @Test
        @DisplayName("infantry units are not vehicles")
        void infantryAreNotVehicles() {
            assertFalse(UnitType.CONFED_INFANTRY.isVehicle());
            assertFalse(UnitType.REBEL_SNIPER.isVehicle());
        }

        @Test
        @DisplayName("mines are not infantry")
        void minesAreNotInfantry() {
            for (UnitType type : UnitType.values()) {
                if (type.isMine()) {
                    assertFalse(type.isInfantry(),
                        () -> type.name() + " is a mine but reports as infantry");
                }
            }
        }
    }

    @Nested
    @DisplayName("Machinery Check")
    class MachineryCheck {

        @Test
        @DisplayName("vehicles are machinery")
        void vehiclesAreMachinery() {
            assertTrue(UnitType.CONFED_FORTRESS.isMachinery());
            assertTrue(UnitType.CONFED_HAMMER.isMachinery());
        }

        @Test
        @DisplayName("infantry are not machinery")
        void infantryAreNotMachinery() {
            assertFalse(UnitType.CONFED_INFANTRY.isMachinery());
            assertFalse(UnitType.REBEL_SNIPER.isMachinery());
        }

        @Test
        @DisplayName("special machinery is machinery")
        void specialMachineryIsMachinery() {
            assertTrue(UnitType.CONFED_FLAME_ASSAULT.isMachinery());
        }
    }

    @Nested
    @DisplayName("Siege Capability")
    class SiegeCapability {

        @Test
        @DisplayName("Fortress is siege capable")
        void fortressIsSiegeCapable() {
            assertTrue(UnitType.CONFED_FORTRESS.isSiegeCapable());
        }

        @Test
        @DisplayName("Hammer is siege capable")
        void hammerIsSiegeCapable() {
            assertTrue(UnitType.CONFED_HAMMER.isSiegeCapable());
        }

        @Test
        @DisplayName("Torrent is siege capable")
        void torrentIsSiegeCapable() {
            assertTrue(UnitType.CONFED_TORRENT.isSiegeCapable());
        }

        @Test
        @DisplayName("Rhino is siege capable")
        void rhinoIsSiegeCapable() {
            assertTrue(UnitType.REBEL_RHINO.isSiegeCapable());
        }

        @Test
        @DisplayName("Sniper is siege capable")
        void sniperIsSiegeCapable() {
            assertTrue(UnitType.REBEL_SNIPER.isSiegeCapable());
        }

        @Test
        @DisplayName("infantry are not siege capable")
        void infantryNotSiegeCapable() {
            assertFalse(UnitType.CONFED_INFANTRY.isSiegeCapable());
            assertFalse(UnitType.REBEL_INFANTRY.isSiegeCapable());
        }

        @Test
        @DisplayName("mines are not siege capable")
        void minesNotSiegeCapable() {
            assertFalse(UnitType.CONFED_MINE_SCORPIO.isSiegeCapable());
            assertFalse(UnitType.CONFED_MINE_FROG.isSiegeCapable());
        }
    }

    @Nested
    @DisplayName("Large Unit Check")
    class LargeUnitCheck {

        @Test
        @DisplayName("only Fortress is a large unit")
        void onlyFortressIsLargeUnit() {
            assertTrue(UnitType.CONFED_FORTRESS.isLargeUnit());
        }

        @Test
        @DisplayName("all other units are not large")
        void allOtherUnitsNotLarge() {
            for (UnitType type : UnitType.values()) {
                if (type != UnitType.CONFED_FORTRESS) {
                    assertFalse(type.isLargeUnit(),
                        () -> type.name() + " should not be large");
                }
            }
        }
    }

    @Nested
    @DisplayName("Display Name")
    class DisplayNameTest {

        @Test
        @DisplayName("all units have non-null display names")
        void allUnitsHaveDisplayNames() {
            for (UnitType type : UnitType.values()) {
                assertNotNull(type.displayName(),
                    () -> type.name() + " should have a display name");
                assertFalse(type.displayName().isEmpty(),
                    () -> type.name() + " display name should not be empty");
            }
        }
    }

    @Nested
    @DisplayName("Enum Completeness")
    class EnumCompleteness {

        @Test
        @DisplayName("there are exactly 19 unit types")
        void exactlySeventeenUnitTypes() {
            assertEquals(19, UnitType.values().length);
        }

        @Test
        @DisplayName("both factions have units")
        void bothFactionsHaveUnits() {
            long confedCount = java.util.Arrays.stream(UnitType.values())
                .filter(u -> u.faction() == Faction.CONFEDERATION).count();
            long rebelCount = java.util.Arrays.stream(UnitType.values())
                .filter(u -> u.faction() == Faction.RESISTANCE).count();

            assertTrue(confedCount > 0, "Confederation should have units");
            assertTrue(rebelCount > 0, "Resistance should have units");
        }
    }
}
