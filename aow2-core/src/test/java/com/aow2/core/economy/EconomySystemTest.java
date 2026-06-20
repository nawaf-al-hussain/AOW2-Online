package com.aow2.core.economy;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EconomySystemTest {

    private EntityManager entities;
    private GameState state;
    private ResourceGenerator resourceGenerator;
    private EconomySystem economy;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        state = new GameState();
        resourceGenerator = new ResourceGenerator();
        economy = new EconomySystem(resourceGenerator);
    }

    /**
     * Creates a Command Centre stat block for testing.
     * REF: complete_building_stats.json — CommandCentre: hp=120, cost=100, buildTime=60
     */
    private BuildingStats createCommandCentreStats() {
        return new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE, 120, 100, 0, 10, 0, 10,
            60, 0, 15, 0, 0, 5, 0, 100, 50, 0, WeaponType.NONE, List.of(100, 200, 300));
    }

    /**
     * Creates a Headquarters stat block for testing.
     */
    private BuildingStats createHeadquartersStats() {
        return new BuildingStats(
            BuildingType.REBEL_HEADQUARTERS, 120, 100, 0, 10, 0, 10,
            60, 0, 15, 0, 0, 5, 0, 100, 50, 0, WeaponType.NONE, List.of(100, 200, 300));
    }

    /**
     * Helper: place a completed Command Centre for a player.
     */
    private Building placeCompletedCC(int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        BuildingType type = faction == Faction.CONFEDERATION
            ? BuildingType.CONFED_COMMAND_CENTRE
            : BuildingType.REBEL_HEADQUARTERS;
        BuildingStats stats = faction == Faction.CONFEDERATION
            ? createCommandCentreStats()
            : createHeadquartersStats();
        Building cc = new Building(entities.allocateEntityId(), faction,
            new GridPosition(50, 50), type, stats);
        cc.setConstructionProgress(stats.buildTime()); // Mark as complete
        entities.addBuilding(cc);
        return cc;
    }

    // --- EconomySystem Tests ---

    @Nested
    @DisplayName("Credit Generation")
    class CreditGeneration {

        @Test
        @DisplayName("Should generate credits per cycle")
        void shouldGenerateCreditsPerCycle() {
            // Given: player 0 has a Command Centre
            placeCompletedCC(0);

            // When: process ticks up to and including the first cycle boundary (tick 127)
            for (int i = 0; i <= EconomySystem.CREDIT_CYCLE_TICKS; i++) {
                economy.processTick(entities, state);
                state.advanceTick();
            }

            // Then: player should have earned income
            int creditsAfter = economy.getCredits(0);
            assertTrue(creditsAfter > EconomySystem.STARTING_CREDITS,
                "Player should have earned credits: got " + creditsAfter);
        }

        @Test
        @DisplayName("Should not generate credits before cycle boundary")
        void shouldNotGenerateCreditsBeforeCycle() {
            // Given: player 0 has a Command Centre
            placeCompletedCC(0);

            // When: process ticks just before the cycle boundary
            for (int i = 0; i < EconomySystem.CREDIT_CYCLE_TICKS - 1; i++) {
                economy.processTick(entities, state);
                state.advanceTick();
            }

            // Then: player should still have starting credits
            assertEquals(EconomySystem.STARTING_CREDITS, economy.getCredits(0));
        }

        @Test
        @DisplayName("Should apply diminishing returns for multiple CCs")
        void shouldApplyDiminishingReturnsForMultipleCCs() {
            // Given: player 0 has two Command Centres
            placeCompletedCC(0);
            Faction faction = Faction.CONFEDERATION;
            BuildingStats stats = createCommandCentreStats();
            Building cc2 = new Building(entities.allocateEntityId(), faction,
                new GridPosition(60, 60), BuildingType.CONFED_COMMAND_CENTRE, stats);
            cc2.setConstructionProgress(stats.buildTime());
            entities.addBuilding(cc2);

            // When: calculate income
            int income = economy.calculateIncome(0, entities);

            // Then: RE formula with upgradeBonus=0, playerModifier=1.0 gives same result as before
            // First CC: 100, Second CC: 100 * 0.70 = 70, Total base: 170
            // income = (170 * 1.0) * 20 / (0 + 20) = 170
            int expectedIncome = 170;
            assertEquals(expectedIncome, income,
                "Two CCs should have diminishing returns: expected " + expectedIncome + ", got " + income);
        }

        @Test
        @DisplayName("Should generate zero income with no CCs")
        void shouldGenerateZeroIncomeWithNoCCs() {
            // Given: player has no Command Centres
            // When
            int income = economy.calculateIncome(0, entities);
            // Then
            assertEquals(0, income);
        }
    }

    // --- H-6: Faction Income Differential ---

    @Nested
    @DisplayName("H-6: Faction Income Differential")
    class FactionIncomeDifferential {

        @Test
        @DisplayName("Resistance should earn 15% more income than Confederation with same buildings")
        void resistanceShouldEarnMoreThanConfederation() {
            // Given: both players have one completed CC, no upgrades, normal difficulty
            placeCompletedCC(0); // Confederation
            placeCompletedCC(1); // Resistance

            // When
            int confedIncome = economy.calculateIncome(0, entities);
            int resistanceIncome = economy.calculateIncome(1, entities);

            // Then: Resistance gets 15% more
            // base=100, playerModifier=1.0, upgradeBonus=0 → (100*1.0)*20/20 = 100
            // Confederation: 100, Resistance: (int)(100 * 1.15) = 115
            assertEquals(100, confedIncome, "Confederation base income should be 100");
            assertEquals(115, resistanceIncome, "Resistance income should be 115 (15% bonus)");
            assertTrue(resistanceIncome > confedIncome,
                "Resistance should earn more than Confederation");
        }
    }

    // --- H-7: Full RE Income Formula ---

    @Nested
    @DisplayName("H-7: RE Income Formula (playerModifier + upgradeBonus)")
    class REIncomeFormula {

        @Test
        @DisplayName("playerModifier should scale income (difficulty effect)")
        void playerModifierShouldScaleIncome() {
            // Given: player 0 has one CC, no upgrades
            placeCompletedCC(0);

            // When: calculate with playerModifier=0.7 (easy)
            int easyIncome = resourceGenerator.calculateCycleIncome(0, entities, 0.7);
            // When: calculate with playerModifier=1.3 (hard)
            int hardIncome = resourceGenerator.calculateCycleIncome(0, entities, 1.3);

            // Then:
            // base=100, upgradeBonus=0
            // Easy: (100 * 0.7) * 20 / 20 = 70
            // Hard: (100 * 1.3) * 20 / 20 = 130
            assertEquals(70, easyIncome, "Easy difficulty income should be 70");
            assertEquals(130, hardIncome, "Hard difficulty income should be 130");
        }

        @Test
        @DisplayName("CC upgrade should affect income via upgradeBonus")
        void ccUpgradeShouldAffectIncome() {
            // Given: player 0 has one CC at upgrade level 2
            Building cc = placeCompletedCC(0);
            cc.setUpgradeLevel(2);

            // When
            int income = economy.calculateIncome(0, entities);

            // Then: upgradeBonus = 2 * 2 = 4
            // income = (100 * 1.0) * 20 / (4 + 20) = 100 * 20 / 24 = 83
            int expectedIncome = (int) (100 * 20.0 / 24);
            assertEquals(expectedIncome, income,
                "Upgraded CC should have reduced per-cycle income: expected " + expectedIncome + ", got " + income);
        }

        @Test
        @DisplayName("EconomySystem.setPlayerIncomeModifier should affect calculated income")
        void setPlayerIncomeModifierShouldAffectIncome() {
            // Given: player 0 has one CC
            placeCompletedCC(0);

            // Default income (normal difficulty, modifier=1.0)
            assertEquals(100, economy.calculateIncome(0, entities));

            // When: set easy modifier
            economy.setPlayerIncomeModifier(0, 0.7);

            // Then: income should be 70
            assertEquals(70, economy.calculateIncome(0, entities));

            // When: set hard modifier
            economy.setPlayerIncomeModifier(0, 1.3);

            // Then: income should be 130
            assertEquals(130, economy.calculateIncome(0, entities));
        }

        @Test
        @DisplayName("Combined effects: faction + modifier + upgrade")
        void combinedEffects() {
            // Given: Resistance player (1) with one CC at upgrade level 1, hard difficulty
            Building cc = placeCompletedCC(1);
            cc.setUpgradeLevel(1);
            economy.setPlayerIncomeModifier(1, 1.3);

            // When
            int income = economy.calculateIncome(1, entities);

            // Then: upgradeBonus = 1 * 2 = 2
            // RE formula: (100 * 1.3) * 20 / (2 + 20) = 130 * 20 / 22 = 118.18...
            // Faction: 118 * 1.15 = 135.7... → 135
            int reFormulaIncome = (int) ((100 * 1.3) * 20.0 / 22);
            int expectedIncome = (int) (reFormulaIncome * 1.15);
            assertEquals(expectedIncome, income,
                "Combined income should apply formula + faction bonus");
            assertTrue(income > 100, "Combined effects should produce meaningful income");
        }

        @Test
        @DisplayName("Multiple CCs with different upgrade levels")
        void multipleCCsWithDifferentUpgrades() {
            // Given: player 0 has two CCs, one at level 0 and one at level 3
            Building cc1 = placeCompletedCC(0);
            cc1.setUpgradeLevel(0);

            BuildingStats stats = createCommandCentreStats();
            Building cc2 = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(60, 60), BuildingType.CONFED_COMMAND_CENTRE, stats);
            cc2.setConstructionProgress(stats.buildTime());
            cc2.setUpgradeLevel(3);
            entities.addBuilding(cc2);

            // When
            int income = economy.calculateIncome(0, entities);

            // Then:
            // baseIncome: CC1=100, CC2=70 (diminishing returns), total=170
            // upgradeBonus: (0*2) + (3*2) = 6
            // income = (170 * 1.0) * 20 / (6 + 20) = 170 * 20 / 26 = 130.76... → 130
            int expectedIncome = (int) (170 * 20.0 / 26);
            assertEquals(expectedIncome, income,
                "Multiple CCs with different upgrades should sum upgrade bonuses");
        }

        @Test
        @DisplayName("getPlayerIncomeModifier should return current modifier")
        void getPlayerIncomeModifier() {
            assertEquals(1.0, economy.getPlayerIncomeModifier(0));
            assertEquals(1.0, economy.getPlayerIncomeModifier(1));

            economy.setPlayerIncomeModifier(0, 0.7);
            assertEquals(0.7, economy.getPlayerIncomeModifier(0));
            assertEquals(1.0, economy.getPlayerIncomeModifier(1));
        }
    }

    @Nested
    @DisplayName("Credit Spending")
    class CreditSpending {

        @Test
        @DisplayName("Should not afford when insufficient credits")
        void shouldNotAffordWhenInsufficientCredits() {
            // Given: player has starting credits
            int cost = EconomySystem.STARTING_CREDITS + 100;

            // When/Then
            assertFalse(economy.canAfford(0, cost));
        }

        @Test
        @DisplayName("Should afford when sufficient credits")
        void shouldAffordWhenSufficientCredits() {
            // Given: player has starting credits
            int cost = EconomySystem.STARTING_CREDITS / 2;

            // When/Then
            assertTrue(economy.canAfford(0, cost));
        }

        @Test
        @DisplayName("Should deduct credits on spend")
        void shouldDeductCreditsOnSpend() {
            // Given: player has starting credits
            int cost = 100;
            int expectedRemaining = EconomySystem.STARTING_CREDITS - cost;

            // When
            boolean result = economy.spendCredits(0, cost);

            // Then
            assertTrue(result);
            assertEquals(expectedRemaining, economy.getCredits(0));
        }

        @Test
        @DisplayName("Should not deduct credits when insufficient")
        void shouldNotDeductCreditsWhenInsufficient() {
            // Given: player has starting credits
            int cost = EconomySystem.STARTING_CREDITS + 50;

            // When
            boolean result = economy.spendCredits(0, cost);

            // Then
            assertFalse(result);
            assertEquals(EconomySystem.STARTING_CREDITS, economy.getCredits(0));
        }
    }

    @Nested
    @DisplayName("Credit Addition")
    class CreditAddition {

        @Test
        @DisplayName("Should add credits on kill reward")
        void shouldAddCreditsOnKillReward() {
            // Given: player has starting credits
            int reward = 50;

            // When
            economy.addCredits(0, reward);

            // Then
            assertEquals(EconomySystem.STARTING_CREDITS + reward, economy.getCredits(0));
        }

        @Test
        @DisplayName("Should add credits multiple times")
        void shouldAddCreditsMultipleTimes() {
            // Given
            economy.addCredits(0, 30);
            economy.addCredits(0, 20);

            // Then
            assertEquals(EconomySystem.STARTING_CREDITS + 50, economy.getCredits(0));
        }
    }

    @Nested
    @DisplayName("Player-Faction Mapping")
    class PlayerFactionMapping {

        @Test
        @DisplayName("Should map player 0 to Confederation")
        void shouldMapPlayer0ToConfederation() {
            assertEquals(Faction.CONFEDERATION, EconomySystem.playerFaction(0));
        }

        @Test
        @DisplayName("Should map player 1 to Resistance")
        void shouldMapPlayer1ToResistance() {
            assertEquals(Faction.RESISTANCE, EconomySystem.playerFaction(1));
        }

        @Test
        @DisplayName("Should map Confederation to player 0")
        void shouldMapConfederationToPlayer0() {
            assertEquals(0, EconomySystem.playerId(Faction.CONFEDERATION));
        }

        @Test
        @DisplayName("Should map Resistance to player 1")
        void shouldMapResistanceToPlayer1() {
            assertEquals(1, EconomySystem.playerId(Faction.RESISTANCE));
        }
    }
}