package com.aow2.client.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CameraController zoom logic: adjustZoom, clamping, smooth interpolation.
 */
class CameraControllerZoomTest {

    private CameraController controller;

    @BeforeEach
    void setUp() {
        controller = new CameraController();
    }

    @Nested
    @DisplayName("Adjust Zoom")
    class AdjustZoom {

        @Test
        @DisplayName("positive delta increases target zoom")
        void positiveDeltaIncreasesZoom() {
            controller.adjustZoom(0.1);
            // Zoom interpolates toward target; after many updates, it should be close
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            assertTrue(controller.getZoom() > 1.0);
        }

        @Test
        @DisplayName("negative delta decreases target zoom")
        void negativeDeltaDecreasesZoom() {
            controller.adjustZoom(-0.1);
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            assertTrue(controller.getZoom() < 1.0);
        }

        @Test
        @DisplayName("zoom clamps at MIN_ZOOM")
        void zoomClampsAtMin() {
            for (int i = 0; i < 20; i++) {
                controller.adjustZoom(-0.5);
            }
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            assertEquals(CameraController.MIN_ZOOM, controller.getZoom(), 0.01);
        }

        @Test
        @DisplayName("zoom clamps at MAX_ZOOM")
        void zoomClampsAtMax() {
            for (int i = 0; i < 20; i++) {
                controller.adjustZoom(0.5);
            }
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            assertEquals(CameraController.MAX_ZOOM, controller.getZoom(), 0.01);
        }

        @Test
        @DisplayName("zero delta does not change zoom")
        void zeroDeltaNoChange() {
            double before = controller.getZoom();
            controller.adjustZoom(0.0);
            for (int i = 0; i < 10; i++) {
                controller.update();
            }
            assertEquals(before, controller.getZoom(), 0.001);
        }
    }

    @Nested
    @DisplayName("Zoom and Viewport Interaction")
    class ZoomViewportInteraction {

        @Test
        @DisplayName("viewport world bounds shrink when zooming in")
        void worldBoundsShrinkOnZoomIn() {
            controller.setViewportSize(1000, 800);

            double[] boundsBefore = controller.getViewportWorldBounds();
            controller.adjustZoom(0.5);
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            double[] boundsAfter = controller.getViewportWorldBounds();

            assertTrue(boundsAfter[2] < boundsBefore[2], "World width should shrink");
            assertTrue(boundsAfter[3] < boundsBefore[3], "World height should shrink");
        }

        @Test
        @DisplayName("viewport world bounds expand when zooming out")
        void worldBoundsExpandOnZoomOut() {
            controller.setViewportSize(1000, 800);

            double[] boundsBefore = controller.getViewportWorldBounds();
            controller.adjustZoom(-0.5);
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            double[] boundsAfter = controller.getViewportWorldBounds();

            assertTrue(boundsAfter[2] > boundsBefore[2], "World width should expand");
            assertTrue(boundsAfter[3] > boundsBefore[3], "World height should expand");
        }
    }

    @Nested
    @DisplayName("Smooth Interpolation")
    class SmoothInterpolation {

        @Test
        @DisplayName("zoom interpolation converges over time")
        void zoomInterpolationConverges() {
            controller.adjustZoom(0.5); // target zoom = 1.5
            double previousZoom = controller.getZoom();
            boolean converged = false;

            for (int i = 0; i < 200; i++) {
                controller.update();
                double currentZoom = controller.getZoom();
                // Once close enough to target, check convergence
                if (Math.abs(currentZoom - 1.5) < 0.01) {
                    converged = true;
                    break;
                }
                // Each step should bring us closer
                assertTrue(currentZoom >= previousZoom - 0.001 || currentZoom <= 1.5);
                previousZoom = currentZoom;
            }

            assertTrue(converged, "Zoom should converge toward target within 200 updates");
        }

        @Test
        @DisplayName("zoom stays within bounds during interpolation")
        void zoomStaysWithinBoundsDuringInterpolation() {
            controller.adjustZoom(1.0); // target = 2.0 (MAX)
            for (int i = 0; i < 50; i++) {
                controller.update();
                double zoom = controller.getZoom();
                assertTrue(zoom >= CameraController.MIN_ZOOM - 0.001,
                    "Zoom should not go below MIN_ZOOM");
                assertTrue(zoom <= CameraController.MAX_ZOOM + 0.001,
                    "Zoom should not go above MAX_ZOOM");
            }
        }
    }

    @Nested
    @DisplayName("Center On Grid with Zoom")
    class CenterOnGridWithZoom {

        @Test
        @DisplayName("centerOnGrid at different zoom levels does not throw")
        void centerOnGridAtDifferentZooms() {
            controller.adjustZoom(0.3);
            for (int i = 0; i < 50; i++) controller.update();

            assertDoesNotThrow(() -> controller.centerOnGrid(5, 5));

            controller.adjustZoom(-0.3);
            for (int i = 0; i < 50; i++) controller.update();

            assertDoesNotThrow(() -> controller.centerOnGrid(10, 10));
        }
    }
}
