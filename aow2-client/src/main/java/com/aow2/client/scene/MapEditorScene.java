package com.aow2.client.scene;

import com.aow2.client.editor.EntityPlacer;
import com.aow2.client.editor.MapEditor;
import com.aow2.client.editor.MapEditor.EditorTool;
import com.aow2.client.editor.TilePainter;
import com.aow2.client.render.CameraController;
import com.aow2.client.render.IsometricRenderer;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitType;
import com.aow2.core.world.GameMap;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FXGL scene for the map editor.
 * Provides a toolbar, palette panel, and map canvas for creating
 * and editing game maps.
 * <p>
 * REF: phases.md Phase 9 - Map Builder scene
 */
public class MapEditorScene {

    private static final Logger LOG = LoggerFactory.getLogger(MapEditorScene.class);

    /** Canvas dimensions. */
    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 720;

    /** The root pane. */
    private final StackPane root;

    /** The editor canvas. */
    private final Canvas editorCanvas;

    /** The graphics context. */
    private final GraphicsContext gc;

    /** The isometric renderer. */
    private final IsometricRenderer isoRenderer;

    /** The camera controller. */
    private final CameraController cameraController;

    /** The map editor backend. */
    private final MapEditor mapEditor;

    /** Animation timer for rendering. */
    private AnimationTimer renderTimer;

    /** Whether the left mouse button is held. */
    private boolean mouseDown;

    /** Last painted grid position (to avoid repainting the same tile). */
    private GridPosition lastPaintedPos;

    /** Callback for returning to main menu. */
    private Runnable onExitCallback;

    /**
     * Constructs a new MapEditorScene.
     */
    public MapEditorScene() {
        this.root = new StackPane();
        this.editorCanvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        this.gc = editorCanvas.getGraphicsContext2D();
        this.isoRenderer = new IsometricRenderer();
        this.cameraController = new CameraController();
        this.mapEditor = new MapEditor();
        this.mouseDown = false;
        this.lastPaintedPos = null;

        buildUI();
        setupInputHandlers();

        LOG.info("MapEditorScene created");
    }

    /**
     * Builds the editor UI with toolbar, palette, and canvas.
     */
    private void buildUI() {
        BorderPane layout = new BorderPane();

        // Top toolbar
        HBox toolbar = createToolbar();
        layout.setTop(toolbar);

        // Left palette panel
        VBox palette = createPalette();
        ScrollPane paletteScroll = new ScrollPane(palette);
        paletteScroll.setPrefWidth(200);
        paletteScroll.setFitToWidth(true);
        layout.setLeft(paletteScroll);

        // Center canvas
        Pane canvasPane = new Pane();
        canvasPane.getChildren().add(editorCanvas);
        editorCanvas.widthProperty().bind(canvasPane.widthProperty());
        editorCanvas.heightProperty().bind(canvasPane.heightProperty());
        layout.setCenter(canvasPane);

        // Status bar
        HBox statusBar = createStatusBar();
        layout.setBottom(statusBar);

        root.getChildren().add(layout);
    }

