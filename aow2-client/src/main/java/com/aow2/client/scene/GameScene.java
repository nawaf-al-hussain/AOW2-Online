package com.aow2.client.scene;

import com.aow2.client.audio.AudioManager;
import com.aow2.client.input.InputHandler;
import com.aow2.client.input.SelectionManager;
import com.aow2.client.render.CameraController;
import com.aow2.client.render.EntityRenderer;
import com.aow2.client.render.FogRenderer;
import com.aow2.client.render.IsometricRenderer;
import com.aow2.client.render.MinimapRenderer;
import com.aow2.client.render.SpriteManager;
import com.aow2.client.ui.HUD;
import com.aow2.core.campaign.CampaignEpisode;
import com.aow2.core.campaign.CampaignManager;
import com.aow2.core.campaign.Mission;
import com.aow2.core.campaign.Objective;
import com.aow2.core.campaign.ScriptEngine;
import com.aow2.core.campaign.Trigger;

import com.aow2.common.config.GameConstants;
import com.aow2.common.config.StatsRegistry;
import com.aow2.common.model.BuildingType;
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
import com.aow2.core.network.LockstepEngine;
import com.aow2.core.network.CommandSerializer;
import com.aow2.client.service.MultiplayerService;
import com.aow2.core.movement.CollisionSystem;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.movement.PathfindingSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.FogOfWarSystem;
import com.aow2.core.world.GameMap;
import com.aow2.core.world.MapLoader;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
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

    /** Player ID for the local player (always 0 in local play). */
    private static final int LOCAL_PLAYER_ID = 0;

    /** The local player's faction. FIX (L-NEW-9): Set from session config instead of hardcoded. */
    private Faction playerFaction = Faction.CONFEDERATION;

    /** Sets the player's faction for this game session. FIX (L-NEW-9): Allows server-assigned faction. */
    public void setPlayerFaction(Faction faction) {
        this.playerFaction = faction;
    }

    /**
     * @return the local player's faction
     */
    public Faction getPlayerFaction() { return playerFaction; }

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

    /** The audio manager for music and SFX. */
    private AudioManager audioManager;

    /** The JavaFX animation timer for rendering. */
    private AnimationTimer renderTimer;

    /** Current player credits (synced from EconomySystem each tick). */
    private int credits;

    /** Whether the sprite manager has been initialized for this scene. */
    private boolean spritesInitialized;

    /** Campaign manager for objective evaluation, or null if not a campaign mission. */
    private CampaignManager campaignManager;

    /** Campaign episode index, or -1 if not a campaign mission. */
    private int campaignEpisodeIndex = -1;

    /** Campaign mission index within the episode, or -1 if not a campaign mission. */
    private int campaignMissionIndex = -1;

    /** Mutable copy of the current mission's objectives, updated each tick. */
    private List<Objective> missionObjectives = new ArrayList<>();

    /** Whether the campaign mission result has already been handled. */
    private boolean campaignResultHandled = false;

    /** Callback invoked when a campaign mission ends (victory or defeat). */
    private Runnable campaignEndCallback;

    /** Mutable copy of the current mission's triggers, updated each tick. FIX(CAMP-2) */
    private List<Trigger> missionTriggers = new ArrayList<>();

    /** Whether this is a campaign mission (skip test entities). FIX(CAMP-5) */
    private boolean isCampaignMission = false;

    // --- Multiplayer / Lockstep Integration ---
    // FIX (LockstepEngine integration from AUDIT_ROUND_3.md): The LockstepEngine was
    // previously only instantiated in tests. These fields wire it into the runtime
    // so that multiplayer commands flow through the deterministic lockstep pipeline.

    /** The lockstep engine for multiplayer deterministic simulation, or null in single-player. */
    private LockstepEngine lockstepEngine;

    /** The multiplayer service for WebSocket communication, or null in single-player. */
    private MultiplayerService multiplayerService;

    /** Whether this game is a multiplayer match (uses lockstep). */
    private boolean isMultiplayer = false;

    /** FIX (ANALYSIS_V2 3.4): Session UUID for sync hash reporting. */
    private String multiplayerSessionUuid;

    /** Buffer for commands received from the opponent via WebSocket, pending lockstep processing. */
    private final java.util.concurrent.ConcurrentLinkedQueue<byte[]> incomingCommandBuffer =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Tracks the enemy kill count for DestroyObjectives. FIX(CAMP-1) */
    private int enemyKillCount = 0;

    /** Tracks previous tick's alive enemy count for kill detection. FIX(CAMP-1) */
    private int previousAliveEnemyCount = -1;

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

            // FIX (F-08): Cancel production — HUD fires "cancel_production:N" when
            // the player right-clicks production queue slot N. Parse the slot index
            // and issue a Cancel command for the selected producing building.
            // Handled before the switch because the switch uses constant case labels
            // and we need a prefix match.
            if (action != null && action.startsWith("cancel_production:")) {
                try {
                    int slotIndex = Integer.parseInt(action.substring("cancel_production:".length()));
                    if (selectionManager != null && selectionManager.hasSelection()) {
                        var selectedIds = selectionManager.getSelectedIds();
                        if (selectedIds.size() == 1) {
                            int buildingId = selectedIds.iterator().next();
                            var building = entityManager.getBuilding(buildingId);
                            if (building != null && building.isAlive()
                                    && building.getBuildingType().producesUnits()) {
                                long tick = gameState.currentTick();
                                var cmd = new CommandType.Cancel(tick, LOCAL_PLAYER_ID, buildingId);
                                tickManager.enqueueCommand(cmd);
                                if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
                                    lockstepEngine.submitCommand(cmd);
                                }
                                LOG.info("Cancel production command issued: building={} slot={}",
                                    buildingId, slotIndex);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid cancel_production action: {}", action);
                }
                return;
            }

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
                case "upgrade" -> {
                    // FIX (Building Upgrade UI): When the Upgrade button is pressed,
                    // check if a single building is selected and issue an UpgradeCommand.
                    if (selectionManager != null && selectionManager.hasSelection()) {
                        var selectedIds = selectionManager.getSelectedIds();
                        if (selectedIds.size() == 1) {
                            int buildingId = selectedIds.iterator().next();
                            var building = entityManager.getBuilding(buildingId);
                            if (building != null && building.isAlive()
                                    && !building.isUnderConstruction()
                                    && building.getUpgradeLevel() < 3) {
                                long tick = gameState.currentTick();
                                var cmd = new CommandType.Upgrade(tick, LOCAL_PLAYER_ID, buildingId);
                                tickManager.enqueueCommand(cmd);
                                if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
                                    lockstepEngine.submitCommand(cmd);
                                }
                                LOG.info("Upgrade command issued for building {} (current level: {})",
                                    buildingId, building.getUpgradeLevel());
                            } else if (building != null) {
                                LOG.debug("Cannot upgrade building {}: alive={}, underConstruction={}, level={}",
                                    buildingId, building.isAlive(), building.isUnderConstruction(),
                                    building != null ? building.getUpgradeLevel() : -1);
                            }
                        }
                    }
                }
                case "produce" -> {
                    // FIX (F-04): When the Produce button is pressed, check if a single
                    // producing building is selected and show the production dialog.
                    if (selectionManager != null && selectionManager.hasSelection()) {
                        var selectedIds = selectionManager.getSelectedIds();
                        if (selectedIds.size() == 1) {
                            int buildingId = selectedIds.iterator().next();
                            var building = entityManager.getBuilding(buildingId);
                            if (building != null && building.isAlive()
                                    && !building.isUnderConstruction()
                                    && building.getBuildingType().producesUnits()) {
                                showProductionDialog(buildingId, building.getBuildingType());
                            } else if (building != null) {
                                LOG.debug("Cannot produce from building {}: producesUnits={}",
                                    buildingId, building.getBuildingType().producesUnits());
                            }
                        }
                    }
                }
                case "research" -> {
                    // FIX (F-05): When the Research button is pressed, check if a single
                    // tech centre / laboratory is selected and show the research dialog.
                    if (selectionManager != null && selectionManager.hasSelection()) {
                        var selectedIds = selectionManager.getSelectedIds();
                        if (selectedIds.size() == 1) {
                            int buildingId = selectedIds.iterator().next();
                            var building = entityManager.getBuilding(buildingId);
                            if (building != null && building.isAlive()
                                    && !building.isUnderConstruction()
                                    && building.getBuildingType().researches()) {
                                showResearchDialog(buildingId, building.getBuildingType());
                            } else if (building != null) {
                                LOG.debug("Cannot research at building {}: researches={}",
                                    buildingId, building.getBuildingType().researches());
                            }
                        }
                    }
                }
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
                    if (enemyUnit != null && enemyUnit.getFaction() != playerFaction) {
                        yield new CommandType.Attack(tick, LOCAL_PLAYER_ID, selectedIds, enemyUnit.getId());
                    }
                    Building enemyBuilding = entityManager.findBuildingAt(targetPos);
                    if (enemyBuilding != null && enemyBuilding.getFaction() != playerFaction) {
                        yield new CommandType.Attack(tick, LOCAL_PLAYER_ID, selectedIds, enemyBuilding.getId());
                    }
                    yield new CommandType.Move(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                }
                // Shift+right-click waypoint queuing — issues a Move that doesn't clear
                // existing orders. The unit's MovementSystem will queue this as the next
                // destination after the current one is reached.
                case "queued_move" -> {
                    yield new CommandType.Move(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                }
                case "queued_attack_move" -> {
                    yield new CommandType.AttackMove(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                }
                case "attack_move" -> {
                    // Attack-move: move to target, engaging enemies along the way
                    yield new CommandType.AttackMove(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                }
                case "stop" -> new CommandType.Stop(tick, LOCAL_PLAYER_ID, selectedIds);
                // FIX (F-11): Hold is now a distinct command — clears path but retains
                // attack target so units can attack enemies in range without moving.
                case "hold" -> new CommandType.Hold(tick, LOCAL_PLAYER_ID, selectedIds);
                case "patrol" -> new CommandType.Patrol(tick, LOCAL_PLAYER_ID, selectedIds, targetPos);
                case "build" -> {
                    BuildingType buildType = inputHandler.getPendingBuildType();
                    if (buildType != null) {
                        yield new CommandType.Build(tick, LOCAL_PLAYER_ID, buildType, targetPos);
                    } else {
                        LOG.warn("Build command issued but no building type selected");
                        yield null;
                    }
                }
                // FIX (F-06): Garrison command — right-click on a friendly bunker while
                // in GARRISON mode issues a Garrison command with the selected unit IDs.
                case "garrison" -> {
                    Building targetBuilding = entityManager.findBuildingAt(targetPos);
                    if (targetBuilding != null && targetBuilding.getFaction() == playerFaction
                            && (targetBuilding.getBuildingType() == BuildingType.CONFED_BUNKER
                                || targetBuilding.getBuildingType() == BuildingType.REBEL_BUNKER)) {
                        yield new CommandType.Garrison(tick, LOCAL_PLAYER_ID, selectedIds, targetBuilding.getId());
                    }
                    LOG.warn("Garrison command: no friendly bunker at ({}, {})", targetGx, targetGy);
                    yield null;
                }
                // FIX (F-07): Siege mode toggle — issues a SiegeMode command for each
                // selected siege-capable unit (Fortress, Hammer, Torrent, Rhino, Sniper).
                case "siege_mode" -> {
                    // Toggle siege mode for each selected unit that is siege-capable.
                    for (int id : selectedIds) {
                        Unit u = entityManager.getUnit(id);
                        if (u != null && u.isAlive() && u.getUnitType().isSiegeCapable()) {
                            boolean newState = !u.isSiegeMode();
                            var siegeCmd = new CommandType.SiegeMode(tick, LOCAL_PLAYER_ID, id, newState);
                            tickManager.enqueueCommand(siegeCmd);
                            if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
                                lockstepEngine.submitCommand(siegeCmd);
                            }
                            LOG.info("Siege mode toggle for unit {}: {}", id, newState);
                        }
                    }
                    yield null;  // commands already enqueued in the loop
                }
                default -> null;
            };

            if (cmd != null) {
                tickManager.enqueueCommand(cmd);
                // FIX (LockstepEngine integration): In multiplayer mode, also submit the
                // command to the lockstep engine so it gets relayed to the opponent.
                if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
                    lockstepEngine.submitCommand(cmd);
                }
                LOG.info("Enqueued command: {} at ({}, {}), selected: {}",
                    command, targetGx, targetGy, selectionManager.getSelectedIds());
            }
        });

        // Camera action callback — handles Space (jump to event) and Home (center on base)
        inputHandler.setCameraActionCallback(action -> {
            switch (action) {
                case "jump_to_event" -> {
                    // Jump to the last alert event (unit under attack, building complete, etc.)
                    // For now, center on the first friendly unit that is in combat
                    var units = entityManager.getAliveUnitsForPlayer(playerFaction);
                    if (!units.isEmpty()) {
                        var u = units.get(0);
                        cameraController.centerOnGrid(u.getPosition().x(), u.getPosition().y());
                        LOG.debug("Space: jumped to unit at {}", u.getPosition());
                    }
                }
                case "center_on_base" -> {
                    // Center on the player's Command Centre / Headquarters
                    var buildings = entityManager.getBuildingsForPlayer(playerFaction);
                    for (var b : buildings) {
                        if (b.getBuildingType() == com.aow2.common.model.BuildingType.CONFED_COMMAND_CENTRE
                            || b.getBuildingType() == com.aow2.common.model.BuildingType.REBEL_HEADQUARTERS) {
                            cameraController.centerOnGrid(b.getPosition().x(), b.getPosition().y());
                            LOG.debug("Home: centered on base at {}", b.getPosition());
                            return;
                        }
                    }
                    LOG.debug("Home: no Command Centre / Headquarters found");
                }
            }
        });

        // Build type callback — shows a dialog to pick which building to construct
        inputHandler.setBuildTypeCallback(() -> {
            // Build the list of available buildings based on player faction
            List<BuildingType> availableBuildings = switch (playerFaction) {
                case CONFEDERATION -> List.of(
                    BuildingType.CONFED_INFANTRY_CENTRE, BuildingType.CONFED_MACHINE_FACTORY,
                    BuildingType.CONFED_BUNKER, BuildingType.CONFED_TECH_CENTRE,
                    BuildingType.CONFED_GENERATOR, BuildingType.CONFED_ROCKET_LAUNCHER,
                    BuildingType.CONFED_LOCATOR
                );
                case RESISTANCE -> List.of(
                    BuildingType.REBEL_BARRACKS, BuildingType.REBEL_FACTORY,
                    BuildingType.REBEL_BUNKER, BuildingType.REBEL_LABORATORY,
                    BuildingType.REBEL_POWERPLANT, BuildingType.REBEL_TOWER,
                    BuildingType.REBEL_WALL
                );
                default -> List.of();
            };

            if (availableBuildings.isEmpty()) {
                LOG.warn("No buildings available for faction: {}", playerFaction);
                return;
            }

            ChoiceDialog<BuildingType> dialog =
                new ChoiceDialog<>(availableBuildings.get(0), availableBuildings);
            dialog.setTitle("Build Structure");
            dialog.setHeaderText("Select building type:");
            dialog.initOwner(gameCanvas.getScene().getWindow());
            var result = dialog.showAndWait();
            result.ifPresent(type -> {
                inputHandler.setPendingBuildType(type);
                inputHandler.setCommandMode(InputHandler.CommandMode.BUILD_PLACEMENT);
                LOG.debug("Build mode: selected {}", type);
            });
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
     * Sets up the lockstep engine for multiplayer deterministic simulation.
     * <p>
     * FIX (LockstepEngine integration from AUDIT_ROUND_3.md): Wires the LockstepEngine
     * into the runtime so that:
     * 1. Local commands are submitted to the lockstep engine (which serializes and
     *    sends them to the opponent via the sendCallback).
     * 2. Opponent commands received via WebSocket are buffered in {@link #incomingCommandBuffer}
     *    and fed into the lockstep engine during {@link #onGameTick()}.
     * 3. The lockstep engine's {@code processFrame()} drains the command buffer and
     *    returns the commands for the current tick, which are enqueued into TickManager.
     * 4. Heartbeats are sent every 30 ticks via the heartbeat callback to prevent
     *    false disconnect pauses when the local player is idle (H2-client fix).
     * 5. Sync hashes are computed and sent to the opponent for desync detection.
     * <p>
     * This method should be called after {@link #initializeGame()} but before
     * {@link #startGameLoop()} when starting a multiplayer match.
     *
     * @param service the multiplayer service with an active game WebSocket connection
     */
    public void setupMultiplayer(MultiplayerService service) {
        this.multiplayerSessionUuid = null;  // will be set via setMultiplayerSessionUuid
        setupMultiplayerInternal(service);
    }

    /**
     * FIX (ANALYSIS_V2 3.4): Sets the multiplayer session UUID for sync hash reporting.
     *
     * @param sessionUuid the session UUID
     */
    public void setMultiplayerSessionUuid(String sessionUuid) {
        this.multiplayerSessionUuid = sessionUuid;
    }

    private void setupMultiplayerInternal(MultiplayerService service) {
        this.multiplayerService = service;
        this.isMultiplayer = true;
        this.lockstepEngine = new LockstepEngine();

        // Wire the send callback: when the lockstep engine serializes a command,
        // send it to the opponent via the game WebSocket.
        lockstepEngine.start(serializedCommand -> {
            // The sendCallback receives binary CommandSerializer bytes.
            // We wrap them in a JSON message for the WebSocket transport.
            if (multiplayerService != null) {
                multiplayerService.sendGameCommand(java.util.Map.of(
                    "type", "command",
                    "data", java.util.Base64.getEncoder().encodeToString(serializedCommand)
                ));
            }
        });

        // FIX (H2-client): Wire the heartbeat callback so heartbeats are actually
        // sent over the wire. Without this, sendHeartbeat() falls through to a
        // debug log and no heartbeat reaches the opponent.
        lockstepEngine.setHeartbeatSendCallback(tick -> {
            if (multiplayerService != null) {
                try {
                    multiplayerService.sendGameCommand(java.util.Map.of(
                        "type", "heartbeat",
                        "tick", tick
                    ));
                } catch (Exception e) {
                    LOG.warn("Failed to send heartbeat at tick {}: {}", tick, e.getMessage());
                }
            }
        });

        // Wire the desync callback
        lockstepEngine.setDesyncCallback(frame -> {
            LOG.error("Desync detected at frame {}", frame);
            if (multiplayerService != null) {
                // FIX (ANALYSIS_V2 3.4): Send the actual session UUID and a non-zero hash
                // so the server can identify which session desynced. Previously sent
                // empty string and hash=0, which the server silently dropped.
                multiplayerService.sendSyncHash(
                    multiplayerSessionUuid != null ? multiplayerSessionUuid : "",
                    frame, -1);  // -1 = desync indicator
            }
        });

        // Inject game systems into the lockstep engine for command processing
        lockstepEngine.setGameSystems(map, movement, combat, economy,
            production, research, placement);

        // Wire the MultiplayerService callback to receive opponent commands
        service.setCallback(new MultiplayerService.MultiplayerCallback() {
            @Override
            public void onMatchFound(String sessionUuid, String opponentName) {}

            @Override
            public void onPlayerConnected(long playerId) {}

            @Override
            public void onCommandReceived(long fromPlayerId, java.util.Map<String, Object> command) {
                // FIX (H2-client): Check if this is a heartbeat message (forwarded by
                // MultiplayerService as a command-like map with type="heartbeat").
                String msgType = (String) command.getOrDefault("type", "");
                if ("heartbeat".equals(msgType)) {
                    // Feed the heartbeat into the lockstep engine to reset the disconnect timer
                    long tick = ((Number) command.getOrDefault("tick", 0L)).longValue();
                    if (lockstepEngine != null && lockstepEngine.isRunning()) {
                        lockstepEngine.receiveHeartbeat(tick);
                    }
                    return;
                }
                // Otherwise it's a command: the command map contains a "data" field
                // with base64-encoded CommandSerializer bytes. Decode and buffer it.
                Object data = command.get("data");
                if (data instanceof String base64) {
                    try {
                        byte[] serialized = java.util.Base64.getDecoder().decode(base64);
                        incomingCommandBuffer.add(serialized);
                    } catch (Exception e) {
                        LOG.warn("Failed to decode opponent command: {}", e.getMessage());
                    }
                } else if (data != null) {
                    LOG.warn("Unexpected command data type: {}", data.getClass());
                }
            }

            @Override
            public void onDesyncDetected(long tick) {
                LOG.error("Opponent reported desync at tick {}", tick);
            }

            @Override
            public void onChatMessage(String senderName, String message) {}

            @Override
            public void onError(String error) {
                LOG.error("Multiplayer error: {}", error);
            }
        });

        LOG.info("Multiplayer lockstep engine initialized and wired");
    }

    /**
     * Initializes the game scene with a map, entity manager, and all core game systems.
     * Creates a test map with test entities for demonstration.
     */
    public void initializeGame() {
        // FIX(M-33): AudioManager initialized and wired for background music and SFX playback.
        // AudioManager is created on the JavaFX thread. Audio resources are loaded lazily
        // from /audio/music/ and /audio/sfx/ on the classpath. If audio files are absent,
        // the manager logs warnings and degrades gracefully (no crash).
        this.audioManager = new AudioManager();

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
        selectionManager.setPlayerFaction(playerFaction);
        hud.setEntityManager(entityManager);
        hud.setPlayerFaction(playerFaction);

        // Initialize fog of war system and connect to renderer and tick manager
        fogOfWarSystem.initialize(map);
        fogRenderer.setFogOfWar(fogOfWarSystem);
        fogRenderer.setPlayerId(LOCAL_PLAYER_ID);
        tickManager.setFogOfWar(fogOfWarSystem);

        // FIX(CAMP-5): Only create test entities for non-campaign games.
        // Campaign maps define their own entities in the map JSON.
        if (!isCampaignMission) {
            createTestEntities();
        }

        // Update power grid for initial buildings
        powerSystem.updatePowerGrid(entityManager);

        // Create game loop
        this.gameLoop = new GameLoop(gameState, this::onGameTick);

        LOG.info("Game initialized with test map {}x{}, all core systems wired", map.getWidth(), map.getHeight());
    }

    /**
     * Initializes the game scene with a map loaded from a classpath resource.
     * Falls back to the test map if the resource is not found.
     * Still creates test entities (map-specific entities will come with the campaign system).
     *
     * @param mapResourcePath classpath resource path (e.g., "data/maps/test_map.json")
     */
    public void initializeGame(String mapResourcePath) {
        // FIX(M-33): AudioManager initialized and wired for background music and SFX playback.
        this.audioManager = new AudioManager();

        // Initialize SpriteManager singleton early (must run on JavaFX thread)
        if (!SpriteManager.getInstance().isInitialized()) {
            SpriteManager.getInstance().initialize();
            isoRenderer.setSpriteManager(SpriteManager.getInstance());
            entityRenderer.setSpriteManager(SpriteManager.getInstance());
            spritesInitialized = true;
            LOG.info("SpriteManager initialized and wired to renderers");
        }

        // Load map from resource, fallback to test map
        try {
            this.map = MapLoader.loadFromResource(mapResourcePath);
            LOG.info("Loaded map from resource: {}", mapResourcePath);
        } catch (Exception e) {
            LOG.warn("Failed to load map resource '{}': {}. Falling back to test map",
                mapResourcePath, e.getMessage());
            this.map = GameMap.createTestMap();
        }

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
        selectionManager.setPlayerFaction(playerFaction);
        hud.setEntityManager(entityManager);
        hud.setPlayerFaction(playerFaction);

        // Initialize fog of war system and connect to renderer and tick manager
        fogOfWarSystem.initialize(map);
        fogRenderer.setFogOfWar(fogOfWarSystem);
        fogRenderer.setPlayerId(LOCAL_PLAYER_ID);
        tickManager.setFogOfWar(fogOfWarSystem);

        // FIX(CAMP-5): Only create test entities for non-campaign games.
        // Campaign maps define their own entities in the map JSON.
        if (!isCampaignMission) {
            createTestEntities();
        }

        // Update power grid for initial buildings
        powerSystem.updatePowerGrid(entityManager);

        // Create game loop
        this.gameLoop = new GameLoop(gameState, this::onGameTick);

        LOG.info("Game initialized with map {}x{} (resource: {}), all core systems wired",
            map.getWidth(), map.getHeight(), mapResourcePath);
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

        // FIX (LockstepEngine integration): In multiplayer mode, drain incoming
        // commands from the WebSocket receive thread into the lockstep engine,
        // then process one lockstep frame. The lockstep engine's processFrame()
        // returns the list of commands for this tick (both local and opponent),
        // which we enqueue into the TickManager for processing.
        if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
            // Drain WebSocket receive buffer into the lockstep engine
            byte[] incoming;
            while ((incoming = incomingCommandBuffer.poll()) != null) {
                lockstepEngine.receiveCommand(incoming);
            }
            // Process one lockstep frame — returns commands for this tick
            var frameCommands = lockstepEngine.processFrame(gameState, entityManager);
            // Enqueue opponent commands (playerId != LOCAL_PLAYER_ID) into TickManager
            for (CommandType cmd : frameCommands) {
                if (cmd.playerId() != LOCAL_PLAYER_ID) {
                    tickManager.enqueueCommand(cmd);
                }
            }
        }

        // Tick construction progress for all buildings under construction
        tickConstructionProgress();

        // Process all game systems in the correct order via TickManager
        tickManager.processTick(gameState, entityManager, map,
            movement, combat, economy, production, research, placement,
            pathfinding, projectiles);

        // Update power grid after entity changes
        powerSystem.updatePowerGrid(entityManager);

        // SOUND INTEGRATION: Process game events for audio playback
        if (audioManager != null) {
            for (var event : gameState.drainEvents()) {
                processAudioEvent(event);
            }
        }

        // Sync credits from EconomySystem for HUD display
        this.credits = economy.getCredits(LOCAL_PLAYER_ID);

        // FIX(CAMP-1): Track enemy kills for DestroyObjectives
        trackEnemyKills();

        // Check campaign objectives if this is a campaign mission
        checkCampaignObjectives();

        // FIX(CAMP-2+3): Process triggers and script engine each tick in campaign mode
        if (isCampaignMission && campaignManager != null) {
            processCampaignTriggers();
            ScriptEngine se = campaignManager.getScriptEngine();
            if (se.isScriptActive()) {
                se.processTick(gameState, entityManager);
            }
        }
    }

    /**
     * SOUND INTEGRATION: Processes game events and plays the appropriate SFX.
     * Only plays sounds for events visible to the local player (fog of war check).
     */
    private void processAudioEvent(com.aow2.common.event.GameEvent event) {
        if (audioManager == null) return;

        switch (event) {
            case com.aow2.common.event.UnitKilledEvent e -> {
                // Play scream for infantry, explosion for vehicles
                var killer = entityManager.getUnit(e.killerId());
                var victim = entityManager.getUnit(e.unitId());
                // Only play if the victim was visible to the local player
                if (victim != null) {
                    if (victim.isInfantry()) {
                        audioManager.playScream();
                    } else {
                        audioManager.playExplosionHeavy();
                    }
                }
            }
            case com.aow2.common.event.BuildingDestroyedEvent e -> {
                audioManager.playExplosionBuilding();
            }
            case com.aow2.common.event.BuildingCompletedEvent e -> {
                // Only play for the local player's buildings
                var building = entityManager.getBuilding(e.buildingId());
                if (building != null && building.getFaction() == playerFaction) {
                    audioManager.playBuildingReady();
                }
            }
            case com.aow2.common.event.UnitProducedEvent e -> {
                // Only play for the local player's units
                if (e.playerId() == LOCAL_PLAYER_ID) {
                    audioManager.playAffirmative();
                }
            }
            case com.aow2.common.event.ResearchCompletedEvent e -> {
                if (e.playerId() == LOCAL_PLAYER_ID) {
                    audioManager.playResearchComplete();
                }
            }
            case com.aow2.common.event.DamageAppliedEvent e -> {
                // Play weapon sound for the attacker (only for visible combat)
                var attacker = entityManager.getUnit(e.attackerId());
                if (attacker != null && attacker.getFaction() == playerFaction) {
                    audioManager.playWeaponSound(attacker.getStats().weaponType());
                }
            }
            case com.aow2.common.event.ResourceChangedEvent e -> {
                if (e.playerId() == LOCAL_PLAYER_ID && e.newCredits() > e.oldCredits()) {
                    audioManager.playMoney();
                }
            }
            default -> { /* unhandled event type */ }
        }
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
                    // FIX (H-NEW-14): Play build completion SFX
                    if (audioManager != null) {
                        audioManager.playBuildingReady();
                    }
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

        // SOUND INTEGRATION: Start background music and preload common SFX
        if (audioManager != null) {
            // Play the original iOS background music
            audioManager.playMusic("music");
            // Preload commonly used SFX (by exact iOS filename)
            audioManager.preloadSFX("select_1");
            audioManager.preloadSFX("select_2");
            audioManager.preloadSFX("building_ready_1");
            audioManager.preloadSFX("build_1");
            audioManager.preloadSFX("research_complete_1");
            audioManager.preloadSFX("money_1");
            audioManager.preloadSFX("click_1");
            audioManager.preloadSFX("menu_open_1");
            audioManager.preloadSFX("menu_close_1");
            audioManager.preloadSFX("explode_heavy_1");
            audioManager.preloadSFX("explode_light_1");
            audioManager.preloadSFX("scream_1");
            audioManager.preloadSFX("machine_light_1");
            audioManager.preloadSFX("sniper_1");
            audioManager.preloadSFX("tank_heavy_1");
            audioManager.preloadSFX("rocket_light_1");
            audioManager.preloadSFX("flamethrower_1");
        }

        if (renderTimer != null) {
            renderTimer.stop();
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
        // FIX (H-NEW-14): Stop all audio when leaving the game scene
        if (audioManager != null) {
            audioManager.stopAll();
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
     * Gets the audio manager (for SFX from HUD and other subsystems).
     * FIX (H-NEW-14): Exposed for UI sound wiring.
     *
     * @return the audio manager, or null if not yet initialized
     */
    public AudioManager getAudioManager() {
        return audioManager;
    }

    /**
     * Gets the economy system (for testing or external access).
     *
     * @return the economy system, or null if not yet initialized
     */
    public EconomySystem getEconomy() {
        return economy;
    }

    /**
     * FIX(PLAYTEST-6): Returns the current map width in tiles.
     *
     * @return map width, or 0 if no map is loaded
     */
    public int getMapWidth() {
        return map != null ? map.getWidth() : 0;
    }

    /**
     * FIX(PLAYTEST-6): Returns the current map height in tiles.
     *
     * @return map height, or 0 if no map is loaded
     */
    public int getMapHeight() {
        return map != null ? map.getHeight() : 0;
    }

    /**
     * Gets the game state (for campaign script loading).
     * FIX(CAMP-5): Expose for AOW2App script loading.
     */
    public GameState getGameState() {
        return gameState;
    }

    /**
     * Gets the entity manager (for campaign script loading).
     * FIX(CAMP-5): Expose for AOW2App script loading.
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Sets the campaign context for this game session.
     * When set, the game scene will evaluate campaign objectives each tick
     * and trigger victory/defeat conditions.
     *
     * @param cm           the campaign manager
     * @param episodeIndex the episode index
     * @param missionIndex the mission index within the episode
     */
    public void setCampaignContext(CampaignManager cm, int episodeIndex, int missionIndex) {
        this.campaignManager = cm;
        this.campaignEpisodeIndex = episodeIndex;
        this.campaignMissionIndex = missionIndex;
        this.campaignResultHandled = false;

        // Resolve the mission and copy its objectives for live tracking
        CampaignEpisode episode = switch (episodeIndex) {
            case 0 -> CampaignEpisode.GLOBAL_CONFEDERATION;
            case 1 -> CampaignEpisode.LIBERATION_OF_PERU;
            default -> CampaignEpisode.CUSTOM_MISSIONS;
        };
        List<Mission> missions = cm.getMissionsForEpisode(episode);
        if (missionIndex < missions.size()) {
            Mission mission = missions.get(missionIndex);
            this.missionObjectives = new ArrayList<>(mission.objectives());
            this.missionTriggers = new ArrayList<>(mission.triggers());
            // Set the player faction from the mission definition
            this.playerFaction = mission.playerFaction();
            this.isCampaignMission = true;
            this.enemyKillCount = 0;
            this.previousAliveEnemyCount = -1;
            LOG.info("Campaign context set: episode={}, mission='{}', {} objectives, {} triggers, faction={}",
                episode.title(), mission.name(), missionObjectives.size(), missionTriggers.size(), playerFaction);
        } else {
            this.missionObjectives = new ArrayList<>();
            this.missionTriggers = new ArrayList<>();
            LOG.warn("Campaign context: mission index {} out of range (max {})",
                missionIndex, missions.size());
        }
    }

    /**
     * Sets the callback invoked when a campaign mission ends.
     *
     * @param callback the end-of-mission callback (receives no arguments)
     */
    public void setCampaignEndCallback(Runnable callback) {
        this.campaignEndCallback = callback;
    }

    /**
     * FIX(CAMP-1): Tracks enemy kills by comparing alive enemy count to previous tick.
     * Increments enemyKillCount when the alive enemy count decreases.
     */
    private void trackEnemyKills() {
        if (!isCampaignMission || entityManager == null) {
            return;
        }
        Faction enemyFaction = (playerFaction == Faction.CONFEDERATION)
            ? Faction.RESISTANCE : Faction.CONFEDERATION;
        int aliveEnemies = entityManager.getAliveUnitsForPlayer(enemyFaction).size()
            + (int) entityManager.getBuildingsForPlayer(enemyFaction).stream()
                .filter(b -> b.isAlive()).count();

        if (previousAliveEnemyCount >= 0 && aliveEnemies < previousAliveEnemyCount) {
            int killed = previousAliveEnemyCount - aliveEnemies;
            enemyKillCount += killed;
        }
        previousAliveEnemyCount = aliveEnemies;
    }

    /**
     * FIX(CAMP-2): Processes campaign triggers each tick.
     * Checks all triggers and fires the script engine's fireTrigger() for any newly activated.
     */
    private void processCampaignTriggers() {
        if (missionTriggers.isEmpty() || gameState == null) {
            return;
        }
        List<Trigger> updated = new ArrayList<>();
        for (Trigger t : missionTriggers) {
            Trigger checked = t.check(gameState, entityManager);
            if (!checked.isActivated() && t.isActivated()) {
                // Already activated — keep as-is
                updated.add(checked);
            } else if (checked.isActivated() && !t.isActivated()) {
                // Just activated — fire trigger in script engine
                LOG.info("Campaign trigger activated: triggerId={}", checked.triggerId());
                campaignManager.getScriptEngine().fireTrigger(checked.triggerId());
                updated.add(checked);
            } else {
                updated.add(checked);
            }
        }
        missionTriggers = updated;
    }

    /**
     * Checks campaign objectives against the current game state.
     * Called each tick when a campaign context is active.
     * <p>
     * Evaluates each objective type:
     * <ul>
     *   <li>{@link Objective.DestroyObjective} — counts destroyed enemy entities against target</li>
     *   <li>{@link Objective.DefendObjective} — checks if the defended entity is still alive and ticks duration</li>
     *   <li>{@link Objective.TimedObjective} — ticks the timer and checks if other objectives are met</li>
     *   <li>{@link Objective.EscortObjective} — checks if the escorted unit is alive and at destination</li>
     *   <li>{@link Objective.CaptureObjective} — checks if a friendly unit is at the target position</li>
     * </ul>
     * When all non-timed objectives are completed (or all including timed), triggers victory.
     * When any objective fails, triggers defeat.
     */
    private void checkCampaignObjectives() {
        if (campaignManager == null || campaignResultHandled) {
            return;
        }
        if (entityManager == null || gameState == null) {
            return;
        }

        // If there are no scripted objectives, fall back to default win/lose:
        // Win: all enemy buildings destroyed. Lose: player HQ destroyed.
        if (missionObjectives.isEmpty()) {
            checkDefaultWinLoseConditions();
            return;
        }

        // Evaluate each objective and build updated list
        boolean anyFailed = false;
        boolean allCompleted = true;
        List<Objective> updated = new ArrayList<>();

        for (Objective obj : missionObjectives) {
            Objective evaluated = evaluateObjective(obj);
            updated.add(evaluated);

            if (evaluated.isFailed()) {
                anyFailed = true;
                allCompleted = false;
            } else if (!evaluated.isCompleted()) {
                allCompleted = false;
            }
        }

        missionObjectives = updated;

        if (anyFailed) {
            handleCampaignDefeat();
        } else if (allCompleted) {
            handleCampaignVictory();
        }
    }

    /**
     * Evaluates a single objective against the current game state.
     *
     * @param obj the objective to evaluate
     * @return an updated objective reflecting the current state
     */
    private Objective evaluateObjective(Objective obj) {
        return switch (obj) {
            case Objective.DestroyObjective destroy -> {
                // FIX(CAMP-1): Use tracked enemyKillCount instead of relying on external increments.
                // trackEnemyKills() runs each tick and counts enemies that disappeared.
                int currentCount = enemyKillCount;
                if (currentCount >= destroy.targetCount()) {
                    yield new Objective.DestroyObjective(
                        destroy.name(), destroy.targetCount(), destroy.targetCount());
                } else {
                    yield new Objective.DestroyObjective(
                        destroy.name(), destroy.targetCount(), currentCount);
                }
            }
            case Objective.DefendObjective defend -> {
                // Check if the defended entity still exists and is alive
                com.aow2.core.entity.Unit unit = entityManager.getUnit(defend.entityId());
                com.aow2.core.entity.Building building = entityManager.getBuilding(defend.entityId());
                boolean entityAlive = (unit != null && unit.isAlive())
                    || (building != null && building.isAlive());

                if (!entityAlive && defend.elapsedTicks() < defend.durationTicks()) {
                    // Entity destroyed before duration elapsed — objective failed
                    yield defend.destroyEntity();
                }
                // Tick the duration counter
                yield defend.tick();
            }
            case Objective.TimedObjective timed -> {
                // Check if other (non-timed) objectives are all completed
                boolean othersMet = missionObjectives.stream()
                    .filter(o -> !(o instanceof Objective.TimedObjective))
                    .allMatch(Objective::isCompleted);
                yield timed.tick().withOtherObjectivesMet(othersMet);
            }
            case Objective.EscortObjective escort -> {
                // Check if the escorted unit is alive
                com.aow2.core.entity.Unit unit = entityManager.getUnit(escort.unitId());
                boolean unitAlive = unit != null && unit.isAlive();

                if (!unitAlive) {
                    yield escort.killUnit();
                }

                // Check if the unit has reached the destination
                if (unitAlive && unit.getPosition().equals(escort.destination())) {
                    yield escort.arrive();
                }
                yield escort;
            }
            case Objective.CaptureObjective capture -> {
                // Check if any friendly alive unit is at the target position
                boolean captured = entityManager.getAliveUnitsForPlayer(playerFaction).stream()
                    .anyMatch(u -> u.getPosition().equals(capture.targetPosition()));
                if (captured) {
                    yield capture.capture();
                }
                yield capture;
            }
        };
    }

    /**
     * Default win/lose condition when a campaign mission has no scripted objectives.
     * Win: all enemy buildings destroyed. Lose: player has no buildings.
     */
    private void checkDefaultWinLoseConditions() {
        Faction enemyFaction = (playerFaction == Faction.CONFEDERATION)
            ? Faction.RESISTANCE : Faction.CONFEDERATION;

        boolean playerHasBuildings = entityManager.getBuildingsForPlayer(playerFaction).stream()
            .anyMatch(b -> b.isAlive());
        boolean enemyHasBuildings = entityManager.getBuildingsForPlayer(enemyFaction).stream()
            .anyMatch(b -> b.isAlive());

        // Also check: lose if player has no units AND no buildings
        boolean playerHasUnits = !entityManager.getAliveUnitsForPlayer(playerFaction).isEmpty();

        if (!playerHasBuildings && !playerHasUnits) {
            handleCampaignDefeat();
        } else if (!enemyHasBuildings) {
            handleCampaignVictory();
        }
    }

    /**
     * Handles campaign victory: stops the game, marks the mission as complete,
     * and invokes the campaign end callback on the JavaFX thread.
     */
    private void handleCampaignVictory() {
        if (campaignResultHandled) {
            return;
        }
        campaignResultHandled = true;

        // Mark the mission as completed in the campaign manager
        campaignManager.completeCurrentMission();
        LOG.info("Campaign mission VICTORY — episode={}, mission={}, score={}",
            campaignEpisodeIndex, campaignMissionIndex, campaignManager.getCampaignScore());

        // Stop the game loop to freeze the game state
        if (gameLoop != null) {
            gameLoop.stop();
        }
        if (renderTimer != null) {
            renderTimer.stop();
        }

        // Show victory dialog and return to campaign on the JavaFX thread
        javafx.application.Platform.runLater(() -> {
            showCampaignResultDialog(true);
            if (campaignEndCallback != null) {
                campaignEndCallback.run();
            }
        });
    }

    /**
     * Handles campaign defeat: stops the game and invokes the campaign end callback.
     */
    private void handleCampaignDefeat() {
        if (campaignResultHandled) {
            return;
        }
        campaignResultHandled = true;

        LOG.info("Campaign mission DEFEAT — episode={}, mission={}",
            campaignEpisodeIndex, campaignMissionIndex);

        // Stop the game loop
        if (gameLoop != null) {
            gameLoop.stop();
        }
        if (renderTimer != null) {
            renderTimer.stop();
        }

        // Show defeat dialog and return to campaign on the JavaFX thread
        javafx.application.Platform.runLater(() -> {
            showCampaignResultDialog(false);
            if (campaignEndCallback != null) {
                campaignEndCallback.run();
            }
        });
    }

    /**
     * Shows the unit production dialog for a producing building.
     * <p>
     * FIX (F-04): Lists the unit types available for the given building type, lets the
     * player choose one, and issues a {@link CommandType.Produce} command. The command
     * is enqueued via {@link TickManager} and, in multiplayer, submitted to the
     * {@link LockstepEngine} for deterministic sync.
     *
     * @param buildingId   the producing building's entity ID
     * @param buildingType the building type (determines which units are available)
     */
    private void showProductionDialog(int buildingId, com.aow2.common.model.BuildingType buildingType) {
        java.util.List<com.aow2.common.model.UnitType> availableUnits = getProducibleUnits(buildingType);
        if (availableUnits.isEmpty()) {
            LOG.warn("No units available for building type: {}", buildingType);
            return;
        }

        ChoiceDialog<com.aow2.common.model.UnitType> dialog =
            new ChoiceDialog<>(availableUnits.get(0), availableUnits);
        dialog.setTitle("Produce Unit");
        dialog.setHeaderText("Select unit to produce at " + buildingType.displayName() + ":");
        if (gameCanvas.getScene() != null && gameCanvas.getScene().getWindow() != null) {
            dialog.initOwner(gameCanvas.getScene().getWindow());
        }
        var result = dialog.showAndWait();
        result.ifPresent(unitType -> {
            long tick = gameState.currentTick();
            var cmd = new CommandType.Produce(tick, LOCAL_PLAYER_ID, buildingId, unitType);
            tickManager.enqueueCommand(cmd);
            if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
                lockstepEngine.submitCommand(cmd);
            }
            LOG.info("Produce command issued: building={} unit={}", buildingId, unitType);
        });
    }

    /**
     * Returns the list of unit types that can be produced at the given building.
     *
     * @param buildingType the producing building type
     * @return list of producible unit types (empty if none)
     */
    private java.util.List<com.aow2.common.model.UnitType> getProducibleUnits(
            com.aow2.common.model.BuildingType buildingType) {
        return switch (buildingType) {
            case CONFED_INFANTRY_CENTRE -> java.util.List.of(
                com.aow2.common.model.UnitType.CONFED_INFANTRY,
                com.aow2.common.model.UnitType.CONFED_GRENADIER,
                com.aow2.common.model.UnitType.CONFED_LIGHT_ASSAULT);
            case CONFED_MACHINE_FACTORY -> java.util.List.of(
                com.aow2.common.model.UnitType.CONFED_HEAVY_ASSAULT,
                com.aow2.common.model.UnitType.CONFED_FLAME_ASSAULT,
                com.aow2.common.model.UnitType.CONFED_HAMMER,
                com.aow2.common.model.UnitType.CONFED_ZEUS,
                com.aow2.common.model.UnitType.CONFED_FORTRESS,
                com.aow2.common.model.UnitType.CONFED_TORRENT);
            case REBEL_BARRACKS -> java.util.List.of(
                com.aow2.common.model.UnitType.REBEL_INFANTRY,
                com.aow2.common.model.UnitType.REBEL_GRENADIER,
                com.aow2.common.model.UnitType.REBEL_SNIPER);
            case REBEL_FACTORY -> java.util.List.of(
                com.aow2.common.model.UnitType.REBEL_COYOTE,
                com.aow2.common.model.UnitType.REBEL_ARMADILLO,
                com.aow2.common.model.UnitType.REBEL_RHINO,
                com.aow2.common.model.UnitType.REBEL_PORCUPINE);
            default -> java.util.List.of();
        };
    }

    /**
     * Shows the research dialog for a technology centre or laboratory.
     * <p>
     * FIX (F-05): Lists available research topics, lets the player choose one, and
     * issues a {@link CommandType.Research} command.
     *
     * @param buildingId   the tech building's entity ID
     * @param buildingType the building type (must be CONFED_TECH_CENTRE or REBEL_LABORATORY)
     */
    private void showResearchDialog(int buildingId, com.aow2.common.model.BuildingType buildingType) {
        // Get the 8-tech tree for the player's faction
        var researchRegistry = com.aow2.core.research.ResearchRegistry.getInstance();
        var factionTechs = researchRegistry.getFactionTechs(playerFaction);
        if (factionTechs.isEmpty()) {
            LOG.warn("No research topics available for faction: {}", playerFaction);
            return;
        }

        ChoiceDialog<com.aow2.core.research.ResearchRegistry.FactionTech> dialog =
            new ChoiceDialog<>(factionTechs.get(0), factionTechs);
        dialog.setTitle("Research Technology");
        dialog.setHeaderText("Select technology to research:");
        if (gameCanvas.getScene() != null && gameCanvas.getScene().getWindow() != null) {
            dialog.initOwner(gameCanvas.getScene().getWindow());
        }
        var result = dialog.showAndWait();
        result.ifPresent(tech -> {
            long tick = gameState.currentTick();
            var cmd = new CommandType.Research(tick, LOCAL_PLAYER_ID, buildingId, tech.globalEffectId());
            tickManager.enqueueCommand(cmd);
            if (isMultiplayer && lockstepEngine != null && lockstepEngine.isRunning()) {
                lockstepEngine.submitCommand(cmd);
            }
            LOG.info("Research command issued: building={} researchId={} ({})",
                buildingId, tech.globalEffectId(), tech.name());
        });
    }

    /**
     * Shows a victory or defeat dialog overlay.
     *
     * @param victory true for victory, false for defeat
     */
    private void showCampaignResultDialog(boolean victory) {
        String title = victory ? "MISSION COMPLETE" : "MISSION FAILED";
        String message = victory
            ? "Objectives accomplished. Score: " + campaignManager.getCampaignScore()
            : "You have been defeated.";
        String color = victory ? "rgb(60, 140, 60)" : "rgb(160, 40, 40)";
        String textColor = victory ? "rgb(200, 255, 200)" : "rgb(255, 200, 200)";

        javafx.scene.control.Dialog<ButtonType> dialog =
            new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(message);

        if (gameCanvas.getScene() != null && gameCanvas.getScene().getWindow() != null) {
            dialog.initOwner(gameCanvas.getScene().getWindow());
        }

        dialog.getDialogPane().setStyle(
            "-fx-background-color: " + color + "; "
            + "-fx-text-fill: " + textColor + ";");

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.showAndWait();
    }
}
