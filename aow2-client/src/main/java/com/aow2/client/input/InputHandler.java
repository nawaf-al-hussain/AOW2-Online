package com.aow2.client.input;

import com.aow2.client.render.CameraController;
import com.aow2.client.render.IsometricRenderer;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /** Callback for command execution. */
    private CommandCallback commandCallback;

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
                    commandMode = CommandMode.BUILD_PLACEMENT;
                    LOG.debug("Build placement mode activated");
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
