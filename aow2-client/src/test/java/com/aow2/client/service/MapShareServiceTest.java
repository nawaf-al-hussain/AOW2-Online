package com.aow2.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MapShareService: constructor, URL handling, connectivity checks.
 * Network-dependent operations are tested for graceful failure when server is unavailable.
 */
class MapShareServiceTest {

    private MapShareService service;

    @BeforeEach
    void setUp() {
        service = new MapShareService("http://localhost:8080");
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("default constructor uses localhost:8080")
        void defaultConstructorUsesLocalhost() {
            MapShareService defaultService = new MapShareService();
            assertEquals("http://localhost:8080", defaultService.getServerUrl());
            defaultService.shutdown();
        }

        @Test
        @DisplayName("custom server URL is stored correctly")
        void customServerUrl() {
            MapShareService custom = new MapShareService("http://example.com:9090");
            assertEquals("http://example.com:9090", custom.getServerUrl());
            custom.shutdown();
        }

        @Test
        @DisplayName("trailing slash is stripped from server URL")
        void trailingSlashStripped() {
            MapShareService custom = new MapShareService("http://example.com/");
            assertEquals("http://example.com", custom.getServerUrl());
            custom.shutdown();
        }

        @Test
        @DisplayName("URL without trailing slash is preserved")
        void urlWithoutTrailingSlash() {
            MapShareService custom = new MapShareService("http://example.com");
            assertEquals("http://example.com", custom.getServerUrl());
            custom.shutdown();
        }
    }

    @Nested
    @DisplayName("Auth Token")
    class AuthToken {

        @Test
        @DisplayName("setAuthToken does not throw")
        void setAuthTokenDoesNotThrow() {
            assertDoesNotThrow(() -> service.setAuthToken("test-token-123"));
        }

        @Test
        @DisplayName("setAuthToken with null does not throw")
        void setAuthTokenWithNull() {
            assertDoesNotThrow(() -> service.setAuthToken(null));
        }
    }

    @Nested
    @DisplayName("Server Unreachable")
    class ServerUnreachable {

        @Test
        @DisplayName("isServerReachable returns false when no server running")
        void isServerReachableReturnsFalse() {
            // Assuming no server is running on localhost:8080 during tests
            assertFalse(service.isServerReachable());
        }

        @Test
        @DisplayName("uploadMap returns error map when server unreachable")
        void uploadMapReturnsErrorWhenUnreachable() {
            Map<String, Object> result = service.uploadMap("Test Map", "desc", "{}");
            assertTrue(result.containsKey("error"));
        }

        @Test
        @DisplayName("downloadMap returns error map when server unreachable")
        void downloadMapReturnsErrorWhenUnreachable() {
            Map<String, Object> result = service.downloadMap(1);
            assertTrue(result.containsKey("error"));
        }

        @Test
        @DisplayName("listCommunityMaps returns empty list when server unreachable")
        void listCommunityMapsReturnsEmptyWhenUnreachable() {
            List<Map<String, Object>> result = service.listCommunityMaps();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("deleteMap returns false when server unreachable")
        void deleteMapReturnsFalseWhenUnreachable() {
            assertFalse(service.deleteMap(1));
        }

        @Test
        @DisplayName("incrementDownloadCount returns false when server unreachable")
        void incrementDownloadCountReturnsFalse() {
            assertFalse(service.incrementDownloadCount(1));
        }
    }

    @Nested
    @DisplayName("Async Operations")
    class AsyncOperations {

        @Test
        @DisplayName("uploadMapAsync returns a CompletableFuture")
        void uploadMapAsyncReturnsFuture() {
            var future = service.uploadMapAsync("Test", "desc", "{}");
            assertNotNull(future);
            // Future should complete (with error since no server)
            assertDoesNotThrow(() -> future.get());
        }

        @Test
        @DisplayName("downloadMapAsync returns a CompletableFuture")
        void downloadMapAsyncReturnsFuture() {
            var future = service.downloadMapAsync(1);
            assertNotNull(future);
            assertDoesNotThrow(() -> future.get());
        }

        @Test
        @DisplayName("listCommunityMapsAsync returns a CompletableFuture")
        void listCommunityMapsAsyncReturnsFuture() {
            var future = service.listCommunityMapsAsync();
            assertNotNull(future);
            assertDoesNotThrow(() -> future.get());
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
        @DisplayName("operations after shutdown still return gracefully")
        void operationsAfterShutdown() {
            service.shutdown();
            // Network operations should still not throw NPE
            assertDoesNotThrow(() -> service.isServerReachable());
        }
    }
}
