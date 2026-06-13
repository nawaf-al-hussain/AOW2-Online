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

            // Then: second CC contributes 30% less than the first
            // First CC: 100, Second CC: 100 * 0.70 = 70, Total: 170
            int expectedIncome = ResourceGenerator.BASE_CC_INCOME +
                (int)(ResourceGenerator.BASE_CC_INCOME * 0.70);
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
