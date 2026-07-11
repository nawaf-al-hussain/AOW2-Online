package com.aow2.client.input;

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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for B-7 from FULL_ANALYSIS.md: Ungarrison has no UI dispatch.
 * <p>
 * BUG (before fix): {@code CommandType.Ungarrison} was fully implemented in the
 * core (defined, serialized, processed, tested) but had ZERO dispatch paths
 * from the client. There was no key binding, no HUD button, and no right-click
 * context that created an Ungarrison command. Once a player garrisoned units
 * into a bunker, there was no way to unload them — the garrisoned units were
 * permanently stuck.
 * <p>
 * FIX: Added a U hotkey in {@code InputHandler.onKeyPressed} that issues an
 * "ungarrison" command via the command callback. {@code GameScene} interprets
 * this and creates {@code CommandType.Ungarrison} for each selected friendly
 * bunker (or all friendly bunkers as a fallback).
 * <p>
 * This test verifies the InputHandler dispatches the "ungarrison" command when
 * the U key is pressed. The GameScene-side dispatch (creating CommandType.Ungarrison)
 * is verified by code inspection — it follows the same pattern as the existing
 * "siege_mode" command dispatch.
 */
@DisplayName("B-7: Ungarrison UI dispatch (U hotkey)")
class UngarrisonUiDispatchTest {

    private InputHandler inputHandler;
    private SelectionManager selectionManager;
    private List<String> issuedCommands;

    @BeforeEach
    void setUp() {
        selectionManager = new SelectionManager();
        CameraController cameraController = new CameraController();
        cameraController.setEdgeScrollEnabled(false);
        IsometricRenderer isoRenderer = new IsometricRenderer();
        inputHandler = new InputHandler(selectionManager, cameraController, isoRenderer);
        issuedCommands = new ArrayList<>();

        inputHandler.setCommandCallback((command, targetGx, targetGy) ->
            issuedCommands.add(command + ":" + targetGx + "," + targetGy));

        // Wire up a real EntityManager with a real unit so SelectionManager has a selection
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

    @Test
    @DisplayName("Pressing U dispatches 'ungarrison' command via callback")
    void pressingUDispatchesUngarrisonCommand() {
        // When: the player presses U
        inputHandler.onKeyPressed(keyPress(KeyCode.U));

        // Then: an "ungarrison" command should be issued via the callback
        assertEquals(1, issuedCommands.size(),
            "Exactly one command should be issued when U is pressed");
        assertTrue(issuedCommands.get(0).startsWith("ungarrison:"),
            "Command should be 'ungarrison', got: " + issuedCommands.get(0));
    }

    @Test
    @DisplayName("Ungarrison command is issued with no target coordinates (-1, -1)")
    void ungarrisonCommandHasNoTarget() {
        // When: the player presses U
        inputHandler.onKeyPressed(keyPress(KeyCode.U));

        // Then: the command should have target coordinates (-1, -1) because
        // ungarrison operates on the currently selected building, not a click target
        assertEquals("ungarrison:-1,-1", issuedCommands.get(0),
            "Ungarrison command should have target (-1, -1) — it operates on selected buildings");
    }

    @Test
    @DisplayName("U hotkey does not interfere with other hotkeys (A, S, D still work)")
    void uHotkeyDoesNotInterfereWithOthers() {
        // When: pressing each hotkey in sequence
        inputHandler.onKeyPressed(keyPress(KeyCode.A));
        inputHandler.onKeyPressed(keyPress(KeyCode.S));
        inputHandler.onKeyPressed(keyPress(KeyCode.D));
        inputHandler.onKeyPressed(keyPress(KeyCode.U));

        // Then: 4 commands should be issued (attack_move mode set, stop, siege_mode, ungarrison)
        // Note: A sets commandMode to ATTACK_MOVE but doesn't issue a command by itself.
        // S issues "stop", D issues "siege_mode", U issues "ungarrison".
        assertEquals(3, issuedCommands.size(),
            "S, D, U should each issue a command (A only sets mode)");
        assertTrue(issuedCommands.contains("stop:-1,-1"));
        assertTrue(issuedCommands.contains("siege_mode:-1,-1"));
        assertTrue(issuedCommands.contains("ungarrison:-1,-1"));
    }
}
