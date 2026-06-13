package com.aow2.core.combat;

import com.aow2.common.event.BuildingDestroyedEvent;
import com.aow2.common.event.DamageAppliedEvent;
import com.aow2.common.event.UnitKilledEvent;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.WeaponType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import com.aow2.core.entity.Entity;
import com.aow2.core.entity.Projectile;
import com.aow2.core.entity.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages all combat interactions in the game.
 * <p>
 * Handles unit attacks, building attacks (defensive structures and bunker garrisons),
 * projectile processing, and siege mode logic.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 4.3 - Combat System
 * REF: combat_formulas.md - Complete combat formulas
 */
public class CombatSystem {

    private static final Logger log = LoggerFactory.getLogger(CombatSystem.class);

    /** Siege mode range bonus for Torrent and Sniper units. */
    private static final int SIEGE_RANGE_BONUS = 2;

    /** Siege mode damage bonus for Torrent and Sniper units. */
    private static final int SIEGE_DAMAGE_BONUS = 2;

    /** Building weapon cooldown ticks between attacks. */
    private static final int BUILDING_ATTACK_COOLDOWN = 5;

    /** Bunker garrison range bonus added to garrisoned unit's attack range. */
    private static final int BUNKER_RANGE_BONUS = 2;

    private final GameState gameState;
    private final EntityManager entityManager;
    private final ProjectileSystem projectileSystem;
    private final ArmorCalculator armorCalculator;

    /**
     * Constructs a CombatSystem with default subsystems.
     *
     * @param gameState     the current game state
     * @param entityManager the entity manager
     */
    public CombatSystem(GameState gameState, EntityManager entityManager) {
        this(gameState, entityManager, new ProjectileSystem(), new ArmorCalculator());
    }

    /**
     * Constructs a CombatSystem with explicit subsystems (for testing).
     *
     * @param gameState         the current game state
     * @param entityManager     the entity manager
     * @param projectileSystem  the projectile system
     * @param armorCalculator   the armor calculator
     */
    public CombatSystem(GameState gameState, EntityManager entityManager,
                        ProjectileSystem projectileSystem, ArmorCalculator armorCalculator) {
        this.gameState = gameState;
        this.entityManager = entityManager;
        this.projectileSystem = projectileSystem;
        this.armorCalculator = armorCalculator;
    }

    /**
     * Process all combat for the current tick.
     * Order: unit attacks → building attacks → siege mode → projectiles.
     */
    public void processTick() {
        processUnitAttacks();
        processBuildingAttacks();
        processSiegeDeployments();
        projectileSystem.processTick(entityManager, gameState);
    }