    /**
     * Creates the top toolbar with file operations and tool selection.
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.setStyle("-fx-background-color: rgb(30, 33, 28);");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // File operations
        Button newMapBtn = createToolButton("New Map");
        newMapBtn.setOnAction(e -> mapEditor.createNewMap(30, 20));

        Button saveBtn = createToolButton("Save");
        saveBtn.setOnAction(e -> {
            var result = mapEditor.validateMap();
            if (result.isValid()) {
                LOG.info("Map is valid, ready to save");
            } else {
                LOG.warn("Map has validation errors: {}", result.getErrors());
            }
        });

        Button loadBtn = createToolButton("Load");
        loadBtn.setOnAction(e -> LOG.info("Load dialog not yet implemented"));

        Button validateBtn = createToolButton("Validate");
        validateBtn.setOnAction(e -> {
            var result = mapEditor.validateMap();
            if (result.isValid()) {
                LOG.info("Map validation passed");
                if (result.hasWarnings()) {
                    LOG.warn("Warnings: {}", result.getWarnings());
                }
            } else {
                LOG.error("Validation errors: {}", result.getErrors());
            }
        });

        // Tool selection
        Label toolLabel = new Label("Tool:");
        toolLabel.setTextFill(Color.rgb(210, 200, 160));

        ComboBox<String> toolCombo = new ComboBox<>();
        toolCombo.getItems().addAll("Terrain", "Building", "Unit", "Erase", "Start Pos");
        toolCombo.setValue("Terrain");
        toolCombo.setOnAction(e -> {
            int idx = toolCombo.getSelectionModel().getSelectedIndex();
            mapEditor.setCurrentTool(EditorTool.values()[idx]);
        });

        // Player selector
        Label playerLabel = new Label("Player:");
        playerLabel.setTextFill(Color.rgb(210, 200, 160));

        ComboBox<String> playerCombo = new ComboBox<>();
        playerCombo.getItems().addAll("Player 0 (Confed)", "Player 1 (Rebel)");
        playerCombo.setValue("Player 0 (Confed)");
        playerCombo.setOnAction(e -> {
            mapEditor.setCurrentPlayerId(playerCombo.getSelectionModel().getSelectedIndex());
        });

        // Back button
        Button backBtn = createToolButton("← Back");
        backBtn.setOnAction(e -> {
            stop();
            if (onExitCallback != null) {
                onExitCallback.run();
            }
        });

        toolbar.getChildren().addAll(
            newMapBtn, saveBtn, loadBtn, validateBtn,
            createSeparator(),
            toolLabel, toolCombo,
            playerLabel, playerCombo,
            createSeparator(),
            backBtn
        );

        return toolbar;
    }

    /**
     * Creates the left palette panel with terrain, building, and unit selections.
     */
    private VBox createPalette() {
        VBox palette = new VBox(5);
        palette.setPadding(new Insets(8));
        palette.setStyle("-fx-background-color: rgb(25, 28, 23);");

        // Terrain section
        Label terrainHeader = new Label("Terrain");
        terrainHeader.setTextFill(Color.rgb(210, 200, 160));
        terrainHeader.setFont(Font.font("Consolas", 14));
        palette.getChildren().add(terrainHeader);

        for (TerrainType terrain : TerrainType.values()) {
            Button btn = new Button(terrain.name());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-background-color: rgb(40, 45, 35); -fx-text-fill: rgb(210, 200, 160); -fx-font-size: 11px;");
            btn.setOnAction(e -> mapEditor.setSelectedTerrain(terrain));
            palette.getChildren().add(btn);
        }

        // Brush size section
        Label brushHeader = new Label("Brush Size");
        brushHeader.setTextFill(Color.rgb(210, 200, 160));
        brushHeader.setFont(Font.font("Consolas", 14));
        palette.getChildren().add(brushHeader);

        for (TilePainter.BrushSize size : TilePainter.BrushSize.values()) {
            Button btn = new Button(size.name() + " (" + size.getSize() + "x" + size.getSize() + ")");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-background-color: rgb(40, 45, 35); -fx-text-fill: rgb(210, 200, 160); -fx-font-size: 11px;");
            btn.setOnAction(e -> mapEditor.getTilePainter().setBrushSize(size));
            palette.getChildren().add(btn);
        }

        // Confederation buildings
        Label confedBuildHeader = new Label("Confed Buildings");
        confedBuildHeader.setTextFill(Color.rgb(100, 150, 255));
        confedBuildHeader.setFont(Font.font("Consolas", 12));
        palette.getChildren().add(confedBuildHeader);

        for (BuildingType type : BuildingType.values()) {
            if (type.faction() == Faction.CONFEDERATION) {
                Button btn = new Button(type.displayName());
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setStyle("-fx-background-color: rgb(35, 40, 55); -fx-text-fill: rgb(150, 180, 255); -fx-font-size: 11px;");
                btn.setOnAction(e -> {
                    mapEditor.setSelectedBuilding(type);
                    mapEditor.setCurrentTool(EditorTool.BUILDING_PLACE);
                });
                palette.getChildren().add(btn);
            }
        }

        // Resistance buildings
        Label rebelBuildHeader = new Label("Rebel Buildings");
        rebelBuildHeader.setTextFill(Color.rgb(255, 130, 100));
        rebelBuildHeader.setFont(Font.font("Consolas", 12));
        palette.getChildren().add(rebelBuildHeader);

        for (BuildingType type : BuildingType.values()) {
            if (type.faction() == Faction.RESISTANCE) {
                Button btn = new Button(type.displayName());
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setStyle("-fx-background-color: rgb(55, 35, 30); -fx-text-fill: rgb(255, 180, 150); -fx-font-size: 11px;");
                btn.setOnAction(e -> {
                    mapEditor.setSelectedBuilding(type);
                    mapEditor.setCurrentTool(EditorTool.BUILDING_PLACE);
                });
                palette.getChildren().add(btn);
            }
        }

        return palette;
    }

