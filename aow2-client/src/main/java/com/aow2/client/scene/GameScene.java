package com.aow2.client.scene;

import com.aow2.client.input.InputHandler;
import com.aow2.client.input.SelectionManager;
import com.aow2.client.render.CameraController;
import com.aow2.client.render.EntityRenderer;
import com.aow2.client.render.IsometricRenderer;
import com.aow2.client.render.MinimapRenderer;
import com.aow2.client.ui.HUD;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.engine.GameLoop;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main gameplay scene that ties all rendering, input, and game logic together.
 * <p>
 * Contains:
 * - IsometricRenderer for the map
 * - EntityRenderer for units/buildings
 * - HUD overlay
 * - MinimapRenderer
 * - InputHandler
 * - SelectionManager
 * - Integrates with GameLoop from aow2-core
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3 - Architecture Overview
 */
public class GameScene {

    private static final Logger LOG = LoggerFactory.getLogger(GameScene.class);

    /** Default canvas width. */
    private static final int CANVAS_WIDTH = 1280;

    /** Default canvas height. */
    private static final int CANVAS_HEIGHT = 720;

    /** The root pane for this scene. */
    private final StackPane root;

    /** The game canvas for isometric rendering. */
    private final Canvas gameCanvas;

    /** The graphics context for the game canvas. */
    private final GraphicsContext gc;

    /** The isometric renderer. */
    private final IsometricRenderer isoRenderer;

    /** The entity renderer. */
    private final EntityRenderer entityRenderer;

    /** The camera controller. */
    private final CameraController cameraController;

    /** The minimap renderer. */
    private final MinimapRenderer minimapRenderer;

    /** The HUD overlay. */
    private final HUD hud;

    /** The selection manager. */
    private final SelectionManager selectionManager;

    /** The input handler. */
    private final InputHandler inputHandler;

    /** The game map. */
    private GameMap map;

    /** The entity manager. */
    private EntityManager entityManager;

    /** The game state. */
    private GameState gameState;

    /** The game loop. */
    private GameLoop gameLoop;

    /** The JavaFX animation timer for rendering. */
    private AnimationTimer renderTimer;

    /** Current player credits. */
    private int credits;

    /**
     * Constructs a new GameScene with all subsystems initialized.
     */
    public GameScene() {
        this.root = new StackPane();
        this.gameCanvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        this.gc = gameCanvas.getGraphicsContext2D();

        // Initialize subsystems
        this.isoRenderer = new IsometricRenderer();
        this.cameraController = new CameraController();
        this.entityRenderer = new EntityRenderer(isoRenderer);
        this.minimapRenderer = new MinimapRenderer();
        this.hud = new HUD();
        this.selectionManager = new SelectionManager();
        this.inputHandler = new InputHandler(selectionManager, cameraController, isoRenderer);

        this.credits = 1000;

        buildScene();
        setupInputHandlers();
        setupCallbacks();

        LOG.info("GameScene created with all subsystems");
    }

    /**
     * Builds the scene graph with canvas, minimap, and HUD overlay.
     */
    private void buildScene() {
        // Game canvas as the base layer
        Pane canvasPane = new Pane();
        canvasPane.getChildren().add(gameCanvas);
        gameCanvas.widthProperty().bind(canvasPane.widthProperty());
        gameCanvas.heightProperty().bind(canvasPane.heightProperty());

        // HUD overlay on top
        StackPane hudRoot = hud.getRoot();

        // Minimap positioned in bottom-right
        Pane minimapContainer = minimapRenderer.getContainer();
        StackPane.setAlignment(minimapContainer, javafx.geometry.Pos.BOTTOM_RIGHT);
        StackPane.setMargin(minimapContainer, new javafx.geometry.Insets(0, 10, 50, 0));

        root.getChildren().addAll(canvasPane, minimapContainer, hudRoot);
    }

    /**
     * Sets up input handlers on the canvas.
     */
    private void setupInputHandlers() {
        gameCanvas.setOnMousePressed(inputHandler::onMousePressed);
        gameCanvas.setOnMouseDragged(inputHandler::onMouseDragged);
        gameCanvas.setOnMouseReleased(inputHandler::onMouseReleased);
        gameCanvas.setOnMouseMoved(inputHandler::onMouseMoved);
        gameCanvas.setOnScroll(inputHandler::onScroll);

        // Key events need focus
        gameCanvas.setFocusTraversable(true);
        gameCanvas.setOnKeyPressed(inputHandler::onKeyPressed);
        gameCanvas.setOnKeyReleased(inputHandler::onKeyReleased);
    }

