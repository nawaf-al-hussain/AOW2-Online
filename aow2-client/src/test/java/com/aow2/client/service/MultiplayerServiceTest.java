package com.aow2.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MultiplayerService: constructor, auth, connectivity.
 * Network-dependent operations are tested for graceful failure when server is unavailable.
 */
class MultiplayerServiceTest {

    private MultiplayerService service;

    @BeforeEach
    void setUp() {
        service = new MultiplayerService("http://localhost:8080");
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("default constructor uses localhost:8080")
        void defaultConstructorUsesLocalhost() {
            MultiplayerService defaultService = new MultiplayerService();
            assertNotNull(defaultService);
            defaultService.shutdown();
        }

        @Test
        @DisplayName("custom server URL constructor does not throw")
        void customServerUrlConstructor() {
            assertDoesNotThrow(() -> new MultiplayerService("http://example.com:9090"));
        }
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("not connected initially")
        void notConnectedInitially() {
            assertFalse(service.isConnected());
        }

        @Test
        @DisplayName("not authenticated initially")
        void notAuthenticatedInitially() {
            assertFalse(service.isAuthenticated());
        }

        @Test
        @DisplayName("JWT token is null initially")
        void jwtTokenNullInitially() {
            assertNull(service.getJwtToken());
        }

        @Test
        @DisplayName("current player is null initially")
        void currentPlayerNullInitially() {
            assertNull(service.getCurrentPlayer());
        }
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("authenticate completes (fails gracefully without server)")
        void authenticateCompletes() {
            CompletableFuture<String> future = service.authenticate("user", "pass");
            assertNotNull(future);
            assertThrows(ExecutionException.class, () -> future.get());
        }

        @Test
        @DisplayName("register completes (fails gracefully without server)")
        void registerCompletes() {
            CompletableFuture<String> future = service.register("newuser", "pass123");
            assertNotNull(future);
            assertThrows(ExecutionException.class, () -> future.get());
        }
    }

    @Nested
    @DisplayName("Matchmaking")
    class Matchmaking {

        @Test
        @DisplayName("findMatch fails gracefully without auth")
        void findMatchWithoutAuth() {
            CompletableFuture<?> future = service.findMatch();
            assertNotNull(future);
            assertThrows(ExecutionException.class, () -> future.get());
        }

        @Test
        @DisplayName("cancelMatchmaking fails gracefully without auth")
        void cancelMatchmakingWithoutAuth() {
            CompletableFuture<?> future = service.cancelMatchmaking();
            assertNotNull(future);
            assertThrows(ExecutionException.class, () -> future.get());
        }
    }

    @Nested
    @DisplayName("Leaderboard")
    class Leaderboard {

        @Test
        @DisplayName("getLeaderboard completes without throwing (fails gracefully)")
        void getLeaderboardCompletes() {
            CompletableFuture<?> future = service.getLeaderboard();
            assertNotNull(future);
            assertThrows(ExecutionException.class, () -> future.get());
        }
    }

    @Nested
    @DisplayName("Player Info")
    class PlayerInfo {

        @Test
        @DisplayName("getPlayerInfo fails gracefully without auth")
        void getPlayerInfoWithoutAuth() {
            CompletableFuture<?> future = service.getPlayerInfo();
            assertNotNull(future);
            assertThrows(ExecutionException.class, () -> future.get());
        }
    }

    @Nested
    @DisplayName("WebSocket")
    class WebSocket {

        @Test
        @DisplayName("connectLobbyWebSocket fails gracefully without auth")
        void connectLobbyWithoutAuth() {
            assertDoesNotThrow(() -> service.connectLobbyWebSocket());
        }

        @Test
        @DisplayName("connectGameWebSocket fails gracefully without auth")
        void connectGameWithoutAuth() {
            assertDoesNotThrow(() -> service.connectGameWebSocket());
        }

        @Test
        @DisplayName("connectChatWebSocket fails gracefully without auth")
        void connectChatWithoutAuth() {
            assertDoesNotThrow(() -> service.connectChatWebSocket());
        }

        @Test
        @DisplayName("disconnect does not throw")
        void disconnectDoesNotThrow() {
            assertDoesNotThrow(() -> service.disconnect());
        }
    }

    @Nested
    @DisplayName("Shutdown")
    class Shutdown {

        @Test
        @DisplayName("shutdown does not throw")
        void shutdownDoesNotThrow() {
            assertDoesNotThrow(() -> service.shutdown());
        }

        @Test
        @DisplayName("double shutdown does not throw")
        void doubleShutdownDoesNotThrow() {
            service.shutdown();
            assertDoesNotThrow(() -> service.shutdown());
        }
    }

    @Nested
    @DisplayName("PlayerInfo Record")
    class PlayerInfoRecord {

        @Test
        @DisplayName("PlayerInfo record holds values correctly")
        void playerInfoRecord() {
            MultiplayerService.PlayerInfo info =
                new MultiplayerService.PlayerInfo(1, "TestPlayer", 1200, 50, 25);
            assertEquals(1, info.id());
            assertEquals("TestPlayer", info.username());
            assertEquals(1200, info.eloRating());
            assertEquals(50, info.gamesPlayed());
            assertEquals(25, info.gamesWon());
        }

        @Test
        @DisplayName("PlayerInfo equals works")
        void playerInfoEquals() {
            MultiplayerService.PlayerInfo info1 =
                new MultiplayerService.PlayerInfo(1, "Test", 1000, 10, 5);
            MultiplayerService.PlayerInfo info2 =
                new MultiplayerService.PlayerInfo(1, "Test", 1000, 10, 5);
            assertEquals(info1, info2);
        }
    }
}
