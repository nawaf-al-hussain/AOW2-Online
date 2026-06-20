package com.aow2.core.combat;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages projectile creation, movement, and impact.
 * <p>
 * Projectiles are tracked in arrays in the original game (400 max active):
 * t[idx] = grid X, u[idx] = grid Y, v[idx] = pixel offset X, w[idx] = pixel offset Y,
 * A[idx] = velocity X, B[idx] = velocity Y, C[idx] = travel time remaining,
 * G[idx] = projectile type, y[idx] = source unit ref, x[idx] = target unit ref.
 * <p>
 * REF: combat_formulas.md "Projectile System" - 400 max active projectiles
 * REF: combat_formulas.md "Projectile Movement (per tick)" - pixel-based movement
 * REF: combat_formulas.md "Projectile Spawn" - flight time = distanceTable / speedTable
 */
public final class ProjectileSystem {

    private static final Logger log = LoggerFactory.getLogger(ProjectileSystem.class);

    /**
     * Fixed flight time for artillery projectiles.
     * REF: combat_formulas.md — flightTime = (at[6][59] - at[5][59]) + 1 (fixed, not distance-based)
     * UNVERIFIED (M-15): ASSUMPTION: 15 ticks based on typical artillery delay in the original game.
     */
    private static final int ARTILLERY_FIXED_FLIGHT_TIME = 15;

    /** Maximum number of active projectiles allowed simultaneously. */
    public static final int MAX_PROJECTILES = 400;

    /**
     * Projectile speed table indexed by weapon type ordinal.
     * Higher values mean faster projectiles (shorter flight time).
     * REF: combat_formulas.md - speedTable[projectileType]
     */
    private static final int[] SPEED_TABLE = {
        8,   // BULLET
        4,   // ROCKET
        3,   // ARTILLERY
        6,   // FLAME
        10,  // MACHINE_GUN
        12,  // SNIPER_RIFLE
        1    // NONE
    };

    /**
     * Splash radius by weapon type. ROCKET, ARTILLERY, and FLAME have splash.
     * REF: combat_formulas.md - "Splash Damage (Artillery)" section
     * FIX(M-3): FLAME splash was defined but unreachable — now enabled.
     */
    private static final int[] SPLASH_RADIUS = {
        0,  // BULLET
        2,  // ROCKET
        3,  // ARTILLERY
        1,  // FLAME
        0,  // MACHINE_GUN
        0,  // SNIPER_RIFLE
        0   // NONE
    };

    /** Tracks the current number of active projectiles for limit enforcement. */
    private int activeProjectileCount;

    /** Armor calculator for research-adjusted armor (may be null in tests). */
    private ArmorCalculator armorCalculator;

    /** Research system for querying completed research (may be null in tests). */
    private ResearchSystem researchSystem;

    public ProjectileSystem() {
        this.activeProjectileCount = 0;
    }

    /**
     * Construct a ProjectileSystem with armor and research support.
     * Used by CombatSystem to enable research-adjusted armor in splash damage.
     *
     * @param armorCalculator the armor calculator
     * @param researchSystem  the research system
     */
    public ProjectileSystem(ArmorCalculator armorCalculator, ResearchSystem researchSystem) {
        this();
        this.armorCalculator = armorCalculator;
        this.researchSystem = researchSystem;
    }

    /**
     * Set the armor calculator and research system for splash damage armor calculation.
     * Allows late binding when these systems are not available at construction time.
     *
     * @param armorCalculator the armor calculator
     * @param researchSystem  the research system
     */
    public void setResearchSystem(ArmorCalculator armorCalculator, ResearchSystem researchSystem) {
        this.armorCalculator = armorCalculator;
        this.researchSystem = researchSystem;
    }

    /**
     * Process all projectiles for one tick.
     * Move projectiles toward targets, check for impact.
     * <p>
     * REF: combat_formulas.md "Projectile Movement (per tick)"
     *
     * @param entities the entity manager containing all game entities
     * @param state    the current game state for event emission
     */
    public void processTick(EntityManager entities, GameState state) {
        List<Projectile> projectiles = entities.getAllProjectiles();
        List<Integer> impactedIds = new ArrayList<>();

        for (Projectile proj : projectiles) {
            proj.advance();

            if (proj.hasReachedTarget()) {
                handleImpact(proj, entities, state);
                impactedIds.add(proj.getId());
            }
        }

        // Remove impacted projectiles
        for (int id : impactedIds) {
            entities.removeProjectile(id);
            activeProjectileCount = Math.max(0, activeProjectileCount - 1);
        }
    }

