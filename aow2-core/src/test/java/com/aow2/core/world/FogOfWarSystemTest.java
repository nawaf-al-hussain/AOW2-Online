package com.aow2.core.world;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.FogOfWarSystem.TileVisibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the FogOfWarSystem.
 * REF: unit_stats.md - sightRange per unit type
 */
class FogOfWarSystemTest {

    private FogOfWarSystem fogOfWar;
    private GameMap map;
    private EntityManager entities;

    @BeforeEach
    void setUp() {
        fogOfWar = new FogOfWarSystem();
        map = new GameMap(16, 16);
        entities = new EntityManager();
    }

    @Test
    @DisplayName("All tiles start as UNEXPLORED before initialization")
    void allTilesStartUnexplored() {
        GridPosition pos = new GridPosition(5, 5);
        assertEquals(TileVisibility.UNEXPLORED, fogOfWar.getVisibility(0, pos));
    }

    @Test
    @DisplayName("After initialization, all tiles are UNEXPLORED")
    void afterInitAllUnexplored() {
        fogOfWar.initialize(map);

        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                GridPosition pos = new GridPosition(x, y);
                assertEquals(TileVisibility.UNEXPLORED, fogOfWar.getVisibility(0, pos),
                    "Tile (" + x + "," + y + ") should be UNEXPLORED");
            }
        }
    }

    @Test
    @DisplayName("isVisible returns false for unexplored tiles")
    void isVisibleFalseForUnexplored() {
        fogOfWar.initialize(map);
        assertFalse(fogOfWar.isVisible(0, new GridPosition(5, 5)));
    }

    @Test
    @DisplayName("isExplored returns false for unexplored tiles")
    void isExploredFalseForUnexplored() {
        fogOfWar.initialize(map);
        assertFalse(fogOfWar.isExplored(0, new GridPosition(5, 5)));
    }

    @Test
    @DisplayName("Unit with sight range reveals surrounding tiles as VISIBLE")
    void unitRevealsSurroundingTiles() {
        fogOfWar.initialize(map);

        // Create a unit at position (8, 8) with sight range 4
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5, 0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
        Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(8, 8), UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        fogOfWar.updateVisibility(0, entities, map);

        // Tiles within sight range should be VISIBLE
        assertTrue(fogOfWar.isVisible(0, new GridPosition(8, 8)));
        assertTrue(fogOfWar.isVisible(0, new GridPosition(10, 8)));
        assertTrue(fogOfWar.isVisible(0, new GridPosition(8, 10)));

        // Tiles beyond sight range should be UNEXPLORED
        assertFalse(fogOfWar.isVisible(0, new GridPosition(13, 8)));
        assertEquals(TileVisibility.UNEXPLORED, fogOfWar.getVisibility(0, new GridPosition(0, 0)));
    }

    @Test
    @DisplayName("Previously visible tiles become EXPLORED when unit moves away")
    void visibleTilesBecomeExploredWhenUnitLeaves() {
        fogOfWar.initialize(map);

        // Create a unit at position (8, 8) with sight range 4
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5, 0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
        Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(8, 8), UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        fogOfWar.updateVisibility(0, entities, map);

        // Verify tile is VISIBLE
        GridPosition pos = new GridPosition(8, 8);
        assertEquals(TileVisibility.VISIBLE, fogOfWar.getVisibility(0, pos));

        // Remove the unit (simulates it moving away)
        entities.removeDeadEntities();
        // We need to actually remove the unit since it's alive
        // Create a new entity manager without the unit
        EntityManager emptyEntities = new EntityManager();

        fogOfWar.updateVisibility(0, emptyEntities, map);

        // Previously visible tile should now be EXPLORED
        assertEquals(TileVisibility.EXPLORED, fogOfWar.getVisibility(0, pos));
        assertTrue(fogOfWar.isExplored(0, pos));
        assertFalse(fogOfWar.isVisible(0, pos));
    }

    @Test
    @DisplayName("Building reveals tiles around it")
    void buildingRevealsTiles() {
        fogOfWar.initialize(map);

        // Create a building at position (5, 5) with sight range
        BuildingStats bStats = new BuildingStats(BuildingType.CONFED_COMMAND_CENTRE, 120, 100, 0, 10, 0, 10, 60, 0, 15, 0, 0, 5, 0, 100, 50, java.util.List.of());
        Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            BuildingType.CONFED_COMMAND_CENTRE, bStats);
        building.setConstructionProgress(200); // Complete construction
        building.setPowered(true);
        entities.addBuilding(building);

        fogOfWar.updateVisibility(0, entities, map);

        // Building position should be visible
        assertTrue(fogOfWar.isVisible(0, new GridPosition(5, 5)));
        assertTrue(fogOfWar.isExplored(0, new GridPosition(5, 5)));
    }

    @Test
    @DisplayName("Player 0 visibility does not affect player 1")
    void playerVisibilityIsIndependent() {
        fogOfWar.initialize(map);

        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5, 0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
        Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(8, 8), UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        fogOfWar.updateVisibility(0, entities, map);

        // Player 0 should see the tile (Confederation is player 0)
        assertTrue(fogOfWar.isVisible(0, new GridPosition(8, 8)));

        // Player 1 should not have visibility updated yet (they have no units)
        fogOfWar.updateVisibility(1, entities, map);
        assertFalse(fogOfWar.isVisible(1, new GridPosition(8, 8)));
    }

    @Test
    @DisplayName("Reset clears all visibility to UNEXPLORED")
    void resetClearsVisibility() {
        fogOfWar.initialize(map);

        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5, 0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
        Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(8, 8), UnitType.CONFED_INFANTRY, stats);
        entities.addUnit(unit);

        fogOfWar.updateVisibility(0, entities, map);
        assertTrue(fogOfWar.isVisible(0, new GridPosition(8, 8)));

        fogOfWar.reset(0);
        assertEquals(TileVisibility.UNEXPLORED, fogOfWar.getVisibility(0, new GridPosition(8, 8)));
    }

    @Test
    @DisplayName("Out of bounds positions return UNEXPLORED")
    void outOfBoundsReturnsUnexplored() {
        fogOfWar.initialize(map);
        // GridPosition validation prevents creating out-of-bounds positions,
        // but we test the boundary conditions
        assertEquals(TileVisibility.UNEXPLORED, fogOfWar.getVisibility(0, new GridPosition(0, 0)));
        assertEquals(TileVisibility.UNEXPLORED, fogOfWar.getVisibility(5, new GridPosition(0, 0)));
    }
}
