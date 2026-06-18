package com.aow2.mod;

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

/**
 * Loads mods from the mods directory.
 * Each mod must contain a mod.json manifest file.
 * <p>
 * FIX (L16): Added game version compatibility check. Mods declare a {@code game_version}
 * field in their manifest. If present and it does not match the current game version,
 * the mod is skipped with a warning. This prevents incompatible mods from loading.
 */
public class ModLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ModLoader.class);
    private static final String MANIFEST_FILE = "mod.json";

    /** Current game version for mod compatibility checking. */
    private static final String CURRENT_GAME_VERSION = "0.2.0";

    private final ObjectMapper objectMapper;
    private final Path modsDirectory;
    private final List<ModManifest> loadedMods;

    public ModLoader(Path modsDirectory) {
        this.objectMapper = new ObjectMapper();
        this.modsDirectory = modsDirectory;
        this.loadedMods = new ArrayList<>();
    }

    public List<ModManifest> loadAll() {
        return loadAll(CURRENT_GAME_VERSION);
    }

    /**
     * Load all mods, checking version compatibility against the given game version.
     *
     * @param gameVersion the current game version string
     * @return list of successfully loaded mod manifests
     */
    public List<ModManifest> loadAll(String gameVersion) {
        loadedMods.clear();

        if (!Files.isDirectory(modsDirectory)) {
            LOG.warn("Mods directory does not exist: {}", modsDirectory);
            return Collections.emptyList();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDirectory)) {
            for (Path modDir : stream) {
                if (!Files.isDirectory(modDir)) continue;

                Path manifestPath = modDir.resolve(MANIFEST_FILE);
                if (!Files.exists(manifestPath)) {
                    LOG.warn("Skipping mod without manifest: {}", modDir);
                    continue;
                }

                try {
                    ModManifest manifest = objectMapper.readValue(manifestPath.toFile(), ModManifest.class);

                    // FIX (L16): Version compatibility check
                    if (manifest.gameVersion() != null && !manifest.gameVersion().isEmpty()
                            && !isVersionCompatible(manifest.gameVersion(), gameVersion)) {
                        LOG.warn("Skipping incompatible mod: {} v{} (requires game {}, current: {})",
                                manifest.name(), manifest.version(), manifest.gameVersion(), gameVersion);
                        continue;
                    }

                    loadedMods.add(manifest);
                    LOG.info("Loaded mod: {} v{} by {}", manifest.name(), manifest.version(), manifest.author());
                } catch (IOException e) {
                    LOG.error("Failed to load mod manifest: {}", manifestPath, e);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to scan mods directory: {}", modsDirectory, e);
        }

        return List.copyOf(loadedMods);
    }

    /**
     * Check if a mod's required game version is compatible with the current version.
     * Uses major.minor prefix matching — a mod built for "0.2.x" works with "0.2.y"
     * but not "0.3.z".
     *
     * @param requiredVersion the mod's required game version
     * @param currentVersion  the current game version
     * @return true if compatible
     */
    private boolean isVersionCompatible(String requiredVersion, String currentVersion) {
        String[] reqParts = requiredVersion.split("\\.");
        String[] curParts = currentVersion.split("\\.");
        // Match major and minor version components
        int matchLength = Math.min(reqParts.length, curParts.length);
        if (matchLength < 2) return true; // Can't determine, allow by default
        return reqParts[0].equals(curParts[0]) && reqParts[1].equals(curParts[1]);
    }

    public List<ModManifest> loadedMods() {
        return List.copyOf(loadedMods);
    }
}
