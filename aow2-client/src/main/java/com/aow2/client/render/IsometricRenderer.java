package com.aow2.client.render;

import com.aow2.common.model.TerrainType;
import com.aow2.core.world.GameMap;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the game world in isometric perspective using JavaFX Canvas.
 * The original game uses a top-down isometric view with 30x20 pixel tile dimensions.
 * <p>
 * Coordinate transform from grid (x,y) to screen (sx,sy):
 * <pre>
 *   sx = (x - y) * TILE_HALF_WIDTH + offsetX
 *   sy = (x + y) * TILE_HALF_HEIGHT + offsetY
 * </pre>
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Map System (isometric tile rendering)
 */
public class IsometricRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(IsometricRenderer.class);

    /** Half the width of a diamond tile. Full width = 30px. REF: original tile dimensions */
    public static final int TILE_HALF_WIDTH = 15;

    /** Half the height of a diamond tile. Full height = 20px. REF: original tile dimensions */
    public static final int TILE_HALF_HEIGHT = 10;

    /** Default horizontal offset to center the map on screen. */
    private double offsetX;

    /** Default vertical offset to center the map on screen. */
    private double offsetY;

    /** The game map to render. */
    private GameMap map;

    /**
     * Constructs a new IsometricRenderer.
     * Initial offsets are set to 0; they should be configured via {@link #centerOnMap(int, int)}.
     */
    public IsometricRenderer() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.map = null;
    }

    /**
     * Sets the game map to render.
     *
     * @param map the game map
     */
    public void setMap(GameMap map) {
        this.map = map;
        LOG.info("IsometricRenderer map set: {}x{}", map.getWidth(), map.getHeight());
    }

    /**
     * Centers the camera on the map based on the viewport dimensions.
     *
     * @param viewportWidth  viewport width in pixels
     * @param viewportHeight viewport height in pixels
     */
    public void centerOnMap(int viewportWidth, int viewportHeight) {
        if (map == null) {
            return;
        }
        // The center tile of the map in screen coordinates (before offset)
        int centerX = map.getWidth() / 2;
        int centerY = map.getHeight() / 2;
        double screenCenterX = gridToScreenX(centerX, centerY);
        double screenCenterY = gridToScreenY(centerX, centerY);
        offsetX = viewportWidth / 2.0 - screenCenterX;
        offsetY = viewportHeight / 4.0 - screenCenterY;
    }

    /**
     * Converts grid coordinates to screen X coordinate (without camera offset).
     *
     * @param gx grid x
     * @param gy grid y
     * @return screen x position
     */
    public double gridToScreenX(int gx, int gy) {
        return (gx - gy) * TILE_HALF_WIDTH + offsetX;
    }

    /**
     * Converts grid coordinates to screen Y coordinate (without camera offset).
     *
     * @param gx grid x
     * @param gy grid y
     * @return screen y position
     */
    public double gridToScreenY(int gx, int gy) {
        return (gx + gy) * TILE_HALF_HEIGHT + offsetY;
    }

    /**
     * Converts screen coordinates to grid coordinates using inverse isometric transform.
     *
     * @param sx screen x
     * @param sy screen y
     * @return grid position as [x, y]
     */
    public int[] screenToGrid(double sx, double sy) {
        double adjustedX = sx - offsetX;
        double adjustedY = sy - offsetY;
        double gx = (adjustedX / TILE_HALF_WIDTH + adjustedY / TILE_HALF_HEIGHT) / 2.0;
        double gy = (adjustedY / TILE_HALF_HEIGHT - adjustedX / TILE_HALF_WIDTH) / 2.0;
        return new int[]{(int) Math.floor(gx), (int) Math.floor(gy)};
    }

    /**
     * Renders the entire map terrain.
     *
     * @param gc           the graphics context to draw on
     * @param cameraOffsetX camera horizontal offset for panning
     * @param cameraOffsetY camera vertical offset for panning
     * @param zoom         zoom scale factor
     */
    public void render(GraphicsContext gc, double cameraOffsetX, double cameraOffsetY, double zoom) {
        if (map == null) {
            return;
        }

        gc.save();
        gc.translate(cameraOffsetX, cameraOffsetY);
        gc.scale(zoom, zoom);

        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                TerrainType terrain = map.getTile(x, y);
                if (terrain != null) {
                    renderTile(gc, x, y, terrain);
                }
            }
        }

        // Draw grid lines for debugging (subtle)
        renderGridOverlay(gc);

        gc.restore();
    }

    /**
     * Renders a single isometric diamond tile at the given grid position.
     *
     * @param gc      graphics context
     * @param gx      grid x
     * @param gy      grid y
     * @param terrain terrain type (determines fill color)
     */
    private void renderTile(GraphicsContext gc, int gx, int gy, TerrainType terrain) {
        double sx = gridToScreenX(gx, gy);
        double sy = gridToScreenY(gx, gy);

        Color fillColor = terrainColor(terrain);
        Color strokeColor = fillColor.darker();

        // Diamond shape: top, right, bottom, left
        double[] xPoints = {
            sx,                  // top
            sx + TILE_HALF_WIDTH,  // right
            sx,                  // bottom
            sx - TILE_HALF_WIDTH   // left
        };
        double[] yPoints = {
            sy - TILE_HALF_HEIGHT,  // top
            sy,                     // right
            sy + TILE_HALF_HEIGHT,  // bottom
            sy                      // left
        };

        gc.setFill(fillColor);
        gc.fillPolygon(xPoints, yPoints, 4);
        gc.setStroke(strokeColor);
        gc.setLineWidth(0.5);
        gc.strokePolygon(xPoints, yPoints, 4);
    }

    /**
     * Renders a subtle grid overlay on the isometric tiles for reference.
     *
     * @param gc graphics context
     */
    private void renderGridOverlay(GraphicsContext gc) {
        gc.setStroke(Color.rgb(0, 0, 0, 0.1));
        gc.setLineWidth(0.3);

        for (int y = 0; y <= map.getHeight(); y++) {
            double startX = gridToScreenX(0, y);
            double startY = gridToScreenY(0, y);
            double endX = gridToScreenX(map.getWidth(), y);
            double endY = gridToScreenY(map.getWidth(), y);
            gc.strokeLine(startX, startY, endX, endY);
        }

        for (int x = 0; x <= map.getWidth(); x++) {
            double startX = gridToScreenX(x, 0);
            double startY = gridToScreenY(x, 0);
            double endX = gridToScreenX(x, map.getHeight());
            double endY = gridToScreenY(x, map.getHeight());
            gc.strokeLine(startX, startY, endX, endY);
        }
    }

    /**
     * Returns the JavaFX color associated with a terrain type.
     * Uses placeholder colors since sprite assets are not yet available.
     *
     * @param terrain the terrain type
     * @return the associated color
     */
    public static Color terrainColor(TerrainType terrain) {
        return switch (terrain) {
            case DEEP_WATER    -> Color.rgb(30, 60, 180);      // deep blue
            case SHALLOW_WATER -> Color.rgb(70, 130, 210);     // light blue
            case SAND          -> Color.rgb(210, 180, 120);     // tan
            case GRASS         -> Color.rgb(76, 153, 0);        // green
            case ROAD          -> Color.rgb(140, 140, 140);     // gray
            case DIRT          -> Color.rgb(160, 140, 100);     // brown-gray
            case HILLS         -> Color.rgb(90, 130, 60);       // olive green
            case FOREST        -> Color.rgb(34, 100, 34);       // dark green
            case BRIDGE        -> Color.rgb(139, 90, 43);       // brown
            case MOUNTAIN      -> Color.rgb(101, 67, 33);       // dark brown
            case SWAMP         -> Color.rgb(60, 100, 60);       // murky green
            case SNOW          -> Color.rgb(230, 240, 250);     // white-blue
            case ICE           -> Color.rgb(180, 220, 240);     // light blue
            case RUINS         -> Color.rgb(90, 90, 90);        // dark gray
            case RESOURCE_DEPOSIT -> Color.rgb(200, 170, 50);   // gold
        };
    }

    /**
     * Gets the horizontal offset.
     *
     * @return the X offset
     */
    public double getOffsetX() {
        return offsetX;
    }

    /**
     * Gets the vertical offset.
     *
     * @return the Y offset
     */
    public double getOffsetY() {
        return offsetY;
    }

    /**
     * Sets the rendering offset (used by camera system for panning).
     *
     * @param offsetX horizontal offset
     * @param offsetY vertical offset
     */
    public void setOffset(double offsetX, double offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }
}
