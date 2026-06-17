package com.aow2.client.scene;

import com.aow2.client.service.MultiplayerService;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
import java.util.Map;

/**
 * Multiplayer lobby screen for matchmaking.
 * <p>
 * Displays:
 * - Login dialog for authentication
 * - Player stats (ELO, match history) after authentication
 * - Matchmaking queue status and estimated wait time
 * - Chat area for lobby communication via WebSocket
 * - Search/Cancel buttons for matchmaking
 * - Leaderboard tab with real server data
 * - Server status indicator
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Multiplayer networking
 * REF: protocol_specification.md - Lobby and matchmaking messages
 */
public class MultiplayerLobbyScene {

    private static final Logger LOG = LoggerFactory.getLogger(MultiplayerLobbyScene.class);

    /** The root pane for this scene. */
    private final StackPane root;

    /** The multiplayer service for server communication. */
    private final MultiplayerService service;

    /** Whether the player is currently searching for a match. */
    private boolean searching;

    /** Timeline for the searching animation. */
    private Timeline searchAnimation;

    /** Search elapsed time in seconds. */
    private int searchElapsedSeconds;

    /** Callback for scene navigation. */
    private SceneCallback callback;

    /** Chat message container for dynamic updates. */
    private VBox chatMessages;

    /** Chat input field. */
    private TextField chatInput;

    /** Player list container for dynamic updates. */
    private VBox playerEntries;

    /** Leaderboard container for dynamic updates. */
    private VBox leaderboardEntries;

    /** Status label in the title bar. */
    private Label statusLabel;

    /** Status dot in the title bar. */
    private Circle statusDot;

    /** Search status label. */
    private Label searchStatus;

    /** Info panel showing current player stats. */
    private VBox playerInfoPanel;

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
         *
         * @param sessionUuid the game session UUID
         */
        void onMatchFound(String sessionUuid);

