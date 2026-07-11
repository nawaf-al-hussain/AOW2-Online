package com.aow2.core.combat;

import com.aow2.common.event.BuildingDestroyedEvent;
import com.aow2.common.event.DamageAppliedEvent;
import com.aow2.common.event.UnitKilledEvent;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
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
     * <p>
     * VERIFIED (M3-extended from CRITICAL_ANALYSIS_REPORT.md): The RE binary's
     * decrypted_data.json a_file_data.byte_sections.siege_damage_bonus array is
     * [12, 8, 4, 8, 6, 3, 4, 3, 0] (9 entries, indexed by unit category).
     * The siege_mode_stats array is [12, 4, 4, 3] (4 entries).
     * The existing constant SIEGE_DAMAGE_BONUS = 15 matches Research ID 36's effect
     * (unit type 10 = Mine Lizard siege upgrade = 15 attack power bonus) and is kept
     * as the canonical siege damage bonus for the generic siege-mode path.
     */
    private static final int SIEGE_DAMAGE_BONUS = 15;

    /**
     * Siege mode range bonus.
     * REF: combat_formulas.md - siege mode increases attack range for deployed units.
     * <p>
     * VERIFIED (M3-extended from CRITICAL_ANALYSIS_REPORT.md): The RE binary's
     * siege_range_bonus array is [12, 6, 6, 4] (4 entries, indexed by siege class).
     * The existing constant SIEGE_RANGE_BONUS = 3 is kept as a conservative default
     * for the generic siege-mode path; per-unit siege range bonuses should ideally
     * come from the siege_range_bonus array indexed by the unit's siege class.
     * ASSUMPTION: +3 range bonus in siege mode — within the RE range of 4-12.
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
    // UNVERIFIED (M-14): Deploy time of 5 ticks is assumed — RE confirms siege mode exists but not the exact deploy duration.
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
     * Order: unit targeting → unit attacks → building attacks → siege mode → projectiles.
     */
    public void processTick() {
        processUnitTargetAcquisition();
        processUnitAttacks();
        processBuildingAttacks();
        processSiegeDeployments();
        projectileSystem.processTick(entityManager, gameState);
    }

    /**
     * Auto-acquire targets for units that have a target reference but haven't
     * transitioned to the attacking state yet (attackState 0 or 1).
     * Also handles idle units with no target — they scan for nearby enemies.
     * <p>
     * Attack state machine:
     *   0 = idle (no target)
     *   1 = targeting/acquiring (has target ref, moving toward or waiting to fire)
     *   2 = firing/wind-up (reserved for future ranged wind-up animation)
     *   3 = attacking (can fire, cooldown permitting)
     * <p>
     * REF: combat_formulas.md - attack state transitions
     */
    private void processUnitTargetAcquisition() {
        List<Unit> allUnits = entityManager.getAllUnits();
        for (Unit unit : allUnits) {
            if (!unit.isAlive()) continue;
            if (unit.isMine() || unit.isGarrisoned()) continue;
            int state = unit.getAttackState();

            if (state == 0) {
                // Idle — scan for nearby enemies to auto-acquire
                Unit nearestEnemy = findNearestEnemyUnit(
                    unit.getPosition(), unit.getFaction(), unit.getStats().sightRange());
                if (nearestEnemy != null) {
                    unit.setTargetUnitRef(nearestEnemy.getId());
                    unit.setAttackState(1); // transition to targeting
                }
            } else if (state == 1) {
                // Targeting — check if we can transition to attacking
                Integer targetRef = unit.getTargetUnitRef();
                if (targetRef == null) {
                    unit.setAttackState(0); // lost target, go idle
                    continue;
                }
                if (targetRef > 0) {
                    Unit target = entityManager.getUnit(targetRef);
                    if (target == null || !target.isAlive()) {
                        unit.setTargetUnitRef(null);
                        unit.setAttackState(0);
                        continue;
                    }
                    if (isInAttackRange(unit, target)) {
                        unit.setAttackState(3); // in range, ready to attack
                        // Stop moving when entering attack state
                        if (unit.getMovementState() != MovementState.IDLE) {
                            unit.clearPath();
                        }
                    }
                    // else: stay in state 1, keep moving toward target (handled by MovementSystem)
                } else if (targetRef < 0) {
                    Building target = entityManager.getBuilding(-targetRef);
                    if (target == null || !target.isAlive()) {
                        unit.setTargetUnitRef(null);
                        unit.setAttackState(0);
                        continue;
                    }
                    if (isInAttackRange(unit, target)) {
                        unit.setAttackState(3);
                        if (unit.getMovementState() != MovementState.IDLE) {
                            unit.clearPath();
                        }
                    }
                }
            }
            // States 2 and 3 are handled by processUnitAttacks()
        }
    }

    /**
     * Process unit attacks for all alive units in attack state.
     * Units in attack state 3 with a valid target will perform attacks.
     * After firing, ranged units transition to state 2 (cooldown/waiting for projectile),
     * and melee units transition to state 3 (can attack again next cooldown cycle).
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
                        // FIX (ANALYSIS_V2 P3): Removed setAttackState(3) override.
                        // performAttack() already manages the state machine:
                        //   - Melee: sets state 3 (ATTACKING) and fires immediately
                        //   - Ranged: sets state 2 (WIND_UP) and returns; fires on a
                        //     subsequent tick when wind-up completes
                        // The previous setAttackState(3) here overwrote the WIND_UP state
                        // every tick, causing ranged units to skip wind-up and fire 2x faster.
                        performAttack(attacker, target);
                    } else {
                        // Target out of range or dead — re-acquire
                        attacker.setAttackState(1);
                    }
                } else {
                    attacker.setTargetUnitRef(null);
                    attacker.setAttackState(0);
                }
            } else if (targetRef != null && targetRef < 0) {
                int buildingId = -targetRef;
                Building target = entityManager.getBuilding(buildingId);
                if (target != null) {
                    if (target.isAlive() && isInAttackRange(attacker, target)) {
                        performAttackOnBuilding(attacker, target);
                        attacker.setAttackState(3);
                    } else {
                        attacker.setAttackState(1);
                    }
                } else {
                    attacker.setTargetUnitRef(null);
                    attacker.setAttackState(0);
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
            boolean fired = false;

            if (isBunker(type)) {
                fired = processBunkerAttack(building);
            } else if (isRocketOrTower(type)) {
                fired = processDefensiveBuildingAttack(building);
            }

            // FIX (ANALYSIS_V2 2.5): Only set cooldown if the building actually fired.
            // Previously cooldown was set unconditionally, so buildings that scanned
            // for enemies but found none went on a 5-tick cooldown before re-scanning.
            if (fired) {
                building.setAttackCooldown(BUILDING_ATTACK_COOLDOWN);
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
    private boolean processBunkerAttack(Building bunker) {
        Integer garrisonedRef = bunker.getGarrisonedUnitRef();
        if (garrisonedRef == null) return false;

        Unit garrison = entityManager.getUnit(garrisonedRef);
        if (garrison == null || !garrison.isAlive()) return false;

        // Find nearest enemy within garrison range
        int effectiveRange = garrison.getStats().attackRange() + BUNKER_RANGE_BONUS;
        Unit nearestEnemy = findNearestEnemyUnit(bunker.getPosition(), bunker.getFaction(), effectiveRange);

        if (nearestEnemy != null) {
            // Garrison attacks using its weapon damage
            int weaponDamage = garrison.getStats().damage();
            // FIX(H-2): Use research-adjusted armor instead of raw base armor.
            // Previously bunker attacks ignored target's armor research entirely.
            // FIX (B-6 from FULL_ANALYSIS.md): Use the data-driven ResearchBonusTracker
            // path so armor effects beyond the hardcoded IDs 0/9/24/33 are also applied.
            int targetArmor = calculateEffectiveUnitArmor(nearestEnemy);
            int damage = DamageCalculator.calculateDamage(weaponDamage, targetArmor);

            nearestEnemy.takeDamage(damage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), nearestEnemy.getId(), damage,
                nearestEnemy.getHp(), bunker.getId()));

            if (!nearestEnemy.isAlive()) {
                // FIX (L6/C-8): Store death anim frame on unit for client rendering
                nearestEnemy.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(nearestEnemy,
                        DamageCalculator.getAttackerCategory(garrison.getStats().weaponType())));
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), nearestEnemy.getId(),
                    nearestEnemy.getUnitType(), bunker.getId()));
                ModEventBridge.fireUnitKilled(nearestEnemy.getId(), nearestEnemy.getUnitType(),
                    nearestEnemy.getFaction(), EconomySystem.playerId(bunker.getFaction()));
            }
            garrison.addExperience(1);
            return true;  // FIX (2.5): fired
        }
        return false;
    }

    /**
     * Process defensive building attacks (Rocket Launcher, Tower).
     * These buildings auto-target the nearest enemy and fire projectiles.
     * <p>
     * REF: combat_formulas.md - defensive building auto-targeting
     *
     * @param building the defensive building
     */
    private boolean processDefensiveBuildingAttack(Building building) {
        BuildingType type = building.getBuildingType();
        int attackRange = building.getStats().attackRange();
        int damage = building.getStats().attackBonus();

        // Find nearest enemy unit within range
        Unit nearestEnemy = findNearestEnemyUnit(building.getPosition(), building.getFaction(), attackRange);

        if (nearestEnemy != null) {
            // Use research-adjusted armor for the target, not raw base armor
            // FIX (B-6 from FULL_ANALYSIS.md): Use the data-driven ResearchBonusTracker
            // path so armor effects beyond the hardcoded IDs 0/9/24/33 are also applied.
            // Also implicitly fixes a latent bug: the previous call used
            // getCompletedResearchForBuilding(building) (the attacker's research), but
            // armor is a defensive stat of the TARGET — calculateEffectiveUnitArmor
            // correctly uses the target unit's own faction research.
            int targetArmor = calculateEffectiveUnitArmor(nearestEnemy);
            int effectiveDamage = DamageCalculator.calculateDamage(damage, targetArmor);

            nearestEnemy.takeDamage(effectiveDamage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), nearestEnemy.getId(), effectiveDamage,
                nearestEnemy.getHp(), building.getId()));

            if (!nearestEnemy.isAlive()) {
                // FIX (L6/C-8): Store death anim frame on unit for client rendering
                nearestEnemy.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(nearestEnemy,
                        DamageCalculator.getAttackerCategory(building.getStats().weaponType())));
                gameState.enqueueEvent(new UnitKilledEvent(
                    gameState.currentTick(), nearestEnemy.getId(),
                    nearestEnemy.getUnitType(), building.getId()));
                ModEventBridge.fireUnitKilled(nearestEnemy.getId(), nearestEnemy.getUnitType(),
                    nearestEnemy.getFaction(), EconomySystem.playerId(building.getFaction()));
            }
            return true;  // FIX (2.5): fired
        }
        return false;
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
     * FIX(M-25): Implemented ranged attack state machine with three phases:
     * - State 0: IDLE — unit is not attacking
     * - State 1: MOVING — unit is moving (no attack)
     * - State 2: WIND_UP — ranged unit is preparing to fire (must stop moving)
     * - State 3: ATTACKING — unit fires/spawns projectile
     * Ranged units enter WIND_UP state when they acquire a target. After wind-up
     * completes, they transition to ATTACKING and fire. Melee units skip WIND_UP.
     * Artillery units (siege-capable) must be in siege mode or stop to fire.
     * <p>
     * UNVERIFIED (M-17): RE only documents states 0 (idle), 1 (moving), 3 (attacking).
     * State 2 (WIND_UP) and its duration (attackSpeed/2) are an assumption not found
     * in RE documentation. This effectively adds ~50% attack delay for all ranged units.
     * If RE binary analysis confirms no wind-up phase exists, remove state 2 and have
     * ranged units fire immediately on target acquisition (same as melee).
     * <p>
     * REF: combat_formulas.md - damage formula
     * REF: combat_formulas.md "Projectile Spawn" - ranged attacks use projectile system
     * REF: combat_formulas.md attack state table
     *
     * @param attacker the attacking unit
     * @param target   the target unit
     */
    public void performAttack(Unit attacker, Unit target) {
        WeaponType weaponType = attacker.getStats().weaponType();
        boolean isRanged = weaponType != WeaponType.BULLET && weaponType != WeaponType.NONE;

        // FIX(M-25): Ranged attack state machine.
        // State 2 (WIND_UP): Ranged unit is preparing to fire. Increment wind-up counter;
        // when wind-up completes, transition to ATTACKING (3) and fire.
        // Wind-up duration is half the weapon cooldown (rounded down, minimum 1 tick).
        if (isRanged && attacker.getAttackState() == 2) {
            int windUp = attacker.getWindUpCounter() + 1;
            int windUpDuration = Math.max(1, attacker.getStats().attackSpeed() / 2);
            if (windUp >= windUpDuration) {
                // Wind-up complete — fire and enter cooldown
                attacker.setAttackState(3); // ATTACKING
                attacker.setWindUpCounter(0);
                executeAttack(attacker, target, weaponType, isRanged);
            } else {
                attacker.setWindUpCounter(windUp);
            }
            return;
        }

        // Set attack state to ATTACKING (3) for melee, or WIND_UP (2) for ranged
        if (isRanged) {
            attacker.setAttackState(2); // WIND_UP
            attacker.setWindUpCounter(0);
            return; // Will fire on next tick when wind-up completes
        }

        // Melee/BULLET: fire immediately
        attacker.setAttackState(3); // ATTACKING
        executeAttack(attacker, target, weaponType, isRanged);
    }

    /**
     * Execute the actual attack (damage or projectile spawn).
     * Shared by performAttack after state machine transitions.
     */
    private void executeAttack(Unit attacker, Unit target, WeaponType weaponType, boolean isRanged) {
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

        // Ranged units (non-BULLET) use projectile system for damage delivery
        if (weaponType != WeaponType.BULLET && weaponType != WeaponType.NONE) {
            // FIX(M-3): Added FLAME to splash weapon types — splashRadius=1 was defined but unreachable
            boolean splash = weaponType == WeaponType.ROCKET || weaponType == WeaponType.ARTILLERY || weaponType == WeaponType.FLAME;
            int splashRadius = splash ? ProjectileSystem.getSplashRadiusForWeapon(weaponType) : 0;
            spawnProjectile(attacker, target, weaponType, weaponDamage, splash, splashRadius);
        } else {
            // Instant damage for BULLET/MELEE weapons
            // FIX (P1-H2): Use the TARGET's own faction research for armor calculation.
            // Armor is a defensive stat — the target's research determines their armor, not the attacker's.
            // FIX (B-6 from FULL_ANALYSIS.md): Use the data-driven ResearchBonusTracker
            // path so armor effects beyond the hardcoded IDs 0/9/24/33 are also applied.
            int targetArmor = calculateEffectiveUnitArmor(target);
            // FIX(C-2): Apply infantry vs machinery 0.7x target multiplier for unit-vs-unit combat.
            // Previously getTargetMultiplier() was dead code — never called in this path.
            double targetMultiplier = DamageCalculator.getTargetMultiplier(attacker, false, target.isMachinery());
            int damage = (int)(DamageCalculator.calculateDamage(weaponDamage, targetArmor) * targetMultiplier);

            target.takeDamage(damage);
            gameState.enqueueEvent(new DamageAppliedEvent(
                gameState.currentTick(), target.getId(), damage, target.getHp(), attacker.getId()));

            if (!target.isAlive()) {
                // FIX (L6/C-8): Store death anim frame on unit for client rendering
                target.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(target,
                        DamageCalculator.getAttackerCategory(weaponType)));

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
            // FIX(M-3): Added FLAME to splash weapon types — splashRadius=1 was defined but unreachable
            boolean splash = weaponType == WeaponType.ROCKET || weaponType == WeaponType.ARTILLERY || weaponType == WeaponType.FLAME;
            int splashRadius = splash ? ProjectileSystem.getSplashRadiusForWeapon(weaponType) : 0;
            spawnProjectile(attacker, target, weaponType, weaponDamage, splash, splashRadius);
        } else {
            // Instant damage for BULLET weapons
            // FIX(M-1): Use armorCalculator.calculateEffectiveBuildingArmor() for melee building
            // attacks, same as ranged projectile path. RE spec says buildings have 0 base armor;
            // armor comes only from research N[] array. Previously used DamageCalculator which
            // added building.getStats().armor() (e.g., Bunker=7), giving inconsistent behavior
            // between melee and ranged attacks on the same building.
            int targetArmor;
            if (researchSystem != null) {
                int playerId = EconomySystem.playerId(target.getFaction());
                targetArmor = armorCalculator.calculateEffectiveBuildingArmor(
                    target, researchSystem.getCompletedResearch(playerId));
            } else {
                targetArmor = 0; // No research system — use RE-correct 0 base
            }
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
    java.util.Set<Integer> getCompletedResearch(Unit unit) {
        if (researchSystem == null) return java.util.Set.of();
        return researchSystem.getCompletedResearch(EconomySystem.playerId(unit.getFaction()));
    }

    /**
     * FIX (B-6 from FULL_ANALYSIS.md): Get the ResearchBonusTracker for a unit's owner.
     * This is the data-driven path that accumulates ALL armor research effects from
     * tech_tree.json — not just the hardcoded IDs in ArmorCalculator's
     * INFANTRY_ARMOR_RESEARCH / VEHICLE_ARMOR_RESEARCH maps. Returns null if no
     * research system is available; callers should fall back to the legacy
     * Set&lt;Integer&gt; overload in that case.
     *
     * @param unit the unit
     * @return the bonus tracker for the unit's owner, or null if no research system
     */
    private com.aow2.core.research.ResearchSystem.ResearchBonusTracker getBonusTrackerForUnit(Unit unit) {
        if (researchSystem == null) return null;
        return researchSystem.getBonusTracker(EconomySystem.playerId(unit.getFaction()));
    }

    /**
     * FIX (B-6): Compute effective unit armor using the data-driven ResearchBonusTracker
     * path when a research system is wired, falling back to the legacy hardcoded-ID
     * path otherwise. This ensures modders who add new armor research effects via
     * tech_tree.json actually see them applied during combat.
     *
     * @param unit the target unit
     * @return effective armor value (base + research bonus)
     */
    private int calculateEffectiveUnitArmor(Unit unit) {
        com.aow2.core.research.ResearchSystem.ResearchBonusTracker tracker = getBonusTrackerForUnit(unit);
        if (tracker != null) {
            return armorCalculator.calculateEffectiveArmor(unit, tracker);
        }
        // Fallback: legacy hardcoded-ID path (only IDs 0, 9, 24, 33 for infantry; none for vehicles).
        return armorCalculator.calculateEffectiveArmor(unit, getCompletedResearch(unit));
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
        int nearestDistClass = Integer.MAX_VALUE;

        for (Unit unit : entityManager.getAllUnits()) {
            if (!unit.isAlive()) continue;
            if (unit.getFaction() == ownerFaction) continue;

            int dx = unit.getPosition().x() - position.x();
            int dy = unit.getPosition().y() - position.y();
            int dist = GridPosition.distanceClass(dx, dy);
            // REF: combat_formulas.md — range checks and nearest selection both use distanceClass (Chebyshev)
            if (dist <= range && dist < nearestDistClass) {
                nearest = unit;
                nearestDistClass = dist;
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
