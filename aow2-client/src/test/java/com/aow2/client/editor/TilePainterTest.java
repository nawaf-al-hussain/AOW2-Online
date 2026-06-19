package com.aow2.client.editor;

import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.core.world.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TilePainter: brush sizes, painting, fill, erase.
 * Uses real GameMap instances (pure Java, no FX).
 */
class TilePainterTest {

    private TilePainter painter;
    private GameMap map;

    @BeforeEach
    void setUp() {
        painter = new TilePainter();
        map = new GameMap(20, 20);
        painter.setMap(map);
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("default brush size is MEDIUM")
        void defaultBrushSizeIsMedium() {
            assertEquals(TilePainter.BrushSize.MEDIUM, painter.getBrushSize());
        }

        @Test
        @DisplayName("default terrain is GRASS")
        void defaultTerrainIsGrass() {
            assertEquals(TerrainType.GRASS, painter.getSelectedTerrain());
        }

        @Test
        @DisplayName("map is accessible after setMap")
        void mapIsAccessible() {
            assertNotNull(painter.getMap());
            assertEquals(20, painter.getMap().getWidth());
        }
    }

    @Nested
    @DisplayName("Brush Configuration")
    class BrushConfiguration {

        @Test
        @DisplayName("setBrushSize updates brush size")
        void setBrushSizeUpdates() {
            painter.setBrushSize(TilePainter.BrushSize.LARGE);
            assertEquals(TilePainter.BrushSize.LARGE, painter.getBrushSize());
        }

        @Test
        @DisplayName("setSelectedTerrain updates terrain")
        void setSelectedTerrainUpdates() {
            painter.setSelectedTerrain(TerrainType.DEEP_WATER);
            assertEquals(TerrainType.DEEP_WATER, painter.getSelectedTerrain());
        }

        @Test
        @DisplayName("all brush sizes have odd dimensions")
        void allBrushSizesHaveOddDimensions() {
            for (TilePainter.BrushSize size : TilePainter.BrushSize.values()) {
                assertEquals(1, size.getSize() % 2,
                    () -> size.name() + " should have odd dimension");
            }
        }
    }

    @Nested
    @DisplayName("Paint with Small Brush")
    class PaintSmallBrush {

        @BeforeEach
        void setUp() {
            painter.setBrushSize(TilePainter.BrushSize.SMALL);
            painter.setSelectedTerrain(TerrainType.DEEP_WATER);
        }

        @Test
        @DisplayName("paints exactly one tile")
        void paintsOneTile() {
            List<GridPosition> painted = painter.paint(new GridPosition(10, 10));
            assertEquals(1, painted.size());
            assertEquals(new GridPosition(10, 10), painted.get(0));
        }

        @Test
        @DisplayName("changes tile terrain")
        void changesTileTerrain() {
            painter.paint(new GridPosition(5, 5));
            assertEquals(TerrainType.DEEP_WATER, map.getTile(5, 5));
        }
    }

    @Nested
    @DisplayName("Paint with Medium Brush")
    class PaintMediumBrush {

        @BeforeEach
        void setUp() {
            painter.setBrushSize(TilePainter.BrushSize.MEDIUM);
            painter.setSelectedTerrain(TerrainType.FOREST);
        }

        @Test
        @DisplayName("paints 3x3 area (9 tiles)")
        void paints3x3Area() {
            List<GridPosition> painted = painter.paint(new GridPosition(10, 10));
            assertEquals(9, painted.size());
        }

