package com.aow2.core.combat;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import com.aow2.core.entity.Projectile;
import com.aow2.core.entity.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProjectileSystem}.
 * Naming convention: shouldXxxWhenYyy
 * Structure: Given-When-Then
 */
class ProjectileSystemTest {

    private ProjectileSystem projectileSystem;
    private EntityManager entityManager;
    private GameState gameState;

    private static final UnitStats INFANTRY_STATS = new UnitStats(
        UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5,
        0, 4, 4, 4, 10, 650, 6, 255, 0, -1);

    private static final UnitStats ZEUS_STATS = new UnitStats(
        UnitType.CONFED_ZEUS, "T-22 Zeus", 70, 6, 3, 7, 5,
        0, 2, 6, 14, 30, 300, 8, 255, 0, -1);

    private static final UnitStats TORRENT_STATS = new UnitStats(
        UnitType.CONFED_TORRENT, "MLRS Torrent", 80, 15, 8, 4, 7,
        2, 6, 6, 7, 50, 250, 8, 255, 2, -1);

    private static final UnitStats REBEL_INFANTRY_STATS = new UnitStats(
        UnitType.REBEL_INFANTRY, "Infantry", 40, 2, 1, 5, 5,
        0, 4, 4, 4, 10, 650, 6, 255, 0, -1);

    private static final BuildingStats BUILDING_STATS = new BuildingStats(
        BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
        4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, List.of(300, 200, 200));

    @BeforeEach
    void setUp() {
        projectileSystem = new ProjectileSystem();
        entityManager = new EntityManager();
        gameState = new GameState();
    }

    @Test
    @DisplayName("Should spawn projectile from attacker to target unit")
    void shouldSpawnProjectileFromAttacker() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(15, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        // When
        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, false, 0, entityManager);

