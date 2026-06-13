package com.aow2.core.combat;

import com.aow2.common.model.*;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DamageCalculatorTest {

    private UnitStats infantryStats;
    private UnitStats zeusStats;
    private UnitStats torrentStats;

    @BeforeEach
    void setUp() {
        infantryStats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5,
            0, 4, 4, 4, 10, 650, 6, 255, 0, -1);
        zeusStats = new UnitStats(UnitType.CONFED_ZEUS, "T-22 Zeus", 70, 6, 3, 7, 5,
            0, 2, 6, 14, 30, 300, 8, 255, 0, -1);
        torrentStats = new UnitStats(UnitType.CONFED_TORRENT, "MLRS Torrent", 80, 15, 8, 4, 7,
            2, 6, 6, 7, 50, 250, 8, 255, 2, -1);
    }

    @Test
    @DisplayName("Zeus vs Infantry: 6*(10-5)/10 = 3")
    void shouldCalculateDamageWithArmorReduction() {
        assertEquals(3, DamageCalculator.calculateDamage(6, 5));
    }

    @Test
    @DisplayName("Minimum damage is 1")
    void shouldClampMinimumDamageToOne() {
        assertEquals(1, DamageCalculator.calculateDamage(1, 9));
    }

    @Test
    @DisplayName("Damage clamped to weaponDamage - armor")
    void shouldClampDamageToUpperBound() {
        assertEquals(10, DamageCalculator.calculateDamage(10, 0));
    }

    @Test
    @DisplayName("Building armor is 0 per RE docs")
    void shouldCalculateBuildingArmorAsZero() {
        var buildingStats = new BuildingStats(BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
            4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, List.of(300, 200, 200));
        var building = new Building(1, BuildingType.CONFED_COMMAND_CENTRE, buildingStats,
            0, new GridPosition(32, 32));
        assertEquals(0, DamageCalculator.calculateEffectiveArmor(building));
    }

    @Test
    @DisplayName("Unit armor with research bonus")
    void shouldCalculateUnitArmorWithBonus() {
        var unit = new Unit(1, UnitType.CONFED_INFANTRY, infantryStats, 0, new GridPosition(10, 10));
        assertEquals(5, DamageCalculator.calculateEffectiveArmor(unit, 0));
        assertEquals(7, DamageCalculator.calculateEffectiveArmor(unit, 2)); // Energy Suit +2
    }

    @Test
    @DisplayName("Splash damage decreases with distance")
    void shouldCalculateSplashDamageWithFalloff() {
        int directDamage = DamageCalculator.calculateSplashDamage(15, 5, 0);
        assertTrue(directDamage >= 1);
        int splashDamage = DamageCalculator.calculateSplashDamage(15, 5, 2);
        assertTrue(splashDamage < directDamage);
        assertTrue(splashDamage >= 1);
    }

    @Test
    @DisplayName("Infantry death animation in range [10,26]")
    void shouldCalculateInfantryDeathAnimation() {
        var unit = new Unit(1, UnitType.CONFED_INFANTRY, infantryStats, 0, new GridPosition(10, 10));
        int animFrame = DamageCalculator.calculateDeathAnimationFrame(unit, 0);
        assertTrue(animFrame >= 10 && animFrame <= 26,
            "Infantry death anim in [10,26], got: " + animFrame);
    }

    @Test
    @DisplayName("Machinery death animation is frame 2")
    void shouldUseFixedFrameForMachineryDeath() {
        var unit = new Unit(1, UnitType.CONFED_ZEUS, zeusStats, 0, new GridPosition(10, 10));
        assertEquals(2, DamageCalculator.calculateDeathAnimationFrame(unit, 0));
    }

    @Test
    @DisplayName("Infantry deals 50% damage to buildings")
    void shouldApplyReducedMultiplierInfantryVsBuilding() {
        var infantry = new Unit(1, UnitType.CONFED_INFANTRY, infantryStats, 0, new GridPosition(10, 10));
        assertEquals(0.5, DamageCalculator.getTargetMultiplier(infantry, true));
    }

    @Test
    @DisplayName("Artillery deals 150% damage to buildings")
    void shouldApplySiegeBonusForTorrent() {
        var torrent = new Unit(1, UnitType.CONFED_TORRENT, torrentStats, 0, new GridPosition(10, 10));
        assertEquals(1.5, DamageCalculator.getTargetMultiplier(torrent, true));
    }

    @Test
    @DisplayName("Unit vs unit is full damage")
    void shouldDealFullDamageUnitVsUnit() {
        var zeus = new Unit(1, UnitType.CONFED_ZEUS, zeusStats, 0, new GridPosition(10, 10));
        assertEquals(1.0, DamageCalculator.getTargetMultiplier(zeus, false));
    }

    @Test
    @DisplayName("Full combat: Zeus(dmg=6) vs Infantry(armor=5) = 3 damage per hit")
    void shouldVerifyFullCombatExchange() {
        var zeus = new Unit(1, UnitType.CONFED_ZEUS, zeusStats, 0, new GridPosition(10, 10));
        var infantry = new Unit(2, UnitType.CONFED_INFANTRY, infantryStats, 1, new GridPosition(11, 10));
        int damage = DamageCalculator.calculateDamage(zeus.stats().damage(), infantry.stats().armor());
        assertEquals(3, damage);
        infantry.takeDamage(damage);
        assertEquals(37, infantry.hp());
        assertTrue(infantry.isAlive());
    }
}
