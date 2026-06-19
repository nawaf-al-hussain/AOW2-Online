package com.aow2.client.scene;

import com.aow2.client.input.InputHandler;
import com.aow2.client.input.SelectionManager;
import com.aow2.client.render.CameraController;
import com.aow2.client.render.EntityRenderer;
import com.aow2.client.render.FogRenderer;
import com.aow2.client.render.IsometricRenderer;
import com.aow2.client.render.MinimapRenderer;
import com.aow2.client.render.SpriteManager;
import com.aow2.client.ui.HUD;
import com.aow2.common.config.GameConstants;
import com.aow2.common.config.StatsRegistry;
import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.combat.ProjectileSystem;
import com.aow2.core.command.CommandProcessor;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.PowerSystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.engine.GameLoop;
import com.aow2.core.engine.GameState;
import com.aow2.core.engine.TickManager;
import com.aow2.core.movement.CollisionSystem;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.movement.PathfindingSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.FogOfWarSystem;
import com.aow2.core.world.GameMap;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
 * - Integrates with TickManager from aow2-core for full game simulation
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3 - Architecture Overview
 */
public class GameScene {

    private static final Logger LOG = LoggerFactory.getLogger(GameScene.class);

    /** Default canvas width. */
    private static final int CANVAS_WIDTH = 1280;

    /** Default canvas height. */
    private static final int CANVAS_HEIGHT = 720;

    /** Player ID for the local player (always 0 = CONFEDERATION in local play). */
    private static final int LOCAL_PLAYER_ID = 0;

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

    /** The tick manager orchestrating all game systems. */
    private TickManager tickManager;

    /** The economy system. */
    private EconomySystem economy;

    /** The movement system. */
    private MovementSystem movement;

    /** The combat system. */
    private CombatSystem combat;

    /** The production system. */
    private ProductionSystem production;

    /** The research system. */
    private ResearchSystem research;

    /** The building placement system. */
    private BuildingPlacementSystem placement;

    /** The pathfinding system. */
    private PathfindingSystem pathfinding;

    /** The projectile system. */
    private ProjectileSystem projectiles;

    /** The fog of war renderer. */
    private FogRenderer fogRenderer;

    /** The fog of war system. */
    private FogOfWarSystem fogOfWarSystem;

    /** The power system. */
    private PowerSystem powerSystem;

    /** The JavaFX animation timer for rendering. */
    private AnimationTimer renderTimer;

    /** Current player credits (synced from EconomySystem each tick). */
    private int credits;

    /** Whether the sprite manager has been initialized for this scene. */
    private boolean spritesInitialized;

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
        this.fogRenderer = new FogRenderer(isoRenderer);
        this.fogOfWarSystem = new FogOfWarSystem();
        this.minimapRenderer = new MinimapRenderer();
        this.hud = new HUD();
        this.selectionManager = new SelectionManager();
        this.inputHandler = new InputHandler(selectionManager, cameraController, isoRenderer);

        // REF: GameConstants.STARTING_CREDITS = 100
        this.credits = GameConstants.STARTING_CREDITS;

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
     * Wires the InputHandler command callback to create CommandType objects
     * and enqueue them via the TickManager for deterministic processing.
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

