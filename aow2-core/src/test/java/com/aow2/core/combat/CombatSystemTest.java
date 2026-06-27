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
    private UnitStats riflemanStats;

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        entityManager = new EntityManager();
        combatSystem = new CombatSystem(gameState, entityManager);

        infantryStats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5,
            0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        // FIX (CI verification): Use BULLET weapon type for direct damage tests.
        // Ranged weapons (MACHINE_GUN, ROCKET, etc.) spawn projectiles via
        // ProjectileSystem instead of applying instant damage, so performAttack()
        // for ranged units does not directly reduce the target's HP.
        riflemanStats = new UnitStats(UnitType.CONFED_INFANTRY, "Rifleman", 40, 6, 5, 5,
            0, 4, 6, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
    }

    @Test
    @DisplayName("Direct attack applies damage")
    void shouldPerformDirectAttack() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, riflemanStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        combatSystem.performAttack(attacker, target);
        // weaponDamage=6, targetArmor=5: raw=6*(10-5)/10=3, clamped=min(3,6-5)=1
        assertEquals(39, target.getHp());
        assertTrue(target.isAlive());
    }

    @Test
    @DisplayName("Unit killed when HP drops to 0, hp set to -1")
    void shouldKillUnitWhenHpDropsToZero() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, riflemanStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        // Each hit deals 1 damage (weaponDamage=6, targetArmor=5: clamped=min(3,1)=1)
        // Need 40 hits to reduce 40 HP to 0 → death marker sets hp=-1
        for (int i = 0; i < 40; i++) {
            attacker.setAttackState(0); // Reset state for each hit
            combatSystem.performAttack(attacker, target);
        }
        assertFalse(target.isAlive());
        assertEquals(-1, target.getHp());
    }

    @Test
    @DisplayName("UnitKilledEvent generated on death")
    void shouldGenerateUnitKilledEvent() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, riflemanStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        int maxIterations = 200;
        while (target.isAlive() && maxIterations-- > 0) {
            attacker.setAttackState(0);
            combatSystem.performAttack(attacker, target);
        }
        assertFalse(target.isAlive(), "Target should be dead after 40 hits");
        var events = gameState.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof UnitKilledEvent));
    }

    @Test
    @DisplayName("DamageAppliedEvent generated on each attack")
    void shouldGenerateDamageAppliedEvent() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, riflemanStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        combatSystem.performAttack(attacker, target);
        var events = gameState.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof DamageAppliedEvent));
    }

    @Test
    @DisplayName("Attacker gains 1 XP per hit")
    void shouldAddExperienceOnHit() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, riflemanStats);
        var target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        combatSystem.performAttack(attacker, target);
        assertEquals(1, attacker.getExperience());
    }

    @Test
    @DisplayName("Infantry deals reduced damage to buildings (50% multiplier)")
    void shouldDealReducedDamageToBuildingsFromInfantry() {
        var attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, infantryStats);
        var buildingStats = new BuildingStats(BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
            4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, 0, WeaponType.NONE, List.of(300, 200, 200));
        var building = new Building(1, Faction.CONFEDERATION, new GridPosition(11, 10),
            BuildingType.CONFED_COMMAND_CENTRE, buildingStats);
        combatSystem.performAttackOnBuilding(attacker, building);
        // Infantry damage=2, Building armor=0, multiplier=0.5: 2*(10-0)/10=2, 2*0.5=1
        assertEquals(119, building.getHp());
    }
}