        /**
         * Called when the player goes back to the main menu.
         */
        void onBack();
    }

    /**
     * Constructs a new MultiplayerLobbyScene with a default MultiplayerService.
     */
    public MultiplayerLobbyScene() {
        this(new MultiplayerService());
    }

    /**
     * Constructs a new MultiplayerLobbyScene with the given service.
     *
     * @param service the multiplayer service for server communication
     */
    public MultiplayerLobbyScene(MultiplayerService service) {
        this.root = new StackPane();
        this.service = service;
        this.searching = false;
        this.searchElapsedSeconds = 0;

        service.setCallback(new MultiplayerService.MultiplayerCallback() {
            @Override
            public void onMatchFound(String sessionUuid, String opponentName) {
                Platform.runLater(() -> handleMatchFound(sessionUuid, opponentName));
            }

            @Override
            public void onPlayerConnected(long playerId) {
                Platform.runLater(() -> LOG.info("Player connected: {}", playerId));
            }

            @Override
            public void onCommandReceived(long fromPlayerId, Map<String, Object> command) {
                Platform.runLater(() -> LOG.debug("Command from player {}: {}", fromPlayerId, command));
            }

            @Override
            public void onDesyncDetected(long tick) {
                Platform.runLater(() -> showError("Desync detected at tick " + tick));
            }

            @Override
            public void onChatMessage(String senderName, String message) {
                Platform.runLater(() -> addChatMessage(senderName, message));
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> showError(error));
            }
        });

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
        statusDot = new Circle(6, Color.rgb(200, 80, 80));
        statusLabel = new Label("Server Offline — Not connected");
        statusLabel.setFont(Font.font("Consolas", 12));
        statusLabel.setTextFill(Color.rgb(120, 130, 100));
        statusBox.getChildren().addAll(statusDot, statusLabel);

        titleBox.getChildren().addAll(titleLabel, statusBox);
        content.setTop(titleBox);

        // Main content area with tabs
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: rgb(20, 23, 18); "
            + "-fx-border-color: rgb(40, 45, 35);");

        // Lobby tab
        Tab lobbyTab = new Tab("Lobby");
        lobbyTab.setClosable(false);
        lobbyTab.setContent(createLobbyContent());
        tabPane.getTabs().add(lobbyTab);

        // Leaderboard tab
        Tab leaderboardTab = new Tab("Leaderboard");
        leaderboardTab.setClosable(false);
        leaderboardTab.setContent(createLeaderboardContent());
        tabPane.getTabs().add(leaderboardTab);

        content.setCenter(tabPane);

        // Bottom bar
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.BOTTOM_LEFT);
        Button backButton = new Button("← Back to Menu");
        backButton.setStyle("-fx-background-color: rgb(40, 45, 35); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(100, 110, 70); "
            + "-fx-padding: 8 20 8 20; -fx-cursor: hand;");
        backButton.setOnAction(e -> {
            cancelSearch();
            service.disconnect();
            if (callback != null) {
                callback.onBack();
            }
        });
        bottomBar.getChildren().add(backButton);
        content.setBottom(bottomBar);

        root.getChildren().add(content);
    }

    /**
     * Creates the main lobby content with login panel, player info,
     * search controls, and chat.
     *
     * @return the lobby content pane
     */
    private BorderPane createLobbyContent() {
        BorderPane lobby = new BorderPane();
        lobby.setPadding(new Insets(10));

        // Left: Player info / Login
        playerInfoPanel = createLoginPanel();
        lobby.setLeft(playerInfoPanel);

        // Center: Search/matchmaking area
        VBox searchPanel = createSearchPanel();
        lobby.setCenter(searchPanel);

        // Right: Chat area
        VBox chatPanel = createChatPanel();
        lobby.setRight(chatPanel);

        return lobby;
    }

    /**
     * Creates the login panel with username and password fields.
     *
     * @return the login panel VBox
     */
    private VBox createLoginPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(300);
        panel.setStyle("-fx-background-color: rgb(20, 23, 18); "
            + "-fx-border-color: rgb(40, 45, 35); -fx-border-width: 1;");

        Label loginTitle = new Label("Login");
        loginTitle.setFont(Font.font("Consolas", 16));
        loginTitle.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setStyle("-fx-background-color: rgb(30, 33, 28); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(60, 65, 50); "
            + "-fx-font-family: Consolas; -fx-font-size: 13px;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setStyle("-fx-background-color: rgb(30, 33, 28); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(60, 65, 50); "
            + "-fx-font-family: Consolas; -fx-font-size: 13px;");

        HBox buttonBox = new HBox(8);
        Button loginButton = new Button("Login");
        loginButton.setStyle("-fx-background-color: rgb(50, 60, 40); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(100, 110, 70); "
            + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;");
        Button registerButton = new Button("Register");
        registerButton.setStyle("-fx-background-color: rgb(40, 50, 60); "
            + "-fx-text-fill: rgb(160, 200, 210); -fx-border-color: rgb(70, 100, 110); "
            + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;");

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: rgb(200, 80, 80); -fx-font-family: Consolas; -fx-font-size: 11px;");
        errorLabel.setWrapText(true);

        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Enter username and password");
                return;
            }
            errorLabel.setText("Connecting...");
            service.authenticate(username, password)
                .thenAccept(token -> Platform.runLater(() -> {
                    errorLabel.setText("");
                    onAuthenticated();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> errorLabel.setText(
                        ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    return null;
                });
        });

        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Enter username and password");
                return;
            }
            errorLabel.setText("Registering...");
            service.register(username, password)
                .thenAccept(token -> Platform.runLater(() -> {
                    errorLabel.setText("");
                    onAuthenticated();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> errorLabel.setText(
                        ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    return null;
                });
        });

        buttonBox.getChildren().addAll(loginButton, registerButton);
        panel.getChildren().addAll(loginTitle, usernameField, passwordField, buttonBox, errorLabel);
        return panel;
    }

    /**
     * Creates the player info panel shown after authentication.
     *
     * @return the player info panel VBox
     */
    private VBox createPlayerInfoPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(300);
        panel.setStyle("-fx-background-color: rgb(20, 23, 18); "
            + "-fx-border-color: rgb(40, 45, 35); -fx-border-width: 1;");

        MultiplayerService.PlayerInfo player = service.getCurrentPlayer();

        Label infoTitle = new Label("Player Info");
        infoTitle.setFont(Font.font("Consolas", 16));
        infoTitle.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        Label nameLabel = new Label(player != null ? player.username() : "Unknown");
        nameLabel.setFont(Font.font("Consolas", 14));
        nameLabel.setStyle("-fx-text-fill: rgb(210, 200, 160);");

        Label eloLabel = new Label(player != null ? "ELO: " + player.eloRating() : "ELO: ---");
        eloLabel.setFont(Font.font("Consolas", 13));
        eloLabel.setStyle("-fx-text-fill: rgb(140, 150, 90);");

        Label gamesLabel = new Label(player != null
            ? "Games: " + player.gamesPlayed() + " | Wins: " + player.gamesWon()
            : "Games: --- | Wins: ---");
        gamesLabel.setFont(Font.font("Consolas", 11));
        gamesLabel.setStyle("-fx-text-fill: rgb(120, 120, 100);");

        Label winRateLabel = new Label("");
        winRateLabel.setFont(Font.font("Consolas", 11));
        winRateLabel.setStyle("-fx-text-fill: rgb(100, 110, 70);");
        if (player != null && player.gamesPlayed() > 0) {
            double winRate = (double) player.gamesWon() / player.gamesPlayed() * 100;
            winRateLabel.setText(String.format("Win Rate: %.1f%%", winRate));
        }

        // Players in lobby (fetched from server or placeholder)
        Label playersTitle = new Label("Players Online");
        playersTitle.setFont(Font.font("Consolas", 13));
        playersTitle.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        playerEntries = new VBox(4);

        // Logout button
        Button logoutButton = new Button("Logout");
        logoutButton.setStyle("-fx-background-color: rgb(60, 40, 40); "
            + "-fx-text-fill: rgb(210, 160, 160); -fx-border-color: rgb(110, 70, 70); "
            + "-fx-padding: 4 12 4 12; -fx-cursor: hand; -fx-font-family: Consolas;");
        logoutButton.setOnAction(e -> {
            service.disconnect();
            refreshPlayerPanel();
        });

        panel.getChildren().addAll(infoTitle, nameLabel, eloLabel, gamesLabel,
            winRateLabel, playersTitle, playerEntries, logoutButton);
        return panel;
    }

    /**
     * Creates the search/matchmaking panel.
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
            if (!service.isAuthenticated()) {
                showError("Please login first");
                return;
            }
            if (!searching) {
                startSearch();
            } else {
                cancelSearch();
            }
        });

        // Search status label
        searchStatus = new Label("");
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
     * Creates the chat panel connected to the real WebSocket chat endpoint.
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

        chatMessages = new VBox(4);
        chatMessages.setPrefHeight(400);
        chatMessages.setStyle("-fx-background-color: rgb(18, 20, 16);");

        ScrollPane chatScroll = new ScrollPane(chatMessages);
        chatScroll.setFitToWidth(true);
        chatScroll.setPrefHeight(500);
        chatScroll.setStyle("-fx-background-color: rgb(18, 20, 16); "
            + "-fx-border-color: rgb(40, 45, 35);");

        chatInput = new TextField();
        chatInput.setPromptText("Type a message...");
        chatInput.setStyle("-fx-background-color: rgb(30, 33, 28); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(60, 65, 50); "
            + "-fx-font-family: Consolas; -fx-font-size: 12px;");
        chatInput.setOnAction(e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                service.sendChatMessage(text);
                chatInput.clear();
            }
        });

        panel.getChildren().addAll(chatTitle, chatScroll, chatInput);
        return panel;
    }

    /**
     * Creates the leaderboard content panel.
     *
     * @return the leaderboard content pane
     */
    private VBox createLeaderboardContent() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: rgb(20, 23, 18);");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("LEADERBOARD");
        title.setFont(Font.font("Consolas", 18));
        title.setStyle("-fx-text-fill: rgb(210, 200, 160);");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: rgb(40, 50, 35); "
            + "-fx-text-fill: rgb(180, 175, 145); -fx-border-color: rgb(80, 90, 60); "
            + "-fx-padding: 4 12 4 12; -fx-cursor: hand; -fx-font-family: Consolas;");
        refreshBtn.setOnAction(e -> refreshLeaderboard());

        header.getChildren().addAll(title, refreshBtn);

        // Column headers
        HBox colHeader = new HBox(0);
        colHeader.setPadding(new Insets(6));
        colHeader.setStyle("-fx-background-color: rgb(30, 33, 28);");
        Label rankCol = new Label("Rank");
        rankCol.setPrefWidth(60);
        rankCol.setFont(Font.font("Consolas", 12));
        rankCol.setStyle("-fx-text-fill: rgb(140, 150, 90);");
        Label nameCol = new Label("Player");
        nameCol.setPrefWidth(200);
        nameCol.setFont(Font.font("Consolas", 12));
        nameCol.setStyle("-fx-text-fill: rgb(140, 150, 90);");
        Label eloCol = new Label("ELO");
        eloCol.setPrefWidth(80);
        eloCol.setFont(Font.font("Consolas", 12));
        eloCol.setStyle("-fx-text-fill: rgb(140, 150, 90);");
        Label winCol = new Label("W/L");
        winCol.setPrefWidth(100);
        winCol.setFont(Font.font("Consolas", 12));
        winCol.setStyle("-fx-text-fill: rgb(140, 150, 90);");
        colHeader.getChildren().addAll(rankCol, nameCol, eloCol, winCol);

        leaderboardEntries = new VBox(2);

        ScrollPane scroll = new ScrollPane(leaderboardEntries);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: rgb(18, 20, 16); "
            + "-fx-border-color: rgb(40, 45, 35);");

        panel.getChildren().addAll(header, colHeader, scroll);
        return panel;
    }

    // --- Authentication Handling ---

    /**
     * Called when authentication succeeds.
     * Updates the UI to show player info and connects WebSocket endpoints.
     */
    private void onAuthenticated() {
        updateServerStatus(true);
        refreshPlayerPanel();
        service.connectLobbyWebSocket();
        service.connectChatWebSocket();
        refreshLeaderboard();
        LOG.info("Authenticated and connected to WebSocket endpoints");
    }

    /**
     * Refreshes the left panel to show login or player info based on auth state.
     */
    private void refreshPlayerPanel() {
        BorderPane lobby = findLobbyContent();
        if (lobby == null) return;

        if (service.isAuthenticated()) {
            lobby.setLeft(createPlayerInfoPanel());
        } else {
            lobby.setLeft(createLoginPanel());
            updateServerStatus(false);
        }
    }

    // --- Matchmaking ---

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
            progress.setProgress(-1);
        }

        // Start search animation timer
        searchAnimation = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            searchElapsedSeconds++;
            if (searchStatus != null) {
                int minutes = searchElapsedSeconds / 60;
                int seconds = searchElapsedSeconds % 60;
                searchStatus.setText(String.format("Searching... %d:%02d", minutes, seconds));
            }
        }));
        searchAnimation.setCycleCount(Animation.INDEFINITE);
        searchAnimation.play();

        // Send matchmaking request
        service.findMatch()
            .thenAccept(result -> Platform.runLater(() -> {
                String status = (String) result.getOrDefault("status", "");
                if ("match_found".equals(status)) {
                    String sessionUuid = (String) result.get("sessionUuid");
                    handleMatchFound(sessionUuid, "Opponent");
                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    cancelSearch();
                    showError("Matchmaking failed: " +
                        (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                });
                return null;
            });

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

        if (searchStatus != null) {
            searchStatus.setText("");
        }

        if (service.isAuthenticated()) {
            service.cancelMatchmaking()
                .exceptionally(ex -> {
                    LOG.debug("Cancel matchmaking request failed (may not be in queue)");
                    return null;
                });
        }

        if (callback != null) {
            callback.onCancelSearch();
        }
        LOG.info("Matchmaking search cancelled");
    }

    /**
     * Handles a match found event from the server.
     *
     * @param sessionUuid  the game session UUID
     * @param opponentName the opponent's display name
     */
    private void handleMatchFound(String sessionUuid, String opponentName) {
        if (searchAnimation != null) {
            searchAnimation.stop();
        }

        searching = false;

        if (searchStatus != null) {
            searchStatus.setText("Match found vs " + opponentName + "! Starting game...");
            searchStatus.setStyle("-fx-text-fill: rgb(80, 200, 80);");
        }

        // Connect game WebSocket
        service.connectGameWebSocket();
        service.joinChatRoom(sessionUuid);

        // Send ready signal
        service.sendReady();

        if (callback != null) {
            callback.onMatchFound(sessionUuid);
        }
        LOG.info("Match found! Session: {}, Opponent: {}", sessionUuid, opponentName);
    }

    // --- Chat ---

    /**
     * Add a chat message to the message list.
     *
     * @param sender  the sender's name
     * @param text    the message text
     */
    private void addChatMessage(String sender, String text) {
        if (chatMessages == null) return;
        Label msg = new Label(sender + ": " + text);
        msg.setFont(Font.font("Consolas", 11));
        msg.setStyle("-fx-text-fill: rgb(150, 145, 125);");
        msg.setWrapText(true);
        chatMessages.getChildren().add(msg);
    }

    // --- Leaderboard ---

    /**
     * Refreshes the leaderboard from the server.
     */
    private void refreshLeaderboard() {
        if (leaderboardEntries == null) return;
        leaderboardEntries.getChildren().clear();

        service.getLeaderboard()
            .thenAccept(players -> Platform.runLater(() -> {
                leaderboardEntries.getChildren().clear();
                int rank = 1;
                for (MultiplayerService.PlayerInfo player : players) {
                    leaderboardEntries.getChildren().add(createLeaderboardRow(rank, player));
                    rank++;
                }
                updateServerStatus(true);
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    Label errorLabel = new Label("Could not load leaderboard");
                    errorLabel.setFont(Font.font("Consolas", 12));
                    errorLabel.setStyle("-fx-text-fill: rgb(200, 80, 80);");
                    leaderboardEntries.getChildren().add(errorLabel);
                    updateServerStatus(false);
                });
                return null;
            });
    }

    /**
     * Creates a leaderboard row for a player.
     */
    private HBox createLeaderboardRow(int rank, MultiplayerService.PlayerInfo player) {
        HBox row = new HBox(0);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.setStyle("-fx-background-color: rgb(25, 28, 22);");

        Label rankLabel = new Label("#" + rank);
        rankLabel.setPrefWidth(60);
        rankLabel.setFont(Font.font("Consolas", 12));
        rankLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        Label nameLabel = new Label(player.username());
        nameLabel.setPrefWidth(200);
        nameLabel.setFont(Font.font("Consolas", 12));
        nameLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");

        Label eloLabel = new Label(String.valueOf(player.eloRating()));
        eloLabel.setPrefWidth(80);
        eloLabel.setFont(Font.font("Consolas", 12));
        eloLabel.setStyle("-fx-text-fill: rgb(140, 150, 90);");

        String winLoss = player.gamesWon() + "/" + (player.gamesPlayed() - player.gamesWon());
        Label wlLabel = new Label(winLoss);
        wlLabel.setPrefWidth(100);
        wlLabel.setFont(Font.font("Consolas", 12));
        wlLabel.setStyle("-fx-text-fill: rgb(100, 110, 70);");

        row.getChildren().addAll(rankLabel, nameLabel, eloLabel, wlLabel);
        return row;
    }

    // --- UI Helpers ---

    /**
     * Updates the server status indicator.
     *
     * @param online whether the server is reachable
     */
    private void updateServerStatus(boolean online) {
        if (statusDot != null) {
            statusDot.setFill(online ? Color.rgb(80, 200, 80) : Color.rgb(200, 80, 80));
        }
        if (statusLabel != null) {
            statusLabel.setText(online ? "Server Online" : "Server Offline");
        }
    }

    /**
     * Shows an error message to the user.
     *
     * @param message the error message
     */
    private void showError(String message) {
        if (searchStatus != null) {
            searchStatus.setText(message);
            searchStatus.setStyle("-fx-text-fill: rgb(200, 80, 80);");
        }
        LOG.warn("UI error: {}", message);
    }

    /**
     * Finds the lobby BorderPane content from the scene graph.
     *
     * @return the lobby BorderPane, or null
     */
    private BorderPane findLobbyContent() {
        if (root.getChildren().isEmpty()) return null;
        if (root.getChildren().get(0) instanceof BorderPane bp) {
            if (bp.getCenter() instanceof TabPane tabPane) {
                for (Tab tab : tabPane.getTabs()) {
                    if ("Lobby".equals(tab.getText()) && tab.getContent() instanceof BorderPane lobby) {
                        return lobby;
                    }
                }
            }
        }
        return null;
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

    // --- Public API ---

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

    /**
     * Returns the multiplayer service instance.
     *
     * @return the multiplayer service
     */
    public MultiplayerService getService() {
        return service;
    }

    /**
     * Disposes of resources when the scene is no longer displayed.
     */
    public void dispose() {
        cancelSearch();
        service.disconnect();
    }
}
