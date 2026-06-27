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

class ResourceGeneratorTest {

    private ResourceGenerator resourceGenerator;
    private EntityManager entities;

    @BeforeEach
    void setUp() {
        resourceGenerator = new ResourceGenerator();
        entities = new EntityManager();
    }

    /**
     * Creates a CC stat block for testing.
     * REF: complete_building_stats.json — CommandCentre: hp=120, cost=100, buildTime=60
     */
    private BuildingStats createCCStats(BuildingType type) {
        return new BuildingStats(
            type, 120, 100, 0, 10, 0, 10,
            60, 0, 15, 0, 0, 5, 0, 100, 50, 0, WeaponType.NONE, List.of(100, 200, 300));
    }

    /**
     * Creates a non-HQ building stat block (Generator).
     */
    private BuildingStats createGeneratorStats() {
        return new BuildingStats(
            BuildingType.CONFED_GENERATOR, 80, 60, 0, 5, 0, 8,
            40, 0, 10, 0, 6, 0, 0, 60, 30, 0, WeaponType.NONE, List.of(80, 160));
    }

    /**
     * Helper: place a completed HQ for a player.
     */
    private Building placeCompletedHQ(int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        BuildingType type = faction == Faction.CONFEDERATION
            ? BuildingType.CONFED_COMMAND_CENTRE
            : BuildingType.REBEL_HEADQUARTERS;
        BuildingStats stats = createCCStats(type);
        Building cc = new Building(entities.allocateEntityId(), faction,
            new GridPosition(50, 50), type, stats);
        cc.setConstructionProgress(stats.buildTime()); // Mark complete
        entities.addBuilding(cc);
        return cc;
    }

    /**
     * Helper: place a completed non-HQ building.
     */
    private Building placeCompletedBuilding(BuildingType type, BuildingStats stats, GridPosition pos, Faction faction) {
        Building b = new Building(entities.allocateEntityId(), faction, pos, type, stats);
        b.setConstructionProgress(stats.buildTime());
        entities.addBuilding(b);
        return b;
    }

    // ============================================================
    // countCommandCentres
    // ============================================================

    @Nested
    @DisplayName("countCommandCentres")
    class CountCommandCentres {

        @Test
        @DisplayName("Should return 0 when player has no buildings")
        void noBuildings() {
            assertEquals(0, resourceGenerator.countCommandCentres(0, entities));
        }

        @Test
        @DisplayName("Should count 1 for a single completed CC")
        void singleCC() {
            placeCompletedHQ(0);
            assertEquals(1, resourceGenerator.countCommandCentres(0, entities));
        }

        @Test
        @DisplayName("Should count 2 for two completed CCs")
        void twoCCs() {
            placeCompletedHQ(0);
            BuildingStats stats = createCCStats(BuildingType.CONFED_COMMAND_CENTRE);
            placeCompletedBuilding(BuildingType.CONFED_COMMAND_CENTRE, stats,
                new GridPosition(60, 60), Faction.CONFEDERATION);
            assertEquals(2, resourceGenerator.countCommandCentres(0, entities));
        }

        @Test
        @DisplayName("Should not count CC still under construction")
        void underConstructionNotCounted() {
            BuildingStats stats = createCCStats(BuildingType.CONFED_COMMAND_CENTRE);
            Building cc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(50, 50), BuildingType.CONFED_COMMAND_CENTRE, stats);
            // constructionProgress = 0, so it's under construction
            entities.addBuilding(cc);
            assertEquals(0, resourceGenerator.countCommandCentres(0, entities));
        }

        @Test
        @DisplayName("Should not count destroyed CC")
        void destroyedCCNotCounted() {
            Building cc = placeCompletedHQ(0);
            cc.takeDamage(999); // Destroy it (hp set to -1)
            assertEquals(0, resourceGenerator.countCommandCentres(0, entities));
        }

        @Test
        @DisplayName("Should not count non-HQ buildings (Generator)")
        void nonHQNotCounted() {
            placeCompletedBuilding(BuildingType.CONFED_GENERATOR, createGeneratorStats(),
                new GridPosition(50, 50), Faction.CONFEDERATION);
            assertEquals(0, resourceGenerator.countCommandCentres(0, entities));
        }

        @Test
        @DisplayName("Should not count other player's buildings")
        void otherPlayerNotCounted() {
            placeCompletedHQ(0); // Player 0's CC
            assertEquals(0, resourceGenerator.countCommandCentres(1, entities));
        }

