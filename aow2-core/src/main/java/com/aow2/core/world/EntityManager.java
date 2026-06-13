package com.aow2.core.world;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Mine;
import com.aow2.core.entity.Projectile;
import com.aow2.core.entity.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all entities (units, buildings, projectiles) in the game world.
 * Provides lookup, insertion, removal, and query operations.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.1 — Entity management
 * REF: unit_stats.md — entity slot ranges per player
 */
public class EntityManager {

    /** All units keyed by entity ID. */
    private final Map<Integer, Unit> units;

    /** All buildings keyed by entity ID. */
    private final Map<Integer, Building> buildings;

    /** All active projectiles keyed by entity ID. */
    private final Map<Integer, Projectile> projectiles;

    /** All mines in the game world. */
    private final List<Mine> mines = new ArrayList<>();

    /** Counter for allocating unique entity IDs. */
    private final AtomicInteger nextEntityId;

    /**
     * Constructs a new EntityManager.
     */
    public EntityManager() {
        this.units = new ConcurrentHashMap<>();
        this.buildings = new ConcurrentHashMap<>();
        this.projectiles = new ConcurrentHashMap<>();
        this.nextEntityId = new AtomicInteger(1);
    }

    /**
     * Allocates a new unique entity ID.
     *
     * @return the next available entity ID
     */
    public int allocateEntityId() {
        return nextEntityId.getAndIncrement();
    }

    // --- Unit operations ---

    /**
     * Adds a unit to the manager.
     *
     * @param unit the unit to add
     */
    public void addUnit(Unit unit) {
        units.put(unit.getId(), unit);
    }

    /**
     * Gets a unit by its entity ID.
     *
     * @param id entity ID
     * @return the unit, or null if not found
     */
    public Unit getUnit(int id) {
        return units.get(id);
    }

    /**
     * Returns all units belonging to the given faction.
     * Dead units (hp = -1) are included; use {@link #getAliveUnitsForPlayer(Faction)} to exclude them.
     *
     * @param faction the faction to filter by
     * @return unmodifiable list of units for the faction
     */
    public List<Unit> getUnitsForPlayer(Faction faction) {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : units.values()) {
            if (unit.getFaction() == faction) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all alive units belonging to the given faction.
     *
     * @param faction the faction to filter by
     * @return unmodifiable list of alive units for the faction
     */
    public List<Unit> getAliveUnitsForPlayer(Faction faction) {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : units.values()) {
            if (unit.getFaction() == faction && unit.isAlive()) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all units in the manager.
     *
     * @return unmodifiable list of all units
     */
    public List<Unit> getAllUnits() {
        return Collections.unmodifiableList(new ArrayList<>(units.values()));
    }

    // --- Building operations ---

    /**
     * Adds a building to the manager.
     *
     * @param building the building to add
     */
    public void addBuilding(Building building) {
        buildings.put(building.getId(), building);
    }

    /**
     * Gets a building by its entity ID.
     *
     * @param id entity ID
     * @return the building, or null if not found
     */
    public Building getBuilding(int id) {
        return buildings.get(id);
    }

    /**
     * Returns all buildings belonging to the given faction.
     *
     * @param faction the faction to filter by
     * @return unmodifiable list of buildings for the faction
     */
    public List<Building> getBuildingsForPlayer(Faction faction) {
        List<Building> result = new ArrayList<>();
        for (Building building : buildings.values()) {
            if (building.getFaction() == faction) {
                result.add(building);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all buildings in the manager.
     *
     * @return unmodifiable list of all buildings
     */
    public List<Building> getAllBuildings() {
        return Collections.unmodifiableList(new ArrayList<>(buildings.values()));
    }

    // --- Mine operations ---

    /**
     * Adds a mine to the manager.
     *
     * @param mine the mine to add
     */
    public void addMine(Mine mine) {
        mines.add(mine);
    }

    /**
     * Returns all mines in the manager.
     *
     * @return unmodifiable list of all mines
     */
    public List<Mine> getMines() {
        return Collections.unmodifiableList(mines);
    }

    /**
     * Gets a mine by its entity ID.
     *
     * @param id entity ID
     * @return an Optional containing the mine, or empty if not found
     */
    public Optional<Mine> getMine(int id) {
        return mines.stream().filter(m -> m.getId() == id).findFirst();
    }

    // --- Projectile operations ---

    /**
     * Adds a projectile to the manager.
     *
     * @param projectile the projectile to add
     */
    public void addProjectile(Projectile projectile) {
        projectiles.put(projectile.getId(), projectile);
    }

    /**
     * Gets a projectile by its entity ID.
     *
     * @param id entity ID
     * @return the projectile, or null if not found
     */
    public Projectile getProjectile(int id) {
        return projectiles.get(id);
    }

    /**
     * Returns all projectiles in the manager.
     *
     * @return unmodifiable list of all projectiles
     */
    public List<Projectile> getAllProjectiles() {
        return Collections.unmodifiableList(new ArrayList<>(projectiles.values()));
    }

    /**
     * Removes a projectile by its entity ID.
     * Used by the projectile system to remove projectiles after impact.
     *
     * @param id entity ID of the projectile to remove
     * @return true if the projectile was removed, false if not found
     */
    public boolean removeProjectile(int id) {
        return projectiles.remove(id) != null;
    }

    // --- Cleanup ---

    /**
     * Removes all dead units, destroyed buildings, and spent projectiles.
     * Should be called at the end of each game tick.
     */
    public void removeDeadEntities() {
        units.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        buildings.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        projectiles.entrySet().removeIf(entry -> entry.getValue().hasReachedTarget());
        mines.removeIf(mine -> mine.getHp() <= 0);
    }

    // --- Spatial queries ---

    /**
     * Finds a unit at the given grid position.
     * Returns the first unit found at that position, or null.
     *
     * @param position grid position to search
     * @return unit at position, or null
     */
    public Unit findUnitAt(GridPosition position) {
        for (Unit unit : units.values()) {
            if (unit.isAlive() && unit.getPosition().equals(position)) {
                return unit;
            }
        }
        return null;
    }

    /**
     * Finds a building at the given grid position.
     * Returns the first building found at that position, or null.
     *
     * @param position grid position to search
     * @return building at position, or null
     */
    public Building findBuildingAt(GridPosition position) {
        for (Building building : buildings.values()) {
            if (building.isAlive() && building.getPosition().equals(position)) {
                return building;
            }
        }
        return null;
    }

    // --- Counts ---

    /**
     * Returns the total number of units (including dead ones not yet cleaned).
     *
     * @return unit count
     */
    public int unitCount() {
        return units.size();
    }

    /**
     * Returns the total number of buildings (including destroyed ones not yet cleaned).
     *
     * @return building count
     */
    public int buildingCount() {
        return buildings.size();
    }

    /**
     * Returns the total number of projectiles.
     *
     * @return projectile count
     */
    public int projectileCount() {
        return projectiles.size();
    }
}
