package com.aow2.core.engine;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.combat.ProjectileSystem;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.movement.CollisionSystem;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.movement.PathfindingSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.FogOfWarSystem;
import com.aow2.core.world.GameMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the TickManager.
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Game Loop
 */
class TickManagerTest {

    private TickManager tickManager;
    private GameState state;
    private EntityManager entities;
    private GameMap map;
    private MovementSystem movement;
    private CombatSystem combat;
    private EconomySystem economy;
    private ProductionSystem production;
    private ResearchSystem research;
    private BuildingPlacementSystem placement;
    private PathfindingSystem pathfinding;
    private ProjectileSystem projectiles;

    @BeforeEach
    void setUp() {
        tickManager = new TickManager();
        state = new GameState();
        entities = new EntityManager();
        map = new GameMap(16, 16);

        pathfinding = new PathfindingSystem();
        CollisionSystem collision = new CollisionSystem();
        movement = new MovementSystem(pathfinding, collision);
        combat = new CombatSystem(state, entities);
        economy = new EconomySystem(new ResourceGenerator());
        production = new ProductionSystem();
        research = new ResearchSystem();
        placement = new BuildingPlacementSystem();
        projectiles = new ProjectileSystem();
    }

    @Test
    @DisplayName("processTick advances the game tick")
    void processTickAdvancesTick() {
        long initialTick = state.currentTick();
        tickManager.processTick(state, entities, map, movement, combat,
            economy, production, research, placement, pathfinding, projectiles);
        assertEquals(initialTick + 1, state.currentTick());
    }

    @Test
    @DisplayName("Multiple ticks advance correctly")
    void multipleTicksAdvanceCorrectly() {
        for (int i = 0; i < 10; i++) {
            tickManager.processTick(state, entities, map, movement, combat,
                economy, production, research, placement, pathfinding, projectiles);
        }
        assertEquals(10, state.currentTick());
    }

    @Test
    @DisplayName("Enqueued commands are processed during tick")
    void enqueuedCommandsAreProcessed() {
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        CommandType.Stop stop = new CommandType.Stop(0, 0, new int[]{1});
        unit.setAttackState(3); // Set attack state so stop can clear it

        tickManager.enqueueCommand(stop);
        assertEquals(1, tickManager.pendingCommandCount());

        tickManager.processTick(state, entities, map, movement, combat,
            economy, production, research, placement, pathfinding, projectiles);

        assertEquals(0, tickManager.pendingCommandCount());
        assertEquals(0, unit.getAttackState());
    }

    @Test
    @DisplayName("Fog of war is updated when set")
    void fogOfWarIsUpdatedWhenSet() {
        FogOfWarSystem fogOfWar = new FogOfWarSystem();
        fogOfWar.initialize(map);
        tickManager.setFogOfWar(fogOfWar);

        // Add a unit so there's something to reveal
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(8, 8),
            UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        // Before tick: tile should be UNEXPLORED
        assertEquals(FogOfWarSystem.TileVisibility.UNEXPLORED,
            fogOfWar.getVisibility(0, new GridPosition(8, 8)));

        tickManager.processTick(state, entities, map, movement, combat,
            economy, production, research, placement, pathfinding, projectiles);

        // After tick: tile should be VISIBLE
        assertEquals(FogOfWarSystem.TileVisibility.VISIBLE,
            fogOfWar.getVisibility(0, new GridPosition(8, 8)));
    }

    @Test
    @DisplayName("Dead entities are removed after tick")
    void deadEntitiesAreRemoved() {
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        assertEquals(1, entities.unitCount());

        // Kill the unit
        unit.takeDamage(9999);
        assertFalse(unit.isAlive());

        // Process tick to remove dead entities
        tickManager.processTick(state, entities, map, movement, combat,
            economy, production, research, placement, pathfinding, projectiles);

        assertEquals(0, entities.unitCount());
    }

    @Test
    @DisplayName("Clear commands removes all pending commands")
    void clearCommandsRemovesAll() {
        tickManager.enqueueCommand(new CommandType.Stop(0, 0, new int[]{1}));
        tickManager.enqueueCommand(new CommandType.Stop(0, 0, new int[]{2}));
        assertEquals(2, tickManager.pendingCommandCount());

        tickManager.clearCommands();
        assertEquals(0, tickManager.pendingCommandCount());
    }

    @Test
    @DisplayName("Process tick with no entities runs without error")
    void processTickWithNoEntities() {
        assertDoesNotThrow(() -> tickManager.processTick(state, entities, map,
            movement, combat, economy, production, research, placement, pathfinding, projectiles));
        assertEquals(1, state.currentTick());
    }
}