    /**
     * Creates the bottom status bar.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(3, 10, 3, 10));
        statusBar.setStyle("-fx-background-color: rgb(30, 33, 28);");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("Map Editor — Click to paint terrain");
        statusLabel.setTextFill(Color.rgb(180, 170, 140));
        statusLabel.setFont(Font.font("Consolas", 11));

        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    /**
     * Creates a styled toolbar button.
     */
    private Button createToolButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: rgb(50, 55, 42); -fx-text-fill: rgb(210, 200, 160); " +
                     "-fx-border-color: rgb(80, 90, 60); -fx-border-width: 1; -fx-font-size: 12px;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgb(70, 75, 55); " +
            "-fx-text-fill: rgb(240, 230, 180); -fx-border-color: rgb(120, 130, 80); " +
            "-fx-border-width: 1; -fx-font-size: 12px;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: rgb(50, 55, 42); " +
            "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(80, 90, 60); " +
            "-fx-border-width: 1; -fx-font-size: 12px;"));
        return btn;
    }

    /**
     * Creates a vertical separator for the toolbar.
     */
    private javafx.scene.shape.Rectangle createSeparator() {
        javafx.scene.shape.Rectangle sep = new javafx.scene.shape.Rectangle(1, 24);
        sep.setFill(Color.rgb(80, 90, 60));
        return sep;
    }

    /**
     * Sets up input handlers for the editor canvas.
     */
    private void setupInputHandlers() {
        editorCanvas.setOnMousePressed(e -> {
            mouseDown = true;
            lastPaintedPos = null;
            handleEditorClick(e.getX(), e.getY());
        });

        editorCanvas.setOnMouseDragged(e -> {
            if (mouseDown) {
                handleEditorClick(e.getX(), e.getY());
            }
        });

        editorCanvas.setOnMouseReleased(e -> {
            mouseDown = false;
            lastPaintedPos = null;
        });

        editorCanvas.setOnScroll(e -> {
            double zoomDelta = e.getDeltaY() > 0 ? 0.1 : -0.1;
            cameraController.adjustZoom(zoomDelta);
        });

        editorCanvas.setFocusTraversable(true);
    }

    /**
     * Handles a click/drag on the editor canvas, converting screen coordinates
     * to grid position and applying the current tool.
     */
    private void handleEditorClick(double screenX, double screenY) {
        GameMap map = mapEditor.getCurrentMap();
        if (map == null) return;

        // Convert screen coordinates to grid position using isometric inverse
        // ASSUMPTION: simplified conversion for editor; actual implementation
        // would use IsometricRenderer's screen-to-grid conversion
        double camX = cameraController.getCameraX();
        double camY = cameraController.getCameraY();
        double zoom = cameraController.getZoom();

        int gridX = (int) ((screenX + camX) / (64 * zoom));
        int gridY = (int) ((screenY + camY) / (32 * zoom));

        // Clamp to map bounds
        gridX = Math.max(0, Math.min(gridX, map.getWidth() - 1));
        gridY = Math.max(0, Math.min(gridY, map.getHeight() - 1));

        GridPosition pos = new GridPosition(gridX, gridY);

        // Avoid repainting the same position on drag
        if (pos.equals(lastPaintedPos)) return;
        lastPaintedPos = pos;

        // Apply current tool
        switch (mapEditor.getCurrentTool()) {
            case TERRAIN_PAINT -> mapEditor.paintTerrain(pos, mapEditor.getSelectedTerrain());
            case BUILDING_PLACE -> {
                if (mapEditor.getSelectedBuilding() != null) {
                    mapEditor.placeBuilding(mapEditor.getSelectedBuilding(), pos, mapEditor.getCurrentPlayerId());
                }
            }
            case UNIT_PLACE -> {
                if (mapEditor.getSelectedUnit() != null) {
                    mapEditor.placeUnit(mapEditor.getSelectedUnit(), pos, mapEditor.getCurrentPlayerId());
                }
            }
            case ERASE -> mapEditor.getEntityPlacer().eraseEntity(pos);
            case STARTING_POSITION -> mapEditor.setStartingPosition(mapEditor.getCurrentPlayerId(), pos);
        }
    }

    /**
     * Initializes the editor with a default map.
     */
    public void initialize() {
        mapEditor.createNewMap(30, 20);
        isoRenderer.setMap(mapEditor.getCurrentMap());
        isoRenderer.centerOnMap(CANVAS_WIDTH, CANVAS_HEIGHT);
        cameraController.setMap(mapEditor.getCurrentMap());
        cameraController.setViewportSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        LOG.info("Map editor initialized with default 30x20 map");
    }

    /**
     * Starts the editor rendering loop.
     */
    public void start() {
        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        renderTimer.start();
        editorCanvas.requestFocus();
        LOG.info("Map editor rendering started");
    }

    /**
     * Stops the editor rendering loop.
     */
    public void stop() {
        if (renderTimer != null) {
            renderTimer.stop();
        }
        LOG.info("Map editor rendering stopped");
    }

    /**
     * Renders the editor canvas.
     */
    private void render() {
        cameraController.update();
        GameMap map = mapEditor.getCurrentMap();

        gc.setFill(Color.rgb(15, 18, 22));
        gc.fillRect(0, 0, editorCanvas.getWidth(), editorCanvas.getHeight());

        if (map != null) {
            isoRenderer.render(gc, cameraController.getCameraX(), cameraController.getCameraY(),
                cameraController.getZoom());
        }
    }

    /**
     * Gets the root pane for this scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Gets the map editor backend.
     *
     * @return the map editor
     */
    public MapEditor getMapEditor() {
        return mapEditor;
    }

    /**
     * Sets the callback for returning to the main menu.
     *
     * @param callback exit callback
     */
    public void setOnExitCallback(Runnable callback) {
        this.onExitCallback = callback;
    }
}
