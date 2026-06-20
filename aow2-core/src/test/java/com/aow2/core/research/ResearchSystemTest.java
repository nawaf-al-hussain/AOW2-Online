package com.aow2.core.research;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.WeaponType;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResearchSystemTest {

    private EntityManager entities;
    private GameState state;
    private ResourceGenerator resourceGenerator;
    private EconomySystem economy;
    private ResearchSystem research;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        state = new GameState();
        resourceGenerator = new ResourceGenerator();
        economy = new EconomySystem(resourceGenerator);
        research = new ResearchSystem();
    }

    private BuildingStats createTechCentreStats() {
        return new BuildingStats(
            BuildingType.CONFED_TECH_CENTRE, 70, 60, 0, 4, 0, 8,
            45, 0, 8, 5, 0, 1, 0, 60, 30, 0, WeaponType.NONE, List.of());
    }

    private BuildingStats createLaboratoryStats() {
        return new BuildingStats(
            BuildingType.REBEL_LABORATORY, 70, 60, 0, 4, 0, 8,
            45, 0, 8, 5, 0, 1, 0, 60, 30, 0, WeaponType.NONE, List.of());
    }

    /**
     * Places a completed, powered Technology Centre for player 0 (Confederation).
     */
    private Building placePoweredTechCentre() {
        BuildingStats stats = createTechCentreStats();
        Building tc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(50, 50), BuildingType.CONFED_TECH_CENTRE, stats);
        tc.setConstructionProgress(stats.buildTime());
        tc.setPowered(true);
        entities.addBuilding(tc);
        return tc;
    }

    /**
     * Places a completed, powered Laboratory for player 1 (Resistance).
     */
    private Building placePoweredLaboratory() {
        BuildingStats stats = createLaboratoryStats();
        Building lab = new Building(entities.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(30, 30), BuildingType.REBEL_LABORATORY, stats);
        lab.setConstructionProgress(stats.buildTime());
        lab.setPowered(true);
        entities.addBuilding(lab);
        return lab;
    }

    @Nested
    @DisplayName("Start Research")
    class StartResearch {

        @Test
        @DisplayName("Should start research in Tech Centre")
        void shouldStartResearchInTechCentre() {
            // Given: a powered Tech Centre with enough credits
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);

            // When: start research ID 0 (first Confederation tech)
            boolean result = research.startResearch(tc, 0, 0, economy);

            // Then
            assertTrue(result, "Should start research in Tech Centre");
            assertTrue(tc.isResearching());
        }

        @Test
        @DisplayName("Should deduct credits on research start")
        void shouldDeductCreditsOnResearchStart() {
            // Given
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);
            int cost = 50; // Research ID 0 costs 50

            // When
            research.startResearch(tc, 0, 0, economy);

            // Then
            assertEquals(1000 - cost, economy.getCredits(0));
        }

        @Test
        @DisplayName("Should reject research when insufficient credits")
        void shouldRejectResearchWhenInsufficientCredits() {
            // Given: not enough credits
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 10);

            // When
            boolean result = research.startResearch(tc, 0, 0, economy);

            // Then
            assertFalse(result, "Should reject research when insufficient credits");
            assertFalse(tc.isResearching());
        }

        @Test
        @DisplayName("Should reject second research in same Tech Centre")
        void shouldRejectSecondResearchInSameTechCentre() {
            // Given: a Tech Centre already researching
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 10000);
            research.startResearch(tc, 0, 0, economy);

            // When: try to start another research in the same Tech Centre
            boolean result = research.startResearch(tc, 1, 0, economy);

            // Then
            assertFalse(result, "Should reject second research in same Tech Centre");
        }

        @Test
        @DisplayName("Should reject research in unpowered Tech Centre")
        void shouldRejectResearchInUnpoweredTechCentre() {
            // Given: an unpowered Tech Centre
            BuildingStats stats = createTechCentreStats();
            Building tc = new Building(entities.allocateEntityId(), Faction.CONFEDERATION,
                new GridPosition(50, 50), BuildingType.CONFED_TECH_CENTRE, stats);
            tc.setConstructionProgress(stats.buildTime());
            tc.setPowered(false);
            entities.addBuilding(tc);
            economy.setCredits(0, 1000);

            // When
            boolean result = research.startResearch(tc, 0, 0, economy);

            // Then
            assertFalse(result, "Should reject research in unpowered Tech Centre");
        }

        @Test
        @DisplayName("Should reject already completed research")
        void shouldRejectAlreadyCompletedResearch() {
            // Given: research already completed
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 10000);
            research.startResearch(tc, 0, 0, economy);

            // Complete the research
            int duration = new TechTree().getTechNode(Faction.CONFEDERATION, 0).duration();
            for (int i = 0; i < duration; i++) {
                research.processTick(entities, state);
            }

            // When: try to start the same research again
            Building tc2 = placePoweredTechCentre();
            boolean result = research.startResearch(tc2, 0, 0, economy);

            // Then
            assertFalse(result, "Should reject already completed research");
        }
    }

    @Nested
    @DisplayName("Research Progress")
    class ResearchProgress {

        @Test
        @DisplayName("Should progress research per tick")
        void shouldProgressResearchPerTick() {
            // Given: an active research
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);
            research.startResearch(tc, 0, 0, economy);

            // When: process a tick
            research.processTick(entities, state);

            // Then: research should still be active (not completed after 1 tick)
            assertTrue(tc.isResearching() || research.hasResearch(0, 0));
        }

        @Test
        @DisplayName("Should complete research and apply effect")
        void shouldCompleteResearchAndApplyEffect() {
            // Given: an active research
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);
            research.startResearch(tc, 0, 0, economy);

            TechTree tree = new TechTree();
            int duration = tree.getTechNode(Faction.CONFEDERATION, 0).duration();

            // When: process enough ticks to complete
            for (int i = 0; i < duration; i++) {
                research.processTick(entities, state);
            }

            // Then: research should be completed
            assertTrue(research.hasResearch(0, 0), "Research should be completed");
            assertFalse(tc.isResearching(), "Tech Centre should no longer be researching");
        }
    }

    @Nested
    @DisplayName("Prerequisites")
    class Prerequisites {

        @Test
        @DisplayName("Should reject research when prerequisites not met")
        void shouldRejectResearchWhenPrerequisitesNotMet() {
            // Given: research ID 1 requires research ID 0 to be completed first
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 10000);

            // When: try to start research ID 1 without completing ID 0
            boolean result = research.startResearch(tc, 1, 0, economy);

            // Then
            assertFalse(result, "Should reject research when prerequisites not met");
        }

        @Test
        @DisplayName("Should allow research when prerequisites are met")
        void shouldAllowResearchWhenPrerequisitesMet() {
            // Given: research ID 0 completed
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 10000);
            research.startResearch(tc, 0, 0, economy);

            TechTree tree = new TechTree();
            int duration = tree.getTechNode(Faction.CONFEDERATION, 0).duration();
            for (int i = 0; i < duration; i++) {
                research.processTick(entities, state);
            }

            // When: try to start research ID 1 (which requires ID 0)
            Building tc2 = placePoweredTechCentre();
            boolean result = research.startResearch(tc2, 1, 0, economy);

            // Then
            assertTrue(result, "Should allow research when prerequisites are met");
        }

        @Test
        @DisplayName("Should allow research with no prerequisites")
        void shouldAllowResearchWithNoPrerequisites() {
            // Given: research ID 0 has no prerequisites (base of the tree)
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);

            // When
            boolean result = research.startResearch(tc, 0, 0, economy);

            // Then
            assertTrue(result, "Should allow research with no prerequisites");
        }
    }

    @Nested
    @DisplayName("Completed Research Tracking")
    class CompletedResearchTracking {

        @Test
        @DisplayName("Should track completed research")
        void shouldTrackCompletedResearch() {
            // Given: no research completed
            assertFalse(research.hasResearch(0, 0));

            // When: complete a research
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);
            research.startResearch(tc, 0, 0, economy);

            TechTree tree = new TechTree();
            int duration = tree.getTechNode(Faction.CONFEDERATION, 0).duration();
            for (int i = 0; i < duration; i++) {
                research.processTick(entities, state);
            }

            // Then
            assertTrue(research.hasResearch(0, 0));
            Set<Integer> completed = research.getCompletedResearch(0);
            assertTrue(completed.contains(0));
        }

        @Test
        @DisplayName("Should return empty set for player with no research")
        void shouldReturnEmptySetForNoResearch() {
            // When
            Set<Integer> completed = research.getCompletedResearch(0);

            // Then
            assertTrue(completed.isEmpty());
        }

        @Test
        @DisplayName("Should track research separately per player")
        void shouldTrackResearchSeparatelyPerPlayer() {
            // Given: player 0 completes a research
            Building tc = placePoweredTechCentre();
            economy.setCredits(0, 1000);
            research.startResearch(tc, 0, 0, economy);

            TechTree tree = new TechTree();
            int duration = tree.getTechNode(Faction.CONFEDERATION, 0).duration();
            for (int i = 0; i < duration; i++) {
                research.processTick(entities, state);
            }

            // Then: player 1 should not have the research
            assertFalse(research.hasResearch(1, 0));
            assertTrue(research.hasResearch(0, 0));
        }
    }

    @Nested
    @DisplayName("Faction-Specific Research")
    class FactionSpecificResearch {

        @Test
        @DisplayName("Should reject research from wrong faction")
        void shouldRejectResearchFromWrongFaction() {
            // Given: player 1 (Resistance) tries to research Confederation tech ID 0
            Building lab = placePoweredLaboratory();
            economy.setCredits(1, 1000);

            // When: research ID 0 is a Confederation tech
            boolean result = research.startResearch(lab, 0, 1, economy);

            // Then: should be rejected (ID 0 doesn't exist in Resistance tree)
            assertFalse(result, "Should reject research from wrong faction");
        }
    }
}
