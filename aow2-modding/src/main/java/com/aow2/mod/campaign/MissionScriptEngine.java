package com.aow2.mod.campaign;

import com.aow2.core.campaign.ScriptEngine;
import com.aow2.core.engine.GameState;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.mod.ModEventBridge;
import com.aow2.core.world.EntityManager;
import com.aow2.mod.script.GameAPI;
import com.aow2.mod.script.LuaEngine;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes mission scripts using LuaJ for campaign missions.
 * Implements the core {@link ScriptEngine} interface to break circular dependencies.
 * Delegates to aow2-modding's {@link LuaEngine} for script execution.
 * Provides game-state bindings so scripts can query and modify the game world.
 * REF: campaign_guide.md Section 3 - trigger and script system
 */
public final class MissionScriptEngine implements ScriptEngine {

    private static final Logger LOG = LoggerFactory.getLogger(MissionScriptEngine.class);

    /** The Lua scripting engine from the modding module. */
    private final LuaEngine luaEngine;

    /** Registered trigger callbacks keyed by trigger ID. */
    private final Map<Integer, Runnable> triggerCallbacks;

    /** Whether a mission script is currently loaded. */
    private boolean scriptActive;

    /** Whether ModEventBridge callbacks have been registered this session. */
    private final AtomicBoolean bridgeRegistered = new AtomicBoolean(false);

