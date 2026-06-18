package com.aow2.client;

import com.aow2.client.scene.CampaignScene;
import com.aow2.client.scene.GameScene;
import com.aow2.client.scene.MainMenuScene;
import com.aow2.client.scene.MapEditorScene;
import com.aow2.client.scene.ModManagerScene;
import com.aow2.client.scene.MultiplayerLobbyScene;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main FXGL application entry point for Art of War 2: Online.
 * <p>
 * Manages the overall application lifecycle, scene transitions,
 * and window configuration. The game uses custom JavaFX scenes
 * rendered on top of the FXGL framework.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3 - Architecture Overview
 */
public class AOW2App extends GameApplication {

    private static final Logger LOG = LoggerFactory.getLogger(AOW2App.class);

    /** Application version. */
    private static final String VERSION = "0.2.0-ALPHA";

    /** Main menu scene. */
    private MainMenuScene mainMenuScene;

    /** Game scene. */
    private GameScene gameScene;

    /** Map editor scene. */
    private MapEditorScene mapEditorScene;

    /** Multiplayer lobby scene. */
    private MultiplayerLobbyScene multiplayerLobbyScene;

    /** Mod manager scene. */
    private ModManagerScene modManagerScene;

    /** Campaign scene. */
    private CampaignScene campaignScene;

    /** Current active scene state. */
    private ActiveScene activeScene;

    /**
     * Enum representing which scene is currently active.
     */
    private enum ActiveScene {
        MAIN_MENU, GAME, MAP_EDITOR, MULTIPLAYER_LOBBY, MOD_MANAGER, CAMPAIGN
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Art of War 2: Online");
        settings.setVersion(VERSION);
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFullScreenAllowed(true);
        settings.setManualResizeEnabled(true);
        settings.setIntroEnabled(false);
        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);
        settings.setProfilingEnabled(false);
        settings.setCloseConfirmation(true);
        settings.setDeveloperMenuEnabled(false);

        LOG.info("Game settings initialized: {}x{}", 1280, 720);
    }

    @Override
    protected void initUI() {
        // Create and show the main menu
        showMainMenu();
    }

    /**
     * Shows the main menu scene.
     */
    private void showMainMenu() {
        mainMenuScene = new MainMenuScene();
        mainMenuScene.setCallback(this::handleMenuAction);

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(mainMenuScene.getRoot());

        activeScene = ActiveScene.MAIN_MENU;
        LOG.info("Main menu scene displayed");
    }

    /**
     * Shows the game scene.
     */
    private void showGame() {
        if (gameScene != null) {
            gameScene.stop();
        }

        gameScene = new GameScene();
        gameScene.initializeGame();

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(gameScene.getRoot());

        gameScene.start();
        gameScene.getGameCanvas().requestFocus();

        activeScene = ActiveScene.GAME;
        LOG.info("Game scene displayed");
    }

    /**
     * Shows the multiplayer lobby scene.
     */
    private void showMultiplayerLobby() {
        if (multiplayerLobbyScene != null) {
            multiplayerLobbyScene.dispose();
        }

        multiplayerLobbyScene = new MultiplayerLobbyScene();
        multiplayerLobbyScene.setCallback(new MultiplayerLobbyScene.SceneCallback() {
            @Override
            public void onSearchMatch() {
                LOG.info("Player searching for match");
            }

            @Override
            public void onCancelSearch() {
                LOG.info("Player cancelled matchmaking");
            }

            @Override
            public void onMatchFound(String sessionUuid) {
                LOG.info("Match found! Session: {}", sessionUuid);
                showGame();
            }

            @Override
            public void onBack() {
                showMainMenu();
            }
        });

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(multiplayerLobbyScene.getRoot());

        activeScene = ActiveScene.MULTIPLAYER_LOBBY;
        LOG.info("Multiplayer lobby scene displayed");
    }

    /**
     * Shows the campaign selection scene.
     */
    private void showCampaign() {
        campaignScene = new CampaignScene();
        campaignScene.setCallback(new CampaignScene.SceneCallback() {
            @Override
            public void onStartMission(int episodeIndex, int missionIndex) {
                LOG.info("Starting campaign mission: episode={}, mission={}", episodeIndex, missionIndex);
                showGame();
            }

            @Override
            public void onBack() {
                showMainMenu();
            }
        });

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(campaignScene.getRoot());

        activeScene = ActiveScene.CAMPAIGN;
        LOG.info("Campaign scene displayed");
    }

    /**
     * Shows the mod manager scene.
     */
    private void showModManager() {
        modManagerScene = new ModManagerScene();
        modManagerScene.setOnBackCallback(this::showMainMenu);

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(modManagerScene.getRoot());

        activeScene = ActiveScene.MOD_MANAGER;
        LOG.info("Mod manager scene displayed");
    }

    /**
     * Handles menu actions from the main menu.
     *
     * @param action the action string from MainMenuScene
     */
    private void handleMenuAction(String action) {
        LOG.info("Menu action: {}", action);

        switch (action) {
            case "campaign" -> {
                LOG.info("Opening Campaign selection");
                showCampaign();
            }
            case "skirmish" -> {
                LOG.info("Starting Skirmish mode");
                showGame();
            }
            case "multiplayer" -> {
                LOG.info("Starting Multiplayer lobby");
                showMultiplayerLobby();
            }
            case "map_editor" -> {
                LOG.info("Starting Map Editor");
                showMapEditor();
            }
            case "mods" -> {
                LOG.info("Opening Mod Manager");
                showModManager();
            }
            case "settings" -> {
                LOG.info("Settings not yet implemented");
            }
            case "quit" -> {
                LOG.info("Quit requested");
                FXGL.getGameController().exit();
            }
            default -> LOG.warn("Unknown menu action: {}", action);
        }
    }

    /**
     * Shows the map editor scene.
     */
    private void showMapEditor() {
        if (mapEditorScene != null) {
            mapEditorScene.stop();
        }

        mapEditorScene = new MapEditorScene();
        mapEditorScene.initialize();
        mapEditorScene.setOnExitCallback(this::showMainMenu);

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(mapEditorScene.getRoot());

        mapEditorScene.start();

        activeScene = ActiveScene.MAP_EDITOR;
        LOG.info("Map editor scene displayed");
    }

    /**
     * Called when the application is about to exit.
     * Performs cleanup of scene resources.
     */
    protected void cleanupOnExit() {
        if (gameScene != null) {
            gameScene.stop();
        }
        if (mapEditorScene != null) {
            mapEditorScene.stop();
        }
        if (multiplayerLobbyScene != null) {
            multiplayerLobbyScene.dispose();
        }
        if (mainMenuScene != null) {
            mainMenuScene.dispose();
        }
        LOG.info("Application exiting");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
