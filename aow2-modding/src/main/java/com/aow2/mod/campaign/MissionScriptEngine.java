package com.aow2.mod.campaign;

import com.aow2.core.campaign.ScriptEngine;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import com.aow2.mod.LuaEngine;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Constructs a new MissionScriptEngine.
     */
    public MissionScriptEngine() {
        this.luaEngine = new LuaEngine();
        this.triggerCallbacks = new ConcurrentHashMap<>();
        this.scriptActive = false;
    }

    /**
     * Loads and executes a mission script, exposing game state to the Lua environment.
     *
     * @param scriptFile path to the Lua script (classpath resource)
     * @param state      current game state
     * @param entities   entity manager for queries
     */
    public boolean loadScript(String scriptFile, GameState state, EntityManager entities) {
        try {
            // Expose game state variables to Lua
            luaEngine.setGlobalInt("gameTick", (int) state.currentTick());
            luaEngine.setGlobalInt("unitCount", entities.unitCount());
            luaEngine.setGlobalInt("buildingCount", entities.buildingCount());
            luaEngine.setGlobalString("gameState", state.isRunning() ? "running" : "paused");

            boolean loaded = luaEngine.loadScript(scriptFile);
            if (loaded) {
                scriptActive = true;
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
        try {
            luaEngine.setGlobalInt("gameTick", (int) state.currentTick());
            luaEngine.setGlobalInt("unitCount", entities.unitCount());
            luaEngine.setGlobalInt("buildingCount", entities.buildingCount());

            boolean loaded = luaEngine.loadScriptFromString(scriptContent, scriptName);
            if (loaded) {
                scriptActive = true;
                LOG.info("Mission script loaded from string: {}", scriptName);
            }
            return loaded;
        } catch (Exception e) {
            LOG.error("Error loading mission script from string: {}", scriptName, e);
            return false;
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
        triggerCallbacks.clear();
        scriptActive = false;
        LOG.debug("MissionScriptEngine reset");
    }
}
