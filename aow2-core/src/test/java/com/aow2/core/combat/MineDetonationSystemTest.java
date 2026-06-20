package com.aow2.core.combat;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Mine;
import com.aow2.core.entity.Unit;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MineDetonationSystemTest {

    private EntityManager entityManager;
    private GameState gameState;
    private CombatSystem combatSystem;
    private MineDetonationSystem mineSystem;

    /**
     * Creates a Confederation Infantry stat block.
     */
    private UnitStats createConfedInfantryStats() {
        return new UnitStats(
            UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5,
            0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1
        );
    }

    /**
     * Creates a Resistance Infantry stat block.
     */
    private UnitStats createRebelInfantryStats() {
        return new UnitStats(
            UnitType.REBEL_INFANTRY, "Resistance Infantry", 40, 5, 4, 5,
            0, 8, 5, WeaponType.BULLET, 5, 30, 10, 5, 10, 0, 0, 1
        );
    }

    /**
     * Creates a Zeus vehicle stat block.
     */
    private UnitStats createZeusStats() {
        return new UnitStats(
            UnitType.CONFED_ZEUS, "T-22 Zeus", 70, 6, 7, 5,
            0, 2, 6, WeaponType.MACHINE_GUN, 2, 14, 30, 300, 8, 255, 0, -1
        );
    }

    /**
     * Creates a Coyote vehicle stat block (Resistance).
     */
    private UnitStats createCoyoteStats() {
        return new UnitStats(
            UnitType.REBEL_COYOTE, "Coyote", 50, 5, 6, 3,
            0, 7, 5, WeaponType.BULLET, 4, 20, 20, 200, 5, 255, 0, -1
        );
    }

    /**
     * Creates a Mine Scorpio stat block.
     * REF: complete_unit_stats.json — Mine Scorpio: hp=1, damage=25
     */
    private UnitStats createScorpioStats() {
        return new UnitStats(
            UnitType.CONFED_MINE_SCORPIO, "Mine Scorpio", 1, 25, 0, 0,
            0, 1, 0, WeaponType.NONE, 1, 5, 15, 0, 0, 255, 0, -1
        );
    }

    /**
     * Creates a Mine Frog stat block.
     * REF: complete_unit_stats.json — Mine Frog: hp=1, damage=50
     */
    private UnitStats createFrogStats() {
        return new UnitStats(
            UnitType.CONFED_MINE_FROG, "Mine Frog", 1, 50, 0, 0,
            0, 1, 0, WeaponType.NONE, 1, 5, 20, 0, 0, 255, 0, -1
        );
    }

    /**
     * Creates a Mine Lizard stat block.
     * REF: complete_unit_stats.json — Mine Lizard: hp=1, damage=40
     */
    private UnitStats createLizardStats() {
        return new UnitStats(
            UnitType.CONFED_MINE_LIZARD, "Mine Lizard", 1, 40, 0, 0,
            0, 1, 0, WeaponType.NONE, 1, 5, 25, 0, 0, 255, 0, -1
        );
    }

    /**
     * Creates a simple building stats for testing.
     */
    private BuildingStats createRebelHQStats() {
        return new BuildingStats(
            BuildingType.REBEL_HEADQUARTERS, 120, 22, 7, 7,
            4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, 0, WeaponType.NONE, List.of(300, 200, 200)
        );
    }

    @BeforeEach
    void setUp() {
        entityManager = new EntityManager();
        gameState = new GameState();
        combatSystem = new CombatSystem(gameState, entityManager);
        mineSystem = new MineDetonationSystem(combatSystem);
    }

    @Nested
    @DisplayName("Armed Mine Triggering")
    class ArmedMineTriggering {

        @Test
        @DisplayName("Armed mine triggers when enemy is in range")
        void shouldTriggerWhenEnemyInRange() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place enemy infantry within trigger radius (distance = 1.0, radius = 2)
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemy = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy);

            mineSystem.processTick(entityManager, gameState);

            // Mine should have detonated
            assertTrue(mine.isTriggered());
            assertFalse(mine.isAlive());
            // Enemy should have taken damage (50 damage, armor 5 → nuclear at dist=1)
            assertTrue(enemy.getHp() < 40);
        }

        @Test
        @DisplayName("Armed mine does NOT trigger when no enemy in range")
        void shouldNotTriggerWhenNoEnemyInRange() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place enemy far away (distance = 10, radius = 2)
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemy = new Unit(2, Faction.RESISTANCE, new GridPosition(20, 20),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy);

            mineSystem.processTick(entityManager, gameState);

            // Mine should NOT have detonated
            assertFalse(mine.isTriggered());
            assertTrue(mine.isAlive());
        }
    }

    @Nested
    @DisplayName("Mine Scorpio Anti-Tank")
    class MineScorpio {

        @Test
        @DisplayName("Mine Scorpio triggers on machinery/vehicles")
        void shouldTriggerOnMachinery() {
            UnitStats scorpioStats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_SCORPIO, scorpioStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place enemy vehicle within trigger radius
            UnitStats coyoteStats = createCoyoteStats();
            Unit enemyVehicle = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10),
                UnitType.REBEL_COYOTE, coyoteStats);
            entityManager.addUnit(enemyVehicle);

            mineSystem.processTick(entityManager, gameState);

            assertTrue(mine.isTriggered());
            // Vehicle should have taken damage
            assertTrue(enemyVehicle.getHp() < 50);
        }

        @Test
        @DisplayName("Mine Scorpio ignores infantry")
        void shouldIgnoreInfantry() {
            UnitStats scorpioStats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_SCORPIO, scorpioStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place enemy infantry right on top of the mine
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemyInfantry = new Unit(2, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemyInfantry);

            mineSystem.processTick(entityManager, gameState);

            // Mine should NOT have detonated — Scorpio ignores infantry
            assertFalse(mine.isTriggered());
            assertTrue(mine.isAlive());
            assertEquals(40, enemyInfantry.getHp()); // no damage
        }
    }

    @Nested
    @DisplayName("Mine Frog Area Damage")
    class MineFrogAreaDamage {

        @Test
        @DisplayName("Mine Frog deals area damage to multiple units")
        void shouldDealAreaDamage() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place two enemy units within blast radius (radius = 1)
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemy1 = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            Unit enemy2 = new Unit(3, Faction.RESISTANCE, new GridPosition(10, 11),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy1);
            entityManager.addUnit(enemy2);

            mineSystem.processTick(entityManager, gameState);

            assertTrue(mine.isTriggered());
            // Both enemies should have taken damage
            assertTrue(enemy1.getHp() < 40);
            assertTrue(enemy2.getHp() < 40);
        }

        @Test
        @DisplayName("Mine Frog does NOT damage units outside blast radius")
        void shouldNotDamageOutsideBlastRadius() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place enemy within trigger radius but far from blast radius
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemyInRange = new Unit(2, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemyInRange);

            // Place another enemy well outside blast radius (distance = 5, blast radius = 1)
            Unit enemyFarAway = new Unit(3, Faction.RESISTANCE, new GridPosition(15, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemyFarAway);

            mineSystem.processTick(entityManager, gameState);

            assertTrue(mine.isTriggered());
            // Close enemy should take damage
            assertTrue(enemyInRange.getHp() < 40);
            // Far enemy should NOT take damage
            assertEquals(40, enemyFarAway.getHp());
        }
    }

    @Nested
    @DisplayName("Disarmed and Dead Mines")
    class DisarmedAndDeadMines {

        @Test
        @DisplayName("Disarmed mine doesn't trigger")
        void shouldNotTriggerDisarmedMine() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            // Mine is NOT armed
            entityManager.addMine(mine);

            // Place enemy on top of mine
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemy = new Unit(2, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy);

            mineSystem.processTick(entityManager, gameState);

            assertFalse(mine.isTriggered());
            assertTrue(mine.isAlive());
            assertEquals(40, enemy.getHp()); // no damage
        }

        @Test
        @DisplayName("Dead mine doesn't trigger")
        void shouldNotTriggerDeadMine() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            mine.takeDamage(100); // kill the mine
            entityManager.addMine(mine);

            // Place enemy on top of dead mine
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemy = new Unit(2, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy);

            mineSystem.processTick(entityManager, gameState);

            // Mine was already dead, should not trigger again
            assertEquals(-1, mine.getHp());
            assertEquals(40, enemy.getHp()); // no damage
        }

        @Test
        @DisplayName("Already triggered mine doesn't trigger again")
        void shouldNotTriggerAlreadyTriggeredMine() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place enemy to trigger
            UnitStats rebelStats = createRebelInfantryStats();
            Unit enemy1 = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy1);

            mineSystem.processTick(entityManager, gameState);
            assertTrue(mine.isTriggered());

            // Add another enemy nearby
            Unit enemy2 = new Unit(3, Faction.RESISTANCE, new GridPosition(10, 11),
                UnitType.REBEL_INFANTRY, rebelStats);
            entityManager.addUnit(enemy2);

            // Process another tick — mine already triggered, should not detonate again
            mineSystem.processTick(entityManager, gameState);
            assertEquals(40, enemy2.getHp()); // no damage from re-trigger
        }
    }

    @Nested
    @DisplayName("Friendly Fire")
    class FriendlyFire {

        @Test
        @DisplayName("Mine does not trigger for friendly units")
        void shouldNotTriggerForFriendlyUnits() {
            UnitStats frogStats = createFrogStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_MINE_FROG, frogStats, 2);
            mine.arm();
            entityManager.addMine(mine);

            // Place friendly unit on top of mine (same faction)
            UnitStats confedStats = createConfedInfantryStats();
            Unit friendly = new Unit(2, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, confedStats);
            entityManager.addUnit(friendly);

            mineSystem.processTick(entityManager, gameState);

            assertFalse(mine.isTriggered());
            assertEquals(40, friendly.getHp()); // no damage
        }
    }
}
