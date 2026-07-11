package com.aow2.client.input;

import com.aow2.client.render.CameraController;
import com.aow2.client.render.IsometricRenderer;
import com.aow2.common.model.BuildingType;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes all mouse and keyboard input for the game.
 * <p>
 * Input bindings:
 * - Left click: select
 * - Left click + drag: area select
 * - Double-click: select all units of same type on screen
 * - Ctrl+click: select all units of same type on screen
 * - Shift+click: add/remove from selection
 * - Right click: command (move/attack/garrison based on context)
 * - Shift+right-click: queue waypoint (does not replace existing orders)
 * - Middle click + drag: pan camera
 * - Hotkeys: A=attack, S=stop, H=hold, P=patrol, B=build, G=garrison, D=siege, U=ungarrison, T=produce, R=research
 * - Tab: cycle through unit types in mixed selection
 * - Space: jump to last event (attack/production completion)
 * - Home: center camera on player's Command Centre / Headquarters
 * - Camera controls: W, arrow keys, edge scroll, middle-click drag
 * - Control groups: Ctrl+1-9 assign, 1-9/0 recall
 * - Escape: deselect/cancel
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Controls and Hotkeys
 */
public class InputHandler {

    private static final Logger LOG = LoggerFactory.getLogger(InputHandler.class);

    /** Maximum time between two clicks to count as a double-click (ms). */
    private static final long DOUBLE_CLICK_THRESHOLD_MS = 350;

    /** Current input mode for command context. */
    public enum CommandMode {
        NORMAL, ATTACK_MOVE, PATROL, BUILD_PLACEMENT, GARRISON
    }

    /** The selection manager. */
    private final SelectionManager selectionManager;

    /** The camera controller. */
    private final CameraController cameraController;

    /** The isometric renderer for coordinate conversion. */
    private final IsometricRenderer isoRenderer;

    /** Current command mode. */
    private CommandMode commandMode;

    /** FIX (L-NEW-17): Control groups (Ctrl+1-9 assign, 1-9 recall). LinkedHashMap for determinism. */
    private final Map<Integer, List<Integer>> controlGroups = new LinkedHashMap<>();

    /** Callback for command execution. */
    private CommandCallback commandCallback;

    /** The building type selected for build placement mode. */
    private BuildingType pendingBuildType;

    /** Callback invoked when the player presses B to enter build mode. */
    private BuildTypeCallback buildTypeCallback;

    /** Whether the left mouse button is currently pressed. */
    private boolean leftMouseDown;

    /** Whether the middle mouse button is currently pressed (for camera drag). */
    private boolean middleMouseDown;

    /** Last mouse X position. */
    private double lastMouseX;

    /** Last mouse Y position. */
    private double lastMouseY;

    /** Mouse X when middle-click drag started (for camera drag offset). */
    private double middleDragStartX;

    /** Mouse Y when middle-click drag started (for camera drag offset). */
    private double middleDragStartY;

    /** Camera X when middle-click drag started. */
    private double middleDragStartCamX;

    /** Camera Y when middle-click drag started. */
    private double middleDragStartCamY;

    /** Timestamp of the last left-click (for double-click detection). */
    private long lastClickTimeMs;

    /** Grid position of the last left-click (for double-click detection). */
    private int lastClickGx = -1;
    private int lastClickGy = -1;

    /** Current camera offset X (for coordinate conversion). */
    private double cameraOffsetX;

    /** Current camera offset Y (for coordinate conversion). */
    private double cameraOffsetY;

    /** Current zoom level (for coordinate conversion). */
    private double zoom;

    /**
     * Callback interface for command execution.
     */
    @FunctionalInterface
    public interface CommandCallback {
        void onCommand(String command, int targetGx, int targetGy);
    }

    /**
     * Callback interface for build mode activation.
     */
    @FunctionalInterface
    public interface BuildTypeCallback {
        void onBuildModeRequested();
    }

    /**
     * Callback for camera actions (jump to event, center on base).
     */
    @FunctionalInterface
    public interface CameraActionCallback {
        void onCameraAction(String action);
    }

    /** Callback for camera actions like jump-to-event and center-on-base. */
    private CameraActionCallback cameraActionCallback;

