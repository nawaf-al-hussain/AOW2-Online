package com.aow2.client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * REST + WebSocket client for the AOW2 Spring Boot multiplayer server.
 * Handles authentication, matchmaking, leaderboard queries, and real-time
 * game communication via WebSocket endpoints.
 * <p>
 * REST endpoints:
 * - POST /api/auth/login — authenticate and receive JWT
 * - POST /api/auth/register — create account and receive JWT
 * - GET  /api/auth/me — get current player info
 * - POST /api/matchmaking/queue — enter matchmaking queue
 * - DELETE /api/matchmaking/queue — leave matchmaking queue
 * - GET  /api/leaderboard — get top players
 * <p>
 * WebSocket endpoints:
 * - /ws/lobby — matchmaking events (match_found, game_start)
 * - /ws/game — gameplay command relay and sync
 * - /ws/chat — real-time lobby chat
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Multiplayer networking
 * REF: protocol_specification.md - Lobby and matchmaking messages
 */
public final class MultiplayerService {

    private static final Logger LOG = LoggerFactory.getLogger(MultiplayerService.class);

    /** Default server URL. */
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    /** JSON object mapper for request/response serialization. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Base server URL for REST endpoints. */
    private final String serverUrl;

    /** HTTP client for REST calls. */
    private final HttpClient httpClient;

    /** Executor for async operations. */
    private final ExecutorService executor;

    /** Current JWT authentication token. */
    private volatile String jwtToken;

    /** Current authenticated player info. */
    private volatile PlayerInfo currentPlayer;

    /** Lobby WebSocket client session. */
    private volatile LobbyWebSocketClient lobbyWebSocket;

    /** Game WebSocket client session. */
    private volatile GameWebSocketClient gameWebSocket;

    /** Chat WebSocket client session. */
    private volatile ChatWebSocketClient chatWebSocket;

    /** Callback interface for UI updates. */
    private volatile MultiplayerCallback callback;

    /** Whether the service is connected. */
    private volatile boolean connected;

    /**
     * Player information record.
     *
     * @param id          player ID
     * @param username    display name
     * @param eloRating   ELO rating
     * @param gamesPlayed total games played
     * @param gamesWon    total games won
     */
    public record PlayerInfo(long id, String username, int eloRating, int gamesPlayed, int gamesWon) {}

    /**
     * Callback interface for multiplayer UI updates.
     * All callbacks are invoked on the JavaFX application thread
     * by the calling scene code.
     */
    public interface MultiplayerCallback {
        /**
         * Called when a match is found via matchmaking.
         *
         * @param sessionUuid the game session UUID
         * @param opponentName the opponent's username
         */
        void onMatchFound(String sessionUuid, String opponentName);

        /**
         * Called when another player connects to the game.
         *
         * @param playerId the connected player's ID
         */
        void onPlayerConnected(long playerId);

        /**
         * Called when a gameplay command is received from the opponent.
         *
         * @param fromPlayerId the sender's player ID
         * @param command      the command data
         */
        void onCommandReceived(long fromPlayerId, Map<String, Object> command);

        /**
         * Called when a desync is detected.
         *
         * @param tick the tick where desync was detected
         */
        void onDesyncDetected(long tick);

        /**
         * Called when a chat message is received.
         *
         * @param senderName the sender's username
         * @param message    the chat message text
         */
        void onChatMessage(String senderName, String message);

        /**
         * Called when an error occurs.
         *
         * @param error the error description
         */
        void onError(String error);
    }

    /**
     * Constructs a MultiplayerService with the default server URL.
     */
    public MultiplayerService() {
        this(DEFAULT_SERVER_URL);
    }

    /**
     * Constructs a MultiplayerService with a configurable server URL.
     *
     * @param serverUrl the base URL of the game server
     */
    public MultiplayerService(String serverUrl) {
        this.serverUrl = serverUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.connected = false;
        LOG.info("MultiplayerService created, server URL: {}", serverUrl);
    }

    // --- Authentication ---

