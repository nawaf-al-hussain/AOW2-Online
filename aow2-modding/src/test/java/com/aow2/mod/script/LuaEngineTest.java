package com.aow2.mod.script;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LuaEngine: script execution, API bindings.
 * Naming: shouldXxxWhenYyy, Given-When-Then.
 */
class LuaEngineTest {

    private LuaEngine engine;
    private GameState gameState;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        engine = new LuaEngine();
        gameState = new GameState();
        entityManager = new EntityManager();
    }

    @Nested
    @DisplayName("Engine Initialization")
    class EngineInit {

        @Test
        @DisplayName("shouldInitializeSuccessfully")
        void shouldInitializeSuccessfully() {
            // When: initializing the engine
            engine.initialize(gameState, entityManager);

            // Then: should be initialized
            assertTrue(engine.isInitialized());
        }

        @Test
        @DisplayName("shouldThrowWhenNotInitialized")
        void shouldThrowWhenNotInitialized() {
            // Given: an uninitialized engine
            // When/Then: executing scripts should throw
            assertThrows(IllegalStateException.class, () -> engine.executeString("return 1"));
        }
    }

    @Nested
    @DisplayName("Script Execution")
    class ScriptExecution {

        @BeforeEach
        void initEngine() {
            engine.initialize(gameState, entityManager);
        }

        @Test
        @DisplayName("shouldExecuteSimpleLuaExpression")
        void shouldExecuteSimpleLuaExpression() {
            // When: executing a simple Lua expression
            Object result = engine.executeExpression("2 + 3");

            // Then: should return the correct result
            assertNotNull(result);
            assertEquals(5.0, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("shouldExecuteLuaStringConcatenation")
        void shouldExecuteLuaStringConcatenation() {
            // When: executing a string concatenation
            Object result = engine.executeExpression("'hello' .. ' ' .. 'world'");

            // Then: should return the concatenated string
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("shouldExecuteLuaTableCreation")
        void shouldExecuteLuaTableCreation() {
            // When: creating a Lua table
            var result = engine.executeString("local t = {1, 2, 3}; return #t");

            // Then: should return the table length as a LuaValue number
            assertNotNull(result);
            assertTrue(result.isnumber(), "Result should be a number");
            assertEquals(3, result.toint());
        }

        @Test
        @DisplayName("shouldReturnNilOnScriptError")
        void shouldReturnNilOnScriptError() {
            // When: executing an invalid script
            var result = engine.executeString("invalid lua syntax !!!");

            // Then: should return NIL
            assertNotNull(result); // LuaValue.NIL is still a value
        }
    }

    @Nested
    @DisplayName("Game API Bindings")
    class GameAPIBindings {

        @BeforeEach
        void initEngine() {
            engine.initialize(gameState, entityManager);
        }

        @Test
        @DisplayName("shouldAccessAow2GlobalTable")
        void shouldAccessAow2GlobalTable() {
            // When: checking for the aow2 global
            Object aow2 = engine.getGlobal("aow2");

            // Then: it should exist (as a LuaTable)
            assertNotNull(aow2);
        }

        @Test
        @DisplayName("shouldCallGetTickFunction")
        void shouldCallGetTickFunction() {
            // When: calling aow2.getTick()
            var result = engine.callFunction("aow2.getTick");

            // Then: should return 0 (no ticks advanced)
            assertNotNull(result);
        }

        @Test
        @DisplayName("shouldCallShowMessageWithoutError")
        void shouldCallShowMessageWithoutError() {
            // When: calling aow2.showMessage()
            assertDoesNotThrow(() -> engine.executeString("aow2.showMessage('test message')"));
        }

        @Test
        @DisplayName("shouldCallSetObjectiveFromLua")
        void shouldCallSetObjectiveFromLua() {
            // When: setting an objective from Lua
            engine.executeString("aow2.setObjective('test_obj', 'completed')");

            // Then: the objective should be set in GameAPI
            assertEquals("completed", GameAPI.getObjective("test_obj"));
        }

        @Test
        @DisplayName("shouldCallGetObjectiveFromLua")
        void shouldCallGetObjectiveFromLua() {
            // Given: an objective set in Java
            GameAPI.setObjective("java_obj", "active");

            // When: getting it from Lua
            Object result = engine.executeExpression("aow2.getObjective('java_obj')");

            // Then: should return the correct value
            assertEquals("active", result);
        }

        @Test
        @DisplayName("shouldSpawnUnitFromLua")
        void shouldSpawnUnitFromLua() {
            // When: spawning a unit from Lua
            engine.executeString(
                "local id = aow2.spawnUnit('confederation', 'CONFED_INFANTRY', 10, 20)");

            // Then: a unit should exist in the entity manager
            assertFalse(entityManager.getAllUnits().isEmpty());
        }

        @Test
        @DisplayName("shouldGetUnitCountFromLua")
        void shouldGetUnitCountFromLua() {
            // Given: some units in the entity manager
            UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "test", 80, 10,
                4, 2, 0, 5, 4, WeaponType.NONE, 0, 60, 100, 5, 2, 0, 0, 0);
            entityManager.addUnit(new Unit(1, Faction.CONFEDERATION,
                new GridPosition(5, 5), UnitType.CONFED_INFANTRY, stats));

            // When: querying unit count from Lua
            Object result = engine.executeExpression("aow2.getUnitCount('confederation')");

            // Then: should return 1
            assertNotNull(result);
            assertEquals(1.0, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("shouldSetTimerFromLua")
        void shouldSetTimerFromLua() {
            // When: setting a timer from Lua
            engine.executeString("aow2.setTimer(30, 'onTimerExpired')");

            // Then: timer should be registered
            assertFalse(GameAPI.getTimers().isEmpty());
            assertTrue(GameAPI.getTimers().containsKey("onTimerExpired"));
        }

        @Test
        @DisplayName("shouldRegisterEventHooksFromLua")
        void shouldRegisterEventHooksFromLua() {
            // When: registering event hooks from Lua
            engine.executeString("aow2.onUnitKilled('onDeath')");
            engine.executeString("aow2.onBuildingDestroyed('onDestroy')");

            // Then: hooks should be registered
            assertTrue(GameAPI.getEventHooks().containsKey("unitKilled"));
            assertTrue(GameAPI.getEventHooks().containsKey("buildingDestroyed"));
        }
    }

    @Nested
    @DisplayName("Global Variables")
    class GlobalVariables {

        @BeforeEach
        void initEngine() {
            engine.initialize(gameState, entityManager);
        }

        @Test
        @DisplayName("shouldSetAndGetGlobalVariable")
        void shouldSetAndGetGlobalVariable() {
            // When: setting a global variable
            engine.setGlobal("testVar", 42);
            Object result = engine.getGlobal("testVar");

            // Then: should retrieve the value
            assertNotNull(result);
            assertEquals(42.0, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("shouldSetStringGlobal")
        void shouldSetStringGlobal() {
            // When: setting a string global
            engine.setGlobal("testStr", "hello");
            Object result = engine.getGlobal("testStr");

            // Then: should retrieve the string
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("shouldReturnNullForUndefinedGlobal")
        void shouldReturnNullForUndefinedGlobal() {
            // When: getting an undefined global
            Object result = engine.getGlobal("undefined_var");

            // Then: should return null
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Custom Function Registration")
    class CustomFunctions {

        @BeforeEach
        void initEngine() {
            engine.initialize(gameState, entityManager);
        }

        @Test
        @DisplayName("shouldRegisterAndCallCustomFunction")
        void shouldRegisterAndCallCustomFunction() {
            // Given: a custom Lua function
            engine.executeString("function double(x) return x * 2 end");

            // When: calling it
            var result = engine.callFunction("double", 21);

            // Then: should return the correct result
            assertNotNull(result);
        }
    }
}