    /**
     * Constructs a new InputHandler.
     */
    public InputHandler(SelectionManager selectionManager, CameraController cameraController,
                        IsometricRenderer isoRenderer) {
        this.selectionManager = selectionManager;
        this.cameraController = cameraController;
        this.isoRenderer = isoRenderer;
        this.commandMode = CommandMode.NORMAL;
        this.leftMouseDown = false;
        this.middleMouseDown = false;
        this.cameraOffsetX = 0;
        this.cameraOffsetY = 0;
        this.zoom = 1.0;
    }

    public void setCommandCallback(CommandCallback callback) {
        this.commandCallback = callback;
    }

    public void setBuildTypeCallback(BuildTypeCallback callback) {
        this.buildTypeCallback = callback;
    }

    /**
     * Sets the camera action callback for jump-to-event and center-on-base.
     *
     * @param callback the camera action callback
     */
    public void setCameraActionCallback(CameraActionCallback callback) {
        this.cameraActionCallback = callback;
    }

    public BuildingType getPendingBuildType() {
        return pendingBuildType;
    }

    public void setPendingBuildType(BuildingType buildingType) {
        this.pendingBuildType = buildingType;
    }

    public void setCameraTransform(double offsetX, double offsetY, double zoom) {
        this.cameraOffsetX = offsetX;
        this.cameraOffsetY = offsetY;
        this.zoom = zoom;
    }

    // =========================================================================
    // Mouse handling
    // =========================================================================

    /**
     * Handles mouse press events.
     */
    public void onMousePressed(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        if (event.getButton() == MouseButton.PRIMARY) {
            leftMouseDown = true;
            selectionManager.startDrag(event.getSceneX(), event.getSceneY());
        } else if (event.getButton() == MouseButton.MIDDLE) {
            // Middle-click starts camera drag
            middleMouseDown = true;
            middleDragStartX = event.getSceneX();
            middleDragStartY = event.getSceneY();
            middleDragStartCamX = cameraController.getCameraX();
            middleDragStartCamY = cameraController.getCameraY();
            LOG.debug("Middle-click camera drag started");
        }
    }

    /**
     * Handles mouse drag events.
     */
    public void onMouseDragged(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        if (middleMouseDown) {
            // Middle-click camera drag: move camera opposite to mouse movement
            double dx = event.getSceneX() - middleDragStartX;
            double dy = event.getSceneY() - middleDragStartY;
            cameraController.panToWorld(
                middleDragStartCamX - dx / zoom,
                middleDragStartCamY - dy / zoom
            );
            return;  // Don't process selection drag or edge scroll while middle-dragging
        }

        if (leftMouseDown) {
            selectionManager.updateDrag(event.getSceneX(), event.getSceneY());
        }

        cameraController.handleMouseMove(event);
    }

    /**
     * Handles mouse release events.
     */
    public void onMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            leftMouseDown = false;

            boolean shiftDown = event.isShiftDown();
            boolean ctrlDown = event.isControlDown();