    /**
     * Constructs a new MissionScriptEngine.
     * FIX(PLAYTEST-5): Wires the GameAPI EventDispatcher so that fireEvent()
     * can invoke Lua callbacks. Without this, onUnitKilled/onBuildingDestroyed
     * hooks registered in Lua are never dispatched.
     */
    public MissionScriptEngine() {
        this.luaEngine = new LuaEngine();
        this.triggerCallbacks = new ConcurrentHashMap<>();
        this.scriptActive = false;

        GameAPI.setEventDispatcher((callbackName, args) -> {
            try {
                LuaValue[] luaArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    luaArgs[i] = LuaEngine.javaToLua(args[i]);
                }
                luaEngine.callFunction(callbackName, luaArgs);
            } catch (Exception e) {
                LOG.error("Error dispatching event to Lua: {}", callbackName, e);
            }
        });
    }

    /**
     * Loads and executes a mission script, exposing game state to the Lua environment.
     *
     * @param scriptFile path to the Lua script (classpath resource)
     * @param state      current game state
     * @param entities   entity manager for queries
     */
    public boolean loadScript(String scriptFile, GameState state, EntityManager entities) {
        return loadScript(scriptFile, state, entities, null);
    }

    /**
     * Loads and executes a mission script, exposing game state and economy to the Lua environment.
     *
     * @param scriptFile    path to the Lua script (classpath resource)
     * @param state         current game state
     * @param entities      entity manager for queries
     * @param economySystem economy system for credit queries (may be null)
     */
    public boolean loadScript(String scriptFile, GameState state, EntityManager entities,
                              EconomySystem economySystem) {
        try {
            // Initialize the Lua engine before setting globals
            if (!luaEngine.isInitialized()) {
                luaEngine.initialize(state, entities, economySystem);
            }

            // Expose game state variables to Lua
            luaEngine.setGlobalInt("gameTick", (int) state.currentTick());
            luaEngine.setGlobalInt("unitCount", entities.unitCount());
            luaEngine.setGlobalInt("buildingCount", entities.buildingCount());
            luaEngine.setGlobalString("gameState", state.isRunning() ? "running" : "paused");

            boolean loaded = luaEngine.loadScript(scriptFile);
            if (loaded) {
                scriptActive = true;
                wireModEventBridge();
                LOG.info("Mission script loaded: {}", scriptFile);
            } else {
                LOG.warn("Failed to load mission script: {}", scriptFile);
            }
            return loaded;
        } catch (Exception e) {
            LOG.error("Error loading mission script: {}", scriptFile, e);
            return false;
        }
    }

    /**
     * Loads a mission script from a raw string, exposing game state to the Lua environment.
     *
     * @param scriptContent the Lua script source code
     * @param scriptName    name for error reporting
     * @param state         current game state
     * @param entities      entity manager for queries
     */
    public boolean loadScriptFromString(String scriptContent, String scriptName,
                                     GameState state, EntityManager entities) {
        return loadScriptFromString(scriptContent, scriptName, state, entities, null);
    }

    /**
     * Loads a mission script from a raw string, exposing game state and economy to the Lua environment.
     *
     * @param scriptContent the Lua script source code
     * @param scriptName    name for error reporting
     * @param state         current game state
     * @param entities      entity manager for queries
     * @param economySystem economy system for credit queries (may be null)
     */
    public boolean loadScriptFromString(String scriptContent, String scriptName,
                                     GameState state, EntityManager entities,
                                     EconomySystem economySystem) {
        try {
            if (!luaEngine.isInitialized()) {
                luaEngine.initialize(state, entities, economySystem);
            }
            luaEngine.setGlobalInt("gameTick", (int) state.currentTick());
            luaEngine.setGlobalInt("unitCount", entities.unitCount());
            luaEngine.setGlobalInt("buildingCount", entities.buildingCount());

            boolean loaded = luaEngine.loadScriptFromString(scriptContent, scriptName);
            if (loaded) {
                scriptActive = true;
                wireModEventBridge();
                LOG.info("Mission script loaded from string: {}", scriptName);
            }
            return loaded;
        } catch (Exception e) {
            LOG.error("Error loading mission script from string: {}", scriptName, e);
            return false;
        }
    }

    /**
     * FIX(PLAYTEST-3): Calls the Lua onStart() function to initialize the mission.
     * All 29 mission scripts define onStart() to register event hooks, show
     * briefing messages, and set initial objectives. Previously this was never
     * called — processTick() was invoked instead, which only calls onTick().
     */
    public void callStartFunction() {
        if (!scriptActive) {
            return;
        }
        try {
            luaEngine.callFunction("onStart");
            LOG.info("Lua onStart() executed successfully");
        } catch (Exception e) {
            LOG.error("Error calling Lua onStart()", e);
        }
    }

    /**
     * FIX(PLAYTEST-5): Registers ModEventBridge callbacks that forward combat
     * events (unit killed, building destroyed) to GameAPI.fireEvent(). This connects
     * the core combat system to the Lua scripting layer. Registration is idempotent
     * — callbacks are only wired once per session.
     */
    private void wireModEventBridge() {
        if (bridgeRegistered.compareAndSet(false, true)) {
            ModEventBridge.registerUnitKilledCallback(
                (unitId, type, faction, killerPlayerId) ->
                    GameAPI.fireEvent("unitKilled", unitId));
            ModEventBridge.registerBuildingDestroyedCallback(
                (buildingId, type, faction, destroyerPlayerId) ->
                    GameAPI.fireEvent("buildingDestroyed", buildingId));
            LOG.info("ModEventBridge callbacks registered for Lua event dispatch");
        }
    }

    /**
     * Processes script events for one game tick.
     * Updates Lua globals with current game state and calls the onTick function if defined.
     * REF: campaign_guide.md Section 3.1 - reinforcement check runs each tick
     *
     * @param state     current game state
     * @param entities  entity manager for queries
     */
    public void processTick(GameState state, EntityManager entities) {
        if (!scriptActive) {
            return;
        }

        try {
            // Update Lua globals each tick
            luaEngine.setGlobalInt("gameTick", (int) state.currentTick());
            luaEngine.setGlobalInt("unitCount", entities.unitCount());
            luaEngine.setGlobalInt("buildingCount", entities.buildingCount());

            // Process script timers (decrement tick counts, fire expired callbacks)
            for (String expiredCallback : GameAPI.processTimers()) {
                try {
                    luaEngine.callFunction(expiredCallback, LuaValue.NIL);
                } catch (Exception e) {
                    LOG.error("Error invoking expired timer callback: {}", expiredCallback, e);
                }
            }

            // Call Lua onTick function if defined
            luaEngine.callFunction("onTick", LuaValue.valueOf(state.currentTick()));
        } catch (Exception e) {
            LOG.error("Error processing script tick at tick={}", state.currentTick(), e);
        }
    }

    /**
     * Registers a Java callback to be invoked when a trigger fires.
     *
     * @param triggerId the trigger ID that activates this callback
     * @param callback  the action to execute when the trigger fires
     */
    public void registerTriggerCallback(int triggerId, Runnable callback) {
        triggerCallbacks.put(triggerId, callback);
        LOG.debug("Registered trigger callback for triggerId={}", triggerId);
    }

    /**
     * Fires a trigger by ID, executing both Java callbacks and Lua trigger handlers.
     *
     * @param triggerId the trigger ID that has been activated
     */
    public void fireTrigger(int triggerId) {
        // Execute Java callback
        Runnable callback = triggerCallbacks.get(triggerId);
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                LOG.error("Error executing Java trigger callback for triggerId={}", triggerId, e);
            }
        }

        // Execute Lua trigger handler
        if (scriptActive) {
            try {
                luaEngine.fireTrigger(triggerId, LuaValue.valueOf(triggerId));
            } catch (Exception e) {
                LOG.error("Error executing Lua trigger for triggerId={}", triggerId, e);
            }
        }

        LOG.debug("Fired trigger: triggerId={}", triggerId);
    }

    /**
     * Exposes a custom integer variable to the Lua environment.
     *
     * @param name  variable name
     * @param value variable value
     */
    public void setScriptVariable(String name, int value) {
        luaEngine.setGlobalInt(name, value);
    }

    /**
     * Exposes a custom string variable to the Lua environment.
     *
     * @param name  variable name
     * @param value variable value
     */
    public void setScriptVariable(String name, String value) {
        luaEngine.setGlobalString(name, value);
    }

    /**
     * Reads an integer variable from the Lua environment.
     *
     * @param name variable name
     * @return the integer value, or 0 if not defined
     */
    public int getScriptVariableInt(String name) {
        return luaEngine.getGlobalInt(name);
    }

    /**
     * Reads a string variable from the Lua environment.
     *
     * @param name variable name
     * @return the string value, or empty string if not defined
     */
    public String getScriptVariableString(String name) {
        return luaEngine.getGlobalString(name);
    }

    /**
     * Returns whether a mission script is currently active.
     *
     * @return true if a script is loaded and active
     */
    public boolean isScriptActive() {
        return scriptActive;
    }

    /**
     * Returns the underlying LuaEngine for advanced usage.
     *
     * @return the Lua engine instance
     */
    public LuaEngine getLuaEngine() {
        return luaEngine;
    }

    /**
     * Resets the script engine, unloading all scripts and clearing callbacks.
     */
    public void reset() {
        luaEngine.reset();
        GameAPI.reset();
        triggerCallbacks.clear();
        scriptActive = false;
        LOG.debug("MissionScriptEngine reset");
    }
}