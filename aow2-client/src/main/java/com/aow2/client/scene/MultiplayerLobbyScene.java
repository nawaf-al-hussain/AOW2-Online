package com.aow2.client.scene;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Multiplayer lobby screen for matchmaking.
 * <p>
 * Displays:
 * - Player list with status (searching, ready, in-game)
 * - Matchmaking queue status and estimated wait time
 * - Chat area for lobby communication
 * - Ready/Cancel buttons
 * - Server status indicator
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Multiplayer networking
 * REF: protocol_specification.md - Lobby and matchmaking messages
 */
public class MultiplayerLobbyScene {

    private static final Logger LOG = LoggerFactory.getLogger(MultiplayerLobbyScene.class);

    /** The root pane for this scene. */
    private final StackPane root;

    /** Whether the player is currently searching for a match. */
    private boolean searching;

    /** Timeline for the searching animation. */
    private Timeline searchAnimation;

    /** Search elapsed time in seconds. */
    private int searchElapsedSeconds;

    /** Players currently in the lobby. */
    private final List<LobbyPlayer> players;

    /** Callback for scene navigation. */
    private SceneCallback callback;

    /**
     * Represents a player in the multiplayer lobby.
     *
     * @param name     player display name
     * @param rating   player ELO rating
     * @param status   player status (searching, ready, in-game)
     * @param faction  preferred faction
     */
    public record LobbyPlayer(String name, int rating, String status, String faction) {}

    /**
     * Callback interface for scene transitions.
     */
    public interface SceneCallback {
        /**
         * Called when the player starts searching for a match.
         */
        void onSearchMatch();

        /**
         * Called when the player cancels matchmaking.
         */
        void onCancelSearch();

        /**
         * Called when a match is found and the game starts.
         */
        void onMatchFound();

        /**
         * Called when the player goes back to the main menu.
         */
        void onBack();
    }

    /**
     * Constructs a new MultiplayerLobbyScene.
     */
    public MultiplayerLobbyScene() {
        this.root = new StackPane();
        this.searching = false;
        this.searchElapsedSeconds = 0;
        this.players = new ArrayList<>();

        // Populate with test players
        players.add(new LobbyPlayer("Commander_Alpha", 1200, "ready", "CONFEDERATION"));
        players.add(new LobbyPlayer("Rebel_Leader", 1150, "searching", "RESISTANCE"));
        players.add(new LobbyPlayer("TankCommander", 1300, "in-game", "CONFEDERATION"));
        players.add(new LobbyPlayer("SniperElite", 1080, "ready", "RESISTANCE"));

        buildUI();
        LOG.info("MultiplayerLobbyScene created");
    }

    /**
     * Build the multiplayer lobby UI.
     */
    private void buildUI() {
        root.setPrefSize(1280, 720);
        root.setStyle("-fx-background-color: rgb(15, 18, 22);");

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(20));

        // Title bar
        VBox titleBox = new VBox(5);
        Label titleLabel = new Label("MULTIPLAYER LOBBY");
        titleLabel.setFont(Font.font("Consolas", 28));
        titleLabel.setTextFill(Color.rgb(210, 200, 160));

        // Server status indicator
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Circle statusDot = new Circle(6, Color.rgb(80, 200, 80));
        Label statusLabel = new Label("Server Online — 47 players");
        statusLabel.setFont(Font.font("Consolas", 12));
        statusLabel.setTextFill(Color.rgb(120, 130, 100));
        statusBox.getChildren().addAll(statusDot, statusLabel);

        titleBox.getChildren().addAll(titleLabel, statusBox);
        content.setTop(titleBox);

        // Main content area
        HBox mainContent = new HBox(20);

        // Left: Player list
        VBox playerListPanel = createPlayerListPanel();
        mainContent.getChildren().add(playerListPanel);

        // Center: Search/matchmaking area
        VBox searchPanel = createSearchPanel();
        mainContent.getChildren().add(searchPanel);

        // Right: Chat area (placeholder)
        VBox chatPanel = createChatPanel();
        mainContent.getChildren().add(chatPanel);

        HBox.setHgrow(searchPanel, javafx.scene.layout.Priority.ALWAYS);
        content.setCenter(mainContent);

