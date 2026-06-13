package com.aow2.core.combat;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;

/**
 * Calculates damage using the exact formulas from the original game.
 * REF: combat_formulas.md - "Damage Calculation for Projectiles"
 *
 * Formula: damage = weaponDamage * (10 - targetArmour) / 10
 * Clamped: damage = max(min(damage, weaponDamage - targetArmour), 1)
 *
 * Buildings have 0 base armour.
 * Infantry death: hp = -1, animation = bi[attackerType] + random(bd[attackerType]) + 10 - 231
 * Machinery death: hp = -1, animation frame = 2
 */
public final class DamageCalculator {

    private DamageCalculator() {}

    /**
     * Calculate damage from attacker to target.
     * REF: combat_formulas.md - projectile damage formula
     *
     * Formula: damage = weaponDamage * (10 - targetArmour) / 10
     * Clamped: damage = max(damage, 1)
     */
    public static int calculateDamage(int weaponDamage, int targetArmor) {
        int damage = weaponDamage * (GameConstants.ARMOR_DIVISOR - targetArmor) / GameConstants.ARMOR_DIVISOR;
        return Math.max(damage, GameConstants.MIN_DAMAGE);
    }

    /**
     * Calculate splash damage for artillery at a given distance from impact.
     * REF: combat_formulas.md - "Splash Damage (Artillery)" section
     */
    public static int calculateSplashDamage(int weaponDamage, int targetArmor, int distanceFromImpact) {
        if (distanceFromImpact == 0) {
            return calculateDamage(weaponDamage, targetArmor);
        }
        double falloff = 1.0 / (1.0 + distanceFromImpact);
        int splashDamage = (int)(weaponDamage * falloff);
        return Math.max(Math.min(
            splashDamage * (GameConstants.ARMOR_DIVISOR - targetArmor) / GameConstants.ARMOR_DIVISOR,
            splashDamage - targetArmor), GameConstants.MIN_DAMAGE);
    }

    /**
     * Calculate effective armor for a unit, including research bonuses.
     * REF: combat_formulas.md - "Armour Calculation" section
     */
    public static int calculateEffectiveArmor(Unit unit, int armorBonus) {
        return unit.getStats().armor() + armorBonus;
    }

    /**
     * Calculate effective armor for a building.
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
            return (base + (int)(Math.random() * range)) + 10 - 231;
        } else {
            return 2;
        }
    }

    /**
     * Get damage multiplier based on attacker type vs target type.
     * REF: combat_formulas.md - "Unit vs Building Interaction"
     */
    public static double getTargetMultiplier(Unit attacker, boolean isTargetBuilding) {
        if (!isTargetBuilding) return 1.0;
        if (attacker.isInfantry()) return 0.5;
        if (attacker.getUnitType() == UnitType.CONFED_TORRENT) return 1.5;
        return 1.0;
    }
}
