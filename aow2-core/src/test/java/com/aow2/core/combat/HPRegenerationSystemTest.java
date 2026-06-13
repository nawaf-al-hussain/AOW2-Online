package com.aow2.core.combat;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.entity.Unit;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HPRegenerationSystemTest {

    private EntityManager entityManager;
    private ResearchSystem researchSystem;
    private HPRegenerationSystem hpRegenSystem;

    /**
     * Creates a Confederation Infantry stat block.
     * REF: complete_unit_stats.json — Infantry: hp=40, damage=2, armor=5
     */
    private UnitStats createConfedInfantryStats() {
        return new UnitStats(
            UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5,
            0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1
        );
    }

    /**
     * Creates a Resistance Infantry stat block.
     * REF: complete_unit_stats.json — Infantry: hp=40, damage=5, armor=5
     */
    private UnitStats createRebelInfantryStats() {
        return new UnitStats(
            UnitType.REBEL_INFANTRY, "Resistance Infantry", 40, 5, 4, 5,
            0, 8, 5, WeaponType.BULLET, 5, 30, 10, 5, 10, 0, 0, 1
        );
    }

    /**
     * Creates a Zeus vehicle stat block for machinery testing.
     * REF: complete_unit_stats.json — Zeus: hp=70, damage=6, armor=5
     */
    private UnitStats createZeusStats() {
        return new UnitStats(
            UnitType.CONFED_ZEUS, "T-22 Zeus", 70, 6, 7, 5,
            0, 2, 6, WeaponType.MACHINE_GUN, 2, 14, 30, 300, 8, 255, 0, -1
        );
    }

    /**
     * Adds a completed research to the ResearchSystem using reflection.
     * This is the simplest way to test research-dependent behavior without
     * needing a full TechTree and EconomySystem setup.
     */
    @SuppressWarnings("unchecked")
    private void addCompletedResearch(ResearchSystem rs, int playerId, int researchId) {
        try {
            java.lang.reflect.Field field = ResearchSystem.class.getDeclaredField("completedResearch");
            field.setAccessible(true);
            Set<Integer>[] sets = (Set<Integer>[]) field.get(rs);
            sets[playerId].add(researchId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add research via reflection", e);
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = new EntityManager();
        researchSystem = new ResearchSystem();
        hpRegenSystem = new HPRegenerationSystem(researchSystem);
    }

    @Nested
    @DisplayName("Infantry HP Regeneration")
    class InfantryRegen {

        @Test
        @DisplayName("Infantry regenerates HP on regen cycle tick (127)")
        void shouldRegenerateOnCycleTick() {
            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            unit.takeDamage(10); // hp = 30
            entityManager.addUnit(unit);

            // Tick 127 is a regen cycle tick
            hpRegenSystem.processTick(entityManager, 127);

            assertEquals(31, unit.getHp()); // 30 + 1 base recovery
        }

        @Test
        @DisplayName("Infantry does NOT regenerate on non-cycle ticks")
        void shouldNotRegenerateOnNonCycleTick() {
            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            unit.takeDamage(10); // hp = 30
            entityManager.addUnit(unit);

            // Tick 50 is NOT a regen cycle tick
            hpRegenSystem.processTick(entityManager, 50);

            assertEquals(30, unit.getHp()); // unchanged
        }

        @Test
        @DisplayName("Infantry with Energy Suit (R0) recovers 3x HP")
        void shouldTripleRecoveryWithEnergySuit() {
            addCompletedResearch(researchSystem, 0, 0); // Confederation player 0, research ID 0

            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            unit.takeDamage(10); // hp = 30
            entityManager.addUnit(unit);

            hpRegenSystem.processTick(entityManager, 127);

            assertEquals(33, unit.getHp()); // 30 + (1 * 3) = 33
        }

        @Test
        @DisplayName("Infantry with First-aid Kit (R9) recovers 3x HP for Resistance")
        void shouldTripleRecoveryWithFirstAidKit() {
            addCompletedResearch(researchSystem, 1, 9); // Resistance player 1, research ID 9

            UnitStats stats = createRebelInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(10, 10),
                UnitType.REBEL_INFANTRY, stats);
            unit.takeDamage(10); // hp = 30
            entityManager.addUnit(unit);

            hpRegenSystem.processTick(entityManager, 127);

            assertEquals(33, unit.getHp()); // 30 + (1 * 3) = 33
        }

        @Test
        @DisplayName("Full-HP infantry doesn't get overhealed")
        void shouldNotOverhealFullHpInfantry() {
            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            // Unit starts at full HP (40)
            entityManager.addUnit(unit);

            hpRegenSystem.processTick(entityManager, 127);

            assertEquals(40, unit.getHp()); // still at max, no overheal
        }

        @Test
        @DisplayName("Dead units don't regenerate")
        void shouldNotRegenerateDeadUnits() {
            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            unit.takeDamage(100); // kills the unit, hp = -1
            entityManager.addUnit(unit);

            hpRegenSystem.processTick(entityManager, 127);

            assertEquals(-1, unit.getHp()); // still dead
        }

        @Test
        @DisplayName("Multiple regen cycles accumulate recovery")
        void shouldAccumulateRecoveryOverMultipleCycles() {
            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            unit.takeDamage(10); // hp = 30
            entityManager.addUnit(unit);

            hpRegenSystem.processTick(entityManager, 127); // +1 → hp = 31
            hpRegenSystem.processTick(entityManager, 254); // +1 → hp = 32

            assertEquals(32, unit.getHp());
        }
    }

    @Nested
    @DisplayName("Machinery Repair")
    class MachineryRepair {

        @Test
        @DisplayName("Machinery does NOT auto-regen in processTick (only at production buildings)")
        void shouldNotAutoRegenMachinery() {
            UnitStats stats = createZeusStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_ZEUS, stats);
            unit.takeDamage(20); // hp = 50
            entityManager.addUnit(unit);

            hpRegenSystem.processTick(entityManager, 127);

            assertEquals(50, unit.getHp()); // unchanged — no auto-regen for machinery
        }

        @Test
        @DisplayName("Machinery can be repaired via repairMachinery method")
        void shouldRepairMachineryViaRepairMethod() {
            UnitStats stats = createZeusStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_ZEUS, stats);
            unit.takeDamage(20); // hp = 50

            hpRegenSystem.repairMachinery(unit, 0);

            assertEquals(52, unit.getHp()); // 50 + 2 base repair
        }

        @Test
        @DisplayName("Machinery repair doesn't overheal")
        void shouldNotOverhealMachinery() {
            UnitStats stats = createZeusStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_ZEUS, stats);
            unit.takeDamage(1); // hp = 69

            hpRegenSystem.repairMachinery(unit, 0);

            assertEquals(70, unit.getHp()); // capped at maxHp
        }

        @Test
        @DisplayName("Dead machinery cannot be repaired")
        void shouldNotRepairDeadMachinery() {
            UnitStats stats = createZeusStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_ZEUS, stats);
            unit.takeDamage(100); // kill, hp = -1

            hpRegenSystem.repairMachinery(unit, 0);

            assertEquals(-1, unit.getHp()); // still dead
        }

        @Test
        @DisplayName("repairMachinery does nothing for infantry units")
        void shouldNotRepairInfantryViaRepairMethod() {
            UnitStats stats = createConfedInfantryStats();
            Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
                UnitType.CONFED_INFANTRY, stats);
            unit.takeDamage(10); // hp = 30

            hpRegenSystem.repairMachinery(unit, 0);

            assertEquals(30, unit.getHp()); // unchanged — repair is only for machinery
        }
    }
}
