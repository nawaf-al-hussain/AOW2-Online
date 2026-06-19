package com.aow2.core.combat;

import com.aow2.common.event.BuildingDestroyedEvent;
import com.aow2.common.event.DamageAppliedEvent;
import com.aow2.common.event.UnitKilledEvent;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.WeaponType;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.entity.Entity;
import com.aow2.core.entity.Projectile;
import com.aow2.core.entity.Unit;
import com.aow2.core.mod.ModEventBridge;
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

    /**
     * Siege mode damage bonus.
     * REF: combat_formulas.md line 356 - Research ID 36: "Unit type 10 siege upgrade = 15"
     * The value 15 represents the siege attack power bonus applied when in siege mode.
     */
    private static final int SIEGE_DAMAGE_BONUS = 15;

    /**
     * Siege mode range bonus.
     * REF: combat_formulas.md - siege mode increases attack range for deployed units.
     * ASSUMPTION: range bonus of +3 based on typical RTS siege mode behavior.
     */
    // ASSUMPTION: +3 range bonus in siege mode — RE spec confirms siege mode increases range but doesn't specify exact value
    // REF: combat_formulas.md - siege mode increases attack range for deployed units
    private static final int SIEGE_RANGE_BONUS = 3;

    /**
     * Building weapon cooldown ticks between attacks.
     * REF: combat_formulas.md - buildings fire every 5 ticks (not every tick).
     */
    private static final int BUILDING_ATTACK_COOLDOWN = 5;

    /**
     * Siege mode deploy/undeploy timer in ticks.
     * Separate from building attack cooldown.
     */
    private static final int SIEGE_DEPLOY_TICKS = 5;

    /** Bunker garrison range bonus added to garrisoned unit's attack range. */
    private static final int BUNKER_RANGE_BONUS = 2;

    private final GameState gameState;
    private final EntityManager entityManager;
    private final ProjectileSystem projectileSystem;
    private final ArmorCalculator armorCalculator;
    private ResearchSystem researchSystem;

    /**
     * Constructs a CombatSystem with default subsystems.
     * The ProjectileSystem will not apply research-adjusted armor to splash damage
     * unless {@link #setResearchSystem(ResearchSystem)} is called.
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
     * Set the research system on the projectile system so splash damage
     * can use research-adjusted armor values.
     * Call this after construction if using the default no-arg CombatSystem constructor.
     *
     * @param researchSystem the research system
     */
    public void setResearchSystem(ResearchSystem researchSystem) {
        this.researchSystem = researchSystem;
        this.projectileSystem.setResearchSystem(armorCalculator, researchSystem);
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

            // REF: combat_formulas.md - BUILDING_ATTACK_COOLDOWN = 5, buildings fire every 5 ticks
            if (building.getAttackCooldown() > 0) {
                building.decrementAttackCooldown();
                continue;
            }

            BuildingType type = building.getBuildingType();

            if (isBunker(type)) {
                processBunkerAttack(building);
            } else if (isRocketOrTower(type)) {
                processDefensiveBuildingAttack(building);
            }

            // Reset building attack cooldown after firing
            building.setAttackCooldown(BUILDING_ATTACK_COOLDOWN);
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
                // FIX (L6): Store death anim frame on unit for client rendering
                nearestEnemy.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(nearestEnemy, 0));
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), nearestEnemy.getId(),
                    nearestEnemy.getUnitType(), bunker.getId()));
                ModEventBridge.fireUnitKilled(nearestEnemy.getId(), nearestEnemy.getUnitType(),
                    nearestEnemy.getFaction(), EconomySystem.playerId(bunker.getFaction()));
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
            // Use research-adjusted armor for the target, not raw base armor
            int targetArmor = armorCalculator.calculateEffectiveArmor(nearestEnemy,
                getCompletedResearchForBuilding(building));
            int effectiveDamage = DamageCalculator.calculateDamage(damage, targetArmor);

            nearestEnemy.takeDamage(effectiveDamage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), nearestEnemy.getId(), effectiveDamage,
                nearestEnemy.getHp(), building.getId()));

            if (!nearestEnemy.isAlive()) {
                // FIX (L6): Store death anim frame on unit for client rendering
                nearestEnemy.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(nearestEnemy, 0));
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), nearestEnemy.getId(),
                    nearestEnemy.getUnitType(), building.getId()));
                ModEventBridge.fireUnitKilled(nearestEnemy.getId(), nearestEnemy.getUnitType(),
                    nearestEnemy.getFaction(), EconomySystem.playerId(building.getFaction()));
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
     * Ranged units (non-BULLET weapons) spawn projectiles that travel to the target.
     * Melee/BULLET units apply damage instantly.
     * <p>
     * REF: combat_formulas.md - damage formula
     * REF: combat_formulas.md "Projectile Spawn" - ranged attacks use projectile system
     *
     * @param attacker the attacking unit
     * @param target   the target unit
     */
    public void performAttack(Unit attacker, Unit target) {
        int weaponDamage = attacker.getStats().damage();
        // Apply siege mode damage bonus only if player has research ID 36
        // REF: combat_formulas.md line 356 - Research ID 36: "Unit type 10 siege upgrade = 15"
        if (attacker.isSiegeMode() && attacker.canSiege()) {
            weaponDamage += getSiegDamageBonus(attacker);
        }

        // Use attackSpeed for weapon cooldown, NOT movement speed
        // REF: combat_formulas.md - weapon cooldown = attackSpeed stat
        int cooldown = attacker.getStats().attackSpeed();
        attacker.setWeaponCooldown(cooldown > 0 ? cooldown : attacker.getStats().speed());

        WeaponType weaponType = attacker.getStats().weaponType();

        // Ranged units (non-BULLET) use projectile system for damage delivery
        if (weaponType != WeaponType.BULLET && weaponType != WeaponType.NONE) {
            boolean splash = weaponType == WeaponType.ROCKET || weaponType == WeaponType.ARTILLERY;
            int splashRadius = splash ? ProjectileSystem.getSplashRadiusForWeapon(weaponType) : 0;
            spawnProjectile(attacker, target, weaponType, weaponDamage, splash, splashRadius);
        } else {
            // Instant damage for BULLET/MELEE weapons
            int targetArmor = armorCalculator.calculateEffectiveArmor(target,
                getCompletedResearch(attacker));
            int damage = DamageCalculator.calculateDamage(weaponDamage, targetArmor);

            target.takeDamage(damage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), target.getId(), damage, target.getHp(), attacker.getId()));

            if (!target.isAlive()) {
                // FIX (L6): Store death anim frame on unit for client rendering
                target.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(target, 0));
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), target.getId(), target.getUnitType(), attacker.getId()));
                ModEventBridge.fireUnitKilled(target.getId(), target.getUnitType(),
                    target.getFaction(), EconomySystem.playerId(attacker.getFaction()));
            }
        }
        attacker.addExperience(1);
    }

    /**
     * Perform an attack from a unit to a building.
     * Ranged units (non-BULLET) spawn projectiles; melee units apply damage instantly.
     * <p>
     * REF: combat_formulas.md - buildings have 0 base armor
     *
     * @param attacker the attacking unit
     * @param target   the target building
     */
    public void performAttackOnBuilding(Unit attacker, Building target) {
        int weaponDamage = attacker.getStats().damage();
        // Apply siege mode damage bonus only if player has research ID 36
        if (attacker.isSiegeMode() && attacker.canSiege()) {
            weaponDamage += getSiegDamageBonus(attacker);
        }

        // Use attackSpeed for weapon cooldown, NOT movement speed
        // REF: combat_formulas.md - weapon cooldown = attackSpeed stat
        int cooldown = attacker.getStats().attackSpeed();
        attacker.setWeaponCooldown(cooldown > 0 ? cooldown : attacker.getStats().speed());

        WeaponType weaponType = attacker.getStats().weaponType();

        // Ranged units (non-BULLET) use projectile system for damage delivery
        if (weaponType != WeaponType.BULLET && weaponType != WeaponType.NONE) {
            boolean splash = weaponType == WeaponType.ROCKET || weaponType == WeaponType.ARTILLERY;
            int splashRadius = splash ? ProjectileSystem.getSplashRadiusForWeapon(weaponType) : 0;
            spawnProjectile(attacker, target, weaponType, weaponDamage, splash, splashRadius);
        } else {
            // Instant damage for BULLET weapons
            int buildingArmorBonus = armorCalculator.getBuildingArmorBonus(target.getFaction());
            int targetArmor = DamageCalculator.calculateEffectiveArmor(target, buildingArmorBonus);
            double targetMultiplier = DamageCalculator.getTargetMultiplier(attacker, true);
            int damage = (int)(DamageCalculator.calculateDamage(weaponDamage, targetArmor) * targetMultiplier);

            target.takeDamage(damage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), target.getId(), damage, target.getHp(), attacker.getId()));

            if (!target.isAlive()) {
                gameState.enqueueEvent(new BuildingDestroyedEvent(
                    gameState.currentTick(), target.getId(), target.getBuildingType(), attacker.getId()));
                ModEventBridge.fireBuildingDestroyed(target.getId(), target.getBuildingType(),
                    target.getFaction(), EconomySystem.playerId(attacker.getFaction()));
            }
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
        unit.setSiegeDeployTimer(SIEGE_DEPLOY_TICKS);
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
        unit.setSiegeDeployTimer(SIEGE_DEPLOY_TICKS);
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
     * Get siege damage bonus for a unit.
     * REF: combat_formulas.md line 356 - Research ID 36: "Unit type 10 siege upgrade = 15"
     * The +15 siege damage bonus only applies when the player has completed research ID 36.
     *
     * @param unit the unit in siege mode
     * @return the siege damage bonus (0 or 15)
     */
    private int getSiegDamageBonus(Unit unit) {
        if (researchSystem == null) return 0;
        int playerId = EconomySystem.playerId(unit.getFaction());
        if (researchSystem.hasResearch(playerId, 36)) {
            return SIEGE_DAMAGE_BONUS;
        }
        return 0;
    }

    /**
     * Get completed research set for a unit's owner.
     * Returns empty set if no research system is available.
     *
     * @param unit the unit
     * @return set of completed research IDs
     */
    private java.util.Set<Integer> getCompletedResearch(Unit unit) {
        if (researchSystem == null) return java.util.Set.of();
        return researchSystem.getCompletedResearch(EconomySystem.playerId(unit.getFaction()));
    }

    /**
     * Get completed research set for a building's owner.
     * Returns empty set if no research system is available.
     *
     * @param building the building
     * @return set of completed research IDs
     */
    private java.util.Set<Integer> getCompletedResearchForBuilding(Building building) {
        if (researchSystem == null) return java.util.Set.of();
        return researchSystem.getCompletedResearch(EconomySystem.playerId(building.getFaction()));
    }

    /**
     * Check if a unit is within attack range of another unit.
     * Accounts for siege mode range bonus.
     */
    private boolean isInAttackRange(Unit attacker, Unit target) {
        int range = getEffectiveAttackRange(attacker);
        int dx = target.getPosition().x() - attacker.getPosition().x();
        int dy = target.getPosition().y() - attacker.getPosition().y();
        return GridPosition.distanceClass(dx, dy) <= range;
    }

    /**
     * Check if a unit is within attack range of a building.
     * Accounts for siege mode range bonus.
     */
    private boolean isInAttackRange(Unit attacker, Building target) {
        int range = getEffectiveAttackRange(attacker);
        int dx = target.getPosition().x() - attacker.getPosition().x();
        int dy = target.getPosition().y() - attacker.getPosition().y();
        return GridPosition.distanceClass(dx, dy) <= range;
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

            double euclDist = unit.getPosition().distanceTo(position);
            // REF: combat_formulas.md — range checks use distanceClass (Chebyshev), not Euclidean
            int dx = unit.getPosition().x() - position.x();
            int dy = unit.getPosition().y() - position.y();
            int dist = GridPosition.distanceClass(dx, dy);
            if (dist <= range && euclDist < nearestDistance) {
                nearest = unit;
                nearestDistance = euclDist;
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
     * Uses the building's stats.weaponType() when available (not NONE),
     * falling back to the hardcoded switch for legacy compatibility.
     *
     * @param building the building entity
     * @return the weapon type
     */
    private WeaponType getBuildingWeaponType(Building building) {
        WeaponType statsWeapon = building.getStats().weaponType();
        if (statsWeapon != WeaponType.NONE) {
            return statsWeapon;
        }
        // Fallback: hardcoded defaults based on building type
        return switch (building.getBuildingType()) {
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
