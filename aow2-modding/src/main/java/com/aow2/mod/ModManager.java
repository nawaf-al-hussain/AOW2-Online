package com.aow2.mod;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mod lifecycle: loading, enabling, disabling, and hot-reload.
 * Coordinates with {@link GameDataRegistry} to apply mod data overrides.
 * <p>
 * REF: project_structure.md - mod system architecture
 * REF: phases.md Phase 10 - Modding System
 */
public final class ModManager {

    private static final Logger LOG = LoggerFactory.getLogger(ModManager.class);
    private static final String MANIFEST_FILE = "mod.json";
    private static final String DATA_DIR = "data";

    private final ObjectMapper objectMapper;
    private final GameDataRegistry registry;

    /** All discovered mods keyed by mod ID. */
    private final Map<String, ModManifest> discoveredMods;

    /** Set of currently enabled mod IDs. */
    private final Map<String, Boolean> enabledMods;

    /** Mod directories keyed by mod ID for file access. */
    private final Map<String, Path> modDirectories;

    /** Cached data overrides per mod. */
    private final Map<String, List<DataOverride>> modOverrides;

    /**
     * Constructs a ModManager with the given data registry.
     *
     * @param registry the game data registry to apply overrides to
     */
    public ModManager(GameDataRegistry registry) {
        this.objectMapper = new ObjectMapper();
        this.registry = registry;
        this.discoveredMods = new ConcurrentHashMap<>();
        this.enabledMods = new ConcurrentHashMap<>();
        this.modDirectories = new ConcurrentHashMap<>();
        this.modOverrides = new ConcurrentHashMap<>();
    }

