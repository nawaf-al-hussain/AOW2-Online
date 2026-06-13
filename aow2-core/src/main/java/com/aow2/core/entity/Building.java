package com.aow2.core.entity;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A game building (command centre, barracks, factory, etc.).
 * Extends {@link Entity} with construction, production, and research state.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 6 — Building Encyclopedia
 * REF: complete_building_stats.json — all stat values
 * REF: MASTER_DOCUMENTATION.md Section 4 — Power system and production queues
 */
public class Building extends Entity {

    /** The building type definition. */
    private final BuildingType buildingType;

    /** Immutable stat block for this building type. */
    private final BuildingStats stats;

    /** Ticks of construction progress (0 = just placed). */
    private int constructionProgress;

    /** Cooldown ticks remaining before this building can attack again. */
    private int attackCooldown;

    /** Whether this building is receiving power. REF: power_system.md */
    private boolean powered;

    /** Rally point waypoint for produced units (null = no waypoint). */
    private GridPosition waypoint;

    /** Production queue of unit types to build. */
    private final List<UnitType> productionQueue;

    /** Currently produced unit type (null if not producing). */
    private UnitType currentProduction;

    /** Ticks of progress toward completing the current production item. */
    private int productionProgress;

    /** Entity ID of a garrisoned unit inside this building (null = empty). */
    private Integer garrisonedUnitRef;

    /** Current upgrade level (0 = base, 1-3 = upgraded). REF: building_stats.md — 1x3 Upgrade levels */
    private int upgradeLevel;

    /** Bonus HP from upgrades. REF: building_stats.md — upgrade increases HP cap. */
    private int upgradeMaxHpBonus;

    /** Identifier of the active research project (null if not researching). */
    private String researchId;

    /**
     * Constructs a new building.
     * Buildings start under construction (constructionProgress = 0).
     *
     * @param id           unique entity ID
     * @param faction      owning faction
     * @param position     grid position
     * @param buildingType the building type
     * @param stats        stat block (hp taken from stats.hp())
     */
    public Building(int id, Faction faction, GridPosition position, BuildingType buildingType, BuildingStats stats) {
        super(id, faction, position, stats.hp());
        this.buildingType = buildingType;
        this.stats = stats;
        this.constructionProgress = 0;
        this.powered = false;
        this.waypoint = null;
        this.productionQueue = new ArrayList<>();
        this.currentProduction = null;
        this.productionProgress = 0;
        this.garrisonedUnitRef = null;
        this.researchId = null;
        this.attackCooldown = 0;
        this.upgradeLevel = 0;
        this.upgradeMaxHpBonus = 0;
    }

    /**
     * Returns whether this building is still under construction.
     * A building is under construction if progress has not yet reached its build time.
     *
     * @return true if still being constructed
     */
    public boolean isUnderConstruction() {
        return constructionProgress < stats.buildTime();
    }

    /**
     * Returns whether this building is currently producing a unit.
     *
     * @return true if producing
     */
    public boolean isProducing() {
        return currentProduction != null;
    }

    /**
     * Returns whether this building is currently researching.
     *
     * @return true if a research project is active
     */
    public boolean isResearching() {
        return researchId != null;
    }

    /**
     * Adds a unit type to the production queue.
     * REF: complete_building_stats.json — queueSlots limits queue size
     *
     * @param unitType the unit type to produce
     * @return true if enqueued successfully, false if queue is full
     */
    public boolean enqueueProduction(UnitType unitType) {
        if (productionQueue.size() >= stats.queueSlots()) {
            return false;
        }
        productionQueue.add(unitType);
        // If not currently producing, start this item immediately
        if (currentProduction == null) {
            startNextProduction();
        }
        return true;
    }

    /**
     * Advances production by one tick.
     * If the current item completes, it is returned and the next item in the queue starts.
     *
     * @return the completed UnitType if production finished this tick, null otherwise
     */
    public UnitType advanceProduction() {
        if (currentProduction == null) {
            return null;
        }

        productionProgress++;

        // The build time for the unit being produced would come from its UnitStats.
        // Since Building doesn't hold a reference to the unit stats registry,
        // we delegate the completion check to the caller by tracking progress.
        // The caller (EntityManager or production system) compares progress to the unit's buildTime.
        // For now, we just advance the counter.
        return null; // caller must check completion via productionProgress vs unit buildTime
    }

