package com.aow2.core.mod;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.UnitType;

/**
 * Bridge for firing mod events from core systems without a compile-time dependency on aow2-modding.
 * The modding module registers implementations at runtime via reflection or service loader.
 * <p>
 * This allows the core combat system to notify mods of unit kills and building destructions
 * without importing any modding classes.
 */
public final class ModEventBridge {

    /** Callback for unit killed events. */
    public interface UnitKilledCallback {
        void onUnitKilled(long unitId, UnitType type, Faction faction, long killerPlayerId);
    }

    /** Callback for building destroyed events. */
    public interface BuildingDestroyedCallback {
        void onBuildingDestroyed(long buildingId, BuildingType type, Faction faction, long destroyerPlayerId);
    }

    private static volatile UnitKilledCallback unitKilledCallback;
    private static volatile BuildingDestroyedCallback buildingDestroyedCallback;

    private ModEventBridge() {}

    /**
     * Registers a callback for unit killed events.
     *
     * @param cb the callback implementation
     */
    public static void registerUnitKilledCallback(UnitKilledCallback cb) {
        unitKilledCallback = cb;
    }

    /**
     * Registers a callback for building destroyed events.
     *
     * @param cb the callback implementation
     */
    public static void registerBuildingDestroyedCallback(BuildingDestroyedCallback cb) {
        buildingDestroyedCallback = cb;
    }

    /**
     * Fires the unit killed event to any registered mod callback.
     *
     * @param unitId          the ID of the killed unit
     * @param type            the unit type
     * @param faction         the faction of the killed unit
     * @param killerPlayerId  the player ID of the killer
     */
    public static void fireUnitKilled(long unitId, UnitType type, Faction faction, long killerPlayerId) {
        UnitKilledCallback cb = unitKilledCallback;
        if (cb != null) {
            cb.onUnitKilled(unitId, type, faction, killerPlayerId);
        }
    }

    /**
     * Fires the building destroyed event to any registered mod callback.
     *
     * @param buildingId      the ID of the destroyed building
     * @param type            the building type
     * @param faction         the faction of the destroyed building
     * @param destroyerPlayerId the player ID of the destroyer
     */
    public static void fireBuildingDestroyed(long buildingId, BuildingType type, Faction faction, long destroyerPlayerId) {
        BuildingDestroyedCallback cb = buildingDestroyedCallback;
        if (cb != null) {
            cb.onBuildingDestroyed(buildingId, type, faction, destroyerPlayerId);
        }
    }
}