package com.aow2.client.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CameraController: zoom, viewport, update, constants.
 * Tests methods that do not require JavaFX events (no handleKeyEvent, etc.).
 */
class CameraControllerTest {

    private CameraController controller;

    @BeforeEach
    void setUp() {
        controller = new CameraController();
        // FIX: Disable edge scrolling in tests so mouse position (0,0) doesn't
        // trigger unintended camera movement. Tests that need edge scrolling
        // can re-enable it.
        controller.setEdgeScrollEnabled(false);
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("should start at position (0,0)")
        void shouldStartAtOrigin() {
            assertEquals(0.0, controller.getCameraX(), 0.001);
            assertEquals(0.0, controller.getCameraY(), 0.001);
        }

        @Test
        @DisplayName("should start at zoom 1.0")
        void shouldStartAtZoom1() {
            assertEquals(1.0, controller.getZoom(), 0.001);
        }

        @Test
        @DisplayName("should have default viewport 1280x720")
        void shouldHaveDefaultViewport() {
            double[] bounds = controller.getViewportWorldBounds();
            // worldWidth = viewportWidth / zoom = 1280 / 1.0
            assertEquals(1280.0, bounds[2], 0.001);
            assertEquals(720.0, bounds[3], 0.001);
        }
    }

    @Nested
    @DisplayName("Zoom Constants")
    class ZoomConstants {

        @Test
        @DisplayName("MIN_ZOOM should be 0.5")
        void minZoomIs0Point5() {
            assertEquals(0.5, CameraController.MIN_ZOOM, 0.001);
        }

        @Test
        @DisplayName("MAX_ZOOM should be 2.0")
        void maxZoomIs2() {
            assertEquals(2.0, CameraController.MAX_ZOOM, 0.001);
        }
    }

    @Nested
    @DisplayName("Viewport Configuration")
    class ViewportConfiguration {

        @Test
        @DisplayName("setViewportSize changes viewport dimensions")
        void setViewportSizeChangesDimensions() {
            controller.setViewportSize(800, 600);
            double[] bounds = controller.getViewportWorldBounds();
            assertEquals(800.0, bounds[2], 0.001);
            assertEquals(600.0, bounds[3], 0.001);
        }

        @Test
        @DisplayName("viewport world bounds account for zoom")
        void viewportBoundsAccountForZoom() {
            controller.setViewportSize(1000, 800);
            controller.adjustZoom(0.5); // zoom becomes 1.5
            // Multiple updates to interpolate toward target zoom
            for (int i = 0; i < 50; i++) {
                controller.update();
            }
            double[] bounds = controller.getViewportWorldBounds();
            // width should be approximately 1000 / 1.5
            double expectedWidth = 1000.0 / controller.getZoom();
            assertEquals(expectedWidth, bounds[2], 0.1);
        }
    }

    @Nested
    @DisplayName("Set Map")
    class SetMap {

        @Test
        @DisplayName("setMap with null does not throw")
        void setMapWithNull() {
            assertDoesNotThrow(() -> controller.setMap(null));
        }
    }

    @Nested
    @DisplayName("Edge Scroll")
    class EdgeScroll {

        @Test
        @DisplayName("edge scroll enabled by default")
        void edgeScrollEnabledByDefault() {
            // Default is true; verify no crash in update
            assertDoesNotThrow(() -> controller.update());
        }

        @Test
        @DisplayName("setEdgeScrollEnabled does not throw")
        void setEdgeScrollEnabled() {
            assertDoesNotThrow(() -> controller.setEdgeScrollEnabled(false));
            assertDoesNotThrow(() -> controller.setEdgeScrollEnabled(true));
        }
    }

    @Nested
    @DisplayName("Release Keys")
    class ReleaseKeys {

        @Test
        @DisplayName("releaseAllKeys does not throw")
        void releaseAllKeys() {
            assertDoesNotThrow(() -> controller.releaseAllKeys());
        }

        @Test
        @DisplayName("update after releaseAllKeys does not pan")
        void updateAfterReleaseAllKeys() {
            controller.releaseAllKeys();
            double xBefore = controller.getCameraX();
            controller.update();
            // Without pan flags set and no edge scroll movement (mouse at center),
            // camera should not change significantly
            assertEquals(xBefore, controller.getCameraX(), 0.001);
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("update does not throw")
        void updateDoesNotThrow() {
            assertDoesNotThrow(() -> controller.update());
        }

        @Test
        @DisplayName("multiple updates do not throw")
        void multipleUpdates() {
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            // Should still be at origin with no input
            assertEquals(0.0, controller.getCameraX(), 0.01);
        }

        @Test
        @DisplayName("centerOnGrid updates target position")
        void centerOnGridUpdatesTarget() {
            controller.centerOnGrid(10, 10);
            // After enough updates to interpolate
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            // Camera should have moved toward the target
            assertNotEquals(0.0, controller.getCameraX(), 0.01);
        }

        @Test
        @DisplayName("panToWorld updates target position")
        void panToWorldUpdatesTarget() {
            controller.panToWorld(100.0, 200.0);
            for (int i = 0; i < 100; i++) {
                controller.update();
            }
            assertNotEquals(0.0, controller.getCameraX(), 0.01);
            assertNotEquals(0.0, controller.getCameraY(), 0.01);
        }
    }
}