        // Then
        assertNotNull(proj, "Projectile should be spawned");
        assertEquals(WeaponType.BULLET, proj.getWeaponType());
        assertEquals(6, proj.getDamage());
        assertEquals(1, proj.getSourceUnitId());
        assertEquals(2, proj.getTargetUnitRef());
        assertTrue(proj.getTravelTicks() > 0, "Travel ticks should be positive");
        assertEquals(1, projectileSystem.getActiveProjectileCount());
    }

    @Test
    @DisplayName("Should move projectile per tick by decrementing travel time")
    void shouldMoveProjectilePerTick() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(15, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, false, 0, entityManager);
        int initialTicks = proj.getTravelTicks();

        // When
        projectileSystem.processTick(entityManager, gameState);

        // Then
        // After one tick, either the projectile impacted or its travel time decreased
        if (initialTicks > 1) {
            // Projectile still in flight
            assertEquals(initialTicks - 1, proj.getTravelTicks(),
                "Travel ticks should decrease by 1 per tick");
        }
        // If initialTicks == 1, the projectile would have impacted and been removed
    }

    @Test
    @DisplayName("Should handle impact on target unit")
    void shouldHandleImpactOnTarget() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, false, 0, entityManager);

        // When - advance until impact
        while (!proj.hasReachedTarget()) {
            proj.advance();
        }
        projectileSystem.handleImpact(proj, entityManager, gameState);

        // Then
        assertTrue(target.getHp() < target.getMaxHp(),
            "Target should have taken damage");
        var events = gameState.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof com.aow2.common.event.DamageAppliedEvent),
            "DamageAppliedEvent should be generated");
    }

    @Test
    @DisplayName("Should apply splash damage to nearby enemies")
    void shouldApplySplashDamageToNearbyEnemies() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_TORRENT, TORRENT_STATS);
        Unit target1 = new Unit(2, Faction.RESISTANCE, new GridPosition(15, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        Unit target2 = new Unit(3, Faction.RESISTANCE, new GridPosition(16, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target1);
        entityManager.addUnit(target2);

        // When - spawn artillery projectile with splash
        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target1, WeaponType.ARTILLERY, 15, true, 3, entityManager);

        // Advance until impact
        while (!proj.hasReachedTarget()) {
            proj.advance();
        }
        projectileSystem.handleImpact(proj, entityManager, gameState);

        // Then - both nearby enemies should take damage
        assertTrue(target1.getHp() < target1.getMaxHp(),
            "Primary target should take damage");
        assertTrue(target2.getHp() < target2.getMaxHp(),
            "Nearby unit should take splash damage");
    }

    @Test
    @DisplayName("Should not exceed max projectile limit of 400")
    void shouldNotExceedMaxProjectileLimit() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(50, 50),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        // When - spawn max + 1 projectiles
        int spawned = 0;
        Projectile lastProj = null;
        for (int i = 0; i < ProjectileSystem.MAX_PROJECTILES + 1; i++) {
            lastProj = projectileSystem.spawnProjectile(
                attacker, target, WeaponType.BULLET, 6, false, 0, entityManager);
            if (lastProj != null) {
                spawned++;
            }
        }

        // Then
        assertEquals(ProjectileSystem.MAX_PROJECTILES, spawned,
            "Should not spawn more than MAX_PROJECTILES");
        assertEquals(ProjectileSystem.MAX_PROJECTILES, projectileSystem.getActiveProjectileCount());

        // The last spawn attempt at limit + 1 should return null
        assertNull(projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, false, 0, entityManager),
            "Spawn beyond limit should return null");
    }

    @Test
    @DisplayName("Should remove projectile on impact after processing")
    void shouldRemoveProjectileOnImpact() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, false, 0, entityManager);

        // When - advance until impact, then process tick to remove
        while (!proj.hasReachedTarget()) {
            proj.advance();
        }
        int countBefore = entityManager.getAllProjectiles().size();
        projectileSystem.processTick(entityManager, gameState);

        // Then
        assertTrue(countBefore > 0, "Should have projectiles before processing");
        // After processTick, impacted projectiles are removed
        assertEquals(0, entityManager.getAllProjectiles().size(),
            "All impacted projectiles should be removed");
        assertEquals(0, projectileSystem.getActiveProjectileCount(),
            "Active count should be 0 after all removed");
    }

    @Test
    @DisplayName("Should handle artillery splash radius correctly")
    void shouldHandleArtillerySplashRadius() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_TORRENT, TORRENT_STATS);
        Unit nearTarget = new Unit(2, Faction.RESISTANCE, new GridPosition(15, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        Unit farTarget = new Unit(3, Faction.RESISTANCE, new GridPosition(30, 30),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(nearTarget);
        entityManager.addUnit(farTarget);

        // When - spawn artillery with splash radius 3
        Projectile proj = projectileSystem.spawnProjectile(
            attacker, nearTarget, WeaponType.ARTILLERY, 15, true, 3, entityManager);

        // Advance until impact
        while (!proj.hasReachedTarget()) {
            proj.advance();
        }
        projectileSystem.handleImpact(proj, entityManager, gameState);

        // Then - near target takes damage, far target does not
        assertTrue(nearTarget.getHp() < nearTarget.getMaxHp(),
            "Near target should take splash damage");
        assertEquals(farTarget.getMaxHp(), farTarget.getHp(),
            "Far target should not take splash damage");
    }

    @Test
    @DisplayName("Should spawn projectile targeting a building")
    void shouldSpawnProjectileTargetingBuilding() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Building target = new Building(2, Faction.RESISTANCE, new GridPosition(15, 10),
            BuildingType.REBEL_HEADQUARTERS, BUILDING_STATS);
        entityManager.addUnit(attacker);
        entityManager.addBuilding(target);

        // When
        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.ROCKET, 6, false, 0, entityManager);

        // Then
        assertNotNull(proj);
        assertEquals(-2, proj.getTargetUnitRef(),
            "Building target ref should be negative building ID");
    }

    @Test
    @DisplayName("Should apply damage to building on projectile impact")
    void shouldApplyDamageToBuildingOnImpact() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Building target = new Building(2, Faction.RESISTANCE, new GridPosition(11, 10),
            BuildingType.REBEL_HEADQUARTERS, BUILDING_STATS);
        entityManager.addUnit(attacker);
        entityManager.addBuilding(target);

        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, false, 0, entityManager);

        // When
        while (!proj.hasReachedTarget()) {
            proj.advance();
        }
        projectileSystem.handleImpact(proj, entityManager, gameState);

        // Then
        assertTrue(target.getHp() < target.getMaxHp(),
            "Building should take damage from projectile");
    }

    @Test
    @DisplayName("Should use default splash radius for rocket weapon type")
    void shouldUseDefaultSplashRadiusForRocket() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(15, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        // When - spawn with auto-detect splash
        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.ROCKET, 10, entityManager);

        // Then
        assertNotNull(proj);
        assertTrue(proj.isSplash(), "Rocket should be splash by default");
        assertTrue(proj.getSplashRadius() > 0, "Rocket should have splash radius");
    }

    @Test
    @DisplayName("Should use default no-splash for bullet weapon type")
    void shouldUseDefaultNoSplashForBullet() {
        // Given
        Unit attacker = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_ZEUS, ZEUS_STATS);
        Unit target = new Unit(2, Faction.RESISTANCE, new GridPosition(15, 10),
            UnitType.REBEL_INFANTRY, REBEL_INFANTRY_STATS);
        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        // When
        Projectile proj = projectileSystem.spawnProjectile(
            attacker, target, WeaponType.BULLET, 6, entityManager);

        // Then
        assertNotNull(proj);
        assertFalse(proj.isSplash(), "Bullet should not be splash");
        assertEquals(0, proj.getSplashRadius());
    }
}