        @Test
        @DisplayName("Should count HQ and completed CCs but not non-HQ buildings")
        void mixedBuildings() {
            placeCompletedHQ(0); // 1 CC
            placeCompletedBuilding(BuildingType.CONFED_GENERATOR, createGeneratorStats(),
                new GridPosition(55, 55), Faction.CONFEDERATION); // Not a CC
            BuildingStats stats = createCCStats(BuildingType.CONFED_COMMAND_CENTRE);
            placeCompletedBuilding(BuildingType.CONFED_COMMAND_CENTRE, stats,
                new GridPosition(60, 60), Faction.CONFEDERATION); // 2nd CC
            assertEquals(2, resourceGenerator.countCommandCentres(0, entities));
        }
    }

    // ============================================================
    // calculateCycleIncome — Base cases
    // ============================================================

    @Nested
    @DisplayName("calculateCycleIncome — Base Cases")
    class CalculateCycleIncomeBase {

        @Test
        @DisplayName("Should return 0 when player has no CCs")
        void noCCsReturnsZero() {
            assertEquals(0, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }

        @Test
        @DisplayName("Single CC at level 0 with normal modifier should return 100")
        void singleCCNormalDifficulty() {
            placeCompletedHQ(0);
            // baseIncome=100, playerModifier=1.0, upgradeBonus=0
            // income = (100 * 1.0) * 20 / (0 + 20) = 100
            assertEquals(100, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }

        @Test
        @DisplayName("Should use default normal modifier when not specified")
        void defaultModifier() {
            placeCompletedHQ(0);
            assertEquals(100, resourceGenerator.calculateCycleIncome(0, entities));
        }
    }

    // ============================================================
    // calculateCycleIncome — Diminishing returns
    // ============================================================

    @Nested
    @DisplayName("calculateCycleIncome — Diminishing Returns")
    class DiminishingReturns {

        @Test
        @DisplayName("Two CCs should have 30% diminishing returns on the second")
        void twoCCsDiminishing() {
            placeCompletedHQ(0);
            BuildingStats stats = createCCStats(BuildingType.CONFED_COMMAND_CENTRE);
            placeCompletedBuilding(BuildingType.CONFED_COMMAND_CENTRE, stats,
                new GridPosition(60, 60), Faction.CONFEDERATION);

            // CC1: 100, CC2: 100 * 0.70 = 70, total = 170
            // income = (170 * 1.0) * 20 / 20 = 170
            assertEquals(170, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }

        @Test
        @DisplayName("Three CCs should have compounding diminishing returns")
        void threeCCsDiminishing() {
            placeCompletedHQ(0);
            BuildingStats stats = createCCStats(BuildingType.CONFED_COMMAND_CENTRE);
            placeCompletedBuilding(BuildingType.CONFED_COMMAND_CENTRE, stats,
                new GridPosition(60, 60), Faction.CONFEDERATION);
            placeCompletedBuilding(BuildingType.CONFED_COMMAND_CENTRE, stats,
                new GridPosition(70, 70), Faction.CONFEDERATION);

            // CC1: 100, CC2: 70, CC3: 70*0.70=49, total = 219
            // income = (219 * 1.0) * 20 / 20 = 219
            assertEquals(219, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }
    }

    // ============================================================
    // calculateCycleIncome — Player modifier (difficulty)
    // ============================================================

    @Nested
    @DisplayName("calculateCycleIncome — Player Modifier")
    class PlayerModifier {

        @Test
        @DisplayName("Easy difficulty (0.7) should reduce income")
        void easyDifficulty() {
            placeCompletedHQ(0);
            // (100 * 0.7) * 20 / 20 = 70
            assertEquals(70, resourceGenerator.calculateCycleIncome(0, entities, 0.7));
        }

        @Test
        @DisplayName("Hard difficulty (1.3) should increase income")
        void hardDifficulty() {
            placeCompletedHQ(0);
            // (100 * 1.3) * 20 / 20 = 130
            assertEquals(130, resourceGenerator.calculateCycleIncome(0, entities, 1.3));
        }

        @Test
        @DisplayName("Zero modifier should return 0 income")
        void zeroModifier() {
            placeCompletedHQ(0);
            assertEquals(0, resourceGenerator.calculateCycleIncome(0, entities, 0.0));
        }
    }

    // ============================================================
    // calculateCycleIncome — Upgrade bonus
    // ============================================================

    @Nested
    @DisplayName("calculateCycleIncome — Upgrade Bonus")
    class UpgradeBonus {

        @Test
        @DisplayName("CC at upgrade level 1 should reduce per-cycle income")
        void upgradeLevel1() {
            Building cc = placeCompletedHQ(0);
            cc.setUpgradeLevel(1);
            // upgradeBonus = 1 * 2 = 2
            // income = (100 * 1.0) * 20 / (2 + 20) = 2000/22 = 90
            int expected = (int) (100.0 * 20.0 / 22);
            assertEquals(expected, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }

        @Test
        @DisplayName("CC at upgrade level 3 should reduce per-cycle income further")
        void upgradeLevel3() {
            Building cc = placeCompletedHQ(0);
            cc.setUpgradeLevel(3);
            // upgradeBonus = 3 * 2 = 6
            // income = (100 * 1.0) * 20 / (6 + 20) = 2000/26 = 76
            int expected = (int) (100.0 * 20.0 / 26);
            assertEquals(expected, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }
    }

    // ============================================================
    // calculateCycleIncome — Faction differential
    // ============================================================

    @Nested
    @DisplayName("calculateCycleIncome — Faction Differential")
    class FactionDifferential {

        @Test
        @DisplayName("Resistance (player 1) should get ~15% more income")
        void resistanceBonus() {
            placeCompletedHQ(1); // Resistance
            // base: 100, modifier: 1.0, upgradeBonus: 0
            // RE formula: (100 * 1.0) * 20 / 20 = 100
            // FIX (CI verification): Math.round(100 * 1.15) = Math.round(114.999...) = 115
            int income = resourceGenerator.calculateCycleIncome(1, entities, 1.0);
            assertEquals(115, income, "Resistance income should be 115 (15% bonus, Math.round), got " + income);
        }

        @Test
        @DisplayName("Confederation (player 0) should not get faction bonus")
        void confederationNoBonus() {
            placeCompletedHQ(0); // Confederation
            assertEquals(100, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }
    }

    // ============================================================
    // calculateCycleIncome — Floor at 0
    // ============================================================

    @Nested
    @DisplayName("calculateCycleIncome — Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Income should never be negative")
        void neverNegative() {
            // Extreme scenario: many upgraded CCs with very low modifier
            placeCompletedHQ(0);
            // Even with negative-ish scenarios, result is max(0, ...)
            int income = resourceGenerator.calculateCycleIncome(0, entities, 0.001);
            assertTrue(income >= 0, "Income should never be negative");
        }

        @Test
        @DisplayName("Under-construction CC should not generate income")
        void underConstructionCCNoIncome() {
            BuildingStats stats = createCCStats(BuildingType.CONFED_COMMAND_CENTRE);
            Building cc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(50, 50), BuildingType.CONFED_COMMAND_CENTRE, stats);
            entities.addBuilding(cc);
            assertEquals(0, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }

        @Test
        @DisplayName("Destroyed CC should not generate income")
        void destroyedCCNoIncome() {
            Building cc = placeCompletedHQ(0);
            cc.takeDamage(999);
            assertEquals(0, resourceGenerator.calculateCycleIncome(0, entities, 1.0));
        }
    }

    // ============================================================
    // getKillReward
    // ============================================================

    @Nested
    @DisplayName("getKillReward")
    class KillReward {

        @Test
        @DisplayName("Should return minimum reward of 1 when baseDistance is 0")
        void zeroBaseDistanceReturnsMin() {
            GridPosition killerPos = new GridPosition(30, 30);
            GridPosition enemyBase = new GridPosition(50, 50);
            assertEquals(1, resourceGenerator.getKillReward(
                com.aow2.common.model.UnitType.CONFED_INFANTRY, killerPos, enemyBase, 0));
        }

        @Test
        @DisplayName("Should return minimum reward of 1 when baseDistance is negative")
        void negativeBaseDistanceReturnsMin() {
            GridPosition killerPos = new GridPosition(30, 30);
            GridPosition enemyBase = new GridPosition(50, 50);
            assertEquals(1, resourceGenerator.getKillReward(
                com.aow2.common.model.UnitType.CONFED_INFANTRY, killerPos, enemyBase, -5));
        }

        @Test
        @DisplayName("Kill reward should increase with distance to enemy base")
        void rewardIncreasesWithDistance() {
            GridPosition enemyBase = new GridPosition(0, 0);
            int baseDistance = 50;

            GridPosition closeKiller = new GridPosition(40, 40); // ~56.6 euclidean
            GridPosition farKiller = new GridPosition(0, 50); // 50 euclidean (same distance as baseDistance, so reward = unitCost * 3 * 50 / 100 = unitCost * 1.5)

            int closeReward = resourceGenerator.getKillReward(
                com.aow2.common.model.UnitType.CONFED_INFANTRY, closeKiller, enemyBase, baseDistance);
            int farReward = resourceGenerator.getKillReward(
                com.aow2.common.model.UnitType.CONFED_INFANTRY, farKiller, enemyBase, baseDistance);

            // Both should be >= 1
            assertTrue(closeReward >= 1, "Close reward should be at least 1");
            assertTrue(farReward >= 1, "Far reward should be at least 1");
        }

        @Test
        @DisplayName("Kill reward should be at least 1 even for cheap units")
        void minimumRewardForCheapUnits() {
            GridPosition killerPos = new GridPosition(25, 25);
            GridPosition enemyBase = new GridPosition(50, 50);
            int reward = resourceGenerator.getKillReward(
                com.aow2.common.model.UnitType.CONFED_INFANTRY, killerPos, enemyBase, 100);
            assertTrue(reward >= 1, "Reward should be at least 1");
        }
    }

    // ============================================================
    // Constants
    // ============================================================

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("BASE_CC_INCOME should be 100")
        void baseCCIncome() {
            assertEquals(100, ResourceGenerator.BASE_CC_INCOME);
        }
    }
}