            // Check if this was a click or a drag
            double[] dragBox = selectionManager.getDragBox();
            if (dragBox == null) {
                int[] grid = screenToGrid(event.getSceneX(), event.getSceneY());
                handleLeftClick(grid[0], grid[1], shiftDown, ctrlDown, event.getSceneX(), event.getSceneY());
            } else {
                double dx = Math.abs(event.getSceneX() - dragBox[0]);
                double dy = Math.abs(event.getSceneY() - dragBox[1]);

                if (dx < 5.0 && dy < 5.0) {
                    int[] grid = screenToGrid(event.getSceneX(), event.getSceneY());
                    handleLeftClick(grid[0], grid[1], shiftDown, ctrlDown, event.getSceneX(), event.getSceneY());
                } else {
                    selectionManager.endDrag(event.getSceneX(), event.getSceneY(), shiftDown,
                        this::screenToGridForDrag);
                }
            }
        } else if (event.getButton() == MouseButton.SECONDARY) {
            handleRightClick(event);
        } else if (event.getButton() == MouseButton.MIDDLE) {
            middleMouseDown = false;
            LOG.debug("Middle-click camera drag ended");
        }
    }

    /**
     * Handles left-click selection with double-click and Ctrl+click support.
     *
     * @param gx grid X
     * @param gy grid Y
     * @param shiftDown whether Shift is held
     * @param ctrlDown whether Ctrl is held
     * @param screenX click screen X (for double-click screen detection)
     * @param screenY click screen Y
     */
    private void handleLeftClick(int gx, int gy, boolean shiftDown, boolean ctrlDown,
                                  double screenX, double screenY) {
        long now = System.currentTimeMillis();

        // Double-click detection: same grid position, within threshold
        boolean isDoubleClick = (now - lastClickTimeMs < DOUBLE_CLICK_THRESHOLD_MS)
                             && (gx == lastClickGx && gy == lastClickGy);

        if (isDoubleClick && !shiftDown) {
            // Double-click: select all units of the same type on screen
            selectionManager.handleClick(gx, gy, false, true);  // ctrlDown=true triggers selectAllOfType
            LOG.debug("Double-click: select all of same type at ({}, {})", gx, gy);
        } else {
            // Normal click — Ctrl+click already selects all of same type via SelectionManager
            selectionManager.handleClick(gx, gy, shiftDown, ctrlDown);
        }

        lastClickTimeMs = now;
        lastClickGx = gx;
        lastClickGy = gy;
    }

    /**
     * Handles mouse move events (no buttons pressed).
     */
    public void onMouseMoved(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        cameraController.handleMouseMove(event);
    }

    /**
     * Handles scroll events for zoom.
     */
    public void onScroll(ScrollEvent event) {
        cameraController.handleScroll(event);
    }

    // =========================================================================
    // Keyboard handling
    // =========================================================================

    /**
     * Handles keyboard press events.
     */
    public void onKeyPressed(KeyEvent event) {
        // Camera controls (W, arrows only — A/S/D are game commands)
        cameraController.handleKeyEvent(event);

        switch (event.getCode()) {
            case A -> {
                if (selectionManager.hasSelection()) {
                    commandMode = CommandMode.ATTACK_MOVE;
                    LOG.debug("Attack move mode activated");
                }
            }
            case S -> {
                if (selectionManager.hasSelection()) {
                    issueCommand("stop", -1, -1);
                    LOG.debug("Stop command issued");
                }
            }
            case H -> {
                if (selectionManager.hasSelection()) {
                    issueCommand("hold", -1, -1);
                    LOG.debug("Hold command issued");
                }
            }
            case P -> {
                if (selectionManager.hasSelection()) {
                    commandMode = CommandMode.PATROL;
                    LOG.debug("Patrol mode activated");
                }
            }
            case B -> {
                if (selectionManager.hasSelection()) {
                    if (buildTypeCallback != null) {
                        buildTypeCallback.onBuildModeRequested();
                    } else {
                        commandMode = CommandMode.BUILD_PLACEMENT;
                    }
                }
            }
            case G -> {
                if (selectionManager.hasSelection()) {
                    commandMode = CommandMode.GARRISON;
                    LOG.debug("Garrison mode activated");
                }
            }
            case D -> {
                if (selectionManager.hasSelection()) {
                    issueCommand("siege_mode", -1, -1);
                    LOG.debug("Siege mode toggle command issued");
                }
            }
            case U -> {
                // FIX (B-7 from FULL_ANALYSIS.md): Ungarrison hotkey — issues an
                // "ungarrison" command for the currently selected building(s).
                // GameScene interprets this and creates CommandType.Ungarrison for
                // each selected friendly bunker that has a garrisoned unit. If no
                // building is selected, the command is still issued (GameScene will
                // look for any friendly bunker with garrisoned units as a fallback).
                issueCommand("ungarrison", -1, -1);
                LOG.debug("Ungarrison command issued");
            }
            case TAB -> {
                // Cycle through unit types in the current selection
                selectionManager.cycleUnitTypeInSelection();
                LOG.debug("Tab: cycled unit type in selection");
            }
            case SPACE -> {
                // Jump to last event (attack alert, production complete, etc.)
                if (cameraActionCallback != null) {
                    cameraActionCallback.onCameraAction("jump_to_event");
                    LOG.debug("Space: jump to last event");
                }
            }
            case HOME -> {
                // Center camera on player's Command Centre / Headquarters
                if (cameraActionCallback != null) {
                    cameraActionCallback.onCameraAction("center_on_base");
                    LOG.debug("Home: center on base");
                }
            }
            case ESCAPE -> {
                if (commandMode != CommandMode.NORMAL) {
                    commandMode = CommandMode.NORMAL;
                    LOG.debug("Command mode cancelled");
                } else {
                    selectionManager.clearSelection();
                    LOG.debug("Selection cleared");
                }
            }
            case DIGIT1, DIGIT2, DIGIT3, DIGIT4, DIGIT5,
                 DIGIT6, DIGIT7, DIGIT8, DIGIT9, DIGIT0 -> {
                int groupNumber = switch (event.getCode()) {
                    case DIGIT1 -> 1;
                    case DIGIT2 -> 2;
                    case DIGIT3 -> 3;
                    case DIGIT4 -> 4;
                    case DIGIT5 -> 5;
                    case DIGIT6 -> 6;
                    case DIGIT7 -> 7;
                    case DIGIT8 -> 8;
                    case DIGIT9 -> 9;
                    case DIGIT0 -> 0;
                    default -> -1;
                };
                if (groupNumber < 0) break;
                if (event.isControlDown() || event.isMetaDown()) {
                    List<Integer> selectedIds = selectionManager.getSelectedIdsList();
                    if (!selectedIds.isEmpty()) {
                        controlGroups.put(groupNumber, selectedIds);
                        LOG.debug("Control group {} assigned: {} units", groupNumber, selectedIds.size());
                    }
                } else {
                    List<Integer> groupIds = controlGroups.get(groupNumber);
                    if (groupIds != null && !groupIds.isEmpty()) {
                        selectionManager.selectUnitsByIds(groupIds);
                        LOG.debug("Control group {} recalled", groupNumber);
                    }
                }
            }
            default -> { /* unhandled key */ }
        }
    }

    /**
     * Handles keyboard release events.
     */
    public void onKeyReleased(KeyEvent event) {
        cameraController.handleKeyEvent(event);
    }

    // =========================================================================
    // Right-click command handling with Shift waypoint queuing
    // =========================================================================

    /**
     * Handles a right-click command based on context.
     * Shift+right-click queues a waypoint instead of replacing existing orders.
     */
    private void handleRightClick(MouseEvent event) {
        if (!selectionManager.hasSelection()) {
            return;
        }

        int[] grid = screenToGrid(event.getSceneX(), event.getSceneY());
        boolean shiftDown = event.isShiftDown();

        String command = switch (commandMode) {
            case ATTACK_MOVE -> "attack_move";
            case PATROL -> "patrol";
            case BUILD_PLACEMENT -> "build";
            case GARRISON -> "garrison";
            case NORMAL -> "move";
        };

        // Shift+right-click queues a waypoint — the command string carries
        // a "queued_" prefix so GameScene knows not to clear existing orders.
        if (shiftDown && commandMode == CommandMode.NORMAL) {
            issueCommand("queued_move", grid[0], grid[1]);
            LOG.debug("Queued move waypoint at ({}, {})", grid[0], grid[1]);
        } else if (shiftDown && commandMode == CommandMode.ATTACK_MOVE) {
            issueCommand("queued_attack_move", grid[0], grid[1]);
            LOG.debug("Queued attack-move waypoint at ({}, {})", grid[0], grid[1]);
        } else {
            issueCommand(command, grid[0], grid[1]);
        }

        // Reset command mode after issuing command (unless Shift held for queuing)
        if (commandMode != CommandMode.NORMAL && !shiftDown) {
            commandMode = CommandMode.NORMAL;
        }
    }

    /**
     * Issues a command through the callback.
     */
    private void issueCommand(String command, int targetGx, int targetGy) {
        if (commandCallback != null) {
            commandCallback.onCommand(command, targetGx, targetGy);
        }
    }

    // =========================================================================
    // Coordinate conversion
    // =========================================================================

    private int[] screenToGrid(double screenX, double screenY) {
        double worldX = (screenX - cameraOffsetX) / zoom;
        double worldY = (screenY - cameraOffsetY) / zoom;
        return isoRenderer.screenToGrid(worldX, worldY);
    }

    private int[] screenToGridForDrag(Double sx, Double sy) {
        return screenToGrid(sx, sy);
    }

    // =========================================================================
    // Getters / Setters
    // =========================================================================

    public CommandMode getCommandMode() {
        return commandMode;
    }

    public void setCommandMode(CommandMode mode) {
        this.commandMode = mode;
    }

    public double getLastMouseX() {
        return lastMouseX;
    }

    public double getLastMouseY() {
        return lastMouseY;
    }

    public boolean isMiddleMouseDown() {
        return middleMouseDown;
    }
}
