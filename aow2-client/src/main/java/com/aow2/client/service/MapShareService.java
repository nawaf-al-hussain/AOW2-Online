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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST client for uploading and downloading community maps from the Spring Boot server.
 * Uses java.net.http.HttpClient for HTTP communication with the map sharing API.
 * <p>
 * Server endpoints:
 * - POST   /api/maps           — upload a new map (requires auth)
 * - GET    /api/maps           — list all community maps
 * - GET    /api/maps/{id}      — download a specific map
 * - DELETE /api/maps/{id}      — delete a map (owner only)
 * - POST   /api/maps/{id}/download — increment download count
 * <p>
 * REF: protocol_specification.md - Type 21 MAP_DATA, Type 41 MAP_LIST
 * REF: phases.md Phase 9 - Map sharing
 */
public class MapShareService {

    private static final Logger LOG = LoggerFactory.getLogger(MapShareService.class);

    /** Default server URL. */
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    /** JSON object mapper. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The HTTP client for server communication. */
    private final HttpClient httpClient;

    /** The server base URL. */
    private final String serverUrl;

    /** JWT authentication token (nullable — required for upload/delete). */
    private String authToken;

    /**
     * Constructs a MapShareService with the default server URL.
     */
    public MapShareService() {
        this(DEFAULT_SERVER_URL);
    }

    /**
     * Constructs a MapShareService with a configurable server URL.
     *
     * @param serverUrl the base URL of the game server (e.g., "http://localhost:8080")
     */
    public MapShareService(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.authToken = null;
        LOG.info("MapShareService created with server URL: {}", this.serverUrl);
    }

    /**
     * Sets the JWT authentication token for authorized requests.
     *
     * @param authToken the JWT token from the auth server
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * Uploads a map to the community server.
     * Requires a valid authentication token.
     *
     * @param name        the map name
     * @param description the map description (may be empty)
     * @param mapData     the JSON-serialized map data
     * @return the server response map containing "id", "name", etc., or an error map
     */
    public Map<String, Object> uploadMap(String name, String description, String mapData) {
        try {
            Map<String, String> requestBody = Map.of(
                "name", name,
                "description", description != null ? description : "",
                "mapData", mapData
            );

            String jsonBody = MAPPER.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/maps"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (authToken != null) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                Map<String, Object> result = MAPPER.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
                LOG.info("Map uploaded successfully: {} (ID: {})", result.get("name"), result.get("id"));
                return result;
            } else {
                LOG.error("Map upload failed: status={}, body={}", response.statusCode(), response.body());
                return Map.of("error", "Upload failed with status " + response.statusCode(),
                    "status", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("Failed to upload map: {}", e.getMessage(), e);
            return Map.of("error", "Upload failed: " + e.getMessage());
        }
    }

    /**
     * Downloads a specific map by ID from the community server.
     *
     * @param mapId the map ID to download
     * @return the server response map containing "id", "name", "mapData", etc., or an error map
     */
    public Map<String, Object> downloadMap(long mapId) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/maps/" + mapId))
                .header("Accept", "application/json")
                .GET();

            if (authToken != null) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = MAPPER.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
                LOG.info("Map downloaded: {} (ID: {})", result.get("name"), result.get("id"));
                return result;
            } else {
                LOG.error("Map download failed: status={}, body={}", response.statusCode(), response.body());
                return Map.of("error", "Download failed with status " + response.statusCode(),
                    "status", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("Failed to download map {}: {}", mapId, e.getMessage(), e);
            return Map.of("error", "Download failed: " + e.getMessage());
        }
    }

    /**
     * Lists all community maps from the server.
     * Returns map metadata (without the full map data payload) for browsing.
     *
     * @return list of map metadata maps, or empty list on failure
     */
    public List<Map<String, Object>> listCommunityMaps() {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/maps"))
                .header("Accept", "application/json")
                .GET();

            if (authToken != null) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> maps = MAPPER.readValue(response.body(),
                    new TypeReference<List<Map<String, Object>>>() {});
                LOG.info("Listed {} community maps", maps.size());
                return maps;
            } else {
                LOG.error("List maps failed: status={}, body={}", response.statusCode(), response.body());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            LOG.error("Failed to list community maps: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Deletes a map from the community server (owner only).
     * Requires a valid authentication token matching the map owner.
     *
     * @param mapId the map ID to delete
     * @return true if deletion succeeded, false otherwise
     */
    public boolean deleteMap(long mapId) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/maps/" + mapId))
                .header("Accept", "application/json")
                .DELETE();

            if (authToken != null) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LOG.info("Map deleted: ID {}", mapId);
                return true;
            } else {
                LOG.error("Map delete failed: status={}, body={}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            LOG.error("Failed to delete map {}: {}", mapId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Increments the download count for a map without downloading the full data.
     *
     * @param mapId the map ID
     * @return true if the increment succeeded, false otherwise
     */
    public boolean incrementDownloadCount(long mapId) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/maps/" + mapId + "/download"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody());

            if (authToken != null) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LOG.debug("Download count incremented for map ID {}", mapId);
                return true;
            } else {
                LOG.warn("Increment download count failed: status={}", response.statusCode());
                return false;
            }
        } catch (Exception e) {
            LOG.error("Failed to increment download count for map {}: {}", mapId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the configured server URL.
     *
     * @return the server base URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Checks connectivity to the server by attempting a GET request to the maps endpoint.
     *
     * @return true if the server is reachable, false otherwise
     */
    public boolean isServerReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/maps"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOG.debug("Server unreachable: {}", e.getMessage());
            return false;
        }
    }
}
