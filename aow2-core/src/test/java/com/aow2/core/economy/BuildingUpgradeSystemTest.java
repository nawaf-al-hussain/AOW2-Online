package com.aow2.core.economy;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingUpgradeSystemTest {

    private BuildingUpgradeSystem upgradeSystem;
    private EconomySystem economy;
    private EntityManager entities;

    @BeforeEach
    void setUp() {
        upgradeSystem = new BuildingUpgradeSystem();
        entities = new EntityManager();
        // EconomySystem needs a ResourceGenerator; its canAfford/spendCredits are all we need.
        economy = new EconomySystem(new ResourceGenerator());
    }

    /**
     * Creates a standard CC stat block with known upgrade costs [100, 200, 300].
     * REF: complete_building_stats.json — CommandCentre: hp=120, cost=100, buildTime=60
     */
    private BuildingStats createCCStats() {
        return new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE, 120, 100, 0, 10, 0, 10,
            60, 0, 15, 0, 0, 5, 0, 100, 50, 0, WeaponType.NONE, List.of(100, 200, 300));
    }

    /**
     * Creates a barracks stat block with upgrade costs [150, 300, 500].
     */
    private BuildingStats createBarracksStats() {
        return new BuildingStats(
            BuildingType.CONFED_INFANTRY_CENTRE, 100, 80, 0, 8, 0, 8,
            50, 0, 10, 0, 0, 5, 0, 80, 40, 0, WeaponType.NONE, List.of(150, 300, 500));
    }

    /**
     * Creates a stat block with no upgrade costs (building cannot be upgraded).
     */
    private BuildingStats createNoUpgradeStats() {
        return new BuildingStats(
            BuildingType.CONFED_LOCATOR, 60, 50, 0, 5, 0, 15,
            40, 0, 5, 0, 0, 0, 0, 50, 25, 0, WeaponType.NONE, List.of());
    }

    /**
     * Helper: place a completed building for Confederation.
     */
    private Building placeCompletedBuilding(BuildingType type, BuildingStats stats, GridPosition pos) {
        Building b = new Building(entities.allocateEntityId(), Faction.CONFEDERATION, pos, type, stats);
        b.setConstructionProgress(stats.buildTime()); // Mark complete
        entities.addBuilding(b);
        return b;
    }

    // ============================================================
    // upgradeBuilding — Success cases
    // ============================================================

    @Nested
    @DisplayName("upgradeBuilding — Success")
    class UpgradeSuccess {

        @Test
        @DisplayName("Should successfully upgrade a completed building at level 0 to level 1")
        void shouldUpgradeLevel0ToLevel1() {
            // Given: a completed CC with enough credits
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            economy.setCredits(0, 1000); // Plenty of credits

            // When
            boolean result = upgradeSystem.upgradeBuilding(cc, 0, economy);

            // Then
            assertTrue(result, "Upgrade should succeed");
            assertEquals(1, cc.getUpgradeLevel(), "Upgrade level should be 1");
            assertEquals(1000 - 100, economy.getCredits(0), "Should deduct 100 credits");
        }

        @Test
        @DisplayName("Should increase HP by 20% of base hp per upgrade level")
        void shouldIncreaseHPBy20PercentPerLevel() {
            // Given: CC has hp=120, 20% = 24 bonus HP per level
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            economy.setCredits(0, 10000);
            int baseHp = cc.getHp(); // Should be 120

            // When: upgrade to level 1
            upgradeSystem.upgradeBuilding(cc, 0, economy);

            // Then: HP should increase by 24, capped at effective max
            int expectedBonus = (int) (120 * 0.20); // 24
            assertEquals(expectedBonus, cc.getUpgradeMaxHpBonus(), "HP bonus should be 24");
            assertEquals(baseHp + expectedBonus, cc.getHp(), "Current HP should increase by bonus");
            assertEquals(120 + expectedBonus, cc.getEffectiveMaxHp(), "Effective max HP should increase");
        }

        @Test
        @DisplayName("Should accumulate HP bonus across multiple upgrades")
        void shouldAccumulateHPBonusAcrossLevels() {
            // Given: CC with enough credits for all 3 upgrades
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            economy.setCredits(0, 10000);

            // When: upgrade through all 3 levels
            upgradeSystem.upgradeBuilding(cc, 0, economy); // Level 1
            upgradeSystem.upgradeBuilding(cc, 0, economy); // Level 2
            upgradeSystem.upgradeBuilding(cc, 0, economy); // Level 3

            // Then: total bonus = 24 * 3 = 72
            int expectedTotalBonus = (int) (120 * 0.20) * 3;
            assertEquals(3, cc.getUpgradeLevel());
            assertEquals(expectedTotalBonus, cc.getUpgradeMaxHpBonus());
            assertEquals(120 + expectedTotalBonus, cc.getEffectiveMaxHp());
        }

        @Test
        @DisplayName("Should cap current HP at effective max after upgrade")
        void shouldCapHPAtEffectiveMax() {
            // Given: CC at half HP
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.setHp(60); // Half HP
            economy.setCredits(0, 10000);

            // When: upgrade
            upgradeSystem.upgradeBuilding(cc, 0, economy);

            // Then: HP should increase by 24 (the bonus), not jump to full
            // setHp(Math.min(hp + bonus, effectiveMaxHp)) = Math.min(60+24, 144) = 84
            assertEquals(84, cc.getHp(), "HP should increase by bonus amount, not exceed effective max");
        }
    }

    // ============================================================
    // upgradeBuilding — Failure cases
    // ============================================================

    @Nested
    @DisplayName("upgradeBuilding — Failure")
    class UpgradeFailure {

        @Test
        @DisplayName("Should fail for destroyed building")
        void shouldFailForDestroyedBuilding() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.takeDamage(999); // Destroy it
            economy.setCredits(0, 10000);

            assertFalse(upgradeSystem.upgradeBuilding(cc, 0, economy));
            assertEquals(0, cc.getUpgradeLevel(), "Level should not change");
        }

        @Test
        @DisplayName("Should fail for building still under construction")
        void shouldFailForUnderConstruction() {
            BuildingStats stats = createCCStats();
            Building cc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(50, 50), BuildingType.CONFED_COMMAND_CENTRE, stats);
            // constructionProgress defaults to 0, so it's under construction
            entities.addBuilding(cc);
            economy.setCredits(0, 10000);

            assertFalse(upgradeSystem.upgradeBuilding(cc, 0, economy));
        }

        @Test
        @DisplayName("Should fail when already at max upgrade level")
        void shouldFailAtMaxLevel() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.setUpgradeLevel(3); // Already max
            economy.setCredits(0, 10000);

            assertFalse(upgradeSystem.upgradeBuilding(cc, 0, economy));
        }

        @Test
        @DisplayName("Should fail when player cannot afford upgrade")
        void shouldFailWhenCannotAfford() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            economy.setCredits(0, 50); // Not enough for 100-cost upgrade

            assertFalse(upgradeSystem.upgradeBuilding(cc, 0, economy));
            assertEquals(0, cc.getUpgradeLevel(), "Level should not change");
            assertEquals(50, economy.getCredits(0), "Credits should not be deducted");
        }

        @Test
        @DisplayName("Should fail when no upgrade cost is defined")
        void shouldFailWhenNoUpgradeCost() {
            // Locator has empty upgradeCosts list
            Building locator = placeCompletedBuilding(
                BuildingType.CONFED_LOCATOR, createNoUpgradeStats(), new GridPosition(55, 55));
            economy.setCredits(0, 10000);

            assertFalse(upgradeSystem.upgradeBuilding(locator, 0, economy));
            assertEquals(0, locator.getUpgradeLevel());
        }
    }

    // ============================================================
    // getUpgradeCost
    // ============================================================

    @Nested
    @DisplayName("getUpgradeCost")
    class GetUpgradeCost {

        @Test
        @DisplayName("Should return correct cost for each upgrade level")
        void shouldReturnCorrectCostPerLevel() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));

            assertEquals(100, upgradeSystem.getUpgradeCost(cc, 1), "Level 1 should cost 100");
            assertEquals(200, upgradeSystem.getUpgradeCost(cc, 2), "Level 2 should cost 200");
            assertEquals(300, upgradeSystem.getUpgradeCost(cc, 3), "Level 3 should cost 300");
        }

        @Test
        @DisplayName("Should return 0 for out-of-range levels")
        void shouldReturnZeroForOutOfRange() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));

            assertEquals(0, upgradeSystem.getUpgradeCost(cc, 0), "Level 0 should be 0");
            assertEquals(0, upgradeSystem.getUpgradeCost(cc, -1), "Negative level should be 0");
            assertEquals(0, upgradeSystem.getUpgradeCost(cc, 4), "Level > max should be 0");
            assertEquals(0, upgradeSystem.getUpgradeCost(cc, 100), "Very high level should be 0");
        }

        @Test
        @DisplayName("Should return 0 when upgradeCosts list is empty")
        void shouldReturnZeroForEmptyCosts() {
            Building locator = placeCompletedBuilding(
                BuildingType.CONFED_LOCATOR, createNoUpgradeStats(), new GridPosition(55, 55));

            assertEquals(0, upgradeSystem.getUpgradeCost(locator, 1));
            assertEquals(0, upgradeSystem.getUpgradeCost(locator, 2));
            assertEquals(0, upgradeSystem.getUpgradeCost(locator, 3));
        }

        @Test
        @DisplayName("Should return 0 when targetLevel exceeds list size")
        void shouldReturnZeroWhenExceedsListSize() {
            // Barracks has only 3 costs — requesting level 4+ should return 0
            Building barracks = placeCompletedBuilding(
                BuildingType.CONFED_INFANTRY_CENTRE, createBarracksStats(), new GridPosition(40, 40));

            assertEquals(0, upgradeSystem.getUpgradeCost(barracks, 4));
        }
    }

    // ============================================================
    // getProductionSpeedModifier
    // ============================================================

    @Nested
    @DisplayName("getProductionSpeedModifier")
    class ProductionSpeedModifier {

        @Test
        @DisplayName("Level 0 should give modifier 300/20 = 15.0")
        void level0Modifier() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));

            assertEquals(300.0 / 20.0, upgradeSystem.getProductionSpeedModifier(cc), 0.001);
        }

        @Test
        @DisplayName("Level 1 should give modifier 300/25 = 12.0")
        void level1Modifier() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.setUpgradeLevel(1);

            assertEquals(300.0 / 25.0, upgradeSystem.getProductionSpeedModifier(cc), 0.001);
        }

        @Test
        @DisplayName("Level 3 should give modifier 300/35 ≈ 8.571")
        void level3Modifier() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.setUpgradeLevel(3);

            // upgradeBonus = 3 * 5 = 15; modifier = 300 / (15 + 20) = 300/35
            assertEquals(300.0 / 35.0, upgradeSystem.getProductionSpeedModifier(cc), 0.001);
        }

        @Test
        @DisplayName("Higher upgrade levels should give lower modifiers (slower effective time)")
        void higherLevelsGiveLowerModifiers() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));

            double mod0 = upgradeSystem.getProductionSpeedModifier(cc);
            cc.setUpgradeLevel(1);
            double mod1 = upgradeSystem.getProductionSpeedModifier(cc);
            cc.setUpgradeLevel(2);
            double mod2 = upgradeSystem.getProductionSpeedModifier(cc);
            cc.setUpgradeLevel(3);
            double mod3 = upgradeSystem.getProductionSpeedModifier(cc);

            assertTrue(mod0 > mod1, "Level 0 modifier > level 1");
            assertTrue(mod1 > mod2, "Level 1 modifier > level 2");
            assertTrue(mod2 > mod3, "Level 2 modifier > level 3");
        }
    }

    // ============================================================
    // canUpgrade
    // ============================================================

    @Nested
    @DisplayName("canUpgrade")
    class CanUpgrade {

        @Test
        @DisplayName("Completed building at level 0 should be upgradeable")
        void completedBuildingAtLevel0() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));

            assertTrue(upgradeSystem.canUpgrade(cc));
        }

        @Test
        @DisplayName("Building at max level should not be upgradeable")
        void atMaxLevel() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.setUpgradeLevel(3);

            assertFalse(upgradeSystem.canUpgrade(cc));
        }

        @Test
        @DisplayName("Destroyed building should not be upgradeable")
        void destroyedBuilding() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            cc.takeDamage(999);

            assertFalse(upgradeSystem.canUpgrade(cc));
        }

        @Test
        @DisplayName("Under-construction building should not be upgradeable")
        void underConstruction() {
            BuildingStats stats = createCCStats();
            Building cc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(50, 50), BuildingType.CONFED_COMMAND_CENTRE, stats);
            entities.addBuilding(cc);

            assertFalse(upgradeSystem.canUpgrade(cc));
        }

        @Test
        @DisplayName("canUpgrade should not check credits (only state check)")
        void canUpgradeDoesNotCheckCredits() {
            Building cc = placeCompletedBuilding(
                BuildingType.CONFED_COMMAND_CENTRE, createCCStats(), new GridPosition(50, 50));
            economy.setCredits(0, 0); // No credits

            // canUpgrade only checks building state, not affordability
            assertTrue(upgradeSystem.canUpgrade(cc));
            // But upgradeBuilding should fail due to insufficient credits
            assertFalse(upgradeSystem.upgradeBuilding(cc, 0, economy));
        }
    }

    // ============================================================
    // MAX_UPGRADE_LEVEL constant
    // ============================================================

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("MAX_UPGRADE_LEVEL should be 3")
        void maxUpgradeLevel() {
            assertEquals(3, BuildingUpgradeSystem.MAX_UPGRADE_LEVEL);
        }
    }
}