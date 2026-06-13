package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MineTest {

    /**
     * Creates a Scorpio mine stat block.
     * REF: complete_unit_stats.json — Mine Scorpio: hp=1, damage=25
     */
    private UnitStats createScorpioStats() {
        return new UnitStats(
            UnitType.CONFED_MINE_SCORPIO,
            "Mine Scorpio",
            1,      // hp
            25,     // damage
            0,      // speed
            0,      // armor
            0,      // attackBonus
            1,      // sightRange
            0,      // attackRange
            WeaponType.NONE, // weaponType
            1,      // attackSpeed
            5,      // buildTime
            15,     // costCredits
            0,      // rewardCredits
            0,      // extendedArmor
            255,    // siegeTargets
            0,      // upgradeLevel
            -1      // availabilityFlag
        );
    }

    /**
     * Creates a Frog mine stat block.
     * REF: complete_unit_stats.json — Mine Frog: hp=1, damage=50
     */
    private UnitStats createFrogStats() {
        return new UnitStats(
            UnitType.CONFED_MINE_FROG,
            "Mine Frog",
            1,      // hp
            50,     // damage
            0,      // speed
            0,      // armor
            0,      // attackBonus
            1,      // sightRange
            0,      // attackRange
            WeaponType.NONE, // weaponType
            1,      // attackSpeed
            5,      // buildTime
            20,     // costCredits
            0,      // rewardCredits
            0,      // extendedArmor
            255,    // siegeTargets
            0,      // upgradeLevel
            -1      // availabilityFlag
        );
    }

    @Nested
    @DisplayName("Mine Creation")
    class Creation {

        @Test
        @DisplayName("Should create mine with correct stats")
        void shouldCreateMineWithStats() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            assertEquals(1, mine.getId());
            assertEquals(Faction.CONFEDERATION, mine.getFaction());
            assertEquals(new GridPosition(10, 20), mine.getPosition());
            assertEquals(UnitType.CONFED_MINE_SCORPIO, mine.getMineType());
            assertEquals(stats, mine.getStats());
            assertEquals(2, mine.getTriggerRadius());
            assertFalse(mine.isArmed());
            assertFalse(mine.isTriggered());
            assertTrue(mine.isAlive());
        }

        @Test
        @DisplayName("Should start unarmed and untriggered")
        void shouldStartUnarmedAndUntriggered() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            assertFalse(mine.isArmed());
            assertFalse(mine.isTriggered());
        }
    }

    @Nested
    @DisplayName("Arm and Disarm")
    class ArmDisarm {

        @Test
        @DisplayName("Should arm a mine")
        void shouldArmMine() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            assertTrue(mine.isArmed());
        }

        @Test
        @DisplayName("Should disarm a mine")
        void shouldDisarmMine() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            assertTrue(mine.isArmed());

            mine.disarm();
            assertFalse(mine.isArmed());
        }

        @Test
        @DisplayName("Should not arm a dead mine")
        void shouldNotArmDeadMine() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.takeDamage(100); // kill the mine
            mine.arm();
            assertFalse(mine.isArmed());
        }
    }

    @Nested
    @DisplayName("Trigger Check")
    class TriggerCheck {

        @Test
        @DisplayName("Should not trigger when unarmed")
        void shouldNotTriggerWhenUnarmed() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            // Enemy right on top of mine
            assertFalse(mine.checkTrigger(new GridPosition(10, 20)));
        }

        @Test
        @DisplayName("Should trigger when armed and enemy within radius")
        void shouldTriggerWhenArmedAndEnemyWithinRadius() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();

            // Enemy 1 tile away (distance = 1.0, radius = 2)
            assertTrue(mine.checkTrigger(new GridPosition(11, 20)));
            assertTrue(mine.checkTrigger(new GridPosition(10, 21)));

            // Enemy at diagonal (distance = 1.414, radius = 2)
            assertTrue(mine.checkTrigger(new GridPosition(11, 21)));
        }

        @Test
        @DisplayName("Should not trigger when enemy outside radius")
        void shouldNotTriggerWhenEnemyOutsideRadius() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();

            // Enemy 3 tiles away (distance = 3.0, radius = 2)
            assertFalse(mine.checkTrigger(new GridPosition(13, 20)));
        }

        @Test
        @DisplayName("Should not trigger when already triggered")
        void shouldNotTriggerWhenAlreadyTriggered() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            mine.detonate();

            // Enemy right on top, but mine already triggered
            assertFalse(mine.checkTrigger(new GridPosition(10, 20)));
        }

        @Test
        @DisplayName("Should not trigger when mine is dead")
        void shouldNotTriggerWhenDead() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            mine.takeDamage(100);

            assertFalse(mine.checkTrigger(new GridPosition(10, 20)));
        }
    }

    @Nested
    @DisplayName("Detonation")
    class Detonation {

        @Test
        @DisplayName("Should detonate and return damage value")
        void shouldDetonateAndReturnDamage() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            int damage = mine.detonate();

            assertEquals(25, damage);
            assertTrue(mine.isTriggered());
            assertFalse(mine.isAlive());
            assertEquals(-1, mine.getHp());
        }

        @Test
        @DisplayName("Should detonate Frog mine for higher damage")
        void shouldDetonateFrogForHigherDamage() {
            UnitStats stats = createFrogStats();
            Mine mine = new Mine(2, Faction.CONFEDERATION, new GridPosition(5, 5),
                UnitType.CONFED_MINE_FROG, stats, 1);

            mine.arm();
            int damage = mine.detonate();

            assertEquals(50, damage);
        }

        @Test
        @DisplayName("Should return 0 damage when detonating already triggered mine")
        void shouldReturnZeroWhenAlreadyTriggered() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            mine.detonate();
            int secondDetonation = mine.detonate();

            assertEquals(0, secondDetonation);
        }

        @Test
        @DisplayName("Should return 0 damage when detonating dead mine")
        void shouldReturnZeroWhenDead() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            mine.takeDamage(100);
            int damage = mine.detonate();

            assertEquals(0, damage);
        }

        @Test
        @DisplayName("Should disarm after detonation")
        void shouldDisarmAfterDetonation() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            mine.arm();
            mine.detonate();

            assertFalse(mine.isArmed());
            assertTrue(mine.isTriggered());
        }
    }

    @Nested
    @DisplayName("Trigger Radius")
    class TriggerRadius {

        @Test
        @DisplayName("Should set and get trigger radius")
        void shouldSetAndGetTriggerRadius() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            assertEquals(2, mine.getTriggerRadius());

            mine.setTriggerRadius(5);
            assertEquals(5, mine.getTriggerRadius());
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("Should include key fields in toString")
        void shouldIncludeKeyFields() {
            UnitStats stats = createScorpioStats();
            Mine mine = new Mine(1, Faction.CONFEDERATION, new GridPosition(10, 20),
                UnitType.CONFED_MINE_SCORPIO, stats, 2);

            String str = mine.toString();
            assertTrue(str.contains("Mine"));
            assertTrue(str.contains("SCORPIO"));
            assertTrue(str.contains("armed=false"));
            assertTrue(str.contains("triggered=false"));
        }
    }
}
