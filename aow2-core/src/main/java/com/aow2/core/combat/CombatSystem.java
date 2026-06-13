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
        List<Unit> allUnits = entityManager.getAllUnits();
        for (Unit attacker : allUnits) {
            if (!attacker.isAlive() || attacker.getAttackState() != 3) continue;
            if (attacker.getWeaponCooldown() > 0) {
                attacker.setWeaponCooldown(attacker.getWeaponCooldown() - 1);
                continue;
            }
            Integer targetRef = attacker.getTargetUnitRef();
            if (targetRef != null && targetRef > 0) {
                Unit target = entityManager.getUnit(targetRef);
                if (target != null) {
                    if (target.isAlive() && isInAttackRange(attacker, target)) {
                        performAttack(attacker, target);
                    } else {
                        attacker.setAttackState(1);
                    }
                }
            } else if (targetRef != null && targetRef < 0) {
                int buildingId = -targetRef;
                Building target = entityManager.getBuilding(buildingId);
                if (target != null) {
                    if (target.isAlive() && isInAttackRange(attacker, target)) {
                        performAttackOnBuilding(attacker, target);
                    } else {
                        attacker.setAttackState(1);
                    }
                }
            }
        }
    }

    private void processBuildingAttacks() {
        List<Building> allBuildings = entityManager.getAllBuildings();
        for (Building building : allBuildings) {
            if (!building.isAlive() || !building.getBuildingType().isDefensive() || !building.isPowered()) continue;
            // ASSUMPTION: Defensive buildings auto-target nearest enemy (to be implemented)
        }
    }

    private void processProjectiles() {
        List<Integer> toRemove = new ArrayList<>();
        List<Projectile> projectiles = entityManager.getAllProjectiles();
        for (Projectile proj : projectiles) {
            proj.advance();
            if (proj.hasReachedTarget()) {
                handleProjectileImpact(proj);
                toRemove.add(proj.getId());
            }
        }
        // ASSUMPTION: Dead projectile cleanup handled by removeDeadEntities
    }

    /**
     * Perform an attack from one unit to another.
     * REF: combat_formulas.md - damage formula
     */
    public void performAttack(Unit attacker, Unit target) {
        int weaponDamage = attacker.getStats().damage();
        int targetArmor = DamageCalculator.calculateEffectiveArmor(target, 0);
        int damage = DamageCalculator.calculateDamage(weaponDamage, targetArmor);

        target.takeDamage(damage);
        gameState.enqueueEvent(new DamageAppliedEvent(
            gameState.currentTick(), target.getId(), damage, target.getHp(), attacker.getId()));

        attacker.setWeaponCooldown(attacker.getStats().speed());

        if (!target.isAlive()) {
            DamageCalculator.calculateDeathAnimationFrame(target, 0);
            gameState.enqueueEvent(new UnitKilledEvent(
                gameState.currentTick(), target.getId(), target.getUnitType(), attacker.getId()));
        }
        attacker.addExperience(1);
    }

    /**
     * Perform an attack from a unit to a building.
     * REF: combat_formulas.md - buildings have 0 base armor
     */
    public void performAttackOnBuilding(Unit attacker, Building target) {
        int weaponDamage = attacker.getStats().damage();
        int targetArmor = DamageCalculator.calculateEffectiveArmor(target);
        double targetMultiplier = DamageCalculator.getTargetMultiplier(attacker, true);
        int damage = (int)(DamageCalculator.calculateDamage(weaponDamage, targetArmor) * targetMultiplier);

        target.takeDamage(damage);
        gameState.enqueueEvent(new DamageAppliedEvent(
            gameState.currentTick(), target.getId(), damage, target.getHp(), attacker.getId()));

        if (!target.isAlive()) {
            gameState.enqueueEvent(new BuildingDestroyedEvent(
                gameState.currentTick(), target.getId(), target.getBuildingType(), attacker.getId()));
        }
        attacker.addExperience(2);
    }

    private void handleProjectileImpact(Projectile proj) {
        if (proj.isSplash()) {
            List<Unit> allUnits = entityManager.getAllUnits();
            for (Unit unit : allUnits) {
                if (!unit.isAlive()) continue;
                if (unit.getPosition().distanceTo(proj.getTargetPosition()) <= proj.getSplashRadius()) {
                    int distance = (int) unit.getPosition().distanceTo(proj.getTargetPosition());
                    int damage = DamageCalculator.calculateSplashDamage(
                        proj.getDamage(), unit.getStats().armor(), distance);
                    unit.takeDamage(damage);
                    gameState.enqueueEvent(new DamageAppliedEvent(
                        gameState.currentTick(), unit.getId(), damage, unit.getHp(), proj.getSourceUnitId()));
                    if (!unit.isAlive()) {
                        gameState.enqueueEvent(new UnitKilledEvent(
                            gameState.currentTick(), unit.getId(), unit.getUnitType(), proj.getSourceUnitId()));
                    }
                }
            }
        } else {
            Integer targetRef = proj.getTargetUnitRef();
            if (targetRef != null && targetRef > 0) {
                Unit target = entityManager.getUnit(targetRef);
                if (target != null) {
                    int damage = DamageCalculator.calculateDamage(
                        proj.getDamage(), target.getStats().armor());
                    target.takeDamage(damage);
                    gameState.enqueueEvent(new DamageAppliedEvent(
                        gameState.currentTick(), target.getId(), damage, target.getHp(), proj.getSourceUnitId()));
                    if (!target.isAlive()) {
                        gameState.enqueueEvent(new UnitKilledEvent(
                            gameState.currentTick(), target.getId(), target.getUnitType(), proj.getSourceUnitId()));
                    }
                }
            }
        }
    }

    private boolean isInAttackRange(Unit attacker, Unit target) {
        return attacker.getPosition().distanceTo(target.getPosition()) <= attacker.getStats().attackRange();
    }

    private boolean isInAttackRange(Unit attacker, Building target) {
        return attacker.getPosition().distanceTo(target.getPosition()) <= attacker.getStats().attackRange();
    }
}
