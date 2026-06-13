package com.aow2.client.editor;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.UnitStats;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles building/unit placement with collision checking and validation.
 * Ensures entities are placed on passable terrain and don't overlap.
 * <p>
 * REF: phases.md Phase 9 - building/unit placement with collision checking
 */
public final class EntityPlacer {

    /** The entity manager to place entities into. */
    private EntityManager entityManager;

    /** The map for terrain validation. */
    private GameMap map;

    /** Default unit stats for placement. ASSUMPTION: placeholder stats for editor. */
    private final Map<UnitType, UnitStats> defaultUnitStats;

    /** Default building stats for placement. ASSUMPTION: placeholder stats for editor. */
    private final Map<BuildingType, BuildingStats> defaultBuildingStats;

    /**
     * Constructs an EntityPlacer.
     */
    public EntityPlacer() {
        this.defaultUnitStats = new HashMap<>();
        this.defaultBuildingStats = new HashMap<>();
        initDefaultStats();
    }

    /**
     * Initializes default placeholder stats for all unit and building types.
     * These are used when placing entities in the editor.
     */
    private void initDefaultStats() {
        for (UnitType type : UnitType.values()) {
            defaultUnitStats.put(type, new UnitStats(
                type, "Editor placeholder", 100, 10,
                100, 4, 2, 0, 5, 4, 60, 100, 5, 2, 0, 0, 0
            ));
        }

        for (BuildingType type : BuildingType.values()) {
            defaultBuildingStats.put(type, new BuildingStats(
                type, 500, 0, 0, 5, 0, 10, 600, 6, 5, 0, 10, 1, 0, 0, 0, java.util.List.of()
            ));
        }
    }

    /**
     * Sets the entity manager.
     *
     * @param entityManager the entity manager
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Sets the game map for terrain validation.
     *
     * @param map the game map
     */
    public void setMap(GameMap map) {
        this.map = map;
    }

    /**
     * Attempts to place a building at the given position.
     * Validates terrain passability and checks for overlapping entities.
     *
     * @param type     the building type to place
     * @param pos      the grid position
     * @param playerId the owning player ID (0 or 1)
     * @return the placed building, or null if placement failed
     */
    public Building placeBuilding(BuildingType type, GridPosition pos, int playerId) {
        if (entityManager == null || map == null) {
            return null;
        }

        // Validate position is within map bounds
        if (!map.isInBounds(pos.x(), pos.y())) {
            return null;
        }

        // Validate terrain is passable
        if (!map.isPassable(pos.x(), pos.y())) {
            return null;
        }

        // Check for collision with existing buildings
        if (entityManager.findBuildingAt(pos) != null) {
            return null;
        }

        // Determine faction from player ID
        Faction faction = playerId == 0 ? Faction.CONFEDERATION : Faction.RESISTANCE;

        // Verify building type matches faction
        if (type.faction() != faction) {
            return null;
        }

        // Create and place the building
        BuildingStats stats = defaultBuildingStats.get(type);
        int entityId = entityManager.allocateEntityId();
        Building building = new Building(entityId, faction, pos, type, stats);
        building.setConstructionProgress(stats.buildTime()); // Fully built in editor
        entityManager.addBuilding(building);

        return building;
    }

    /**
     * Attempts to place a unit at the given position.
     * Validates terrain passability and checks for overlapping units.
     *
     * @param type     the unit type to place
     * @param pos      the grid position
     * @param playerId the owning player ID (0 or 1)
     * @return the placed unit, or null if placement failed
     */
    public Unit placeUnit(UnitType type, GridPosition pos, int playerId) {
        if (entityManager == null || map == null) {
            return null;
        }

        // Validate position is within map bounds
        if (!map.isInBounds(pos.x(), pos.y())) {
            return null;
        }

        // Validate terrain is passable
        if (!map.isPassable(pos.x(), pos.y())) {
            return null;
        }

        // Check for collision with existing units
        if (entityManager.findUnitAt(pos) != null) {
            return null;
        }

        // Determine faction from player ID
        Faction faction = playerId == 0 ? Faction.CONFEDERATION : Faction.RESISTANCE;

        // Verify unit type matches faction
        if (type.faction() != faction) {
            return null;
        }

        // Create and place the unit
        UnitStats stats = defaultUnitStats.get(type);
        int entityId = entityManager.allocateEntityId();
        Unit unit = new Unit(entityId, faction, pos, type, stats);
        entityManager.addUnit(unit);

        return unit;
    }

    /**
     * Removes an entity at the given position.
     * Checks both units and buildings.
     *
     * @param pos the position to erase
     * @return true if an entity was removed
     */
    public boolean eraseEntity(GridPosition pos) {
        if (entityManager == null) {
            return false;
        }

        // ASSUMPTION: remove by setting HP to -1; cleanup handled by EntityManager.removeDeadEntities()
        Unit unit = entityManager.findUnitAt(pos);
        if (unit != null) {
            unit.takeDamage(unit.getHp() + 1);
            return true;
        }

        Building building = entityManager.findBuildingAt(pos);
        if (building != null) {
            building.takeDamage(building.getHp() + 1);
            return true;
        }

        return false;
    }

    /**
     * Checks whether a building can be placed at the given position.
     *
     * @param type building type
     * @param pos  grid position
     * @return true if placement is valid
     */
    public boolean canPlaceBuilding(BuildingType type, GridPosition pos) {
        if (map == null || !map.isInBounds(pos.x(), pos.y())) {
            return false;
        }
        if (!map.isPassable(pos.x(), pos.y())) {
            return false;
        }
        if (entityManager != null && entityManager.findBuildingAt(pos) != null) {
            return false;
        }
        return true;
    }

    /**
     * Checks whether a unit can be placed at the given position.
     *
     * @param type unit type
     * @param pos  grid position
     * @return true if placement is valid
     */
    public boolean canPlaceUnit(UnitType type, GridPosition pos) {
        if (map == null || !map.isInBounds(pos.x(), pos.y())) {
            return false;
        }
        if (!map.isPassable(pos.x(), pos.y())) {
            return false;
        }
        if (entityManager != null && entityManager.findUnitAt(pos) != null) {
            return false;
        }
        return true;
    }
}
