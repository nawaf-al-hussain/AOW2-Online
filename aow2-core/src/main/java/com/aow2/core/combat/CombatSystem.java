package com.aow2.core.combat;

import com.aow2.common.event.*;
import com.aow2.common.model.*;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.EntityManager;
import com.aow2.core.entity.Projectile;
import com.aow2.core.entity.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all combat interactions in the game.
 * REF: MASTER_DOCUMENTATION.md Section 4.3 - Combat System
 * REF: combat_formulas.md - Complete combat formulas
 */
public class CombatSystem {

    private final GameState gameState;
    private final EntityManager entityManager;

    public CombatSystem(GameState gameState, EntityManager entityManager) {
        this.gameState = gameState;
        this.entityManager = entityManager;
    }

    /**
     * Process all combat for the current tick.
     */
    public void processTick() {
        processUnitAttacks();
        processBuildingAttacks();
        processProjectiles();
    }

    private void processUnitAttacks() {
        for (Unit attacker : entityManager.getAliveUnits()) {
            if (!attacker.isAlive() || attacker.attackState() != 3) continue;
            if (attacker.weaponCooldown() > 0) {
                attacker.setWeaponCooldown(attacker.weaponCooldown() - 1);
                continue;
            }
            if (attacker.targetUnitRef() > 0) {
                entityManager.getUnit(attacker.targetUnitRef()).ifPresent(target -> {
                    if (target.isAlive() && isInAttackRange(attacker, target)) {
                        performAttack(attacker, target);
                    } else {
                        attacker.setAttackState(1);
                    }
                });
            } else if (attacker.targetUnitRef() < 0) {
                int buildingId = -attacker.targetUnitRef();
                entityManager.getBuilding(buildingId).ifPresent(target -> {
                    if (target.isAlive() && isInAttackRange(attacker, target)) {
                        performAttackOnBuilding(attacker, target);
                    } else {
                        attacker.setAttackState(1);
                    }
                });
            }
        }
    }

    private void processBuildingAttacks() {
        for (Building building : entityManager.getAliveBuildings()) {
            if (!building.isAlive() || !building.isDefensive() || !building.isPowered()) continue;
            // ASSUMPTION: Defensive buildings auto-target nearest enemy (to be implemented)
        }
    }

    private void processProjectiles() {
        List<Integer> toRemove = new ArrayList<>();
        for (Projectile proj : entityManager.getActiveProjectiles()) {
            proj.advance();
            if (proj.hasReachedTarget()) {
                handleProjectileImpact(proj);
                toRemove.add(proj.id());
            }
        }
        toRemove.forEach(entityManager::removeProjectile);
    }

    /**
     * Perform an attack from one unit to another.
     * REF: combat_formulas.md - damage formula
     */
    public void performAttack(Unit attacker, Unit target) {
        int weaponDamage = attacker.stats().damage();
        int targetArmor = DamageCalculator.calculateEffectiveArmor(target, 0);
        int damage = DamageCalculator.calculateDamage(weaponDamage, targetArmor);

        target.takeDamage(damage);
        gameState.enqueueEvent(new DamageAppliedEvent(gameState.currentTick(), target.id(), damage, target.hp(), attacker.id()));

        attacker.setWeaponCooldown(attacker.stats().speed());

        if (!target.isAlive()) {
            DamageCalculator.calculateDeathAnimationFrame(target, 0);
            gameState.enqueueEvent(new UnitKilledEvent(gameState.currentTick(), target.id(), target.unitType(), attacker.id()));
        }
        attacker.addExperience(1);
    }

    /**
     * Perform an attack from a unit to a building.
     * REF: combat_formulas.md - buildings have 0 base armor
     */
    public void performAttackOnBuilding(Unit attacker, Building target) {
        int weaponDamage = attacker.stats().damage();
        int targetArmor = DamageCalculator.calculateEffectiveArmor(target);
        double targetMultiplier = DamageCalculator.getTargetMultiplier(attacker, true);
        int damage = (int)(DamageCalculator.calculateDamage(weaponDamage, targetArmor) * targetMultiplier);

        target.takeDamage(damage);
        gameState.enqueueEvent(new DamageAppliedEvent(gameState.currentTick(), target.id(), damage, target.hp(), attacker.id()));

        if (!target.isAlive()) {
            gameState.enqueueEvent(new BuildingDestroyedEvent(gameState.currentTick(), target.id(), target.buildingType(), attacker.id()));
        }
        attacker.addExperience(2);
    }

    private void handleProjectileImpact(Projectile proj) {
        if (proj.hasSplash()) {
            for (Unit unit : entityManager.getAliveUnits()) {
                if (unit.position().distanceTo(proj.targetPosition()) <= proj.splashRadius()) {
                    int distance = (int) unit.position().distanceTo(proj.targetPosition());
                    int damage = DamageCalculator.calculateSplashDamage(proj.damage(), unit.stats().armor(), distance);
                    unit.takeDamage(damage);
                    gameState.enqueueEvent(new DamageAppliedEvent(gameState.currentTick(), unit.id(), damage, unit.hp(), proj.sourceUnitId()));
                    if (!unit.isAlive()) {
                        gameState.enqueueEvent(new UnitKilledEvent(gameState.currentTick(), unit.id(), unit.unitType(), proj.sourceUnitId()));
                    }
                }
            }
        } else {
            if (proj.targetUnitRef() > 0) {
                entityManager.getUnit(proj.targetUnitRef()).ifPresent(target -> {
                    int damage = DamageCalculator.calculateDamage(proj.damage(), target.stats().armor());
                    target.takeDamage(damage);
                    gameState.enqueueEvent(new DamageAppliedEvent(gameState.currentTick(), target.id(), damage, target.hp(), proj.sourceUnitId()));
                    if (!target.isAlive()) {
                        gameState.enqueueEvent(new UnitKilledEvent(gameState.currentTick(), target.id(), target.unitType(), proj.sourceUnitId()));
                    }
                });
            }
        }
    }

    private boolean isInAttackRange(Unit attacker, Unit target) {
        return attacker.position().distanceTo(target.position()) <= attacker.stats().attackRange();
    }

    private boolean isInAttackRange(Unit attacker, Building target) {
        return attacker.position().distanceTo(target.position()) <= attacker.stats().attackRange();
    }
}
