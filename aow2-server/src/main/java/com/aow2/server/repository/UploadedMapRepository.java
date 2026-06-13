package com.aow2.server.repository;

import com.aow2.server.model.UploadedMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for UploadedMap entity persistence and querying.
 * Supports community map sharing with download tracking.
 * REF: protocol_specification.md - Type 21 MAP_DATA, Type 41 MAP_LIST
 */
@Repository
public interface UploadedMapRepository extends JpaRepository<UploadedMap, Long> {

    /**
     * Finds all maps uploaded by a specific player.
     *
     * @param uploaderId the uploader's player ID
     * @return list of maps uploaded by the player
     */
    List<UploadedMap> findByUploaderIdOrderByCreatedAtDesc(Long uploaderId);

    /**
     * Finds all maps ordered by creation date, for map browsing.
     *
     * @return list of all uploaded maps ordered by newest first
     */
    List<UploadedMap> findAllByOrderByCreatedAtDesc();

    /**
     * Searches for maps by name (case-insensitive substring match).
     *
     * @param name the search term
     * @return list of maps whose name contains the search term
     */
    List<UploadedMap> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String name);
}
