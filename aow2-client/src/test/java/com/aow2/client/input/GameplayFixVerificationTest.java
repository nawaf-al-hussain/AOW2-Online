package com.aow2.client.input;

import com.aow2.client.input.InputHandler.CommandMode;
import com.aow2.client.input.InputHandler.CommandCallback;
import com.aow2.client.render.CameraController;
import com.aow2.client.render.IsometricRenderer;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying gameplay fixes from the FULL_ANALYSIS fix loop.
 * <p>
 * These tests verify the InputHandler key bindings and command dispatch without
 * requiring a running FXGL display. They use real InputHandler, SelectionManager,
 * and CameraController instances with mock callbacks.
 *
 * @see InputHandler
 * @see CameraController
 */
@DisplayName("Gameplay Fix Verification Tests")
class GameplayFixVerificationTest {

    private InputHandler inputHandler;
    private SelectionManager selectionManager;
    private CameraController cameraController;
    private IsometricRenderer isoRenderer;

    /** Captures commands issued by InputHandler for verification. */
    private List<String> issuedCommands;

    @BeforeEach
    void setUp() {
        selectionManager = new SelectionManager();
        cameraController = new CameraController();
        cameraController.setEdgeScrollEnabled(false);
        isoRenderer = new IsometricRenderer();
        inputHandler = new InputHandler(selectionManager, cameraController, isoRenderer);
        issuedCommands = new ArrayList<>();

        inputHandler.setCommandCallback((command, targetGx, targetGy) -> {
            issuedCommands.add(command + ":" + targetGx + "," + targetGy);
        });

        // Create a real EntityManager with a real unit so SelectionManager can select it
        EntityManager entityManager = new EntityManager();
        UnitStats stats = new UnitStats(UnitType.CONFED_INFANTRY, "Infantry",
            40, 2, 5, 5, 0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit unit = new Unit(1, Faction.CONFEDERATION, new GridPosition(5, 5),
            UnitType.CONFED_INFANTRY, stats);
        entityManager.addUnit(unit);

        selectionManager.setEntityManager(entityManager);
        selectionManager.setPlayerFaction(Faction.CONFEDERATION);
        selectionManager.selectUnitsByIds(List.of(1));
        assertTrue(selectionManager.hasSelection(), "Test setup: unit should be selected");
    }

    private KeyEvent keyPress(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    // =========================================================================
    // F-10: A/S/D key conflict — camera should NOT pan on A/S/D
    // =========================================================================

    @Nested
    @DisplayName("F-10: A/S/D key conflict resolved")
    class CameraKeyConflictTest {

        @Test
        @DisplayName("Pressing A does NOT set panLeft flag on camera")
        void aKeyDoesNotPanCameraLeft() {
            double xBefore = cameraController.getCameraX();
            inputHandler.onKeyPressed(keyPress(KeyCode.A));
            cameraController.update();
            // Camera should not have moved — A is now exclusively attack-move, not camera pan
            assertEquals(xBefore, cameraController.getCameraX(), 0.001,
                "F-10: Pressing A must NOT pan the camera left");
        }

        @Test
        @DisplayName("Pressing S does NOT set panDown flag on camera")
        void sKeyDoesNotPanCameraDown() {
            double yBefore = cameraController.getCameraY();
            inputHandler.onKeyPressed(keyPress(KeyCode.S));
            cameraController.update();
            assertEquals(yBefore, cameraController.getCameraY(), 0.001,
                "F-10: Pressing S must NOT pan the camera down");
        }

        @Test
        @DisplayName("Pressing D does NOT set panRight flag on camera")
        void dKeyDoesNotPanCameraRight() {
            double xBefore = cameraController.getCameraX();
            inputHandler.onKeyPressed(keyPress(KeyCode.D));
            cameraController.update();
            assertEquals(xBefore, cameraController.getCameraX(), 0.001,
                "F-10: Pressing D must NOT pan the camera right");
        }

        @Test
        @DisplayName("Pressing W DOES pan camera up (W is still a camera key)")
        void wKeyStillPansCameraUp() {
            double yBefore = cameraController.getCameraY();
            inputHandler.onKeyPressed(keyPress(KeyCode.W));
            cameraController.update();
            assertNotEquals(yBefore, cameraController.getCameraY(), 0.001,
                "W should still pan the camera up");
        }

        @Test
        @DisplayName("Pressing arrow keys DOES pan camera (arrows are still camera keys)")
        void arrowKeysStillPanCamera() {
            double xBefore = cameraController.getCameraX();
            inputHandler.onKeyPressed(keyPress(KeyCode.LEFT));
            cameraController.update();
            assertNotEquals(xBefore, cameraController.getCameraX(), 0.001,
                "LEFT arrow should still pan the camera left");
        }
    }

    // =========================================================================
    // F-06: Garrison hotkey — G enters GARRISON mode
    // =========================================================================

    @Nested
    @DisplayName("F-06: Garrison hotkey (G)")
    class GarrisonHotkeyTest {

        @Test
        @DisplayName("Pressing G sets command mode to GARRISON")
        void gKeySetsGarrisonMode() {
            assertEquals(CommandMode.NORMAL, inputHandler.getCommandMode(),
                "Initial mode should be NORMAL");

            inputHandler.onKeyPressed(keyPress(KeyCode.G));

            assertEquals(CommandMode.GARRISON, inputHandler.getCommandMode(),
                "F-06: Pressing G must set GARRISON mode");
        }

        @Test
        @DisplayName("Pressing G with no selection does NOT set GARRISON mode")
        void gKeyWithNoSelectionDoesNothing() {
            selectionManager.clearSelection();

            inputHandler.onKeyPressed(keyPress(KeyCode.G));

            assertEquals(CommandMode.NORMAL, inputHandler.getCommandMode(),
                "G with no selection should stay in NORMAL mode");
        }
    }

    // =========================================================================
    // F-07: Siege mode hotkey — D issues siege_mode command
    // =========================================================================

    @Nested
    @DisplayName("F-07: Siege mode hotkey (D)")
    class SiegeModeHotkeyTest {

        @Test
        @DisplayName("Pressing D issues siege_mode command")
        void dKeyIssuesSiegeModeCommand() {
            inputHandler.onKeyPressed(keyPress(KeyCode.D));

            assertFalse(issuedCommands.isEmpty(), "F-07: Pressing D must issue a command");
            assertTrue(issuedCommands.get(0).startsWith("siege_mode:"),
                "F-07: Command should be 'siege_mode', got: " + issuedCommands.get(0));
        }

        @Test
        @DisplayName("Pressing D with no selection does NOT issue command")
        void dKeyWithNoSelectionDoesNothing() {
            selectionManager.clearSelection();

            inputHandler.onKeyPressed(keyPress(KeyCode.D));

            assertTrue(issuedCommands.isEmpty(),
                "D with no selection should not issue any command");
        }
    }

    // =========================================================================
    // F-11: Hold command — H issues "hold" (not "stop")
    // =========================================================================

    @Nested
    @DisplayName("F-11: Hold command distinct from Stop")
    class HoldCommandTest {

        @Test
        @DisplayName("Pressing H issues 'hold' command (not 'stop')")
        void hKeyIssuesHoldNotStop() {
            inputHandler.onKeyPressed(keyPress(KeyCode.H));

            assertFalse(issuedCommands.isEmpty(), "H must issue a command");
            assertTrue(issuedCommands.get(0).startsWith("hold:"),
                "F-11: H should issue 'hold', not 'stop'. Got: " + issuedCommands.get(0));
        }

        @Test
        @DisplayName("Pressing S issues 'stop' command (distinct from hold)")
        void sKeyIssuesStop() {
            inputHandler.onKeyPressed(keyPress(KeyCode.S));

            assertFalse(issuedCommands.isEmpty(), "S must issue a command");
            assertTrue(issuedCommands.get(0).startsWith("stop:"),
                "S should issue 'stop'. Got: " + issuedCommands.get(0));
        }

        @Test
        @DisplayName("Hold and Stop produce different command strings")
        void holdAndStopAreDifferent() {
            // Issue hold
            inputHandler.onKeyPressed(keyPress(KeyCode.H));
            String holdCommand = issuedCommands.get(0);
            issuedCommands.clear();

            // Issue stop
            inputHandler.onKeyPressed(keyPress(KeyCode.S));
            String stopCommand = issuedCommands.get(0);

            assertNotEquals(holdCommand, stopCommand,
                "F-11: Hold and Stop must produce different commands");
            assertTrue(holdCommand.startsWith("hold:"), "Hold command should start with 'hold:'");
            assertTrue(stopCommand.startsWith("stop:"), "Stop command should start with 'stop:'");
        }
    }

    // =========================================================================
    // F-08: Cancel production — verified via GameScene (tested in GameScene tests)
    // F-04/F-05: Production/Research dialogs — verified via GameScene (tested in GameScene tests)
    // These require GameScene which needs FXGL — tested via code inspection instead.
    // =========================================================================
}
