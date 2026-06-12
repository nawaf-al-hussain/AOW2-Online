package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;

/**
 * A game unit (infantry, vehicle, or mine).
 * Extends {@link Entity} with unit-specific combat and state fields.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 5 — Unit Encyclopedia
 * REF: complete_unit_stats.json — all stat values
 * REF: combat_formulas.md — attack cycles, cooldowns, death marker
 */
public class Unit extends Entity {

    /** The unit type definition. */
    private final UnitType unitType;

    /** Immutable stat block for this unit type. */
    private final UnitStats stats;

    /** Ticks remaining before the unit can attack again. */
    private int attackCooldown;

    /** Current attack state machine value. REF: combat_formulas.md — attack state */
    private int attackState;

    /** Counts consecutive ticks where the unit cannot reach its target. */
    private int stuckCounter;

    /** Movement target position (null if idle). */
    private GridPosition targetPosition;

    /** Reference to the target unit's entity ID (null if no target). */
    private Integer targetUnitRef;

    /** Current cycle within the attack animation. REF: combat_formulas.md — attack cycle */
    private int attackCycle;

    /** Ticks remaining before the current weapon can fire again. */
    private int weaponCooldown;

    /** Current rank (0 = unranked, 1-3 = ranked). REF: complete_building_stats.json — rank thresholds */
    private int rank;

    /** Accumulated experience points. */
    private int experience;

    /**
     * Constructs a new unit.
     *
     * @param id       unique entity ID
     * @param faction  owning faction
     * @param position initial grid position
     * @param unitType the unit type
     * @param stats    stat block (hp taken from stats.hp())
     */
    public Unit(int id, Faction faction, GridPosition position, UnitType unitType, UnitStats stats) {
        super(id, faction, position, stats.hp());
        this.unitType = unitType;
        this.stats = stats;
        this.attackCooldown = 0;
        this.attackState = 0;
        this.stuckCounter = 0;
        this.targetPosition = null;
        this.targetUnitRef = null;
        this.attackCycle = 0;
        this.weaponCooldown = 0;
        this.rank = 0;
        this.experience = 0;
    }

    // --- Category checks (delegate to UnitType) ---

    /**
     * @return true if this unit is infantry
     */
    public boolean isInfantry() {
        return unitType.isInfantry();
    }

    /**
     * @return true if this unit is a vehicle
     */
    public boolean isVehicle() {
        return unitType.isVehicle();
    }

    /**
     * @return true if this unit is a mine
     */
    public boolean isMine() {
        return unitType.isMine();
    }

    /**
     * @return true if this unit occupies 2 cells (e.g., Fortress)
     * REF: unit_stats.md — bitmask 65536 for 2-cell collision units
     */
    public boolean isLargeUnit() {
        return unitType.isLargeUnit();
    }

    /**
     * Adds experience points and handles rank-up logic.
     * Rank thresholds: [20, 35, 50] for ranks 1, 2, 3.
     * <p>
     * REF: complete_building_stats.json — rank_exp_thresholds: [20, 35, 50]
     * REF: complete_building_stats.json — rank_credit_rewards: [10, 25, 51]
     *
     * @param amount experience to add
     * @return the new rank if a rank-up occurred, otherwise the current rank
     */
    public int addExperience(int amount) {
        if (!isAlive()) {
            return rank;
        }
        experience += amount;

        // Check rank-up against thresholds [20, 35, 50]
        int[] thresholds = {20, 35, 50};
        int newRank = rank;
        for (int i = 0; i < thresholds.length; i++) {
            if (experience >= thresholds[i]) {
                newRank = i + 1;
            }
        }

        if (newRank > rank) {
            rank = newRank;
        }
        return rank;
    }

    // --- Getters and Setters ---

    public UnitType getUnitType() {
        return unitType;
    }

    public UnitStats getStats() {
        return stats;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(int attackCooldown) {
        this.attackCooldown = attackCooldown;
    }

    public int getAttackState() {
        return attackState;
    }

    public void setAttackState(int attackState) {
        this.attackState = attackState;
    }

    public int getStuckCounter() {
        return stuckCounter;
    }

    public void setStuckCounter(int stuckCounter) {
        this.stuckCounter = stuckCounter;
    }

    public GridPosition getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(GridPosition targetPosition) {
        this.targetPosition = targetPosition;
    }

    public Integer getTargetUnitRef() {
        return targetUnitRef;
    }

    public void setTargetUnitRef(Integer targetUnitRef) {
        this.targetUnitRef = targetUnitRef;
    }

    public int getAttackCycle() {
        return attackCycle;
    }

    public void setAttackCycle(int attackCycle) {
        this.attackCycle = attackCycle;
    }

    public int getWeaponCooldown() {
        return weaponCooldown;
    }

    public void setWeaponCooldown(int weaponCooldown) {
        this.weaponCooldown = weaponCooldown;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    @Override
    public String toString() {
        return "Unit{id=" + getId() + ", type=" + unitType + ", rank=" + rank +
               ", xp=" + experience + ", hp=" + getHp() + "/" + getMaxHp() + "}";
    }
}
