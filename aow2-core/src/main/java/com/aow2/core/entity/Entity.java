package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;

/**
 * Base class for all game entities (units, buildings, projectiles).
 * Provides common fields: identity, faction, position, and health.
 * <p>
 * REF: combat_formulas.md — "ca[unit + 1616] = -1" indicates dead units have hp set to -1.
 * REF: MASTER_DOCUMENTATION.md Section 3.1 — Entity lifecycle
 */
public abstract class Entity {

    /** Unique entity identifier within the match. */
    private final int id;

    /** Faction this entity belongs to. NEUTRAL for unowned objects. */
    private final Faction faction;

    /** Current grid position on the map. */
    private GridPosition position;

    /** Current hit points. -1 indicates a dead/destroyed entity. */
    private int hp;

    /** Maximum hit points (used to cap healing). */
    private final int maxHp;

    /**
     * Constructs a new entity.
     *
     * @param id       unique entity ID
     * @param faction  owning faction
     * @param position initial grid position
     * @param maxHp    maximum hit points (initial hp is set to this value)
     */
    protected Entity(int id, Faction faction, GridPosition position, int maxHp) {
        this.id = id;
        this.faction = faction;
        this.position = position;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    /**
     * Applies damage to this entity.
     * If hp drops to 0 or below, it is set to -1 to mark death.
     * <p>
     * REF: combat_formulas.md — "ca[unit + 1616] = -1" — dead units store hp as -1
     *
     * @param amount raw damage amount (after armor reduction, applied externally)
     */
    public void takeDamage(int amount) {
        if (hp <= 0) {
            return; // already dead
        }
        hp -= amount;
        if (hp <= 0) {
            hp = -1; // REF: combat_formulas.md — death marker
        }
    }

    /**
     * Heals this entity by the given amount, capped at maxHp.
     * Dead entities cannot be healed.
     *
     * @param amount healing amount
     */
    public void heal(int amount) {
        if (hp <= 0) {
            return; // cannot heal dead entities
        }
        hp = Math.min(hp + amount, maxHp);
    }

    /**
     * Returns whether this entity is alive.
     * An entity is alive if hp &gt; 0.
     *
     * @return true if alive, false if dead/destroyed
     */
    public boolean isAlive() {
        return hp > 0;
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public Faction getFaction() {
        return faction;
    }

    public GridPosition getPosition() {
        return position;
    }

    public void setPosition(GridPosition position) {
        this.position = position;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    /**
     * Sets the current HP directly. Used when the effective max HP cap
     * differs from the base maxHp (e.g., after building upgrades).
     *
     * @param hp the new HP value
     */
    public void setHp(int hp) {
        this.hp = hp;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", faction=" + faction + ", hp=" + hp + "/" + maxHp + "}";
    }
}
