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
 * - Right click: command (move/attack/garrison based on context)
 * - Box drag: area select
 * - Hotkeys: A=attack, S=stop, H=hold, P=patrol, B=build
 * - Camera controls: WASD, arrow keys, edge scroll
 * - Escape: deselect/cancel
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Controls and Hotkeys
 */
public class InputHandler {

    private static final Logger LOG = LoggerFactory.getLogger(InputHandler.class);

    /** Current input mode for command context. */
    public enum CommandMode {
        NORMAL, ATTACK_MOVE, PATROL, BUILD_PLACEMENT
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

    /** Last mouse X position. */
    private double lastMouseX;

    /** Last mouse Y position. */
    private double lastMouseY;

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
        /**
         * Called when a command is issued.
         *
         * @param command the command type
         * @param targetGx target grid X (or -1 if no target)
         * @param targetGy target grid Y (or -1 if no target)
         */
        void onCommand(String command, int targetGx, int targetGy);
    }

    /**
     * Callback interface for build mode activation.
     * Called when the player presses B, allowing the UI layer to show
     * a building type selection dialog.
     */
    @FunctionalInterface
    public interface BuildTypeCallback {
        /** Called when the player requests build mode. */
        void onBuildModeRequested();
    }

    /**
     * Constructs a new InputHandler.
     *
     * @param selectionManager the selection manager
     * @param cameraController the camera controller
     * @param isoRenderer      the isometric renderer
     */
    public InputHandler(SelectionManager selectionManager, CameraController cameraController,
                        IsometricRenderer isoRenderer) {
        this.selectionManager = selectionManager;
        this.cameraController = cameraController;
        this.isoRenderer = isoRenderer;
        this.commandMode = CommandMode.NORMAL;
        this.leftMouseDown = false;
        this.cameraOffsetX = 0;
        this.cameraOffsetY = 0;
        this.zoom = 1.0;
    }

    /**
     * Sets the command callback.
     *
     * @param callback the command callback
     */
    public void setCommandCallback(CommandCallback callback) {
        this.commandCallback = callback;
    }

    /**
     * Sets the build type callback, invoked when the player presses B.
     *
     * @param callback the build type callback
     */
    public void setBuildTypeCallback(BuildTypeCallback callback) {
        this.buildTypeCallback = callback;
    }

    /**
     * Gets the pending building type selected for build placement.
     *
     * @return the building type, or null if none selected
     */
    public BuildingType getPendingBuildType() {
        return pendingBuildType;
    }

    /**
     * Sets the pending building type for build placement.
     *
     * @param buildingType the building type to build
     */
    public void setPendingBuildType(BuildingType buildingType) {
        this.pendingBuildType = buildingType;
    }

    /**
     * Sets the current camera transform for coordinate conversion.
     *
     * @param offsetX camera X offset
     * @param offsetY camera Y offset
     * @param zoom    zoom level
     */
    public void setCameraTransform(double offsetX, double offsetY, double zoom) {
        this.cameraOffsetX = offsetX;
        this.cameraOffsetY = offsetY;
        this.zoom = zoom;
    }

    /**
     * Handles mouse press events.
     *
     * @param event the mouse event
     */
    public void onMousePressed(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        if (event.getButton() == MouseButton.PRIMARY) {
            leftMouseDown = true;
            selectionManager.startDrag(event.getSceneX(), event.getSceneY());
        }
    }

    /**
     * Handles mouse drag events.
     *
     * @param event the mouse event
     */
    public void onMouseDragged(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        if (leftMouseDown) {
            selectionManager.updateDrag(event.getSceneX(), event.getSceneY());
        }

        // Pass to camera controller for edge scrolling
        cameraController.handleMouseMove(event);
    }

    /**
     * Handles mouse release events.
     *
     * @param event the mouse event
     */
    public void onMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            leftMouseDown = false;

            boolean shiftDown = event.isShiftDown();
            boolean ctrlDown = event.isControlDown();

