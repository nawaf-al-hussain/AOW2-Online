package com.aow2.client.render;

import com.aow2.common.model.GridPosition;
import com.aow2.core.world.FogOfWarSystem;
import com.aow2.core.world.FogOfWarSystem.TileVisibility;
import com.aow2.core.world.GameMap;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders fog of war overlay on the game map.
 * <p>
 * Rendering rules:
 * - UNEXPLORED: solid black overlay (player has never seen this tile)
 * - EXPLORED: semi-transparent dark overlay (previously seen terrain visible but dimmed)
 * - VISIBLE: no overlay (tile is currently in sight)
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Fog of war rendering
 * REF: unit_stats.md - sightRange per unit type determines visible area
 */
public final class FogRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(FogRenderer.class);

    /** Color for unexplored tiles (solid black). */
    private static final Color UNEXPLORED_COLOR = Color.rgb(0, 0, 0, 1.0);

    /** Color for explored but not currently visible tiles (semi-transparent dark). */
    private static final Color EXPLORED_COLOR = Color.rgb(0, 0, 0, 0.55);

    /** The fog of war system providing visibility data. */
    private FogOfWarSystem fogOfWar;

    /** The player ID this renderer is for. */
    private int playerId;

    /** Reference to isometric renderer for coordinate transforms. */
    private final IsometricRenderer isoRenderer;

    /**
     * Constructs a FogRenderer with the given isometric renderer.
     *
     * @param isoRenderer the isometric renderer for coordinate transforms
     */
    public FogRenderer(IsometricRenderer isoRenderer) {
        this.isoRenderer = isoRenderer;
        this.playerId = 0;
    }

    /**
     * Sets the fog of war system to render from.
     *
     * @param fogOfWar the fog of war system
     */
    public void setFogOfWar(FogOfWarSystem fogOfWar) {
        this.fogOfWar = fogOfWar;
    }

    /**
     * Sets the player ID this renderer is for.
     *
     * @param playerId the player ID (0 or 1)
     */
    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    /**
     * Renders the fog of war overlay on the game canvas.
     * Only renders fog tiles; visible tiles are left untouched.
     *
     * @param gc           the graphics context
     * @param map          the game map
     * @param cameraOffsetX camera horizontal offset
     * @param cameraOffsetY camera vertical offset
     * @param zoom         zoom scale factor
     */
    public void render(GraphicsContext gc, GameMap map, double cameraOffsetX,
                       double cameraOffsetY, double zoom) {
        if (fogOfWar == null || map == null) {
            return;
        }

        gc.save();
        gc.translate(cameraOffsetX, cameraOffsetY);
        gc.scale(zoom, zoom);

        int tileHalfW = IsometricRenderer.TILE_HALF_WIDTH;
        int tileHalfH = IsometricRenderer.TILE_HALF_HEIGHT;

        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                GridPosition pos = new GridPosition(x, y);
                TileVisibility vis = fogOfWar.getVisibility(playerId, pos);

                if (vis == TileVisibility.VISIBLE) {
                    continue; // No fog overlay for visible tiles
                }

                double sx = isoRenderer.gridToScreenX(x, y);
                double sy = isoRenderer.gridToScreenY(x, y);

                Color overlayColor = switch (vis) {
                    case UNEXPLORED -> UNEXPLORED_COLOR;
                    case EXPLORED -> EXPLORED_COLOR;
                    case VISIBLE -> null; // Already handled above
                };

                if (overlayColor == null) {
                    continue;
                }

                // Draw isometric diamond overlay
                double[] xPoints = {
                    sx,
                    sx + tileHalfW,
                    sx,
                    sx - tileHalfW
                };
                double[] yPoints = {
                    sy - tileHalfH,
                    sy,
                    sy + tileHalfH,
                    sy
                };

                gc.setFill(overlayColor);
                gc.fillPolygon(xPoints, yPoints, 4);
            }
        }

        gc.restore();
    }
}
