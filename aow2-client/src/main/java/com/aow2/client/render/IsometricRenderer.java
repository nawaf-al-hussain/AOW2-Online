package com.aow2.client.render;

import com.aow2.common.model.TerrainType;
import com.aow2.core.world.GameMap;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
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
 * <p>
 * When a SpriteManager is available, terrain tiles are rendered using sprite images
 * instead of colored diamonds for improved visual quality.
 * <p>
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

    /** Whether the debug grid overlay is enabled. Default false. */
    private boolean debugGridEnabled = false;

    /** The game map to render. */
    private GameMap map;

    /** The sprite manager for terrain sprite images (nullable). */
    private SpriteManager spriteManager;

    /**
     * Constructs a new IsometricRenderer.
     * Initial offsets are set to 0; they should be configured via {@link #centerOnMap(int, int)}.
     */
    public IsometricRenderer() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.map = null;
        this.spriteManager = null;
    }

    /**
     * Sets the SpriteManager for terrain sprite rendering.
     * When set, terrain tiles will use sprite images instead of colored diamonds.
     *
     * @param spriteManager the sprite manager instance
     */
    public void setSpriteManager(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        LOG.info("IsometricRenderer SpriteManager set: initialized={}",
            spriteManager != null && spriteManager.isInitialized());
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
     * Uses sprite images when SpriteManager is available, otherwise falls back
     * to colored diamonds.
     *
     * @param gc            the graphics context to draw on
     * @param cameraOffsetX camera horizontal offset for panning
     * @param cameraOffsetY camera vertical offset for panning
     * @param zoom          zoom scale factor
     */
    public void render(GraphicsContext gc, double cameraOffsetX, double cameraOffsetY, double zoom) {
        if (map == null) {
            return;
        }

        gc.save();
        gc.translate(cameraOffsetX, cameraOffsetY);
        gc.scale(zoom, zoom);

        // Viewport culling: only render tiles within the visible camera bounds
        double viewportW = gc.getCanvas().getWidth();
        double viewportH = gc.getCanvas().getHeight();
        int[] gtl = screenToGrid((0 - cameraOffsetX) / zoom, (0 - cameraOffsetY) / zoom);
        int[] gtr = screenToGrid((viewportW - cameraOffsetX) / zoom, (0 - cameraOffsetY) / zoom);
        int[] gbl = screenToGrid((0 - cameraOffsetX) / zoom, (viewportH - cameraOffsetY) / zoom);
        int[] gbr = screenToGrid((viewportW - cameraOffsetX) / zoom, (viewportH - cameraOffsetY) / zoom);
        int minY = Math.max(0, Math.min(Math.min(gtl[1], gtr[1]), Math.min(gbl[1], gbr[1])) - 1);
        int maxY = Math.min(map.getHeight() - 1, Math.max(Math.max(gtl[1], gtr[1]), Math.max(gbl[1], gbr[1])) + 1);
        int minX = Math.max(0, Math.min(Math.min(gtl[0], gtr[0]), Math.min(gbl[0], gbr[0])) - 1);
        int maxX = Math.min(map.getWidth() - 1, Math.max(Math.max(gtl[0], gtr[0]), Math.max(gbl[0], gbr[0])) + 1);

        boolean useSprites = spriteManager != null && spriteManager.isInitialized();

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TerrainType terrain = map.getTile(x, y);
                if (terrain != null) {
                    if (useSprites) {
                        renderTileWithSprite(gc, x, y, terrain);
                    } else {
                        renderTile(gc, x, y, terrain);
                    }
                }
            }
        }

        // Draw grid lines for debugging (subtle) — only when enabled
        if (debugGridEnabled) {
            renderGridOverlay(gc, minX, maxX, minY, maxY);
        }

        gc.restore();
    }

    /**
     * Renders a single isometric diamond tile using a sprite image.
     * The sprite is drawn centered on the tile position.
     *
     * @param gc      graphics context
     * @param gx      grid x
     * @param gy      grid y
     * @param terrain terrain type
     */
    private void renderTileWithSprite(GraphicsContext gc, int gx, int gy, TerrainType terrain) {
        double sx = gridToScreenX(gx, gy);
        double sy = gridToScreenY(gx, gy);

        Image sprite = spriteManager.getTerrainSprite(terrain);
        if (sprite != null) {
            // Draw sprite centered on the tile's diamond center
            double drawX = sx - sprite.getWidth() / 2.0;
            double drawY = sy - sprite.getHeight() / 2.0;
            gc.drawImage(sprite, drawX, drawY);
        } else {
            // Fall back to colored diamond if sprite is unavailable
            renderTile(gc, gx, gy, terrain);
        }
    }

    /**
     * Renders a single isometric diamond tile at the given grid position.
     * Uses colored fill based on terrain type (original fallback behavior).
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
    private void renderGridOverlay(GraphicsContext gc, int minX, int maxX, int minY, int maxY) {
        gc.setStroke(Color.rgb(0, 0, 0, 0.1));
        gc.setLineWidth(0.3);

        for (int y = minY; y <= maxY + 1; y++) {
            double startX = gridToScreenX(minX, y);
            double startY = gridToScreenY(minX, y);
            double endX = gridToScreenX(maxX + 1, y);
            double endY = gridToScreenY(maxX + 1, y);
            gc.strokeLine(startX, startY, endX, endY);
        }

        for (int x = minX; x <= maxX + 1; x++) {
            double startX = gridToScreenX(x, minY);
            double startY = gridToScreenY(x, minY);
            double endX = gridToScreenX(x, maxY + 1);
            double endY = gridToScreenY(x, maxY + 1);
            gc.strokeLine(startX, startY, endX, endY);
        }
    }

    /**
     * Returns the JavaFX color associated with a terrain type.
     * Used by the colored diamond fallback and by ProceduralSpriteGenerator.
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
            // FIX: Removed DIRT, ICE, RUINS — these terrain types were removed
            // from TerrainType enum (not in RE spec, were fabricated).
            case HILLS         -> Color.rgb(90, 130, 60);       // olive green
            case FOREST        -> Color.rgb(34, 100, 34);       // dark green
            case BRIDGE        -> Color.rgb(139, 90, 43);       // brown
            case MOUNTAIN      -> Color.rgb(101, 67, 33);       // dark brown
            case SWAMP         -> Color.rgb(60, 100, 60);       // murky green
            case SNOW          -> Color.rgb(230, 240, 250);     // white-blue
            case RESOURCE_DEPOSIT -> Color.rgb(200, 170, 50);   // gold
            // DEEP_WATER and SHALLOW_WATER handled by callers that need transparency
        };
    }

    /**
     * Gets whether the debug grid overlay is enabled.
     *
     * @return true if debug grid overlay is rendered
     */
    public boolean isDebugGridEnabled() {
        return debugGridEnabled;
    }

    /**
     * Enables or disables the debug grid overlay.
     *
     * @param enabled true to render the grid overlay
     */
    public void setDebugGridEnabled(boolean enabled) {
        this.debugGridEnabled = enabled;
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
