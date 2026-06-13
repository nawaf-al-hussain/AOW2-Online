package com.aow2.client.render;

import com.aow2.common.model.TerrainType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a minimap in the corner of the screen.
 * <p>
 * Features:
 * - 200x150 pixel minimap panel
 * - Shows terrain colors (simplified)
 * - Shows unit positions as dots
 * - Shows camera viewport rectangle
 * - Click on minimap to move camera
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Minimap and viewport
 */
public class MinimapRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(MinimapRenderer.class);

    /** Minimap width in pixels. */
    public static final int MINIMAP_WIDTH = 200;

    /** Minimap height in pixels. */
    public static final int MINIMAP_HEIGHT = 150;

    /** Minimap border width. */
    private static final double BORDER_WIDTH = 2.0;

    /** Minimap background color. */
    private static final Color BG_COLOR = Color.rgb(20, 20, 30, 0.85);

    /** Minimap border color. */
    private static final Color BORDER_COLOR = Color.rgb(180, 160, 100);

    /** Viewport rectangle color. */
    private static final Color VIEWPORT_COLOR = Color.rgb(255, 255, 255, 0.7);

    /** The game map. */
    private GameMap map;

    /** The entity manager. */
    private EntityManager entityManager;

    /** The minimap canvas. */
    private Canvas minimapCanvas;

    /** The container pane for the minimap. */
    private Pane container;

    /** Scale factors from map coordinates to minimap coordinates. */
    private double scaleX;
    private double scaleY;

    /** Callback for camera movement when clicking the minimap. */
    private Runnable cameraUpdateCallback;

    /**
     * Constructs a new MinimapRenderer.
     */
    public MinimapRenderer() {
        this.minimapCanvas = new Canvas(MINIMAP_WIDTH, MINIMAP_HEIGHT);
        this.container = new StackPane();
        this.container.getChildren().add(minimapCanvas);
        this.container.setStyle("-fx-border-color: rgb(180,160,100); -fx-border-width: 2;");
        this.container.setMaxSize(MINIMAP_WIDTH, MINIMAP_HEIGHT);
        this.container.setMinSize(MINIMAP_WIDTH, MINIMAP_HEIGHT);

        // Mouse click handler for camera navigation
        minimapCanvas.setOnMouseClicked(this::handleClick);
        minimapCanvas.setOnMouseDragged(this::handleClick);
    }

    /**
     * Sets the game map for minimap rendering.
     *
     * @param map the game map
     */
    public void setMap(GameMap map) {
        this.map = map;
        if (map != null) {
            this.scaleX = (double) MINIMAP_WIDTH / map.getWidth();
            this.scaleY = (double) MINIMAP_HEIGHT / map.getHeight();
            LOG.info("MinimapRenderer map set: {}x{}, scale: {:.2f}x{:.2f}",
                map.getWidth(), map.getHeight(), scaleX, scaleY);
        }
    }

    /**
     * Sets the entity manager for showing unit/building positions.
     *
     * @param entityManager the entity manager
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Sets a callback that is invoked when the minimap is clicked (to update camera).
     *
     * @param callback the camera update callback
     */
    public void setCameraUpdateCallback(Runnable callback) {
        this.cameraUpdateCallback = callback;
    }

    /**
     * Gets the minimap container pane for adding to the scene graph.
     *
     * @return the container pane
     */
    public Pane getContainer() {
        return container;
    }

    /**
     * Gets the last clicked minimap position converted to grid coordinates.
     *
     * @return [gridX, gridY] or null if no click occurred
     */
    public int[] getLastClickGrid() {
        return lastClickGrid;
    }

    /** Stores the last click position in grid coordinates. */
    private int[] lastClickGrid;

    /**
     * Renders the minimap content.
     *
     * @param cameraController the camera controller for viewport rectangle
     */
    public void render(CameraController cameraController) {
        if (map == null) {
            return;
        }

        GraphicsContext gc = minimapCanvas.getGraphicsContext2D();

        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, MINIMAP_WIDTH, MINIMAP_HEIGHT);

        // Terrain tiles
        renderTerrain(gc);

        // Entity positions
        renderEntities(gc);

        // Camera viewport rectangle
        renderViewport(gc, cameraController);

        // Border
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(BORDER_WIDTH);
        gc.strokeRect(0, 0, MINIMAP_WIDTH, MINIMAP_HEIGHT);
    }

    /**
     * Renders the terrain tiles on the minimap.
     *
     * @param gc graphics context
     */
    private void renderTerrain(GraphicsContext gc) {
        double tileW = Math.max(scaleX, 1);
        double tileH = Math.max(scaleY, 1);

        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                TerrainType terrain = map.getTile(x, y);
                if (terrain != null && terrain != TerrainType.GRASS) {
                    // Only draw non-grass tiles (grass is the background)
                    Color color = IsometricRenderer.terrainColor(terrain).darker();
                    gc.setFill(color);
                    gc.fillRect(x * scaleX, y * scaleY, tileW, tileH);
                }
            }
        }
    }

    /**
     * Renders entity positions as colored dots on the minimap.
     *
     * @param gc graphics context
     */
    private void renderEntities(GraphicsContext gc) {
        if (entityManager == null) {
            return;
        }

        // Buildings as small squares
        for (Building building : entityManager.getAllBuildings()) {
            if (building.isAlive()) {
                Color color = EntityRenderer.factionColor(building.getFaction());
                gc.setFill(color);
                double bx = building.getPosition().x() * scaleX;
                double by = building.getPosition().y() * scaleY;
                gc.fillRect(bx - 1.5, by - 1.5, 3, 3);
            }
        }

        // Units as dots
        for (Unit unit : entityManager.getAllUnits()) {
            if (unit.isAlive()) {
                Color color = EntityRenderer.factionColor(unit.getFaction()).brighter();
                gc.setFill(color);
                double ux = unit.getPosition().x() * scaleX;
                double uy = unit.getPosition().y() * scaleY;
                gc.fillOval(ux - 1, uy - 1, 2, 2);
            }
        }
    }

    /**
     * Renders the camera viewport rectangle on the minimap.
     *
     * @param gc               graphics context
     * @param cameraController the camera controller
     */
    private void renderViewport(GraphicsContext gc, CameraController cameraController) {
        // ASSUMPTION: The viewport rectangle is calculated from the camera position and zoom.
        // We approximate by showing a rectangle centered on where the camera is pointing.
        double[] bounds = cameraController.getViewportWorldBounds();

        // Convert world bounds to grid coordinates, then to minimap coordinates
        double centerWorldX = bounds[0] + bounds[2] / 2;
        double centerWorldY = bounds[1] + bounds[3] / 2;

        // Inverse isometric: approximate grid position from world center
        double approxGridX = (centerWorldX / IsometricRenderer.TILE_HALF_WIDTH
            + centerWorldY / IsometricRenderer.TILE_HALF_HEIGHT) / 2.0;
        double approxGridY = (centerWorldY / IsometricRenderer.TILE_HALF_HEIGHT
            - centerWorldX / IsometricRenderer.TILE_HALF_WIDTH) / 2.0;

        // Viewport size in grid cells (approximate)
        double vpGridW = bounds[2] / (IsometricRenderer.TILE_HALF_WIDTH * 2);
        double vpGridH = bounds[3] / (IsometricRenderer.TILE_HALF_HEIGHT * 2);

        double vpMinimapX = approxGridX * scaleX - vpGridW * scaleX / 2;
        double vpMinimapY = approxGridY * scaleY - vpGridH * scaleY / 2;
        double vpMinimapW = vpGridW * scaleX;
        double vpMinimapH = vpGridH * scaleY;

        gc.setStroke(VIEWPORT_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeRect(
            Math.max(0, vpMinimapX),
            Math.max(0, vpMinimapY),
            Math.min(vpMinimapW, MINIMAP_WIDTH),
            Math.min(vpMinimapH, MINIMAP_HEIGHT)
        );
    }

    /**
     * Handles mouse clicks on the minimap to move the camera.
     *
     * @param event the mouse event
     */
    private void handleClick(MouseEvent event) {
        if (map == null) {
            return;
        }

        double clickX = event.getX();
        double clickY = event.getY();

        // Convert minimap coordinates to grid coordinates
        int gridX = (int) (clickX / scaleX);
        int gridY = (int) (clickY / scaleY);
        gridX = Math.clamp(gridX, 0, map.getWidth() - 1);
        gridY = Math.clamp(gridY, 0, map.getHeight() - 1);

        lastClickGrid = new int[]{gridX, gridY};
        LOG.debug("Minimap click at ({}, {}) -> grid ({}, {})", clickX, clickY, gridX, gridY);

        if (cameraUpdateCallback != null) {
            cameraUpdateCallback.run();
        }
    }

    /**
     * Returns the grid position from a minimap click.
     * Used by the CameraController to center the camera on the clicked location.
     *
     * @param clickX minimap X pixel
     * @param clickY minimap Y pixel
     * @return [gridX, gridY]
     */
    public int[] minimapToGrid(double clickX, double clickY) {
        if (map == null) {
            return new int[]{0, 0};
        }
        int gridX = (int) (clickX / scaleX);
        int gridY = (int) (clickY / scaleY);
        return new int[]{
            Math.clamp(gridX, 0, map.getWidth() - 1),
            Math.clamp(gridY, 0, map.getHeight() - 1)
        };
    }
}
