package com.aow2.core.ai;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.UnitType;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AI economy decision-making: build order, placement, production.
 */
class EconomyAITest {

    private EntityManager entities;
    private EconomySystem economy;
    private EconomyAI economyAI;
    private GameMap map;
    private BuildingPlacementSystem placement;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        ResourceGenerator resourceGenerator = new ResourceGenerator();
        economy = new EconomySystem(resourceGenerator);
        economyAI = new EconomyAI();
        map = new GameMap(64, 64);
        placement = new BuildingPlacementSystem();
    }

    /**
     * Creates a completed Command Centre stat block.
     */
    private BuildingStats createCCStats(BuildingType type) {
        return new BuildingStats(type, 120, 100, 0, 10, 0, 10, 60, 0, 15, 0, 0, 5, 0, 100, 50, 0, WeaponType.NONE, List.of(100, 200, 300));
    }

    /**
     * Creates a completed Generator stat block.
     */
    private BuildingStats createGeneratorStats(BuildingType type) {
        return new BuildingStats(type, 60, 20, 0, 3, 0, 6, 30, 0, 5, 0, 10, 0, 0, 20, 10, 0, WeaponType.NONE, List.of());
    }

    /**
     * Creates a completed Infantry Centre stat block.
     */
    private BuildingStats createInfantryCentreStats(BuildingType type) {
        return new BuildingStats(type, 80, 30, 0, 5, 0, 8, 40, 0, 10, 5, 0, 5, 0, 30, 15, 0, WeaponType.NONE, List.of(50, 100, 150));
    }

    /**
     * Creates a completed Machine Factory stat block.
     */
    private BuildingStats createFactoryStats(BuildingType type) {
        return new BuildingStats(type, 100, 50, 0, 7, 0, 8, 50, 0, 12, 8, 0, 5, 0, 50, 25, 0, WeaponType.NONE, List.of(75, 150, 225));
    }

    /**
     * Creates a completed Tech Centre stat block.
     */
    private BuildingStats createTechCentreStats(BuildingType type) {
        return new BuildingStats(type, 70, 60, 0, 4, 0, 8, 45, 0, 8, 5, 0, 1, 0, 60, 30, 0, WeaponType.NONE, List.of());
    }

    /**
     * Creates a completed Bunker stat block.
     */
    private BuildingStats createBunkerStats(BuildingType type) {
        return new BuildingStats(type, 150, 15, 0, 12, 0, 7, 25, 5, 18, 3, 0, 1, 0, 15, 8, 0, WeaponType.NONE, List.of());
    }

    private Building placeCompletedBuilding(int playerId, BuildingType type, BuildingStats stats, GridPosition pos) {
        Faction faction = EconomySystem.playerFaction(playerId);
        Building building = new Building(entities.allocateEntityId(), faction, pos, type, stats);
        building.setConstructionProgress(stats.buildTime());
        building.setPowered(true);
        entities.addBuilding(building);
        return building;
    }

    @Nested
    @DisplayName("Build Order Decisions")
    class BuildOrderDecisions {

        @Test
        @DisplayName("Given no buildings, when deciding next building, then Command Centre is chosen")
        void shouldChooseCommandCentreFirst() {
            // Given: player has no buildings but enough credits
            economy.setCredits(0, 500);

            // When
            BuildingType next = economyAI.decideNextBuilding(entities, economy, 0);

            // Then
            assertEquals(BuildingType.CONFED_COMMAND_CENTRE, next);
        }

        @Test
        @DisplayName("Given CC exists, when deciding next building, then Generator is chosen")
        void shouldChooseGeneratorAfterCC() {
            // Given: player has a Command Centre
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));
            economy.setCredits(0, 500);

            // When
            BuildingType next = economyAI.decideNextBuilding(entities, economy, 0);

            // Then
            assertEquals(BuildingType.CONFED_GENERATOR, next);
        }

        @Test
        @DisplayName("Given CC and Generator, when deciding next building, then Infantry Centre is chosen")
        void shouldChooseInfantryCentreAfterGenerator() {
            // Given
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_GENERATOR,
                createGeneratorStats(BuildingType.CONFED_GENERATOR), new GridPosition(11, 10));
            economy.setCredits(0, 500);

            // When
            BuildingType next = economyAI.decideNextBuilding(entities, economy, 0);

            // Then
            assertEquals(BuildingType.CONFED_INFANTRY_CENTRE, next);
        }

        @Test
        @DisplayName("Given insufficient credits, when deciding next building, then null is returned")
        void shouldReturnNullWhenCannotAfford() {
            // Given: player has no buildings and insufficient credits
            economy.setCredits(0, 10); // Can't afford CC (costs 100)

            // When
            BuildingType next = economyAI.decideNextBuilding(entities, economy, 0);

            // Then
            assertNull(next, "Should return null when cannot afford any building");
        }

        @Test
        @DisplayName("Given all basic buildings exist, when deciding next building, then additional production is chosen")
        void shouldChooseAdditionalProductionWhenBaseComplete() {
            // Given: all basic buildings are built
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_GENERATOR,
                createGeneratorStats(BuildingType.CONFED_GENERATOR), new GridPosition(11, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_INFANTRY_CENTRE,
                createInfantryCentreStats(BuildingType.CONFED_INFANTRY_CENTRE), new GridPosition(12, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_TECH_CENTRE,
                createTechCentreStats(BuildingType.CONFED_TECH_CENTRE), new GridPosition(13, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_MACHINE_FACTORY,
                createFactoryStats(BuildingType.CONFED_MACHINE_FACTORY), new GridPosition(14, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_BUNKER,
                createBunkerStats(BuildingType.CONFED_BUNKER), new GridPosition(15, 10));
            economy.setCredits(0, 500);

            // When
            BuildingType next = economyAI.decideNextBuilding(entities, economy, 0);

            // Then: should pick a second infantry centre or null (depends on credit threshold)
            assertNotNull(next, "Should choose additional production when base is complete");
        }

        @Test
        @DisplayName("Given Resistance faction, when deciding next building, then Rebel buildings are chosen")
        void shouldChooseRebelBuildingsForResistance() {
            // Given: Resistance player with no buildings but enough credits
            economy.setCredits(1, 500);

            // When
            BuildingType next = economyAI.decideNextBuilding(entities, economy, 1);

            // Then
            assertEquals(BuildingType.REBEL_HEADQUARTERS, next);
        }
    }

    @Nested
    @DisplayName("Production Decisions")
    class ProductionDecisions {

        @Test
        @DisplayName("Given idle infantry centre, when deciding production, then infantry is produced")
        void shouldProduceInfantryAtInfantryCentre() {
            // Given: player has a CC and Infantry Centre
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_INFANTRY_CENTRE,
                createInfantryCentreStats(BuildingType.CONFED_INFANTRY_CENTRE), new GridPosition(11, 10));
            economy.setCredits(0, 500);
            ResearchSystem research = new ResearchSystem();

            // When
            Map<Integer, UnitType> decisions = economyAI.decideProduction(entities, economy, research, 0);

            // Then: should produce infantry
            assertFalse(decisions.isEmpty(), "Should have production decisions");
            assertTrue(decisions.containsValue(UnitType.CONFED_INFANTRY),
                "Should produce infantry at infantry centre");
        }

        @Test
        @DisplayName("Given unit cap reached, when deciding production, then no production occurs")
        void shouldNotProduceWhenAtUnitCap() {
            // Given: player has 50 units (at cap)
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_INFANTRY_CENTRE,
                createInfantryCentreStats(BuildingType.CONFED_INFANTRY_CENTRE), new GridPosition(11, 10));

            // Add 50 units
            UnitStats infantryStats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
            for (int i = 0; i < 50; i++) {
                Unit unit = new Unit(entities.allocateEntityId(), Faction.CONFEDERATION,
                    new GridPosition(10 + (i % 10), 20 + (i / 10)), UnitType.CONFED_INFANTRY, infantryStats);
                entities.addUnit(unit);
            }

            economy.setCredits(0, 500);
            ResearchSystem research = new ResearchSystem();

            // When
            Map<Integer, UnitType> decisions = economyAI.decideProduction(entities, economy, research, 0);

            // Then
            assertTrue(decisions.isEmpty(), "Should not produce when at unit cap");
        }

        @Test
        @DisplayName("Given insufficient credits, when deciding production, then no production occurs")
        void shouldNotProduceWhenCannotAfford() {
            // Given: player has a CC and Infantry Centre but no credits
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));
            placeCompletedBuilding(0, BuildingType.CONFED_INFANTRY_CENTRE,
                createInfantryCentreStats(BuildingType.CONFED_INFANTRY_CENTRE), new GridPosition(11, 10));
            economy.setCredits(0, 0); // No credits
            ResearchSystem research = new ResearchSystem();

            // When
            Map<Integer, UnitType> decisions = economyAI.decideProduction(entities, economy, research, 0);

            // Then
            assertTrue(decisions.isEmpty(), "Should not produce when cannot afford");
        }
    }

    @Nested
    @DisplayName("Defense Decisions")
    class DefenseDecisions {

        @Test
        @DisplayName("Given enemy near base, when checking defense, then defense is needed")
        void shouldNeedDefenseWhenEnemyNearBase() {
            // Given: player has a CC and enemy is nearby
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));

            // Place enemy unit near base (within 20 cells)
            UnitStats rebelStats = new UnitStats(UnitType.REBEL_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
            Unit enemy = new Unit(entities.allocateEntityId(), Faction.RESISTANCE,
                new GridPosition(15, 15), UnitType.REBEL_INFANTRY, rebelStats);
            entities.addUnit(enemy);

            // When
            boolean needsDefense = economyAI.needsDefense(entities, 0);

            // Then
            assertTrue(needsDefense, "Should need defense when enemy is near base");
        }

        @Test
        @DisplayName("Given no enemies near base, when checking defense, then no defense needed")
        void shouldNotNeedDefenseWhenNoEnemiesNearBase() {
            // Given: player has a CC and no enemy units nearby
            placeCompletedBuilding(0, BuildingType.CONFED_COMMAND_CENTRE,
                createCCStats(BuildingType.CONFED_COMMAND_CENTRE), new GridPosition(10, 10));

            // Place enemy unit far from base
            UnitStats rebelStats = new UnitStats(UnitType.REBEL_INFANTRY, "Infantry", 40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
            Unit enemy = new Unit(entities.allocateEntityId(), Faction.RESISTANCE,
                new GridPosition(50, 50), UnitType.REBEL_INFANTRY, rebelStats);
            entities.addUnit(enemy);

            // When
            boolean needsDefense = economyAI.needsDefense(entities, 0);

            // Then
            assertFalse(needsDefense, "Should not need defense when no enemies near base");
        }

        @Test
        @DisplayName("Given no base, when checking defense, then no defense needed")
        void shouldNotNeedDefenseWithNoBase() {
            // Given: player has no Command Centre
            // When
            boolean needsDefense = economyAI.needsDefense(entities, 0);

            // Then
            assertFalse(needsDefense, "Should not need defense with no base");
        }
    }
}
