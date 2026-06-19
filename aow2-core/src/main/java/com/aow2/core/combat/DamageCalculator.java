package com.aow2.core.combat;

import com.aow2.common.config.GameConstants;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;

/**
 * Calculates damage using the exact formulas from the original game.
 * REF: combat_formulas.md - "Damage Calculation for Projectiles"
 *
 * Formula: damage = weaponDamage * (10 - targetArmour) / 10
 * Clamped: damage = max(min(damage, weaponDamage - targetArmour), 1)
 *
 * FIX LOG:
 * - Added missing upper clamp: min(damage, weaponDamage - targetArmor)
 *   REF: combat_formulas.md lines 46-48: damage = max(min(damage, cg[0][10] - targetArmour), 1)
 * - Removed incorrect distance falloff from regular artillery splash
 *   REF: combat_formulas.md lines 214-228: artillery applies SAME full formula to every unit in blast
 * - Added infantry vs machinery damage reduction
 *   REF: combat_formulas.md lines 456-459: infantry deals reduced damage to machinery
 */
public final class DamageCalculator {

    // Seeded RNG for lockstep determinism
    private static final long SEED = 42L;
    private static final java.util.Random RNG = new java.util.Random(SEED);

    private DamageCalculator() {}

    /**
     * Calculate damage from attacker to target.
     * REF: combat_formulas.md - projectile damage formula (lines 46-48)
     *
     * Two-step clamp formula:
     *   damage = weaponDamage * (10 - targetArmour) / 10
     *   damage = max(min(damage, weaponDamage - targetArmour), 1)
     *
     * The min() upper clamp prevents inflated damage at low armor values.
     * Example: weaponDamage=5, targetArmor=2 → raw=4, min(4,3)=3, max(3,1)=3
     */
    public static int calculateDamage(int weaponDamage, int targetArmor) {
        int damage = weaponDamage * (GameConstants.ARMOR_DIVISOR - targetArmor) / GameConstants.ARMOR_DIVISOR;
        // REF: combat_formulas.md - upper clamp prevents inflated damage for low weaponDamage
        damage = Math.min(damage, weaponDamage - targetArmor);
        return Math.max(damage, GameConstants.MIN_DAMAGE);
    }

    /**
     * 31x31 distance lookup table for nuclear damage, indexed by (dy+15)*31+(dx+15).
     * REF: combat_formulas.md lines 236-256 — RE uses bS[bT[79]+attackType] * distanceTable[(dy+15)*31+(dx+15)] / 12
     * The table is essentially a precomputed Chebyshev distance class (square falloff pattern).
     * Each cell stores max(|dx|, |dy|) — the distance class (0-15).
     * Distances beyond 15 return minimum damage.
     */
    private static final int[] NUCLEAR_DISTANCE_TABLE = new int[31 * 31];

    static {
        for (int dy = -15; dy <= 15; dy++) {
            for (int dx = -15; dx <= 15; dx++) {
                // Chebyshev distance = max(|dx|, |dy|) — gives square damage falloff matching original
                int distClass = Math.max(Math.abs(dx), Math.abs(dy));
                NUCLEAR_DISTANCE_TABLE[(dy + 15) * 31 + (dx + 15)] = distClass;
            }
        }
    }

    /**
     * Calculate nuclear damage at offset (dx, dy) from impact point.
     * REF: combat_formulas.md lines 236-256 — nuclear damage uses 31x31 distance lookup table
     * with the same two-step clamp as normal damage.
     *
     * RE formula:
     *   distanceFactor = bS[bT[79]+attackType] * distanceTable[(dy+15)*31+(dx+15)] / 12
     *   damage = max(min(((10 - armour) * distanceFactor) / 10, distanceFactor - armour), 1)
     *
     * Our implementation uses Chebyshev distance from the 31x31 table as the distance class.
     * distanceFactor = weaponDamage * (12 - distClass) / 12
     * Then apply the same two-step clamp.
     *
     * @param weaponDamage base weapon damage
     * @param targetArmor  target's armor value
     * @param dx           x offset from impact point
     * @param dy           y offset from impact point
     * @return nuclear damage after distance falloff and armor clamp
     */
    public static int calculateNuclearDamage(int weaponDamage, int targetArmor, int dx, int dy) {
        // Distances beyond the 31x31 table (|dx|>15 or |dy|>15) return minimum damage
        if (dx < -15 || dx > 15 || dy < -15 || dy > 15) {
            return GameConstants.MIN_DAMAGE;
        }

        // Look up the distance class from the 31x31 table
        int distClass = NUCLEAR_DISTANCE_TABLE[(dy + 15) * 31 + (dx + 15)];

        // ASSUMPTION: distance factor formula uses divisor 12 and linear falloff from 12 to 0 —
        // RE spec confirms a 31x31 distance table is used but the exact formula deriving values from it is reconstructed.
        // REF: combat_formulas.md lines 236-256
        // distanceFactor = weaponDamage * (12 - distClass) / 12
        // At distClass 0 (center): full weaponDamage; at distClass 12+: weaponDamage * 0 / 12 = 0
        int distanceFactor = weaponDamage * (12 - distClass) / 12;

        // Apply the same two-step clamp as normal damage:
        // damage = max(min(distanceFactor * (10 - armor) / 10, distanceFactor - armor), 1)
        int damage = distanceFactor * (GameConstants.ARMOR_DIVISOR - targetArmor) / GameConstants.ARMOR_DIVISOR;
        damage = Math.min(damage, distanceFactor - targetArmor);
        return Math.max(damage, GameConstants.MIN_DAMAGE);
    }

