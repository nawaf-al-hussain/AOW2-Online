package com.aow2.core.world;

import com.aow2.common.config.StatsRegistry;
import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.core.campaign.SaveData;
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
     * Creates a new EntityManager populated from save data.
     * Uses StatsRegistry to get stats for each unit/building type,
     * then restores each entity from its saved state.
     *
     * @param unitSaves     list of unit save records
     * @param buildingSaves list of building save records
     * @return a new EntityManager populated with restored entities
     */
    public static EntityManager restoreFromSave(
            List<SaveData.UnitSave> unitSaves,
            List<SaveData.BuildingSave> buildingSaves) {
        EntityManager em = new EntityManager();
        StatsRegistry registry = StatsRegistry.getInstance();

        // Restore units
        int maxId = 0;
        for (SaveData.UnitSave save : unitSaves) {
            UnitStats stats = registry.getUnitStats(save.unitType());
            Unit unit = new Unit(save.entityId(), save.faction(), save.position(),
                save.unitType(), stats);
            // Restore saved HP (may differ from max if damaged)
            unit.takeDamage(stats.hp() - save.hp());
            unit.setRank(save.rank());
            unit.setExperience(save.experience());
            unit.setSiegeMode(save.siegeMode());
            unit.setAttackCooldown(save.attackCooldown());
            unit.setWeaponCooldown(save.weaponCooldown());
            em.addUnit(unit);
            if (save.entityId() >= maxId) {
                maxId = save.entityId() + 1;
            }
        }

        // Restore buildings
        for (SaveData.BuildingSave save : buildingSaves) {
            BuildingStats stats = registry.getBuildingStats(save.buildingType());
            Building building = new Building(save.entityId(), save.faction(),
                save.position(), save.buildingType(), stats);
            // Restore saved HP
            building.takeDamage(stats.hp() - save.hp());
            building.setConstructionProgress(save.constructionProgress());
            building.setPowered(save.powered());
            building.setResearchId(save.researchId());
            building.setProductionProgress(save.productionProgress());
            // Restore production queue
            for (UnitType unitType : save.productionQueue()) {
                building.enqueueProduction(unitType);
            }
            em.addBuilding(building);
            if (save.entityId() >= maxId) {
                maxId = save.entityId() + 1;
            }
        }

        // Ensure next entity ID is above the highest restored ID
        em.nextEntityId.set(Math.max(maxId, 1));

        return em;
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
     * Returns all alive, non-garrisoned units belonging to the given faction.
     * Garrisoned units are excluded because they are hidden inside buildings
     * and should not appear on the map or be counted for army strength.
     *
     * @param faction the faction to filter by
     * @return unmodifiable list of alive, non-garrisoned units for the faction
     */
    public List<Unit> getAliveUnitsForPlayer(Faction faction) {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : units.values()) {
            if (unit.getFaction() == faction && unit.isAlive() && !unit.isGarrisoned()) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all garrisoned (alive, inside a building) units belonging to the given faction.
     * These units are inside buildings and not visible on the map.
     *
     * @param faction the faction to filter by
     * @return unmodifiable list of garrisoned units for the faction
     */
    public List<Unit> getGarrisonedUnitsForPlayer(Faction faction) {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : units.values()) {
            if (unit.getFaction() == faction && unit.isAlive() && unit.isGarrisoned()) {
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
     * Returns all mines in the manager.
     * Alias for {@link #getMines()} for naming consistency with {@link #getAllUnits()}.
     *
     * @return unmodifiable list of all mines
     */
    public List<Mine> getAllMines() {
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
        mines.removeIf(mine -> !mine.isAlive());
    }

    // --- Visibility-filtered queries ---

    /**
     * Returns only alive, non-garrisoned units whose positions are VISIBLE to the given player.
     * Used by the AI to make decisions based only on what it can see.
     * <p>
     * If fogOfWar is null, falls back to the unfiltered method (full information,
     * useful for testing or when fog of war is disabled).
     *
     * @param playerId the player whose visibility to check
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return unmodifiable list of visible, alive, non-garrisoned units for the player's faction
     */
    public List<Unit> getVisibleUnitsForPlayer(int playerId, FogOfWarSystem fogOfWar) {
        if (fogOfWar == null) {
            Faction faction = com.aow2.core.economy.EconomySystem.playerFaction(playerId);
            return getAliveUnitsForPlayer(faction);
        }
        Faction faction = com.aow2.core.economy.EconomySystem.playerFaction(playerId);
        List<Unit> allUnits = getAliveUnitsForPlayer(faction);
        List<Unit> result = new ArrayList<>();
        for (Unit unit : allUnits) {
            if (fogOfWar.isVisible(playerId, unit.getPosition())) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns only alive, non-garrisoned ENEMY units whose positions are VISIBLE to the given player.
     * Used by the AI to see only enemy units it can actually observe.
     *
     * @param playerId the player whose visibility to check
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return unmodifiable list of visible enemy alive units
     */
    public List<Unit> getVisibleEnemyUnitsForPlayer(int playerId, FogOfWarSystem fogOfWar) {
        Faction aiFaction = com.aow2.core.economy.EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;
        if (fogOfWar == null) {
            return getAliveUnitsForPlayer(enemyFaction);
        }
        List<Unit> enemyUnits = getAliveUnitsForPlayer(enemyFaction);
        List<Unit> result = new ArrayList<>();
        for (Unit unit : enemyUnits) {
            if (fogOfWar.isVisible(playerId, unit.getPosition())) {
                result.add(unit);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns only ENEMY buildings whose positions are VISIBLE to the given player.
     *
     * @param playerId the player whose visibility to check
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return unmodifiable list of visible enemy buildings
     */
    public List<Building> getVisibleEnemyBuildingsForPlayer(int playerId, FogOfWarSystem fogOfWar) {
        Faction aiFaction = com.aow2.core.economy.EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;
        if (fogOfWar == null) {
            return getBuildingsForPlayer(enemyFaction);
        }
        List<Building> enemyBuildings = getBuildingsForPlayer(enemyFaction);
        List<Building> result = new ArrayList<>();
        for (Building building : enemyBuildings) {
            if (fogOfWar.isVisible(playerId, building.getPosition())) {
                result.add(building);
            }
        }
        return Collections.unmodifiableList(result);
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
            if (unit.isAlive() && !unit.isGarrisoned() && unit.getPosition().equals(position)) {
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
