package com.aow2.core.campaign;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Campaign save/load system.
 * Manages persistence of campaign game state across 3 save slots.
 * REF: campaign_guide.md Section 1.1 - 3 simultaneous save slots (/s0m, /s1m, /s2m)
 * REF: campaign_guide.md Section 8.1 - progress persistence via U() method to RecordStore
 */
public final class SaveManager {

    private static final Logger LOG = LoggerFactory.getLogger(SaveManager.class);

    /** Maximum number of save slots. REF: campaign_guide.md - 3 save slots */
    public static final int MAX_SAVE_SLOTS = 3;

    /** File extension for save files. */
    private static final String SAVE_EXTENSION = ".json";

    /** In-memory cache of save data keyed by slot index. */
    private final Map<Integer, SaveData> saveCache;

    /** Directory where save files are stored. */
    private final Path saveDirectory;

    /** Jackson object mapper for JSON serialization. */
    private final ObjectMapper objectMapper;

    /**
     * Constructs a SaveManager that stores saves in the specified directory.
     *
     * @param saveDirectory directory for save file persistence
     */
    public SaveManager(Path saveDirectory) {
        this.saveDirectory = saveDirectory;
        this.saveCache = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.findAndRegisterModules();
        ensureSaveDirectory();
    }

    /**
     * Constructs a SaveManager with in-memory storage only (for testing).
     */
    public SaveManager() {
        this.saveDirectory = null;
        this.saveCache = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Saves the complete game state to the specified slot.
     *
     * @param slot         save slot index (0-2)
     * @param state        current game state
     * @param entities     entity manager with all entities
     * @param episode      current campaign episode
     * @param missionIndex current mission index within the episode
     * @return true if the save was successful
     */
    public boolean save(int slot, GameState state, EntityManager entities,
                        CampaignEpisode episode, int missionIndex) {
        return save(slot, state, entities, episode, missionIndex, List.of(), List.of(), List.of());
    }

    /**
     * Saves the complete game state to the specified slot with full objective/trigger state.
     *
     * @param slot              save slot index (0-2)
     * @param state             current game state
     * @param entities          entity manager with all entities
     * @param episode           current campaign episode
     * @param missionIndex      current mission index within the episode
     * @param completedResearch list of completed research IDs
     * @param credits           credit amounts per player
     * @param objectives        current mission objectives
     * @return true if the save was successful
     */
    public boolean save(int slot, GameState state, EntityManager entities,
                        CampaignEpisode episode, int missionIndex,
                        List<String> completedResearch, List<Integer> credits,
                        List<Objective> objectives) {
        if (slot < 0 || slot >= MAX_SAVE_SLOTS) {
            LOG.error("Invalid save slot: {}", slot);
            return false;
        }

        try {
            List<SaveData.UnitSave> unitSaves = serializeUnits(entities);
            List<SaveData.BuildingSave> buildingSaves = serializeBuildings(entities);

            SaveData saveData = new SaveData(
                slot,
                episode,
                missionIndex,
                Instant.now(),
                state.currentTick(),
                unitSaves,
                buildingSaves,
                completedResearch,
                credits,
                objectives,
                List.of()
            );

            saveCache.put(slot, saveData);

            if (saveDirectory != null) {
                persistToFile(slot, saveData);
            }

            LOG.info("Saved game to slot {} (episode={}, mission={}, tick={})",
                slot, episode, missionIndex, state.currentTick());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to save game to slot {}", slot, e);
            return false;
        }
    }

    /**
     * Loads the game state from the specified slot.
     *
     * @param slot save slot index (0-2)
     * @return the loaded save data, or null if the slot is empty or loading fails
     */
    public SaveData load(int slot) {
        if (slot < 0 || slot >= MAX_SAVE_SLOTS) {
            LOG.error("Invalid save slot: {}", slot);
            return null;
        }

        // Try cache first
        SaveData cached = saveCache.get(slot);
        if (cached != null) {
            LOG.info("Loaded save from slot {} (cache hit)", slot);
            return cached;
        }

        // Try loading from file
        if (saveDirectory != null) {
            try {
                SaveData loaded = loadFromFile(slot);
                if (loaded != null) {
                    saveCache.put(slot, loaded);
                    LOG.info("Loaded save from slot {} (file)", slot);
                    return loaded;
                }
            } catch (Exception e) {
                LOG.error("Failed to load save from slot {}", slot, e);
            }
        }

        LOG.warn("No save found in slot {}", slot);
        return null;
    }

    /**
     * Checks whether a save exists in the specified slot.
     *
     * @param slot save slot index (0-2)
     * @return true if a save exists
     */
    public boolean hasSave(int slot) {
        if (slot < 0 || slot >= MAX_SAVE_SLOTS) {
            return false;
        }
        if (saveCache.containsKey(slot)) {
            return true;
        }
        if (saveDirectory != null) {
            return Files.exists(getSaveFilePath(slot));
        }
        return false;
    }

    /**
     * Deletes the save in the specified slot.
     *
     * @param slot save slot index (0-2)
     * @return true if the save was deleted or did not exist
     */
    public boolean deleteSave(int slot) {
        if (slot < 0 || slot >= MAX_SAVE_SLOTS) {
            LOG.error("Invalid save slot: {}", slot);
            return false;
        }

        saveCache.remove(slot);

        if (saveDirectory != null) {
            try {
                Path filePath = getSaveFilePath(slot);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
            } catch (IOException e) {
                LOG.error("Failed to delete save file for slot {}", slot, e);
                return false;
            }
        }

        LOG.info("Deleted save in slot {}", slot);
        return true;
    }

    /**
     * Returns the save directory path.
     *
     * @return save directory path, or null if in-memory only
     */
    public Path getSaveDirectory() {
        return saveDirectory;
    }

    // --- Private helpers ---

    private void ensureSaveDirectory() {
        if (saveDirectory != null) {
            try {
                Files.createDirectories(saveDirectory);
            } catch (IOException e) {
                LOG.error("Failed to create save directory: {}", saveDirectory, e);
            }
        }
    }

    private Path getSaveFilePath(int slot) {
        return saveDirectory.resolve("save_slot_" + slot + SAVE_EXTENSION);
    }

    private void persistToFile(int slot, SaveData saveData) throws IOException {
        Path filePath = getSaveFilePath(slot);
        objectMapper.writeValue(filePath.toFile(), saveData);
        LOG.debug("Persisted save to file: {}", filePath);
    }

    private SaveData loadFromFile(int slot) throws IOException {
        Path filePath = getSaveFilePath(slot);
        if (!Files.exists(filePath)) {
            return null;
        }
        return objectMapper.readValue(filePath.toFile(), SaveData.class);
    }

    private List<SaveData.UnitSave> serializeUnits(EntityManager entities) {
        List<SaveData.UnitSave> result = new ArrayList<>();
        for (Unit unit : entities.getAllUnits()) {
            result.add(new SaveData.UnitSave(
                unit.getId(),
                unit.getUnitType(),
                unit.getFaction(),
                unit.getPosition(),
                unit.getHp(),
                unit.getMaxHp(),
                unit.getRank(),
                unit.getExperience(),
                unit.isSiegeMode(),
                unit.getAttackCooldown(),
                unit.getWeaponCooldown()
            ));
        }
        return result;
    }

    private List<SaveData.BuildingSave> serializeBuildings(EntityManager entities) {
        List<SaveData.BuildingSave> result = new ArrayList<>();
        for (Building building : entities.getAllBuildings()) {
            result.add(new SaveData.BuildingSave(
                building.getId(),
                building.getBuildingType(),
                building.getFaction(),
                building.getPosition(),
                building.getHp(),
                building.getMaxHp(),
                building.getConstructionProgress(),
                building.isPowered(),
                building.getResearchId(),
                building.getProductionQueue(),
                building.getProductionProgress()
            ));
        }
        return result;
    }
}
