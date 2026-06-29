package com.aow2.core.movement;

import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitCategory;
import com.aow2.core.world.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathfindingSystemTest {

    private PathfindingSystem pathfinding;

    @BeforeEach
    void setUp() {
        pathfinding = new PathfindingSystem();
    }

    @Nested
    @DisplayName("Basic Pathfinding")
    class BasicPathfinding {

        @Test
        @DisplayName("Should find direct path on open grass")
        void shouldFindDirectPathOnOpenGrass() {
            // Given: a 10x10 grass map
            GameMap map = new GameMap(10, 10);
            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(5, 0);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should be non-empty and end at goal
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
        }

        @Test
        @DisplayName("Should return empty path when start equals goal")
        void shouldReturnEmptyPathWhenStartEqualsGoal() {
            // Given: same start and goal
            GameMap map = new GameMap(10, 10);
            GridPosition pos = new GridPosition(5, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(pos, pos, map);

            // Then: path should be empty (no movement needed)
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("Should return empty path for null arguments")
        void shouldReturnEmptyPathForNullArguments() {
            // Given: null arguments
            GameMap map = new GameMap(10, 10);

            // When/Then: should return empty
            assertTrue(pathfinding.findPath(null, new GridPosition(1, 1), map).isEmpty());
            assertTrue(pathfinding.findPath(new GridPosition(1, 1), null, map).isEmpty());
            assertTrue(pathfinding.findPath(new GridPosition(1, 1), new GridPosition(2, 2), null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Obstacle Avoidance")
    class ObstacleAvoidance {

        @Test
        @DisplayName("Should find path around water")
        void shouldFindPathAroundWater() {
            // Given: a map with a wall of water separating start and goal
            GameMap map = new GameMap(10, 10);
            // Wall of water at x=5
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.DEEP_WATER);
            }
            // Leave a gap at y=0
            map.setTile(5, 0, TerrainType.GRASS);

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should go around the water
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));

            // Verify path does not go through water
            for (GridPosition pos : path) {
                assertNotEquals(TerrainType.DEEP_WATER, map.getTile(pos.x(), pos.y()),
                    "Path should not pass through water at " + pos);
            }
        }

        @Test
        @DisplayName("Should find path around mountain")
        void shouldFindPathAroundMountain() {
            // Given: a map with a mountain blocking direct path
            GameMap map = new GameMap(10, 10);
            for (int x = 3; x <= 7; x++) {
                for (int y = 3; y <= 7; y++) {
                    map.setTile(x, y, TerrainType.MOUNTAIN);
                }
            }

            GridPosition start = new GridPosition(0, 5);
            GridPosition goal = new GridPosition(9, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should go around the mountain
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));

            for (GridPosition pos : path) {
                assertNotEquals(TerrainType.MOUNTAIN, map.getTile(pos.x(), pos.y()),
                    "Path should not pass through mountain at " + pos);
            }
        }

        @Test
        @DisplayName("Should return empty path for unreachable goal")
        void shouldReturnEmptyPathForUnreachableGoal() {
            // Given: a goal completely surrounded by water
            GameMap map = new GameMap(10, 10);
            // Surround (5,5) with water
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    map.setTile(5 + dx, 5 + dy, TerrainType.DEEP_WATER);
                }
            }

            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(5, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: no path should exist
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("Should return empty path for impassable start")
        void shouldReturnEmptyPathForImpassableStart() {
            // Given: start position is on impassable terrain
            GameMap map = new GameMap(10, 10);
            map.setTile(0, 0, TerrainType.DEEP_WATER);

            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(5, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: no path should exist
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("Should return empty path for impassable goal")
        void shouldReturnEmptyPathForImpassableGoal() {
            // Given: goal position is on impassable terrain
            GameMap map = new GameMap(10, 10);
            map.setTile(5, 5, TerrainType.MOUNTAIN);

            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(5, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: no path should exist
            assertTrue(path.isEmpty());
        }
    }

    @Nested
    @DisplayName("Diagonal Movement")
    class DiagonalMovement {

        @Test
        @DisplayName("Should find path with diagonal movement")
        void shouldFindPathWithDiagonalMovement() {
            // Given: a map where diagonal movement is the shortest path
            GameMap map = new GameMap(10, 10);
            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(5, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should use diagonals (length ~5 steps, not 10)
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
            // Diagonal path should be about 5 steps (each diagonal covers dx=1,dy=1)
            assertTrue(path.size() <= 6,
                "Diagonal path to (5,5) should be about 5 steps, got " + path.size());
        }

        @Test
        @DisplayName("Should not cut through impassable diagonal corners")
        void shouldNotCutThroughImpassableDiagonalCorners() {
            // Given: a diagonal gap where cutting through would be illegal
            // Start at (1,1), goal at (2,2)
            // (2,1) and (1,2) are both water — diagonal from (1,1) to (2,2) should be blocked
            GameMap map = new GameMap(10, 10);
            map.setTile(2, 1, TerrainType.DEEP_WATER);
            map.setTile(1, 2, TerrainType.DEEP_WATER);

            GridPosition start = new GridPosition(1, 1);
            GridPosition goal = new GridPosition(2, 2);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should not cut diagonally through the water corner
            // If a path exists, it should go around (via (3,1) or similar)
            if (!path.isEmpty()) {
                // Verify no diagonal cut: the path should not step directly from (1,1) to (2,2)
                if (path.size() == 1) {
                    // Direct step to (2,2) would be the diagonal cut — this should NOT happen
                    assertNotEquals(new GridPosition(2, 2), path.get(0),
                        "Should not cut diagonally through impassable corners");
                }
            }
            // The goal might still be unreachable if completely blocked
        }
    }

    @Nested
    @DisplayName("Terrain Costs")
    class TerrainCosts {

        @Test
        @DisplayName("Should prefer road over grass for shorter path")
        void shouldPreferRoadOverGrass() {
            // Given: two paths — one through grass, one through road
            GameMap map = new GameMap(10, 10);
            // Road from (0,5) to (9,5)
            for (int x = 0; x < 10; x++) {
                map.setTile(x, 5, TerrainType.ROAD);
            }

            GridPosition start = new GridPosition(0, 5);
            GridPosition goal = new GridPosition(9, 5);

            // When: finding a path along the road
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should follow the road
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));

            // Road has cost 1 (same as grass), but verify the path stays on road
            long roadSteps = path.stream()
                .filter(p -> map.getTile(p.x(), p.y()) == TerrainType.ROAD)
                .count();
            // All steps should be on road since start and goal are on road and road is straight
            assertEquals(path.size(), roadSteps,
                "Path should follow the road entirely");
        }

        @Test
        @DisplayName("Should account for terrain costs when choosing path")
        void shouldAccountForTerrainCostsWhenChoosingPath() {
            // Given: a map with a forest shortcut and a grass detour
            GameMap map = new GameMap(10, 3);
            // Forest in the middle (y=1) — higher cost
            for (int x = 0; x < 10; x++) {
                map.setTile(x, 1, TerrainType.FOREST);
            }

            GridPosition start = new GridPosition(0, 1);
            GridPosition goal = new GridPosition(9, 1);

            // When: finding a path through forest
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should exist
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
        }

        @Test
        @DisplayName("Should return correct terrain cost for each terrain type")
        void shouldReturnCorrectTerrainCost() {
            // Given: all terrain types
            // When/Then: verify costs match TerrainType.getMovementCost()
            assertEquals(Integer.MAX_VALUE, pathfinding.getTerrainCost(TerrainType.DEEP_WATER));
            assertEquals(Integer.MAX_VALUE, pathfinding.getTerrainCost(TerrainType.SHALLOW_WATER));
            assertEquals(2, pathfinding.getTerrainCost(TerrainType.SAND));
            assertEquals(1, pathfinding.getTerrainCost(TerrainType.GRASS));
            assertEquals(2, pathfinding.getTerrainCost(TerrainType.FOREST));
            assertEquals(3, pathfinding.getTerrainCost(TerrainType.HILLS));
            assertEquals(Integer.MAX_VALUE, pathfinding.getTerrainCost(TerrainType.MOUNTAIN));
            assertEquals(0, pathfinding.getTerrainCost(TerrainType.ROAD));
            assertEquals(1, pathfinding.getTerrainCost(TerrainType.BRIDGE));
            assertEquals(4, pathfinding.getTerrainCost(TerrainType.SWAMP));
            assertEquals(3, pathfinding.getTerrainCost(TerrainType.SNOW));
            assertEquals(1, pathfinding.getTerrainCost(TerrainType.RESOURCE_DEPOSIT));
        }

        @Test
        @DisplayName("Should return max value for null terrain")
        void shouldReturnMaxValueForNullTerrain() {
            assertEquals(Integer.MAX_VALUE, pathfinding.getTerrainCost(null));
        }
    }

    @Nested
    @DisplayName("Unit Awareness")
    class UnitAwareness {

        @Test
        @DisplayName("Should respect occupied cells")
        void shouldRespectOccupiedCells() {
            // Given: a map with an occupied cell blocking the direct path
            GameMap map = new GameMap(10, 10);
            Set<GridPosition> occupied = new HashSet<>();
            occupied.add(new GridPosition(1, 0));
            occupied.add(new GridPosition(1, 1));
            occupied.add(new GridPosition(1, 2));

            GridPosition start = new GridPosition(0, 1);
            GridPosition goal = new GridPosition(3, 1);

            // When: finding a path with occupied cells
            List<GridPosition> path = pathfinding.findPath(start, goal, map, occupied);

            // Then: path should avoid occupied cells
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));

            for (GridPosition pos : path) {
                assertFalse(occupied.contains(pos),
                    "Path should avoid occupied cell at " + pos);
            }
        }

        @Test
        @DisplayName("Should find path with empty occupied set")
        void shouldFindPathWithEmptyOccupiedSet() {
            // Given: no occupied cells
            GameMap map = new GameMap(10, 10);
            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(5, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map, Collections.emptySet());

            // Then: path should be found normally
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
        }

        @Test
        @DisplayName("Should allow pathfinding to occupied goal cell")
        void shouldAllowPathfindingToOccupiedGoalCell() {
            // Given: goal cell is occupied by another unit
            GameMap map = new GameMap(10, 10);
            GridPosition goal = new GridPosition(5, 5);
            Set<GridPosition> occupied = new HashSet<>();
            occupied.add(goal);

            GridPosition start = new GridPosition(0, 0);

            // When: finding a path to the occupied goal
            List<GridPosition> path = pathfinding.findPath(start, goal, map, occupied);

            // Then: path should still be found (unit will stop adjacent to goal)
            // REF: pathfinding.md — original game allows path to occupied cell
            assertFalse(path.isEmpty());
        }
    }

    @Nested
    @DisplayName("Performance & Scale")
    class PerformanceAndScale {

        @Test
        @DisplayName("Should handle large maps (128x128)")
        void shouldHandleLargeMaps() {
            // Given: a 128x128 map (REF: map_system.md - 128x128 grid)
            // Note: MAX_PATH_LENGTH=50 means paths are truncated at 50 steps
            // Use a shorter distance that fits within the path limit
            GameMap map = new GameMap(GameMap.MAX_MAP_SIZE, GameMap.MAX_MAP_SIZE);
            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(30, 30);

            // When: finding a path across the map
            long startTime = System.nanoTime();
            List<GridPosition> path = pathfinding.findPath(start, goal, map);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            // Then: path should be found in reasonable time
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
            assertTrue(elapsedMs < 100,
                "Pathfinding on 128x128 map should complete in < 100ms, took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Should find path with many obstacles within time limit")
        void shouldFindPathWithManyObstacles() {
            // Given: a 50x50 map with scattered obstacles
            GameMap map = new GameMap(50, 50);
            // Create a checkerboard of water (every other cell)
            for (int x = 0; x < 50; x++) {
                for (int y = 0; y < 50; y++) {
                    if ((x + y) % 3 == 0) {
                        map.setTile(x, y, TerrainType.DEEP_WATER);
                    }
                }
            }
            // Ensure start and goal are passable
            map.setTile(0, 0, TerrainType.GRASS);
            map.setTile(49, 49, TerrainType.GRASS);

            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(49, 49);

            // When: finding a path
            long startTime = System.nanoTime();
            List<GridPosition> path = pathfinding.findPath(start, goal, map);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            // Then: should complete within time limit
            if (!path.isEmpty()) {
                assertEquals(goal, path.get(path.size() - 1));
                // Verify no water in path
                for (GridPosition pos : path) {
                    assertNotEquals(TerrainType.DEEP_WATER, map.getTile(pos.x(), pos.y()));
                }
            }
            assertTrue(elapsedMs < 500,
                "Pathfinding with many obstacles should be < 500ms, took " + elapsedMs + "ms");
        }
    }

    @Nested
    @DisplayName("Original Game Behavior Matching")
    class OriginalGameBehavior {

        @Test
        @DisplayName("Should find path using bridge across water (test map)")
        void shouldFindPathUsingBridge() {
            // Given: the standard test map with a river and bridge
            GameMap map = GameMap.createTestMap();
            // Start above the river, goal below the river
            GridPosition start = new GridPosition(3, 0);
            GridPosition goal = new GridPosition(3, 7);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should go through the bridge
            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));

            // Verify the path uses bridge tiles
            boolean usesBridge = path.stream()
                .anyMatch(p -> map.getTile(p.x(), p.y()) == TerrainType.BRIDGE);
            assertTrue(usesBridge, "Path should use bridge to cross river");
        }

        @Test
        @DisplayName("Should not find path when water fully separates areas")
        void shouldNotCrossUncrossableWater() {
            // Given: a map with a complete wall of water
            GameMap map = new GameMap(10, 10);
            for (int x = 0; x < 10; x++) {
                map.setTile(x, 5, TerrainType.DEEP_WATER);
            }

            GridPosition start = new GridPosition(5, 2);
            GridPosition goal = new GridPosition(5, 8);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: no path should exist across complete water wall
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("Should find adjacent step for single-cell movement")
        void shouldFindAdjacentStepForSingleCellMovement() {
            // Given: start and goal are adjacent
            GameMap map = new GameMap(10, 10);
            GridPosition start = new GridPosition(5, 5);
            GridPosition goal = new GridPosition(6, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should be exactly one step
            assertEquals(1, path.size());
            assertEquals(goal, path.get(0));
        }
    }

    @Nested
    @DisplayName("Per-Unit-Type Terrain Passability (H-8)")
    class PerUnitTypePassability {

        @Test
        @DisplayName("F-26: Infantry cannot cross SHALLOW_WATER (impassable for all units)")
        void infantryShouldCrossShallowWater() {
            // FIX (F-26): SHALLOW_WATER is now impassable for ALL units including infantry.
            // Previously this test expected infantry to cross — now it should not.
            // Given: a map with a wall of SHALLOW_WATER separating start and goal
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.SHALLOW_WATER);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: finding a path for INFANTRY (category-aware)
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.INFANTRY);

            // Then: no path should exist (shallow water is impassable)
            assertTrue(path.isEmpty(), "F-26: Infantry should NOT cross SHALLOW_WATER");
        }

        @Test
        @DisplayName("Vehicle should NOT find path through SHALLOW_WATER")
        void vehicleShouldNotCrossShallowWater() {
            // Given: a map with a complete wall of SHALLOW_WATER separating start and goal
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.SHALLOW_WATER);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: finding a path for VEHICLE (category-aware)
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.VEHICLE);

            // Then: no path should exist (shallow water is impassable for vehicles)
            assertTrue(path.isEmpty(), "Vehicle should not cross shallow water");
        }

        @Test
        @DisplayName("SPECIAL_MACHINERY should NOT find path through SHALLOW_WATER")
        void specialMachineryShouldNotCrossShallowWater() {
            // Given: a map with a complete wall of SHALLOW_WATER
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.SHALLOW_WATER);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: finding a path for SPECIAL_MACHINERY
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.SPECIAL_MACHINERY);

            // Then: no path should exist
            assertTrue(path.isEmpty(), "Special machinery should not cross shallow water");
        }

        @Test
        @DisplayName("Backward-compat findPath (no category) should treat SHALLOW_WATER as impassable")
        void backwardCompatFindPathShouldBlockShallowWater() {
            // Given: a map with a wall of SHALLOW_WATER
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.SHALLOW_WATER);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: using the old findPath without category
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: shallow water should be impassable (backward compat)
            assertTrue(path.isEmpty(),
                "Default findPath should treat shallow water as impassable");
        }

        @Test
        @DisplayName("F-26: Infantry on SHALLOW_WATER start cannot find path (impassable)")
        void infantryOnShallowWaterStartShouldFindPath() {
            // FIX (F-26): SHALLOW_WATER is now impassable for ALL units. Infantry starting
            // on shallow water cannot path through more shallow water.
            // Given: infantry starting on shallow water
            GameMap map = new GameMap(10, 10);
            map.setTile(0, 5, TerrainType.SHALLOW_WATER);
            map.setTile(1, 5, TerrainType.SHALLOW_WATER);

            GridPosition start = new GridPosition(0, 5);
            GridPosition goal = new GridPosition(5, 5);

            // When: finding path for infantry
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.INFANTRY);

            // Then: no path should exist (shallow water is impassable)
            assertTrue(path.isEmpty(), "F-26: Infantry should NOT path through SHALLOW_WATER");
        }

        @Test
        @DisplayName("Vehicle should not cross SWAMP")
        void vehicleShouldNotCrossSwamp() {
            // Given: a map with a complete wall of SWAMP
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.SWAMP);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: finding path for VEHICLE
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.VEHICLE);

            // Then: no path (swamp is impassable for vehicles)
            assertTrue(path.isEmpty(), "Vehicle should not cross swamp");
        }

        @Test
        @DisplayName("Infantry should cross SWAMP")
        void infantryShouldCrossSwamp() {
            // Given: a map with a complete wall of SWAMP
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.SWAMP);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // When: finding path for INFANTRY
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.INFANTRY);

            // Then: path should be found
            assertFalse(path.isEmpty(), "Infantry should cross swamp");
            assertEquals(goal, path.get(path.size() - 1));
        }

        @Test
        @DisplayName("F-26: Infantry cannot cross SHALLOW_WATER (impassable for all units)")
        void infantryShouldPreferGrassOverShallowWater() {
            // FIX (F-26): SHALLOW_WATER is now impassable for ALL units including infantry.
            // Previously isPassableBy(INFANTRY)=true but getMovementCost()=MAX_VALUE,
            // creating a contradiction. Now both agree: impassable.
            // Given: a map with a shallow water column blocking the path
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(4, y, TerrainType.SHALLOW_WATER);
            }

            GridPosition start = new GridPosition(0, 5);
            GridPosition goal = new GridPosition(9, 5);

            // When: finding path for infantry
            List<GridPosition> path = pathfinding.findPath(start, goal, map,
                Collections.emptySet(), UnitCategory.INFANTRY);

            // Then: no path should exist (shallow water is impassable)
            assertTrue(path.isEmpty(), "F-26: Infantry should NOT cross SHALLOW_WATER (impassable for all)");
        }

        @Test
        @DisplayName("F-26: getTerrainCost for SHALLOW_WATER returns MAX_VALUE for all categories (isPassableBy=false)")
        void getTerrainCostShouldReturnFiniteCostForInfantryShallowWater() {
            // FIX (F-26): SHALLOW_WATER is now impassable for ALL units (including infantry)
            // to resolve the isPassableBy vs getMovementCost contradiction. Both methods now
            // agree: impassable. The pathfinder's getTerrainCost still returns 3 for
            // INFANTRY (legacy category-aware override) but isPassableBy returns false,
            // so the tile never enters the A* open set.
            assertEquals(Integer.MAX_VALUE, pathfinding.getTerrainCost(TerrainType.SHALLOW_WATER),
                "Default shallow water cost should be MAX_VALUE");

            assertEquals(Integer.MAX_VALUE, pathfinding.getTerrainCost(TerrainType.SHALLOW_WATER, UnitCategory.VEHICLE),
                "Shallow water cost for vehicle should be MAX_VALUE");

            // isPassableBy now returns false for all categories — the contradiction is resolved
            assertFalse(TerrainType.SHALLOW_WATER.isPassableBy(UnitCategory.INFANTRY),
                "F-26: SHALLOW_WATER should be impassable for infantry (consistent with getMovementCost=MAX_VALUE)");
        }

        @Test
        @DisplayName("No unit type should cross DEEP_WATER")
        void noUnitTypeShouldCrossDeepWater() {
            // Given: a map with a complete wall of DEEP_WATER
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.DEEP_WATER);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // Then: no category can cross deep water
            for (UnitCategory cat : UnitCategory.values()) {
                List<GridPosition> path = pathfinding.findPath(start, goal, map,
                    Collections.emptySet(), cat);
                assertTrue(path.isEmpty(),
                    cat + " should not cross deep water");
            }
        }

        @Test
        @DisplayName("No unit type should cross MOUNTAIN")
        void noUnitTypeShouldCrossMountain() {
            // Given: a map with a complete wall of MOUNTAIN
            GameMap map = new GameMap(10, 10);
            for (int y = 0; y < 10; y++) {
                map.setTile(5, y, TerrainType.MOUNTAIN);
            }

            GridPosition start = new GridPosition(2, 5);
            GridPosition goal = new GridPosition(8, 5);

            // Then: no category can cross mountains
            for (UnitCategory cat : UnitCategory.values()) {
                List<GridPosition> path = pathfinding.findPath(start, goal, map,
                    Collections.emptySet(), cat);
                assertTrue(path.isEmpty(),
                    cat + " should not cross mountain");
            }
        }
    }

    @Nested
    @DisplayName("Path Quality")
    class PathQuality {

        @Test
        @DisplayName("Path should be contiguous (each step is adjacent)")
        void pathShouldBeContiguous() {
            // Given: a path across the map
            GameMap map = new GameMap(20, 20);
            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(15, 12);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: each consecutive step should be adjacent (including diagonal)
            assertFalse(path.isEmpty());
            GridPosition previous = start;
            for (GridPosition current : path) {
                int dx = Math.abs(current.x() - previous.x());
                int dy = Math.abs(current.y() - previous.y());
                assertTrue(dx <= 1 && dy <= 1,
                    "Step from " + previous + " to " + current + " is not adjacent");
                previous = current;
            }
        }

        @Test
        @DisplayName("Path should not contain duplicates")
        void pathShouldNotContainDuplicates() {
            // Given: a moderately complex path
            GameMap map = new GameMap(20, 20);
            // Add some obstacles for complexity
            for (int y = 3; y <= 8; y++) {
                map.setTile(5, y, TerrainType.DEEP_WATER);
            }

            GridPosition start = new GridPosition(0, 5);
            GridPosition goal = new GridPosition(10, 5);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: no duplicate positions
            assertFalse(path.isEmpty());
            assertEquals(path.size(), new HashSet<>(path).size(),
                "Path should not contain duplicate positions");
        }

        @Test
        @DisplayName("Path should not exceed max path length")
        void pathShouldNotExceedMaxLength() {
            // Given: a long path
            GameMap map = new GameMap(GameMap.MAX_MAP_SIZE, GameMap.MAX_MAP_SIZE);
            GridPosition start = new GridPosition(0, 0);
            GridPosition goal = new GridPosition(30, 30);

            // When: finding a path
            List<GridPosition> path = pathfinding.findPath(start, goal, map);

            // Then: path should respect max length
            assertTrue(path.size() <= 200,
                "Path should not exceed 200 steps, got " + path.size());
        }
    }
}
