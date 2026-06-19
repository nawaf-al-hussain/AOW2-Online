package com.aow2.mod;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Mod manifest loaded from mod.json in each mod directory.
 * <p>
 * FIX (P3-M3): Added compact constructor validation for required fields.
 * Previously null/blank ID, name, version, or author would propagate silently
 * into ModLoader and ModManager, causing NullPointerExceptions downstream.
 */
public record ModManifest(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("version") String version,
    @JsonProperty("description") String description,
    @JsonProperty("author") String author,
    @JsonProperty("game_version") String gameVersion,
    @JsonProperty("dependencies") List<String> dependencies,
    @JsonProperty("scripts") List<String> scripts,
    @JsonProperty("data_overrides") List<String> dataOverrides
) {
    public ModManifest {
        // FIX (P3-M3): Validate required fields — fail fast with descriptive errors
        Objects.requireNonNull(id, "Mod manifest 'id' must not be null");
        Objects.requireNonNull(name, "Mod manifest 'name' must not be null");
        Objects.requireNonNull(version, "Mod manifest 'version' must not be null");
        Objects.requireNonNull(author, "Mod manifest 'author' must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Mod manifest 'id' must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Mod manifest 'name' must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("Mod manifest 'version' must not be blank");
        }
        if (author.isBlank()) {
            throw new IllegalArgumentException("Mod manifest 'author' must not be blank");
        }

        dependencies = dependencies != null ? dependencies : List.of();
        scripts = scripts != null ? scripts : List.of();
        dataOverrides = dataOverrides != null ? dataOverrides : List.of();
    }
}
