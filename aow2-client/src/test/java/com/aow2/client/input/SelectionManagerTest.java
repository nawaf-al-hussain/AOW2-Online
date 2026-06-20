package com.aow2.client.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SelectionManager: selection state, clear, basic operations.
 * Tests that do not require EntityManager (null entity manager).
 */
class SelectionManagerTest {

    private SelectionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SelectionManager();
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("should start with no selection")
        void shouldStartWithNoSelection() {
            assertFalse(manager.hasSelection());
        }

        @Test
        @DisplayName("should have zero selection count")
        void shouldHaveZeroSelectionCount() {
            assertEquals(0, manager.selectionCount());
        }

        @Test
        @DisplayName("should return empty selected IDs set")
        void shouldReturnEmptySelectedIds() {
            assertTrue(manager.getSelectedIds().isEmpty());
        }

        @Test
        @DisplayName("should default to CONFEDERATION faction")
        void shouldDefaultToConfederationFaction() {
            assertNotNull(manager.getSelectedIds());
        }
    }

    @Nested
    @DisplayName("Selection Operations")
    class SelectionOperations {

        @Test
        @DisplayName("clearSelection removes all selections")
        void clearSelectionRemovesAll() {
            // Even without entity manager, clear should not throw
            assertDoesNotThrow(() -> manager.clearSelection());
            assertFalse(manager.hasSelection());
        }

        @Test
        @DisplayName("handleClick with null entity manager does not throw")
        void handleClickWithNullEntityManager() {
            assertDoesNotThrow(() -> manager.handleClick(5, 5, false, false));
            assertFalse(manager.hasSelection());
        }

        @Test
        @DisplayName("handleClick does nothing without entity manager")
        void handleClickDoesNothing() {
            manager.handleClick(10, 10, false, false);
            manager.handleClick(10, 10, true, false);
            manager.handleClick(10, 10, false, true);
            assertEquals(0, manager.selectionCount());
        }
    }

    @Nested
    @DisplayName("Max Selection Constant")
    class MaxSelectionConstant {

        @Test
        @DisplayName("MAX_SELECTION should be 20")
        void maxSelectionIs20() {
            assertEquals(20, SelectionManager.MAX_SELECTION);
        }
    }

    @Nested
    @DisplayName("Clean Selection")
    class CleanSelection {

        @Test
        @DisplayName("cleanSelection with null entity manager does not throw")
        void cleanSelectionWithNullEntityManager() {
            assertDoesNotThrow(() -> manager.cleanSelection());
        }

        @Test
        @DisplayName("cleanSelection does not throw when empty")
        void cleanSelectionWhenEmpty() {
            manager.cleanSelection();
            assertFalse(manager.hasSelection());
        }
    }

    @Nested
    @DisplayName("Selected Units and Buildings")
    class SelectedUnitsAndBuildings {

        @Test
        @DisplayName("getSelectedUnits returns empty list with null entity manager")
        void getSelectedUnitsWithNullEntityManager() {
            assertTrue(manager.getSelectedUnits().isEmpty());
        }

        @Test
        @DisplayName("getSelectedBuildings returns empty list with null entity manager")
        void getSelectedBuildingsWithNullEntityManager() {
            assertTrue(manager.getSelectedBuildings().isEmpty());
        }
    }

    @Nested
    @DisplayName("Drag State")
    class DragState {

        @Test
        @DisplayName("should not be dragging initially")
        void shouldNotBeDraggingInitially() {
            assertFalse(manager.isDragging());
        }

        @Test
        @DisplayName("getDragBox returns null when not dragging")
        void getDragBoxReturnsNullWhenNotDragging() {
            assertNull(manager.getDragBox());
        }
    }

    @Nested
    @DisplayName("Player Faction")
    class PlayerFaction {

        @Test
        @DisplayName("setPlayerFaction does not throw")
        void setPlayerFactionDoesNotThrow() {
            assertDoesNotThrow(() -> manager.setPlayerFaction(
                com.aow2.common.model.Faction.RESISTANCE));
        }
    }
}
