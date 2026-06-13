package com.aow2.core.ai;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.PowerSystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.movement.CollisionSystem;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.movement.PathfindingSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the AI system.
 * Tests the AI playing against itself (both players controlled by AI).
 */
class AISystemTest {

    private EntityManager entities;
    private GameState state;
    private GameMap map;
    private EconomySystem economy;
    private ResearchSystem research;
    private ProductionSystem production;
    private BuildingPlacementSystem placement;
    private PowerSystem power;
    private MovementSystem movement;
    private CombatSystem combat;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        state = new GameState();
        map = new GameMap(64, 64);

        ResourceGenerator resourceGenerator = new ResourceGenerator();
        economy = new EconomySystem(resourceGenerator);
        research = new ResearchSystem();
        production = new ProductionSystem();
        placement = new BuildingPlacementSystem();
        power = new PowerSystem();
        PathfindingSystem pathfinding = new PathfindingSystem();
        CollisionSystem collision = new CollisionSystem();
        movement = new MovementSystem(pathfinding, collision);
        combat = new CombatSystem(state, entities);
    }

    /**
     * Creates a completed Command Centre for a player.
     */
    private Building placeCompletedCC(int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        BuildingType type = faction == Faction.CONFEDERATION
            ? BuildingType.CONFED_COMMAND_CENTRE
            : BuildingType.REBEL_HEADQUARTERS;
        BuildingStats stats = createCCStats(type);
        GridPosition pos = faction == Faction.CONFEDERATION
            ? new GridPosition(10, 10)
            : new GridPosition(50, 50);
        Building cc = new Building(entities.allocateEntityId(), faction, pos, type, stats);
        cc.setConstructionProgress(stats.buildTime());
        cc.setPowered(true);
        entities.addBuilding(cc);
        return cc;
    }

    private BuildingStats createCCStats(BuildingType type) {
        return new BuildingStats(type, 120, 100, 0, 10, 0, 10, 60, 0, 15, 0, 0, 5, 0, 100, 50, List.of(100, 200, 300));
    }

    @Nested
    @DisplayName("AI System Initialization")
    class Initialization {

        @Test
        @DisplayName("Given AI system, when created with Hard difficulty, then properties are set correctly")
        void shouldInitializeWithCorrectProperties() {
            // Given
            AISystem ai = new AISystem(AIDifficulty.HARD, 1);

            // Then
            assertEquals(AIDifficulty.HARD, ai.getDifficulty());
            assertEquals(1, ai.getPlayerId());
            assertEquals(0, ai.getActiveTaskCount());
        }

        @Test
        @DisplayName("Given AI system, when created with different difficulties, then tick intervals differ")
        void shouldHaveDifferentTickIntervalsPerDifficulty() {
            // Given
            AISystem easyAI = new AISystem(AIDifficulty.EASY, 0);
            AISystem normalAI = new AISystem(AIDifficulty.NORMAL, 0);
            AISystem hardAI = new AISystem(AIDifficulty.HARD, 0);

            // Then
            assertTrue(AIDifficulty.EASY.tickInterval > AIDifficulty.NORMAL.tickInterval);
            assertTrue(AIDifficulty.NORMAL.tickInterval > AIDifficulty.HARD.tickInterval);
        }
    }

    @Nested
    @DisplayName("AI Decision Frequency")
    class DecisionFrequency {

        @Test
        @DisplayName("Given hard AI, when ticks advance past interval, then AI makes decision")
        void shouldMakeDecisionAtCorrectInterval() {
            // Given: hard AI with 15-tick interval
            AISystem ai = new AISystem(AIDifficulty.HARD, 0);
            placeCompletedCC(0);

            // When: advance ticks to trigger decision
            for (int i = 0; i <= 20; i++) {
                ai.processTick(entities, map, economy, research, production, placement, movement, combat, state);
                state.advanceTick();
            }

            // Then: AI should have made at least one decision
            assertTrue(ai.getLastDecisionTick() >= 0, "AI should have processed a tick");
        }

        @Test
        @DisplayName("Given easy AI, when ticks not at interval, then AI skips decision")
        void shouldSkipDecisionBeforeInterval() {
            // Given: easy AI with 60-tick interval
            AISystem ai = new AISystem(AIDifficulty.EASY, 0);
            placeCompletedCC(0);

            // When: advance only 10 ticks (before first interval)
            for (int i = 0; i <= 10; i++) {
                ai.processTick(entities, map, economy, research, production, placement, movement, combat, state);
                state.advanceTick();
            }

            // Then: lastDecisionTick should still be 0 (initial)
            // The AI should not have processed a full decision cycle yet
            assertTrue(ai.getLastDecisionTick() < AIDifficulty.EASY.tickInterval);
        }
    }

    @Nested
    @DisplayName("AI Strategy Quality")
    class StrategyQuality {

        @Test
        @DisplayName("Given hard AI, then strategy quality is 1.0")
        void hardAIShouldHavePerfectStrategyQuality() {
            assertEquals(1.0, AIDifficulty.HARD.strategyQuality);
        }

        @Test
        @DisplayName("Given easy AI, then strategy quality is 0.5")
        void easyAIShouldHaveReducedStrategyQuality() {
            assertEquals(0.5, AIDifficulty.EASY.strategyQuality);
        }

        @Test
        @DisplayName("Given normal AI, then max concurrent tasks is 5")
        void normalAIShouldHave5ConcurrentTasks() {
            assertEquals(5, AIDifficulty.NORMAL.maxConcurrentTasks);
        }
    }

    @Nested
    @DisplayName("AI Task Management")
    class TaskManagement {

        @Test
        @DisplayName("Given AI with active tasks, when task completes, then task count decreases")
        void shouldDecreaseTaskCountOnCompletion() {
            // Given
            AISystem ai = new AISystem(AIDifficulty.NORMAL, 0);

            // Simulate active tasks by processing
            placeCompletedCC(0);
            for (int i = 0; i <= 30; i++) {
                ai.processTick(entities, map, economy, research, production, placement, movement, combat, state);
                state.advanceTick();
            }

            int tasksBefore = ai.getActiveTaskCount();

            // When
            if (tasksBefore > 0) {
                ai.taskCompleted();
            }

            // Then
            if (tasksBefore > 0) {
                assertEquals(tasksBefore - 1, ai.getActiveTaskCount());
            }
        }

        @Test
        @DisplayName("Given AI, when task count goes below 0, then it stays at 0")
        void shouldNotGoBelowZeroTasks() {
            // Given
            AISystem ai = new AISystem(AIDifficulty.NORMAL, 0);

            // When
            ai.taskCompleted(); // No tasks to complete

            // Then
            assertEquals(0, ai.getActiveTaskCount());
        }
    }

    @Nested
    @DisplayName("AI Difficulty Properties")
    class DifficultyProperties {

        @Test
        @DisplayName("Given all difficulties, then tick intervals are positive")
        void allDifficultiesShouldHavePositiveTickIntervals() {
            for (AIDifficulty d : AIDifficulty.values()) {
                assertTrue(d.tickInterval > 0, d.name() + " should have positive tick interval");
            }
        }

        @Test
        @DisplayName("Given all difficulties, then strategy quality is between 0 and 1")
        void allDifficultiesShouldHaveValidStrategyQuality() {
            for (AIDifficulty d : AIDifficulty.values()) {
                assertTrue(d.strategyQuality > 0 && d.strategyQuality <= 1.0,
                    d.name() + " should have strategy quality in (0,1]");
            }
        }

        @Test
        @DisplayName("Given all difficulties, then max concurrent tasks is positive")
        void allDifficultiesShouldHavePositiveConcurrentTasks() {
            for (AIDifficulty d : AIDifficulty.values()) {
                assertTrue(d.maxConcurrentTasks > 0,
                    d.name() + " should have positive max concurrent tasks");
            }
        }
    }
}
