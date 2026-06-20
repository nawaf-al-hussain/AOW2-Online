package com.aow2.client.scene;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal settings scene with placeholder sections and a back button.
 * <p>
 * Sections: Graphics, Audio, Controls — each with placeholder labels.
 * The back button calls the {@link SceneCallback#onBack()} method.
 * <p>
 * FIX (L-NEW-10): Replaces the blocking Alert dialog stub.
 */
public class SettingsScene {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsScene.class);

    /** Button style matching the military theme used across scenes. */
    private static final String BTN_STYLE = "-fx-background-color: rgb(40, 45, 35); "
        + "-fx-text-fill: rgb(210, 200, 160); "
        + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;";

    /** The root pane for this scene. */
    private final StackPane root;

    /** Callback for scene navigation. */
    private SceneCallback callback;

    /**
     * Functional interface for scene navigation callbacks.
     */
    @FunctionalInterface
    public interface SceneCallback {
        /** Called when the user presses the Back button. */
        void onBack();
    }

    /**
     * Constructs a new SettingsScene.
     */
    public SettingsScene() {
        root = new StackPane();
        root.setStyle("-fx-background-color: rgb(25, 30, 20);");
        buildUI();
    }

    /**
     * Builds the settings UI.
     */
    private void buildUI() {
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(40));

        // Title
        Label titleLabel = new Label("Settings");
        titleLabel.setStyle("-fx-text-fill: rgb(210, 200, 160); "
            + "-fx-font-size: 24px; -fx-font-weight: bold; -fx-font-family: Consolas;");

        // Graphics section
        Label graphicsHeader = new Label("Graphics");
        graphicsHeader.setStyle("-fx-text-fill: rgb(210, 200, 160); "
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: Consolas;");
        Label graphicsPlaceholder = new Label("  Resolution, Anti-Aliasing, V-Sync, Texture Quality — coming soon");
        graphicsPlaceholder.setStyle("-fx-text-fill: rgb(140, 135, 110); -fx-font-family: Consolas;");

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-border-color: rgb(80, 85, 60);");

        // Audio section
        Label audioHeader = new Label("Audio");
        audioHeader.setStyle("-fx-text-fill: rgb(210, 200, 160); "
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: Consolas;");
        Label audioPlaceholder = new Label("  Master Volume, Music Volume, SFX Volume — coming soon");
        audioPlaceholder.setStyle("-fx-text-fill: rgb(140, 135, 110); -fx-font-family: Consolas;");

        Separator sep2 = new Separator();
        sep2.setStyle("-fx-border-color: rgb(80, 85, 60);");

        // Controls section
        Label controlsHeader = new Label("Controls");
        controlsHeader.setStyle("-fx-text-fill: rgb(210, 200, 160); "
            + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: Consolas;");
        Label controlsPlaceholder = new Label("  Key Bindings, Mouse Sensitivity — coming soon");
        controlsPlaceholder.setStyle("-fx-text-fill: rgb(140, 135, 110); -fx-font-family: Consolas;");

        // Back button
        Button backButton = new Button("← Back");
        backButton.setStyle(BTN_STYLE);
        backButton.setOnAction(e -> {
            if (callback != null) {
                callback.onBack();
            }
        });

        content.getChildren().addAll(
            titleLabel,
            graphicsHeader, graphicsPlaceholder,
            sep1,
            audioHeader, audioPlaceholder,
            sep2,
            controlsHeader, controlsPlaceholder,
            new Separator(),
            backButton
        );

        root.getChildren().add(content);
    }

    /**
     * Gets the root pane of this scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Sets the scene callback for navigation.
     *
     * @param callback the callback
     */
    public void setCallback(SceneCallback callback) {
        this.callback = callback;
    }
}