    /**
     * Spawn a projectile from attacker to target.
     * Calculates flight time based on distance and projectile speed.
     * <p>
     * REF: combat_formulas.md "Projectile Spawn" - flight time = distanceTable / speedTable
     * REF: combat_formulas.md - velocity calculation: velX = startOffX / (flightTime + 1)
     *
     * @param attacker    the unit firing the projectile
     * @param target      the target entity (unit or building)
     * @param weaponType  the type of weapon firing
     * @param damage      the damage this projectile will deal on impact
     * @param splash      whether this projectile deals splash damage
     * @param splashRadius splash radius in tiles (0 if no splash)
     * @param entities    the entity manager for storing the new projectile
     * @return the spawned projectile, or null if the limit has been reached
     */
    public Projectile spawnProjectile(Unit attacker, Entity target, WeaponType weaponType,
                                      int damage, boolean splash, int splashRadius,
                                      EntityManager entities) {
        if (activeProjectileCount >= MAX_PROJECTILES) {
            log.debug("Projectile limit reached ({}), cannot spawn new projectile", MAX_PROJECTILES);
            return null;
        }

        GridPosition startPos = attacker.getPosition();
        GridPosition targetPos = target.getPosition();
        Integer targetUnitRef = null;

        // Determine target reference and position
        if (target instanceof Unit targetUnit) {
            targetUnitRef = targetUnit.getId();
            targetPos = targetUnit.getPosition();
        } else if (target instanceof Building targetBuilding) {
            // Buildings use negative reference IDs in the original game
            targetUnitRef = -targetBuilding.getId();
        }

        // Calculate flight time based on distance and projectile speed
        // FIX(M-26): Use Chebyshev distance (distanceClass) for consistency with
        // all other gameplay distance calculations (range checks, splash, etc.).
        // UNVERIFIED (M-2): RE uses distanceTable[abs(dy)*21 + abs(dx)] / speedTable[projectileType]
        // — a lookup table with a different indexing scheme. Our approximation uses
        // Chebyshev distance / speed which may produce slightly different flight times.
        // Extract exact distanceTable from RE binary for parity if needed.
        int dx = Math.abs(targetPos.x() - startPos.x());
        int dy = Math.abs(targetPos.y() - startPos.y());
        int chebyshevDist = Math.max(dx, dy);
        int speedIndex = weaponType.ordinal();
        int speed = SPEED_TABLE[Math.min(speedIndex, SPEED_TABLE.length - 1)];
        int flightTime = Math.max(1, (int) Math.ceil((double) chebyshevDist / speed));

        // REF: combat_formulas.md — artillery uses fixed flight time, not distance-based
        // flightTime = (at[6][59] - at[5][59]) + 1
        if (weaponType == WeaponType.ARTILLERY) {
            flightTime = ARTILLERY_FIXED_FLIGHT_TIME;
        }

        int projectileId = entities.allocateEntityId();
        Projectile projectile = new Projectile(
            projectileId,
            attacker.getFaction(),
            startPos,
            weaponType,
            damage,
            attacker.getId(),
            targetUnitRef,
            targetPos,
            splash,
            splashRadius,
            flightTime
        );

        entities.addProjectile(projectile);
        activeProjectileCount++;
        log.debug("Spawned projectile {} from unit {} to target {} (flightTime={}, weapon={})",
            projectileId, attacker.getId(), target.getId(), flightTime, weaponType);
        return projectile;
    }

    /**
     * Overloaded spawn method that uses default splash radius for the weapon type.
     *
     * @param attacker    the unit firing the projectile
     * @param target      the target entity
     * @param weaponType  the weapon type
     * @param damage      the damage on impact
     * @param entities    the entity manager
     * @return the spawned projectile, or null if the limit has been reached
     */
    public Projectile spawnProjectile(Unit attacker, Entity target, WeaponType weaponType,
                                      int damage, EntityManager entities) {
        int weaponIndex = weaponType.ordinal();
        // FIX(M-3): Added FLAME to splash weapon types — splashRadius=1 was defined but unreachable
        boolean splash = weaponType == WeaponType.ROCKET || weaponType == WeaponType.ARTILLERY || weaponType == WeaponType.FLAME;
        int splashRadius = splash ? SPLASH_RADIUS[Math.min(weaponIndex, SPLASH_RADIUS.length - 1)] : 0;
        return spawnProjectile(attacker, target, weaponType, damage, splash, splashRadius, entities);
    }

