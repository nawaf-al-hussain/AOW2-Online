package com.aow2.core.campaign;

import com.aow2.common.model.Faction;

import java.util.List;

/**
 * A campaign mission definition.
 * Contains all data needed to set up and evaluate a single campaign mission,
 * including objectives, triggers, scripting hooks, and metadata.
 * REF: campaign_guide.md Section 2 - mission data structure with objectives, triggers, and scripting
 * REF: campaign_guide.md Section 8.1 - campaign state machine tracks mission progression
 */
public record Mission(
    /** Unique mission identifier within its episode. */
    int id,

    /** Display name of the mission. */
    String name,

    /** Short description shown in mission select. */
    String description,

    /** Detailed briefing text shown before the mission starts. */
    String briefing,

    /** The faction the player controls in this mission. */
    Faction playerFaction,

    /** Map file path relative to the data/maps directory. */
    String mapFile,

    /** List of objectives that must be completed to win. */
    List<Objective> objectives,

    /** List of triggers that can fire during the mission. */
    List<Trigger> triggers,

    /** List of Lua script files to load for this mission. */
    List<String> scriptFiles,

    /** Difficulty rating from 1 (easy) to 5 (hardest). */
    int difficulty
) {

    /**
     * Compact constructor with validation.
     */
    public Mission {
        if (id < 0) {
            throw new IllegalArgumentException("Mission ID must be non-negative, got: " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Mission name must not be blank");
        }
        if (playerFaction == null) {
            throw new IllegalArgumentException("Player faction must not be null");
        }
        if (mapFile == null || mapFile.isBlank()) {
            throw new IllegalArgumentException("Map file must not be blank");
        }
        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException("Difficulty must be 1-5, got: " + difficulty);
        }
        objectives = objectives != null ? List.copyOf(objectives) : List.of();
        triggers = triggers != null ? List.copyOf(triggers) : List.of();
        scriptFiles = scriptFiles != null ? List.copyOf(scriptFiles) : List.of();
    }

    /**
     * Returns whether all objectives in this mission are completed.
     *
     * @return true if every objective is completed
     */
    public boolean allObjectivesCompleted() {
        return objectives.stream().allMatch(Objective::isCompleted);
    }

    /**
     * Returns whether any objective in this mission has failed.
     *
     * @return true if any objective is failed
     */
    public boolean anyObjectiveFailed() {
        return objectives.stream().anyMatch(Objective::isFailed);
    }
}
