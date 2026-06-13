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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrated map editor accessible from main menu.
 * Allows creating and editing game maps with terrain painting,
 * building/unit placement, and starting position configuration.
 * <p>
 * REF: phases.md Phase 9 - Map Builder
 */
public final class MapEditor {

    private static final Logger LOG = LoggerFactory.getLogger(MapEditor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The current map being edited. */
    private GameMap currentMap;

    /** The entity manager for placed entities. */
    private EntityManager entityManager;

    /** Currently selected terrain type for painting. */
    private TerrainType selectedTerrain;

    /** Currently selected building type for placement. */
    private BuildingType selectedBuilding;

    /** Currently selected unit type for placement. */
    private UnitType selectedUnit;

    /** Current player ID for entity placement (0 = Confederation, 1 = Resistance). */
    private int currentPlayerId;

    /** Current editor tool. */
    private EditorTool currentTool;

    /** Tile painter for brush-based terrain editing. */
    private final TilePainter tilePainter;

    /** Entity placer for building/unit placement. */
    private final EntityPlacer entityPlacer;

    /** Starting positions for each player. Key = playerId, Value = grid position. */
    private final Map<Integer, GridPosition> startingPositions;

    /** Map name for save/load. */
    private String mapName;

    /**
     * Editor tools available in the map editor.
     */
    public enum EditorTool {
        TERRAIN_PAINT,
        BUILDING_PLACE,
        UNIT_PLACE,
        ERASE,
        STARTING_POSITION
    }

    /**
     * Constructs a new MapEditor with default settings.
     */
    public MapEditor() {
        this.tilePainter = new TilePainter();
        this.entityPlacer = new EntityPlacer();
        this.currentTool = EditorTool.TERRAIN_PAINT;
        this.selectedTerrain = TerrainType.GRASS;
        this.currentPlayerId = 0;
        this.startingPositions = new HashMap<>();
        this.mapName = "Untitled";
    }

    /**
     * Creates a new map with the given dimensions.
     * Fills the map with GRASS terrain by default.
     *
     * @param width  map width (1-127)
     * @param height map height (1-127)
     */
    public void createNewMap(int width, int height) {
        this.currentMap = new GameMap(width, height);
        this.entityManager = new EntityManager();
        this.startingPositions.clear();
        this.mapName = "Untitled";

        tilePainter.setMap(currentMap);
        entityPlacer.setMap(currentMap);
        entityPlacer.setEntityManager(entityManager);

        LOG.info("Created new map: {}x{}", width, height);
    }

    /**
     * Paints terrain at the given position using the selected brush and terrain.
     *
     * @param pos     center position for painting
     * @param terrain terrain type to paint
     */
    public void paintTerrain(GridPosition pos, TerrainType terrain) {
        if (currentMap == null) {
            return;
        }
        tilePainter.setSelectedTerrain(terrain);
        tilePainter.paint(pos);
    }

    /**
     * Places a building at the given position for the specified player.
     *
     * @param type     building type
     * @param pos      grid position
     * @param playerId owning player ID
     * @return true if placement succeeded
     */
    public boolean placeBuilding(BuildingType type, GridPosition pos, int playerId) {
        Building building = entityPlacer.placeBuilding(type, pos, playerId);
        if (building != null) {
            LOG.debug("Placed building {} at ({},{}) for player {}",
                type, pos.x(), pos.y(), playerId);
            return true;
        }
        return false;
    }

    /**
     * Places a unit at the given position for the specified player.
     *
     * @param type     unit type
     * @param pos      grid position
     * @param playerId owning player ID
     * @return true if placement succeeded
     */
    public boolean placeUnit(UnitType type, GridPosition pos, int playerId) {
        Unit unit = entityPlacer.placeUnit(type, pos, playerId);
        if (unit != null) {
            LOG.debug("Placed unit {} at ({},{}) for player {}",
                type, pos.x(), pos.y(), playerId);
            return true;
        }
        return false;
    }

    /**
     * Sets the starting position for a player.
     *
     * @param playerId the player ID (0 or 1)
     * @param pos      the starting position
     */
    public void setStartingPosition(int playerId, GridPosition pos) {
        if (playerId < 0 || playerId > 1) {
            throw new IllegalArgumentException("Player ID must be 0 or 1, got: " + playerId);
        }
        startingPositions.put(playerId, pos);
        LOG.debug("Set starting position for player {} at ({},{})", playerId, pos.x(), pos.y());
    }

    /**
     * Validates the map for playability.
     * Checks for:
     * - Both players have starting positions
     * - Starting positions are on passable terrain
     * - At least one HQ/CC building per player
     * - Map is not too small
     * - No unreachable areas (basic flood fill check)
     *
     * @return validation result with errors and warnings
     */
    public MapValidationResult validateMap() {
        MapValidationResult result = new MapValidationResult();

        if (currentMap == null) {
            result.addError("No map loaded");
            return result;
        }

        // Check minimum map size
        if (currentMap.getWidth() < 8 || currentMap.getHeight() < 8) {
            result.addError("Map is too small (minimum 8x8), current: " +
                currentMap.getWidth() + "x" + currentMap.getHeight());
        }

        // Check player 0 starting position
        if (!startingPositions.containsKey(0)) {
            result.addError("Player 0 (Confederation) has no starting position");
        } else {
            GridPosition p0 = startingPositions.get(0);
            if (!currentMap.isPassable(p0.x(), p0.y())) {
                result.addError("Player 0 starting position is on impassable terrain");
            }
        }

        // Check player 1 starting position
        if (!startingPositions.containsKey(1)) {
            result.addError("Player 1 (Resistance) has no starting position");
        } else {
            GridPosition p1 = startingPositions.get(1);
            if (!currentMap.isPassable(p1.x(), p1.y())) {
                result.addError("Player 1 starting position is on impassable terrain");
            }
        }

        // Check starting positions are not too close
        if (startingPositions.containsKey(0) && startingPositions.containsKey(1)) {
            double dist = startingPositions.get(0).distanceTo(startingPositions.get(1));
            if (dist < 10) {
                result.addWarning("Starting positions are very close (" +
                    String.format("%.1f", dist) + " cells apart)");
            }
        }

        // Check for HQ/CC buildings per player
        boolean player0HasHQ = false;
        boolean player1HasHQ = false;
        for (Building building : entityManager.getAllBuildings()) {
            if (building.getBuildingType().isHQ()) {
                if (building.getFaction() == Faction.CONFEDERATION) {
                    player0HasHQ = true;
                } else if (building.getFaction() == Faction.RESISTANCE) {
                    player1HasHQ = true;
                }
            }
        }
        if (!player0HasHQ) {
            result.addWarning("Player 0 has no Command Centre");
        }
        if (!player1HasHQ) {
            result.addWarning("Player 1 has no Headquarters");
        }

        // Basic flood fill to check for unreachable passable areas
        checkUnreachableAreas(result);

        // Check for excessive water/mountain coverage
        checkTerrainBalance(result);

        LOG.info("Map validation: {} errors, {} warnings",
            result.getErrors().size(), result.getWarnings().size());
        return result;
    }

    /**
     * Performs a basic flood fill to detect unreachable passable areas.
     * ASSUMPTION: Two passable areas are unreachable if no path of passable
     * tiles connects them. Uses simple BFS from the first passable tile.
     */
    private void checkUnreachableAreas(MapValidationResult result) {
        if (currentMap == null) return;

        // Find first passable tile
        GridPosition start = null;
        for (int x = 0; x < currentMap.getWidth() && start == null; x++) {
            for (int y = 0; y < currentMap.getHeight() && start == null; y++) {
                if (currentMap.isPassable(x, y)) {
                    start = new GridPosition(x, y);
                }
            }
        }

        if (start == null) {
            result.addError("Map has no passable terrain");
            return;
        }

        // BFS flood fill
        boolean[][] visited = new boolean[currentMap.getWidth()][currentMap.getHeight()];
        java.util.Queue<GridPosition> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited[start.x()][start.y()] = true;
        int reachableCount = 1;

        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        while (!queue.isEmpty()) {
            GridPosition current = queue.poll();
            for (int[] dir : directions) {
                int nx = current.x() + dir[0];
                int ny = current.y() + dir[1];
                if (currentMap.isInBounds(nx, ny) && !visited[nx][ny] && currentMap.isPassable(nx, ny)) {
                    visited[nx][ny] = true;
                    queue.add(new GridPosition(nx, ny));
                    reachableCount++;
                }
            }
        }

        // Count total passable tiles
        int totalPassable = 0;
        for (int x = 0; x < currentMap.getWidth(); x++) {
            for (int y = 0; y < currentMap.getHeight(); y++) {
                if (currentMap.isPassable(x, y)) {
                    totalPassable++;
                }
            }
        }

        if (reachableCount < totalPassable) {
            int unreachable = totalPassable - reachableCount;
            result.addWarning("Map has " + unreachable + " unreachable passable tile(s)");
        }
    }

    /**
     * Checks terrain balance — warns if too much water/mountain.
     */
    private void checkTerrainBalance(MapValidationResult result) {
        if (currentMap == null) return;

        int total = currentMap.getWidth() * currentMap.getHeight();
        int waterCount = 0;
        int mountainCount = 0;

        for (int x = 0; x < currentMap.getWidth(); x++) {
            for (int y = 0; y < currentMap.getHeight(); y++) {
                TerrainType t = currentMap.getTile(x, y);
                if (t == TerrainType.DEEP_WATER) waterCount++;
                if (t == TerrainType.MOUNTAIN) mountainCount++;
            }
        }

        double waterRatio = (double) waterCount / total;
        double mountainRatio = (double) mountainCount / total;

        if (waterRatio > 0.5) {
            result.addWarning(String.format("Map is %.0f%% water, may limit gameplay", waterRatio * 100));
        }
        if (mountainRatio > 0.5) {
            result.addWarning(String.format("Map is %.0f%% mountain, may limit gameplay", mountainRatio * 100));
        }
    }

    /**
     * Saves the map to a JSON file.
     * The JSON format includes map dimensions, terrain tiles, placed entities,
     * and starting positions.
     *
     * @param filePath path to save the map file
     * @return true if save succeeded
     */
    public boolean saveMap(Path filePath) {
        if (currentMap == null) {
            LOG.warn("No map to save");
            return false;
        }

        try {
            MapDataExport data = new MapDataExport();
            data.name = mapName;
            data.width = currentMap.getWidth();
            data.height = currentMap.getHeight();

            // Export non-GRASS tiles
            data.tiles = new ArrayList<>();
            for (int x = 0; x < currentMap.getWidth(); x++) {
                for (int y = 0; y < currentMap.getHeight(); y++) {
                    TerrainType terrain = currentMap.getTile(x, y);
                    if (terrain != TerrainType.GRASS) {
                        TileExport tile = new TileExport();
                        tile.x = x;
                        tile.y = y;
                        tile.terrain = terrain.name();
                        data.tiles.add(tile);
                    }
                }
            }

            // Export buildings
            data.buildings = new ArrayList<>();
            for (Building b : entityManager.getAllBuildings()) {
                EntityExport entity = new EntityExport();
                entity.type = b.getBuildingType().name();
                entity.x = b.getPosition().x();
                entity.y = b.getPosition().y();
                entity.playerId = b.getFaction() == Faction.CONFEDERATION ? 0 : 1;
                data.buildings.add(entity);
            }

            // Export units
            data.units = new ArrayList<>();
            for (Unit u : entityManager.getAllUnits()) {
                EntityExport entity = new EntityExport();
                entity.type = u.getUnitType().name();
                entity.x = u.getPosition().x();
                entity.y = u.getPosition().y();
                entity.playerId = u.getFaction() == Faction.CONFEDERATION ? 0 : 1;
                data.units.add(entity);
            }

            // Export starting positions
            data.startingPositions = new ArrayList<>();
            for (var entry : startingPositions.entrySet()) {
                StartingPositionExport sp = new StartingPositionExport();
                sp.playerId = entry.getKey();
                sp.x = entry.getValue().x();
                sp.y = entry.getValue().y();
                data.startingPositions.add(sp);
            }

            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);

            LOG.info("Map saved to {} ({}x{}, {} buildings, {} units)",
                filePath, data.width, data.height,
                data.buildings.size(), data.units.size());
            return true;

        } catch (IOException e) {
            LOG.error("Failed to save map: {}", filePath, e);
            return false;
        }
    }

