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
 */
public class ModLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ModLoader.class);
    private static final String MANIFEST_FILE = "mod.json";

    private final ObjectMapper objectMapper;
    private final Path modsDirectory;
    private final List<ModManifest> loadedMods;

    public ModLoader(Path modsDirectory) {
        this.objectMapper = new ObjectMapper();
        this.modsDirectory = modsDirectory;
        this.loadedMods = new ArrayList<>();
    }

    public List<ModManifest> loadAll() {
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

    public List<ModManifest> loadedMods() {
        return List.copyOf(loadedMods);
    }
}
