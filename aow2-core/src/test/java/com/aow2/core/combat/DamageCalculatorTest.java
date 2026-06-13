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
    @DisplayName("Damage formula: weaponDamage*(10-armor)/10, clamped to min(damage, weaponDamage-armor)")
    void shouldCalculateDamageWithTwoStepClamp() {
        // REF: combat_formulas.md lines 46-48
        // damage = weaponDamage * (10 - targetArmour) / 10
        // damage = max(min(damage, weaponDamage - targetArmour), 1)
        // Example: 6*(10-5)/10 = 3, min(3, 6-5=1) = 1, max(1,1) = 1
        assertEquals(1, DamageCalculator.calculateDamage(6, 5));
    }

    @Test
    @DisplayName("Minimum damage is 1")
    void shouldClampMinimumDamageToOne() {
        // damage = 1*(10-9)/10 = 0, min(0, 1-9=-8) = -8, max(-8, 1) = 1
        assertEquals(1, DamageCalculator.calculateDamage(1, 9));
    }

    @Test
    @DisplayName("Damage clamped to weaponDamage - armor when raw damage exceeds it")
    void shouldClampDamageToUpperBound() {
        // damage = 10*(10-0)/10 = 10, min(10, 10-0=10) = 10
        assertEquals(10, DamageCalculator.calculateDamage(10, 0));
    }

    @Test
    @DisplayName("Upper clamp example: weaponDamage=5, armor=2")
    void shouldApplyUpperClampForLowWeaponDamage() {
        // REF: combat_formulas.md - the min() clamp prevents inflated damage for low weaponDamage
        // damage = 5*(10-2)/10 = 4, min(4, 5-2=3) = 3, max(3,1) = 3
        assertEquals(3, DamageCalculator.calculateDamage(5, 2));
    }

    @Test
    @DisplayName("Building armor is 0 per RE docs")
    void shouldCalculateBuildingArmorAsZero() {
        var buildingStats = new BuildingStats(BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
            4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, List.of(300, 200, 200));
        var building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, buildingStats);
        assertEquals(0, DamageCalculator.calculateEffectiveArmor(building));
    }

    @Test
    @DisplayName("Building armor with research bonus")
    void shouldCalculateBuildingArmorWithBonus() {
        var buildingStats = new BuildingStats(BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
            4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, List.of(300, 200, 200));
        var building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, buildingStats);
        // With bonus = 4 (Fortified Structures research)
        assertEquals(4, DamageCalculator.calculateEffectiveArmor(building, 4));
    }

    @Test
    @DisplayName("Unit armor with research bonus")
    void shouldCalculateUnitArmorWithBonus() {
        var unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, infantryStats);
        assertEquals(5, DamageCalculator.calculateEffectiveArmor(unit, 0));
        assertEquals(7, DamageCalculator.calculateEffectiveArmor(unit, 2)); // Energy Suit +2
    }

    @Test
    @DisplayName("Regular artillery splash has no distance falloff")
    void shouldCalculateSplashDamageWithoutFalloff() {
        // REF: combat_formulas.md lines 214-228 - regular artillery: same damage for all in blast
        int directDamage = DamageCalculator.calculateSplashDamage(15, 5, 0);
        assertTrue(directDamage >= 1);
        // Regular splash at distance 2 = same as direct (no falloff)
        int splashDamage = DamageCalculator.calculateSplashDamage(15, 5, 2);
        assertEquals(directDamage, splashDamage,
            "Regular artillery splash should deal same damage at all distances in blast radius");
    }

    @Test
    @DisplayName("Nuclear splash has distance falloff")
    void shouldCalculateNuclearSplashWithFalloff() {
        // REF: combat_formulas.md lines 236-256 - nuclear: 1/(1+distance) falloff
        int directDamage = DamageCalculator.calculateSplashDamage(15, 5, 0, true);
        assertTrue(directDamage >= 1);
        int splashDamage = DamageCalculator.calculateSplashDamage(15, 5, 2, true);
        assertTrue(splashDamage < directDamage,
            "Nuclear splash should decrease with distance");
        assertTrue(splashDamage >= 1);
    }

    @Test
    @DisplayName("Infantry death animation in range [10,26]")
    void shouldCalculateInfantryDeathAnimation() {
        var unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, infantryStats);
        int animFrame = DamageCalculator.calculateDeathAnimationFrame(unit, 0);
        assertTrue(animFrame >= 10 && animFrame <= 26,
            "Infantry death anim in [10,26], got: " + animFrame);
    }

    @Test
    @DisplayName("Machinery death animation is frame 2")
    void shouldUseFixedFrameForMachineryDeath() {
        var unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        assertEquals(2, DamageCalculator.calculateDeathAnimationFrame(unit, 0));
    }

    @Test
    @DisplayName("Infantry deals 50% damage to buildings")
    void shouldApplyReducedMultiplierInfantryVsBuilding() {
        var infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, infantryStats);
        assertEquals(0.5, DamageCalculator.getTargetMultiplier(infantry, true));
    }

    @Test
    @DisplayName("Siege-capable units deal 150% damage to buildings")
    void shouldApplySiegeBonusForSiegeCapable() {
        var torrent = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_TORRENT, torrentStats);
        assertEquals(1.5, DamageCalculator.getTargetMultiplier(torrent, true));
    }

    @Test
    @DisplayName("Infantry deals reduced damage to machinery")
    void shouldApplyReducedMultiplierInfantryVsMachinery() {
        var infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_INFANTRY, infantryStats);
        // REF: combat_formulas.md lines 456-459 - infantry vs machinery reduction
        assertEquals(0.7, DamageCalculator.getTargetMultiplier(infantry, false, true));
    }

    @Test
    @DisplayName("Unit vs unit is full damage")
    void shouldDealFullDamageUnitVsUnit() {
        var zeus = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        assertEquals(1.0, DamageCalculator.getTargetMultiplier(zeus, false));
    }

    @Test
    @DisplayName("Full combat with corrected formula: Zeus(dmg=6) vs Infantry(armor=5)")
    void shouldVerifyFullCombatExchange() {
        var zeus = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10), UnitType.CONFED_ZEUS, zeusStats);
        var infantry = new Unit(2, Faction.RESISTANCE, new GridPosition(11, 10), UnitType.CONFED_INFANTRY, infantryStats);
        // REF: combat_formulas.md - damage = 6*(10-5)/10 = 3, min(3, 6-5=1) = 1, max(1,1) = 1
        int damage = DamageCalculator.calculateDamage(zeus.getStats().damage(), infantry.getStats().armor());
        assertEquals(1, damage);
        infantry.takeDamage(damage);
        assertEquals(39, infantry.getHp());
        assertTrue(infantry.isAlive());
    }
}
