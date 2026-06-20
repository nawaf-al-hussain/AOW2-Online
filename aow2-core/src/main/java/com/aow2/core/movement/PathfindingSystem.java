package com.aow2.core.movement;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitCategory;
import com.aow2.core.world.GameMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* pathfinding on the game grid.
 * <p>
 * REF: pathfinding.md — original game uses Bresenham line + obstacle avoidance routing.
 * ASSUMPTION (L5): We use a true A* algorithm instead of the original Bresenham-based approach.
 * This is a deliberate improvement: A* produces shorter paths and handles complex terrain better,
 * but paths may differ from the original game in edge cases, which could cause minor
 * replay divergence if comparing path choices tick-by-tick.
 * <p>
 * Key differences from the original game's Bresenham-based approach:
 * - We use a true A* algorithm instead of Bresenham line + obstacle avoidance.
 * - We support up to 200 steps (original was 50).
 * - We use octile distance as the heuristic (admissible for 8-directional movement).
 * - Diagonal movement cost is base * sqrt(2) ≈ 1.41.
 */
public final class PathfindingSystem {

    private static final Logger LOG = LoggerFactory.getLogger(PathfindingSystem.class);

    /** 8-directional movement deltas: N, NE, E, SE, S, SW, W, NW. */
    private static final int[][] DIRECTIONS = {
        {0, -1},   // NORTH
        {1, -1},   // NORTH_EAST
        {1, 0},    // EAST
        {1, 1},    // SOUTH_EAST
        {0, 1},    // SOUTH
        {-1, 1},   // SOUTH_WEST
        {-1, 0},   // WEST
        {-1, -1}   // NORTH_WEST
    };

    /** Indices of diagonal directions in the DIRECTIONS array. */
    private static final Set<Integer> DIAGONAL_INDICES = Set.of(1, 3, 5, 7);

    public PathfindingSystem() {
        // Stateless; all state is local to each findPath call
    }

    /**
     * Finds the shortest path from start to goal using A*.
     * Does not consider other units as obstacles.
     * Uses default terrain passability (isPassable()) — does not support per-unit-type
     * terrain like SHALLOW_WATER for infantry. Use the category-aware overload instead.
     *
     * @param start starting grid position
     * @param goal  target grid position
     * @param map   the game map
     * @return list of GridPosition waypoints (excluding start, including goal), or empty list if no path
     */
    public List<GridPosition> findPath(GridPosition start, GridPosition goal, GameMap map) {
        return findPath(start, goal, map, Collections.emptySet());
    }

    /**
     * Finds path with unit awareness (avoids occupied cells).
     * Uses default terrain passability (isPassable()).
     * REF: pathfinding.md — isPassable checks bW[y][x] for unit occupancy
     *
     * @param start    starting grid position
     * @param goal     target grid position
     * @param map      the game map
     * @param occupied set of grid positions occupied by units (treated as impassable)
     * @return list of GridPosition waypoints (excluding start, including goal), or empty list if no path
     */
    public List<GridPosition> findPath(GridPosition start, GridPosition goal, GameMap map,
                                        Set<GridPosition> occupied) {
        return findPath(start, goal, map, occupied, (UnitCategory) null);
    }

