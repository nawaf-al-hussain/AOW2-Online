package com.aow2.core.research;

import com.aow2.common.model.Faction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TechTreeTest {

    private TechTree techTree;

    @BeforeEach
    void setUp() {
        techTree = new TechTree();
    }

    @Nested
    @DisplayName("Tech Node Count")
    class TechNodeCount {

        @Test
        @DisplayName("Confederation should have 25 tech nodes")
        void confederationShouldHave25TechNodes() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.CONFEDERATION);
            assertEquals(25, nodes.size(), "Confederation should have 25 tech nodes (IDs 0-23 + 43)");
        }

        @Test
        @DisplayName("Resistance should have 23 tech nodes")
        void resistanceShouldHave23TechNodes() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.RESISTANCE);
            assertEquals(23, nodes.size(), "Resistance should have 23 tech nodes (IDs 24-47 excluding 43)");
        }

        @Test
        @DisplayName("Should return empty list for Neutral faction")
        void shouldReturnEmptyListForNeutral() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.NEUTRAL);
            assertTrue(nodes.isEmpty(), "Neutral faction should have no tech nodes");
        }

        @Test
        @DisplayName("Total research nodes should be 48")
        void totalResearchNodesShouldBe48() {
            int confedCount = techTree.getTechNodes(Faction.CONFEDERATION).size();
            int resistanceCount = techTree.getTechNodes(Faction.RESISTANCE).size();
            assertEquals(48, confedCount + resistanceCount, "Total research IDs 0-47 = 48 nodes");
        }
    }

    @Nested
    @DisplayName("Research ID Coverage")
    class ResearchIdCoverage {

        @Test
        @DisplayName("All Confederation research IDs 0-23 and 43 should exist")
        void allConfederationResearchIdsShouldExist() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.CONFEDERATION);
            Set<Integer> ids = new HashSet<>();
            for (TechTree.TechTreeNode node : nodes) {
                ids.add(node.id());
            }

            // IDs 0-23
            for (int i = 0; i <= 23; i++) {
                assertTrue(ids.contains(i), "Confederation should contain research ID " + i);
            }
            // ID 43
            assertTrue(ids.contains(43), "Confederation should contain research ID 43");
            // No extra IDs
            assertEquals(25, ids.size(), "Confederation should have exactly 25 unique IDs");
        }

        @Test
        @DisplayName("All Resistance research IDs 24-47 excluding 43 should exist")
        void allResistanceResearchIdsShouldExist() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.RESISTANCE);
            Set<Integer> ids = new HashSet<>();
            for (TechTree.TechTreeNode node : nodes) {
                ids.add(node.id());
            }

            // IDs 24-47 excluding 43
            for (int i = 24; i <= 47; i++) {
                if (i == 43) {
                    assertFalse(ids.contains(i), "Resistance should NOT contain research ID 43 (Confederation-only)");
                } else {
                    assertTrue(ids.contains(i), "Resistance should contain research ID " + i);
                }
            }
            assertEquals(23, ids.size(), "Resistance should have exactly 23 unique IDs");
        }

        @Test
        @DisplayName("No duplicate research IDs within a faction")
        void noDuplicateResearchIdsWithinFaction() {
            // Confederation
            List<TechTree.TechTreeNode> confedNodes = techTree.getTechNodes(Faction.CONFEDERATION);
            Set<Integer> confedIds = new HashSet<>();
            for (TechTree.TechTreeNode node : confedNodes) {
                assertTrue(confedIds.add(node.id()),
                    "Duplicate research ID " + node.id() + " in Confederation");
            }

            // Resistance
            List<TechTree.TechTreeNode> resistanceNodes = techTree.getTechNodes(Faction.RESISTANCE);
            Set<Integer> resistanceIds = new HashSet<>();
            for (TechTree.TechTreeNode node : resistanceNodes) {
                assertTrue(resistanceIds.add(node.id()),
                    "Duplicate research ID " + node.id() + " in Resistance");
            }
        }

        @Test
        @DisplayName("R43 should only exist in Confederation, not Resistance")
        void r43ShouldOnlyExistInConfederation() {
            assertNotNull(techTree.getTechNode(Faction.CONFEDERATION, 43),
                "R43 should exist for Confederation");
            assertNull(techTree.getTechNode(Faction.RESISTANCE, 43),
                "R43 should NOT exist for Resistance");
        }
    }

    @Nested
    @DisplayName("Specific Tech Node")
    class SpecificTechNode {

        @Test
        @DisplayName("Should return Confederation tech node R0 by ID")
        void shouldReturnConfederationTechNodeR0() {
            TechTree.TechTreeNode node = techTree.getTechNode(Faction.CONFEDERATION, 0);
            assertNotNull(node);
            assertEquals(0, node.id());
            assertEquals("Energy Suit", node.name());
            assertEquals(Faction.CONFEDERATION, node.faction());
        }

        @Test
        @DisplayName("Should return Resistance tech node R24 by ID")
        void shouldReturnResistanceTechNodeR24() {
            TechTree.TechTreeNode node = techTree.getTechNode(Faction.RESISTANCE, 24);
            assertNotNull(node);
            assertEquals(24, node.id());
            assertEquals("Titanium Jacket", node.name());
            assertEquals(Faction.RESISTANCE, node.faction());
        }

        @Test
        @DisplayName("Should return null for non-existent tech ID")
        void shouldReturnNullForNonExistentTech() {
            assertNull(techTree.getTechNode(Faction.CONFEDERATION, 999));
        }

        @Test
        @DisplayName("Should return null when tech ID belongs to different faction")
        void shouldReturnNullWhenWrongFaction() {
            // R0 is Confederation, not Resistance
            assertNull(techTree.getTechNode(Faction.RESISTANCE, 0),
                "Should return null when tech ID belongs to different faction");
            // R24 is Resistance, not Confederation
            assertNull(techTree.getTechNode(Faction.CONFEDERATION, 24),
                "Should return null when tech ID belongs to different faction");
        }

        @Test
        @DisplayName("All Confederation nodes should have CONFEDERATION faction")
        void allConfederationNodesShouldHaveConfederationFaction() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                assertEquals(Faction.CONFEDERATION, node.faction(),
                    "Node " + node.id() + " should have CONFEDERATION faction");
            }
        }

        @Test
        @DisplayName("All Resistance nodes should have RESISTANCE faction")
        void allResistanceNodesShouldHaveResistanceFaction() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                assertEquals(Faction.RESISTANCE, node.faction(),
                    "Node " + node.id() + " should have RESISTANCE faction");
            }
        }
    }

    @Nested
    @DisplayName("Prerequisites")
    class Prerequisites {

        @Test
        @DisplayName("Should return prerequisites for tech")
        void shouldReturnPrerequisitesForTech() {
            // R1 requires R0
            List<Integer> prereqs = techTree.getPrerequisites(Faction.CONFEDERATION, 1);
            assertFalse(prereqs.isEmpty(), "R1 should have prerequisites");
            assertTrue(prereqs.contains(0), "R1 should require R0");
        }

        @Test
        @DisplayName("Should return multiple prerequisites for merge nodes")
        void shouldReturnMultiplePrerequisitesForMergeNodes() {
            // R8 requires both R6 and R7
            List<Integer> prereqs = techTree.getPrerequisites(Faction.CONFEDERATION, 8);
            assertEquals(2, prereqs.size(), "R8 should have 2 prerequisites");
            assertTrue(prereqs.contains(6), "R8 should require R6");
            assertTrue(prereqs.contains(7), "R8 should require R7");
        }

        @Test
        @DisplayName("Should return empty prerequisites for base techs")
        void shouldReturnEmptyPrerequisitesForBaseTechs() {
            // R0 is a base tech with no prerequisites
            assertTrue(techTree.getPrerequisites(Faction.CONFEDERATION, 0).isEmpty(),
                "R0 should have no prerequisites");
            // R4 is a base tech
            assertTrue(techTree.getPrerequisites(Faction.CONFEDERATION, 4).isEmpty(),
                "R4 should have no prerequisites");
            // R24 is a base Resistance tech
            assertTrue(techTree.getPrerequisites(Faction.RESISTANCE, 24).isEmpty(),
                "R24 should have no prerequisites");
        }

        @Test
        @DisplayName("Should return empty list for non-existent tech")
        void shouldReturnEmptyListForNonExistentTech() {
            assertTrue(techTree.getPrerequisites(Faction.CONFEDERATION, 999).isEmpty());
        }

        @Test
        @DisplayName("Every prerequisite references a valid research ID within the same faction")
        void everyPrerequisiteReferencesValidIdInSameFaction() {
            // Check Confederation
            List<TechTree.TechTreeNode> confedNodes = techTree.getTechNodes(Faction.CONFEDERATION);
            Set<Integer> confedIds = new HashSet<>();
            for (TechTree.TechTreeNode node : confedNodes) {
                confedIds.add(node.id());
            }
            for (TechTree.TechTreeNode node : confedNodes) {
                for (int prereq : node.prerequisites()) {
                    assertTrue(confedIds.contains(prereq),
                        "Confederation node " + node.id() + " prereq " + prereq
                            + " is not a valid Confederation research ID");
                }
            }

            // Check Resistance
            List<TechTree.TechTreeNode> resistNodes = techTree.getTechNodes(Faction.RESISTANCE);
            Set<Integer> resistIds = new HashSet<>();
            for (TechTree.TechTreeNode node : resistNodes) {
                resistIds.add(node.id());
            }
            for (TechTree.TechTreeNode node : resistNodes) {
                for (int prereq : node.prerequisites()) {
                    assertTrue(resistIds.contains(prereq),
                        "Resistance node " + node.id() + " prereq " + prereq
                            + " is not a valid Resistance research ID");
                }
            }
        }
    }

    @Nested
    @DisplayName("Unlocked Techs")
    class UnlockedTechs {

        @Test
        @DisplayName("Should return techs unlocked after completion")
        void shouldReturnUnlockedTechsAfterCompletion() {
            // R0 unlocks R1, R2, R3
            List<Integer> unlocked = techTree.getUnlockedTechs(Faction.CONFEDERATION, 0);
            assertEquals(3, unlocked.size(), "R0 should unlock 3 techs");
            assertTrue(unlocked.contains(1), "R0 should unlock R1");
            assertTrue(unlocked.contains(2), "R0 should unlock R2");
            assertTrue(unlocked.contains(3), "R0 should unlock R3");
        }

        @Test
        @DisplayName("Should return empty list for terminal tech")
        void shouldReturnEmptyListForTerminalTech() {
            // R14 is the last in the Confederation artillery chain
            assertTrue(techTree.getUnlockedTechs(Faction.CONFEDERATION, 14).isEmpty(),
                "R14 should not unlock further techs");
            // R38 is the last in the Resistance artillery chain
            assertTrue(techTree.getUnlockedTechs(Faction.RESISTANCE, 38).isEmpty(),
                "R38 should not unlock further techs");
        }

        @Test
        @DisplayName("Should return empty list for non-existent tech")
        void shouldReturnEmptyListForNonExistentTech() {
            assertTrue(techTree.getUnlockedTechs(Faction.CONFEDERATION, 999).isEmpty());
        }
    }

    @Nested
    @DisplayName("DAG Validation")
    class DagValidation {

        @Test
        @DisplayName("Prerequisites should form a valid DAG (no cycles) for Confederation")
        void prerequisitesShouldFormValidDagForConfederation() {
            assertNoCycles(Faction.CONFEDERATION);
        }

        @Test
        @DisplayName("Prerequisites should form a valid DAG (no cycles) for Resistance")
        void prerequisitesShouldFormValidDagForResistance() {
            assertNoCycles(Faction.RESISTANCE);
        }

        private void assertNoCycles(Faction faction) {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(faction);
            Map<Integer, List<Integer>> adjacency = new HashMap<>();
            Set<Integer> allIds = new HashSet<>();

            for (TechTree.TechTreeNode node : nodes) {
                allIds.add(node.id());
                adjacency.put(node.id(), node.prerequisites());
            }

            // DFS-based cycle detection
            // State: 0=unvisited, 1=in-progress, 2=completed
            Map<Integer, Integer> state = new HashMap<>();
            for (int id : allIds) {
                state.put(id, 0);
            }

            for (int id : allIds) {
                if (state.get(id) == 0) {
                    assertFalse(hasCycle(id, adjacency, state),
                        "Cycle detected in " + faction + " tech tree starting from node " + id);
                }
            }
        }

        private boolean hasCycle(int node, Map<Integer, List<Integer>> adjacency, Map<Integer, Integer> state) {
            state.put(node, 1); // in-progress
            List<Integer> prereqs = adjacency.getOrDefault(node, List.of());
            for (int prereq : prereqs) {
                int prereqState = state.getOrDefault(prereq, 2);
                if (prereqState == 1) {
                    return true; // back edge = cycle
                }
                if (prereqState == 0 && hasCycle(prereq, adjacency, state)) {
                    return true;
                }
            }
            state.put(node, 2); // completed
            return false;
        }
    }

    @Nested
    @DisplayName("Consistent Unlock Chains")
    class ConsistentUnlockChains {

        @Test
        @DisplayName("Confederation unlock chains should be consistent with prerequisites")
        void confederationUnlockChainsShouldBeConsistent() {
            assertConsistentUnlockChains(Faction.CONFEDERATION);
        }

        @Test
        @DisplayName("Resistance unlock chains should be consistent with prerequisites")
        void resistanceUnlockChainsShouldBeConsistent() {
            assertConsistentUnlockChains(Faction.RESISTANCE);
        }

        private void assertConsistentUnlockChains(Faction faction) {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(faction);

            for (TechTree.TechTreeNode node : nodes) {
                for (int unlockedId : node.unlocks()) {
                    List<Integer> prereqs = techTree.getPrerequisites(faction, unlockedId);
                    assertFalse(prereqs.isEmpty(),
                        "Unlocked tech " + unlockedId + " should have at least one prerequisite");
                    assertTrue(prereqs.contains(node.id()),
                        "Unlocked tech " + unlockedId + " should list " + node.id() + " as prerequisite");
                }
            }
        }

        @Test
        @DisplayName("Every unlock target should exist in the same faction")
        void everyUnlockTargetShouldExistInSameFaction() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                for (int unlockedId : node.unlocks()) {
                    assertNotNull(techTree.getTechNode(Faction.CONFEDERATION, unlockedId),
                        "Confederation node " + node.id() + " unlocks " + unlockedId
                            + " but that ID doesn't exist in Confederation");
                }
            }
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                for (int unlockedId : node.unlocks()) {
                    assertNotNull(techTree.getTechNode(Faction.RESISTANCE, unlockedId),
                        "Resistance node " + node.id() + " unlocks " + unlockedId
                            + " but that ID doesn't exist in Resistance");
                }
            }
        }
    }

    @Nested
    @DisplayName("Tech Node Properties")
    class TechNodeProperties {

        @Test
        @DisplayName("Should have valid costs for all techs")
        void shouldHaveValidCostsForAllTechs() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                assertTrue(node.cost() > 0,
                    "Tech " + node.id() + " should have positive cost, got " + node.cost());
            }
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                assertTrue(node.cost() > 0,
                    "Tech " + node.id() + " should have positive cost, got " + node.cost());
            }
        }

        @Test
        @DisplayName("Should have valid durations for all techs")
        void shouldHaveValidDurationsForAllTechs() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                assertTrue(node.duration() > 0,
                    "Tech " + node.id() + " should have positive duration, got " + node.duration());
            }
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                assertTrue(node.duration() > 0,
                    "Tech " + node.id() + " should have positive duration, got " + node.duration());
            }
        }

        @Test
        @DisplayName("Should have non-null names and descriptions")
        void shouldHaveNonNullNamesAndDescriptions() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                assertNotNull(node.name(), "Node " + node.id() + " should have a name");
                assertNotNull(node.description(), "Node " + node.id() + " should have a description");
                assertFalse(node.name().isBlank(), "Node " + node.id() + " name should not be blank");
            }
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                assertNotNull(node.name(), "Node " + node.id() + " should have a name");
                assertNotNull(node.description(), "Node " + node.id() + " should have a description");
                assertFalse(node.name().isBlank(), "Node " + node.id() + " name should not be blank");
            }
        }

        @Test
        @DisplayName("Costs should be within reasonable range (50-120)")
        void costsShouldBeWithinReasonableRange() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                assertTrue(node.cost() >= 50 && node.cost() <= 120,
                    "Tech " + node.id() + " cost " + node.cost() + " outside range 50-120");
            }
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                assertTrue(node.cost() >= 50 && node.cost() <= 120,
                    "Tech " + node.id() + " cost " + node.cost() + " outside range 50-120");
            }
        }

        @Test
        @DisplayName("Durations should be within reasonable range (300-600)")
        void durationsShouldBeWithinReasonableRange() {
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.CONFEDERATION)) {
                assertTrue(node.duration() >= 300 && node.duration() <= 600,
                    "Tech " + node.id() + " duration " + node.duration() + " outside range 300-600");
            }
            for (TechTree.TechTreeNode node : techTree.getTechNodes(Faction.RESISTANCE)) {
                assertTrue(node.duration() >= 300 && node.duration() <= 600,
                    "Tech " + node.id() + " duration " + node.duration() + " outside range 300-600");
            }
        }
    }

    @Nested
    @DisplayName("Confederation Tree Structure")
    class ConfederationTreeStructure {

        @Test
        @DisplayName("Confederation base techs (no prerequisites) should be R0, R4, R5, R15, R21")
        void confederationBaseTechsShouldBeCorrect() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.CONFEDERATION);
            List<Integer> baseIds = new ArrayList<>();
            for (TechTree.TechTreeNode node : nodes) {
                if (node.prerequisites().isEmpty()) {
                    baseIds.add(node.id());
                }
            }
            assertTrue(baseIds.contains(0), "R0 should be a base tech");
            assertTrue(baseIds.contains(4), "R4 should be a base tech");
            assertTrue(baseIds.contains(5), "R5 should be a base tech");
            assertTrue(baseIds.contains(15), "R15 should be a base tech");
            assertTrue(baseIds.contains(21), "R21 should be a base tech");
            assertEquals(5, baseIds.size(), "Confederation should have exactly 5 base techs");
        }

        @Test
        @DisplayName("R0 should unlock R1, R2, R3")
        void r0ShouldUnlockR1R2R3() {
            List<Integer> unlocked = techTree.getUnlockedTechs(Faction.CONFEDERATION, 0);
            assertTrue(unlocked.contains(1), "R0 should unlock R1");
            assertTrue(unlocked.contains(2), "R0 should unlock R2");
            assertTrue(unlocked.contains(3), "R0 should unlock R3");
        }

        @Test
        @DisplayName("R43 should exist and require R21")
        void r43ShouldExistAndRequireR21() {
            TechTree.TechTreeNode node = techTree.getTechNode(Faction.CONFEDERATION, 43);
            assertNotNull(node, "R43 should exist in Confederation tree");
            assertEquals(List.of(21), node.prerequisites(), "R43 should require R21");
        }
    }

    @Nested
    @DisplayName("Resistance Tree Structure")
    class ResistanceTreeStructure {

        @Test
        @DisplayName("Resistance base techs (no prerequisites) should be R24, R25, R39, R40")
        void resistanceBaseTechsShouldBeCorrect() {
            List<TechTree.TechTreeNode> nodes = techTree.getTechNodes(Faction.RESISTANCE);
            List<Integer> baseIds = new ArrayList<>();
            for (TechTree.TechTreeNode node : nodes) {
                if (node.prerequisites().isEmpty()) {
                    baseIds.add(node.id());
                }
            }
            assertTrue(baseIds.contains(24), "R24 should be a base tech");
            assertTrue(baseIds.contains(25), "R25 should be a base tech");
            assertTrue(baseIds.contains(39), "R39 should be a base tech");
            assertTrue(baseIds.contains(40), "R40 should be a base tech");
            assertEquals(4, baseIds.size(), "Resistance should have exactly 4 base techs");
        }

        @Test
        @DisplayName("R24 should unlock R26 and R27")
        void r24ShouldUnlockR26AndR27() {
            List<Integer> unlocked = techTree.getUnlockedTechs(Faction.RESISTANCE, 24);
            assertTrue(unlocked.contains(26), "R24 should unlock R26");
            assertTrue(unlocked.contains(27), "R24 should unlock R27");
        }

        @Test
        @DisplayName("R29 should require both R27 and R28 (merge point)")
        void r29ShouldRequireR27AndR28() {
            List<Integer> prereqs = techTree.getPrerequisites(Faction.RESISTANCE, 29);
            assertTrue(prereqs.contains(27), "R29 should require R27");
            assertTrue(prereqs.contains(28), "R29 should require R28");
        }

        @Test
        @DisplayName("R38 should be the final artillery research with no unlocks")
        void r38ShouldBeFinalArtillery() {
            TechTree.TechTreeNode node = techTree.getTechNode(Faction.RESISTANCE, 38);
            assertNotNull(node, "R38 should exist");
            assertTrue(node.unlocks().isEmpty(), "R38 should not unlock further techs");
        }

        @Test
        @DisplayName("R41 should require both R39 and R40 (economy merge)")
        void r41ShouldRequireR39AndR40() {
            List<Integer> prereqs = techTree.getPrerequisites(Faction.RESISTANCE, 41);
            assertTrue(prereqs.contains(39), "R41 should require R39");
            assertTrue(prereqs.contains(40), "R41 should require R40");
        }
    }
}
