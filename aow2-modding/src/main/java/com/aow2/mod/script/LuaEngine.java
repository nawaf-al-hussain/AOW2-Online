package com.aow2.mod.script;

import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LuaJ-based scripting engine for campaign missions and mod scripts.
 * Provides a sandboxed Lua environment with game API bindings.
 * <p>
 * REF: tech_stack.md - LuaJ 3.x for pure Java Lua interpretation
 * REF: phases.md Phase 10 - scripting engine
 */
public final class LuaEngine {

    private static final Logger LOG = LoggerFactory.getLogger(LuaEngine.class);

    /** The Lua globals (execution environment). */
    private Globals globals;

    /** Whether the engine has been initialized. */
    private boolean initialized;

    /** The game state reference. */
    private GameState gameState;

    /** The entity manager reference. */
    private EntityManager entityManager;

    /** Script bindings for Java-to-Lua bridging. */
    private ScriptBindings scriptBindings;

    /**
     * Constructs a new LuaEngine.
     */
    public LuaEngine() {
        this.initialized = false;
    }

    /**
     * Initializes the Lua engine with game API bindings.
     * Creates a sandboxed Lua environment and registers the game API.
     *
     * @param state    the current game state
     * @param entities the entity manager
     */
    public void initialize(GameState state, EntityManager entities) {
        this.gameState = state;
        this.entityManager = entities;

        // Create Lua globals with standard libraries
        this.globals = JsePlatform.standardGlobals();

        // Create and apply script bindings
        this.scriptBindings = new ScriptBindings(globals, state, entities);
        scriptBindings.bindAll();

        this.initialized = true;
        LOG.info("Lua engine initialized with game API bindings");
    }

    /**
     * Executes a Lua script file.
     *
     * @param scriptPath path to the Lua script file
     */
    public void executeScript(String scriptPath) {
        ensureInitialized();

        try {
            String script = Files.readString(Path.of(scriptPath), StandardCharsets.UTF_8);
            globals.load(script, scriptPath).call();
            LOG.info("Executed Lua script: {}", scriptPath);
        } catch (IOException e) {
            LOG.error("Failed to read Lua script: {}", scriptPath, e);
        } catch (Exception e) {
            LOG.error("Lua script execution error: {}", scriptPath, e);
        }
    }

    /**
     * Executes a Lua script from a string.
     *
     * @param script the Lua script content
     * @return the result of script execution, or NIL on error
     */
    public LuaValue executeString(String script) {
        ensureInitialized();

        try {
            return globals.load(script, "inline").call();
        } catch (Exception e) {
            LOG.error("Lua inline execution error", e);
            return LuaValue.NIL;
        }
    }

    /**
     * Executes a Lua expression and returns the result.
     *
     * @param expression the Lua expression to evaluate
     * @return the result object, or null on error
     */
    public Object executeExpression(String expression) {
        LuaValue result = executeString("return " + expression);
        return luaToJava(result);
    }

    /**
     * Registers a Java function as a Lua global.
     *
     * @param name     the global name in Lua
     * @param function the LuaValue function to register
     */
    public void registerFunction(String name, LuaValue function) {
        ensureInitialized();
        globals.set(name, function);
        LOG.debug("Registered Lua function: {}", name);
    }

    /**
     * Calls a Lua function by name with the given arguments.
     *
     * @param name the function name
     * @param args variable arguments to pass
     * @return the function return values
     */
    public Varargs callFunction(String name, Object... args) {
        ensureInitialized();

        LuaValue function = globals.get(name);
        if (function.isnil()) {
            LOG.warn("Lua function not found: {}", name);
            return LuaValue.NONE;
        }

        LuaValue[] luaArgs = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) {
            luaArgs[i] = javaToLua(args[i]);
        }

