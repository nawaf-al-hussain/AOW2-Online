package com.aow2.server.controller;

import com.aow2.server.model.UploadedMap;
import com.aow2.server.repository.UploadedMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for community map management endpoints.
 * Handles map listing, uploading, downloading, deletion, and download tracking.
 * <p>
 * Endpoints:
 * - POST   /api/maps              — upload a new map (requires auth)
 * - GET    /api/maps              — list all community maps
 * - GET    /api/maps/{id}         — download a specific map
 * - DELETE /api/maps/{id}         — delete a map (owner only)
 * - POST   /api/maps/{id}/download — increment download count
 * <p>
 * REF: protocol_specification.md - Type 21 MAP_DATA, Type 41 MAP_LIST
 * REF: protocol_specification.md - Type 26 MAP_VALIDATION
 * REF: phases.md Phase 9 - Map sharing
 */
@RestController
@RequestMapping("/api/maps")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);

    private final UploadedMapRepository uploadedMapRepository;

    /** Default page size for map listing. FIX (M-NEW-19) */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** Maximum page size to prevent excessive queries. */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Constructs the MapController.
     *
     * @param uploadedMapRepository repository for map persistence
     */
    public MapController(UploadedMapRepository uploadedMapRepository) {
        this.uploadedMapRepository = uploadedMapRepository;
    }

    /**
     * Lists community-uploaded maps with pagination.
     * GET /api/maps?page=0&size=20
     * Returns map metadata without the full map data payload for efficient browsing.
     * FIX (M-NEW-19): Added pagination to prevent loading all maps at once.
     *
     * @param page zero-based page index (default 0)
     * @param size page size (default 20, max 100)
     * @return 200 with a page of map metadata
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listMaps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        size = Math.min(size, MAX_PAGE_SIZE);
        var pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var pageResult = uploadedMapRepository.findAll(pageRequest);

        List<Map<String, Object>> summaries = pageResult.getContent().stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "name", m.getName(),
                "description", m.getDescription() != null ? m.getDescription() : "",
                "uploaderId", m.getUploaderId(),
                "downloadCount", m.getDownloadCount(),
                "createdAt", m.getCreatedAt().toString()
        )).toList();

        return ResponseEntity.ok(Map.of(
                "maps", summaries,
                "page", page,
                "size", size,
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages()
        ));
    }

    /**
     * Uploads a new custom map.
     * POST /api/maps
     * Requires authentication; the authenticated player becomes the map owner.
     *
     * @param authentication the authenticated player (becomes the uploader)
     * @param request        must contain "name", "mapData", and optionally "description"
     * @return 201 with the created map metadata, or 400 on validation error
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadMap(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        Long uploaderId = (Long) authentication.getPrincipal();
        String name = request.get("name");
        String mapData = request.get("mapData");
        String description = request.getOrDefault("description", "");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Map name is required"));
        }
        if (mapData == null || mapData.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Map data is required"));
        }

        // Enforce maximum name length
        if (name.length() > 64) {
            return ResponseEntity.badRequest().body(Map.of("error", "Map name must be 64 characters or fewer"));
        }

        // Enforce maximum map data size (5 MB)
        if (mapData.length() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Map data exceeds 5 MB limit"));
        }

        UploadedMap map = new UploadedMap(uploaderId, name, description, mapData);
        map = uploadedMapRepository.save(map);

        log.info("Map uploaded: {} (ID: {}) by player {}", name, map.getId(), uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", map.getId(),
                "name", map.getName(),
                "description", map.getDescription() != null ? map.getDescription() : "",
                "uploaderId", map.getUploaderId(),
                "createdAt", map.getCreatedAt().toString()
        ));
    }

    /**
     * Downloads a specific map by ID.
     * GET /api/maps/{id}
     * Returns the full map data including the JSON map payload.
     *
     * @param id the map ID
     * @return 200 with full map data, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> downloadMap(@PathVariable Long id) {
        return uploadedMapRepository.findById(id)
                .map(map -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", map.getId(),
                        "name", map.getName(),
                        "description", map.getDescription() != null ? map.getDescription() : "",
                        "mapData", map.getMapData(),
                        "uploaderId", map.getUploaderId(),
                        "downloadCount", map.getDownloadCount()
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.<String, Object>of("error", "Map not found: " + id)));
    }

    /**
     * Deletes a map owned by the requesting player.
     * DELETE /api/maps/{id}
     * Only the map owner (original uploader) can delete the map.
     *
     * @param authentication the authenticated player
     * @param id             the map ID to delete
     * @return 200 on success, 403 if not the owner, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMap(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Long playerId = (Long) authentication.getPrincipal();
        return uploadedMapRepository.findById(id)
                .<ResponseEntity<Map<String, Object>>>map(map -> {
                    if (!map.getUploaderId().equals(playerId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.<String, Object>of("error", "Not the map owner"));
                    }
                    uploadedMapRepository.delete(map);
                    log.info("Map deleted: {} (ID: {}) by player {}", map.getName(), id, playerId);
                    return ResponseEntity.ok(Map.<String, Object>of("status", "deleted", "id", id));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.<String, Object>of("error", "Map not found: " + id)));
    }

    /**
     * Increments the download count for a map.
     * POST /api/maps/{id}/download
     * This endpoint is called when a player explicitly downloads a map
     * (separate from GET which auto-increments). Allows clients to
     * track download counts without fetching the full map data.
     *
     * @param id the map ID
     * @return 200 with updated download count, or 404 if not found
     */
    @PostMapping("/{id}/download")
    public ResponseEntity<Map<String, Object>> incrementDownloadCount(@PathVariable Long id) {
        return uploadedMapRepository.findById(id)
                .map(map -> {
                    map.incrementDownloadCount();
                    uploadedMapRepository.save(map);
                    log.debug("Download count incremented for map {} (ID: {}), now {}",
                        map.getName(), id, map.getDownloadCount());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "id", map.getId(),
                            "downloadCount", map.getDownloadCount()
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.<String, Object>of("error", "Map not found: " + id)));
    }
}
