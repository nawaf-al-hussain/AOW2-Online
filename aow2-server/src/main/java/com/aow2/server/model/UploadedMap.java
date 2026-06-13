package com.aow2.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a community-uploaded custom map.
 * Players can create, share, and download custom maps for multiplayer matches.
 * REF: protocol_specification.md - Type 21 MAP_DATA, Type 41 MAP_LIST
 */
@Entity
@Table(name = "uploaded_maps")
public class UploadedMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "map_data", nullable = false, columnDefinition = "TEXT")
    private String mapData;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Default constructor required by JPA.
     */
    public UploadedMap() {
        this.createdAt = Instant.now();
    }

    /**
     * Constructs an UploadedMap with all required fields.
     *
     * @param uploaderId the ID of the player who uploaded the map
     * @param name       the display name of the map
     * @param description optional description
     * @param mapData    the JSON-serialized map data
     */
    public UploadedMap(Long uploaderId, String name, String description, String mapData) {
        this.uploaderId = uploaderId;
        this.name = name;
        this.description = description;
        this.mapData = mapData;
        this.downloadCount = 0;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMapData() { return mapData; }
    public void setMapData(String mapData) { this.mapData = mapData; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Increments the download counter by one.
     */
    public void incrementDownloadCount() {
        this.downloadCount++;
    }
}