        return function.invoke(LuaValue.varargsOf(luaArgs));
    }

    /**
     * Sets a Lua global variable.
     *
     * @param name  the variable name
     * @param value the value to set
     */
    public void setGlobal(String name, Object value) {
        ensureInitialized();
        globals.set(name, javaToLua(value));
    }

    /**
     * Gets a Lua global variable as a Java object.
     *
     * @param name the variable name
     * @return the value as a Java object
     */
    public Object getGlobal(String name) {
        ensureInitialized();
        return luaToJava(globals.get(name));
    }

    /**
     * Returns whether the engine is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the Lua globals for advanced usage.
     *
     * @return the Lua globals
     */
    public Globals getGlobals() {
        return globals;
    }

    /**
     * Sets a Lua global variable to an integer value.
     *
     * @param name  the variable name
     * @param value the integer value
     */
    public void setGlobalInt(String name, int value) {
        ensureInitialized();
        globals.set(name, LuaValue.valueOf(value));
    }

    /**
     * Sets a Lua global variable to a string value.
     *
     * @param name  the variable name
     * @param value the string value
     */
    public void setGlobalString(String name, String value) {
        ensureInitialized();
        globals.set(name, LuaValue.valueOf(value));
    }

    /**
     * Gets a Lua global variable as an integer.
     *
     * @param name the variable name
     * @return the integer value, or 0 if not defined or not a number
     */
    public int getGlobalInt(String name) {
        ensureInitialized();
        LuaValue val = globals.get(name);
        return val.isint() ? val.toint() : (val.isnumber() ? val.toint() : 0);
    }

    /**
     * Gets a Lua global variable as a string.
     *
     * @param name the variable name
     * @return the string value, or empty string if not defined or not a string
     */
    public String getGlobalString(String name) {
        ensureInitialized();
        LuaValue val = globals.get(name);
        return val.isstring() ? val.tojstring() : "";
    }

    /**
     * Loads a Lua script from a classpath resource.
     *
     * @param scriptFile path to the script resource
     * @return true if loaded successfully
     */
    public boolean loadScript(String scriptFile) {
        ensureInitialized();
        try {
            var is = getClass().getClassLoader().getResourceAsStream(scriptFile);
            if (is == null) {
                LOG.error("Script resource not found: {}", scriptFile);
                return false;
            }
            String script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            globals.load(script, scriptFile).call();
            LOG.info("Loaded Lua script from resource: {}", scriptFile);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load Lua script: {}", scriptFile, e);
            return false;
        }
    }

    /**
     * Loads a Lua script from a raw string.
     *
     * @param scriptContent the script source code
     * @param scriptName    name for error reporting
     * @return true if loaded successfully
     */
    public boolean loadScriptFromString(String scriptContent, String scriptName) {
        ensureInitialized();
        try {
            globals.load(scriptContent, scriptName).call();
            LOG.info("Loaded Lua script from string: {}", scriptName);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load Lua script from string: {}", scriptName, e);
            return false;
        }
    }

    /**
     * Fires a trigger in the Lua environment by calling the onTrigger function if defined.
     *
     * @param triggerId the trigger ID
     * @param arg       the LuaValue argument to pass
     */
    public void fireTrigger(int triggerId, LuaValue arg) {
        ensureInitialized();
        try {
            LuaValue onTrigger = globals.get("onTrigger");
            if (!onTrigger.isnil()) {
                onTrigger.call(LuaValue.valueOf(triggerId), arg);
            }
        } catch (Exception e) {
            LOG.error("Error firing Lua trigger: {}", triggerId, e);
        }
    }

    /**
     * Resets the Lua engine state, clearing all globals and scripts.
     */
    public void reset() {
        if (initialized && globals != null) {
            // Clear all Lua globals by re-creating the standard globals
            globals = JsePlatform.standardGlobals();
        }
        gameState = null;
        entityManager = null;
        scriptBindings = null;
        initialized = false;
        // Also reset the static GameAPI state to prevent stale references
        GameAPI.reset();
        LOG.debug("LuaEngine reset");
    }

    /**
     * Ensures the engine is initialized before use.
     *
     * @throws IllegalStateException if not initialized
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("LuaEngine not initialized. Call initialize() first.");
        }
    }

    /**
     * Converts a Java object to a LuaValue.
     *
     * @param value Java object
     * @return corresponding LuaValue
     */
    static LuaValue javaToLua(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof LuaValue lv) return lv;
        if (value instanceof Boolean b) return LuaValue.valueOf(b);
        if (value instanceof Integer i) return LuaValue.valueOf(i);
        if (value instanceof Long l) return LuaValue.valueOf(l);
        if (value instanceof Double d) return LuaValue.valueOf(d);
        if (value instanceof Float f) return LuaValue.valueOf(f.doubleValue());
        if (value instanceof String s) return LuaValue.valueOf(s);
        return LuaValue.NIL;
    }

    /**
     * Converts a LuaValue to a Java object.
     *
     * @param value Lua value
     * @return corresponding Java object
     */
    static Object luaToJava(LuaValue value) {
        if (value == null || value.isnil()) return null;
        if (value.isboolean()) return value.toboolean();
        if (value.isint()) return value.toint();
        if (value.islong()) return value.tolong();
        if (value.isnumber()) return value.todouble();
        if (value.isstring()) return value.tojstring();
        return value;
    }
}
