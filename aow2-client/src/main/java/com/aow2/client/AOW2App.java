package com.aow2.client;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;

import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * Main FXGL application entry point for Art of War 2: Online.
 * Opens the game window with basic settings.
 */
public class AOW2App extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Art of War 2: Online");
        settings.setVersion("0.1.0-SNAPSHOT");
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFullScreenAllowed(true);
        settings.setManualResizeEnabled(true);
        settings.setIntroEnabled(false);
        settings.setMenuEnabled(true);
        settings.setProfilingEnabled(false);
        settings.setCloseConfirmation(false);
    }

    @Override
    protected void initUI() {
        Text titleText = new Text("Art of War 2: Online");
        titleText.setFill(Color.WHITE);
        titleText.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        titleText.setTranslateX(50);
        titleText.setTranslateY(50);
        FXGL.getGameScene().addUINode(titleText);

        Text statusText = new Text("Phase 0: Project Scaffolding Complete");
        statusText.setFill(Color.LIGHTGRAY);
        statusText.setStyle("-fx-font-size: 14px;");
        statusText.setTranslateX(50);
        statusText.setTranslateY(80);
        FXGL.getGameScene().addUINode(statusText);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