    /**
     * Process unit attacks for all alive units in attack state.
     * Units in attack state 3 with a valid target will perform attacks.
     * <p>
     * REF: combat_formulas.md - attack cycle and cooldown system
     */
    private void processUnitAttacks() {
        List<Unit> allUnits = entityManager.getAllUnits();
        for (Unit attacker : allUnits) {
            if (!attacker.isAlive() || attacker.getAttackState() != 3) continue;
            if (attacker.getWeaponCooldown() > 0) {
                attacker.setWeaponCooldown(attacker.getWeaponCooldown() - 1);
                continue;
            }
            // Units in siege mode cannot move but can still attack
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

    /**
     * Process building attacks for defensive structures.
     * <p>
     * Defensive buildings (Bunker, Rocket Launcher, Tower) auto-target the nearest
     * enemy unit within their attack range. Bunkers use their garrisoned unit's
     * weapon for attacks with a range bonus.
     * <p>
     * REF: combat_formulas.md - building attack logic
     * REF: combat_formulas.md - bunker garrison attack with range bonus
     */
    private void processBuildingAttacks() {
        List<Building> allBuildings = entityManager.getAllBuildings();
        for (Building building : allBuildings) {
            if (!building.isAlive() || !building.getBuildingType().isDefensive()) continue;
            if (!building.isPowered()) continue;
            if (building.isUnderConstruction()) continue;

            BuildingType type = building.getBuildingType();

            if (isBunker(type)) {
                processBunkerAttack(building);
            } else if (isRocketOrTower(type)) {
                processDefensiveBuildingAttack(building);
            }
        }
    }

    /**
     * Process bunker garrison attack logic.
     * A bunker with a garrisoned unit uses that unit's weapon to attack enemies.
     * The garrisoned unit gets a range bonus while inside the bunker.
     * <p>
     * REF: combat_formulas.md - bunker garrison attack
     *
     * @param bunker the bunker building
     */
    private void processBunkerAttack(Building bunker) {
        Integer garrisonedRef = bunker.getGarrisonedUnitRef();
        if (garrisonedRef == null) return;

        Unit garrison = entityManager.getUnit(garrisonedRef);
        if (garrison == null || !garrison.isAlive()) return;

        // Find nearest enemy within garrison range
        int effectiveRange = garrison.getStats().attackRange() + BUNKER_RANGE_BONUS;
        Unit nearestEnemy = findNearestEnemyUnit(bunker.getPosition(), bunker.getFaction(), effectiveRange);

        if (nearestEnemy != null) {
            // Garrison attacks using its weapon damage
            int weaponDamage = garrison.getStats().damage();
            int targetArmor = nearestEnemy.getStats().armor();
            int damage = DamageCalculator.calculateDamage(weaponDamage, targetArmor);

            nearestEnemy.takeDamage(damage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), nearestEnemy.getId(), damage,
                nearestEnemy.getHp(), bunker.getId()));

            if (!nearestEnemy.isAlive()) {
                DamageCalculator.calculateDeathAnimationFrame(nearestEnemy, 0);
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), nearestEnemy.getId(),
                    nearestEnemy.getUnitType(), bunker.getId()));
            }
            garrison.addExperience(1);
        }
    }

    /**
     * Process defensive building attacks (Rocket Launcher, Tower).
     * These buildings auto-target the nearest enemy and fire projectiles.
     * <p>
     * REF: combat_formulas.md - defensive building auto-targeting
     *
     * @param building the defensive building
     */
    private void processDefensiveBuildingAttack(Building building) {
        BuildingType type = building.getBuildingType();
        int attackRange = building.getStats().attackRange();
        int damage = building.getStats().attackBonus();

        // Find nearest enemy unit within range
        Unit nearestEnemy = findNearestEnemyUnit(building.getPosition(), building.getFaction(), attackRange);

        if (nearestEnemy != null) {
            // Determine weapon type based on building type
            WeaponType weaponType = getBuildingWeaponType(type);

            // Create a temporary "attacker unit" reference for the projectile
            // The building itself fires, so source ID is building ID
            int targetArmor = nearestEnemy.getStats().armor();
            int effectiveDamage = DamageCalculator.calculateDamage(damage, targetArmor);

            nearestEnemy.takeDamage(effectiveDamage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), nearestEnemy.getId(), effectiveDamage,
                nearestEnemy.getHp(), building.getId()));

            if (!nearestEnemy.isAlive()) {
                DamageCalculator.calculateDeathAnimationFrame(nearestEnemy, 0);
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), nearestEnemy.getId(),
                    nearestEnemy.getUnitType(), building.getId()));
            }
        }
    }

    /**
     * Process siege mode deployment timers and siege-specific logic.
     * Units in siege mode gain increased range and damage.
     * Units transitioning in or out of siege mode have a deploy timer.
     */
    private void processSiegeDeployments() {
        List<Unit> allUnits = entityManager.getAllUnits();
        for (Unit unit : allUnits) {
            if (!unit.isAlive()) continue;
            if (unit.getSiegeDeployTimer() > 0) {
                unit.setSiegeDeployTimer(unit.getSiegeDeployTimer() - 1);
            }
        }
    }

    /**
     * Perform an attack from one unit to another.
     * <p>
     * REF: combat_formulas.md - damage formula
     *
     * @param attacker the attacking unit
     * @param target   the target unit
     */
    public void performAttack(Unit attacker, Unit target) {
        int weaponDamage = attacker.getStats().damage();
        // Apply siege mode damage bonus
        if (attacker.isSiegeMode() && attacker.canSiege()) {
            weaponDamage += SIEGE_DAMAGE_BONUS;
        }
        int targetArmor = target.getStats().armor();
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
     * <p>
     * REF: combat_formulas.md - buildings have 0 base armor
     *
     * @param attacker the attacking unit
     * @param target   the target building
     */
    public void performAttackOnBuilding(Unit attacker, Building target) {
        int weaponDamage = attacker.getStats().damage();
        // Apply siege mode damage bonus
        if (attacker.isSiegeMode() && attacker.canSiege()) {
            weaponDamage += SIEGE_DAMAGE_BONUS;
        }
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

    /**
     * Spawn a projectile from a unit to a target entity.
     * Delegates to the ProjectileSystem.
     *
     * @param attacker   the firing unit
     * @param target     the target entity
     * @param weaponType the weapon type
     * @param damage     the damage on impact
     * @param splash     whether splash damage applies
     * @param splashRadius splash radius
     * @return the spawned projectile, or null if limit reached
     */
    public Projectile spawnProjectile(Unit attacker, Entity target, WeaponType weaponType,
                                      int damage, boolean splash, int splashRadius) {
        return projectileSystem.spawnProjectile(
            attacker, target, weaponType, damage, splash, splashRadius, entityManager);
    }

    /**
     * Enter siege mode for a unit.
     * Only Torrent and Sniper can enter siege mode.
     * The unit cannot move while in siege mode.
     *
     * @param unit the unit to enter siege mode
     * @return true if siege mode was activated
     */
    public boolean enterSiegeMode(Unit unit) {
        if (!unit.canSiege() || !unit.isAlive()) {
            return false;
        }
        if (unit.isSiegeMode()) {
            return false; // already in siege mode
        }
        unit.setSiegeMode(true);
        unit.setSiegeDeployTimer(BUILDING_ATTACK_COOLDOWN);
        unit.clearPath(); // Cannot move in siege mode
        log.debug("Unit {} entering siege mode", unit.getId());
        return true;
    }

    /**
     * Exit siege mode for a unit.
     * The unit can move again after a brief undeploy timer.
     *
     * @param unit the unit to exit siege mode
     * @return true if siege mode was deactivated
     */
    public boolean exitSiegeMode(Unit unit) {
        if (!unit.isSiegeMode()) {
            return false;
        }
        unit.setSiegeMode(false);
        unit.setSiegeDeployTimer(BUILDING_ATTACK_COOLDOWN);
        log.debug("Unit {} exiting siege mode", unit.getId());
        return true;
    }

    /**
     * Get the effective attack range for a unit, including siege mode bonus.
     *
     * @param unit the unit
     * @return effective attack range
     */
    public int getEffectiveAttackRange(Unit unit) {
        int range = unit.getStats().attackRange();
        if (unit.isSiegeMode() && unit.canSiege()) {
            range += SIEGE_RANGE_BONUS;
        }
        return range;
    }

    // --- Helper methods ---

    /**
     * Check if a unit is within attack range of another unit.
     * Accounts for siege mode range bonus.
     */
    private boolean isInAttackRange(Unit attacker, Unit target) {
        int range = getEffectiveAttackRange(attacker);
        return attacker.getPosition().distanceTo(target.getPosition()) <= range;
    }

    /**
     * Check if a unit is within attack range of a building.
     * Accounts for siege mode range bonus.
     */
    private boolean isInAttackRange(Unit attacker, Building target) {
        int range = getEffectiveAttackRange(attacker);
        return attacker.getPosition().distanceTo(target.getPosition()) <= range;
    }

    /**
     * Find the nearest enemy unit within range of a position.
     *
     * @param position center position to search from
     * @param ownerFaction faction of the building (enemies are opposite faction)
     * @param range   maximum range to search
     * @return the nearest enemy unit, or null if none found
     */
    private Unit findNearestEnemyUnit(GridPosition position, Faction ownerFaction, int range) {
        Unit nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Unit unit : entityManager.getAllUnits()) {
            if (!unit.isAlive()) continue;
            if (unit.getFaction() == ownerFaction) continue;

            double distance = unit.getPosition().distanceTo(position);
            if (distance <= range && distance < nearestDistance) {
                nearest = unit;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * Check if a building type is a bunker.
     */
    private boolean isBunker(BuildingType type) {
        return type == BuildingType.CONFED_BUNKER || type == BuildingType.REBEL_BUNKER;
    }

    /**
     * Check if a building type is a rocket launcher or tower.
     */
    private boolean isRocketOrTower(BuildingType type) {
        return type == BuildingType.CONFED_ROCKET_LAUNCHER ||
               type == BuildingType.REBEL_TOWER;
    }

    /**
     * Get the weapon type for a defensive building.
     * Rocket launchers fire rockets; towers fire bullets.
     *
     * @param type the building type
     * @return the weapon type
     */
    private WeaponType getBuildingWeaponType(BuildingType type) {
        return switch (type) {
            case CONFED_ROCKET_LAUNCHER -> WeaponType.ROCKET;
            case REBEL_TOWER -> WeaponType.BULLET;
            default -> WeaponType.NONE;
        };
    }

    // --- Accessors for subsystems ---

    /**
     * Get the projectile system instance.
     *
     * @return the projectile system
     */
    public ProjectileSystem getProjectileSystem() {
        return projectileSystem;
    }

    /**
     * Get the armor calculator instance.
     *
     * @return the armor calculator
     */
    public ArmorCalculator getArmorCalculator() {
        return armorCalculator;
    }
}