    /**
     * Finds path with unit category-aware terrain passability and unit avoidance.
     * This is the primary pathfinding method that supports per-unit-type terrain rules.
     * For example, infantry can cross SHALLOW_WATER while vehicles cannot.
     *
     * @param start    starting grid position
     * @param goal     target grid position
     * @param map      the game map
     * @param occupied set of grid positions occupied by units (treated as impassable)
     * @param category the unit category determining terrain passability, or null for default passability
     * @return list of GridPosition waypoints (excluding start, including goal), or empty list if no path
     */
    public List<GridPosition> findPath(GridPosition start, GridPosition goal, GameMap map,
                                        Set<GridPosition> occupied, UnitCategory category) {
        // Validate inputs
        if (start == null || goal == null || map == null) {
            LOG.warn("Null argument passed to findPath");
            return Collections.emptyList();
        }

        // Check if start or goal is impassable terrain for this unit category
        boolean startPassable = category != null
            ? map.isPassable(start.x(), start.y(), category)
            : map.isPassable(start.x(), start.y());
        if (!startPassable) {
            LOG.debug("Start position {} is impassable for category {}", start, category);
            return Collections.emptyList();
        }

        boolean goalPassable = category != null
            ? map.isPassable(goal.x(), goal.y(), category)
            : map.isPassable(goal.x(), goal.y());
        if (!goalPassable) {
            LOG.debug("Goal position {} is impassable for category {}", goal, category);
            return Collections.emptyList();
        }

        // Start == goal: no movement needed
        if (start.equals(goal)) {
            return Collections.emptyList();
        }

        // Goal is occupied by a unit (unless it's the start position)
        if (occupied.contains(goal) && !start.equals(goal)) {
            LOG.debug("Goal position {} is occupied by a unit", goal);
            // REF: pathfinding.md — original game still finds a path to an occupied cell
            // but the unit will stop adjacent to it. We allow pathfinding to the goal
            // and let the movement system handle the final approach.
        }

        // A* search
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Set<GridPosition> closedSet = new HashSet<>();
        Map<GridPosition, PathNode> openMap = new HashMap<>();

        PathNode startNode = new PathNode(start, 0, heuristic(start, goal), null, false);
        openSet.add(startNode);
        openMap.put(start, startNode);

        int stepsExplored = 0;

        while (!openSet.isEmpty()) {
            PathNode current = openSet.poll();

            // Goal reached
            if (current.position().equals(goal)) {
                List<GridPosition> path = reconstructPath(current);
                LOG.debug("Path found from {} to {} in {} steps, path length {}",
                    start, goal, stepsExplored, path.size());
                return path;
            }

            // Skip if already processed
            if (closedSet.contains(current.position())) {
                continue;
            }
            closedSet.add(current.position());

            stepsExplored++;

            // Safety limit to prevent infinite loops
            if (stepsExplored > GameConstants.MAX_PATH_LENGTH * 4) {
                LOG.warn("Path search exceeded step limit from {} to {}", start, goal);
                return Collections.emptyList();
            }

            // Explore neighbors (8 directions)
            int dirIndex = 0;
            for (int[] delta : DIRECTIONS) {
                int nx = current.position().x() + delta[0];
                int ny = current.position().y() + delta[1];

                // Bounds check
                if (!map.isInBounds(nx, ny)) {
                    dirIndex++;
                    continue;
                }

                GridPosition neighbor = new GridPosition(nx, ny);

                // Already processed
                if (closedSet.contains(neighbor)) {
                    dirIndex++;
                    continue;
                }

                // Terrain passability (category-aware)
                TerrainType terrain = map.getTile(nx, ny);
                boolean terrainPassable = category != null
                    ? terrain != null && terrain.isPassableBy(category)
                    : terrain != null && terrain.isPassable();
                if (!terrainPassable) {
                    dirIndex++;
                    continue;
                }

                // Occupied cell check (allow goal cell even if occupied)
                boolean isDiagonal = DIAGONAL_INDICES.contains(dirIndex);
                if (occupied.contains(neighbor) && !neighbor.equals(goal)) {
                    dirIndex++;
                    continue;
                }

                // Diagonal movement: check that both adjacent cardinal cells are passable
                // to prevent cutting through impassable diagonal gaps
                // REF: pathfinding.md — original game does not allow diagonal through impassable corners
                if (isDiagonal) {
                    int adjX1 = current.position().x() + delta[0];
                    int adjY1 = current.position().y();
                    int adjX2 = current.position().x();
                    int adjY2 = current.position().y() + delta[1];

                    boolean adj1Passable = category != null
                        ? map.isPassable(adjX1, adjY1, category)
                        : map.isPassable(adjX1, adjY1);
                    boolean adj2Passable = category != null
                        ? map.isPassable(adjX2, adjY2, category)
                        : map.isPassable(adjX2, adjY2);
                    if (!adj1Passable || !adj2Passable) {
                        dirIndex++;
                        continue;
                    }
                }

                // Calculate movement cost (category-aware)
                int terrainCost = getTerrainCost(terrain, category);
                // UNVERIFIED (L-5): 1.41 approximation — RE uses a terrain-cost lookup table with direction+terrain combined cost.
                // REF: pathfinding.md — diagonal cost uses lookup table in original game;
                // approximate with sqrt(2) ≈ 1.41 multiplier for 8-directional A*
                double moveCost = isDiagonal
                    ? terrainCost * 1.41
                    : terrainCost;
                double tentativeG = current.g() + moveCost;

                // Create neighbor node
                PathNode neighborNode = new PathNode(neighbor, tentativeG,
                    tentativeG + heuristic(neighbor, goal), current, isDiagonal);

                // Check if this position already has a better path in the open map (O(1) lookup)
                PathNode existing = openMap.get(neighbor);
                if (existing != null && existing.g() <= tentativeG) {
                    dirIndex++;
                    continue;
                }
                openSet.add(neighborNode);
                openMap.put(neighbor, neighborNode);
                dirIndex++;
            }
        }

        // No path found
        LOG.debug("No path found from {} to {}", start, goal);
        return Collections.emptyList();
    }

