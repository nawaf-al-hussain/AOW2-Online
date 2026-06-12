package com.aow2.mod;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Mod manifest loaded from mod.json in each mod directory.
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
        dependencies = dependencies != null ? dependencies : List.of();
        scripts = scripts != null ? scripts : List.of();
        dataOverrides = dataOverrides != null ? dataOverrides : List.of();
    }
}
