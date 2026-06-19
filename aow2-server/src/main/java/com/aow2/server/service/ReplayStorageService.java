package com.aow2.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * Service for persisting and retrieving replay files on the local filesystem.
 * <p>
 * P1 Fix: Previously, ReplayController discarded the actual replay data and only
 * stored a synthetic file path string. This service ensures replay data is written
 * to disk and can be read back for download.
 */
@Service
public class ReplayStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayStorageService.class);

    /** Directory where replay files are stored, configured via application properties. */
    private final Path replayDir;

    /**
     * Creates the ReplayStorageService.
     *
     * @param replayDirectoryPath filesystem path for replay storage (defaults to "replays")
     */
    public ReplayStorageService(
            @Value("${aow2.replay.directory:replays}") String replayDirectoryPath) {
        this.replayDir = Paths.get(replayDirectoryPath).toAbsolutePath();
        try {
            Files.createDirectories(this.replayDir);
            LOG.info("Replay storage directory initialized: {}", this.replayDir);
        } catch (IOException e) {
            LOG.error("Failed to create replay directory: {}", this.replayDir, e);
            throw new RuntimeException("Cannot initialize replay storage", e);
        }
    }

    /**
     * Saves replay data to disk. Accepts Base64-encoded data and decodes it
     * before writing to a binary file.
     *
     * @param matchId    the match ID (used as filename)
     * @param replayData Base64-encoded replay data
     * @return the relative file path stored in the database
     * @throws IOException if the file cannot be written
     */
    public String saveReplay(Long matchId, String replayData) throws IOException {
        Path filePath = replayDir.resolve(matchId + ".aow2rep");
        byte[] decoded = Base64.getDecoder().decode(replayData);
        Files.write(filePath, decoded, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        LOG.info("Replay saved: {} ({} bytes)", filePath, decoded.length);
        return "replays/" + matchId + ".aow2rep";
    }

    /**
     * Reads replay data from disk and returns it as Base64-encoded string.
     *
     * @param matchId the match ID to retrieve
     * @return Base64-encoded replay data, or null if the file does not exist
     * @throws IOException if the file cannot be read
     */
    public String loadReplay(Long matchId) throws IOException {
        Path filePath = replayDir.resolve(matchId + ".aow2rep");
        if (!Files.exists(filePath)) {
            return null;
        }
        byte[] data = Files.readAllBytes(filePath);
        LOG.debug("Replay loaded: {} ({} bytes)", filePath, data.length);
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Checks whether a replay file exists for the given match ID.
     *
     * @param matchId the match ID to check
     * @return true if the replay file exists on disk
     */
    public boolean replayExists(Long matchId) {
        return Files.exists(replayDir.resolve(matchId + ".aow2rep"));
    }
}
