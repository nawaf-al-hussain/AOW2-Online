package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A game unit (infantry, vehicle, or mine).
 * Extends {@link Entity} with unit-specific combat and state fields.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 5 — Unit Encyclopedia
 * REF: complete_unit_stats.json — all stat values
 * REF: combat_formulas.md — attack cycles, cooldowns, death marker
 * REF: pathfinding.md — path stored in al[0][unit][], al[1][unit][], max 50 steps
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

    /** Current movement state. REF: pathfinding.md — stuck/attacking/idle states */
    private MovementState movementState;

    /** Computed path as a list of waypoints. REF: pathfinding.md — al[0][unit][], al[1][unit][] */
    private List<GridPosition> path;

    /** Current index within the path list. REF: pathfinding.md — ca[unit + 1010] pathStart */
    private int pathIndex;

    /** Tick accumulator for movement speed. Units move one cell per speed ticks. */
    private int moveTickAccumulator;

    /** The building entity ID this unit is garrisoned inside (null = not garrisoned).
     * When garrisoned, the unit is hidden inside a building and excluded from
     * spatial queries and army strength calculations. */
    private Integer garrisonedBuildingId;

    /** Whether this unit is in siege mode (deployed, increased range/damage, cannot move).
     * REF: combat_formulas.md - research ID 36: "Unit type 10 siege upgrade = 15"
     * Torrent and Sniper can enter siege mode for increased range and damage. */
    private boolean siegeMode;

    /** Ticks remaining before siege mode is fully deployed or undeployed. */
    private int siegeDeployTimer;

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
        this.movementState = MovementState.IDLE;
        this.path = new ArrayList<>();
        this.pathIndex = 0;
        this.moveTickAccumulator = 0;
        this.garrisonedBuildingId = null;
        this.siegeMode = false;
        this.siegeDeployTimer = 0;
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
     * Whether this unit is treated as machinery for combat calculations.
     * Delegates to UnitType.isMachinery().
     */
    public boolean isMachinery() {
        return unitType.isMachinery();
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

    public MovementState getMovementState() {
        return movementState;
    }

    public void setMovementState(MovementState movementState) {
        this.movementState = movementState;
    }

    /**
     * Returns the current movement path as an unmodifiable list.
     * REF: pathfinding.md — al[0][unit][0..49], al[1][unit][0..49]
     *
     * @return unmodifiable list of grid positions in the path
     */
    public List<GridPosition> getPath() {
        return Collections.unmodifiableList(path);
    }

    /**
     * Sets the unit's movement path and resets the path index to 0.
     *
     * @param path list of grid positions forming the path
     */
    public void setPath(List<GridPosition> path) {
        this.path = new ArrayList<>(path);
        this.pathIndex = 0;
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public void setPathIndex(int pathIndex) {
        this.pathIndex = pathIndex;
    }

    /**
     * Returns the current waypoint the unit is heading toward,
     * or null if the path is exhausted.
     *
     * @return next grid position, or null
     */
    public GridPosition getCurrentWaypoint() {
        if (pathIndex < path.size()) {
            return path.get(pathIndex);
        }
        return null;
    }

    /**
     * Advances the path index by one step.
     *
     * @return true if there are more waypoints, false if the path is exhausted
     */
    public boolean advancePath() {
        pathIndex++;
        return pathIndex < path.size();
    }

    /**
     * Returns whether the unit has remaining waypoints in its path.
     *
     * @return true if there are more waypoints to follow
     */
    public boolean hasPathRemaining() {
        return pathIndex < path.size();
    }

    /**
     * Clears the unit's path and resets movement state to IDLE.
     */
    public void clearPath() {
        this.path.clear();
        this.pathIndex = 0;
        this.movementState = MovementState.IDLE;
        this.targetPosition = null;
        this.stuckCounter = 0;
        this.moveTickAccumulator = 0;
    }

    public int getMoveTickAccumulator() {
        return moveTickAccumulator;
    }

    public void setMoveTickAccumulator(int moveTickAccumulator) {
        this.moveTickAccumulator = moveTickAccumulator;
    }

    /**
     * Returns the building entity ID this unit is garrisoned inside, or null if not garrisoned.
     *
     * @return the garrisoned building ID, or null
     */
    public Integer getGarrisonedBuildingId() {
        return garrisonedBuildingId;
    }

    /**
     * Sets the garrisoned building ID. Set to a building ID when garrisoning,
     * or null when ungarrisoning.
     *
     * @param garrisonedBuildingId the building ID, or null
     */
    public void setGarrisonedBuildingId(Integer garrisonedBuildingId) {
        this.garrisonedBuildingId = garrisonedBuildingId;
    }

    /**
     * Returns whether this unit is currently garrisoned inside a building.
     *
     * @return true if the unit is garrisoned
     */
    public boolean isGarrisoned() {
        return garrisonedBuildingId != null;
    }

    /**
     * Returns whether this unit is currently in siege mode.
     * Siege mode increases attack range and damage but prevents movement.
     *
     * @return true if in siege mode
     */
    public boolean isSiegeMode() {
        return siegeMode;
    }

    /**
     * Sets the siege mode state of this unit.
     *
     * @param siegeMode true to enable siege mode, false to disable
     */
    public void setSiegeMode(boolean siegeMode) {
        this.siegeMode = siegeMode;
    }

    /**
     * Returns the remaining siege deployment timer ticks.
     *
     * @return ticks remaining for siege deployment/undeployment
     */
    public int getSiegeDeployTimer() {
        return siegeDeployTimer;
    }

    /**
     * Sets the siege deployment timer.
     *
     * @param siegeDeployTimer ticks for deployment/undeployment
     */
    public void setSiegeDeployTimer(int siegeDeployTimer) {
        this.siegeDeployTimer = siegeDeployTimer;
    }

    /**
     * Returns whether this unit type can enter siege mode.
     * REF: combat_formulas.md - siege mode for Fortress, Hammer, Torrent
     * REF: unit_stats.md - Hammer: "Upgrade allows to switch to the siege mode"
     * REF: unit_stats.md - Rhino: "Upgrade allows siege mode which increases damage and firing rate"
     * REF: unit_stats.md - Fortress: "For rocket salvo you need to activate siege mode"
     *
     * @return true if this unit can siege
     */
    public boolean canSiege() {
        return unitType.isSiegeCapable();
    }

    @Override
    public String toString() {
        return "Unit{id=" + getId() + ", type=" + unitType + ", rank=" + rank +
               ", xp=" + experience + ", hp=" + getHp() + "/" + getMaxHp() + "}";
    }
}
