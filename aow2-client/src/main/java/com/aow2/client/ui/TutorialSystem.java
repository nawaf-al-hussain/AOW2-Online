package com.aow2.client.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple tutorial overlay that shows tooltips for new players.
 * <p>
 * Guides the player through basic game mechanics step by step:
 * 1. Selecting units
 * 2. Moving units
 * 3. Attacking enemies
 * 4. Building structures
 * 5. Producing units
 * 6. Researching technology
 * <p>
 * Each step displays a tooltip with instructions and a "Next" button.
 * The tutorial can be dismissed at any time and will not reappear
 * once completed.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Tutorial System
 */
public final class TutorialSystem {

    private static final Logger LOG = LoggerFactory.getLogger(TutorialSystem.class);

    /** Tutorial step record containing display information. */
    public record TutorialStep(String title, String description, String highlightAction) {}

    /** All tutorial steps in order. */
    private static final TutorialStep[] STEPS = {
        new TutorialStep("Welcome!", "Welcome to Art of War 2: Online! This tutorial will guide you through the basics.", null),
        new TutorialStep("Select Units", "Left-click on a unit to select it. Drag a box to select multiple units.", "select"),
        new TutorialStep("Move Units", "Right-click on the map to move selected units to that location.", "move"),
        new TutorialStep("Attack Enemies", "Select units, then right-click on an enemy unit or building to attack.", "attack"),
        new TutorialStep("Build Structures", "Press B to open the build menu, then click a position on the map to place a building.", "build"),
        new TutorialStep("Produce Units", "Select a production building (Infantry Centre or Factory), then choose a unit to produce.", "produce"),
        new TutorialStep("Research Technology", "Select a Tech Centre to research upgrades that improve your units and buildings.", "research"),
        new TutorialStep("Victory!", "Destroy the enemy HQ to win! Good luck, Commander!", null)
    };

    /** The root overlay pane. */
    private final StackPane overlay;

    /** The tooltip container. */
    private final VBox tooltipBox;

    /** The title label. */
    private final Label titleLabel;

    /** The description label. */
    private final Label descLabel;

    /** The next button. */
    private final Button nextButton;

    /** The skip button. */
    private final Button skipButton;

    /** The step indicator label. */
    private final Label stepIndicator;

    /** Current step index. */
    private int currentStep;

    /** Whether the tutorial has been completed or skipped. */
    private boolean completed;

    /** Queue of steps for sequential playback. */
    private final Deque<Integer> stepQueue;

    /** Callback for tutorial step highlighting. */
    private TutorialCallback callback;

    /**
     * Callback interface for tutorial highlight actions.
     */
    public interface TutorialCallback {
        /**
         * Called when a tutorial step wants to highlight a UI element.
         *
         * @param action the action to highlight (e.g., "select", "move")
         */
        void onHighlight(String action);

        /**
         * Called when the tutorial is completed or skipped.
         */
        void onTutorialComplete();
    }

    /**
     * Constructs a new TutorialSystem.
     */
    public TutorialSystem() {
        this.overlay = new StackPane();
        this.tooltipBox = new VBox(10);
        this.titleLabel = new Label();
        this.descLabel = new Label();
        this.nextButton = new Button("Next");
        this.skipButton = new Button("Skip Tutorial");
        this.stepIndicator = new Label();
        this.currentStep = 0;
        this.completed = false;
        this.stepQueue = new ArrayDeque<>();

        buildUI();
        LOG.info("TutorialSystem created with {} steps", STEPS.length);
    }

    /**
     * Build the tutorial overlay UI.
     */
    private void buildUI() {
        // Semi-transparent backdrop
        overlay.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);

        // Tooltip box positioned in the center-bottom area
        tooltipBox.setAlignment(Pos.CENTER);
        tooltipBox.setMaxWidth(500);
        tooltipBox.setPadding(new Insets(20));
        tooltipBox.setStyle("-fx-background-color: rgba(20, 25, 18, 0.95); "
            + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 2; "
            + "-fx-border-radius: 8; -fx-background-radius: 8;");