        @Test
        @DisplayName("paints all tiles in range")
        void paintsAllInRange() {
            painter.paint(new GridPosition(10, 10));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    assertEquals(TerrainType.FOREST, map.getTile(10 + dx, 10 + dy));
                }
            }
        }
    }

    @Nested
    @DisplayName("Paint with Large Brush")
    class PaintLargeBrush {

        @Test
        @DisplayName("paints 5x5 area (25 tiles)")
        void paints5x5Area() {
            painter.setBrushSize(TilePainter.BrushSize.LARGE);
            painter.setSelectedTerrain(TerrainType.SAND);
            List<GridPosition> painted = painter.paint(new GridPosition(10, 10));
            assertEquals(25, painted.size());
        }
    }

    @Nested
    @DisplayName("Paint at Edges")
    class PaintAtEdges {

        @Test
        @DisplayName("painting near edge clips to map bounds")
        void paintingNearEdgeClips() {
            painter.setBrushSize(TilePainter.BrushSize.LARGE);
            painter.setSelectedTerrain(TerrainType.MOUNTAIN);
            List<GridPosition> painted = painter.paint(new GridPosition(0, 0));

            // Should paint fewer than 25 tiles due to clipping
            assertTrue(painted.size() < 25);
            assertTrue(painted.size() > 0);
        }

        @Test
        @DisplayName("painting at corner clips correctly")
        void paintingAtCorner() {
            painter.setBrushSize(TilePainter.BrushSize.MEDIUM);
            painter.setSelectedTerrain(TerrainType.SNOW);
            List<GridPosition> painted = painter.paint(new GridPosition(0, 0));

            // 3x3 centered at (0,0) clipped to map -> 4 tiles
            assertEquals(4, painted.size());
        }
    }

    @Nested
    @DisplayName("Paint with Null Map")
    class PaintWithNullMap {

        @Test
        @DisplayName("paint with null map returns empty list")
        void paintWithNullMap() {
            TilePainter noMapPainter = new TilePainter();
            List<GridPosition> painted = noMapPainter.paint(new GridPosition(5, 5));
            assertTrue(painted.isEmpty());
        }

        @Test
        @DisplayName("paint with null position returns empty list")
        void paintWithNullPosition() {
            List<GridPosition> painted = painter.paint(null, TerrainType.GRASS,
                TilePainter.BrushSize.SMALL);
            assertTrue(painted.isEmpty());
        }
    }

    @Nested
    @DisplayName("Fill Region")
    class FillRegion {

        @Test
        @DisplayName("fillRegion paints entire region")
        void fillRegionPaintsEntireRegion() {
            int count = painter.fillRegion(
                new GridPosition(5, 5), new GridPosition(7, 7), TerrainType.ROAD);

            // 3x3 = 9 tiles
            assertEquals(9, count);
            assertEquals(TerrainType.ROAD, map.getTile(5, 5));
            assertEquals(TerrainType.ROAD, map.getTile(7, 7));
        }

        @Test
        @DisplayName("fillRegion with reversed coordinates works")
        void fillRegionReversedCoordinates() {
            int count = painter.fillRegion(
                new GridPosition(7, 7), new GridPosition(5, 5), TerrainType.BRIDGE);
            assertEquals(9, count);
        }

        @Test
        @DisplayName("fillRegion with null map returns zero")
        void fillRegionNullMap() {
            TilePainter noMapPainter = new TilePainter();
            int count = noMapPainter.fillRegion(
                new GridPosition(0, 0), new GridPosition(5, 5), TerrainType.GRASS);
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("Erase")
    class Erase {

        @Test
        @DisplayName("erase sets tile back to GRASS")
        void eraseSetsBackToGrass() {
            map.setTile(5, 5, TerrainType.DEEP_WATER);
            painter.setBrushSize(TilePainter.BrushSize.SMALL);
            painter.erase(new GridPosition(5, 5));
            assertEquals(TerrainType.GRASS, map.getTile(5, 5));
        }

        @Test
        @DisplayName("erase uses current brush size")
        void eraseUsesBrushSize() {
            map.setTile(9, 9, TerrainType.MOUNTAIN);
            map.setTile(10, 10, TerrainType.MOUNTAIN);
            map.setTile(11, 11, TerrainType.MOUNTAIN);

            painter.setBrushSize(TilePainter.BrushSize.LARGE);
            List<GridPosition> erased = painter.erase(new GridPosition(10, 10));

            assertTrue(erased.size() >= 3);
            assertEquals(TerrainType.GRASS, map.getTile(10, 10));
        }
    }
}
