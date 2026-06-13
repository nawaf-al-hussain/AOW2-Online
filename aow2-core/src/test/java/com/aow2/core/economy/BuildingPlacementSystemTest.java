package com.aow2.core.economy;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlacementSystemTest {

    private EntityManager entities;
    private GameMap map;
    private ResourceGenerator resourceGenerator;
    private EconomySystem economy;
    private BuildingPlacementSystem placementSystem;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        map = new GameMap(100, 100); // Large map for placement tests
        resourceGenerator = new ResourceGenerator();
        economy = new EconomySystem(resourceGenerator);
        placementSystem = new BuildingPlacementSystem();
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

    /**
     * Places a completed Command Centre for player 0 at the given position.
     */
    private Building placeCompletedCC(GridPosition pos) {
        BuildingStats stats = createCommandCentreStats();
        Building cc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
            pos, BuildingType.CONFED_COMMAND_CENTRE, stats);
        cc.setConstructionProgress(stats.buildTime());
        entities.addBuilding(cc);
        return cc;
    }

    @Nested
    @DisplayName("Placement Within CC Radius")
    class PlacementWithinCCRadius {

        @Test
        @DisplayName("Should allow placement within CC radius")
        void shouldAllowPlacementWithinCCRadius() {
            // Given: player has a CC
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);

            // When: try to place a Generator nearby
            GridPosition placementPos = new GridPosition(52, 50);
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, placementPos, 0, entities, map, economy);

            // Then
            assertTrue(canPlace, "Should allow placement within CC radius");
        }

        @Test
        @DisplayName("Should reject placement outside CC radius")
        void shouldRejectPlacementOutsideCCRadius() {
            // Given: player has a CC at (50,50) with radius 20
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);

            // When: try to place a Generator far away
            GridPosition placementPos = new GridPosition(80, 80);
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, placementPos, 0, entities, map, economy);

            // Then: distance ~42, outside radius 20
            assertFalse(canPlace, "Should reject placement outside CC radius");
        }

        @Test
        @DisplayName("Should allow CC placement anywhere (no radius requirement)")
        void shouldAllowCCPlacementAnywhere() {
            // Given: player has enough credits
            economy.setCredits(0, 1000);

            // When: try to place a Command Centre (no existing CC required for first one)
            GridPosition placementPos = new GridPosition(30, 30);
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_COMMAND_CENTRE, placementPos, 0, entities, map, economy);

            // Then: CC placement should not require being near an existing CC
            assertTrue(canPlace, "Should allow CC placement without existing CC");
        }
    }

    @Nested
    @DisplayName("Terrain Checks")
    class TerrainChecks {

        @Test
        @DisplayName("Should reject placement on impassable terrain")
        void shouldRejectPlacementOnImpassableTerrain() {
            // Given: map with water at (55,50), player has CC
            map.setTile(55, 50, TerrainType.WATER);
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);

            // When
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, new GridPosition(55, 50), 0, entities, map, economy);

            // Then
            assertFalse(canPlace, "Should reject placement on water");
        }

        @Test
        @DisplayName("Should reject placement on mountain terrain")
        void shouldRejectPlacementOnMountainTerrain() {
            // Given: map with mountain at (55,50), player has CC
            map.setTile(55, 50, TerrainType.MOUNTAIN);
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);

            // When
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, new GridPosition(55, 50), 0, entities, map, economy);

            // Then
            assertFalse(canPlace, "Should reject placement on mountain");
        }

        @Test
        @DisplayName("Should allow placement on grass terrain")
        void shouldAllowPlacementOnGrassTerrain() {
            // Given: grass is the default terrain, player has CC
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);

            // When
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, new GridPosition(52, 50), 0, entities, map, economy);

            // Then
            assertTrue(canPlace, "Should allow placement on grass");
        }
    }

    @Nested
    @DisplayName("Building Overlap Checks")
    class BuildingOverlapChecks {

        @Test
        @DisplayName("Should reject placement overlapping existing buildings")
        void shouldRejectPlacementOverlappingBuildings() {
            // Given: player has a CC and an existing building at (52,50)
            placeCompletedCC(new GridPosition(50, 50));
            BuildingStats genStats = createGeneratorStats();
            Building existingGen = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(52, 50), BuildingType.CONFED_GENERATOR, genStats);
            existingGen.setConstructionProgress(genStats.buildTime());
            entities.addBuilding(existingGen);
            economy.setCredits(0, 1000);

            // When: try to place another building at the same position
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_INFANTRY_CENTRE, new GridPosition(52, 50), 0, entities, map, economy);

            // Then
            assertFalse(canPlace, "Should reject placement overlapping existing building");
        }
    }

    @Nested
    @DisplayName("Credit Checks")
    class CreditChecks {

        @Test
        @DisplayName("Should reject placement when insufficient credits")
        void shouldRejectPlacementWhenInsufficientCredits() {
            // Given: player has a CC but not enough credits
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 10); // Generator costs 20

            // When
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, new GridPosition(52, 50), 0, entities, map, economy);

            // Then
            assertFalse(canPlace, "Should reject placement when insufficient credits");
        }

        @Test
        @DisplayName("Should allow placement when sufficient credits")
        void shouldAllowPlacementWhenSufficientCredits() {
            // Given: player has a CC and enough credits
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);

            // When
            boolean canPlace = placementSystem.canPlace(
                BuildingType.CONFED_GENERATOR, new GridPosition(52, 50), 0, entities, map, economy);

            // Then
            assertTrue(canPlace, "Should allow placement when sufficient credits");
        }
    }

    @Nested
    @DisplayName("Building Placement Execution")
    class BuildingPlacementExecution {

        @Test
        @DisplayName("Should place building and deduct credits")
        void shouldPlaceBuildingAndDeductCredits() {
            // Given
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 1000);
            int cost = 20; // Generator cost

            // When
            Building placed = placementSystem.placeBuilding(
                BuildingType.CONFED_GENERATOR, new GridPosition(52, 50), 0, entities, map, economy);

            // Then
            assertNotNull(placed);
            assertEquals(BuildingType.CONFED_GENERATOR, placed.getBuildingType());
            assertEquals(new GridPosition(52, 50), placed.getPosition());
            assertEquals(1000 - cost, economy.getCredits(0));
            assertTrue(placed.isUnderConstruction());
        }

        @Test
        @DisplayName("Should return null when placement fails")
        void shouldReturnNullWhenPlacementFails() {
            // Given: not enough credits
            placeCompletedCC(new GridPosition(50, 50));
            economy.setCredits(0, 5);

            // When
            Building placed = placementSystem.placeBuilding(
                BuildingType.CONFED_GENERATOR, new GridPosition(52, 50), 0, entities, map, economy);

            // Then
            assertNull(placed);
            assertEquals(5, economy.getCredits(0)); // Credits unchanged
        }
    }

    @Nested
    @DisplayName("Valid Positions")
    class ValidPositions {

        @Test
        @DisplayName("Should return valid positions within CC radius")
        void shouldReturnValidPositions() {
            // Given
            placeCompletedCC(new GridPosition(50, 50));

            // When
            Set<GridPosition> valid = placementSystem.getValidPositions(
                BuildingType.CONFED_GENERATOR, 0, entities, map);

            // Then: should contain positions near the CC
            assertFalse(valid.isEmpty(), "Should have some valid positions");
            assertTrue(valid.contains(new GridPosition(50, 51)),
                "Adjacent position should be valid");
        }

        @Test
        @DisplayName("Should return empty set when no CCs exist")
        void shouldReturnEmptyWhenNoCCs() {
            // Given: no CCs for player 0

            // When
            Set<GridPosition> valid = placementSystem.getValidPositions(
                BuildingType.CONFED_GENERATOR, 0, entities, map);

            // Then
            assertTrue(valid.isEmpty(), "Should return empty set when no CCs");
        }
    }
}
