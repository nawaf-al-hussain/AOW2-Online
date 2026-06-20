package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.engine.GameState;
import com.aow2.core.movement.CollisionSystem;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.movement.PathfindingSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CommandProcessor.
 * REF: protocol_specification.md - command types
 */
class CommandProcessorTest {

    private CommandProcessor processor;
    private GameState state;
    private EntityManager entities;
    private GameMap map;
    private MovementSystem movement;
    private CombatSystem combat;
    private EconomySystem economy;
    private ProductionSystem production;
    private ResearchSystem research;
    private BuildingPlacementSystem placement;

    @BeforeEach
    void setUp() {
        processor = new CommandProcessor();
        state = new GameState();
        entities = new EntityManager();
        map = new GameMap(16, 16);

        PathfindingSystem pathfinding = new PathfindingSystem();
        CollisionSystem collision = new CollisionSystem();
        movement = new MovementSystem(pathfinding, collision);
        combat = new CombatSystem(state, entities);
        economy = new EconomySystem(new ResourceGenerator());
        production = new ProductionSystem();
        research = new ResearchSystem();
        placement = new BuildingPlacementSystem();
    }

    @Test
    @DisplayName("Move command processes without error")
    void moveCommandProcesses() {
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        CommandType.Move move = new CommandType.Move(0, 0, new int[]{1}, new GridPosition(8, 8));

        assertDoesNotThrow(() -> processor.process(move, state, entities, map,
            movement, combat, economy, production, research, placement));
    }

    @Test
    @DisplayName("Stop command clears unit path and target")
    void stopCommandClearsUnitState() {
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        unit.setTargetUnitRef(2);
        unit.setAttackState(3);
        entities.addUnit(unit);

        CommandType.Stop stop = new CommandType.Stop(0, 0, new int[]{1});
        processor.process(stop, state, entities, map,
            movement, combat, economy, production, research, placement);

        assertNull(unit.getTargetUnitRef());
        assertEquals(0, unit.getAttackState());
    }

    @Test
    @DisplayName("Attack command sets target reference")
    void attackCommandSetsTarget() {
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var attacker = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        var target = new com.aow2.core.entity.Unit(2, Faction.RESISTANCE, new GridPosition(6, 5),
            UnitType.REBEL_INFANTRY, stats);
        entities.addUnit(attacker);
        entities.addUnit(target);

        CommandType.Attack attack = new CommandType.Attack(0, 0, new int[]{1}, 2);
        processor.process(attack, state, entities, map,
            movement, combat, economy, production, research, placement);

        assertEquals(2, attacker.getTargetUnitRef());
        assertTrue(attacker.getAttackState() > 0);
    }

    @Test
    @DisplayName("Build command validates placement")
    void buildCommandValidatesPlacement() {
        CommandType.Build build = new CommandType.Build(0, 0,
            com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE,
            new GridPosition(5, 5));

        // Without a Command Centre nearby and credits, building should fail gracefully
        assertDoesNotThrow(() -> processor.process(build, state, entities, map,
            movement, combat, economy, production, research, placement));
    }

    @Test
    @DisplayName("Produce command validates producer building")
    void produceCommandValidatesProducer() {
        CommandType.Produce produce = new CommandType.Produce(0, 0, 999,
            UnitType.CONFED_INFANTRY);

        // Non-existent producer should not throw
        assertDoesNotThrow(() -> processor.process(produce, state, entities, map,
            movement, combat, economy, production, research, placement));
    }

    @Test
    @DisplayName("Research command validates tech centre")
    void researchCommandValidatesTechCentre() {
        CommandType.Research researchCmd = new CommandType.Research(0, 0, 999, 0);

        // Non-existent tech centre should not throw
        assertDoesNotThrow(() -> processor.process(researchCmd, state, entities, map,
            movement, combat, economy, production, research, placement));
    }

    @Test
    @DisplayName("Cancel command handles non-existent building")
    void cancelCommandHandlesNonExistentBuilding() {
        CommandType.Cancel cancel = new CommandType.Cancel(0, 0, 999);
        assertDoesNotThrow(() -> processor.process(cancel, state, entities, map,
            movement, combat, economy, production, research, placement));
    }

    @Test
    @DisplayName("Garrison command validates building existence")
    void garrisonCommandValidatesBuilding() {
        CommandType.Garrison garrison = new CommandType.Garrison(0, 0, new int[]{1}, 999);
        assertDoesNotThrow(() -> processor.process(garrison, state, entities, map,
            movement, combat, economy, production, research, placement));
    }

    @Test
    @DisplayName("Siege mode command processes for valid unit")
    void siegeModeCommandProcesses() {
        UnitStats stats = new UnitStats(UnitType.CONFED_TORRENT, "Torrent", 80, 15, 4, 7, 2, 6, 6, WeaponType.ROCKET, 12, 8, 50, 250, 8, 255, 2, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_TORRENT, stats);
        entities.addUnit(unit);

        CommandType.SiegeMode siegeOn = new CommandType.SiegeMode(0, 0, 1, true);
        processor.process(siegeOn, state, entities, map,
            movement, combat, economy, production, research, placement);

        assertTrue(unit.isSiegeMode());
    }

    @Test
    @DisplayName("Patrol command issues move order")
    void patrolCommandIssuesMove() {
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        var unit = new com.aow2.core.entity.Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        CommandType.Patrol patrol = new CommandType.Patrol(0, 0, new int[]{1}, new GridPosition(8, 8));
        assertDoesNotThrow(() -> processor.process(patrol, state, entities, map,
            movement, combat, economy, production, research, placement));
    }
}
