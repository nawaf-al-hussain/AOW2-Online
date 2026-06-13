package com.aow2.core.economy;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import com.aow2.core.research.ResearchSystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSystemTest {

    private EntityManager entities;
    private GameState state;
    private ResourceGenerator resourceGenerator;
    private EconomySystem economy;
    private ResearchSystem research;
    private ProductionSystem production;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        state = new GameState();
        resourceGenerator = new ResourceGenerator();
        economy = new EconomySystem(resourceGenerator);
        research = new ResearchSystem();
        production = new ProductionSystem();
    }

    private BuildingStats createInfantryCentreStats() {
        return new BuildingStats(
            BuildingType.CONFED_INFANTRY_CENTRE, 80, 30, 0, 5, 0, 8,
            40, 0, 10, 5, 0, 5, 0, 30, 15, List.of(50, 100, 150)
        );
    }

    private BuildingStats createMachineFactoryStats() {
        return new BuildingStats(
            BuildingType.CONFED_MACHINE_FACTORY, 100, 50, 0, 7, 0, 8,
            50, 0, 12, 8, 0, 5, 0, 50, 25, List.of(75, 150, 225)
        );
    }

    /**
     * Places a completed, powered Infantry Centre for player 0.
     */
    private Building placePoweredInfantryCentre() {
        BuildingStats stats = createInfantryCentreStats();
        Building ic = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(50, 50), BuildingType.CONFED_INFANTRY_CENTRE, stats);
        ic.setConstructionProgress(stats.buildTime());
        ic.setPowered(true);
        entities.addBuilding(ic);
        return ic;
    }

    /**
     * Places a completed, unpowered Infantry Centre for player 0.
     */
    private Building placeUnpoweredInfantryCentre() {
        BuildingStats stats = createInfantryCentreStats();
        Building ic = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(50, 50), BuildingType.CONFED_INFANTRY_CENTRE, stats);
        ic.setConstructionProgress(stats.buildTime());
        ic.setPowered(false);
        entities.addBuilding(ic);
        return ic;
    }

    @Nested
    @DisplayName("Enqueue Unit")
    class EnqueueUnit {

        @Test
        @DisplayName("Should enqueue unit in powered building")
        void shouldEnqueueUnitInPoweredBuilding() {
            // Given: a powered Infantry Centre
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);

            // When
            boolean result = production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // Then
            assertTrue(result, "Should enqueue unit in powered building");
            assertTrue(ic.isProducing());
            assertEquals(UnitType.CONFED_INFANTRY, ic.getCurrentProduction());
        }

        @Test
        @DisplayName("Should reject enqueue in unpowered building")
        void shouldRejectEnqueueInUnpoweredBuilding() {
            // Given: an unpowered Infantry Centre
            Building ic = placeUnpoweredInfantryCentre();
            economy.setCredits(0, 1000);

            // When
            boolean result = production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // Then
            assertFalse(result, "Should reject enqueue in unpowered building");
            assertFalse(ic.isProducing());
        }

        @Test
        @DisplayName("Should reject enqueue when insufficient credits")
        void shouldRejectEnqueueWhenInsufficientCredits() {
            // Given: powered IC but not enough credits
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1); // Infantry costs 10

            // When
            boolean result = production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // Then
            assertFalse(result, "Should reject enqueue when insufficient credits");
        }

        @Test
        @DisplayName("Should reject wrong unit type for building")
        void shouldRejectWrongUnitTypeForBuilding() {
            // Given: an Infantry Centre (not a Machine Factory)
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);

            // When: try to produce a vehicle at an infantry centre
            boolean result = production.enqueueUnit(ic, UnitType.CONFED_ZEUS, 0, economy, research);

            // Then
            assertFalse(result, "Should reject vehicle production at infantry centre");
        }

        @Test
        @DisplayName("Should deduct credits on enqueue")
        void shouldDeductCreditsOnEnqueue() {
            // Given
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);
            int cost = 10; // Infantry cost

            // When
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // Then
            assertEquals(1000 - cost, economy.getCredits(0));
        }

        @Test
        @DisplayName("Should reject enqueue when queue full")
        void shouldRejectEnqueueWhenQueueFull() {
            // Given: an IC with full queue (5 queue slots = 1 current + 5 queued = 6 total)
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 10000);

            // When: fill the queue (6 enqueues: 1 current + 5 in queue)
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // 7th enqueue should fail (1 current + 5 queued = full)
            boolean result = production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // Then
            assertFalse(result, "Should reject enqueue when queue is full");
        }
    }

    @Nested
    @DisplayName("Production Progress")
    class ProductionProgress {

        @Test
        @DisplayName("Should progress production per tick")
        void shouldProgressProductionPerTick() {
            // Given: a building with a unit in production
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // When: process one tick
            production.processTick(entities, state, economy);

            // Then: production should have advanced
            assertEquals(1, ic.getProductionProgress());
        }

        @Test
        @DisplayName("Should complete and spawn unit when production time reached")
        void shouldCompleteAndSpawnUnit() {
            // Given: a building with a unit in production
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            int requiredTime = production.calculateProductionTime(UnitType.CONFED_INFANTRY, ic);

            // When: process enough ticks to complete production
            for (int i = 0; i < requiredTime; i++) {
                production.processTick(entities, state, economy);
            }

            // Then: unit should have been spawned
            long confedUnits = entities.getAliveUnitsForPlayer(Faction.CONFEDERATION).size();
            assertTrue(confedUnits > 0, "Should have spawned at least one unit");
        }

        @Test
        @DisplayName("Should process sequential queue items")
        void shouldProcessSequentialQueueItems() {
            // Given: a building with two units in queue
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 10000);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_GRENADIER, 0, economy, research);

            // When: process enough ticks for the first unit
            int infantryTime = production.calculateProductionTime(UnitType.CONFED_INFANTRY, ic);
            for (int i = 0; i < infantryTime; i++) {
                production.processTick(entities, state, economy);
            }

            // Then: first unit should be done, second should be producing
            assertEquals(UnitType.CONFED_GRENADIER, ic.getCurrentProduction(),
                "Second unit should now be producing");
            assertEquals(0, ic.getProductionProgress(),
                "Second unit progress should start at 0");
        }

        @Test
        @DisplayName("Should not progress production in unpowered building")
        void shouldNotProgressProductionInUnpoweredBuilding() {
            // Given: an unpowered building with a unit (manually set up)
            Building ic = placeUnpoweredInfantryCentre();
            // We can't enqueue via the system (it rejects unpowered), so manually set it
            ic.enqueueProduction(UnitType.CONFED_INFANTRY);

            // When: process one tick
            production.processTick(entities, state, economy);

            // Then: production should NOT have advanced (building not powered)
            assertEquals(0, ic.getProductionProgress());
        }
    }

    @Nested
    @DisplayName("Production Time Calculation")
    class ProductionTimeCalculation {

        @Test
        @DisplayName("Should calculate production time using RE formula")
        void shouldCalculateProductionTime() {
            // Given
            Building ic = placePoweredInfantryCentre();

            // When: calculate production time for infantry
            int time = production.calculateProductionTime(UnitType.CONFED_INFANTRY, ic);

            // Then: time should be positive
            // REF: formula = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)
            // With modifier=10, bonus=0: (40 * 10) / 10 * 20 / 20 = 40
            assertTrue(time > 0, "Production time should be positive");
            assertEquals(40, time); // Infantry baseBuildTime=40 with default modifiers
        }

        @Test
        @DisplayName("Should return different times for different unit types")
        void shouldReturnDifferentTimesForDifferentUnits() {
            // Given
            Building ic = placePoweredInfantryCentre();

            // When
            int infantryTime = production.calculateProductionTime(UnitType.CONFED_INFANTRY, ic);
            int grenadierTime = production.calculateProductionTime(UnitType.CONFED_GRENADIER, ic);

            // Then: grenadier takes longer
            assertTrue(grenadierTime > infantryTime,
                "Grenadier should take longer to produce than Infantry");
        }
    }

    @Nested
    @DisplayName("Cancel Production")
    class CancelProduction {

        @Test
        @DisplayName("Should refund on cancel")
        void shouldRefundOnCancel() {
            // Given: a building with items in queue
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);
            production.enqueueUnit(ic, UnitType.CONFED_GRENADIER, 0, economy, research);

            int creditsBefore = economy.getCredits(0);

            // When: cancel the second item (index 0 in the queue list)
            boolean result = production.cancelProduction(ic, 0, 0, economy);

            // Then: should get 50% refund
            assertTrue(result);
            int refund = (int)(15 * 0.50); // Grenadier costs 15, 50% refund
            assertEquals(creditsBefore + refund, economy.getCredits(0));
        }

        @Test
        @DisplayName("Should reject cancel with invalid index")
        void shouldRejectCancelWithInvalidIndex() {
            // Given: a building with one item in queue
            Building ic = placePoweredInfantryCentre();
            economy.setCredits(0, 1000);
            production.enqueueUnit(ic, UnitType.CONFED_INFANTRY, 0, economy, research);

            // When: try to cancel with invalid index
            boolean result = production.cancelProduction(ic, 99, 0, economy);

            // Then
            assertFalse(result, "Should reject cancel with invalid index");
        }
    }
}