    /**
     * Loads a map from a JSON file.
     *
     * @param filePath path to the map file
     * @return true if load succeeded
     */
    public boolean loadMap(Path filePath) {
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            MapDataExport data = MAPPER.readValue(json, MapDataExport.class);

            createNewMap(data.width, data.height);
            this.mapName = data.name != null ? data.name : "Untitled";

            // Load tiles
            if (data.tiles != null) {
                for (TileExport tile : data.tiles) {
                    try {
                        TerrainType terrain = TerrainType.valueOf(tile.terrain);
                        currentMap.setTile(tile.x, tile.y, terrain);
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Unknown terrain type '{}' at ({},{})", tile.terrain, tile.x, tile.y);
                    }
                }
            }

            // Load buildings
            if (data.buildings != null) {
                for (EntityExport entity : data.buildings) {
                    try {
                        BuildingType type = BuildingType.valueOf(entity.type);
                        placeBuilding(type, new GridPosition(entity.x, entity.y), entity.playerId);
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Unknown building type '{}'", entity.type);
                    }
                }
            }

            // Load units
            if (data.units != null) {
                for (EntityExport entity : data.units) {
                    try {
                        UnitType type = UnitType.valueOf(entity.type);
                        placeUnit(type, new GridPosition(entity.x, entity.y), entity.playerId);
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Unknown unit type '{}'", entity.type);
                    }
                }
            }

            // Load starting positions
            if (data.startingPositions != null) {
                for (StartingPositionExport sp : data.startingPositions) {
                    setStartingPosition(sp.playerId, new GridPosition(sp.x, sp.y));
                }
            }

            LOG.info("Map loaded from {} ({}x{})", filePath, data.width, data.height);
            return true;

        } catch (IOException e) {
            LOG.error("Failed to load map: {}", filePath, e);
            return false;
        }
    }

    // --- Getters and Setters ---

    public GameMap getCurrentMap() {
        return currentMap;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public TerrainType getSelectedTerrain() {
        return selectedTerrain;
    }

    public void setSelectedTerrain(TerrainType selectedTerrain) {
        this.selectedTerrain = selectedTerrain;
    }

    public BuildingType getSelectedBuilding() {
        return selectedBuilding;
    }

    public void setSelectedBuilding(BuildingType selectedBuilding) {
        this.selectedBuilding = selectedBuilding;
    }

    public UnitType getSelectedUnit() {
        return selectedUnit;
    }

    public void setSelectedUnit(UnitType selectedUnit) {
        this.selectedUnit = selectedUnit;
    }

    public int getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(int currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public EditorTool getCurrentTool() {
        return currentTool;
    }

    public void setCurrentTool(EditorTool currentTool) {
        this.currentTool = currentTool;
    }

    public TilePainter getTilePainter() {
        return tilePainter;
    }

    public EntityPlacer getEntityPlacer() {
        return entityPlacer;
    }

    public Map<Integer, GridPosition> getStartingPositions() {
        return Map.copyOf(startingPositions);
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    // --- JSON Export DTOs ---

    /** DTO for map data export/import. */
    static final class MapDataExport {
        @JsonProperty("name")
        public String name;
        @JsonProperty("width")
        public int width;
        @JsonProperty("height")
        public int height;
        @JsonProperty("tiles")
        public List<TileExport> tiles;
        @JsonProperty("buildings")
        public List<EntityExport> buildings;
        @JsonProperty("units")
        public List<EntityExport> units;
        @JsonProperty("starting_positions")
        public List<StartingPositionExport> startingPositions;
    }

    /** DTO for tile export. */
    static final class TileExport {
        @JsonProperty("x")
        public int x;
        @JsonProperty("y")
        public int y;
        @JsonProperty("terrain")
        public String terrain;
    }

    /** DTO for entity export. */
    static final class EntityExport {
        @JsonProperty("type")
        public String type;
        @JsonProperty("x")
        public int x;
        @JsonProperty("y")
        public int y;
        @JsonProperty("player_id")
        public int playerId;
    }

    /** DTO for starting position export. */
    static final class StartingPositionExport {
        @JsonProperty("player_id")
        public int playerId;
        @JsonProperty("x")
        public int x;
        @JsonProperty("y")
        public int y;
    }
}
