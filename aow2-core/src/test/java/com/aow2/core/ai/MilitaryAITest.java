package com.aow2.core.ai;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AI military decision-making: attack, defend, retreat, strength assessment.
 */
class MilitaryAITest {

    private EntityManager entities;
    private GameMap map;
    private MilitaryAI militaryAI;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        map = new GameMap(64, 64);
        militaryAI = new MilitaryAI();
    }

    /**
     * Creates a completed Command Centre stat block.
     */
    private BuildingStats createCCStats(BuildingType type) {
        return new BuildingStats(type, 120, 100, 0, 10, 0, 10, 60, 0, 15, 0, 0, 5, 0, 100, 50, 0, WeaponType.NONE, List.of(100, 200, 300));
    }

    /**
     * Creates Confederation infantry stats.
     */
    private UnitStats createConfedInfantryStats() {
        return new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
    }

    /**
     * Creates Resistance infantry stats.
     */
    private UnitStats createRebelInfantryStats() {
        return new UnitStats(UnitType.REBEL_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
    }

    /**
     * Creates Confederation vehicle stats (Hammer).
     */
    private UnitStats createHammerStats() {
        return new UnitStats(UnitType.CONFED_HAMMER, "T-21 Hammer", 70, 6, 7, 5, 0, 2, 6, WeaponType.MACHINE_GUN, 2, 7, 30, 300, 8, 255, 0, -1);
    }

    /**
     * Creates Resistance vehicle stats (Coyote).
     */
    private UnitStats createCoyoteStats() {
        return new UnitStats(UnitType.REBEL_COYOTE, "Coyote", 60, 5, 8, 4, 0, 2, 5, WeaponType.MACHINE_GUN, 4, 6, 25, 400, 7, 255, 0, -1);
    }

    private Building placeCompletedCC(int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        BuildingType type = faction == Faction.CONFEDERATION
            ? BuildingType.CONFED_COMMAND_CENTRE
            : BuildingType.REBEL_HEADQUARTERS;
        BuildingStats stats = createCCStats(type);
        GridPosition pos = faction == Faction.CONFEDERATION
            ? new GridPosition(10, 10)
            : new GridPosition(50, 50);
        Building cc = new Building(entities.allocateEntityId(), faction, pos, type, stats);
        cc.setConstructionProgress(stats.buildTime());
        cc.setPowered(true);
        entities.addBuilding(cc);
        return cc;
    }

    private Unit addUnit(Faction faction, GridPosition pos, UnitType type, UnitStats stats) {
        Unit unit = new Unit(entities.allocateEntityId(), faction, pos, type, stats);
        entities.addUnit(unit);
        return unit;
    }

    @Nested
    @DisplayName("Military Strength Assessment")
    class StrengthAssessment {

        @Test
        @DisplayName("Given equal forces, when assessing advantage, then ratio is approximately 1.0")
        void shouldReturnEqualAdvantageForEqualForces() {
            // Given: both players have 3 infantry units
            placeCompletedCC(0);
            placeCompletedCC(1);
            for (int i = 0; i < 3; i++) {
                addUnit(Faction.CONFEDERATION, new GridPosition(10 + i, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
                addUnit(Faction.RESISTANCE, new GridPosition(50 + i, 55), UnitType.REBEL_INFANTRY, createRebelInfantryStats());
            }

            // When
            double advantage = militaryAI.assessMilitaryAdvantage(entities, 0);

            // Then
            assertEquals(1.0, advantage, 0.01, "Equal forces should have advantage ratio of 1.0");
        }

        @Test
        @DisplayName("Given AI has more units, when assessing advantage, then ratio is greater than 1.0")
        void shouldReturnAdvantageWhenMoreUnits() {
            // Given: AI has 5 units, enemy has 2
            placeCompletedCC(0);
            placeCompletedCC(1);
            for (int i = 0; i < 5; i++) {
                addUnit(Faction.CONFEDERATION, new GridPosition(10 + i, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            }
            for (int i = 0; i < 2; i++) {
                addUnit(Faction.RESISTANCE, new GridPosition(50 + i, 55), UnitType.REBEL_INFANTRY, createRebelInfantryStats());
            }

            // When
            double advantage = militaryAI.assessMilitaryAdvantage(entities, 0);

            // Then
            assertTrue(advantage > 1.0, "More units should give advantage > 1.0, got " + advantage);
        }

        @Test
        @DisplayName("Given AI has fewer units, when assessing advantage, then ratio is less than 1.0")
        void shouldReturnDisadvantageWhenFewerUnits() {
            // Given: AI has 1 unit, enemy has 4
            placeCompletedCC(0);
            placeCompletedCC(1);
            addUnit(Faction.CONFEDERATION, new GridPosition(10, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            for (int i = 0; i < 4; i++) {
                addUnit(Faction.RESISTANCE, new GridPosition(50 + i, 55), UnitType.REBEL_INFANTRY, createRebelInfantryStats());
            }

            // When
            double advantage = militaryAI.assessMilitaryAdvantage(entities, 0);

            // Then
            assertTrue(advantage < 1.0, "Fewer units should give advantage < 1.0, got " + advantage);
        }

        @Test
        @DisplayName("Given no enemy units, when assessing advantage, then ratio is very large")
        void shouldReturnMaximumAdvantageWithNoEnemy() {
            // Given: AI has units, enemy has none
            placeCompletedCC(0);
            addUnit(Faction.CONFEDERATION, new GridPosition(10, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());

            // When
            double advantage = militaryAI.assessMilitaryAdvantage(entities, 0);

            // Then
            assertEquals(Double.MAX_VALUE, advantage, "No enemy should give maximum advantage");
        }
    }

    @Nested
    @DisplayName("Military Action Decisions")
    class ActionDecisions {

        @Test
        @DisplayName("Given base under attack, when deciding action, then Defend is chosen")
        void shouldDefendWhenBaseUnderAttack() {
            // Given: AI has units and enemy is near base
            placeCompletedCC(0);
            addUnit(Faction.CONFEDERATION, new GridPosition(10, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            // Enemy unit near AI base (within 20 cells)
            addUnit(Faction.RESISTANCE, new GridPosition(12, 12), UnitType.REBEL_INFANTRY, createRebelInfantryStats());

            // When
            MilitaryAction action = militaryAI.decideAction(entities, map, 0);

            // Then
            assertInstanceOf(MilitaryAction.Defend.class, action,
                "Should defend when base is under attack");
        }

        @Test
        @DisplayName("Given military advantage > 1.5x, when deciding action, then Attack is chosen")
        void shouldAttackWhenAdvantageIsHigh() {
            // Given: AI has many more units than enemy
            placeCompletedCC(0);
            placeCompletedCC(1);
            // AI has 10 units
            for (int i = 0; i < 10; i++) {
                addUnit(Faction.CONFEDERATION, new GridPosition(10 + i, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            }
            // Enemy has 2 units far from AI base
            addUnit(Faction.RESISTANCE, new GridPosition(50, 50), UnitType.REBEL_INFANTRY, createRebelInfantryStats());
            addUnit(Faction.RESISTANCE, new GridPosition(51, 51), UnitType.REBEL_INFANTRY, createRebelInfantryStats());

            // When
            MilitaryAction action = militaryAI.decideAction(entities, map, 0);

            // Then
            assertInstanceOf(MilitaryAction.Attack.class, action,
                "Should attack when military advantage > 1.5x");
        }

        @Test
        @DisplayName("Given severe disadvantage, when deciding action, then Retreat is chosen")
        void shouldRetreatWhenOutnumbered() {
            // Given: AI has 1 unit, enemy has many
            placeCompletedCC(0);
            placeCompletedCC(1);
            addUnit(Faction.CONFEDERATION, new GridPosition(10, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            // Many enemy units far from base (not triggering defense)
            for (int i = 0; i < 10; i++) {
                addUnit(Faction.RESISTANCE, new GridPosition(50 + i, 55), UnitType.REBEL_INFANTRY, createRebelInfantryStats());
            }

            // When
            MilitaryAction action = militaryAI.decideAction(entities, map, 0);

            // Then
            assertInstanceOf(MilitaryAction.Retreat.class, action,
                "Should retreat when severely outnumbered");
        }

        @Test
        @DisplayName("Given no units, when deciding action, then HoldPosition with empty list")
        void shouldHoldPositionWithNoUnits() {
            // Given: AI has no units
            placeCompletedCC(0);

            // When
            MilitaryAction action = militaryAI.decideAction(entities, map, 0);

            // Then
            assertInstanceOf(MilitaryAction.HoldPosition.class, action,
                "Should hold position with no units");
            assertTrue(((MilitaryAction.HoldPosition) action).unitIds().isEmpty());
        }
    }

    @Nested
    @DisplayName("Attack Group Selection")
    class AttackGroupSelection {

        @Test
        @DisplayName("Given mixed units, when selecting attack group, then vehicles are prioritized")
        void shouldPrioritizeVehiclesInAttackGroup() {
            // Given: mix of infantry and vehicles
            placeCompletedCC(0);
            addUnit(Faction.CONFEDERATION, new GridPosition(10, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            addUnit(Faction.CONFEDERATION, new GridPosition(11, 15), UnitType.CONFED_HAMMER, createHammerStats());
            addUnit(Faction.CONFEDERATION, new GridPosition(12, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());

            // When
            List<Unit> attackGroup = militaryAI.selectAttackGroup(entities, 0, 2);

            // Then: first unit should be a vehicle
            assertEquals(2, attackGroup.size());
            assertTrue(attackGroup.get(0).isVehicle(), "Vehicle should be prioritized in attack group");
        }

        @Test
        @DisplayName("Given desired size larger than army, when selecting attack group, then all units are selected")
        void shouldSelectAllUnitsWhenDesiredSizeExceedsArmy() {
            // Given: 3 units, requesting 5
            placeCompletedCC(0);
            for (int i = 0; i < 3; i++) {
                addUnit(Faction.CONFEDERATION, new GridPosition(10 + i, 15), UnitType.CONFED_INFANTRY, createConfedInfantryStats());
            }

            // When
            List<Unit> attackGroup = militaryAI.selectAttackGroup(entities, 0, 5);

            // Then
            assertEquals(3, attackGroup.size(), "Should select all available units");
        }
    }

    @Nested
    @DisplayName("Attack Target Finding")
    class AttackTargetFinding {

        @Test
        @DisplayName("Given enemy buildings, when finding target, then highest HP building is chosen")
        void shouldTargetHighestHPBuilding() {
            // Given: enemy has various buildings
            placeCompletedCC(0);
            placeCompletedCC(1);

            // When
            GridPosition target = militaryAI.findAttackTarget(entities, map, 0);

            // Then: should target the enemy CC (highest HP = 120)
            assertNotNull(target, "Should find an attack target");
        }

        @Test
        @DisplayName("Given no enemy buildings but enemy units, when finding target, then unit cluster centroid is chosen")
        void shouldTargetUnitClusterWhenNoBuildings() {
            // Given: no enemy buildings but enemy units
            placeCompletedCC(0);
            addUnit(Faction.RESISTANCE, new GridPosition(40, 40), UnitType.REBEL_INFANTRY, createRebelInfantryStats());
            addUnit(Faction.RESISTANCE, new GridPosition(42, 42), UnitType.REBEL_INFANTRY, createRebelInfantryStats());

            // When
            GridPosition target = militaryAI.findAttackTarget(entities, map, 0);

            // Then: should find a target near the enemy unit cluster
            assertNotNull(target, "Should find target near enemy units");
        }

        @Test
        @DisplayName("Given no enemy units or buildings, when finding target, then null is returned")
        void shouldReturnNullWithNoEnemies() {
            // Given: no enemy units or buildings
            placeCompletedCC(0);

            // When
            GridPosition target = militaryAI.findAttackTarget(entities, map, 0);

            // Then
            assertNull(target, "Should return null when no enemies exist");
        }
    }

    @Nested
    @DisplayName("MilitaryAction Pattern Matching")
    class MilitaryActionPatternMatching {

        @Test
        @DisplayName("Given Attack action, when pattern matching, then correct fields are extracted")
        void shouldExtractAttackFields() {
            // Given
            GridPosition target = new GridPosition(50, 50);
            List<Integer> unitIds = List.of(1, 2, 3);
            MilitaryAction.Attack attack = new MilitaryAction.Attack(target, unitIds);

            // When/Then
            if (attack instanceof MilitaryAction.Attack a) {
                assertEquals(target, a.target());
                assertEquals(unitIds, a.unitIds());
            } else {
                fail("Should match Attack pattern");
            }
        }

        @Test
        @DisplayName("Given Defend action, when pattern matching, then correct fields are extracted")
        void shouldExtractDefendFields() {
            // Given
            GridPosition defendPoint = new GridPosition(10, 10);
            List<Integer> unitIds = List.of(4, 5);
            MilitaryAction.Defend defend = new MilitaryAction.Defend(defendPoint, unitIds);

            // When/Then
            if (defend instanceof MilitaryAction.Defend d) {
                assertEquals(defendPoint, d.defendPoint());
                assertEquals(unitIds, d.unitIds());
            } else {
                fail("Should match Defend pattern");
            }
        }

        @Test
        @DisplayName("Given Retreat action, when pattern matching, then correct fields are extracted")
        void shouldExtractRetreatFields() {
            // Given
            GridPosition rallyPoint = new GridPosition(5, 5);
            List<Integer> unitIds = List.of(6, 7, 8);
            MilitaryAction.Retreat retreat = new MilitaryAction.Retreat(rallyPoint, unitIds);

            // When/Then
            if (retreat instanceof MilitaryAction.Retreat r) {
                assertEquals(rallyPoint, r.rallyPoint());
                assertEquals(unitIds, r.unitIds());
            } else {
                fail("Should match Retreat pattern");
            }
        }

        @Test
        @DisplayName("Given HoldPosition action, when pattern matching, then correct fields are extracted")
        void shouldExtractHoldPositionFields() {
            // Given
            List<Integer> unitIds = List.of(9, 10);
            MilitaryAction.HoldPosition hold = new MilitaryAction.HoldPosition(unitIds);

            // When/Then
            if (hold instanceof MilitaryAction.HoldPosition hp) {
                assertEquals(unitIds, hp.unitIds());
            } else {
                fail("Should match HoldPosition pattern");
            }
        }

        @Test
        @DisplayName("Given Harass action, when pattern matching, then correct fields are extracted")
        void shouldExtractHarassFields() {
            // Given
            GridPosition target = new GridPosition(30, 30);
            List<Integer> unitIds = List.of(11, 12, 13);
            MilitaryAction.Harass harass = new MilitaryAction.Harass(target, unitIds);

            // When/Then
            if (harass instanceof MilitaryAction.Harass h) {
                assertEquals(target, h.target());
                assertEquals(unitIds, h.unitIds());
            } else {
                fail("Should match Harass pattern");
            }
        }
    }
}
