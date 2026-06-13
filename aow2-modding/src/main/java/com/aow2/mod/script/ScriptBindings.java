package com.aow2.mod.script;

import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LibFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds Java methods from {@link GameAPI} to Lua globals using LuaJ.
 * Creates an "aow2" global table in Lua with all game API functions.
 * <p>
 * Usage in Lua:
 * <pre>
 * aow2.spawnUnit("confederation", "CONFED_INFANTRY", 10, 20)
 * aow2.destroyUnit(5)
 * aow2.showMessage("Hello from mod!")
 * aow2.setTimer(10, "onTimerExpired")
 * aow2.onUnitKilled("onUnitDeath")
 * local count = aow2.getUnitCount("resistance")
 * local tick = aow2.getTick()
 * </pre>
 * <p>
 * REF: phases.md Phase 10 - script bindings
 * REF: tech_stack.md - LuaJ 3.x for pure Java Lua interpretation
 */
public final class ScriptBindings {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptBindings.class);

    /** The Lua globals to bind into. */
    private final Globals globals;

    /** Game state reference. */
    private final GameState gameState;

    /** Entity manager reference. */
    private final EntityManager entityManager;

    /**
     * Constructs ScriptBindings.
     *
     * @param globals       Lua globals
     * @param gameState     game state
     * @param entityManager entity manager
     */
    public ScriptBindings(Globals globals, GameState gameState, EntityManager entityManager) {
        this.globals = globals;
        this.gameState = gameState;
        this.entityManager = entityManager;
    }

    /**
     * Binds all game API functions to the "aow2" Lua global table.
     */
    public void bindAll() {
        // Initialize GameAPI with references
        GameAPI.initialize(gameState, entityManager);

        // Create the aow2 table
        LuaTable aow2 = new LuaTable();

        // Spawn/Destroy
        aow2.set("spawnUnit", new SpawnUnitFunction());
        aow2.set("destroyUnit", new DestroyUnitFunction());

        // Objectives
        aow2.set("getObjective", new GetObjectiveFunction());
        aow2.set("setObjective", new SetObjectiveFunction());

        // Messages
        aow2.set("showMessage", new ShowMessageFunction());

        // Timers
        aow2.set("setTimer", new SetTimerFunction());

        // Event hooks
        aow2.set("onUnitKilled", new OnUnitKilledFunction());
        aow2.set("onBuildingDestroyed", new OnBuildingDestroyedFunction());
        aow2.set("onAreaEntered", new OnAreaEnteredFunction());

        // Queries
        aow2.set("getUnitCount", new GetUnitCountFunction());
        aow2.set("getBuildingCount", new GetBuildingCountFunction());
        aow2.set("getCredits", new GetCreditsFunction());
        aow2.set("getTick", new GetTickFunction());

        // Register the aow2 table as a global
        globals.set("aow2", aow2);

        LOG.info("Bound {} game API functions to Lua 'aow2' table", aow2.keyCount());
    }

    // --- Lua Function Implementations ---

    /** aow2.spawnUnit(faction, unitType, x, y) -> unitId */
    static final class SpawnUnitFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue faction, LuaValue unitType, LuaValue x, LuaValue y) {
            int id = GameAPI.spawnUnit(faction.tojstring(), unitType.tojstring(), x.toint(), y.toint());
            return LuaValue.valueOf(id);
        }
    }

    /** aow2.destroyUnit(unitId) */
    static final class DestroyUnitFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue unitId) {
            GameAPI.destroyUnit(unitId.toint());
            return LuaValue.NIL;
        }
    }

    /** aow2.getObjective(name) -> status */
    static final class GetObjectiveFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue name) {
            return LuaValue.valueOf(GameAPI.getObjective(name.tojstring()));
        }
    }

    /** aow2.setObjective(name, status) */
    static final class SetObjectiveFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue name, LuaValue status) {
            GameAPI.setObjective(name.tojstring(), status.tojstring());
            return LuaValue.NIL;
        }
    }

    /** aow2.showMessage(text) */
    static final class ShowMessageFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue text) {
            GameAPI.showMessage(text.tojstring());
            return LuaValue.NIL;
        }
    }

    /** aow2.setTimer(seconds, callbackName) */
    static final class SetTimerFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue seconds, LuaValue callbackName) {
            GameAPI.setTimer(seconds.toint(), callbackName.tojstring());
            return LuaValue.NIL;
        }
    }

    /** aow2.onUnitKilled(callbackName) */
    static final class OnUnitKilledFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue callbackName) {
            GameAPI.onUnitKilled(callbackName.tojstring());
            return LuaValue.NIL;
        }
    }

    /** aow2.onBuildingDestroyed(callbackName) */
    static final class OnBuildingDestroyedFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue callbackName) {
            GameAPI.onBuildingDestroyed(callbackName.tojstring());
            return LuaValue.NIL;
        }
    }

    /** aow2.onAreaEntered(x, y, radius, callbackName) */
    static final class OnAreaEnteredFunction extends LibFunction {
        @Override
        public Varargs invoke(Varargs args) {
            GameAPI.onAreaEntered(args.arg(1).toint(), args.arg(2).toint(),
                args.arg(3).toint(), args.arg(4).tojstring());
            return LuaValue.NIL;
        }
    }

    /** aow2.getUnitCount(faction) -> count */
    static final class GetUnitCountFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue faction) {
            return LuaValue.valueOf(GameAPI.getUnitCount(faction.tojstring()));
        }
    }

    /** aow2.getBuildingCount(faction) -> count */
    static final class GetBuildingCountFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue faction) {
            return LuaValue.valueOf(GameAPI.getBuildingCount(faction.tojstring()));
        }
    }

    /** aow2.getCredits(faction) -> credits */
    static final class GetCreditsFunction extends LibFunction {
        @Override
        public LuaValue call(LuaValue faction) {
            return LuaValue.valueOf(GameAPI.getCredits(faction.tojstring()));
        }
    }

    /** aow2.getTick() -> tick */
    static final class GetTickFunction extends LibFunction {
        @Override
        public LuaValue call() {
            return LuaValue.valueOf(GameAPI.getTick());
        }
    }
}