    /**
     * Discovers and loads all mods from the given directory.
     * Each subdirectory containing a mod.json manifest is recognized as a mod.
     *
     * @param modsDirectory path to the mods root directory
     * @return list of discovered mod manifests
     */
    public List<ModManifest> discoverMods(Path modsDirectory) {
        discoveredMods.clear();
        modDirectories.clear();
        modOverrides.clear();

        if (!Files.isDirectory(modsDirectory)) {
            LOG.warn("Mods directory does not exist: {}", modsDirectory);
            return Collections.emptyList();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDirectory)) {
            for (Path modDir : stream) {
                if (!Files.isDirectory(modDir)) continue;

                Path manifestPath = modDir.resolve(MANIFEST_FILE);
                if (!Files.exists(manifestPath)) {
                    LOG.warn("Skipping mod directory without manifest: {}", modDir);
                    continue;
                }

                try {
                    ModManifest manifest = objectMapper.readValue(manifestPath.toFile(), ModManifest.class);
                    discoveredMods.put(manifest.id(), manifest);
                    modDirectories.put(manifest.id(), modDir);

                    // Pre-load data overrides
                    loadModOverrides(manifest.id());

                    LOG.info("Discovered mod: {} v{} by {} [{} overrides]",
                        manifest.name(), manifest.version(), manifest.author(),
                        modOverrides.getOrDefault(manifest.id(), List.of()).size());
                } catch (IOException e) {
                    LOG.error("Failed to load mod manifest: {}", manifestPath, e);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to scan mods directory: {}", modsDirectory, e);
        }

        LOG.info("Discovered {} mods", discoveredMods.size());
        return getDiscoveredMods();
    }

    /**
     * Enables a mod for the current game session.
     * Applies all data overrides from the mod to the game data registry.
     *
     * @param modId the mod ID to enable
     * @return true if the mod was successfully enabled
     */
    public boolean enableMod(String modId) {
        if (!discoveredMods.containsKey(modId)) {
            LOG.warn("Cannot enable unknown mod: {}", modId);
            return false;
        }

        if (enabledMods.containsKey(modId)) {
            LOG.debug("Mod {} is already enabled", modId);
            return true;
        }

        // Check dependencies
        ModManifest manifest = discoveredMods.get(modId);
        for (String dep : manifest.dependencies()) {
            if (!enabledMods.containsKey(dep)) {
                LOG.error("Cannot enable mod {}: dependency {} not enabled", modId, dep);
                return false;
            }
        }

        // Apply data overrides
        List<DataOverride> overrides = modOverrides.getOrDefault(modId, List.of());
        for (DataOverride override : overrides) {
            registry.applyOverride(override);
        }

        enabledMods.put(modId, true);
        LOG.info("Enabled mod: {} ({} overrides applied)", manifest.name(), overrides.size());
        return true;
    }

    /**
     * Disables a mod. Resets all overrides and re-applies remaining enabled mods.
     *
     * @param modId the mod ID to disable
     * @return true if the mod was successfully disabled
     */
    public boolean disableMod(String modId) {
        if (!enabledMods.containsKey(modId)) {
            LOG.debug("Mod {} is not enabled", modId);
            return false;
        }

        // Check if other enabled mods depend on this one
        for (var entry : enabledMods.entrySet()) {
            if (!entry.getKey().equals(modId)) {
                ModManifest otherManifest = discoveredMods.get(entry.getKey());
                if (otherManifest != null && otherManifest.dependencies().contains(modId)) {
                    LOG.error("Cannot disable mod {}: mod {} depends on it", modId, entry.getKey());
                    return false;
                }
            }
        }

        enabledMods.remove(modId);

        // Reset all overrides and re-apply remaining enabled mods
        registry.resetAllOverrides();
        for (String enabledId : enabledMods.keySet()) {
            List<DataOverride> overrides = modOverrides.getOrDefault(enabledId, List.of());
            for (DataOverride override : overrides) {
                registry.applyOverride(override);
            }
        }

        LOG.info("Disabled mod: {}", modId);
        return true;
    }

    /**
     * Returns all active (enabled) mods.
     *
     * @return list of enabled mod manifests
     */
    public List<ModManifest> getActiveMods() {
        List<ModManifest> active = new ArrayList<>();
        for (String modId : enabledMods.keySet()) {
            ModManifest manifest = discoveredMods.get(modId);
            if (manifest != null) {
                active.add(manifest);
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * Returns all discovered mods.
     *
     * @return list of all discovered mod manifests
     */
    public List<ModManifest> getDiscoveredMods() {
        return List.copyOf(discoveredMods.values());
    }

    /**
     * Returns whether a specific mod is enabled.
     *
     * @param modId the mod ID
     * @return true if the mod is enabled
     */
    public boolean isModEnabled(String modId) {
        return enabledMods.containsKey(modId);
    }

    /**
     * Applies all active mod data overrides to the game data registry.
     * Useful after loading a save game to re-apply mod state.
     */
    public void applyOverrides(GameDataRegistry targetRegistry) {
        targetRegistry.resetAllOverrides();
        for (String modId : enabledMods.keySet()) {
            List<DataOverride> overrides = modOverrides.getOrDefault(modId, List.of());
            for (DataOverride override : overrides) {
                targetRegistry.applyOverride(override);
            }
        }
        LOG.info("Applied overrides from {} active mods", enabledMods.size());
    }

    /**
     * Hot-reloads a mod (for development).
     * Re-reads the mod manifest and data overrides from disk.
     *
     * @param modId the mod ID to hot-reload
     */
    public void hotReload(String modId) {
        if (!discoveredMods.containsKey(modId)) {
            LOG.warn("Cannot hot-reload unknown mod: {}", modId);
            return;
        }

        Path modDir = modDirectories.get(modId);
        Path manifestPath = modDir.resolve(MANIFEST_FILE);

        try {
            // Reload manifest
            ModManifest manifest = objectMapper.readValue(manifestPath.toFile(), ModManifest.class);
            discoveredMods.put(modId, manifest);

            // Reload data overrides
            loadModOverrides(modId);

            // If the mod is enabled, re-apply its overrides
            if (enabledMods.containsKey(modId)) {
                // Reset and re-apply all
                registry.resetAllOverrides();
                for (String enabledId : enabledMods.keySet()) {
                    List<DataOverride> overrides = modOverrides.getOrDefault(enabledId, List.of());
                    for (DataOverride override : overrides) {
                        registry.applyOverride(override);
                    }
                }
            }

            LOG.info("Hot-reloaded mod: {} v{}", manifest.name(), manifest.version());
        } catch (IOException e) {
            LOG.error("Failed to hot-reload mod: {}", modId, e);
        }
    }

    /**
     * Returns data overrides for a specific mod.
     *
     * @param modId the mod ID
     * @return list of data overrides, or empty list
     */
    public List<DataOverride> getModOverrides(String modId) {
        return List.copyOf(modOverrides.getOrDefault(modId, List.of()));
    }

    /**
     * Loads data overrides for a mod from its data directory.
     * Reads JSON files listed in the manifest's dataOverrides field.
     */
    private void loadModOverrides(String modId) {
        Path modDir = modDirectories.get(modId);
        ModManifest manifest = discoveredMods.get(modId);
        if (modDir == null || manifest == null) return;

        List<DataOverride> overrides = new ArrayList<>();

        for (String dataFile : manifest.dataOverrides()) {
            Path dataPath = modDir.resolve(dataFile);
            if (!Files.exists(dataPath)) {
                LOG.warn("Mod {} data file not found: {}", modId, dataFile);
                continue;
            }

            try {
                List<DataOverride> fileOverrides = objectMapper.readValue(
                    dataPath.toFile(),
                    new TypeReference<List<DataOverride>>() {}
                );
                overrides.addAll(fileOverrides);
            } catch (IOException e) {
                LOG.error("Failed to load mod data overrides: {}", dataPath, e);
            }
        }

        modOverrides.put(modId, overrides);
    }
}
