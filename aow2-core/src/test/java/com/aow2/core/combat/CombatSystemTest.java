package com.aow2.core.combat;

import com.aow2.common.event.*;
import com.aow2.common.model.*;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatSystemTest {

    private GameState gameState;
    private EntityManager entityManager;
    private CombatSystem combatSystem;
    private UnitStats infantryStats;
    private UnitStats zeusStats;

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        entityManager = new EntityManager();
        combatSystem = new CombatSystem(gameState, entityManager);

        infantryStats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5,
            0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
        zeusStats = new UnitStats(UnitType.CONFED_ZEUS, "T-22 Zeus", 70, 6, 3, 7, 5,
            0, 2, 6, 14, 30, 300, 8, 255, 0, -1);
    }

    @Test
    @DisplayName("Direct attack applies damage")
    void shouldPerformDirectAttack() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        combatSystem.performAttack(attacker, target);
        assertEquals(37, target.getHp());
        assertTrue(target.isAlive());
    }

    @Test
    @DisplayName("Unit killed when HP drops to 0, hp set to -1")
    void shouldKillUnitWhenHpDropsToZero() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        for (int i = 0; i < 14; i++) {
            combatSystem.performAttack(attacker, target);
        }
        assertFalse(target.isAlive());
        assertEquals(-1, target.getHp());
    }

    @Test
    @DisplayName("UnitKilledEvent generated on death")
    void shouldGenerateUnitKilledEvent() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        while (target.isAlive()) {
            combatSystem.performAttack(attacker, target);
        }
        var events = gameState.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof UnitKilledEvent));
    }

    @Test
    @DisplayName("DamageAppliedEvent generated on each attack")
    void shouldGenerateDamageAppliedEvent() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        combatSystem.performAttack(attacker, target);
        var events = gameState.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof DamageAppliedEvent));
    }

    @Test
    @DisplayName("Attacker gains 1 XP per hit")
    void shouldAddExperienceOnHit() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        combatSystem.performAttack(attacker, target);
        assertEquals(1, attacker.getExperience());
    }

    @Test
    @DisplayName("Infantry deals reduced damage to buildings (50% multiplier)")
    void shouldDealReducedDamageToBuildingsFromInfantry() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, infantryStats);
        var buildingStats = new BuildingStats(BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
            4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, List.of(300, 200, 200));
        var building = new Building(1, Faction.CONFEDERATION, new GridPosition(11, 10),
            BuildingType.CONFED_COMMAND_CENTRE, buildingStats);
        combatSystem.performAttackOnBuilding(attacker, building);
        // Infantry damage=2, Building armor=0, multiplier=0.5: 2*(10-0)/10=2, 2*0.5=1
        assertEquals(119, building.getHp());
    }
}
