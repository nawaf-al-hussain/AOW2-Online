package com.aow2.mod;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GameDataRegistry: stat registration, overrides, reset.
 * Naming: shouldXxxWhenYyy, Given-When-Then.
 */
class GameDataRegistryTest {

    private GameDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GameDataRegistry();
    }

    private UnitStats createDefaultUnitStats() {
        return new UnitStats(
            UnitType.CONFED_INFANTRY, "Standard infantry", 80, 10,
            100, 4, 2, 0, 5, 4, 60, 100, 5, 2, 0, 0, 0, 0
        );
    }

    private BuildingStats createDefaultBuildingStats() {
        return new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE, 500, 0, 0, 5,
            0, 10, 600, 6, 5, 0, 10, 1, 0, 0, 0, java.util.List.of()
        );
    }

    @Nested
    @DisplayName("Stat Registration")
    class StatRegistration {

        @Test
        @DisplayName("shouldRegisterUnitStats")
        void shouldRegisterUnitStats() {
            // Given: unit stats
            UnitStats stats = createDefaultUnitStats();

            // When: registering
            registry.registerUnitStats(UnitType.CONFED_INFANTRY, stats);

            // Then: stats should be retrievable
            assertEquals(1, registry.unitTypeCount());
            assertNotNull(registry.getUnitStats(UnitType.CONFED_INFANTRY));
            assertEquals(80, registry.getUnitStats(UnitType.CONFED_INFANTRY).hp());
        }

        @Test
        @DisplayName("shouldRegisterBuildingStats")
        void shouldRegisterBuildingStats() {
            // Given: building stats
            BuildingStats stats = createDefaultBuildingStats();

            // When: registering
            registry.registerBuildingStats(BuildingType.CONFED_COMMAND_CENTRE, stats);

            // Then: stats should be retrievable
            assertEquals(1, registry.buildingTypeCount());
            assertNotNull(registry.getBuildingStats(BuildingType.CONFED_COMMAND_CENTRE));
            assertEquals(500, registry.getBuildingStats(BuildingType.CONFED_COMMAND_CENTRE).hp());
        }

        @Test
        @DisplayName("shouldReturnNullForUnregisteredType")
        void shouldReturnNullForUnregisteredType() {
            assertNull(registry.getUnitStats(UnitType.CONFED_INFANTRY));
            assertNull(registry.getBuildingStats(BuildingType.CONFED_COMMAND_CENTRE));
        }
    }

    @Nested
    @DisplayName("Data Overrides")
    class DataOverrides {

        @BeforeEach
        void registerStats() {
            registry.registerUnitStats(UnitType.CONFED_INFANTRY, createDefaultUnitStats());
            registry.registerBuildingStats(BuildingType.CONFED_COMMAND_CENTRE, createDefaultBuildingStats());
        }

        @Test
        @DisplayName("shouldApplyUnitHpOverride")
        void shouldApplyUnitHpOverride() {
            // Given: an override for unit HP
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "hp", 120);

            // When: applying the override
            registry.applyOverride(override);

            // Then: modified stats should reflect the override
            assertEquals(120, registry.getUnitStats(UnitType.CONFED_INFANTRY).hp());
            // Base stats should remain unchanged
            assertEquals(80, registry.getBaseUnitStats(UnitType.CONFED_INFANTRY).hp());
        }

        @Test
        @DisplayName("shouldApplyUnitDamageOverride")
        void shouldApplyUnitDamageOverride() {
            // Given: an override for unit damage
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "damage", 25);

            // When: applying
            registry.applyOverride(override);

            // Then: damage should be modified
            assertEquals(25, registry.getUnitStats(UnitType.CONFED_INFANTRY).damage());
        }

        @Test
        @DisplayName("shouldApplyUnitSpeedOverride")
        void shouldApplyUnitSpeedOverride() {
            // Given: an override for speed
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "speed", 8);

            // When: applying
            registry.applyOverride(override);

            // Then: speed should be modified
            assertEquals(8, registry.getUnitStats(UnitType.CONFED_INFANTRY).speed());
        }

        @Test
        @DisplayName("shouldApplyBuildingHpOverride")
        void shouldApplyBuildingHpOverride() {
            // Given: an override for building HP
            DataOverride override = new DataOverride("building", "CONFED_COMMAND_CENTRE", "hp", 800);

            // When: applying
            registry.applyOverride(override);

            // Then: HP should be modified
            assertEquals(800, registry.getBuildingStats(BuildingType.CONFED_COMMAND_CENTRE).hp());
        }

        @Test
        @DisplayName("shouldApplyMultipleOverridesToSameUnit")
        void shouldApplyMultipleOverridesToSameUnit() {
            // Given: multiple overrides for the same unit
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "hp", 120));
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "damage", 25));
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "speed", 8));

            // Then: all overrides should be applied
            UnitStats modified = registry.getUnitStats(UnitType.CONFED_INFANTRY);
            assertEquals(120, modified.hp());
            assertEquals(25, modified.damage());
            assertEquals(8, modified.speed());
            // Other fields should remain unchanged
            assertEquals(2, modified.armor());
        }

        @Test
        @DisplayName("shouldTrackOverrideCount")
        void shouldTrackOverrideCount() {
            // Given: some overrides
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "hp", 120));
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "damage", 25));

            // Then: override count should match
            assertEquals(2, registry.overrideCount());
        }

        @Test
        @DisplayName("shouldReplaceOverrideForSameField")
        void shouldReplaceOverrideForSameField() {
            // Given: two overrides for the same field
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "hp", 120));
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "hp", 150));

            // Then: the second override should take effect
            assertEquals(150, registry.getUnitStats(UnitType.CONFED_INFANTRY).hp());
        }
    }

    @Nested
    @DisplayName("Override Reset")
    class OverrideReset {

        @BeforeEach
        void registerAndOverride() {
            registry.registerUnitStats(UnitType.CONFED_INFANTRY, createDefaultUnitStats());
            registry.applyOverride(new DataOverride("unit", "CONFED_INFANTRY", "hp", 120));
        }

        @Test
        @DisplayName("shouldResetAllOverridesToBaseStats")
        void shouldResetAllOverridesToBaseStats() {
            // When: resetting overrides
            registry.resetAllOverrides();

            // Then: stats should be back to base
            assertEquals(80, registry.getUnitStats(UnitType.CONFED_INFANTRY).hp());
            assertEquals(0, registry.overrideCount());
        }

        @Test
        @DisplayName("shouldPreserveBaseStatsAfterReset")
        void shouldPreserveBaseStatsAfterReset() {
            // When: resetting overrides
            registry.resetAllOverrides();

            // Then: base stats should be unchanged
            assertEquals(80, registry.getBaseUnitStats(UnitType.CONFED_INFANTRY).hp());
        }
    }

    @Nested
    @DisplayName("DataOverride Record")
    class DataOverrideRecord {

        @Test
        @DisplayName("shouldCreateDataOverrideWithValidValues")
        void shouldCreateDataOverrideWithValidValues() {
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "hp", 120);
            assertEquals("unit", override.targetType());
            assertEquals("CONFED_INFANTRY", override.targetId());
            assertEquals("hp", override.field());
            assertEquals(120, override.value());
        }

        @Test
        @DisplayName("shouldRejectNullTargetType")
        void shouldRejectNullTargetType() {
            assertThrows(IllegalArgumentException.class,
                () -> new DataOverride(null, "CONFED_INFANTRY", "hp", 120));
        }

        @Test
        @DisplayName("shouldRejectBlankTargetId")
        void shouldRejectBlankTargetId() {
            assertThrows(IllegalArgumentException.class,
                () -> new DataOverride("unit", "", "hp", 120));
        }

        @Test
        @DisplayName("shouldRejectNullField")
        void shouldRejectNullField() {
            assertThrows(IllegalArgumentException.class,
                () -> new DataOverride("unit", "CONFED_INFANTRY", null, 120));
        }

        @Test
        @DisplayName("shouldRejectNullValue")
        void shouldRejectNullValue() {
            assertThrows(IllegalArgumentException.class,
                () -> new DataOverride("unit", "CONFED_INFANTRY", "hp", null));
        }

        @Test
        @DisplayName("shouldConvertValueToInt")
        void shouldConvertValueToInt() {
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "hp", 120);
            assertEquals(120, override.intValue());
        }

        @Test
        @DisplayName("shouldConvertStringValueToInt")
        void shouldConvertStringValueToInt() {
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "hp", "150");
            assertEquals(150, override.intValue());
        }

        @Test
        @DisplayName("shouldConvertValueToDouble")
        void shouldConvertValueToDouble() {
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "hp", 120.5);
            assertEquals(120.5, override.doubleValue(), 0.001);
        }

        @Test
        @DisplayName("shouldConvertValueToString")
        void shouldConvertValueToString() {
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "hp", 120);
            assertEquals("120", override.stringValue());
        }

        @Test
        @DisplayName("shouldThrowWhenConvertingInvalidIntValue")
        void shouldThrowWhenConvertingInvalidIntValue() {
            DataOverride override = new DataOverride("unit", "CONFED_INFANTRY", "name", "not_a_number");
            assertThrows(IllegalStateException.class, override::intValue);
        }
    }
}
