package com.aow2.core.campaign;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.BuildingType;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record containing all saved game state for a campaign mission.
 * Captures the complete game snapshot including entity positions, health,
 * resources, research state, and campaign progression.
 * REF: campaign_guide.md Section 1.1 - 3 save slots stored as /s0m, /s1m, /s2m
 * REF: campaign_guide.md Section 8.1 - progress persistence via U() method
 */
public record SaveData(
    /** The save slot index (0-2). */
    int slot,

    /** The campaign episode this save belongs to. */
    CampaignEpisode episode,

    /** The mission index within the episode. */
    int missionIndex,

    /** Timestamp when this save was created. */
    Instant saveTime,

    /** Current game tick at save time. */
    long gameTick,

    /** Serialized unit state snapshots. */
    List<UnitSave> units,

    /** Serialized building state snapshots. */
    List<BuildingSave> buildings,

    /** Completed research IDs per faction. */
    List<String> completedResearch,

    /** Credit amounts per player index. */
    List<Integer> credits,

    /** Current mission objectives with progress. */
    List<Objective> objectives,

    /** Current mission triggers with activation state. */
    List<Trigger> triggers
) {

    /**
     * Compact constructor with defensive copies.
     */
    public SaveData {
        if (slot < 0 || slot > 2) {
            throw new IllegalArgumentException("Save slot must be 0-2, got: " + slot);
        }
        if (episode == null) {
            throw new IllegalArgumentException("Episode must not be null");
        }
        if (missionIndex < 0) {
            throw new IllegalArgumentException("Mission index must be non-negative, got: " + missionIndex);
        }
        units = units != null ? List.copyOf(units) : List.of();
        buildings = buildings != null ? List.copyOf(buildings) : List.of();
        completedResearch = completedResearch != null ? List.copyOf(completedResearch) : List.of();
        credits = credits != null ? List.copyOf(credits) : List.of();
        objectives = objectives != null ? List.copyOf(objectives) : List.of();
        triggers = triggers != null ? List.copyOf(triggers) : List.of();
    }

    /**
     * Serialized unit state for save/load.
     * REF: campaign_guide.md Section 5.1 - entity data layout (101 bytes per entity)
     */
    public record UnitSave(
        int entityId,
        UnitType unitType,
        Faction faction,
        GridPosition position,
        int hp,
        int maxHp,
        int rank,
        int experience,
        boolean siegeMode,
        int attackCooldown,
        int weaponCooldown
    ) {}

    /**
     * Serialized building state for save/load.
     * REF: campaign_guide.md Section 6 - building categories and footprints
     */
    public record BuildingSave(
        int entityId,
        BuildingType buildingType,
        Faction faction,
        GridPosition position,
        int hp,
        int maxHp,
        int constructionProgress,
        boolean powered,
        String researchId,
        List<UnitType> productionQueue,
        int productionProgress
    ) {}
}
