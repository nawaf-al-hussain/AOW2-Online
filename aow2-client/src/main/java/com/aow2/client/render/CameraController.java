package com.aow2.client.render;

import com.aow2.core.world.GameMap;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages camera pan and zoom for the isometric game view.
 * <p>
 * Controls:
 * - Pan: WASD or arrow keys, or mouse edge scrolling
 * - Zoom: mouse wheel (0.5x to 2.0x range)
 * - Camera bounds are clamped to map edges
 * - Smooth camera interpolation for fluid movement
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Camera and viewport
 */
public class CameraController {

    private static final Logger LOG = LoggerFactory.getLogger(CameraController.class);

    /** Minimum zoom level. */
    public static final double MIN_ZOOM = 0.5;

    /** Maximum zoom level. */
    public static final double MAX_ZOOM = 2.0;

    /** Zoom step per scroll tick. */
    private static final double ZOOM_STEP = 0.1;

    /** Camera pan speed in pixels per frame. */
    private static final double PAN_SPEED = 8.0;

    /** Edge scroll activation zone in pixels from screen border. */
    private static final int EDGE_SCROLL_ZONE = 20;

    /** Smooth interpolation factor (0.0 = no movement, 1.0 = instant). */
    private static final double SMOOTH_FACTOR = 0.15;

    /** Current camera position (top-left of viewport in world coords). */
    private double cameraX;

    /** Current camera position (top-left of viewport in world coords). */
    private double cameraY;

    /** Target camera position for smooth interpolation. */
    private double targetX;

    /** Target camera position for smooth interpolation. */
    private double targetY;

    /** Current zoom level. */
    private double zoom;

    /** Target zoom level for smooth interpolation. */
    private double targetZoom;

    /** Viewport width in pixels. */
    private int viewportWidth;

    /** Viewport height in pixels. */
    private int viewportHeight;

    /** Whether each pan direction is currently active. */
    private boolean panUp;
    private boolean panDown;
    private boolean panLeft;
    private boolean panRight;

    /** Current mouse position for edge scrolling. */
    private double mouseX;
    private double mouseY;

    /** Whether edge scrolling is enabled. */
    private boolean edgeScrollEnabled;

    /** The game map for bounds calculation. */
    private GameMap map;

    /**
     * Constructs a new CameraController with default settings.
     */
    public CameraController() {
        this.cameraX = 0;
        this.cameraY = 0;
        this.targetX = 0;
        this.targetY = 0;
        this.zoom = 1.0;
        this.targetZoom = 1.0;
        this.viewportWidth = 1280;
        this.viewportHeight = 720;
        this.panUp = false;
        this.panDown = false;
        this.panLeft = false;
        this.panRight = false;
        this.mouseX = 0;
        this.mouseY = 0;
        this.edgeScrollEnabled = true;
        this.map = null;
    }

    /**
     * Sets the game map for bounds clamping.
     *
     * @param map the game map
     */
    public void setMap(GameMap map) {
        this.map = map;
    }

