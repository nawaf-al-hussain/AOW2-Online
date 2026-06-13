package com.aow2.server.controller;

import com.aow2.server.model.UploadedMap;
import com.aow2.server.repository.UploadedMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for community map management endpoints.
 * Handles map listing, uploading, downloading, and deletion.
 * REF: protocol_specification.md - Type 21 MAP_DATA, Type 41 MAP_LIST
 * REF: protocol_specification.md - Type 26 MAP_VALIDATION
 */
@RestController
@RequestMapping("/api/maps")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);

    private final UploadedMapRepository uploadedMapRepository;

    /**
     * Constructs the MapController.
     *
     * @param uploadedMapRepository repository for map persistence
     */
    public MapController(UploadedMapRepository uploadedMapRepository) {
        this.uploadedMapRepository = uploadedMapRepository;
    }

    /**
     * Lists all community-uploaded maps.
     * GET /api/maps
     *
     * @return 200 with a list of map metadata (without the full map data payload)
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listMaps() {
        List<UploadedMap> maps = uploadedMapRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> summaries = maps.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "name", m.getName(),
                "description", m.getDescription() != null ? m.getDescription() : "",
                "uploaderId", m.getUploaderId(),
                "downloadCount", m.getDownloadCount(),
                "createdAt", m.getCreatedAt().toString()
        )).toList();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Uploads a new custom map.
     * POST /api/maps
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
     *
     * @param id the map ID
     * @return 200 with full map data, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> downloadMap(@PathVariable Long id) {
        return uploadedMapRepository.findById(id)
                .map(map -> {
                    map.incrementDownloadCount();
                    uploadedMapRepository.save(map);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "id", map.getId(),
                            "name", map.getName(),
                            "description", map.getDescription() != null ? map.getDescription() : "",
                            "mapData", map.getMapData(),
                            "uploaderId", map.getUploaderId(),
                            "downloadCount", map.getDownloadCount()
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Map not found: " + id)));
    }

    /**
     * Deletes a map owned by the requesting player.
     * DELETE /api/maps/{id}
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
                .map(map -> {
                    if (!map.getUploaderId().equals(playerId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>body(Map.of("error", "Not the map owner"));
                    }
                    uploadedMapRepository.delete(map);
                    log.info("Map deleted: {} (ID: {}) by player {}", map.getName(), id, playerId);
                    return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .<Map<String, Object>>body(Map.of("error", "Map not found: " + id)));
    }
}