    /**
     * Handle projectile impact.
     * Applies damage to target. Splash projectiles affect area.
     * <p>
     * REF: combat_formulas.md "Splash Damage (Artillery)" - area damage around impact
     * REF: combat_formulas.md "Damage Calculation for Projectiles" - armor reduction
     *
     * @param projectile the projectile that has impacted
     * @param entities   the entity manager for finding targets
     * @param state      the game state for event emission
     */
    public void handleImpact(Projectile projectile, EntityManager entities, GameState state) {
        if (projectile.isSplash()) {
            applySplashDamage(projectile, entities, state);
        } else {
            applyDirectDamage(projectile, entities, state);
        }
    }

    /**
     * Apply splash damage to all enemy units within splash radius of impact.
     * <p>
     * REF: combat_formulas.md "Splash Damage (Artillery)" section
     * REF: combat_formulas.md "Nuclear/Explosion Damage" - distanceFactor falloff
     *
     * @param projectile the splash projectile
     * @param entities   entity manager for finding nearby units
     * @param state      game state for events
     */
    private void applySplashDamage(Projectile projectile, EntityManager entities, GameState state) {
        GridPosition impactPos = projectile.getTargetPosition();
        int splashRadius = projectile.getSplashRadius();

        // Splash targets use their OWN research for armor (armor is a defensive stat)
        // The attacking faction's research does not affect target armor.

        // FIX(C-3): Look up the source unit to apply target type multiplier for splash damage.
        Unit sourceUnit = entities.getUnit(projectile.getSourceUnitId());

        // Splash damage to units
        for (Unit unit : entities.getAllUnits()) {
            if (!unit.isAlive()) continue;

            // FIX(M-27): Use Chebyshev distance for splash hit detection, consistent
            // with all other gameplay distance calculations.
            int sdx = Math.abs(unit.getPosition().x() - impactPos.x());
            int sdy = Math.abs(unit.getPosition().y() - impactPos.y());
            int distToImpact = Math.max(sdx, sdy);
            if (distToImpact <= splashRadius) {
                int distance = distToImpact;
                // Use research-adjusted armor for splash targets
                int effectiveArmor = getEffectiveArmor(unit);
                // FIX(C-3): Apply infantry vs machinery/building multiplier for splash targets.
                double targetMultiplier = DamageCalculator.getTargetMultiplier(
                    sourceUnit, false, unit.isMachinery());
                int damage = (int)(DamageCalculator.calculateSplashDamage(
                    projectile.getDamage(), effectiveArmor, distance) * targetMultiplier);
                unit.takeDamage(damage);

                state.enqueueEvent(new com.aow2.common.event.DamageAppliedEvent(
                    state.currentTick(), unit.getId(), damage, unit.getHp(),
                    projectile.getSourceUnitId()));

                if (!unit.isAlive()) {
                    // FIX (L6/C-8): Store death anim frame for client rendering
                    unit.setDeathAnimFrame(
                        DamageCalculator.calculateDeathAnimationFrame(unit,
                            DamageCalculator.getAttackerCategory(projectile.getWeaponType())));
                    state.enqueueEvent(new com.aow2.common.event.UnitKilledEvent(
                        state.currentTick(), unit.getId(), unit.getUnitType(),
                        projectile.getSourceUnitId()));
                }
            }
        }

        // Splash damage to buildings
        for (Building building : entities.getAllBuildings()) {
            if (!building.isAlive()) continue;

            // FIX(M-27): Use Chebyshev distance for splash hit detection.
            int bdx = Math.abs(building.getPosition().x() - impactPos.x());
            int bdy = Math.abs(building.getPosition().y() - impactPos.y());
            int bDistToImpact = Math.max(bdx, bdy);
            if (bDistToImpact <= splashRadius) {
                int distance = bDistToImpact;
                // Use research-adjusted building armor for splash targets
                int effectiveBuildingArmor = getEffectiveBuildingArmor(building);
                // FIX(C-3): Apply infantry vs building multiplier for splash building targets.
                double buildingTargetMultiplier = DamageCalculator.getTargetMultiplier(
                    sourceUnit, true, false);
                int damage = (int)(DamageCalculator.calculateSplashDamage(
                    projectile.getDamage(), effectiveBuildingArmor, distance) * buildingTargetMultiplier);
                building.takeDamage(damage);

                state.enqueueEvent(new com.aow2.common.event.DamageAppliedEvent(
                    state.currentTick(), building.getId(), damage, building.getHp(),
                    projectile.getSourceUnitId()));

                if (!building.isAlive()) {
                    state.enqueueEvent(new com.aow2.common.event.BuildingDestroyedEvent(
                        state.currentTick(), building.getId(), building.getBuildingType(),
                        projectile.getSourceUnitId()));
                }
            }
        }

        log.debug("Splash impact at {} with radius {} from projectile {}",
            impactPos, splashRadius, projectile.getId());
    }

