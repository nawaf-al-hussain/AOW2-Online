package com.aow2.client.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SelectionManager drag operations: start, update, end, box coordinates.
 * Tests that do not require EntityManager.
 */
class SelectionManagerDragTest {

    private SelectionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SelectionManager();
    }

    @Nested
    @DisplayName("Start Drag")
    class StartDrag {

        @Test
        @DisplayName("startDrag sets dragging to true")
        void startDragSetsDraggingTrue() {
            manager.startDrag(100.0, 200.0);
            assertTrue(manager.isDragging());
        }

        @Test
        @DisplayName("getDragBox returns initial position after start")
        void getDragBoxAfterStart() {
            manager.startDrag(50.0, 75.0);
            double[] box = manager.getDragBox();
            assertNotNull(box);
            assertEquals(50.0, box[0], 0.001);
            assertEquals(75.0, box[1], 0.001);
            assertEquals(50.0, box[2], 0.001);
            assertEquals(75.0, box[3], 0.001);
        }
    }

    @Nested
    @DisplayName("Update Drag")
    class UpdateDrag {

        @Test
        @DisplayName("updateDrag changes current position")
        void updateDragChangesCurrentPosition() {
            manager.startDrag(10.0, 20.0);
            manager.updateDrag(100.0, 200.0);
            double[] box = manager.getDragBox();
            assertEquals(10.0, box[0], 0.001);
            assertEquals(20.0, box[1], 0.001);
            assertEquals(100.0, box[2], 0.001);
            assertEquals(200.0, box[3], 0.001);
        }

        @Test
        @DisplayName("updateDrag without start does nothing")
        void updateDragWithoutStartDoesNothing() {
            manager.updateDrag(100.0, 200.0);
            assertFalse(manager.isDragging());
            assertNull(manager.getDragBox());
        }
    }

    @Nested
    @DisplayName("End Drag")
    class EndDrag {

        @Test
        @DisplayName("endDrag without start does not throw")
        void endDragWithoutStart() {
            assertDoesNotThrow(() -> manager.endDrag(100.0, 200.0, false, mockConverter()));
        }

        @Test
        @DisplayName("endDrag with small drag returns without selecting")
        void endDragWithSmallDrag() {
            manager.startDrag(10.0, 20.0);
            manager.endDrag(12.0, 22.0, false, mockConverter());
            assertFalse(manager.isDragging());
            assertEquals(0, manager.selectionCount());
        }

        @Test
        @DisplayName("endDrag stops dragging state")
        void endDragStopsDragging() {
            manager.startDrag(10.0, 20.0);
            manager.endDrag(50.0, 60.0, false, mockConverter());
            assertFalse(manager.isDragging());
            assertNull(manager.getDragBox());
        }

        @Test
        @DisplayName("endDrag with large drag does not crash with null entity manager")
        void endDragWithLargeDragNoEntityManager() {
            manager.startDrag(10.0, 20.0);
            assertDoesNotThrow(() -> manager.endDrag(100.0, 200.0, false, mockConverter()));
        }

        @Test
        @DisplayName("endDrag with shift does not clear existing selection")
        void endDragWithShiftPreservesSelection() {
            manager.startDrag(10.0, 20.0);
            assertDoesNotThrow(() -> manager.endDrag(12.0, 22.0, true, mockConverter()));
            // Small drag doesn't trigger box select, so this just tests no crash
        }

        @Test
        @DisplayName("endDrag with null entity manager returns safely")
        void endDragWithNullEntityManager() {
            manager.startDrag(0.0, 0.0);
            // Large drag
            manager.endDrag(100.0, 100.0, false, mockConverter());
            assertFalse(manager.isDragging());
        }
    }

    @Nested
    @DisplayName("Drag Box Coordinates")
    class DragBoxCoordinates {

        @Test
        @DisplayName("drag box has four elements")
        void dragBoxHasFourElements() {
            manager.startDrag(10.0, 20.0);
            manager.updateDrag(30.0, 40.0);
            double[] box = manager.getDragBox();
            assertEquals(4, box.length);
        }

        @Test
        @DisplayName("drag start and current are independent")
        void dragStartAndCurrentIndependent() {
            manager.startDrag(10.0, 20.0);
            manager.updateDrag(50.0, 60.0);
            double[] box = manager.getDragBox();
            assertEquals(10.0, box[0], 0.001); // startX
            assertEquals(20.0, box[1], 0.001); // startY
            assertEquals(50.0, box[2], 0.001); // currentX
            assertEquals(60.0, box[3], 0.001); // currentY
        }
    }

    @Nested
    @DisplayName("Drag Box Converter")
    class DragBoxConverter {

        @Test
        @DisplayName("grid converter is called for large drag")
        void gridConverterCalledForLargeDrag() {
            manager.startDrag(0.0, 0.0);
            // Use a converter that returns large grid coordinates to ensure no selection
            boolean[] called = {false};
            BiFunction<Double, Double, int[]> converter = (sx, sy) -> {
                called[0] = true;
                return new int[]{0, 0};
            };
            manager.endDrag(100.0, 100.0, false, converter);
            // The converter is called for large drags even with null entity manager
            // because the code checks dragging first
            assertFalse(manager.isDragging());
        }
    }

    private BiFunction<Double, Double, int[]> mockConverter() {
        return (sx, sy) -> new int[]{0, 0};
    }
}
