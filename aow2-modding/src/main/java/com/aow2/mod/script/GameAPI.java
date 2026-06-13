package com.aow2.mod.script;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Lua-accessible game API for modding and campaign scripting.
 * Available in Lua as: aow2.spawnUnit(...), aow2.destroyUnit(...), etc.
 * <p>
 * REF: tech_stack.md - exposed API for modding and campaign scripting
 * REF: phases.md Phase 10 - GameAPI for Lua scripts
 * <p>
 * ASSUMPTION: Faction resolution uses "confederation"/"resistance" string names
 * in Lua, mapped to Faction enum values internally.
 */
public final class GameAPI {

    private static final Logger LOG = LoggerFactory.getLogger(GameAPI.class);

    /** Game state reference. */
    private static GameState gameState;

    /** Entity manager reference. */
    private static EntityManager entityManager;

    /** Objective status map. Key = objective name, Value = status string. */
    private static final Map<String, String> objectives = new HashMap<>();

    /** Timer callbacks. Key = callback name, Value = remaining ticks. */
    private static final Map<String, Integer> timers = new HashMap<>();

    /** Event hook callbacks. */
    private static final Map<String, String> eventHooks = new HashMap<>();

    /**
     * Private constructor — all methods are static for Lua access.
     */
    private GameAPI() {}

    /**
     * Initializes the GameAPI with game state and entity manager references.
     *
     * @param state    game state
     * @param entities entity manager
     */
    static void initialize(GameState state, EntityManager entities) {
        gameState = state;
        entityManager = entities;
    }

    // --- Spawn / Destroy ---

    /**
     * Spawns a unit at the given position.
     *
     * @param faction  faction name ("confederation" or "resistance")
     * @param unitType unit type name (e.g., "CONFED_INFANTRY")
     * @param x        grid X position
     * @param y        grid Y position
     * @return the spawned unit's entity ID, or -1 on failure
     */
    public static int spawnUnit(String faction, String unitType, int x, int y) {
        if (entityManager == null) return -1;

        try {
            Faction f = resolveFaction(faction);
            UnitType type = UnitType.valueOf(unitType);
            GridPosition pos = new GridPosition(
                Math.clamp(x, 0, 127),
                Math.clamp(y, 0, 127)
            );

            // ASSUMPTION: default stats for scripting-spawned units
            UnitStats stats = new UnitStats(type, "Script spawned", 100, 10,
                4, 2, 0, 5, 4, WeaponType.NONE, 0, 60, 100, 5, 2, 0, 0, 0);

            int id = entityManager.allocateEntityId();
            Unit unit = new Unit(id, f, pos, type, stats);
            entityManager.addUnit(unit);

            LOG.debug("Script spawned unit: {} {} at ({},{}) id={}", faction, unitType, x, y, id);
            return id;
        } catch (Exception e) {
            LOG.error("Failed to spawn unit: {} {} at ({},{})", faction, unitType, x, y, e);
            return -1;
        }
    }

    /**
     * Destroys a unit by entity ID.
     *
     * @param unitId the unit's entity ID
     */
    public static void destroyUnit(int unitId) {
        if (entityManager == null) return;

        Unit unit = entityManager.getUnit(unitId);
        if (unit != null && unit.isAlive()) {
            unit.takeDamage(unit.getHp() + 1);
            LOG.debug("Script destroyed unit: {}", unitId);
        }
    }

    // --- Objectives ---

    /**
     * Gets the status of a campaign objective.
     *
     * @param name objective name
     * @return objective status, or "unknown" if not set
     */
    public static String getObjective(String name) {
        return objectives.getOrDefault(name, "unknown");
    }

    /**
     * Sets the status of a campaign objective.
     *
     * @param name   objective name
     * @param status new status (e.g., "completed", "failed", "active")
     */
    public static void setObjective(String name, String status) {
        objectives.put(name, status);
        LOG.debug("Script set objective '{}' = '{}'", name, status);
    }

    // --- Messages ---

