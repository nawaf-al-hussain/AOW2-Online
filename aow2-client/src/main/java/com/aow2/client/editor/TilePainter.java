package com.aow2.client.editor;

import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.core.world.GameMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles brush-based terrain painting with adjustable brush size.
 * Supports brush sizes 1x1, 3x3, and 5x5 for efficient map editing.
 * <p>
 * REF: phases.md Phase 9 - terrain painting with adjustable brush
 */
public final class TilePainter {

    /** Supported brush sizes for terrain painting. */
    public enum BrushSize {
        SMALL(1),
        MEDIUM(3),
        LARGE(5);

        private final int size;

        BrushSize(int size) {
            this.size = size;
        }

        /** Returns the brush dimension (side length). */
        public int getSize() {
            return size;
        }
    }

    /** The map being painted. */
    private GameMap map;

    /** Current brush size. */
    private BrushSize brushSize;

    /** The terrain type currently selected for painting. */
    private TerrainType selectedTerrain;

    /**
     * Constructs a TilePainter with default medium brush and GRASS terrain.
     */
    public TilePainter() {
        this.brushSize = BrushSize.MEDIUM;
        this.selectedTerrain = TerrainType.GRASS;
    }

    /**
     * Sets the map to paint on.
     *
     * @param map the game map
     */
    public void setMap(GameMap map) {
        this.map = map;
    }

    /**
     * Returns the current map.
     *
     * @return the game map, or null if not set
     */
    public GameMap getMap() {
        return map;
    }

    /**
     * Sets the brush size.
     *
     * @param brushSize the new brush size
     */
    public void setBrushSize(BrushSize brushSize) {
        this.brushSize = brushSize;
    }

    /**
     * Returns the current brush size.
     *
     * @return brush size
     */
    public BrushSize getBrushSize() {
        return brushSize;
    }

    /**
     * Sets the terrain type to paint.
     *
     * @param terrain the terrain type
     */
    public void setSelectedTerrain(TerrainType terrain) {
        this.selectedTerrain = terrain;
    }

    /**
     * Returns the currently selected terrain type.
     *
     * @return selected terrain
     */
    public TerrainType getSelectedTerrain() {
        return selectedTerrain;
    }

    /**
     * Paints terrain at the given grid position using the current brush size
     * and selected terrain type.
     * <p>
     * For a brush size of N, paints an NxN area centered on the given position.
     * Odd-sized brushes center exactly; even-sized brushes offset to the top-left.
     *
     * @param pos center position for the brush
     * @return list of positions that were actually painted
     */
    public List<GridPosition> paint(GridPosition pos) {
        return paint(pos, selectedTerrain, brushSize);
    }

    /**
     * Paints terrain at the given grid position with a specific terrain type
     * and brush size.
     *
     * @param pos      center position for the brush
     * @param terrain  terrain type to paint
     * @param size     brush size to use
     * @return list of positions that were actually painted
     */
    public List<GridPosition> paint(GridPosition pos, TerrainType terrain, BrushSize size) {
        List<GridPosition> painted = new ArrayList<>();

        if (map == null || pos == null) {
            return painted;
        }

        int halfBrush = size.getSize() / 2;
        int startX = pos.x() - halfBrush;
        int startY = pos.y() - halfBrush;

        for (int dx = 0; dx < size.getSize(); dx++) {
            for (int dy = 0; dy < size.getSize(); dy++) {
                int tx = startX + dx;
                int ty = startY + dy;

                if (map.isInBounds(tx, ty)) {
                    map.setTile(tx, ty, terrain);
                    painted.add(new GridPosition(tx, ty));
                }
            }
        }

        return painted;
    }

    /**
     * Fills a rectangular region with the selected terrain type.
     *
     * @param start  top-left corner
     * @param end    bottom-right corner
     * @param terrain terrain type to fill with
     * @return number of tiles painted
     */
    public int fillRegion(GridPosition start, GridPosition end, TerrainType terrain) {
        if (map == null) {
            return 0;
        }

        int count = 0;
        int minX = Math.min(start.x(), end.x());
        int maxX = Math.max(start.x(), end.x());
        int minY = Math.min(start.y(), end.y());
        int maxY = Math.max(start.y(), end.y());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (map.isInBounds(x, y)) {
                    map.setTile(x, y, terrain);
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Erases terrain at the given position by setting it to GRASS.
     *
     * @param pos center position for erasing
     * @return list of positions that were erased
     */
    public List<GridPosition> erase(GridPosition pos) {
        return paint(pos, TerrainType.GRASS, brushSize);
    }
}
