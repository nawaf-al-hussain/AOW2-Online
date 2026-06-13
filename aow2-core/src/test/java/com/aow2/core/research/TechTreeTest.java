package com.aow2.core.research;

import com.aow2.common.model.Faction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechTreeTest {

    private TechTree techTree;

    @BeforeEach
    void setUp() {
        techTree = new TechTree();
    }

    @Nested
    @DisplayName("Tech Nodes Retrieval")
    class TechNodesRetrieval {

        @Test
        @DisplayName("Should return all tech nodes for Confederation")
        void shouldReturnAllTechNodesForConfederation() {
            // When
            List<TechTree.ResearchNode> nodes = techTree.getTechNodes(Faction.CONFEDERATION);

            // Then
            assertFalse(nodes.isEmpty(), "Confederation should have tech nodes");
            assertEquals(8, nodes.size(), "Confederation should have 8 base tech nodes");
        }

        @Test
        @DisplayName("Should return all tech nodes for Resistance")
        void shouldReturnAllTechNodesForResistance() {
            // When
            List<TechTree.ResearchNode> nodes = techTree.getTechNodes(Faction.RESISTANCE);

            // Then
            assertFalse(nodes.isEmpty(), "Resistance should have tech nodes");
            assertEquals(7, nodes.size(), "Resistance should have 7 base tech nodes");
        }

        @Test
        @DisplayName("Should return empty list for Neutral faction")
        void shouldReturnEmptyListForNeutral() {
            // When
            List<TechTree.ResearchNode> nodes = techTree.getTechNodes(Faction.NEUTRAL);

            // Then
            assertTrue(nodes.isEmpty(), "Neutral faction should have no tech nodes");
        }
    }

    @Nested
    @DisplayName("Specific Tech Node")
    class SpecificTechNode {

        @Test
        @DisplayName("Should return specific tech node by ID and faction")
        void shouldReturnSpecificTechNode() {
            // When
            TechTree.ResearchNode node = techTree.getTechNode(Faction.CONFEDERATION, 0);

            // Then
            assertNotNull(node);
            assertEquals(0, node.id());
            assertEquals("Reinforced Infantry Armour", node.name());
            assertEquals(Faction.CONFEDERATION, node.faction());
        }

        @Test
        @DisplayName("Should return null for non-existent tech ID")
        void shouldReturnNullForNonExistentTech() {
            // When
            TechTree.ResearchNode node = techTree.getTechNode(Faction.CONFEDERATION, 999);

            // Then
            assertNull(node);
        }

        @Test
        @DisplayName("Should return null when tech ID belongs to different faction")
        void shouldReturnNullWhenWrongFaction() {
            // When: research ID 0 is Confederation, not Resistance
            TechTree.ResearchNode node = techTree.getTechNode(Faction.RESISTANCE, 0);

            // Then
            assertNull(node, "Should return null when tech ID belongs to different faction");
        }

        @Test
        @DisplayName("Should return Resistance tech node by ID")
        void shouldReturnResistanceTechNode() {
            // When
            TechTree.ResearchNode node = techTree.getTechNode(Faction.RESISTANCE, 8);

            // Then
            assertNotNull(node);
            assertEquals(8, node.id());
            assertEquals("Guerrilla Tactics", node.name());
            assertEquals(Faction.RESISTANCE, node.faction());
        }
    }

    @Nested
    @DisplayName("Prerequisites")
    class Prerequisites {

        @Test
        @DisplayName("Should return prerequisites for tech")
        void shouldReturnPrerequisitesForTech() {
            // When: research ID 1 requires research ID 0
            List<Integer> prereqs = techTree.getPrerequisites(Faction.CONFEDERATION, 1);

            // Then
            assertFalse(prereqs.isEmpty(), "Research ID 1 should have prerequisites");
            assertTrue(prereqs.contains(0), "Research ID 1 should require research ID 0");
        }

        @Test
        @DisplayName("Should return empty prerequisites for base tech")
        void shouldReturnEmptyPrerequisitesForBaseTech() {
            // When: research ID 0 is the base of the tree
            List<Integer> prereqs = techTree.getPrerequisites(Faction.CONFEDERATION, 0);

            // Then
            assertTrue(prereqs.isEmpty(), "Base tech should have no prerequisites");
        }

        @Test
        @DisplayName("Should return empty list for non-existent tech")
        void shouldReturnEmptyListForNonExistentTech() {
            // When
            List<Integer> prereqs = techTree.getPrerequisites(Faction.CONFEDERATION, 999);

            // Then
            assertTrue(prereqs.isEmpty());
        }
    }

    @Nested
    @DisplayName("Unlocked Techs")
    class UnlockedTechs {

        @Test
        @DisplayName("Should return techs unlocked after completion")
        void shouldReturnUnlockedTechsAfterCompletion() {
            // When: completing research ID 0 should unlock research ID 1
            List<Integer> unlocked = techTree.getUnlockedTechs(Faction.CONFEDERATION, 0);

            // Then
            assertFalse(unlocked.isEmpty(), "Research ID 0 should unlock other techs");
            assertTrue(unlocked.contains(1), "Research ID 0 should unlock research ID 1");
        }

        @Test
        @DisplayName("Should return empty list for terminal tech")
        void shouldReturnEmptyListForTerminalTech() {
            // When: research ID 7 is the last in the Confederation chain
            List<Integer> unlocked = techTree.getUnlockedTechs(Faction.CONFEDERATION, 7);

            // Then
            assertTrue(unlocked.isEmpty(), "Terminal tech should not unlock further techs");
        }

        @Test
        @DisplayName("Should return empty list for non-existent tech")
        void shouldReturnEmptyListForNonExistentTech() {
            // When
            List<Integer> unlocked = techTree.getUnlockedTechs(Faction.CONFEDERATION, 999);

            // Then
            assertTrue(unlocked.isEmpty());
        }
    }

    @Nested
    @DisplayName("Tech Node Properties")
    class TechNodeProperties {

        @Test
        @DisplayName("Should have valid costs for all techs")
        void shouldHaveValidCostsForAllTechs() {
            // When
            List<TechTree.ResearchNode> confedNodes = techTree.getTechNodes(Faction.CONFEDERATION);
            List<TechTree.ResearchNode> rebelNodes = techTree.getTechNodes(Faction.RESISTANCE);

            // Then: all costs should be positive
            for (TechTree.ResearchNode node : confedNodes) {
                assertTrue(node.cost() > 0,
                    "Tech " + node.id() + " should have positive cost, got " + node.cost());
            }
            for (TechTree.ResearchNode node : rebelNodes) {
                assertTrue(node.cost() > 0,
                    "Tech " + node.id() + " should have positive cost, got " + node.cost());
            }
        }

        @Test
        @DisplayName("Should have valid durations for all techs")
        void shouldHaveValidDurationsForAllTechs() {
            // When
            List<TechTree.ResearchNode> confedNodes = techTree.getTechNodes(Faction.CONFEDERATION);
            List<TechTree.ResearchNode> rebelNodes = techTree.getTechNodes(Faction.RESISTANCE);

            // Then: all durations should be positive
            for (TechTree.ResearchNode node : confedNodes) {
                assertTrue(node.duration() > 0,
                    "Tech " + node.id() + " should have positive duration, got " + node.duration());
            }
            for (TechTree.ResearchNode node : rebelNodes) {
                assertTrue(node.duration() > 0,
                    "Tech " + node.id() + " should have positive duration, got " + node.duration());
            }
        }

        @Test
        @DisplayName("Should have consistent unlock chains")
        void shouldHaveConsistentUnlockChains() {
            // Given: for each tech that unlocks others, the unlocked techs should have
            // this tech as a prerequisite
            List<TechTree.ResearchNode> confedNodes = techTree.getTechNodes(Faction.CONFEDERATION);

            for (TechTree.ResearchNode node : confedNodes) {
                for (int unlockedId : node.unlocks()) {
                    List<Integer> prereqs = techTree.getPrerequisites(Faction.CONFEDERATION, unlockedId);
                    assertTrue(prereqs.contains(node.id()),
                        "Unlocked tech " + unlockedId + " should list " + node.id() + " as prerequisite");
                }
            }
        }
    }
}
