package com.aow2.core.economy;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerSystemTest {

    private EntityManager entities;
    private PowerSystem powerSystem;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        powerSystem = new PowerSystem();
    }

    private BuildingStats createCommandCentreStats() {
        return new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE, 120, 100, 0, 10, 0, 10,
            60, 0, 15, 0, 0, 5, 0, 100, 50, List.of(100, 200, 300)
        );
    }

    private BuildingStats createGeneratorStats() {
        return new BuildingStats(
            BuildingType.CONFED_GENERATOR, 60, 20, 0, 3, 0, 6,
            30, 0, 5, 0, 10, 0, 0, 20, 10, List.of()
        );
    }

    private BuildingStats createInfantryCentreStats() {
        return new BuildingStats(
            BuildingType.CONFED_INFANTRY_CENTRE, 80, 30, 0, 5, 0, 8,
            40, 0, 10, 5, 0, 5, 0, 30, 15, List.of(50, 100, 150)
        );
    }

    /**
     * Places a completed building for the Confederation faction.
     */
    private Building placeCompletedBuilding(BuildingType type, BuildingStats stats, GridPosition pos) {
        Building building = new Building(entities.allocateEntityId(), Faction.CONFEDERATION, pos, type, stats);
        building.setConstructionProgress(stats.buildTime());
        entities.addBuilding(building);
        return building;
    }

    @Nested
    @DisplayName("Power Radius")
    class PowerRadius {

        @Test
        @DisplayName("Should return base power radius for level 0 generator")
        void shouldReturnBasePowerRadiusForLevel0() {
            // Given: a generator at level 0
            Building generator = placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));

            // When
            int radius = powerSystem.getPowerRadius(generator);

            // Then: level 0 = index 0 = radius 10
            assertEquals(10, radius);
        }
    }

    @Nested
    @DisplayName("Building Power Check")
    class BuildingPowerCheck {

        @Test
        @DisplayName("Should power buildings within radius")
        void shouldPowerBuildingsWithinRadius() {
            // Given: a generator and a nearby building
            Building generator = placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));
            Building infantryCentre = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createInfantryCentreStats(), new GridPosition(52, 50));

            // When
            boolean powered = powerSystem.isBuildingPowered(infantryCentre, entities);

            // Then: distance ~2, within radius 10
            assertTrue(powered, "Building at distance 2 should be powered by generator with radius 10");
        }

        @Test
        @DisplayName("Should not power buildings outside radius")
        void shouldNotPowerBuildingsOutsideRadius() {
            // Given: a generator and a far-away building
            Building generator = placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));
            Building farBuilding = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createInfantryCentreStats(), new GridPosition(70, 70));

            // When
            boolean powered = powerSystem.isBuildingPowered(farBuilding, entities);

            // Then: distance ~28, outside radius 10
            assertFalse(powered, "Building at distance ~28 should not be powered by generator with radius 10");
        }

        @Test
        @DisplayName("Should always consider Command Centre as powered")
        void shouldAlwaysConsiderCCAsPowered() {
            // Given: a CC with no generators
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCommandCentreStats(), new GridPosition(50, 50));

            // When
            boolean powered = powerSystem.isBuildingPowered(cc, entities);

            // Then
            assertTrue(powered, "Command Centre should always be powered");
        }

        @Test
        @DisplayName("Should power itself for generator")
        void shouldPowerItselfForGenerator() {
            // Given: a completed generator
            Building generator = placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));

            // When
            boolean powered = powerSystem.isBuildingPowered(generator, entities);

            // Then
            assertTrue(powered, "Generator should power itself when alive and completed");
        }
    }

    @Nested
    @DisplayName("Position Power Check")
    class PositionPowerCheck {

        @Test
        @DisplayName("Should detect position is powered")
        void shouldDetectPositionIsPowered() {
            // Given: a generator
            placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));

            // When: check a nearby position
            boolean powered = powerSystem.isPositionPowered(new GridPosition(52, 50), 0, entities);

            // Then
            assertTrue(powered);
        }

        @Test
        @DisplayName("Should detect position is not powered")
        void shouldDetectPositionIsNotPowered() {
            // Given: a generator
            placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));

            // When: check a far position
            boolean powered = powerSystem.isPositionPowered(new GridPosition(80, 80), 0, entities);

            // Then
            assertFalse(powered);
        }

        @Test
        @DisplayName("Should detect no power when no generators exist")
        void shouldDetectNoPowerWhenNoGenerators() {
            // Given: no generators, just a CC
            placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCommandCentreStats(), new GridPosition(50, 50));

            // When: check any position for power
            boolean powered = powerSystem.isPositionPowered(new GridPosition(50, 50), 0, entities);

            // Then: CCs don't generate power, only generators do
            assertFalse(powered);
        }
    }

    @Nested
    @DisplayName("Power Grid Update")
    class PowerGridUpdate {

        @Test
        @DisplayName("Should update power grid when generator destroyed")
        void shouldUpdatePowerGridWhenGeneratorDestroyed() {
            // Given: a generator and a powered building
            Building generator = placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));
            Building infantryCentre = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createInfantryCentreStats(), new GridPosition(52, 50));

            // Initially set the building as powered
            infantryCentre.setPowered(true);
            assertTrue(infantryCentre.isPowered());

            // When: destroy the generator and update power grid
            generator.takeDamage(generator.getMaxHp() + 1); // Destroy it
            powerSystem.updatePowerGrid(entities);

            // Then: building should lose power
            assertFalse(infantryCentre.isPowered(), "Building should lose power when generator is destroyed");
        }

        @Test
        @DisplayName("Should gain power when generator built nearby")
        void shouldGainPowerWhenGeneratorBuiltNearby() {
            // Given: an unpowered building
            Building infantryCentre = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createInfantryCentreStats(), new GridPosition(52, 50));
            assertFalse(infantryCentre.isPowered());

            // When: build a generator nearby and update power grid
            placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));
            powerSystem.updatePowerGrid(entities);

            // Then: building should now be powered
            assertTrue(infantryCentre.isPowered(), "Building should gain power when generator is built nearby");
        }

        @Test
        @DisplayName("Should not power buildings of different faction")
        void shouldNotPowerBuildingsOfDifferentFaction() {
            // Given: a Confederation generator
            placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));

            // And: a Resistance building nearby
            BuildingStats rebelStats = new BuildingStats(
                BuildingType.REBEL_BARRACKS, 80, 30, 0, 5, 0, 8,
                40, 0, 10, 5, 0, 5, 0, 30, 15, List.of(50, 100, 150));
            Building rebelBarracks = new Building(entities.allocateEntityId(), Faction.RESISTANCE,
                new GridPosition(52, 50), BuildingType.REBEL_BARRACKS, rebelStats);
            rebelBarracks.setConstructionProgress(rebelStats.buildTime());
            entities.addBuilding(rebelBarracks);

            // When
            boolean powered = powerSystem.isBuildingPowered(rebelBarracks, entities);

            // Then
            assertFalse(powered, "Generator should not power enemy buildings");
        }
    }

    @Nested
    @DisplayName("Powered Buildings List")
    class PoweredBuildingsList {

        @Test
        @DisplayName("Should return buildings powered by a specific generator")
        void shouldReturnBuildingsPoweredByGenerator() {
            // Given: a generator and two buildings within radius
            Building generator = placeCompletedBuilding(
                BuildingType.CONFED_GENERATOR, createGeneratorStats(), new GridPosition(50, 50));
            Building ic1 = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createInfantryCentreStats(), new GridPosition(52, 50));
            Building ic2 = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createInfantryCentreStats(), new GridPosition(50, 52));

            // When
            var powered = powerSystem.getPoweredBuildings(generator, entities);

            // Then
            assertEquals(2, powered.size());
            assertTrue(powered.contains(ic1));
            assertTrue(powered.contains(ic2));
        }
    }
}
