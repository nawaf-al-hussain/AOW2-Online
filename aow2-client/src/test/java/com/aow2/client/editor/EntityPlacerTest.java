package com.aow2.client.editor;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EntityPlacer: building/unit placement, collision, faction checks.
 * Uses real EntityManager and GameMap instances (pure Java, no FX).
 */
class EntityPlacerTest {

    private EntityPlacer placer;
    private GameMap map;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        placer = new EntityPlacer();
        map = new GameMap(30, 30);
        entityManager = new EntityManager();
        placer.setMap(map);
        placer.setEntityManager(entityManager);
    }

    @Nested
    @DisplayName("Building Placement")
    class BuildingPlacement {

        @Test
        @DisplayName("places Confederation building for player 0")
        void placesConfedBuildingForPlayer0() {
            Building b = placer.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 0);
            assertNotNull(b);
            assertEquals(Faction.CONFEDERATION, b.getFaction());
            assertEquals(BuildingType.CONFED_COMMAND_CENTRE, b.getBuildingType());
        }

        @Test
        @DisplayName("places Resistance building for player 1")
        void placesRebelBuildingForPlayer1() {
            Building b = placer.placeBuilding(BuildingType.REBEL_HEADQUARTERS,
                new GridPosition(10, 10), 1);
            assertNotNull(b);
            assertEquals(Faction.RESISTANCE, b.getFaction());
        }

        @Test
        @DisplayName("rejects building for wrong faction")
        void rejectsWrongFaction() {
            Building b = placer.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 1); // Resistance player
            assertNull(b);
        }

        @Test
        @DisplayName("rejects overlapping buildings")
        void rejectsOverlappingBuildings() {
            placer.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 0);
            Building b = placer.placeBuilding(BuildingType.CONFED_GENERATOR,
                new GridPosition(5, 5), 0);
            assertNull(b);
        }
    }

    @Nested
    @DisplayName("Unit Placement")
    class UnitPlacement {

        @Test
        @DisplayName("places Confederation unit for player 0")
        void placesConfedUnitForPlayer0() {
            Unit u = placer.placeUnit(UnitType.CONFED_INFANTRY,
                new GridPosition(8, 8), 0);
            assertNotNull(u);
            assertEquals(Faction.CONFEDERATION, u.getFaction());
            assertEquals(UnitType.CONFED_INFANTRY, u.getUnitType());
        }

        @Test
        @DisplayName("places Resistance unit for player 1")
        void placesRebelUnitForPlayer1() {
            Unit u = placer.placeUnit(UnitType.REBEL_INFANTRY,
                new GridPosition(12, 12), 1);
            assertNotNull(u);
            assertEquals(Faction.RESISTANCE, u.getFaction());
        }

        @Test
        @DisplayName("rejects unit for wrong faction")
        void rejectsWrongFaction() {
            Unit u = placer.placeUnit(UnitType.CONFED_INFANTRY,
                new GridPosition(8, 8), 1);
            assertNull(u);
        }

        @Test
        @DisplayName("rejects overlapping units")
        void rejectsOverlappingUnits() {
            placer.placeUnit(UnitType.CONFED_INFANTRY, new GridPosition(8, 8), 0);
            Unit u = placer.placeUnit(UnitType.CONFED_GRENADIER, new GridPosition(8, 8), 0);
            assertNull(u);
        }
    }

    @Nested
    @DisplayName("Terrain Validation")
    class TerrainValidation {

        @Test
        @DisplayName("rejects placement on water")
        void rejectsOnWater() {
            map.setTile(5, 5, TerrainType.DEEP_WATER);
            Unit u = placer.placeUnit(UnitType.CONFED_INFANTRY, new GridPosition(5, 5), 0);
            assertNull(u);
        }

        @Test
        @DisplayName("rejects placement on mountain")
        void rejectsOnMountain() {
            map.setTile(5, 5, TerrainType.MOUNTAIN);
            Building b = placer.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 0);
            assertNull(b);
        }

        @Test
        @DisplayName("allows placement on grass")
        void allowsOnGrass() {
            Unit u = placer.placeUnit(UnitType.REBEL_INFANTRY, new GridPosition(5, 5), 1);
            assertNotNull(u);
        }

        @Test
        @DisplayName("allows placement on road")
        void allowsOnRoad() {
            map.setTile(10, 10, TerrainType.ROAD);
            Unit u = placer.placeUnit(UnitType.CONFED_INFANTRY, new GridPosition(10, 10), 0);
            assertNotNull(u);
        }
    }

    @Nested
    @DisplayName("Bounds Validation")
    class BoundsValidation {

        @Test
        @DisplayName("rejects placement on null map")
        void rejectsOnNullMap() {
            EntityPlacer noMapPlacer = new EntityPlacer();
            noMapPlacer.setEntityManager(entityManager);
            Unit u = noMapPlacer.placeUnit(UnitType.CONFED_INFANTRY,
                new GridPosition(5, 5), 0);
            assertNull(u);
        }

        @Test
        @DisplayName("rejects placement on null entity manager")
        void rejectsOnNullEntityManager() {
            EntityPlacer noEmPlacer = new EntityPlacer();
            noEmPlacer.setMap(map);
            Unit u = noEmPlacer.placeUnit(UnitType.CONFED_INFANTRY,
                new GridPosition(5, 5), 0);
            assertNull(u);
        }
    }

    @Nested
    @DisplayName("Can Place Checks")
    class CanPlaceChecks {

        @Test
        @DisplayName("canPlaceBuilding returns true for valid position")
        void canPlaceBuildingValid() {
            assertTrue(placer.canPlaceBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("canPlaceBuilding returns false for occupied position")
        void canPlaceBuildingOccupied() {
            placer.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 0);
            assertFalse(placer.canPlaceBuilding(BuildingType.CONFED_GENERATOR,
                new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("canPlaceBuilding returns false for impassable terrain")
        void canPlaceBuildingImpassable() {
            map.setTile(5, 5, TerrainType.MOUNTAIN);
            assertFalse(placer.canPlaceBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("canPlaceUnit returns true for valid position")
        void canPlaceUnitValid() {
            assertTrue(placer.canPlaceUnit(UnitType.CONFED_INFANTRY,
                new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("canPlaceUnit returns false for occupied position")
        void canPlaceUnitOccupied() {
            placer.placeUnit(UnitType.CONFED_INFANTRY, new GridPosition(5, 5), 0);
            assertFalse(placer.canPlaceUnit(UnitType.CONFED_GRENADIER,
                new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("canPlaceUnit returns false for null map")
        void canPlaceUnitNullMap() {
            EntityPlacer noMapPlacer = new EntityPlacer();
            assertFalse(noMapPlacer.canPlaceUnit(UnitType.CONFED_INFANTRY,
                new GridPosition(5, 5)));
        }
    }

    @Nested
    @DisplayName("Erase Entity")
    class EraseEntity {

        @Test
        @DisplayName("eraseEntity removes placed unit")
        void eraseEntityRemovesUnit() {
            placer.placeUnit(UnitType.CONFED_INFANTRY, new GridPosition(5, 5), 0);
            assertTrue(placer.eraseEntity(new GridPosition(5, 5)));
            assertNull(entityManager.findUnitAt(new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("eraseEntity removes placed building")
        void eraseEntityRemovesBuilding() {
            placer.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 0);
            assertTrue(placer.eraseEntity(new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("eraseEntity returns false for empty position")
        void eraseEntityEmptyPosition() {
            assertFalse(placer.eraseEntity(new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("eraseEntity with null entity manager returns false")
        void eraseEntityNullEntityManager() {
            EntityPlacer noEmPlacer = new EntityPlacer();
            assertFalse(noEmPlacer.eraseEntity(new GridPosition(5, 5)));
        }
    }
}
