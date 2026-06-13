package com.aow2.mod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModManager: mod loading, enabling, overrides, hot-reload.
 * Naming: shouldXxxWhenYyy, Given-When-Then.
 */
class ModManagerTest {

    private GameDataRegistry registry;
    private ModManager modManager;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempModsDir;

    @BeforeEach
    void setUp() {
        registry = new GameDataRegistry();
        modManager = new ModManager(registry);
        objectMapper = new ObjectMapper();
    }

    private void createMod(String modId, String name, List<String> dataOverrides,
                           List<String> dependencies) throws IOException {
        Path modDir = tempModsDir.resolve(modId);
        Files.createDirectories(modDir);

        ModManifest manifest = new ModManifest(
            modId, name, "1.0.0", "Test mod description", "Test author",
            "0.2.0", dependencies, List.of(), dataOverrides
        );

        objectMapper.writeValue(modDir.resolve("mod.json").toFile(), manifest);
    }

    private void createDataOverrides(String modId) throws IOException {
        Path dataDir = tempModsDir.resolve(modId).resolve("data");
        Files.createDirectories(dataDir);

        List<DataOverride> overrides = List.of(
            new DataOverride("unit", "CONFED_INFANTRY", "hp", 120),
            new DataOverride("building", "CONFED_COMMAND_CENTRE", "hp", 800)
        );

        objectMapper.writeValue(dataDir.resolve("unit_overrides.json").toFile(), overrides);
    }

    @Nested
    @DisplayName("Mod Discovery")
    class ModDiscovery {

        @Test
        @DisplayName("shouldDiscoverModsFromDirectory")
        void shouldDiscoverModsFromDirectory() throws IOException {
            // Given: a directory with a valid mod
            createMod("test_mod", "Test Mod", List.of("data/unit_overrides.json"), List.of());
            createDataOverrides("test_mod");

            // When: discovering mods
            List<ModManifest> mods = modManager.discoverMods(tempModsDir);

            // Then: the mod should be discovered
            assertEquals(1, mods.size());
            assertEquals("test_mod", mods.getFirst().id());
            assertEquals("Test Mod", mods.getFirst().name());
        }

        @Test
        @DisplayName("shouldReturnEmptyListForNonexistentDirectory")
        void shouldReturnEmptyListForNonexistentDirectory() {
            // Given: a nonexistent directory
            Path nonexistent = Path.of("/nonexistent/mods");

            // When: discovering mods
            List<ModManifest> mods = modManager.discoverMods(nonexistent);

            // Then: should return empty list
            assertTrue(mods.isEmpty());
        }

        @Test
        @DisplayName("shouldSkipDirectoriesWithoutManifest")
        void shouldSkipDirectoriesWithoutManifest() throws IOException {
            // Given: a directory without mod.json
            Files.createDirectories(tempModsDir.resolve("no_manifest"));

            // When: discovering mods
            List<ModManifest> mods = modManager.discoverMods(tempModsDir);

            // Then: no mods should be found
            assertTrue(mods.isEmpty());
        }

        @Test
        @DisplayName("shouldDiscoverMultipleMods")
        void shouldDiscoverMultipleMods() throws IOException {
            // Given: multiple mod directories
            createMod("mod_a", "Mod A", List.of(), List.of());
            createMod("mod_b", "Mod B", List.of(), List.of());

            // When: discovering mods
            List<ModManifest> mods = modManager.discoverMods(tempModsDir);

            // Then: both mods should be found
            assertEquals(2, mods.size());
        }
    }

    @Nested
    @DisplayName("Mod Enabling")
    class ModEnabling {

        @Test
        @DisplayName("shouldEnableModSuccessfully")
        void shouldEnableModSuccessfully() throws IOException {
            // Given: a discovered mod
            createMod("test_mod", "Test Mod", List.of(), List.of());
            modManager.discoverMods(tempModsDir);

            // When: enabling the mod
            boolean result = modManager.enableMod("test_mod");

            // Then: should succeed
            assertTrue(result);
            assertTrue(modManager.isModEnabled("test_mod"));
            assertEquals(1, modManager.getActiveMods().size());
        }

        @Test
        @DisplayName("shouldFailToEnableUnknownMod")
        void shouldFailToEnableUnknownMod() {
            // Given: no mods discovered
            // When: trying to enable unknown mod
            boolean result = modManager.enableMod("unknown_mod");

            // Then: should fail
            assertFalse(result);
        }

        @Test
        @DisplayName("shouldFailToEnableModWithMissingDependency")
        void shouldFailToEnableModWithMissingDependency() throws IOException {
            // Given: a mod that depends on another mod
            createMod("dependent_mod", "Dependent Mod", List.of(), List.of("required_mod"));
            modManager.discoverMods(tempModsDir);

            // When: enabling the dependent mod without its dependency
            boolean result = modManager.enableMod("dependent_mod");

            // Then: should fail
            assertFalse(result);
        }

