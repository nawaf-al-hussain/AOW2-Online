package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mine entity that can be placed on the map.
 * Mines are immobile units that detonate when enemy units enter their trigger radius.
 * Three mine types exist: Scorpio, Frog, and Lizard.
 * <p>
 * FIX(H-10): Mine integration is now complete:
 * - EntityManager tracks mines via addMine()/getAllMines()/removeDeadEntities()
 * - MineDetonationSystem.processTick() is called by TickManager each tick
 * - Mine placement is handled by CommandProcessor for mine-laying unit actions
 * Remaining: EntityRenderer should be updated to render mine sprites in z-order
 * with other entities (client-side rendering, not a core logic issue).
 * <p>
 * REF: complete_unit_stats.json mines section - 3 mine types (Scorpio, Frog, Lizard)
 * REF: unit_stats.md - mine trigger radius and detonation damage
 */
public final class Mine extends Entity {

    private static final Logger LOG = LoggerFactory.getLogger(Mine.class);

    /** The specific mine type. */
    private final UnitType mineType;

    /** Immutable stat block for this mine type. */
    private final UnitStats stats;

    /** Whether this mine has been armed and is ready to detonate. */
    private boolean armed;

    /** Whether this mine has been triggered and is detonating. */
    private boolean triggered;

    /** The radius (in tiles) within which enemy units trigger detonation. */
    private int triggerRadius;

    /**
     * Constructs a new mine.
     * Mines start unarmed and must be armed before they can detect enemies.
     *
     * @param id            unique entity ID
     * @param faction       owning faction
     * @param position      grid position where the mine is placed
     * @param mineType      the specific mine type (SCORPIO, FROG, or LIZARD)
     * @param stats         stat block (hp and damage taken from stats)
     * @param triggerRadius detonation trigger radius in tiles
     */
    public Mine(int id, Faction faction, GridPosition position,
                UnitType mineType, UnitStats stats, int triggerRadius) {
        super(id, faction, position, stats.hp());
        this.mineType = mineType;
        this.stats = stats;
        this.armed = false;
        this.triggered = false;
        this.triggerRadius = triggerRadius;
    }

    /**
     * Arms this mine, enabling it to detect and detonate on enemy proximity.
     */
    public void arm() {
        if (!armed && isAlive()) {
            this.armed = true;
            LOG.debug("Mine {} armed at {}", getId(), getPosition());
        }
    }

    /**
     * Disarms this mine, preventing detonation.
     * Typically used when the owning player wants to reclaim or reposition the mine.
     */
    public void disarm() {
        if (armed) {
            this.armed = false;
            LOG.debug("Mine {} disarmed at {}", getId(), getPosition());
        }
    }

    /**
     * Checks whether an enemy at the given position should trigger this mine.
     * The mine must be armed, not already triggered, and the enemy must be
     * within the trigger radius.
     *
     * @param enemyPosition position of the potential enemy unit
     * @return true if this mine would detonate
     */
    public boolean checkTrigger(GridPosition enemyPosition) {
        if (!armed || triggered || !isAlive()) {
            return false;
        }
        int dist = GridPosition.distanceClass(
            getPosition().x() - enemyPosition.x(),
            getPosition().y() - enemyPosition.y());
        return dist <= triggerRadius;
    }

    /**
     * Detonates this mine, dealing its damage value and destroying itself.
     * The mine's hp is set to -1 (death marker) upon detonation.
     *
     * @return the damage value dealt by this mine's detonation
     */
    public int detonate() {
        if (triggered || !isAlive()) {
            return 0;
        }
        this.triggered = true;
        this.armed = false;
        int damage = stats.damage();
        takeDamage(getHp()); // destroy self
        LOG.info("Mine {} detonated at {} dealing {} damage", getId(), getPosition(), damage);
        return damage;
    }

    /**
     * Returns whether this mine is currently armed and ready to detonate.
     *
     * @return true if armed
     */
    public boolean isArmed() {
        return armed;
    }

    /**
     * Returns whether this mine has already detonated.
     *
     * @return true if detonated
     */
    public boolean isTriggered() {
        return triggered;
    }

    /**
     * Returns the trigger radius in tiles.
     *
     * @return trigger radius
     */
    public int getTriggerRadius() {
        return triggerRadius;
    }

    /**
     * Sets the trigger radius.
     *
     * @param triggerRadius new trigger radius in tiles
     */
    public void setTriggerRadius(int triggerRadius) {
        this.triggerRadius = triggerRadius;
    }

    /**
     * Returns the mine's unit type.
     *
     * @return the mine type
     */
    public UnitType getMineType() {
        return mineType;
    }

    /**
     * Returns the mine's stat block.
     *
     * @return unit stats
     */
    public UnitStats getStats() {
        return stats;
    }

    @Override
    public String toString() {
        return "Mine{id=" + getId() + ", type=" + mineType +
               ", armed=" + armed + ", triggered=" + triggered +
               ", hp=" + getHp() + "/" + getMaxHp() + "}";
    }
}