    /**
     * Shows a message to the player.
     * ASSUMPTION: message display handled by the client UI layer.
     *
     * @param text message text
     */
    public static void showMessage(String text) {
        LOG.info("[SCRIPT MESSAGE] {}", text);
    }

    // --- Timers ---

    /**
     * Sets a timer that calls a Lua function after the given number of seconds.
     * ASSUMPTION: 1 second = 30 game ticks.
     *
     * @param seconds       delay in seconds
     * @param callbackName  name of the Lua function to call
     */
    public static void setTimer(int seconds, String callbackName) {
        int ticks = seconds * 30;
        timers.put(callbackName, ticks);
        LOG.debug("Script set timer: {}s -> {}", seconds, callbackName);
    }

    // --- Event Hooks ---

    /**
     * Registers a callback for the unit killed event.
     *
     * @param callbackName Lua function name
     */
    public static void onUnitKilled(String callbackName) {
        eventHooks.put("unitKilled", callbackName);
        LOG.debug("Script registered onUnitKilled -> {}", callbackName);
    }

    /**
     * Registers a callback for the building destroyed event.
     *
     * @param callbackName Lua function name
     */
    public static void onBuildingDestroyed(String callbackName) {
        eventHooks.put("buildingDestroyed", callbackName);
        LOG.debug("Script registered onBuildingDestroyed -> {}", callbackName);
    }

    /**
     * Registers a callback for when a unit enters a specific area.
     *
     * @param x            area center X
     * @param y            area center Y
     * @param radius       area radius in cells
     * @param callbackName Lua function name
     */
    public static void onAreaEntered(int x, int y, int radius, String callbackName) {
        eventHooks.put("areaEntered_" + x + "_" + y + "_" + radius, callbackName);
        LOG.debug("Script registered onAreaEntered at ({},{}) r={} -> {}",
            x, y, radius, callbackName);
    }

    // --- Queries ---

    /**
     * Returns the number of alive units for a faction.
     *
     * @param faction faction name
     * @return unit count
     */
    public static int getUnitCount(String faction) {
        if (entityManager == null) return 0;
        return entityManager.getAliveUnitsForPlayer(resolveFaction(faction)).size();
    }

    /**
     * Returns the number of buildings for a faction.
     *
     * @param faction faction name
     * @return building count
     */
    public static int getBuildingCount(String faction) {
        if (entityManager == null) return 0;
        return entityManager.getBuildingsForPlayer(resolveFaction(faction)).size();
    }

    /**
     * Returns the current credits for a faction.
     * ASSUMPTION: credits tracked by EconomySystem; returns 0 for API stub.
     *
     * @param faction faction name
     * @return credit amount
     */
    public static int getCredits(String faction) {
        // ASSUMPTION: would need reference to EconomySystem for real implementation
        return 0;
    }

    /**
     * Returns the current game tick.
     *
     * @return current tick number
     */
    public static int getTick() {
        if (gameState == null) return 0;
        return (int) gameState.currentTick();
    }

    // --- Internal ---

    /**
     * Resolves a faction string to a Faction enum.
     */
    private static Faction resolveFaction(String faction) {
        return switch (faction.toLowerCase()) {
            case "confederation", "confed", "player0" -> Faction.CONFEDERATION;
            case "resistance", "rebel", "player1" -> Faction.RESISTANCE;
            default -> throw new IllegalArgumentException("Unknown faction: " + faction);
        };
    }

    /**
     * Returns the event hooks map (for internal use by event system).
     *
     * @return unmodifiable copy of event hooks
     */
    static Map<String, String> getEventHooks() {
        return Map.copyOf(eventHooks);
    }

    /**
     * Returns the timers map (for internal use by tick system).
     *
     * @return copy of timers
     */
    static Map<String, Integer> getTimers() {
        return Map.copyOf(timers);
    }

    /**
     * Clears all script state (objectives, timers, hooks).
     * Called when a mission ends or the LuaEngine is reset.
     */
    public static void reset() {
        objectives.clear();
        timers.clear();
        eventHooks.clear();
    }
}