        // Input handler command callback — creates CommandType objects and enqueues via TickManager
        inputHandler.setCommandCallback((command, targetGx, targetGy) -> {
            if (tickManager == null || gameState == null || entityManager == null) {
                LOG.warn("Cannot issue command: game systems not initialized");
                return;
            }
            if (!selectionManager.hasSelection()) {
                return;
            }

            int[] selectedIds = selectionManager.getSelectedIds().stream()
                .mapToInt(Integer::intValue).toArray();
            if (selectedIds.length == 0) {
                return;
            }

            long tick = gameState.currentTick();
            GridPosition targetPos = new GridPosition(targetGx, targetGy);

            CommandType cmd = switch (command) {
                case "move" -> {
                    // Check if there's an enemy entity at the target position
                    Unit enemyUnit = entityManager.findUnitAt(targetPos);
                    if (enemyUnit != null && enemyUnit.getFaction() != Faction.CONFEDERATION) {
                        yield new CommandType.Attack(tick, LOCAL_PLAYER_ID, selectedIds, enemyUnit.getId());
                    }
                    Building enemyBuilding = entityManager.findBuildingAt(targetPos);
                    if (enemyBuilding != null && enemyBuilding.getFaction() != Faction.CONFEDERATION) {
                        yield new CommandType.Attack(tick, LOCAL_PLAYER_ID, selectedIds, enemyBuilding.getId());
                    }
                    yield new CommandType.Move(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                }
                case "attack_move" -> {
                    // Attack-move: attack if enemy at target, otherwise move
                    Unit enemyUnit = entityManager.findUnitAt(targetPos);
                    if (enemyUnit != null && enemyUnit.getFaction() != Faction.CONFEDERATION) {
                        yield new CommandType.Attack(tick, LOCAL_PLAYER_ID, selectedIds, enemyUnit.getId());
                    }
                    Building enemyBuilding = entityManager.findBuildingAt(targetPos);
                    if (enemyBuilding != null && enemyBuilding.getFaction() != Faction.CONFEDERATION) {
                        yield new CommandType.Attack(tick, LOCAL_PLAYER_ID, selectedIds, enemyBuilding.getId());
                    }
                    yield new CommandType.Move(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                }
                case "stop" -> new CommandType.Stop(tick, LOCAL_PLAYER_ID, selectedIds);
                case "hold" -> new CommandType.Stop(tick, LOCAL_PLAYER_ID, selectedIds);
                case "patrol" -> new CommandType.Patrol(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                default -> null;
            };

            if (cmd != null) {
                tickManager.enqueueCommand(cmd);
                LOG.info("Enqueued command: {} at ({}, {}), selected: {}",
                    command, targetGx, targetGy, selectionManager.getSelectedIds());
            }
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
     * Initializes the game scene with a map, entity manager, and all core game systems.
     * Creates a test map with test entities for demonstration.
     */
    public void initializeGame() {
        // Initialize SpriteManager singleton early (must run on JavaFX thread)
        if (!SpriteManager.getInstance().isInitialized()) {
            SpriteManager.getInstance().initialize();
            isoRenderer.setSpriteManager(SpriteManager.getInstance());
            entityRenderer.setSpriteManager(SpriteManager.getInstance());
            spritesInitialized = true;
            LOG.info("SpriteManager initialized and wired to renderers");
        }

        // Create test map
        this.map = GameMap.createTestMap();
        this.entityManager = new EntityManager();
        this.gameState = new GameState();

        // REF: GameConstants.STARTING_CREDITS = 100
        this.credits = GameConstants.STARTING_CREDITS;

        // --- Instantiate core game systems ---

        // Economy: ResourceGenerator → EconomySystem
        ResourceGenerator resourceGenerator = new ResourceGenerator();
        this.economy = new EconomySystem(resourceGenerator);

        // Movement: PathfindingSystem + CollisionSystem → MovementSystem
        this.pathfinding = new PathfindingSystem();
        CollisionSystem collision = new CollisionSystem();
        this.movement = new MovementSystem(pathfinding, collision);

        // Combat: CombatSystem (internally creates ProjectileSystem and ArmorCalculator)
        this.combat = new CombatSystem(gameState, entityManager);
        this.projectiles = combat.getProjectileSystem();

        // Production, Research, Placement
        this.production = new ProductionSystem();
        this.research = new ResearchSystem();
        this.placement = new BuildingPlacementSystem();

        // Power system
        this.powerSystem = new PowerSystem();

        // Tick manager: orchestrates all systems per tick
        this.tickManager = new TickManager();

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

        // Initialize fog of war system and connect to renderer and tick manager
        fogOfWarSystem.initialize(map);
        fogRenderer.setFogOfWar(fogOfWarSystem);
        fogRenderer.setPlayerId(LOCAL_PLAYER_ID);
        tickManager.setFogOfWar(fogOfWarSystem);

        // Create test entities using StatsRegistry
        createTestEntities();

        // Update power grid for initial buildings
        powerSystem.updatePowerGrid(entityManager);

        // Create game loop
        this.gameLoop = new GameLoop(gameState, this::onGameTick);

        LOG.info("Game initialized with test map {}x{}, all core systems wired", map.getWidth(), map.getHeight());
    }

    /**
     * Creates test entities for development and demonstration.
     * Uses StatsRegistry for all unit and building stats instead of hardcoded values.
     */
    private void createTestEntities() {
        StatsRegistry registry = StatsRegistry.getInstance();

        // --- Confederation units ---
        // REF: StatsRegistry holds RE-verified stats for all 17 unit types
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(1, 1), com.aow2.common.model.UnitType.CONFED_INFANTRY,
            registry.getUnitStats(com.aow2.common.model.UnitType.CONFED_INFANTRY)));
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(2, 1), com.aow2.common.model.UnitType.CONFED_INFANTRY,
            registry.getUnitStats(com.aow2.common.model.UnitType.CONFED_INFANTRY)));
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(1, 2), com.aow2.common.model.UnitType.CONFED_GRENADIER,
            registry.getUnitStats(com.aow2.common.model.UnitType.CONFED_GRENADIER)));