        @Test
        @DisplayName("shouldEnableModAfterDependencyIsEnabled")
        void shouldEnableModAfterDependencyIsEnabled() throws IOException {
            // Given: two mods, one depending on the other
            createMod("required_mod", "Required Mod", List.of(), List.of());
            createMod("dependent_mod", "Dependent Mod", List.of(), List.of("required_mod"));
            modManager.discoverMods(tempModsDir);

            // When: enabling the required mod first, then the dependent
            assertTrue(modManager.enableMod("required_mod"));
            boolean result = modManager.enableMod("dependent_mod");

            // Then: both should be enabled
            assertTrue(result);
            assertEquals(2, modManager.getActiveMods().size());
        }

        @Test
        @DisplayName("shouldApplyDataOverridesWhenEnabling")
        void shouldApplyDataOverridesWhenEnabling() throws IOException {
            // Given: a mod with data overrides
            registerDefaultStats();
            createMod("stat_mod", "Stat Mod", List.of("data/unit_overrides.json"), List.of());
            createDataOverrides("stat_mod");
            modManager.discoverMods(tempModsDir);

            // When: enabling the mod
            modManager.enableMod("stat_mod");

            // Then: overrides should be applied to the registry (2 overrides from createDataOverrides)
            assertEquals(2, registry.overrideCount());
        }
    }

    @Nested
    @DisplayName("Mod Disabling")
    class ModDisabling {

        @Test
        @DisplayName("shouldDisableModSuccessfully")
        void shouldDisableModSuccessfully() throws IOException {
            // Given: an enabled mod
            createMod("test_mod", "Test Mod", List.of(), List.of());
            modManager.discoverMods(tempModsDir);
            modManager.enableMod("test_mod");

            // When: disabling the mod
            boolean result = modManager.disableMod("test_mod");

            // Then: should succeed
            assertTrue(result);
            assertFalse(modManager.isModEnabled("test_mod"));
        }

        @Test
        @DisplayName("shouldFailToDisableModWhenRequiredByAnother")
        void shouldFailToDisableModWhenRequiredByAnother() throws IOException {
            // Given: two enabled mods, one depending on the other
            createMod("required_mod", "Required Mod", List.of(), List.of());
            createMod("dependent_mod", "Dependent Mod", List.of(), List.of("required_mod"));
            modManager.discoverMods(tempModsDir);
            modManager.enableMod("required_mod");
            modManager.enableMod("dependent_mod");

            // When: trying to disable the required mod
            boolean result = modManager.disableMod("required_mod");

            // Then: should fail
            assertFalse(result);
            assertTrue(modManager.isModEnabled("required_mod"));
        }

        @Test
        @DisplayName("shouldReturnFalseWhenDisablingNotEnabledMod")
        void shouldReturnFalseWhenDisablingNotEnabledMod() throws IOException {
            // Given: a discovered but not enabled mod
            createMod("test_mod", "Test Mod", List.of(), List.of());
            modManager.discoverMods(tempModsDir);

            // When: trying to disable it
            boolean result = modManager.disableMod("test_mod");

            // Then: should return false
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Hot Reload")
    class HotReload {

        @Test
        @DisplayName("shouldHotReloadModFromDisk")
        void shouldHotReloadModFromDisk() throws IOException {
            // Given: an enabled mod
            createMod("test_mod", "Test Mod", List.of(), List.of());
            modManager.discoverMods(tempModsDir);
            modManager.enableMod("test_mod");

            // When: hot-reloading the mod
            assertDoesNotThrow(() -> modManager.hotReload("test_mod"));

            // Then: mod should still be enabled
            assertTrue(modManager.isModEnabled("test_mod"));
        }

        @Test
        @DisplayName("shouldNotThrowForUnknownModHotReload")
        void shouldNotThrowForUnknownModHotReload() {
            assertDoesNotThrow(() -> modManager.hotReload("unknown_mod"));
        }
    }

    private void registerDefaultStats() {
        var unitType = com.aow2.common.model.UnitType.CONFED_INFANTRY;
        var buildingType = com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE;

        registry.registerUnitStats(unitType, new com.aow2.common.model.UnitStats(
            unitType, "Test infantry", 80, 10, 100, 4, 2, 0, 5, 4,
            60, 100, 5, 2, 0, 0, 0
        ));

        registry.registerBuildingStats(buildingType, new com.aow2.common.model.BuildingStats(
            buildingType, 500, 0, 0, 5, 0, 10, 600, 6, 5, 0, 10, 1, 0, 0, 0,
            java.util.List.of()
        ));
    }
}
