package com.aow2.mod;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua scripting engine powered by LuaJ.
 * Provides a sandboxed Lua execution environment for mission scripts and mod support.
 * REF: campaign_guide.md - mission scripting system uses Lua-based event triggers
 */
public final class LuaEngine {

    private static final Logger LOG = LoggerFactory.getLogger(LuaEngine.class);

    /** The Lua globals environment. */
    private final Globals globals;

    /** Registered callback functions keyed by trigger ID. */
    private final Map<Integer, LuaValue> triggerCallbacks;

    /** Whether a script is currently loaded. */
    private boolean scriptLoaded;

    /**
     * Constructs a new LuaEngine with standard Lua globals.
     */
    public LuaEngine() {
        this.globals = JsePlatform.standardGlobals();
        this.triggerCallbacks = new ConcurrentHashMap<>();
        this.scriptLoaded = false;
    }

    /**
     * Loads and executes a Lua script from a classpath resource.
     *
     * @param resourcePath classpath path to the Lua script
     * @return true if the script loaded successfully, false otherwise
     */
    public boolean loadScript(String resourcePath) {
        try (InputStream is = LuaEngine.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.error("Script resource not found: {}", resourcePath);
                return false;
            }
            String script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            globals.load(script, resourcePath).call();
            scriptLoaded = true;
            LOG.info("Loaded Lua script: {}", resourcePath);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load Lua script: {}", resourcePath, e);
            return false;
        }
    }

    /**
     * Loads and executes a Lua script from a raw string.
     *
     * @param scriptContent the Lua script source code
     * @param scriptName    name for error reporting
     * @return true if the script loaded successfully, false otherwise
     */
    public boolean loadScriptFromString(String scriptContent, String scriptName) {
        try {
            globals.load(scriptContent, scriptName).call();
            scriptLoaded = true;
            LOG.info("Loaded Lua script from string: {}", scriptName);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load Lua script from string: {}", scriptName, e);
            return false;
        }
    }

    /**
     * Exposes a Java value to the Lua environment as a global variable.
     *
     * @param name  the global variable name in Lua
     * @param value the value to expose
     */
    public void setGlobal(String name, LuaValue value) {
        globals.set(name, value);
    }

    /**
     * Exposes an integer value to the Lua environment.
     *
     * @param name  the global variable name in Lua
     * @param value the integer value
     */
    public void setGlobalInt(String name, int value) {
        globals.set(name, LuaValue.valueOf(value));
    }

    /**
     * Exposes a string value to the Lua environment.
     *
     * @param name  the global variable name in Lua
     * @param value the string value
     */
    public void setGlobalString(String name, String value) {
        globals.set(name, LuaValue.valueOf(value));
    }

    /**
     * Reads an integer global from the Lua environment.
     *
     * @param name the global variable name
     * @return the integer value, or 0 if not defined
     */
    public int getGlobalInt(String name) {
        LuaValue val = globals.get(name);
        return val.isnil() ? 0 : val.toint();
    }

    /**
     * Reads a string global from the Lua environment.
     *
     * @param name the global variable name
     * @return the string value, or empty string if not defined
     */
    public String getGlobalString(String name) {
        LuaValue val = globals.get(name);
        return val.isnil() ? "" : val.tojstring();
    }

    /**
     * Calls a Lua function by name with the given arguments.
     *
     * @param functionName name of the Lua function
     * @param args         arguments to pass
     * @return the return value from Lua, or NIL if the function doesn't exist
     */
    public LuaValue callFunction(String functionName, LuaValue... args) {
        LuaValue func = globals.get(functionName);
        if (func.isnil()) {
            LOG.warn("Lua function not found: {}", functionName);
            return LuaValue.NIL;
        }
        return func.invoke(args).arg1();
    }

    /**
     * Registers a Lua callback function for a trigger ID.
     * The callback function name is stored and invoked when the trigger fires.
     *
     * @param triggerId      the trigger ID
     * @param callbackName   the Lua function name to call
     */
    public void registerTriggerCallback(int triggerId, String callbackName) {
        LuaValue func = globals.get(callbackName);
        if (func.isnil()) {
            LOG.warn("Callback function not found in Lua: {}", callbackName);
        }
        triggerCallbacks.put(triggerId, func);
    }

    /**
     * Fires a trigger callback in the Lua environment.
     *
     * @param triggerId the trigger ID that fired
     * @param args      arguments to pass to the callback
     */
    public void fireTrigger(int triggerId, LuaValue... args) {
        LuaValue callback = triggerCallbacks.get(triggerId);
        if (callback != null && !callback.isnil()) {
            try {
                callback.invoke(args);
            } catch (Exception e) {
                LOG.error("Error executing trigger callback for triggerId={}", triggerId, e);
            }
        }
    }

    /**
     * Creates a new Lua table for passing structured data.
     *
     * @return a new empty Lua table
     */
    public LuaTable createTable() {
        return new LuaTable();
    }

    /**
     * Returns whether a script is currently loaded.
     *
     * @return true if a script has been loaded
     */
    public boolean isScriptLoaded() {
        return scriptLoaded;
    }

    /**
     * Resets the Lua engine, clearing all loaded scripts and callbacks.
     */
    public void reset() {
        triggerCallbacks.clear();
        scriptLoaded = false;
        LOG.debug("LuaEngine reset");
    }
}