    /**
     * Authenticates with the server using username and password.
     * On success, stores the JWT token and player info.
     *
     * @param username the player's username
     * @param password the player's password
     * @return a CompletableFuture resolving to the JWT token
     */
    public CompletableFuture<String> authenticate(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> body = Map.of("username", username, "password", password);
                String json = MAPPER.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> result = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    String token = (String) result.get("token");
                    this.jwtToken = token;
                    this.currentPlayer = parsePlayerInfo(result);
                    this.connected = true;
                    LOG.info("Authenticated as {} (ELO: {})", username, currentPlayer.eloRating());
                    return token;
                } else {
                    Map<String, Object> error = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    String errorMsg = (String) error.getOrDefault("error", "Authentication failed");
                    LOG.warn("Authentication failed: {}", errorMsg);
                    throw new RuntimeException(errorMsg);
                }
            } catch (Exception e) {
                LOG.error("Authentication error", e);
                throw new RuntimeException("Connection failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Registers a new account on the server.
     * On success, stores the JWT token and player info.
     *
     * @param username the desired username
     * @param password the desired password
     * @return a CompletableFuture resolving to the JWT token
     */
    public CompletableFuture<String> register(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> body = Map.of("username", username, "password", password);
                String json = MAPPER.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 201) {
                    Map<String, Object> result = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    String token = (String) result.get("token");
                    this.jwtToken = token;
                    this.currentPlayer = parsePlayerInfo(result);
                    this.connected = true;
                    LOG.info("Registered as {} (ELO: {})", username, currentPlayer.eloRating());
                    return token;
                } else {
                    Map<String, Object> error = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    String errorMsg = (String) error.getOrDefault("error", "Registration failed");
                    LOG.warn("Registration failed: {}", errorMsg);
                    throw new RuntimeException(errorMsg);
                }
            } catch (Exception e) {
                LOG.error("Registration error", e);
                throw new RuntimeException("Connection failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // --- Matchmaking ---

    /**
     * Enters the matchmaking queue to find an opponent.
     * Requires prior authentication.
     *
     * @return a CompletableFuture resolving to the match status map
     */
    public CompletableFuture<Map<String, Object>> findMatch() {
        return CompletableFuture.supplyAsync(() -> {
            ensureAuthenticated();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/matchmaking/queue"))
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> result = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    LOG.info("Matchmaking status: {}", result.get("status"));
                    return result;
                } else {
                    throw new RuntimeException("Matchmaking request failed: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Find match error", e);
                throw new RuntimeException("Matchmaking failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Leaves the matchmaking queue.
     * Requires prior authentication.
     *
     * @return a CompletableFuture resolving to the cancellation status map
     */
    public CompletableFuture<Map<String, Object>> cancelMatchmaking() {
        return CompletableFuture.supplyAsync(() -> {
            ensureAuthenticated();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/matchmaking/queue"))
                    .header("Authorization", "Bearer " + jwtToken)
                    .DELETE()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> result = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    LOG.info("Left matchmaking queue");
                    return result;
                } else {
                    throw new RuntimeException("Cancel matchmaking failed: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Cancel matchmaking error", e);
                throw new RuntimeException("Cancel failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // --- Leaderboard ---

    /**
     * Fetches the leaderboard from the server.
     *
     * @return a CompletableFuture resolving to the list of player entries
     */
    public CompletableFuture<List<PlayerInfo>> getLeaderboard() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/leaderboard?limit=50"))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> entries = MAPPER.readValue(response.body(),
                        new TypeReference<List<Map<String, Object>>>() {});
                    List<PlayerInfo> players = new java.util.ArrayList<>();
                    for (Map<String, Object> entry : entries) {
                        players.add(parsePlayerInfo(entry));
                    }
                    LOG.info("Fetched leaderboard: {} entries", players.size());
                    return players;
                } else {
                    throw new RuntimeException("Leaderboard request failed: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Leaderboard fetch error", e);
                throw new RuntimeException("Leaderboard failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // --- Player Info ---

    /**
     * Gets the current authenticated player's information from the server.
     *
     * @return a CompletableFuture resolving to the player info
     */
    public CompletableFuture<PlayerInfo> getPlayerInfo() {
        return CompletableFuture.supplyAsync(() -> {
            ensureAuthenticated();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/auth/me"))
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> result = MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                    this.currentPlayer = parsePlayerInfo(result);
                    return currentPlayer;
                } else {
                    throw new RuntimeException("Player info request failed: " + response.statusCode());
                }
            } catch (Exception e) {
                LOG.error("Player info fetch error", e);
                throw new RuntimeException("Player info failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // --- WebSocket Connections ---

    /**
     * Connects to the lobby WebSocket for matchmaking events.
     * Sends an auth message with the current JWT token after connection.
     */
    public void connectLobbyWebSocket() {
        ensureAuthenticated();
        try {
            String wsUrl = serverUrl.replace("http", "ws") + "/ws/lobby";
            lobbyWebSocket = new LobbyWebSocketClient(URI.create(wsUrl), this);
            lobbyWebSocket.connect();
            LOG.info("Connecting to lobby WebSocket: {}", wsUrl);
        } catch (Exception e) {
            LOG.error("Failed to connect lobby WebSocket", e);
            notifyError("Failed to connect to lobby: " + e.getMessage());
        }
    }

    /**
     * Connects to the game WebSocket for gameplay command relay.
     * Sends an auth message with the current JWT token after connection.
     */
    public void connectGameWebSocket() {
        ensureAuthenticated();
        try {
            String wsUrl = serverUrl.replace("http", "ws") + "/ws/game";
            gameWebSocket = new GameWebSocketClient(URI.create(wsUrl), this);
            gameWebSocket.connect();
            LOG.info("Connecting to game WebSocket: {}", wsUrl);
        } catch (Exception e) {
            LOG.error("Failed to connect game WebSocket", e);
            notifyError("Failed to connect to game: " + e.getMessage());
        }
    }

    /**
     * Connects to the chat WebSocket for real-time messaging.
     * Sends an auth message with the current JWT token after connection.
     */
    public void connectChatWebSocket() {
        ensureAuthenticated();
        try {
            String wsUrl = serverUrl.replace("http", "ws") + "/ws/chat";
            chatWebSocket = new ChatWebSocketClient(URI.create(wsUrl), this);
            chatWebSocket.connect();
            LOG.info("Connecting to chat WebSocket: {}", wsUrl);
        } catch (Exception e) {
            LOG.error("Failed to connect chat WebSocket", e);
            notifyError("Failed to connect to chat: " + e.getMessage());
        }
    }

    /**
     * Sends a chat message via the chat WebSocket.
     *
     * @param message the chat message text
     */
    public void sendChatMessage(String message) {
        if (chatWebSocket != null && chatWebSocket.isOpen()) {
            try {
                Map<String, Object> msg = Map.of(
                    "type", "chat",
                    "message", message
                );
                chatWebSocket.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.error("Failed to send chat message", e);
            }
        }
    }

    /**
     * Sends a game command via the game WebSocket.
     *
     * @param command the command data map
     */
    public void sendGameCommand(Map<String, Object> command) {
        if (gameWebSocket != null && gameWebSocket.isOpen()) {
            try {
                Map<String, Object> msg = Map.of(
                    "type", "command",
                    "command", command
                );
                gameWebSocket.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.error("Failed to send game command", e);
            }
        }
    }

    /**
     * Sends a sync hash report via the game WebSocket.
     *
     * @param sessionUuid the game session UUID
     * @param tick        the current tick
     * @param hash        the state hash
     */
    public void sendSyncHash(String sessionUuid, long tick, long hash) {
        if (gameWebSocket != null && gameWebSocket.isOpen()) {
            try {
                Map<String, Object> msg = Map.of(
                    "type", "sync_hash",
                    "sessionUuid", sessionUuid,
                    "tick", tick,
                    "hash", hash
                );
                gameWebSocket.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.error("Failed to send sync hash", e);
            }
        }
    }

    /**
     * Sends a ready signal via the lobby WebSocket.
     */
    public void sendReady() {
        if (lobbyWebSocket != null && lobbyWebSocket.isOpen()) {
            try {
                Map<String, Object> msg = Map.of("type", "ready");
                lobbyWebSocket.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.error("Failed to send ready signal", e);
            }
        }
    }

    /**
     * Joins a match chat room via the chat WebSocket.
     *
     * @param matchId the match/session UUID
     */
    public void joinChatRoom(String matchId) {
        if (chatWebSocket != null && chatWebSocket.isOpen()) {
            try {
                Map<String, Object> msg = Map.of(
                    "type", "join",
                    "matchId", matchId
                );
                chatWebSocket.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.error("Failed to join chat room", e);
            }
        }
    }

    // --- Lifecycle ---

    /**
     * Disconnects all WebSocket connections and cleans up resources.
     */
    public void disconnect() {
        if (lobbyWebSocket != null) {
            lobbyWebSocket.close();
            lobbyWebSocket = null;
        }
        if (gameWebSocket != null) {
            gameWebSocket.close();
            gameWebSocket = null;
        }
        if (chatWebSocket != null) {
            chatWebSocket.close();
            chatWebSocket = null;
        }
        connected = false;
        LOG.info("MultiplayerService disconnected");
    }

    /**
     * Shuts down the service, disconnecting and closing the executor.
     * Waits up to 5 seconds for pending tasks to complete before forcing shutdown.
     */
    public void shutdown() {
        disconnect();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOG.warn("ExecutorService forced shutdown after timeout");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("MultiplayerService shutdown");
    }

    // --- Getters ---

    /**
     * Returns whether the service is currently connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns whether the user is currently authenticated.
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() {
        return jwtToken != null && currentPlayer != null;
    }

    /**
     * Returns the current player info.
     *
     * @return the current player info, or null if not authenticated
     */
    public PlayerInfo getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * Returns the current JWT token.
     *
     * @return the JWT token, or null if not authenticated
     */
    public String getJwtToken() {
        return jwtToken;
    }

    /**
     * Sets the callback for multiplayer events.
     *
     * @param callback the callback implementation
     */
    public void setCallback(MultiplayerCallback callback) {
        this.callback = callback;
    }

    // --- Internal ---

    /**
     * Ensures the user is authenticated before performing protected operations.
     *
     * @throws IllegalStateException if not authenticated
     */
    private void ensureAuthenticated() {
        if (jwtToken == null) {
            throw new IllegalStateException("Not authenticated. Call authenticate() or register() first.");
        }
    }

    /**
     * Parses a map into a PlayerInfo record.
     */
    private PlayerInfo parsePlayerInfo(Map<String, Object> data) {
        return new PlayerInfo(
            ((Number) data.getOrDefault("id", 0L)).longValue(),
            (String) data.getOrDefault("username", "Unknown"),
            ((Number) data.getOrDefault("eloRating", 1000)).intValue(),
            ((Number) data.getOrDefault("gamesPlayed", 0)).intValue(),
            ((Number) data.getOrDefault("gamesWon", 0)).intValue()
        );
    }

    /**
     * Notifies the callback of an error.
     */
    private void notifyError(String error) {
        MultiplayerCallback cb = callback;
        if (cb != null) {
            cb.onError(error);
        }
    }

    /**
     * Handles an incoming lobby WebSocket message.
     * Dispatches match_found and game_start events to the callback.
     */
    void handleLobbyMessage(String payload) {
        try {
            Map<String, Object> msg = MAPPER.readValue(payload,
                new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.getOrDefault("type", "");

            switch (type) {
                case "auth_ok" -> LOG.info("Lobby WebSocket authenticated");
                case "queued" -> LOG.info("Added to matchmaking queue");
                case "match_found" -> {
                    String sessionUuid = (String) msg.get("sessionUuid");
                    LOG.info("Match found! Session: {}", sessionUuid);
                    MultiplayerCallback cb = callback;
                    if (cb != null) {
                        long opponentId = ((Number) msg.getOrDefault("player2Id", 0L)).longValue();
                        cb.onMatchFound(sessionUuid, "Player_" + opponentId);
                    }
                }
                case "game_start" -> {
                    LOG.info("Game starting! Session: {}", msg.get("sessionUuid"));
                    MultiplayerCallback cb = callback;
                    if (cb != null) {
                        long p2 = ((Number) msg.getOrDefault("player2Id", 0L)).longValue();
                        cb.onPlayerConnected(p2);
                    }
                }
                case "error" -> {
                    String error = (String) msg.getOrDefault("message", "Unknown lobby error");
                    LOG.warn("Lobby error: {}", error);
                    notifyError(error);
                }
                default -> LOG.debug("Unhandled lobby message type: {}", type);
            }
        } catch (Exception e) {
            LOG.error("Failed to parse lobby message", e);
        }
    }

    /**
     * Handles an incoming game WebSocket message.
     * Dispatches command and desync events to the callback.
     */
    void handleGameMessage(String payload) {
        try {
            Map<String, Object> msg = MAPPER.readValue(payload,
                new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.getOrDefault("type", "");

            switch (type) {
                case "auth_ok" -> LOG.info("Game WebSocket authenticated");
                case "command" -> {
                    long fromPlayerId = ((Number) msg.getOrDefault("fromPlayerId", 0L)).longValue();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> command = (Map<String, Object>) msg.get("command");
                    MultiplayerCallback cb = callback;
                    if (cb != null) {
                        cb.onCommandReceived(fromPlayerId, command != null ? command : Map.of());
                    }
                }
                case "desync" -> {
                    long tick = ((Number) msg.getOrDefault("tick", 0L)).longValue();
                    LOG.error("Desync detected at tick {}", tick);
                    MultiplayerCallback cb = callback;
                    if (cb != null) {
                        cb.onDesyncDetected(tick);
                    }
                }
                case "opponent_disconnected" -> {
                    LOG.info("Opponent disconnected");
                    notifyError("Opponent disconnected");
                }
                case "game_over" -> {
                    LOG.info("Game over. Winner: {}", msg.get("winnerId"));
                    notifyError("Game over");
                }
                case "error" -> {
                    String error = (String) msg.getOrDefault("message", "Unknown game error");
                    LOG.warn("Game error: {}", error);
                    notifyError(error);
                }
                default -> LOG.debug("Unhandled game message type: {}", type);
            }
        } catch (Exception e) {
            LOG.error("Failed to parse game message", e);
        }
    }

    /**
     * Handles an incoming chat WebSocket message.
     */
    void handleChatMessage(String payload) {
        try {
            Map<String, Object> msg = MAPPER.readValue(payload,
                new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.getOrDefault("type", "");

            switch (type) {
                case "auth_ok" -> LOG.info("Chat WebSocket authenticated");
                case "chat" -> {
                    long playerId = ((Number) msg.getOrDefault("playerId", 0L)).longValue();
                    String message = (String) msg.getOrDefault("message", "");
                    MultiplayerCallback cb = callback;
                    if (cb != null) {
                        String senderName = playerId == (currentPlayer != null ? currentPlayer.id() : -1)
                            ? "You" : "Player_" + playerId;
                        cb.onChatMessage(senderName, message);
                    }
                }
                case "error" -> {
                    String error = (String) msg.getOrDefault("message", "Unknown chat error");
                    LOG.warn("Chat error: {}", error);
                    notifyError(error);
                }
                default -> LOG.debug("Unhandled chat message type: {}", type);
            }
        } catch (Exception e) {
            LOG.error("Failed to parse chat message", e);
        }
    }

    // --- Inner WebSocket Client Classes ---

    /**
     * Base WebSocket client using Jakarta WebSocket API.
     * Provides connect, send, close, and open-state checking.
     */
    static abstract class BaseWebSocketClient extends jakarta.websocket.Endpoint {
        private jakarta.websocket.Session session;
        private final URI uri;
        private final MultiplayerService service;

        BaseWebSocketClient(URI uri, MultiplayerService service) {
            this.uri = uri;
            this.service = service;
        }

        /**
         * Connects to the WebSocket server.
         */
        void connect() {
            try {
                jakarta.websocket.WebSocketContainer container =
                    jakarta.websocket.ContainerProvider.getWebSocketContainer();
                container.connectToServer(this, uri);
            } catch (Exception e) {
                LOG.error("WebSocket connection failed: {}", uri, e);
                service.notifyError("WebSocket connection failed: " + e.getMessage());
            }
        }

        @Override
        public void onOpen(jakarta.websocket.Session session, jakarta.websocket.EndpointConfig config) {
            this.session = session;
            LOG.info("WebSocket connected: {}", uri);
            // Send authentication message
            try {
                String authMsg = MAPPER.writeValueAsString(
                    Map.of("type", "auth", "token", service.jwtToken));
                session.getAsyncRemote().sendText(authMsg);
            } catch (Exception e) {
                LOG.error("Failed to send auth message", e);
            }
        }

        @Override
        public void onClose(jakarta.websocket.Session session, jakarta.websocket.CloseReason closeReason) {
            this.session = null;
            LOG.info("WebSocket closed: {} - {}", uri, closeReason);
        }

        @Override
        public void onError(jakarta.websocket.Session session, Throwable thr) {
            LOG.error("WebSocket error: {}", uri, thr);
            service.notifyError("WebSocket error: " + thr.getMessage());
        }

        /**
         * Sends a text message via the WebSocket.
         *
         * @param message the message string
         */
        void send(String message) {
            jakarta.websocket.Session s = session;
            if (s != null && s.isOpen()) {
                s.getAsyncRemote().sendText(message);
            }
        }

        /**
         * Closes the WebSocket connection.
         */
        void close() {
            jakarta.websocket.Session s = session;
            if (s != null && s.isOpen()) {
                try {
                    s.close();
                } catch (Exception e) {
                    LOG.debug("Error closing WebSocket", e);
                }
            }
            session = null;
        }

        /**
         * Returns whether the WebSocket is currently open.
         */
        boolean isOpen() {
            jakarta.websocket.Session s = session;
            return s != null && s.isOpen();
        }

        MultiplayerService getService() {
            return service;
        }
    }

    /**
     * WebSocket client for the lobby endpoint (/ws/lobby).
     * Receives matchmaking events: queued, match_found, game_start.
     */
    static final class LobbyWebSocketClient extends BaseWebSocketClient {
        LobbyWebSocketClient(URI uri, MultiplayerService service) {
            super(uri, service);
        }

        @Override
        public void onOpen(jakarta.websocket.Session session, jakarta.websocket.EndpointConfig config) {
            super.onOpen(session, config);
            session.addMessageHandler(new jakarta.websocket.MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    getService().handleLobbyMessage(message);
                }
            });
        }
    }

    /**
     * WebSocket client for the game endpoint (/ws/game).
     * Receives gameplay commands, desync notifications, and game-over events.
     */
    static final class GameWebSocketClient extends BaseWebSocketClient {
        GameWebSocketClient(URI uri, MultiplayerService service) {
            super(uri, service);
        }

        @Override
        public void onOpen(jakarta.websocket.Session session, jakarta.websocket.EndpointConfig config) {
            super.onOpen(session, config);
            session.addMessageHandler(new jakarta.websocket.MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    getService().handleGameMessage(message);
                }
            });
        }
    }

    /**
     * WebSocket client for the chat endpoint (/ws/chat).
     * Receives and sends real-time chat messages.
     */
    static final class ChatWebSocketClient extends BaseWebSocketClient {
        ChatWebSocketClient(URI uri, MultiplayerService service) {
            super(uri, service);
        }

        @Override
        public void onOpen(jakarta.websocket.Session session, jakarta.websocket.EndpointConfig config) {
            super.onOpen(session, config);
            session.addMessageHandler(new jakarta.websocket.MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    getService().handleChatMessage(message);
                }
            });
        }
    }
}