        // --- Resistance units ---
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(6, 6), com.aow2.common.model.UnitType.REBEL_INFANTRY,
            registry.getUnitStats(com.aow2.common.model.UnitType.REBEL_INFANTRY)));
        entityManager.addUnit(new com.aow2.core.entity.Unit(
            entityManager.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(5, 5), com.aow2.common.model.UnitType.REBEL_SNIPER,
            registry.getUnitStats(com.aow2.common.model.UnitType.REBEL_SNIPER)));

        // --- Confederation building ---
        // REF: StatsRegistry holds RE-verified stats for all 16 building types
        entityManager.addBuilding(new com.aow2.core.entity.Building(
            entityManager.allocateEntityId(), Faction.CONFEDERATION,
            new GridPosition(0, 0), com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE,
            registry.getBuildingStats(com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE)));

        // --- Resistance headquarters ---
        entityManager.addBuilding(new com.aow2.core.entity.Building(
            entityManager.allocateEntityId(), Faction.RESISTANCE,
            new GridPosition(7, 7), com.aow2.common.model.BuildingType.REBEL_HEADQUARTERS,
            registry.getBuildingStats(com.aow2.common.model.BuildingType.REBEL_HEADQUARTERS)));

        LOG.info("Test entities created via StatsRegistry: {} units, {} buildings",
            entityManager.unitCount(), entityManager.buildingCount());
    }

    /**
     * Game tick callback from the GameLoop.
     * Delegates to TickManager for deterministic processing of all game systems,
     * then handles client-specific updates (construction progress, power grid, HUD sync).
     * <p>
     * TickManager processing order:
     * 1. Process commands
     * 2. Process movement
     * 3. Process combat
     * 4. Process production
     * 5. Process research
     * 6. Process economy
     * 7. Remove dead entities
     * 8. Advance tick
     */
    private void onGameTick() {
        if (gameState == null || !gameState.isRunning()) {
            return;
        }

        // Tick construction progress for all buildings under construction
        tickConstructionProgress();

        // Process all game systems in the correct order via TickManager
        tickManager.processTick(gameState, entityManager, map,
            movement, combat, economy, production, research, placement,
            pathfinding, projectiles);

        // Update power grid after entity changes
        powerSystem.updatePowerGrid(entityManager);

        // Sync credits from EconomySystem for HUD display
        this.credits = economy.getCredits(LOCAL_PLAYER_ID);
    }

    /**
     * Advances construction progress for all buildings under construction.
     * Each tick, buildings with constructionProgress &lt; buildTime get +1 progress.
     * When construction completes, the building becomes functional.
     * <p>
     * REF: complete_building_stats.json — buildTime per building type
     */
    private void tickConstructionProgress() {
        if (entityManager == null) {
            return;
        }
        for (com.aow2.core.entity.Building building : entityManager.getAllBuildings()) {
            if (building.isAlive() && building.isUnderConstruction()) {
                building.setConstructionProgress(building.getConstructionProgress() + 1);
                if (!building.isUnderConstruction()) {
                    LOG.info("Building {} ({}) construction complete at {}",
                        building.getId(), building.getBuildingType().displayName(), building.getPosition());
                    // Update power grid when a building completes construction
                    if (building.getBuildingType().producesPower()) {
                        powerSystem.updatePowerGrid(entityManager);
                    }
                }
            }
        }
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

        // Render fog of war overlay (after terrain, before entities)
        fogRenderer.render(gc, map, cameraController.getCameraX(),
            cameraController.getCameraY(), cameraController.getZoom());

        // Render entities
        entityRenderer.render(gc, entityManager, cameraController.getCameraX(),
            cameraController.getCameraY(), cameraController.getZoom());

        // Render drag selection box
        renderDragBox(gc);

        // Render minimap
        minimapRenderer.render(cameraController);

        // Update HUD with credits from EconomySystem
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

    /**
     * Gets the tick manager (for testing or external access).
     *
     * @return the tick manager, or null if not yet initialized
     */
    public TickManager getTickManager() {
        return tickManager;
    }

    /**
     * Gets the economy system (for testing or external access).
     *
     * @return the economy system, or null if not yet initialized
     */
    public EconomySystem getEconomy() {
        return economy;
    }
}
