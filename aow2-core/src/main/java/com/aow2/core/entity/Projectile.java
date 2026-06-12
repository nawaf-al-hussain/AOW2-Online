package com.aow2.core.entity;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.WeaponType;

/**
 * A projectile traveling from a source unit to a target position or unit.
 * Extends {@link Entity} with projectile-specific fields for tracking travel and impact.
 * <p>
 * REF: combat_formulas.md — projectile types: bullet, rocket, artillery shell, flame
 * REF: combat_formulas.md — splash damage for rockets and artillery
 */
public class Projectile extends Entity {

    /** Weapon type that created this projectile. */
    private final WeaponType weaponType;

    /** Damage this projectile will deal on impact. */
    private final int damage;

    /** Entity ID of the unit that fired this projectile. */
    private final int sourceUnitId;

    /** Entity ID of the target unit (null if targeting ground position). */
    private Integer targetUnitRef;

    /** Target position (where the projectile is heading). */
    private GridPosition targetPosition;

    /** Whether this projectile deals splash damage. */
    private final boolean splash;

    /** Radius of splash damage in tiles (0 if no splash). */
    private final int splashRadius;

    /** Ticks remaining before the projectile reaches its target. */
    private int travelTicks;

    /**
     * Constructs a new projectile.
     * Projectiles have hp=1 as a lifecycle marker; they are not damageable in the normal sense.
     *
     * @param id            unique entity ID
     * @param faction       faction of the firing unit
     * @param position      starting position
     * @param weaponType    weapon type
     * @param damage        damage on impact
     * @param sourceUnitId  entity ID of the firing unit
     * @param targetUnitRef entity ID of the target unit (null for ground-targeted)
     * @param targetPosition where the projectile is heading
     * @param splash        whether splash damage applies
     * @param splashRadius  splash radius in tiles
     * @param travelTicks   ticks to reach target
     */
    public Projectile(int id, Faction faction, GridPosition position, WeaponType weaponType,
                      int damage, int sourceUnitId, Integer targetUnitRef,
                      GridPosition targetPosition, boolean splash, int splashRadius,
                      int travelTicks) {
        super(id, faction, position, 1); // hp=1 as lifecycle marker
        this.weaponType = weaponType;
        this.damage = damage;
        this.sourceUnitId = sourceUnitId;
        this.targetUnitRef = targetUnitRef;
        this.targetPosition = targetPosition;
        this.splash = splash;
        this.splashRadius = splashRadius;
        this.travelTicks = travelTicks;
    }

    /**
     * Advances the projectile by one tick, decrementing travel time.
     */
    public void advance() {
        if (travelTicks > 0) {
            travelTicks--;
        }
    }

    /**
     * Returns whether this projectile has reached (or passed) its target.
     *
     * @return true if the projectile should be processed for impact
     */
    public boolean hasReachedTarget() {
        return travelTicks <= 0;
    }

    // --- Getters ---

    public WeaponType getWeaponType() {
        return weaponType;
    }

    public int getDamage() {
        return damage;
    }

    public int getSourceUnitId() {
        return sourceUnitId;
    }

    public Integer getTargetUnitRef() {
        return targetUnitRef;
    }

    public GridPosition getTargetPosition() {
        return targetPosition;
    }

    public boolean isSplash() {
        return splash;
    }

    public int getSplashRadius() {
        return splashRadius;
    }

    public int getTravelTicks() {
        return travelTicks;
    }

    @Override
    public String toString() {
        return "Projectile{id=" + getId() + ", weapon=" + weaponType +
               ", dmg=" + damage + ", ticks=" + travelTicks + "}";
    }
}