    /**
     * Apply direct damage to a single target.
     * <p>
     * REF: combat_formulas.md "Damage Calculation for Projectiles"
     *
     * @param projectile the direct-fire projectile
     * @param entities   entity manager for finding the target
     * @param state      game state for events
     */
    private void applyDirectDamage(Projectile projectile, EntityManager entities, GameState state) {
        Integer targetRef = projectile.getTargetUnitRef();
        if (targetRef == null) {
            return;
        }

        // FIX(C-3): Look up the source unit to apply target type multiplier.
        // Ranged projectiles (ROCKET/ARTILLERY) previously skipped getTargetMultiplier(),
        // so infantry dealt full damage to machinery/buildings via ranged attacks.
        Unit sourceUnit = entities.getUnit(projectile.getSourceUnitId());

        if (targetRef > 0) {
            // Target is a unit
            Unit target = entities.getUnit(targetRef);
            if (target != null && target.isAlive()) {
                // Use research-adjusted armor for direct-fire unit targets (same as splash)
                int effectiveArmor = getEffectiveArmor(target);
                double targetMultiplier = DamageCalculator.getTargetMultiplier(
                    sourceUnit, false, target.isMachinery());
                int damage = (int)(DamageCalculator.calculateDamage(
                    projectile.getDamage(), effectiveArmor) * targetMultiplier);
                target.takeDamage(damage);

                state.enqueueEvent(new com.aow2.common.event.DamageAppliedEvent(
                    state.currentTick(), target.getId(), damage, target.getHp(),
                    projectile.getSourceUnitId()));

                if (!target.isAlive()) {
                    // FIX (L6/C-8): Store death anim frame for client rendering
                    target.setDeathAnimFrame(
                        DamageCalculator.calculateDeathAnimationFrame(target,
                            DamageCalculator.getAttackerCategory(projectile.getWeaponType())));
                    state.enqueueEvent(new com.aow2.common.event.UnitKilledEvent(
                        state.currentTick(), target.getId(), target.getUnitType(),
                        projectile.getSourceUnitId()));
                }
            }
        } else {
            // Target is a building (negative ref)
            int buildingId = -targetRef;
            Building target = entities.getBuilding(buildingId);
            if (target != null && target.isAlive()) {
                double targetMultiplier = DamageCalculator.getTargetMultiplier(
                    sourceUnit, true, false);
                int damage = (int)(DamageCalculator.calculateDamage(
                    projectile.getDamage(), 0) * targetMultiplier);
                target.takeDamage(damage);

                state.enqueueEvent(new com.aow2.common.event.DamageAppliedEvent(
                    state.currentTick(), target.getId(), damage, target.getHp(),
                    projectile.getSourceUnitId()));

                if (!target.isAlive()) {
                    state.enqueueEvent(new com.aow2.common.event.BuildingDestroyedEvent(
                        state.currentTick(), target.getId(), target.getBuildingType(),
                        projectile.getSourceUnitId()));
                }
            }
        }
    }

    /**
     * Get effective armor for a unit, using research-adjusted values when available.
     * Falls back to base armor if no research system is configured.
     */
    private int getEffectiveArmor(Unit unit) {
        if (armorCalculator != null && researchSystem != null) {
            int playerId = EconomySystem.playerId(unit.getFaction());
            Set<Integer> completedResearch = researchSystem.getCompletedResearch(playerId);
            return armorCalculator.calculateEffectiveArmor(unit, completedResearch);
        }
        return unit.getStats().armor();
    }

    /**
     * Get effective building armor, using research-adjusted values when available.
     * Falls back to 0 (base building armor) if no research system is configured.
     */
    private int getEffectiveBuildingArmor(Building building) {
        if (armorCalculator != null && researchSystem != null) {
            int playerId = EconomySystem.playerId(building.getFaction());
            Set<Integer> completedResearch = researchSystem.getCompletedResearch(playerId);
            return armorCalculator.calculateEffectiveBuildingArmor(building, completedResearch);
        }
        return 0;
    }

    /**
     * Get the current number of active projectiles.
     *
     * @return active projectile count
     */
    public int getActiveProjectileCount() {
        return activeProjectileCount;
    }

    /**
     * Get the splash radius for a given weapon type.
     *
     * @param weaponType the weapon type
     * @return splash radius in tiles (0 if no splash)
     */
    public static int getSplashRadiusForWeapon(WeaponType weaponType) {
        int index = weaponType.ordinal();
        if (index < SPLASH_RADIUS.length) {
            return SPLASH_RADIUS[index];
        }
        return 0;
    }
}
