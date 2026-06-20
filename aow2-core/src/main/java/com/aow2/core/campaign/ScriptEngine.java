package com.aow2.core.campaign;

import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;

/**
 * Interface for script execution in campaign missions.
 * Implementations are provided by the modding module (LuaJ-based).
 * This interface breaks the circular dependency between aow2-core and aow2-modding.
 */
public interface ScriptEngine {

    /**
     * Loads a script from a classpath resource.
     *
     * @param scriptFile path to the script resource
     * @param state      current game state
     * @param entities   entity manager for queries
     * @return true if loaded successfully
     */
    boolean loadScript(String scriptFile, GameState state, EntityManager entities);

    /**
     * Loads a script from a raw string.
     *
     * @param scriptContent the script source code
     * @param scriptName    name for error reporting
     * @param state         current game state
     * @param entities      entity manager for queries
     * @return true if loaded successfully
     */
    boolean loadScriptFromString(String scriptContent, String scriptName,
                                 GameState state, EntityManager entities);

    /**
     * Processes script events for one game tick.
     *
     * @param state    current game state
     * @param entities entity manager for queries
     */
    void processTick(GameState state, EntityManager entities);

    /**
     * Registers a Java callback for a trigger ID.
     *
     * @param triggerId the trigger ID
     * @param callback  the action to execute
     */
    void registerTriggerCallback(int triggerId, Runnable callback);

    /**
     * Fires a trigger by ID.
     *
     * @param triggerId the trigger ID that activated
     */
    void fireTrigger(int triggerId);

    /**
     * Sets an integer script variable.
     */
    void setScriptVariable(String name, int value);

    /**
     * Sets a string script variable.
     */
    void setScriptVariable(String name, String value);

    /**
     * Gets an integer script variable.
     */
    int getScriptVariableInt(String name);

    /**
     * Gets a string script variable.
     */
    String getScriptVariableString(String name);

    /**
     * Returns whether a script is currently active.
     */
    boolean isScriptActive();

    /**
     * Resets the script engine.
     */
    void reset();
}