    /**
     * Calculate nuclear damage at a given distance from impact (backward-compatible overload).
     * Computes Chebyshev distance internally and delegates to the dx,dy overload.
     *
     * @param weaponDamage       base weapon damage
     * @param targetArmor        target's armor value
     * @param distanceFromImpact Chebyshev distance from impact point
     * @return nuclear damage after distance falloff and armor clamp
     */
    public static int calculateNuclearDamage(int weaponDamage, int targetArmor, int distanceFromImpact) {
        // Use dx=distanceFromImpact, dy=0 for backward compatibility
        // This gives the same Chebyshev distance as the original single-distance call
        return calculateNuclearDamage(weaponDamage, targetArmor, distanceFromImpact, 0);
    }

    /**
     * Calculate splash damage for artillery at a given distance from impact.
     * REF: combat_formulas.md lines 214-228 - "Splash Damage (Artillery)"
     *
     * Regular artillery splash applies the SAME full damage formula to every unit
     * in the blast radius — no distance falloff. Distance falloff exists ONLY
     * for nuclear/explosion special damage (a separate mechanic, lines 236-256).
     *
     * @param weaponDamage       base weapon damage
     * @param targetArmor        target's armor value
     * @param distanceFromImpact distance from impact point (unused for regular splash)
     * @param isNuclear          true if this is nuclear/explosion damage (uses falloff)
     */
    public static int calculateSplashDamage(int weaponDamage, int targetArmor,
                                             int distanceFromImpact, boolean isNuclear) {
        if (isNuclear) {
            return calculateNuclearDamage(weaponDamage, targetArmor, distanceFromImpact);
        }
        // REF: combat_formulas.md lines 214-228 - regular artillery: no distance falloff
        return calculateDamage(weaponDamage, targetArmor);
    }

    /**
     * Calculate splash damage for artillery (non-nuclear).
     * Backward-compatible overload that defaults to non-nuclear.
     */
    public static int calculateSplashDamage(int weaponDamage, int targetArmor, int distanceFromImpact) {
        return calculateSplashDamage(weaponDamage, targetArmor, distanceFromImpact, false);
    }

    /**
     * Calculate effective armor for a unit, including research bonuses.
     * REF: combat_formulas.md - "Armour Calculation" section
     */
    public static int calculateEffectiveArmor(Unit unit, int armorBonus) {
        return unit.getStats().armor() + armorBonus;
    }

    /**
     * Calculate effective armor for a building, including research bonuses.
     * REF: combat_formulas.md lines 64-68 - "N[(-unitRef - 1) / 50]" for building armor
     * Buildings use a separate armor value from research (IDs 4, 16, 40).
     *
     * @param building       the target building
     * @param buildingArmorBonus armor bonus from research
     * @return effective armor value
     */
    public static int calculateEffectiveArmor(Building building, int buildingArmorBonus) {
        return building.getStats().armor() + buildingArmorBonus;
    }

    /**
     * Calculate effective armor for a building (no research bonus).
     * REF: combat_formulas.md - "Buildings have 0 base armour (use construction HP)"
     */
    public static int calculateEffectiveArmor(Building building) {
        return 0;
    }

    /**
     * Calculate death animation frame for a killed unit.
     * REF: combat_formulas.md - "Infantry vs Machinery Death Animation"
     */
    public static int calculateDeathAnimationFrame(Unit dyingUnit, int attackerCategory) {
        if (dyingUnit.isInfantry()) {
            int base = GameConstants.DEATH_ANIM_BASE[attackerCategory];
            int range = GameConstants.DEATH_ANIM_RANGE[attackerCategory];
            return (base + (int)(RNG.nextDouble() * range)) + 10 - 231;
        } else {
            return 2;
        }
    }

    /**
     * Get damage multiplier based on attacker type vs target type.
     * REF: combat_formulas.md - "Unit vs Building Interaction"
     * REF: combat_formulas.md lines 456-459 - infantry vs machinery reduction
     *
     * FIX LOG:
     * - Added infantry vs machinery damage reduction
     */
    public static double getTargetMultiplier(Unit attacker, boolean isTargetBuilding, boolean isTargetMachinery) {
        if (isTargetBuilding) {
            // ASSUMPTION: 50% damage reduction — RE spec confirms infantry deals reduced damage to buildings but doesn't specify exact multiplier
            // REF: combat_formulas.md lines 456-459
            if (attacker.isInfantry()) return 0.5;
            // ASSUMPTION: 50% bonus — RE spec confirms siege weapons deal bonus damage to buildings but doesn't specify exact multiplier
            // REF: combat_formulas.md - siege weapons bonus vs buildings
            if (attacker.getUnitType().isSiegeCapable()) return 1.5;
            return 1.0;
        }
        // REF: combat_formulas.md lines 456-459 - infantry deals reduced damage to machinery
        if (isTargetMachinery && attacker.isInfantry()) {
            // ASSUMPTION: 30% damage reduction — RE spec confirms reduction exists but doesn't specify exact multiplier
            // REF: combat_formulas.md lines 456-459
            return 0.7;
        }
        return 1.0;
    }

    /**
     * Backward-compatible overload.
     */
    public static double getTargetMultiplier(Unit attacker, boolean isTargetBuilding) {
        return getTargetMultiplier(attacker, isTargetBuilding, false);
    }
}
