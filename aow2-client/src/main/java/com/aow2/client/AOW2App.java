package com.aow2.client;

import com.aow2.client.scene.CampaignScene;
import com.aow2.client.scene.GameScene;
import com.aow2.client.scene.MainMenuScene;
import com.aow2.client.scene.MapEditorScene;
import com.aow2.client.scene.ModManagerScene;
import com.aow2.client.scene.MultiplayerLobbyScene;
import com.aow2.client.scene.SettingsScene;

import com.aow2.core.campaign.CampaignEpisode;
import com.aow2.core.campaign.CampaignManager;
import com.aow2.core.campaign.Mission;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;

import javafx.scene.control.ChoiceDialog;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

    /** Campaign manager shared between CampaignScene and GameScene. */
    private CampaignManager campaignManager;

    /** Settings scene. */
    private SettingsScene settingsScene;

    /** Current active scene state. */
    private ActiveScene activeScene;

    /**
     * Enum representing which scene is currently active.
     */
    private enum ActiveScene {
        MAIN_MENU, GAME, MAP_EDITOR, MULTIPLAYER_LOBBY, MOD_MANAGER, CAMPAIGN, SETTINGS
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
     * Shows the game scene with the default test map.
     */
    private void showGame() {
        showGame(null);
    }

    /**
     * Shows the game scene, optionally loading a specific map from a classpath resource.
     * If mapResourcePath is null, uses the default test map.
     *
     * @param mapResourcePath classpath resource path for the map (e.g., "data/maps/test_map.json"),
     *                        or null to use the test map
     */
    private void showGame(String mapResourcePath) {
        if (gameScene != null) {
            gameScene.stop();
            // Clear references to allow GC before allocating a new scene
            gameScene = null;
        }

        FXGL.getGameScene().clearUINodes();

        gameScene = new GameScene();
        if (mapResourcePath != null && !mapResourcePath.isBlank()) {
            gameScene.initializeGame(mapResourcePath);
        } else {
            gameScene.initializeGame();
        }

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(gameScene.getRoot());

        gameScene.start();
        gameScene.getGameCanvas().requestFocus();

        activeScene = ActiveScene.GAME;
        LOG.info("Game scene displayed (map: {})",
            mapResourcePath != null ? mapResourcePath : "test map");
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
        if (campaignScene != null) {
            FXGL.getGameScene().clearUINodes();
            campaignScene = null;
        }

        campaignScene = new CampaignScene();
        // Create or reuse the CampaignManager so state persists across scene transitions
        if (campaignManager == null) {
            campaignManager = new CampaignManager();
        }
        campaignScene.setCampaignManager(campaignManager);

        campaignScene.setCallback(new CampaignScene.SceneCallback() {
            @Override
            public void onStartMission(int episodeIndex, int missionIndex) {
                LOG.info("Starting campaign mission: episode={}, mission={}", episodeIndex, missionIndex);
                // Resolve the episode enum from index
                CampaignEpisode episode = switch (episodeIndex) {
                    case 0 -> CampaignEpisode.GLOBAL_CONFEDERATION;
                    case 1 -> CampaignEpisode.LIBERATION_OF_PERU;
                    default -> CampaignEpisode.CUSTOM_MISSIONS;
                };
                // Start the campaign so the manager tracks the active episode
                campaignManager.startCampaign(episode);
                // Set the correct mission index for the selected mission
                campaignManager.setCurrentMissionIndex(missionIndex);
                // Get the mission's map file from CampaignManager
                List<Mission> missions = campaignManager.getMissionsForEpisode(episode);
                String mapPath = null;
                if (missionIndex < missions.size()) {
                    mapPath = missions.get(missionIndex).mapFile();
                }
                // Launch the game scene with the campaign map
                showGame(mapPath);
                // Wire campaign context into the newly created GameScene
                if (gameScene != null) {
                    gameScene.setCampaignContext(campaignManager, episodeIndex, missionIndex);
                    gameScene.setCampaignEndCallback(() -> {
                        LOG.info("Campaign mission ended, returning to campaign scene");
                        showCampaign();
                    });
                }
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
        if (modManagerScene != null) {
            FXGL.getGameScene().clearUINodes();
            modManagerScene = null;
        }

        modManagerScene = new ModManagerScene();
        modManagerScene.setOnBackCallback(this::showMainMenu);

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(modManagerScene.getRoot());

        activeScene = ActiveScene.MOD_MANAGER;
        LOG.info("Mod manager scene displayed");
    }

    /**
     * Shows the settings scene.
     * FIX (L-NEW-10): Replaces blocking Alert dialog with proper scene.
     */
    private void showSettings() {
        if (settingsScene != null) {
            FXGL.getGameScene().clearUINodes();
            settingsScene = null;
        }

        settingsScene = new SettingsScene();
        settingsScene.setCallback(this::showMainMenu);

        FXGL.getGameScene().clearUINodes();
        FXGL.getGameScene().addUINode(settingsScene.getRoot());

        activeScene = ActiveScene.SETTINGS;
        LOG.info("Settings scene displayed");
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
                showSkirmishMapSelection();
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
                LOG.info("Opening Settings");
                showSettings();
            }
            case "quit" -> {
                LOG.info("Quit requested");
                FXGL.getGameController().exit();
            }
            default -> LOG.warn("Unknown menu action: {}", action);
        }
    }

    /**
     * Shows a map selection dialog for Skirmish mode, then starts the game
     * with the selected map.
     */
    private void showSkirmishMapSelection() {
        List<String> availableMaps = discoverMapResources();
        if (availableMaps.isEmpty()) {
            LOG.warn("No map files found on classpath, falling back to test map");
            showGame();
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(availableMaps.get(0), availableMaps);
        dialog.setTitle("Select Map");
        dialog.setHeaderText("Choose a map for Skirmish mode:");
        dialog.initOwner(FXGL.getGameScene().getRoot().getScene().getWindow());

        var result = dialog.showAndWait();
        if (result.isPresent()) {
            LOG.info("Skirmish map selected: {}", result.get());
            showGame(result.get());
        } else {
            LOG.info("Skirmish map selection cancelled, using test map");
            showGame();
        }
    }

    /**
     * Scans the classpath for available map JSON files under data/maps/.
     *
     * @return list of classpath resource paths for discovered maps
     */
    private List<String> discoverMapResources() {
        List<String> mapPaths = new ArrayList<>();
        try {
            // Try classpath scanning via ClassLoader resources
            var url = getClass().getClassLoader().getResource("data/maps");
            if (url != null && url.getProtocol().equals("file")) {
                // Development mode: direct filesystem access
                Path mapsDir = Paths.get(url.toURI());
                try (Stream<Path> files = Files.list(mapsDir)) {
                    files.filter(p -> p.toString().endsWith(".json"))
                        .sorted()
                        .forEach(p -> mapPaths.add("data/maps/" + p.getFileName().toString()));
                }
            } else if (url != null) {
                // JAR mode: scan entries via FileSystem
                try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
                    Path mapsDir = fs.getPath("/data/maps");
                    try (Stream<Path> files = Files.list(mapsDir)) {
                        files.filter(p -> p.toString().endsWith(".json"))
                            .sorted()
                            .forEach(p -> mapPaths.add("data/maps/" + p.getFileName().toString()));
                    }
                }
            }
        } catch (URISyntaxException | IOException e) {
            LOG.warn("Failed to scan classpath for map files: {}", e.getMessage());
        }

        // Remove duplicates (maps may appear in both aow2-client and aow2-core resources)
        List<String> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String path : mapPaths) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (seen.add(filename)) {
                deduped.add(path);
            }
        }

        LOG.info("Discovered {} map files on classpath", deduped.size());
        return deduped;
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
    @Override
    protected void onExit() {
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