    /**
     * Sets up callbacks between subsystems.
     */
    private void setupCallbacks() {
        // HUD action callback
        hud.setActionCallback(action -> {
            LOG.debug("HUD action: {}", action);
            switch (action) {
                case "attack" -> inputHandler.onKeyPressed(
                    new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "a", "a", javafx.scene.input.KeyCode.A, false, false, false, false));
                case "stop" -> inputHandler.onKeyPressed(
                    new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "s", "s", javafx.scene.input.KeyCode.S, false, false, false, false));
                case "hold" -> inputHandler.onKeyPressed(
                    new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "h", "h", javafx.scene.input.KeyCode.H, false, false, false, false));
                case "patrol" -> inputHandler.onKeyPressed(
                    new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "p", "p", javafx.scene.input.KeyCode.P, false, false, false, false));
                case "build" -> inputHandler.onKeyPressed(
                    new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "b", "b", javafx.scene.input.KeyCode.B, false, false, false, false));
                default -> LOG.warn("Unknown HUD action: {}", action);
            }
        });

        // Input handler command callback
        inputHandler.setCommandCallback((command, targetGx, targetGy) -> {
            LOG.info("Command: {} at ({}, {}), selected: {}",
                command, targetGx, targetGy, selectionManager.getSelectedIds());
            // Commands will be processed by the game loop in a future phase
        });

        // Minimap click callback
        minimapRenderer.setCameraUpdateCallback(() -> {
            int[] grid = minimapRenderer.getLastClickGrid();
            if (grid != null) {
                cameraController.centerOnGrid(grid[0], grid[1]);
            }
        });
    }

    /**
     * Initializes the game scene with a map and entity manager.
     * Creates a test map with test entities for demonstration.
     */
    public void initializeGame() {
        // Create test map
        this.map = GameMap.createTestMap();
        this.entityManager = new EntityManager();
        this.gameState = new GameState();
        this.credits = 1000;

        // Connect subsystems to data
        isoRenderer.setMap(map);
        isoRenderer.centerOnMap(CANVAS_WIDTH, CANVAS_HEIGHT);
        cameraController.setMap(map);
        cameraController.setViewportSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        cameraController.centerOnGrid(map.getWidth() / 2, map.getHeight() / 2);
        minimapRenderer.setMap(map);
        minimapRenderer.setEntityManager(entityManager);
        entityRenderer.setSelectedEntityIds(selectionManager.getSelectedIds());
        selectionManager.setEntityManager(entityManager);
        selectionManager.setPlayerFaction(Faction.CONFEDERATION);
        hud.setEntityManager(entityManager);
        hud.setPlayerFaction(Faction.CONFEDERATION);

        // Create test entities
        createTestEntities();

        // Create game loop
        this.gameLoop = new GameLoop(gameState, this::onGameTick);

        LOG.info("Game initialized with test map {}x{}", map.getWidth(), map.getHeight());
    }

    /**
     * Creates test entities for development and demonstration.
     */
    private void createTestEntities() {
        // Confederation units
        var confedInfantryStats = new com.aow2.common.model.UnitStats(
            com.aow2.common.model.UnitType.CONFED_INFANTRY, "Standard infantry", 80, 10,
            4, 2, 0, 5, 4, com.aow2.common.model.WeaponType.BULLET, 5, 60, 100, 5, 4, 0, 0, 0);

        var confedGrenadierStats = new com.aow2.common.model.UnitStats(
            com.aow2.common.model.UnitType.CONFED_GRENADIER, "Grenadier", 60, 25,
            3, 1, 0, 5, 5, com.aow2.common.model.WeaponType.ROCKET, 8, 90, 150, 8, 2, 0, 0, 0);

        // Resistance units
        var rebelInfantryStats = new com.aow2.common.model.UnitStats(
            com.aow2.common.model.UnitType.REBEL_INFANTRY, "Rebel infantry", 70, 9,
            5, 1, 0, 5, 4, com.aow2.common.model.WeaponType.BULLET, 5, 50, 80, 4, 3, 0, 0, 0);

        var rebelSniperStats = new com.aow2.common.model.UnitStats(
            com.aow2.common.model.UnitType.REBEL_SNIPER, "Sniper", 50, 35,
            3, 0, 0, 7, 7, com.aow2.common.model.WeaponType.SNIPER_RIFLE, 15, 120, 200, 10, 1, 0, 0, 0);

        // Place Confederation units on the left side
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(1, 1), com.aow2.common.model.UnitType.CONFED_INFANTRY, confedInfantryStats));
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(2, 1), com.aow2.common.model.UnitType.CONFED_INFANTRY, confedInfantryStats));
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(1, 2), com.aow2.common.model.UnitType.CONFED_GRENADIER, confedGrenadierStats));

        // Place Resistance units on the right side
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(6, 6), com.aow2.common.model.UnitType.REBEL_INFANTRY, rebelInfantryStats));
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(5, 5), com.aow2.common.model.UnitType.REBEL_SNIPER, rebelSniperStats));

        // Confederation building
        var commandCentreStats = new com.aow2.common.model.BuildingStats(
            com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE, 500, 0, 0, 5,
            0, 10, 600, 6, 5, 0, 10, 1, 0, 0, 0, 0, com.aow2.common.model.WeaponType.NONE, java.util.List.of());
        entityManager.addBuilding(new com.aow2.core.entity.Building(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(0, 0), com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE,
            commandCentreStats));

        // Resistance headquarters
        var headquartersStats = new com.aow2.common.model.BuildingStats(
            com.aow2.common.model.BuildingType.REBEL_HEADQUARTERS, 450, 0, 0, 4,
            0, 10, 500, 6, 4, 0, 10, 1, 0, 0, 0, 0, com.aow2.common.model.WeaponType.NONE, java.util.List.of());
        entityManager.addBuilding(new com.aow2.core.entity.Building(
            entityManager.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(7, 7), com.aow2.common.model.BuildingType.REBEL_HEADQUARTERS,
            headquartersStats));

        LOG.info("Test entities created: {} units, {} buildings",
            entityManager.unitCount(), entityManager.buildingCount());
    }

    /**
     * Game tick callback from the GameLoop.
     */
    private void onGameTick() {
        // Process game logic each tick
        // For Phase 2, we just update the game state
        // Combat and movement will be added in later phases
    }

    /**
     * Starts the game and rendering loop.
     */
    public void start() {
        if (gameLoop != null) {
            gameLoop.start();
        }

        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        renderTimer.start();

        gameCanvas.requestFocus();
        LOG.info("Game scene started");
    }

    /**
     * Stops the game and rendering loop.
     */
    public void stop() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        if (renderTimer != null) {
            renderTimer.stop();
        }
        LOG.info("Game scene stopped");
    }

    /**
     * Main render method called each frame.
     */
    private void render() {
        // Update camera
        cameraController.update();

        // Update input handler camera transform
        inputHandler.setCameraTransform(
            cameraController.getCameraX(), cameraController.getCameraY(), cameraController.getZoom());

        // Update selection (remove dead entities)
        selectionManager.cleanSelection();

        // Sync selection to entity renderer
        entityRenderer.setSelectedEntityIds(selectionManager.getSelectedIds());

        // Clear canvas
        gc.setFill(Color.rgb(15, 18, 22));
        gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        // Render isometric map
        isoRenderer.render(gc, cameraController.getCameraX(), cameraController.getCameraY(),
            cameraController.getZoom());

        // Render entities
        entityRenderer.render(gc, entityManager, cameraController.getCameraX(),
            cameraController.getCameraY(), cameraController.getZoom());

        // Render drag selection box
        renderDragBox(gc);

        // Render minimap
        minimapRenderer.render(cameraController);

        // Update HUD
        hud.update(credits, gameState != null ? gameState.currentTick() : 0,
            selectionManager.getSelectedIds());
    }

    /**
     * Renders the drag selection box if the player is dragging.
     *
     * @param gc graphics context
     */
    private void renderDragBox(GraphicsContext gc) {
        double[] dragBox = selectionManager.getDragBox();
        if (dragBox == null) {
            return;
        }

        double x1 = Math.min(dragBox[0], dragBox[2]);
        double y1 = Math.min(dragBox[1], dragBox[3]);
        double w = Math.abs(dragBox[2] - dragBox[0]);
        double h = Math.abs(dragBox[3] - dragBox[1]);

        // Semi-transparent fill
        gc.setFill(Color.rgb(0, 255, 0, 0.1));
        gc.fillRect(x1, y1, w, h);

        // Green border
        gc.setStroke(Color.rgb(0, 255, 0, 0.7));
        gc.setLineWidth(1.0);
        gc.strokeRect(x1, y1, w, h);
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
     * Gets the game canvas.
     *
     * @return the game canvas
     */
    public Canvas getGameCanvas() {
        return gameCanvas;
    }

    /**
     * Gets the selection manager.
     *
     * @return the selection manager
     */
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    /**
     * Gets the camera controller.
     *
     * @return the camera controller
     */
    public CameraController getCameraController() {
        return cameraController;
    }
}