    /**
     * Sets the viewport dimensions.
     *
     * @param width  viewport width in pixels
     * @param height viewport height in pixels
     */
    public void setViewportSize(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    /**
     * Centers the camera on a specific grid position.
     * Accounts for the IsometricRenderer's offsetX/offsetY to ensure correct alignment.
     * FIX(H-22): Now reads the IsometricRenderer's centering offsets to prevent
     * camera misalignment when the renderer offsets the map to center it on screen.
     *
     * @param gx grid x coordinate
     * @param gy grid y coordinate
     * @param rendererOffsetX the IsometricRenderer's horizontal offset
     * @param rendererOffsetY the IsometricRenderer's vertical offset
     */
    public void centerOnGrid(int gx, int gy, double rendererOffsetX, double rendererOffsetY) {
        double sx = (gx - gy) * IsometricRenderer.TILE_HALF_WIDTH + rendererOffsetX;
        double sy = (gx + gy) * IsometricRenderer.TILE_HALF_HEIGHT + rendererOffsetY;
        targetX = -sx + viewportWidth / 2.0 / zoom;
        targetY = -sy + viewportHeight / 2.0 / zoom;
    }

    /**
     * Centers the camera on a specific grid position (backward-compatible, zero offsets).
     *
     * @param gx grid x coordinate
     * @param gy grid y coordinate
     */
    public void centerOnGrid(int gx, int gy) {
        centerOnGrid(gx, gy, 0, 0);
    }

    /**
     * Moves the camera target to focus on a screen position (e.g., from minimap click).
     *
     * @param worldX world X coordinate
     * @param worldY world Y coordinate
     */
    public void panToWorld(double worldX, double worldY) {
        targetX = -worldX + viewportWidth / 2.0 / zoom;
        targetY = -worldY + viewportHeight / 2.0 / zoom;
    }

    /**
     * Updates camera state each frame: applies smooth interpolation and clamps to map bounds.
     */
    public void update() {
        // Apply keyboard panning
        if (panUp) targetY += PAN_SPEED / zoom;
        if (panDown) targetY -= PAN_SPEED / zoom;
        if (panLeft) targetX += PAN_SPEED / zoom;
        if (panRight) targetX -= PAN_SPEED / zoom;

        // Apply edge scrolling
        if (edgeScrollEnabled) {
            if (mouseX < EDGE_SCROLL_ZONE) targetX += PAN_SPEED * 0.5 / zoom;
            if (mouseX > viewportWidth - EDGE_SCROLL_ZONE) targetX -= PAN_SPEED * 0.5 / zoom;
            if (mouseY < EDGE_SCROLL_ZONE) targetY += PAN_SPEED * 0.5 / zoom;
            if (mouseY > viewportHeight - EDGE_SCROLL_ZONE) targetY -= PAN_SPEED * 0.5 / zoom;
        }

        // Clamp to map bounds
        clampToMapBounds();

        // Smooth interpolation
        cameraX += (targetX - cameraX) * SMOOTH_FACTOR;
        cameraY += (targetY - cameraY) * SMOOTH_FACTOR;
        zoom += (targetZoom - zoom) * SMOOTH_FACTOR;
    }

    /**
     * Clamps the camera target position so the viewport stays within map bounds.
     * ASSUMPTION: Map world bounds are calculated from the isometric projection of the map dimensions.
     */
    private void clampToMapBounds() {
        if (map == null) {
            return;
        }

        // Calculate world bounds of the map
        double mapWorldWidth = (map.getWidth() + map.getHeight()) * IsometricRenderer.TILE_HALF_WIDTH;
        double mapWorldHeight = (map.getWidth() + map.getHeight()) * IsometricRenderer.TILE_HALF_HEIGHT;

        double minCamX = -mapWorldWidth * 0.3;
        double maxCamX = mapWorldWidth * 0.5;
        double minCamY = -mapWorldHeight * 0.1;
        double maxCamY = mapWorldHeight * 0.5;

        targetX = Math.clamp(targetX, minCamX, maxCamX);
        targetY = Math.clamp(targetY, minCamY, maxCamY);
    }

    /**
     * Handles keyboard events for camera panning.
     *
     * @param event the key event
     */
    public void handleKeyEvent(KeyEvent event) {
        boolean pressed = event.getEventType() == KeyEvent.KEY_PRESSED;

        switch (event.getCode()) {
            case W, UP    -> panUp = pressed;
            // FIX (F-10): Removed S, A, D from camera controls — they conflict with
            // the game command hotkeys (S=stop, A=attack-move, D=siege mode toggle).
            // Camera panning now uses W + arrow keys only.
            case DOWN     -> panDown = pressed;
            case LEFT     -> panLeft = pressed;
            case RIGHT    -> panRight = pressed;
            default -> { /* not a camera key */ }
        }
    }

    /**
     * Handles mouse movement for edge scrolling.
     *
     * @param event the mouse event
     */
    public void handleMouseMove(MouseEvent event) {
        mouseX = event.getSceneX();
        mouseY = event.getSceneY();
    }

    /**
     * Handles mouse scroll for zooming.
     *
     * @param event the scroll event
     */
    public void handleScroll(ScrollEvent event) {
        double delta = event.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP;
        adjustZoom(delta);
    }

    /**
     * Adjusts the zoom level by the given delta, clamped between MIN_ZOOM and MAX_ZOOM.
     * Can be called programmatically (e.g., from the map editor) as well as from scroll input.
     *
     * @param delta the zoom delta (positive to zoom in, negative to zoom out)
     */
    public void adjustZoom(double delta) {
        targetZoom = Math.clamp(targetZoom + delta, MIN_ZOOM, MAX_ZOOM);
    }

    /**
     * Gets the current camera X offset for rendering.
     *
     * @return camera X offset
     */
    public double getCameraX() {
        return cameraX;
    }

    /**
     * Gets the current camera Y offset for rendering.
     *
     * @return camera Y offset
     */
    public double getCameraY() {
        return cameraY;
    }

    /**
     * Gets the current zoom level.
     *
     * @return zoom factor
     */
    public double getZoom() {
        return zoom;
    }

    /**
     * Gets the viewport rectangle in world coordinates (for minimap display).
     *
     * @return [worldX, worldY, worldWidth, worldHeight]
     */
    public double[] getViewportWorldBounds() {
        double worldX = -cameraX;
        double worldY = -cameraY;
        double worldWidth = viewportWidth / zoom;
        double worldHeight = viewportHeight / zoom;
        return new double[]{worldX, worldY, worldWidth, worldHeight};
    }

    /**
     * Sets whether edge scrolling is enabled.
     *
     * @param enabled true to enable edge scrolling
     */
    public void setEdgeScrollEnabled(boolean enabled) {
        this.edgeScrollEnabled = enabled;
    }

    /**
     * Releases all pan directions (e.g., when focus is lost).
     */
    public void releaseAllKeys() {
        panUp = false;
        panDown = false;
        panLeft = false;
        panRight = false;
    }
}