            // Check if this was a click or a drag
            double[] dragBox = selectionManager.getDragBox();
            if (dragBox == null) {
                // No active drag box — treat as a single click
                int[] grid = screenToGrid(event.getSceneX(), event.getSceneY());
                selectionManager.handleClick(grid[0], grid[1], shiftDown, ctrlDown);
            } else {
                double dx = Math.abs(event.getSceneX() - dragBox[0]);
                double dy = Math.abs(event.getSceneY() - dragBox[1]);

                if (dx < 5.0 && dy < 5.0) {
                    // Click - convert screen to grid and select
                    int[] grid = screenToGrid(event.getSceneX(), event.getSceneY());
                    selectionManager.handleClick(grid[0], grid[1], shiftDown, ctrlDown);
                } else {
                    // Drag box selection
                    selectionManager.endDrag(event.getSceneX(), event.getSceneY(), shiftDown,
                        this::screenToGridForDrag);
                }
            }
        } else if (event.getButton() == MouseButton.SECONDARY) {
            // Right click - issue command
            handleRightClick(event);
        }
    }

    /**
     * Handles mouse move events (no buttons pressed).
     *
     * @param event the mouse event
     */
    public void onMouseMoved(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        cameraController.handleMouseMove(event);
    }

    /**
     * Handles scroll events for zoom.
     *
     * @param event the scroll event
     */
    public void onScroll(ScrollEvent event) {
        cameraController.handleScroll(event);
    }

    /**
     * Handles keyboard press events.
     *
     * @param event the key event
     */
    public void onKeyPressed(KeyEvent event) {
        // Camera controls
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
                        LOG.debug("Build placement mode activated (no callback)");
                    }
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
            // FIX (L-NEW-17): Control groups — Ctrl+Digit1-9 assign, Digit1-9/0 recall
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
                    // Assign current selection to control group
                    List<Integer> selectedIds = selectionManager.getSelectedIdsList();
                    if (!selectedIds.isEmpty()) {
                        controlGroups.put(groupNumber, selectedIds);
                        LOG.debug("Control group {} assigned: {} units", groupNumber, selectedIds.size());
                    }
                } else {
                    // Recall control group
                    List<Integer> groupIds = controlGroups.get(groupNumber);
                    if (groupIds != null && !groupIds.isEmpty()) {
                        selectionManager.selectUnitsByIds(groupIds);
                        LOG.debug("Control group {} recalled: {} units", groupNumber, groupIds.size());
                    }
                }
            }
            default -> { /* unhandled key */ }
        }
    }

    /**
     * Handles keyboard release events.
     *
     * @param event the key event
     */
    public void onKeyReleased(KeyEvent event) {
        cameraController.handleKeyEvent(event);
    }

    /**
     * Handles a right-click command based on context.
     *
     * @param event the mouse event
     */
    private void handleRightClick(MouseEvent event) {
        if (!selectionManager.hasSelection()) {
            return;
        }

        int[] grid = screenToGrid(event.getSceneX(), event.getSceneY());

        String command = switch (commandMode) {
            case ATTACK_MOVE -> "attack_move";
            case PATROL -> "patrol";
            case BUILD_PLACEMENT -> "build";
            case NORMAL -> "move"; // Default: move command
        };

        issueCommand(command, grid[0], grid[1]);

        // Reset command mode after issuing command
        if (commandMode != CommandMode.NORMAL) {
            commandMode = CommandMode.NORMAL;
        }
    }

    /**
     * Issues a command through the callback.
     *
     * @param command  command string
     * @param targetGx target grid X
     * @param targetGy target grid Y
     */
    private void issueCommand(String command, int targetGx, int targetGy) {
        if (commandCallback != null) {
            commandCallback.onCommand(command, targetGx, targetGy);
        }
    }

    /**
     * Converts screen coordinates to grid coordinates using the current camera transform.
     *
     * @param screenX screen X
     * @param screenY screen Y
     * @return [gridX, gridY]
     */
    private int[] screenToGrid(double screenX, double screenY) {
        // Undo camera transform: screen -> world -> grid
        double worldX = (screenX - cameraOffsetX) / zoom;
        double worldY = (screenY - cameraOffsetY) / zoom;
        return isoRenderer.screenToGrid(worldX, worldY);
    }

    /**
     * Screen to grid conversion for drag selection (BiFunction interface).
     *
     * @param sx screen X
     * @param sy screen Y
     * @return [gridX, gridY]
     */
    private int[] screenToGridForDrag(Double sx, Double sy) {
        return screenToGrid(sx, sy);
    }

    /**
     * Gets the current command mode.
     *
     * @return the command mode
     */
    public CommandMode getCommandMode() {
        return commandMode;
    }

    /**
     * Sets the current command mode.
     *
     * @param mode the command mode
     */
    public void setCommandMode(CommandMode mode) {
        this.commandMode = mode;
    }

    /**
     * Gets the last mouse X position.
     *
     * @return last mouse X
     */
    public double getLastMouseX() {
        return lastMouseX;
    }

    /**
     * Gets the last mouse Y position.
     *
     * @return last mouse Y
     */
    public double getLastMouseY() {
        return lastMouseY;
    }
}
