package com.aow2.mod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs mods from ZIP files and validates mod structure.
 * Handles extraction, manifest validation, version compatibility,
 * and dependency resolution for mod installation.
 * <p>
 * REF: phases.md Phase 10 - Mod Installer
 * REF: project_structure.md - mod system architecture
 */
public final class ModInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(ModInstaller.class);

    /** The manifest file name required in every mod. */
    private static final String MANIFEST_FILE = "mod.json";

    /** Current game version for compatibility checks. */
    private static final String GAME_VERSION = "0.2.0";

    /** JSON object mapper for manifest parsing. */
    private final ObjectMapper objectMapper;

    /** The mods root directory. */
    private final Path modsDirectory;

    /** Reference to ModManager for dependency checks. */
    private final ModManager modManager;

    /**
     * Constructs a ModInstaller with the given mods directory and mod manager.
     *
     * @param modsDirectory the root directory where mods are installed
     * @param modManager    the mod manager for dependency resolution
     */
    public ModInstaller(Path modsDirectory, ModManager modManager) {
        this.modsDirectory = modsDirectory;
        this.modManager = modManager;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Installs a mod from a ZIP file.
     * Validates the ZIP contains a mod.json manifest, checks game version
     * compatibility, resolves dependencies, and extracts to the mods directory.
     *
     * @param zipFile path to the ZIP file to install
     * @return true if the mod was installed successfully
     */
    public boolean installFromZip(Path zipFile) {
        if (!Files.exists(zipFile)) {
            LOG.error("ZIP file not found: {}", zipFile);
            return false;
        }

        if (!zipFile.toString().endsWith(".zip")) {
            LOG.error("File is not a ZIP: {}", zipFile);
            return false;
        }

        LOG.info("Installing mod from ZIP: {}", zipFile);

        // First pass: validate the ZIP contents and read manifest
        ModManifest manifest;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            manifest = readManifestFromZip(zis);
        } catch (IOException e) {
            LOG.error("Failed to read ZIP file: {}", zipFile, e);
            return false;
        }

        if (manifest == null) {
            LOG.error("No valid {} found in ZIP: {}", MANIFEST_FILE, zipFile);
            return false;
        }

        // Validate the manifest
        List<String> errors = validateManifest(manifest);
        if (!errors.isEmpty()) {
            LOG.error("Mod validation failed for {}: {}", manifest.name(), errors);
            return false;
        }

        // Check game version compatibility
        if (!isVersionCompatible(manifest)) {
            LOG.error("Mod {} requires game version {}, current version is {}",
                manifest.name(), manifest.gameVersion(), GAME_VERSION);
            return false;
        }

        // Check dependencies
        List<String> missingDeps = checkDependencies(manifest);
        if (!missingDeps.isEmpty()) {
            LOG.error("Mod {} has missing dependencies: {}", manifest.name(), missingDeps);
            return false;
        }

        // Check if mod already exists
        Path modDir = modsDirectory.resolve(manifest.id());
        if (Files.exists(modDir)) {
            LOG.warn("Mod {} already exists, overwriting", manifest.id());
            try {
                deleteDirectory(modDir);
            } catch (IOException e) {
                LOG.error("Failed to remove existing mod directory: {}", modDir, e);
                return false;
            }
        }

        // Extract ZIP to mods directory
        try {
            extractZip(zipFile, modDir);
            LOG.info("Mod {} v{} by {} installed successfully",
                manifest.name(), manifest.version(), manifest.author());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to extract mod ZIP: {}", zipFile, e);
            // Clean up partial extraction
            try {
                deleteDirectory(modDir);
            } catch (Exception cleanupEx) {
                LOG.warn("Failed to clean up partial extraction: {}", modDir, cleanupEx);
            }
            return false;
        }
    }

    /**
     * Uninstalls a mod by removing its directory from the mods folder.
     *
     * @param modId the mod ID to uninstall
     * @return true if the mod was uninstalled successfully
     */
    public boolean uninstallMod(String modId) {
        Path modDir = modsDirectory.resolve(modId);

        if (!Files.exists(modDir)) {
            LOG.warn("Mod directory not found for uninstall: {}", modId);
            return false;
        }

        // Check if the mod is currently enabled
        if (modManager.isModEnabled(modId)) {
            boolean disabled = modManager.disableMod(modId);
            if (!disabled) {
                LOG.error("Cannot uninstall mod {}: could not disable (other mods may depend on it)", modId);
                return false;
            }
        }

        try {
            deleteDirectory(modDir);
            LOG.info("Mod {} uninstalled successfully", modId);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete mod directory: {}", modDir, e);
            return false;
        }
    }

    /**
     * Validates a mod directory by checking for manifest and required fields.
     *
     * @param modDir the mod directory to validate
     * @return a list of validation error messages (empty if valid)
     */
    public List<String> validateMod(Path modDir) {
        List<String> errors = new ArrayList<>();

        if (!Files.isDirectory(modDir)) {
            errors.add("Not a directory: " + modDir);
            return errors;
        }

        Path manifestPath = modDir.resolve(MANIFEST_FILE);
        if (!Files.exists(manifestPath)) {
            errors.add("Missing " + MANIFEST_FILE + " manifest");
            return errors;
        }

        ModManifest manifest;
        try {
            manifest = objectMapper.readValue(manifestPath.toFile(), ModManifest.class);
        } catch (IOException e) {
            errors.add("Invalid " + MANIFEST_FILE + ": " + e.getMessage());
            return errors;
        }

        errors.addAll(validateManifest(manifest));

        // Verify that referenced data files exist
        for (String dataFile : manifest.dataOverrides()) {
            Path dataPath = modDir.resolve(dataFile);
            if (!Files.exists(dataPath)) {
                errors.add("Referenced data file not found: " + dataFile);
            }
        }

        // Verify that referenced script files exist
        for (String script : manifest.scripts()) {
            Path scriptPath = modDir.resolve(script);
            if (!Files.exists(scriptPath)) {
                errors.add("Referenced script file not found: " + script);
            }
        }

        return errors;
    }

    // --- Internal ---

    /**
     * Reads and parses the mod.json manifest from a ZIP stream.
     */
    private ModManifest readManifestFromZip(ZipInputStream zis) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            // Look for mod.json at root or one level deep
            String name = entry.getName();
            if (name.endsWith(MANIFEST_FILE) && (isRootFile(name) || isOneLevelDeep(name))) {
                byte[] bytes = zis.readAllBytes();
                return objectMapper.readValue(bytes, ModManifest.class);
            }
        }
        return null;
    }

    /**
     * Checks if a zip entry path represents a root-level file.
     */
    private boolean isRootFile(String name) {
        return !name.contains("/") || name.startsWith("/") && name.lastIndexOf("/") == 0;
    }

    /**
     * Checks if a zip entry path is one directory level deep.
     * This handles ZIPs where the mod contents are inside a single root directory.
     */
    private boolean isOneLevelDeep(String name) {
        long slashCount = name.chars().filter(c -> c == '/').count();
        return slashCount == 1 && name.endsWith(MANIFEST_FILE);
    }

    /**
     * Validates a mod manifest for required fields and correctness.
     */
    private List<String> validateManifest(ModManifest manifest) {
        List<String> errors = new ArrayList<>();

        if (manifest.id() == null || manifest.id().isBlank()) {
            errors.add("Missing required field: id");
        } else if (!manifest.id().matches("[a-z0-9_-]+")) {
            errors.add("Mod ID must contain only lowercase letters, numbers, hyphens, and underscores");
        }

        if (manifest.name() == null || manifest.name().isBlank()) {
            errors.add("Missing required field: name");
        }

        if (manifest.version() == null || manifest.version().isBlank()) {
            errors.add("Missing required field: version");
        }

        if (manifest.author() == null || manifest.author().isBlank()) {
            errors.add("Missing required field: author");
        }

        return errors;
    }

    /**
     * Checks if the mod's game version requirement is compatible.
     * Supports semantic versioning patterns:
     * - Exact match: "0.2.0"
     * - Major.minor wildcard: "0.2.*"
     * - Minimum version: ">=0.2.0"
     */
    private boolean isVersionCompatible(ModManifest manifest) {
        String requiredVersion = manifest.gameVersion();
        if (requiredVersion == null || requiredVersion.isBlank()) {
            return true;
        }

        // Strip any prefix
        String version = requiredVersion.trim();
        if (version.startsWith(">=")) {
            version = version.substring(2).trim();
            return compareVersions(GAME_VERSION, version) >= 0;
        }

        if (version.endsWith(".*")) {
            String prefix = version.substring(0, version.length() - 2);
            return GAME_VERSION.startsWith(prefix);
        }

        return GAME_VERSION.equals(version);
    }

    /**
     * Compares two semantic version strings.
     *
     * @return negative if v1 &lt; v2, zero if equal, positive if v1 &gt; v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }

    /**
     * Parses a version part as an integer, returning 0 on failure.
     */
    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Checks that all declared dependencies are available (discovered).
     *
     * @return list of missing dependency IDs
     */
    private List<String> checkDependencies(ModManifest manifest) {
        List<String> missing = new ArrayList<>();
        for (String dep : manifest.dependencies()) {
            boolean found = modManager.getDiscoveredMods().stream()
                .anyMatch(m -> m.id().equals(dep));
            if (!found) {
                // Check if it exists on disk even if not yet discovered
                Path depDir = modsDirectory.resolve(dep);
                if (!Files.isDirectory(depDir)) {
                    missing.add(dep);
                }
            }
        }
        return missing;
    }

    /**
     * Extracts a ZIP file to the target directory.
     * Handles the case where ZIP entries are inside a single root directory
     * by stripping that prefix.
     */
    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            // Detect if all entries share a common prefix directory
            String prefix = detectCommonPrefix(zis);

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Strip common prefix if detected
                if (prefix != null && entryName.startsWith(prefix)) {
                    entryName = entryName.substring(prefix.length());
                }

                if (entryName.isEmpty() || entryName.endsWith("/")) {
                    continue;
                }

                Path destPath = targetDir.resolve(entryName).normalize();

                // Security check: prevent zip slip
                if (!destPath.startsWith(targetDir)) {
                    LOG.warn("Skipping suspicious ZIP entry: {}", entryName);
                    continue;
                }

                Files.createDirectories(destPath.getParent());
                Files.copy(zis, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Detects if all ZIP entries share a common directory prefix.
     * This handles ZIPs where the contents are inside a single root folder.
     *
     * @return the common prefix string (including trailing slash), or null
     */
    private String detectCommonPrefix(ZipInputStream zis) throws IOException {
        String commonPrefix = null;
        boolean allInDir = true;

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            int slashIdx = name.indexOf('/');
            if (slashIdx < 0) {
                allInDir = false;
                break;
            }
            String prefix = name.substring(0, slashIdx + 1);
            if (commonPrefix == null) {
                commonPrefix = prefix;
            } else if (!commonPrefix.equals(prefix)) {
                allInDir = false;
                break;
            }
        }

        return allInDir ? commonPrefix : null;
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    deleteDirectory(child);
                } else {
                    Files.delete(child);
                }
            }
        }
        Files.delete(dir);
    }
}