    /**
     * Calculates the terrain movement cost for a given terrain type.
     * REF: pathfinding.md — terrain costs affect pathfinding decisions
     * REF: GameConstants.TERRAIN_MOVEMENT_COSTS
     *
     * @param terrain the terrain type
     * @return movement cost (higher = slower), Integer.MAX_VALUE for impassable
     */
    public int getTerrainCost(TerrainType terrain) {
        return getTerrainCost(terrain, null);
    }

    /**
     * Calculates the terrain movement cost for a given terrain type and unit category.
     * Returns finite costs for terrain that is passable by the given category,
     * even if the base cost is Integer.MAX_VALUE (e.g., SHALLOW_WATER for infantry).
     * REF: pathfinding.md — terrain costs affect pathfinding decisions
     * REF: map_system.md — SHALLOW_WATER is passable by infantry with cost 3
     *
     * @param terrain  the terrain type
     * @param category the unit category, or null for default passability
     * @return movement cost (higher = slower), Integer.MAX_VALUE for impassable
     */
    public int getTerrainCost(TerrainType terrain, UnitCategory category) {
        if (terrain == null) {
            return Integer.MAX_VALUE;
        }
        // SHALLOW_WATER has base cost MAX_VALUE but infantry can cross it (cost 3)
        if (terrain == TerrainType.SHALLOW_WATER && category == UnitCategory.INFANTRY) {
            return 3;
        }
        int[] costs = GameConstants.TERRAIN_MOVEMENT_COSTS;
        int ordinal = terrain.ordinal();
        if (ordinal >= 0 && ordinal < costs.length) {
            return costs[ordinal];
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Octile distance heuristic for A*.
     * Admissible for 8-directional movement with diagonal cost sqrt(2).
     * h(n) = max(dx, dy) + (sqrt(2) - 1) * min(dx, dy)
     * REF: pathfinding.md — distance lookup table uses Manhattan-like distance class
     *
     * @param a first position
     * @param b second position
     * @return estimated cost from a to b
     */
    private double heuristic(GridPosition a, GridPosition b) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        int maxD = Math.max(dx, dy);
        int minD = Math.min(dx, dy);
        // REF: pathfinding.md — octile distance heuristic (sqrt(2) - 1) * min(dx,dy) + max(dx,dy)
        return maxD + 0.41 * minD;
    }

    /**
     * Reconstructs the path from the goal node back to the start.
     *
     * @param goalNode the reached goal node
     * @return list of positions from start (exclusive) to goal (inclusive)
     */
    private List<GridPosition> reconstructPath(PathNode goalNode) {
        List<GridPosition> path = new ArrayList<>();
        PathNode current = goalNode;

        // Walk back from goal to start, collecting positions
        while (current.parent() != null) {
            path.add(current.position());
            current = current.parent();
        }

        // Reverse to get start -> goal order
        Collections.reverse(path);

        // Trim to max path length
        if (path.size() > GameConstants.MAX_PATH_LENGTH) {
            path = new ArrayList<>(path.subList(0, GameConstants.MAX_PATH_LENGTH));
        }

        return path;
    }

    /**
     * Node in the A* open set.
     * Records position, g-cost (from start), f-cost (g + heuristic),
     * parent for path reconstruction, and whether this step is diagonal.
     *
     * @param position grid position
     * @param g        cost from start to this node
     * @param f        total estimated cost (g + heuristic)
     * @param parent   parent node (null for start)
     * @param diagonal whether this step is a diagonal move
     */
    record PathNode(
        GridPosition position,
        double g,
        double f,
        PathNode parent,
        boolean diagonal
    ) implements Comparable<PathNode> {
        @Override
        public int compareTo(PathNode other) {
            int cmp = Double.compare(this.f, other.f);
            if (cmp != 0) return cmp;
            // Tie-break: prefer higher g (closer to goal)
            return Double.compare(other.g, this.g);
        }
    }
}