        // Bottom bar
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.BOTTOM_LEFT);
        Button backButton = new Button("← Back to Menu");
        backButton.setStyle("-fx-background-color: rgb(40, 45, 35); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(100, 110, 70); "
            + "-fx-padding: 8 20 8 20; -fx-cursor: hand;");
        backButton.setOnAction(e -> {
            cancelSearch();
            if (callback != null) {
                callback.onBack();
            }
        });
        bottomBar.getChildren().add(backButton);
        content.setBottom(bottomBar);

        root.getChildren().add(content);
    }

    /**
     * Create the player list panel.
     *
     * @return the panel VBox
     */
    private VBox createPlayerListPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(320);
        panel.setStyle("-fx-background-color: rgb(20, 23, 18); "
            + "-fx-border-color: rgb(40, 45, 35); -fx-border-width: 1;");

        Label listTitle = new Label("Players in Lobby");
        listTitle.setFont(Font.font("Consolas", 14));
        listTitle.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        VBox playerEntries = new VBox(6);
        for (LobbyPlayer player : players) {
            HBox entry = new HBox(10);
            entry.setAlignment(Pos.CENTER_LEFT);
            entry.setPadding(new Insets(6));
            entry.setStyle("-fx-background-color: rgb(25, 28, 22);");

            Circle statusCircle = new Circle(4, getPlayerStatusColor(player.status()));
            Label nameLabel = new Label(player.name());
            nameLabel.setFont(Font.font("Consolas", 12));
            nameLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
            Label ratingLabel = new Label("(" + player.rating() + ")");
            ratingLabel.setFont(Font.font("Consolas", 10));
            ratingLabel.setStyle("-fx-text-fill: rgb(100, 100, 80);");
            Label factionLabel = new Label(player.faction());
            factionLabel.setFont(Font.font("Consolas", 10));
            factionLabel.setStyle("-fx-text-fill: rgb(100, 110, 70);");

            entry.getChildren().addAll(statusCircle, nameLabel, ratingLabel, factionLabel);
            playerEntries.getChildren().add(entry);
        }

        panel.getChildren().addAll(listTitle, playerEntries);
        return panel;
    }

    /**
     * Create the search/matchmaking panel.
     *
     * @return the panel VBox
     */
    private VBox createSearchPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setAlignment(Pos.CENTER);

        Label searchTitle = new Label("Find Match");
        searchTitle.setFont(Font.font("Consolas", 22));
        searchTitle.setStyle("-fx-text-fill: rgb(210, 200, 160);");

        Label searchDesc = new Label("Search for an opponent of similar skill level");
        searchDesc.setFont(Font.font("Consolas", 13));
        searchDesc.setStyle("-fx-text-fill: rgb(120, 120, 100);");

        // Search button
        Button searchButton = new Button("Search for Match");
        searchButton.setId("search-button");
        searchButton.setStyle("-fx-background-color: rgb(60, 70, 45); "
            + "-fx-text-fill: rgb(240, 230, 180); -fx-border-color: rgb(140, 150, 90); "
            + "-fx-font-size: 16px; -fx-font-weight: bold; "
            + "-fx-padding: 12 40 12 40; -fx-cursor: hand;");
        searchButton.setOnAction(e -> {
            if (!searching) {
                startSearch();
            } else {
                cancelSearch();
            }
        });

        // Search status label
        Label searchStatus = new Label("");
        searchStatus.setId("search-status");
        searchStatus.setFont(Font.font("Consolas", 14));
        searchStatus.setStyle("-fx-text-fill: rgb(140, 150, 90);");

        // Progress bar for search
        ProgressBar searchProgress = new ProgressBar();
        searchProgress.setId("search-progress");
        searchProgress.setPrefWidth(300);
        searchProgress.setProgress(0);
        searchProgress.setVisible(false);

        panel.getChildren().addAll(searchTitle, searchDesc, searchButton,
            searchStatus, searchProgress);
        return panel;
    }

    /**
     * Create the chat panel (placeholder).
     *
     * @return the panel VBox
     */
    private VBox createChatPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(280);
        panel.setStyle("-fx-background-color: rgb(20, 23, 18); "
            + "-fx-border-color: rgb(40, 45, 35); -fx-border-width: 1;");

        Label chatTitle = new Label("Lobby Chat");
        chatTitle.setFont(Font.font("Consolas", 14));
        chatTitle.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        VBox messages = new VBox(4);
        messages.setPrefHeight(400);
        messages.setStyle("-fx-background-color: rgb(18, 20, 16);");

        // Placeholder messages
        addChatMessage(messages, "Commander_Alpha", "GG everyone!");
        addChatMessage(messages, "Rebel_Leader", "Looking for 1v1");
        addChatMessage(messages, "System", "Server maintenance at 02:00 UTC");

        panel.getChildren().addAll(chatTitle, messages);
        return panel;
    }

    /**
     * Add a chat message to the message list.
     */
    private void addChatMessage(VBox messages, String sender, String text) {
        Label msg = new Label(sender + ": " + text);
        msg.setFont(Font.font("Consolas", 11));
        msg.setStyle("-fx-text-fill: rgb(150, 145, 125);");
        msg.setWrapText(true);
        messages.getChildren().add(msg);
    }

    /**
     * Get the color for a player status.
     */
    private Color getPlayerStatusColor(String status) {
        return switch (status) {
            case "ready" -> Color.rgb(80, 200, 80);
            case "searching" -> Color.rgb(200, 200, 80);
            case "in-game" -> Color.rgb(200, 80, 80);
            default -> Color.rgb(128, 128, 128);
        };
    }

    /**
     * Start searching for a match.
     */
    public void startSearch() {
        searching = true;
        searchElapsedSeconds = 0;

        Button searchButton = (Button) root.lookup("#search-button");
        if (searchButton != null) {
            searchButton.setText("Cancel Search");
            searchButton.setStyle("-fx-background-color: rgb(80, 40, 40); "
                + "-fx-text-fill: rgb(240, 180, 180); -fx-border-color: rgb(150, 80, 80); "
                + "-fx-font-size: 16px; -fx-font-weight: bold; "
                + "-fx-padding: 12 40 12 40; -fx-cursor: hand;");
        }

        ProgressBar progress = (ProgressBar) root.lookup("#search-progress");
        if (progress != null) {
            progress.setVisible(true);
            progress.setProgress(-1); // Indeterminate
        }

        // Start search animation timer
        searchAnimation = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            searchElapsedSeconds++;
            Label status = (Label) root.lookup("#search-status");
            if (status != null) {
                int minutes = searchElapsedSeconds / 60;
                int seconds = searchElapsedSeconds % 60;
                status.setText(String.format("Searching... %d:%02d", minutes, seconds));
            }
        }));
        searchAnimation.setCycleCount(Animation.INDEFINITE);
        searchAnimation.play();

        if (callback != null) {
            callback.onSearchMatch();
        }
        LOG.info("Matchmaking search started");
    }

    /**
     * Cancel the matchmaking search.
     */
    public void cancelSearch() {
        searching = false;
        searchElapsedSeconds = 0;

        if (searchAnimation != null) {
            searchAnimation.stop();
            searchAnimation = null;
        }

        Button searchButton = (Button) root.lookup("#search-button");
        if (searchButton != null) {
            searchButton.setText("Search for Match");
            searchButton.setStyle("-fx-background-color: rgb(60, 70, 45); "
                + "-fx-text-fill: rgb(240, 230, 180); -fx-border-color: rgb(140, 150, 90); "
                + "-fx-font-size: 16px; -fx-font-weight: bold; "
                + "-fx-padding: 12 40 12 40; -fx-cursor: hand;");
        }

        ProgressBar progress = (ProgressBar) root.lookup("#search-progress");
        if (progress != null) {
            progress.setVisible(false);
            progress.setProgress(0);
        }

        Label status = (Label) root.lookup("#search-status");
        if (status != null) {
            status.setText("");
        }

        if (callback != null) {
            callback.onCancelSearch();
        }
        LOG.info("Matchmaking search cancelled");
    }

    /**
     * Notify that a match has been found.
     */
    public void matchFound() {
        if (searchAnimation != null) {
            searchAnimation.stop();
        }

        Label status = (Label) root.lookup("#search-status");
        if (status != null) {
            status.setText("Match found! Starting game...");
            status.setStyle("-fx-text-fill: rgb(80, 200, 80);");
        }

        if (callback != null) {
            callback.onMatchFound();
        }
        LOG.info("Match found!");
    }

    /**
     * Check if currently searching for a match.
     *
     * @return true if searching
     */
    public boolean isSearching() {
        return searching;
    }

    /**
     * Get the root pane for this scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Set the scene callback.
     *
     * @param callback the callback
     */
    public void setCallback(SceneCallback callback) {
        this.callback = callback;
    }
}
