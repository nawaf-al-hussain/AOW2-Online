package com.aow2.core.entity;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingTest {

    /**
     * Creates a Command Centre stat block.
     * REF: complete_building_stats.json — CommandCentre: hp=120, cost=100, buildTime=60
     */
    private BuildingStats createCommandCentreStats() {
        return new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE,
            120,    // hp
            100,    // baseCost
            0,      // speed
            10,     // armor
            0,      // attackBonus
            10,     // sightRange
            60,     // buildTime
            0,      // attackRange
            15,     // extendedArmor
            0,      // powerConsume
            0,      // powerProduce
            5,      // queueSlots
            0,      // techRequirement
            100,    // costCredits
            50,     // rewardCredits
            List.of(100, 200, 300)  // upgradeCosts
        );
    }

    /**
     * Creates a Barracks stat block.
     * REF: complete_building_stats.json — Barracks: hp=80, cost=30, buildTime=40, queueSlots=5
     */
    private BuildingStats createBarracksStats() {
        return new BuildingStats(
            BuildingType.REBEL_BARRACKS,
            80,     // hp
            30,     // baseCost
            0,      // speed
            5,      // armor
            0,      // attackBonus
            8,      // sightRange
            40,     // buildTime
            0,      // attackRange
            10,     // extendedArmor
            5,      // powerConsume
            0,      // powerProduce
            5,      // queueSlots
            0,      // techRequirement
            30,     // costCredits
            15,     // rewardCredits
            List.of(50, 100, 150)  // upgradeCosts
        );
    }

    /**
     * Creates a Generator stat block.
     * REF: complete_building_stats.json — Generator: hp=60, powerProduce=10
     */
    private BuildingStats createGeneratorStats() {
        return new BuildingStats(
            BuildingType.CONFED_GENERATOR,
            60,     // hp
            20,     // baseCost
            0,      // speed
            3,      // armor
            0,      // attackBonus
            6,      // sightRange
            30,     // buildTime
            0,      // attackRange
            5,      // extendedArmor
            0,      // powerConsume
            10,     // powerProduce
            0,      // queueSlots
            0,      // techRequirement
            20,     // costCredits
            10,     // rewardCredits
            List.of()  // upgradeCosts
        );
    }

    @Nested
    @DisplayName("Building Creation")
    class Creation {

        @Test
        @DisplayName("Should create building with correct stats from RE data")
        void shouldCreateBuildingWithStats() {
            BuildingStats stats = createCommandCentreStats();
            Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_COMMAND_CENTRE, stats);

            assertEquals(1, building.getId());
            assertEquals(Faction.CONFEDERATION, building.getFaction());
            assertEquals(new GridPosition(5, 5), building.getPosition());
            assertEquals(120, building.getHp());     // hp from stats
            assertEquals(120, building.getMaxHp());   // maxHp = stats.hp
            assertEquals(BuildingType.CONFED_COMMAND_CENTRE, building.getBuildingType());
            assertTrue(building.isAlive());
        }

        @Test
        @DisplayName("Should start under construction")
        void shouldStartUnderConstruction() {
            BuildingStats stats = createCommandCentreStats();
            Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_COMMAND_CENTRE, stats);

            assertTrue(building.isUnderConstruction());
            assertEquals(0, building.getConstructionProgress());
        }

        @Test
        @DisplayName("Should start unpowered")
        void shouldStartUnpowered() {
            BuildingStats stats = createCommandCentreStats();
            Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_COMMAND_CENTRE, stats);

            assertFalse(building.isPowered());
        }

        @Test
        @DisplayName("Should start with no production or research")
        void shouldStartWithNoProductionOrResearch() {
            BuildingStats stats = createCommandCentreStats();
            Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_COMMAND_CENTRE, stats);

            assertFalse(building.isProducing());
            assertFalse(building.isResearching());
            assertNull(building.getCurrentProduction());
            assertEquals(0, building.getProductionProgress());
            assertNull(building.getGarrisonedUnitRef());
            assertNull(building.getResearchId());
            assertNull(building.getWaypoint());
        }
    }

    @Nested
    @DisplayName("Construction State")
    class ConstructionState {

        @Test
        @DisplayName("Should report under construction while progress < buildTime")
        void shouldReportUnderConstruction() {
            BuildingStats stats = createCommandCentreStats(); // buildTime=60
            Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_COMMAND_CENTRE, stats);

            building.setConstructionProgress(30);
            assertTrue(building.isUnderConstruction());

            building.setConstructionProgress(59);
            assertTrue(building.isUnderConstruction());
        }

        @Test
        @DisplayName("Should report construction complete when progress reaches buildTime")
        void shouldReportConstructionComplete() {
            BuildingStats stats = createCommandCentreStats(); // buildTime=60
            Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_COMMAND_CENTRE, stats);

            building.setConstructionProgress(60);
            assertFalse(building.isUnderConstruction());
        }
    }

    @Nested
    @DisplayName("Power State")
    class PowerState {

        @Test
        @DisplayName("Should toggle power state")
        void shouldTogglePowerState() {
            BuildingStats stats = createBarracksStats();
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            assertFalse(building.isPowered());

            building.setPowered(true);
            assertTrue(building.isPowered());

            building.setPowered(false);
            assertFalse(building.isPowered());
        }
    }

    @Nested
    @DisplayName("Production Queue")
    class ProductionQueue {

        @Test
        @DisplayName("Should enqueue production and start immediately if idle")
        void shouldEnqueueAndStartIfIdle() {
            BuildingStats stats = createBarracksStats(); // queueSlots=5
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            boolean enqueued = building.enqueueProduction(UnitType.REBEL_INFANTRY);
            assertTrue(enqueued);
            assertTrue(building.isProducing());
            assertEquals(UnitType.REBEL_INFANTRY, building.getCurrentProduction());
            assertEquals(0, building.getProductionProgress());
        }

        @Test
        @DisplayName("Should queue items when already producing")
        void shouldQueueWhenAlreadyProducing() {
            BuildingStats stats = createBarracksStats(); // queueSlots=5
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            building.enqueueProduction(UnitType.REBEL_INFANTRY);
            building.enqueueProduction(UnitType.REBEL_GRENADIER);

            // Current production is first item, second is queued
            assertEquals(UnitType.REBEL_INFANTRY, building.getCurrentProduction());
            assertEquals(1, building.getProductionQueue().size());
            assertEquals(UnitType.REBEL_GRENADIER, building.getProductionQueue().get(0));
        }

        @Test
        @DisplayName("Should reject production when queue is full")
        void shouldRejectWhenQueueFull() {
            BuildingStats stats = createBarracksStats(); // queueSlots=5
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            // Fill the queue (1 current + 5 queued = 6, but queueSlots=5 limits the queue list)
            building.enqueueProduction(UnitType.REBEL_INFANTRY);
            building.enqueueProduction(UnitType.REBEL_GRENADIER);
            building.enqueueProduction(UnitType.REBEL_SNIPER);
            building.enqueueProduction(UnitType.REBEL_INFANTRY);
            building.enqueueProduction(UnitType.REBEL_GRENADIER);
            building.enqueueProduction(UnitType.REBEL_SNIPER);

            // Queue has 5 slots; 1 current + 5 in queue = 6 total; 6th enqueue should fail
            boolean result = building.enqueueProduction(UnitType.REBEL_INFANTRY);
            assertFalse(result);
        }

        @Test
        @DisplayName("Should advance production progress")
        void shouldAdvanceProduction() {
            BuildingStats stats = createBarracksStats();
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            building.enqueueProduction(UnitType.REBEL_INFANTRY);
            building.advanceProduction();
            assertEquals(1, building.getProductionProgress());

            building.advanceProduction();
            assertEquals(2, building.getProductionProgress());
        }

        @Test
        @DisplayName("Should complete current production and start next")
        void shouldCompleteAndStartNext() {
            BuildingStats stats = createBarracksStats();
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            building.enqueueProduction(UnitType.REBEL_INFANTRY);
            building.enqueueProduction(UnitType.REBEL_GRENADIER);

            // Complete first item
            UnitType completed = building.completeCurrentProduction();
            assertEquals(UnitType.REBEL_INFANTRY, completed);
            assertEquals(UnitType.REBEL_GRENADIER, building.getCurrentProduction());
            assertEquals(0, building.getProductionProgress());
        }

        @Test
        @DisplayName("Should complete final item and become idle")
        void shouldCompleteFinalItem() {
            BuildingStats stats = createBarracksStats();
            Building building = new Building(1, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            building.enqueueProduction(UnitType.REBEL_INFANTRY);

            UnitType completed = building.completeCurrentProduction();
            assertEquals(UnitType.REBEL_INFANTRY, completed);
            assertFalse(building.isProducing());
            assertNull(building.getCurrentProduction());
        }
    }
}