        // Title
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; "
            + "-fx-text-fill: rgb(210, 200, 160);");
        titleLabel.setWrapText(true);

        // Description
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: rgb(180, 175, 145);");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(450);

        // Step indicator
        stepIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: rgb(100, 100, 80);");

        // Next button
        nextButton.setStyle("-fx-background-color: rgb(60, 70, 45); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(100, 110, 70); "
            + "-fx-padding: 6 20 6 20; -fx-cursor: hand;");
        nextButton.setOnAction(e -> advanceStep());

        // Skip button
        skipButton.setStyle("-fx-background-color: transparent; "
            + "-fx-text-fill: rgb(120, 115, 95); -fx-padding: 4 10 4 10; -fx-cursor: hand;");
        skipButton.setOnAction(e -> skipTutorial());

        // Button row
        javafx.scene.layout.HBox buttonRow = new javafx.scene.layout.HBox(10, nextButton, skipButton);
        buttonRow.setAlignment(Pos.CENTER);

        tooltipBox.getChildren().addAll(titleLabel, descLabel, stepIndicator, buttonRow);
        StackPane.setAlignment(tooltipBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(tooltipBox, new Insets(0, 0, 80, 0));

        overlay.getChildren().add(tooltipBox);
    }

    /**
     * Start the tutorial from the first step.
     */
    public void start() {
        if (completed) {
            return;
        }
        currentStep = 0;
        overlay.setVisible(true);
        overlay.setMouseTransparent(false);
        showStep(currentStep);
        LOG.info("Tutorial started");
    }

    /**
     * Advance to the next tutorial step.
     */
    public void advanceStep() {
        currentStep++;
        if (currentStep >= STEPS.length) {
            completeTutorial();
            return;
        }
        showStep(currentStep);
    }

    /**
     * Show a specific tutorial step with a fade animation.
     *
     * @param index the step index
     */
    private void showStep(int index) {
        TutorialStep step = STEPS[index];
        titleLabel.setText(step.title());
        descLabel.setText(step.description());
        stepIndicator.setText(String.format("Step %d of %d", index + 1, STEPS.length));

        // Update next button text on last step
        nextButton.setText(index == STEPS.length - 1 ? "Finish" : "Next");

        // Notify callback for UI highlighting
        if (callback != null && step.highlightAction() != null) {
            callback.onHighlight(step.highlightAction());
        }

        // Fade animation
        FadeTransition fade = new FadeTransition(Duration.millis(200), tooltipBox);
        fade.setFromValue(0.5);
        fade.setToValue(1.0);
        fade.play();

        LOG.debug("Tutorial step {}: {}", index + 1, step.title());
    }

    /**
     * Skip the entire tutorial.
     */
    public void skipTutorial() {
        completeTutorial();
        LOG.info("Tutorial skipped");
    }

    /**
     * Complete the tutorial and hide the overlay.
     */
    private void completeTutorial() {
        completed = true;
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);

        if (callback != null) {
            callback.onTutorialComplete();
        }
        LOG.info("Tutorial completed");
    }

    /**
     * Check if the tutorial has been completed.
     *
     * @return true if completed or skipped
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Get the current step index.
     *
     * @return current step index (0-based)
     */
    public int getCurrentStep() {
        return currentStep;
    }

    /**
     * Get the overlay pane for adding to the scene graph.
     *
     * @return the overlay StackPane
     */
    public StackPane getOverlay() {
        return overlay;
    }

    /**
     * Set the tutorial callback.
     *
     * @param callback the callback
     */
    public void setCallback(TutorialCallback callback) {
        this.callback = callback;
    }

    /**
     * Reset the tutorial so it can be played again.
     */
    public void reset() {
        completed = false;
        currentStep = 0;
    }
}
