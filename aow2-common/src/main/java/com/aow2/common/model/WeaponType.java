package com.aow2.common.model;

/**
 * Weapon types used by units and buildings.
 * REF: combat_formulas.md - projectile types: bullet, rocket, artillery shell, flame
 *
 * Each weapon type has associated projectile speed and splash radius data.
 * REF: combat_formulas.md "Projectile System" - speedTable and splash radius per weapon type
 * ASSUMPTION: Speed and splash values are reconstructed from RE analysis;
 * exact values may differ from the original compiled game.
 */
public enum WeaponType {
    /** Standard bullet — instant or fast travel, no splash. Speed index: 8 */
    BULLET(8, 0),
    /** Rocket — moderate travel time, 2-tile splash. Speed index: 4 */
    ROCKET(4, 2),
    /** Artillery shell — slow travel, 3-tile splash, fixed flight time. Speed index: 3 */
    ARTILLERY(3, 3),
    /** Flame — short range, 1-tile splash. Speed index: 6 */
    FLAME(6, 1),
    /** Machine gun — rapid fire, no splash. Speed index: 10 */
    MACHINE_GUN(10, 0),
    /** Sniper rifle — very fast travel, no splash. Speed index: 12 */
    SNIPER_RIFLE(12, 0),
    /** No weapon — unarmed entities. */
    NONE(1, 0);

    /** Projectile speed index — higher means faster (shorter flight time).
     *  REF: combat_formulas.md - speedTable[projectileType] */
    private final int speed;

    /** Splash radius in tiles (0 = no splash).
     *  REF: combat_formulas.md - "Splash Damage (Artillery)" section */
    private final int splashRadius;

    WeaponType(int speed, int splashRadius) {
        this.speed = speed;
        this.splashRadius = splashRadius;
    }

    /** @return projectile speed index (higher = faster) */
    public int speed() { return speed; }

    /** @return splash radius in tiles (0 = no splash) */
    public int splashRadius() { return splashRadius; }
}