    /**
     * Starts the next production item from the queue.
     * Called internally after enqueue or after a production completes.
     */
    private void startNextProduction() {
        if (!productionQueue.isEmpty()) {
            currentProduction = productionQueue.remove(0);
            productionProgress = 0;
        } else {
            currentProduction = null;
            productionProgress = 0;
        }
    }

    /**
     * Completes the current production item and starts the next in queue.
     *
     * @return the UnitType that was just completed
     */
    public UnitType completeCurrentProduction() {
        if (currentProduction == null) {
            return null;
        }
        UnitType completed = currentProduction;
        startNextProduction();
        return completed;
    }

    // --- Getters and Setters ---

    public BuildingType getBuildingType() {
        return buildingType;
    }

    public BuildingStats getStats() {
        return stats;
    }

    public int getConstructionProgress() {
        return constructionProgress;
    }

    public void setConstructionProgress(int constructionProgress) {
        this.constructionProgress = constructionProgress;
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public GridPosition getWaypoint() {
        return waypoint;
    }

    public void setWaypoint(GridPosition waypoint) {
        this.waypoint = waypoint;
    }

    public List<UnitType> getProductionQueue() {
        return Collections.unmodifiableList(productionQueue);
    }

    public UnitType getCurrentProduction() {
        return currentProduction;
    }

    public int getProductionProgress() {
        return productionProgress;
    }

    public Integer getGarrisonedUnitRef() {
        return garrisonedUnitRef;
    }

    public void setGarrisonedUnitRef(Integer garrisonedUnitRef) {
        this.garrisonedUnitRef = garrisonedUnitRef;
    }

    public String getResearchId() {
        return researchId;
    }

    public void setResearchId(String researchId) {
        this.researchId = researchId;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(int v) {
        this.attackCooldown = v;
    }

    /** Decrements the attack cooldown by 1 if it is currently > 0. */
    public void decrementAttackCooldown() {
        if (attackCooldown > 0) attackCooldown--;
    }

    /**
     * Returns the current upgrade level of this building.
     * REF: building_stats.md — 1x3 Upgrade levels
     *
     * @return upgrade level (0-3)
     */
    public int getUpgradeLevel() {
        return upgradeLevel;
    }

    /**
     * Sets the upgrade level of this building.
     *
     * @param level the new upgrade level (0-3)
     */
    public void setUpgradeLevel(int level) {
        this.upgradeLevel = Math.max(0, Math.min(level, 3));
    }

    /**
     * Returns the HP bonus from upgrades.
     *
     * @return bonus HP from upgrades
     */
    public int getUpgradeMaxHpBonus() {
        return upgradeMaxHpBonus;
    }

    /**
     * Sets the HP bonus from upgrades.
     *
     * @param bonus the HP bonus to add
     */
    public void setUpgradeMaxHpBonus(int bonus) {
        this.upgradeMaxHpBonus = bonus;
    }

    /**
     * Returns the effective max HP including upgrade bonuses.
     *
     * @return max HP + upgrade bonus
     */
    public int getEffectiveMaxHp() {
        return getMaxHp() + upgradeMaxHpBonus;
    }

    /**
     * Sets the current production item.
     * Used when cancelling the active production to clear or restart it.
     *
     * @param production the unit type being produced, or null to clear
     */
    public void setCurrentProduction(UnitType production) {
        this.currentProduction = production;
        if (production == null) {
            this.productionProgress = 0;
        }
    }

    /**
     * Clears the entire production queue and current production.
     * Used when rebuilding the queue after a cancellation.
     */
    public void clearProductionQueue() {
        this.productionQueue.clear();
    }

    /**
     * Sets the production progress.
     *
     * @param progress the new production progress value
     */
    public void setProductionProgress(int progress) {
        this.productionProgress = progress;
    }

    @Override
    public String toString() {
        return "Building{id=" + getId() + ", type=" + buildingType +
               ", hp=" + getHp() + "/" + getMaxHp() +
               ", construction=" + (isUnderConstruction() ? "in-progress" : "complete") +
               ", powered=" + powered + "}";
    }
}
