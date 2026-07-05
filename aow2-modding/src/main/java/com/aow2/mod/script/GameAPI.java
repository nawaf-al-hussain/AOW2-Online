package com.aow2.mod.script;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.engine.GameState;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua-accessible game API for modding and campaign scripting.
 * Available in Lua as: aow2.spawnUnit(...), aow2.destroyUnit(...), etc.
 * <p>
 * THREAD-SAFETY WARNING (H-23): All fields (gameState, entityManager, economySystem,
 * objectives, timers, eventHooks) are static mutable shared state accessed from both the
 * game loop thread and the Lua scripting thread. This is NOT thread-safe. A proper fix
 * would require either: (a) synchronizing all access with locks, (b) using thread-local
 * state, or (c) using a message-passing queue between threads. This is an architectural
 * issue that cannot be fixed without significant refactoring.
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

    /** Economy system reference for credit queries. */
    private static EconomySystem economySystem;

    /** FIX(PLAYTEST-6): Cached map dimensions for spawn coordinate clamping. */
    private static int mapWidth = 128;
    private static int mapHeight = 128;

    /** Objective status map. Key = objective name, Value = status string.
     *  FIX (P1-M1): Changed to ConcurrentHashMap for thread-safety between
     *  game loop thread and Lua scripting thread. */
    private static final Map<String, String> objectives = new ConcurrentHashMap<>();

    /** Timer callbacks. Key = callback name, Value = remaining ticks.
     *  FIX (P1-M1): Changed to ConcurrentHashMap for thread-safety. */
    private static final Map<String, Integer> timers = new ConcurrentHashMap<>();

    /** Event hook callbacks.
     *  FIX (P1-M1): Changed to ConcurrentHashMap for thread-safety. */
    private static final Map<String, String> eventHooks = new ConcurrentHashMap<>();

    /** Message queue for script messages to be displayed by the client UI layer. */
    private static final List<String> messageQueue = Collections.synchronizedList(new ArrayList<>());

    /**
     * Private constructor — all methods are static for Lua access.
     */
    private GameAPI() {}

    /**
     * Initializes the GameAPI with game state, entity manager, and economy system references.
     *
     * @param state          game state
     * @param entities       entity manager
     * @param economy        economy system for credit queries
     */
    public static void initialize(GameState state, EntityManager entities, EconomySystem economy) {
        gameState = state;
        entityManager = entities;
        economySystem = economy;
    }

    /**
     * FIX(PLAYTEST-6): Sets the map dimensions for spawn coordinate clamping.
     * Called by MissionScriptEngine when the game scene is initialized with a map.
     *
     * @param width  map width in tiles
     * @param height map height in tiles
     */
    public static void setMapDimensions(int width, int height) {
        mapWidth = Math.max(1, width);
        mapHeight = Math.max(1, height);
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
            // FIX(PLAYTEST-6): Clamp spawn coordinates to actual map dimensions
            // instead of hardcoded 127. Uses dimensions set via setMapDimensions().
            GridPosition pos = new GridPosition(
                Math.clamp(x, 0, mapWidth - 1),
                Math.clamp(y, 0, mapHeight - 1)
            );

            // Look up unit stats from StatsRegistry instead of hardcoded dummy values
            UnitStats stats;
            try {
                stats = com.aow2.common.config.StatsRegistry.getInstance().getUnitStats(type);
            } catch (IllegalArgumentException e) {
                LOG.warn("No stats registered for unit type {}, falling back to defaults", type);
                stats = new UnitStats(type, "Script spawned", 100, 10,
                    4, 2, 0, 5, 4, WeaponType.NONE, 0, 60, 100, 5, 2, 0, 0, 0);
            }

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
     * Shows a message to the player by adding it to the message queue.
     * The client UI layer can poll this queue via {@link #getAndClearMessages()}.
     *
     * @param text message text
     */
    public static void showMessage(String text) {
        LOG.info("[SCRIPT MESSAGE] {}", text);
        messageQueue.add(text);
    }

    /**
     * Returns and clears all pending script messages from the queue.
     * The client UI layer should call this each frame to display messages.
     *
     * @return list of pending messages, cleared from the queue
     */
    public static List<String> getAndClearMessages() {
        List<String> messages = new ArrayList<>(messageQueue);
        messageQueue.clear();
        return messages;
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

    /**
     * Processes all active timers, decrementing tick counts and collecting
     * expired timer callbacks to be fired. Must be called each game tick.
     * <p>
     * Returns the list of callback names whose timers have expired, so the
     * caller (e.g., MissionScriptEngine) can invoke the corresponding Lua functions.
     *
     * @return list of expired timer callback names
     */
    public static java.util.List<String> processTimers() {
        java.util.List<String> expired = new java.util.ArrayList<>();
        var iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expired.add(entry.getKey());
                iterator.remove();
                LOG.debug("Script timer expired: {}", entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }
        return expired;
    }

    // --- Event Hooks ---
    // FIX (M-NEW-13): Event hooks are now dispatched via fireEvent() and the
    // EventDispatcher functional interface. Combat systems call fireEvent()
    // which delegates to the configured dispatcher (typically LuaEngine).

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
        try {
            return entityManager.getAliveUnitsForPlayer(resolveFaction(faction)).size();
        } catch (IllegalArgumentException e) {
            LOG.warn("getUnitCount: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of buildings for a faction.
     *
     * @param faction faction name
     * @return building count
     */
    public static int getBuildingCount(String faction) {
        if (entityManager == null) return 0;
        try {
            return entityManager.getBuildingsForPlayer(resolveFaction(faction)).size();
        } catch (IllegalArgumentException e) {
            LOG.warn("getBuildingCount: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the current credits for a faction.
     * Delegates to the EconomySystem which tracks per-player credit balances.
     *
     * @param faction faction name
     * @return credit amount, or 0 if economy system is not available
     */
    public static int getCredits(String faction) {
        if (economySystem == null) return 0;
        Faction f = resolveFaction(faction);
        int playerId = EconomySystem.playerId(f);
        return economySystem.getCredits(playerId);
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
     * Functional interface for dispatching events to Lua scripts.
     * Allows the combat system to fire events without directly depending on LuaEngine.
     */
    @FunctionalInterface
    public interface EventDispatcher {
        /**
         * Dispatches a Lua event callback.
         *
         * @param callbackName the Lua function name registered via onUnitKilled / onBuildingDestroyed etc.
         * @param args         arguments to pass to the Lua function
         */
        void dispatch(String callbackName, Object... args);
    }

    /** The event dispatcher used to invoke Lua callbacks. */
    private static EventDispatcher eventDispatcher;

    /**
     * Sets the event dispatcher used to invoke registered Lua callbacks.
     * Typically set to a lambda that calls LuaEngine.callFunction().
     *
     * @param dispatcher the dispatcher implementation
     */
    public static void setEventDispatcher(EventDispatcher dispatcher) {
        eventDispatcher = dispatcher;
    }

    /**
     * Fires a registered event hook by looking up the callback name and invoking
     * it via the configured EventDispatcher.
     *
     * @param eventType the event type key (e.g., "unitKilled", "buildingDestroyed")
     * @param args       arguments to pass to the Lua callback
     */
    public static void fireEvent(String eventType, Object... args) {
        String callbackName = eventHooks.get(eventType);
        if (callbackName == null) {
            return;
        }
        if (eventDispatcher != null) {
            try {
                eventDispatcher.dispatch(callbackName, args);
                LOG.debug("Fired event hook: {} -> {}", eventType, callbackName);
            } catch (Exception e) {
                LOG.error("Failed to fire event hook: {} -> {}", eventType, callbackName, e);
            }
        } else {
            LOG.warn("Event hook '{}' registered but no EventDispatcher configured", eventType);
        }
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
        // FIX (ANALYSIS_V2 5.3): Clear ALL static state to prevent leaks across
        // game sessions. Previously gameState, entityManager, economySystem, and
        // map dimensions were not cleared — a new game would operate on the previous
        // game's state if the caller forgot to re-initialize.
        gameState = null;
        entityManager = null;
        economySystem = null;
        objectives.clear();
        timers.clear();
        eventHooks.clear();
        messageQueue.clear();
        mapWidth = 128;
        mapHeight = 128;
    }
}
