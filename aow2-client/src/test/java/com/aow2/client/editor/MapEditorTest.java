package com.aow2.client.editor;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MapEditor: map creation, painting, placement, validation, save/load.
 * Naming: shouldXxxWhenYyy, Given-When-Then.
 */
class MapEditorTest {

    private MapEditor editor;

    @BeforeEach
    void setUp() {
        editor = new MapEditor();
    }

    @Nested
    @DisplayName("Map Creation")
    class MapCreation {

        @Test
        @DisplayName("shouldCreateNewMapWithGivenDimensions")
        void shouldCreateNewMapWithGivenDimensions() {
            // Given: a new editor
            // When: creating a 30x20 map
            editor.createNewMap(30, 20);

            // Then: map should have correct dimensions
            assertNotNull(editor.getCurrentMap());
            assertEquals(30, editor.getCurrentMap().getWidth());
            assertEquals(20, editor.getCurrentMap().getHeight());
        }

        @Test
        @DisplayName("shouldInitializeWithGrassTerrain")
        void shouldInitializeWithGrassTerrain() {
            // Given: a new map
            editor.createNewMap(10, 10);

            // Then: all tiles should be GRASS
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    assertEquals(TerrainType.GRASS, editor.getCurrentMap().getTile(x, y));
                }
            }
        }

        @Test
        @DisplayName("shouldClearPreviousStateWhenCreatingNewMap")
        void shouldClearPreviousStateWhenCreatingNewMap() {
            // Given: an editor with an existing map
            editor.createNewMap(10, 10);
            editor.paintTerrain(new GridPosition(5, 5), TerrainType.DEEP_WATER);

            // When: creating a new map
            editor.createNewMap(20, 20);

            // Then: the new map should be fresh
            assertEquals(20, editor.getCurrentMap().getWidth());
            assertEquals(TerrainType.GRASS, editor.getCurrentMap().getTile(5, 5));
        }

        @Test
        @DisplayName("shouldThrowWhenCreatingMapWithInvalidDimensions")
        void shouldThrowWhenCreatingMapWithInvalidDimensions() {
            assertThrows(IllegalArgumentException.class, () -> editor.createNewMap(0, 10));
            assertThrows(IllegalArgumentException.class, () -> editor.createNewMap(10, 200));
        }
    }

    @Nested
    @DisplayName("Terrain Painting")
    class TerrainPainting {

        @BeforeEach
        void setUpMap() {
            editor.createNewMap(20, 20);
        }

        @Test
        @DisplayName("shouldPaintTerrainAtPosition")
        void shouldPaintTerrainAtPosition() {
            // Given: a map and a position
            GridPosition pos = new GridPosition(5, 5);

            // When: painting water terrain
            editor.paintTerrain(pos, TerrainType.DEEP_WATER);

            // Then: the tile should be WATER
            assertEquals(TerrainType.DEEP_WATER, editor.getCurrentMap().getTile(5, 5));
        }

        @Test
        @DisplayName("shouldPaintMultipleTerrainTypes")
        void shouldPaintMultipleTerrainTypes() {
            // When: painting different terrains
            editor.paintTerrain(new GridPosition(0, 0), TerrainType.MOUNTAIN);
            editor.paintTerrain(new GridPosition(1, 1), TerrainType.FOREST);
            editor.paintTerrain(new GridPosition(2, 2), TerrainType.SAND);

            // Then: each tile should have the correct terrain
            assertEquals(TerrainType.MOUNTAIN, editor.getCurrentMap().getTile(0, 0));
            assertEquals(TerrainType.FOREST, editor.getCurrentMap().getTile(1, 1));
            assertEquals(TerrainType.SAND, editor.getCurrentMap().getTile(2, 2));
        }

        @Test
        @DisplayName("shouldPaintWithBrushSize")
        void shouldPaintWithBrushSize() {
            // Given: tile painter with medium (3x3) brush
            editor.getTilePainter().setBrushSize(TilePainter.BrushSize.MEDIUM);
            editor.getTilePainter().setSelectedTerrain(TerrainType.DEEP_WATER);

            // When: painting at center position
            var painted = editor.getTilePainter().paint(new GridPosition(10, 10));

            // Then: 9 tiles should be painted (3x3 area)
            assertTrue(painted.size() >= 9);
            assertEquals(TerrainType.DEEP_WATER, editor.getCurrentMap().getTile(9, 9));
            assertEquals(TerrainType.DEEP_WATER, editor.getCurrentMap().getTile(10, 10));
            assertEquals(TerrainType.DEEP_WATER, editor.getCurrentMap().getTile(11, 11));
        }

        @Test
        @DisplayName("shouldDoNothingWhenPaintingOnNullMap")
        void shouldDoNothingWhenPaintingOnNullMap() {
            // Given: an editor without a map
            MapEditor emptyEditor = new MapEditor();

            // When/Then: painting should not throw
            assertDoesNotThrow(() -> emptyEditor.paintTerrain(new GridPosition(5, 5), TerrainType.DEEP_WATER));
        }
    }

    @Nested
    @DisplayName("Entity Placement")
    class EntityPlacement {

        @BeforeEach
        void setUpMap() {
            editor.createNewMap(20, 20);
        }

        @Test
        @DisplayName("shouldPlaceBuildingForCorrectFaction")
        void shouldPlaceBuildingForCorrectFaction() {
            // When: placing a Confederation building for player 0
            boolean placed = editor.placeBuilding(
                BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 0);

            // Then: placement should succeed
            assertTrue(placed);
            assertNotNull(editor.getEntityManager().findBuildingAt(new GridPosition(5, 5)));
        }

        @Test
        @DisplayName("shouldRejectBuildingForWrongFaction")
        void shouldRejectBuildingForWrongFaction() {
            // When: placing a Confederation building for player 1 (Resistance)
            boolean placed = editor.placeBuilding(
                BuildingType.CONFED_COMMAND_CENTRE,
                new GridPosition(5, 5), 1);

            // Then: placement should fail
            assertFalse(placed);
        }

        @Test
        @DisplayName("shouldPlaceUnitForCorrectFaction")
        void shouldPlaceUnitForCorrectFaction() {
            // When: placing a Resistance unit for player 1
            boolean placed = editor.placeUnit(
                UnitType.REBEL_INFANTRY,
                new GridPosition(10, 10), 1);

            // Then: placement should succeed
            assertTrue(placed);
        }

        @Test
        @DisplayName("shouldRejectUnitOnImpassableTerrain")
        void shouldRejectUnitOnImpassableTerrain() {
            // Given: water terrain at a position
            editor.paintTerrain(new GridPosition(5, 5), TerrainType.DEEP_WATER);

            // When: trying to place a unit on water
            boolean placed = editor.placeUnit(
                UnitType.CONFED_INFANTRY,
                new GridPosition(5, 5), 0);

            // Then: placement should fail
            assertFalse(placed);
        }

        @Test
        @DisplayName("shouldRejectOverlappingBuildings")
        void shouldRejectOverlappingBuildings() {
            // Given: a building already at a position
            editor.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE, new GridPosition(5, 5), 0);

            // When: trying to place another building at the same position
            boolean placed = editor.placeBuilding(
                BuildingType.CONFED_GENERATOR,
                new GridPosition(5, 5), 0);

            // Then: second placement should fail
            assertFalse(placed);
        }
    }

    @Nested
    @DisplayName("Starting Positions")
    class StartingPositions {

        @BeforeEach
        void setUpMap() {
            editor.createNewMap(20, 20);
        }

        @Test
        @DisplayName("shouldSetStartingPositionForPlayer")
        void shouldSetStartingPositionForPlayer() {
            // When: setting starting positions
            editor.setStartingPosition(0, new GridPosition(2, 2));
            editor.setStartingPosition(1, new GridPosition(18, 18));

            // Then: both positions should be recorded
            assertEquals(new GridPosition(2, 2), editor.getStartingPositions().get(0));
            assertEquals(new GridPosition(18, 18), editor.getStartingPositions().get(1));
        }

        @Test
        @DisplayName("shouldThrowForInvalidPlayerId")
        void shouldThrowForInvalidPlayerId() {
            assertThrows(IllegalArgumentException.class,
                () -> editor.setStartingPosition(2, new GridPosition(5, 5)));
            assertThrows(IllegalArgumentException.class,
                () -> editor.setStartingPosition(-1, new GridPosition(5, 5)));
        }
    }

    @Nested
    @DisplayName("Map Validation")
    class MapValidation {

        @Test
        @DisplayName("shouldReportErrorWhenNoMap")
        void shouldReportErrorWhenNoMap() {
            // Given: no map loaded
            // When: validating
            var result = editor.validateMap();

            // Then: should report error
            assertFalse(result.isValid());
            assertTrue(result.getErrors().contains("No map loaded"));
        }

        @Test
        @DisplayName("shouldReportMissingStartingPositions")
        void shouldReportMissingStartingPositions() {
            // Given: a map without starting positions
            editor.createNewMap(20, 20);

            // When: validating
            var result = editor.validateMap();

            // Then: should report missing starting positions
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Player 0")));
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Player 1")));
        }

        @Test
        @DisplayName("shouldPassValidationWithCompleteMap")
        void shouldPassValidationWithCompleteMap() {
            // Given: a complete map with starting positions and HQs
            editor.createNewMap(30, 30);
            editor.setStartingPosition(0, new GridPosition(2, 2));
            editor.setStartingPosition(1, new GridPosition(28, 28));
            editor.placeBuilding(BuildingType.CONFED_COMMAND_CENTRE, new GridPosition(2, 2), 0);
            editor.placeBuilding(BuildingType.REBEL_HEADQUARTERS, new GridPosition(28, 28), 1);

            // When: validating
            var result = editor.validateMap();

            // Then: should be valid
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("shouldWarnWhenStartingPositionsTooClose")
        void shouldWarnWhenStartingPositionsTooClose() {
            // Given: starting positions very close together
            editor.createNewMap(20, 20);
            editor.setStartingPosition(0, new GridPosition(5, 5));
            editor.setStartingPosition(1, new GridPosition(6, 6));

            // When: validating
            var result = editor.validateMap();

            // Then: should have a warning about close positions
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("close")));
        }

        @Test
        @DisplayName("shouldWarnAboutExcessiveImpassableTerrain")
        void shouldWarnAboutExcessiveImpassableTerrain() {
            // Given: a map with >50% water
            editor.createNewMap(10, 10);
            editor.setStartingPosition(0, new GridPosition(0, 0));
            editor.setStartingPosition(1, new GridPosition(9, 9));

            // Paint >50% water
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 6; y++) {
                    editor.paintTerrain(new GridPosition(x, y), TerrainType.DEEP_WATER);
                }
            }

            // When: validating
            var result = editor.validateMap();

            // Then: should warn about water coverage
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("water")));
        }

        @Test
        @DisplayName("shouldDetectUnreachablePassableAreas")
        void shouldDetectUnreachablePassableAreas() {
            // Given: a map with two separate grass areas divided by water
            editor.createNewMap(10, 10);
            editor.setStartingPosition(0, new GridPosition(0, 0));
            editor.setStartingPosition(1, new GridPosition(9, 9));

            // Fill a dividing wall of water
            for (int y = 0; y < 10; y++) {
                editor.paintTerrain(new GridPosition(5, y), TerrainType.DEEP_WATER);
            }

            // When: validating
            var result = editor.validateMap();

            // Then: should warn about unreachable areas
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("unreachable")));
        }
    }

    @Nested
    @DisplayName("Save and Load")
    class SaveAndLoad {

        @Test
        @DisplayName("shouldSaveAndLoadMapPreservingTerrain")
        void shouldSaveAndLoadMapPreservingTerrain() throws Exception {
            // Given: a map with custom terrain
            editor.createNewMap(10, 10);
            editor.paintTerrain(new GridPosition(3, 3), TerrainType.DEEP_WATER);
            editor.paintTerrain(new GridPosition(7, 7), TerrainType.MOUNTAIN);
            editor.setStartingPosition(0, new GridPosition(0, 0));
            editor.setStartingPosition(1, new GridPosition(9, 9));

            Path tempFile = Files.createTempFile("test_map", ".json");

            try {
                // When: saving and loading
                assertTrue(editor.saveMap(tempFile));

                MapEditor loadedEditor = new MapEditor();
                assertTrue(loadedEditor.loadMap(tempFile));

                // Then: terrain should be preserved
                assertEquals(TerrainType.DEEP_WATER, loadedEditor.getCurrentMap().getTile(3, 3));
                assertEquals(TerrainType.MOUNTAIN, loadedEditor.getCurrentMap().getTile(7, 7));
                assertEquals(TerrainType.GRASS, loadedEditor.getCurrentMap().getTile(5, 5));
                assertEquals(new GridPosition(0, 0), loadedEditor.getStartingPositions().get(0));
                assertEquals(new GridPosition(9, 9), loadedEditor.getStartingPositions().get(1));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("shouldReturnFalseWhenSavingWithNoMap")
        void shouldReturnFalseWhenSavingWithNoMap() {
            assertFalse(editor.saveMap(Path.of("test.json")));
        }
    }
}
