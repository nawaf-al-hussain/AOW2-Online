package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnitTest {

    /**
     * Creates a RE Infantry stat block.
     * REF: complete_unit_stats.json — Infantry: hp=40, damage=5, armor=5, cost=10
     */
    private UnitStats createInfantryStats() {
        return new UnitStats(
            UnitType.REBEL_INFANTRY,
            "Resistance Infantry",
            40,     // hp
            5,      // damage
            4,      // speed
            5,      // armor
            0,      // attackBonus
            8,      // sightRange
            5,      // attackRange
            WeaponType.BULLET, // weaponType
            5,      // attackSpeed
            30,     // buildTime
            10,     // costCredits
            5,      // rewardCredits
            10,     // extendedArmor
            0,      // siegeTargets
            0,      // upgradeLevel
            1       // availabilityFlag
        );
    }

    /**
     * Creates a Confederation Fortress stat block.
     * REF: complete_unit_stats.json — Fortress: hp=200, damage=30, armor=15
     */
    private UnitStats createFortressStats() {
        return new UnitStats(
            UnitType.CONFED_FORTRESS,
            "AV-40 Fortress",
            200,    // hp
            30,     // damage
            2,      // speed
            15,     // armor
            5,      // attackBonus
            10,     // sightRange
            7,      // attackRange
            WeaponType.ARTILLERY, // weaponType
            10,     // attackSpeed
            120,    // buildTime
            60,     // costCredits
            30,     // rewardCredits
            20,     // extendedArmor
            1,      // siegeTargets
            0,      // upgradeLevel
            1       // availabilityFlag
        );
    }

    @Nested
    @DisplayName("Unit Creation")
    class Creation {

        @Test
        @DisplayName("Should create unit with correct stats from RE data")
        void shouldCreateUnitWithStats() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 10), UnitType.REBEL_INFANTRY, stats);

            assertEquals(1, unit.getId());
            assertEquals(Faction.RESISTANCE, unit.getFaction());
            assertEquals(new GridPosition(5, 10), unit.getPosition());
            assertEquals(40, unit.getHp());       // hp from stats
            assertEquals(40, unit.getMaxHp());     // maxHp = stats.hp
            assertEquals(UnitType.REBEL_INFANTRY, unit.getUnitType());
            assertTrue(unit.isAlive());
        }

        @Test
        @DisplayName("Should initialize combat fields to zero/null")
        void shouldInitializeCombatFields() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            assertEquals(0, unit.getAttackCooldown());
            assertEquals(0, unit.getAttackState());
            assertEquals(0, unit.getStuckCounter());
            assertNull(unit.getTargetPosition());
            assertNull(unit.getTargetUnitRef());
            assertEquals(0, unit.getAttackCycle());
            assertEquals(0, unit.getWeaponCooldown());
            assertEquals(0, unit.getRank());
            assertEquals(0, unit.getExperience());
        }
    }

    @Nested
    @DisplayName("Take Damage")
    class TakeDamage {

        @Test
        @DisplayName("Should reduce hp by damage amount")
        void shouldReduceHp() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(10);
            assertEquals(30, unit.getHp()); // 40 - 10
            assertTrue(unit.isAlive());
        }

        @Test
        @DisplayName("Should set hp to -1 on death")
        void shouldSetHpToMinusOneOnDeath() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(50); // 40 hp - 50 = -10 -> set to -1
            assertEquals(-1, unit.getHp());
            assertFalse(unit.isAlive());
        }

        @Test
        @DisplayName("Should set hp to -1 on exact lethal damage")
        void shouldSetHpToMinusOneOnExactLethal() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(40); // exactly 0 -> set to -1
            assertEquals(-1, unit.getHp());
            assertFalse(unit.isAlive());
        }

        @Test
        @DisplayName("Should not take damage when already dead")
        void shouldNotTakeDamageWhenDead() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(100); // kill
            assertEquals(-1, unit.getHp());

            unit.takeDamage(10); // should be ignored
            assertEquals(-1, unit.getHp());
        }

        @Test
        @DisplayName("Should handle multiple damage hits")
        void shouldHandleMultipleDamage() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(15);
            assertEquals(25, unit.getHp());

            unit.takeDamage(20);
            assertEquals(5, unit.getHp());

            unit.takeDamage(5);
            assertEquals(-1, unit.getHp()); // killed on 3rd hit
        }
    }

    @Nested
    @DisplayName("Heal")
    class Heal {

        @Test
        @DisplayName("Should heal damaged unit")
        void shouldHealDamagedUnit() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(20); // hp = 20
            unit.heal(10);
            assertEquals(30, unit.getHp());
        }

        @Test
        @DisplayName("Should cap heal at maxHp")
        void shouldCapHealAtMaxHp() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(5); // hp = 35
            unit.heal(50);     // would be 85, capped to 40
            assertEquals(40, unit.getHp());
        }

        @Test
        @DisplayName("Should not heal dead unit")
        void shouldNotHealDeadUnit() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(100); // kill
            assertEquals(-1, unit.getHp());

            unit.heal(50);
            assertEquals(-1, unit.getHp()); // still dead
        }
    }

    @Nested
    @DisplayName("Category Checks")
    class CategoryChecks {

        @Test
        @DisplayName("Infantry unit should report isInfantry=true")
        void infantryCategory() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            assertTrue(unit.isInfantry());
            assertFalse(unit.isVehicle());
            assertFalse(unit.isMine());
        }

        @Test
        @DisplayName("Vehicle unit should report isVehicle=true and isLargeUnit=true for Fortress")
        void vehicleCategory() {
            UnitStats stats = createFortressStats();
            Unit unit = new Unit(2, Faction.CONFEDERATION, new GridPosition(0, 0), UnitType.CONFED_FORTRESS, stats);

            assertFalse(unit.isInfantry());
            assertTrue(unit.isVehicle());
            assertFalse(unit.isMine());
            assertTrue(unit.isLargeUnit()); // Fortress is a 2-cell unit
        }
    }

    @Nested
    @DisplayName("Experience and Ranking")
    class ExperienceRanking {

        @Test
        @DisplayName("Should accumulate experience")
        void shouldAccumulateExperience() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.addExperience(10);
            assertEquals(10, unit.getExperience());
            assertEquals(0, unit.getRank()); // not enough for rank 1 yet
        }

        @Test
        @DisplayName("Should rank up at threshold 20")
        void shouldRankUpAt20() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.addExperience(20);
            assertEquals(20, unit.getExperience());
            assertEquals(1, unit.getRank()); // rank 1 at 20 xp
        }

        @Test
        @DisplayName("Should rank up at threshold 35")
        void shouldRankUpAt35() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.addExperience(35);
            assertEquals(35, unit.getExperience());
            assertEquals(2, unit.getRank()); // rank 2 at 35 xp
        }

        @Test
        @DisplayName("Should rank up at threshold 50")
        void shouldRankUpAt50() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.addExperience(50);
            assertEquals(50, unit.getExperience());
            assertEquals(3, unit.getRank()); // max rank at 50 xp
        }

        @Test
        @DisplayName("Should rank up incrementally through multiple addExperience calls")
        void shouldRankUpIncrementally() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.addExperience(15);
            assertEquals(0, unit.getRank()); // 15 < 20

            unit.addExperience(10);
            assertEquals(25, unit.getExperience());
            assertEquals(1, unit.getRank()); // 25 >= 20

            unit.addExperience(15);
            assertEquals(40, unit.getExperience());
            assertEquals(2, unit.getRank()); // 40 >= 35

            unit.addExperience(15);
            assertEquals(55, unit.getExperience());
            assertEquals(3, unit.getRank()); // 55 >= 50
        }

        @Test
        @DisplayName("Should not add experience to dead units")
        void shouldNotAddExperienceToDead() {
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);

            unit.takeDamage(100); // kill
            int rank = unit.addExperience(50);

            assertEquals(0, unit.getExperience()); // no xp added
            assertEquals(0, rank);
        }
    }
}
