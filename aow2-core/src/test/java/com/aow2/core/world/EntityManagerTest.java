package com.aow2.core.world;

import com.aow2.common.model.*;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityManagerTest {

    /**
     * Creates a simple Infantry stat block for testing.
     */
    private UnitStats createInfantryStats() {
        return new UnitStats(
            UnitType.REBEL_INFANTRY, "Infantry", 40, 5,
            4, 5, 0, 8, 5, WeaponType.BULLET, 5, 30, 10, 5, 10, 0, 0, 1
        );
    }

    /**
     * Creates a simple Barracks stat block for testing.
     */
    private BuildingStats createBarracksStats() {
        return new BuildingStats(
            BuildingType.REBEL_BARRACKS, 80, 30, 0, 5, 0,
            8, 40, 0, 10, 5, 0, 5, 0, 30, 15, 0, WeaponType.NONE, List.of());
    }

    @Nested
    @DisplayName("Unit Management")
    class UnitManagement {

        @Test
        @DisplayName("Should add and get unit by ID")
        void shouldAddAndGetUnit() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();
            Unit unit = new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5), UnitType.REBEL_INFANTRY, stats);

            manager.addUnit(unit);

            Unit retrieved = manager.getUnit(1);
            assertNotNull(retrieved);
            assertEquals(1, retrieved.getId());
            assertEquals(UnitType.REBEL_INFANTRY, retrieved.getUnitType());
        }

        @Test
        @DisplayName("Should return null for non-existent unit ID")
        void shouldReturnNullForMissingUnit() {
            EntityManager manager = new EntityManager();
            assertNull(manager.getUnit(999));
        }

        @Test
        @DisplayName("Should filter units by faction")
        void shouldFilterByFaction() {
            EntityManager manager = new EntityManager();
            UnitStats rebelStats = createInfantryStats();
            UnitStats confedStats = new UnitStats(
                UnitType.CONFED_INFANTRY, "Infantry", 40, 5,
                4, 5, 0, 8, 5, WeaponType.BULLET, 5, 30, 10, 5, 10, 0, 0, 1
            );

            manager.addUnit(new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, rebelStats));
            manager.addUnit(new Unit(2, Faction.RESISTANCE, new GridPosition(1, 1), UnitType.REBEL_GRENADIER, rebelStats));
            manager.addUnit(new Unit(3, Faction.CONFEDERATION, new GridPosition(2, 2), UnitType.CONFED_INFANTRY, confedStats));

            List<Unit> rebelUnits = manager.getUnitsForPlayer(Faction.RESISTANCE);
            assertEquals(2, rebelUnits.size());

            List<Unit> confedUnits = manager.getUnitsForPlayer(Faction.CONFEDERATION);
            assertEquals(1, confedUnits.size());
        }

        @Test
        @DisplayName("Should exclude dead units from getAliveUnitsForPlayer")
        void shouldExcludeDeadUnits() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();

            Unit alive = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);
            Unit dead = new Unit(2, Faction.RESISTANCE, new GridPosition(1, 1), UnitType.REBEL_INFANTRY, stats);
            dead.takeDamage(100); // kill

            manager.addUnit(alive);
            manager.addUnit(dead);

            List<Unit> allUnits = manager.getUnitsForPlayer(Faction.RESISTANCE);
            assertEquals(2, allUnits.size()); // includes dead

            List<Unit> aliveUnits = manager.getAliveUnitsForPlayer(Faction.RESISTANCE);
            assertEquals(1, aliveUnits.size()); // excludes dead
            assertEquals(1, aliveUnits.get(0).getId());
        }

        @Test
        @DisplayName("Should return all units")
        void shouldReturnAllUnits() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();

            manager.addUnit(new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats));
            manager.addUnit(new Unit(2, Faction.CONFEDERATION, new GridPosition(1, 1), UnitType.CONFED_INFANTRY, stats));

            assertEquals(2, manager.getAllUnits().size());
        }
    }

    @Nested
    @DisplayName("Building Management")
    class BuildingManagement {

        @Test
        @DisplayName("Should add and get building by ID")
        void shouldAddAndGetBuilding() {
            EntityManager manager = new EntityManager();
            BuildingStats stats = createBarracksStats();
            Building building = new Building(10, Faction.RESISTANCE, new GridPosition(3, 3),
                BuildingType.REBEL_BARRACKS, stats);

            manager.addBuilding(building);

            Building retrieved = manager.getBuilding(10);
            assertNotNull(retrieved);
            assertEquals(10, retrieved.getId());
            assertEquals(BuildingType.REBEL_BARRACKS, retrieved.getBuildingType());
        }

        @Test
        @DisplayName("Should return null for non-existent building ID")
        void shouldReturnNullForMissingBuilding() {
            EntityManager manager = new EntityManager();
            assertNull(manager.getBuilding(999));
        }

        @Test
        @DisplayName("Should filter buildings by faction")
        void shouldFilterByFaction() {
            EntityManager manager = new EntityManager();
            BuildingStats rebelStats = createBarracksStats();
            BuildingStats confedStats = new BuildingStats(
                BuildingType.CONFED_INFANTRY_CENTRE, 80, 30, 0, 5, 0,
                8, 40, 0, 10, 5, 0, 5, 0, 30, 15, 0, WeaponType.NONE, List.of());

            manager.addBuilding(new Building(1, Faction.RESISTANCE, new GridPosition(0, 0),
                BuildingType.REBEL_BARRACKS, rebelStats));
            manager.addBuilding(new Building(2, Faction.CONFEDERATION, new GridPosition(5, 5),
                BuildingType.CONFED_INFANTRY_CENTRE, confedStats));

            assertEquals(1, manager.getBuildingsForPlayer(Faction.RESISTANCE).size());
            assertEquals(1, manager.getBuildingsForPlayer(Faction.CONFEDERATION).size());
        }
    }

    @Nested
    @DisplayName("Dead Entity Cleanup")
    class DeadEntityCleanup {

        @Test
        @DisplayName("Should remove dead units")
        void shouldRemoveDeadUnits() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();

            Unit alive = new Unit(1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);
            Unit dead = new Unit(2, Faction.RESISTANCE, new GridPosition(1, 1), UnitType.REBEL_INFANTRY, stats);
            dead.takeDamage(100); // kill

            manager.addUnit(alive);
            manager.addUnit(dead);
            assertEquals(2, manager.unitCount());

            manager.removeDeadEntities();
            assertEquals(1, manager.unitCount());
            assertNotNull(manager.getUnit(1));
            assertNull(manager.getUnit(2));
        }

        @Test
        @DisplayName("Should remove destroyed buildings")
        void shouldRemoveDestroyedBuildings() {
            EntityManager manager = new EntityManager();
            BuildingStats stats = createBarracksStats();

            Building alive = new Building(1, Faction.RESISTANCE, new GridPosition(0, 0),
                BuildingType.REBEL_BARRACKS, stats);
            Building destroyed = new Building(2, Faction.RESISTANCE, new GridPosition(5, 5),
                BuildingType.REBEL_BARRACKS, stats);
            destroyed.takeDamage(200); // destroy

            manager.addBuilding(alive);
            manager.addBuilding(destroyed);
            assertEquals(2, manager.buildingCount());

            manager.removeDeadEntities();
            assertEquals(1, manager.buildingCount());
            assertNotNull(manager.getBuilding(1));
            assertNull(manager.getBuilding(2));
        }
    }

    @Nested
    @DisplayName("Entity ID Allocation")
    class EntityIdAllocation {

        @Test
        @DisplayName("Should allocate sequential unique IDs")
        void shouldAllocateSequentialIds() {
            EntityManager manager = new EntityManager();

            int id1 = manager.allocateEntityId();
            int id2 = manager.allocateEntityId();
            int id3 = manager.allocateEntityId();

            assertEquals(1, id1);
            assertEquals(2, id2);
            assertEquals(3, id3);
        }

        @Test
        @DisplayName("Should not reuse IDs after cleanup")
        void shouldNotReuseIdsAfterCleanup() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();

            int id1 = manager.allocateEntityId();
            Unit unit = new Unit(id1, Faction.RESISTANCE, new GridPosition(0, 0), UnitType.REBEL_INFANTRY, stats);
            manager.addUnit(unit);

            unit.takeDamage(100);
            manager.removeDeadEntities();

            int id2 = manager.allocateEntityId();
            assertEquals(2, id2); // not reused
        }
    }

    @Nested
    @DisplayName("Spatial Queries")
    class SpatialQueries {

        @Test
        @DisplayName("Should find unit at position")
        void shouldFindUnitAtPosition() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();
            GridPosition pos = new GridPosition(5, 5);

            manager.addUnit(new Unit(1, Faction.RESISTANCE, pos, UnitType.REBEL_INFANTRY, stats));

            Unit found = manager.findUnitAt(pos);
            assertNotNull(found);
            assertEquals(1, found.getId());
        }

        @Test
        @DisplayName("Should return null when no unit at position")
        void shouldReturnNullWhenNoUnitAtPosition() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();

            manager.addUnit(new Unit(1, Faction.RESISTANCE, new GridPosition(5, 5), UnitType.REBEL_INFANTRY, stats));

            assertNull(manager.findUnitAt(new GridPosition(0, 0)));
        }

        @Test
        @DisplayName("Should not find dead unit at position")
        void shouldNotFindDeadUnitAtPosition() {
            EntityManager manager = new EntityManager();
            UnitStats stats = createInfantryStats();
            GridPosition pos = new GridPosition(5, 5);

            Unit dead = new Unit(1, Faction.RESISTANCE, pos, UnitType.REBEL_INFANTRY, stats);
            dead.takeDamage(100);
            manager.addUnit(dead);

            assertNull(manager.findUnitAt(pos));
        }

        @Test
        @DisplayName("Should find building at position")
        void shouldFindBuildingAtPosition() {
            EntityManager manager = new EntityManager();
            BuildingStats stats = createBarracksStats();
            GridPosition pos = new GridPosition(3, 3);

            manager.addBuilding(new Building(1, Faction.RESISTANCE, pos, BuildingType.REBEL_BARRACKS, stats));

            Building found = manager.findBuildingAt(pos);
            assertNotNull(found);
            assertEquals(1, found.getId());
        }

        @Test
        @DisplayName("Should return null when no building at position")
        void shouldReturnNullWhenNoBuildingAtPosition() {
            EntityManager manager = new EntityManager();
            BuildingStats stats = createBarracksStats();

            manager.addBuilding(new Building(1, Faction.RESISTANCE, new GridPosition(3, 3), BuildingType.REBEL_BARRACKS, stats));

            assertNull(manager.findBuildingAt(new GridPosition(0, 0)));
        }
    }
}
