package com.aow2.client.render;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Direction;
import com.aow2.common.model.Faction;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitType;

import javafx.scene.image.Image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages sprite loading from PNG files and procedural sprite generation.
 * Caches all loaded/generated sprites in HashMaps for efficient rendering.
 * <p>
 * Sprite loading priority:
 * 1. Load from /assets/sprites/ directory if a PNG file exists
 * 2. Fall back to procedurally generated sprites from ProceduralSpriteGenerator
 * <p>
 * Cache keys:
 * - Unit sprites: "{unitType.name()}_{direction.name()}" (8 directions per unit type)
 * - Building sprites: "{buildingType.name()}" (one per type, faction-colored)
 * - Terrain sprites: "{terrainType.name()}" (one per terrain type)
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 5 - Unit Encyclopedia (unit visual types)
 * REF: MASTER_DOCUMENTATION.md Section 6 - Building Encyclopedia (building sizes)
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Map System (isometric tile rendering)
 */
public class SpriteManager {

    private static final Logger LOG = LoggerFactory.getLogger(SpriteManager.class);

    /** Default base path for sprite assets. */
    private static final String DEFAULT_SPRITE_BASE_PATH = "assets/sprites";

    /** Singleton instance. */
    private static volatile SpriteManager instance;

    /** Cache for unit sprites: key = "UNITTYPE_DIRECTION". */
    private final Map<String, Image> unitSpriteCache;

    /** Cache for building sprites: key = "BUILDINGTYPE_FACTION". */
    private final Map<String, Image> buildingSpriteCache;

    /** Cache for terrain sprites: key = "TERRAINTYPE". */
    private final Map<String, Image> terrainSpriteCache;

    /** The procedural sprite generator for fallback sprites. */
    private final ProceduralSpriteGenerator generator;

    /** Base path for sprite asset files. */
    private final String spriteBasePath;

    /** Whether the sprite manager has been initialized. */
    private boolean initialized;

    /**
     * Private constructor for singleton pattern.
     * Initializes caches and the procedural sprite generator.
     */
    private SpriteManager() {
        this(DEFAULT_SPRITE_BASE_PATH);
    }

    /**
     * Private constructor with configurable base path (for testing).
     *
     * @param spriteBasePath the base directory path for sprite assets
     */
    private SpriteManager(String spriteBasePath) {
        this.unitSpriteCache = new HashMap<>();
        this.buildingSpriteCache = new HashMap<>();
        this.terrainSpriteCache = new HashMap<>();
        this.generator = new ProceduralSpriteGenerator();
        this.spriteBasePath = spriteBasePath;
        this.initialized = false;
    }

    /**
     * Returns the singleton instance of SpriteManager.
     * Uses double-checked locking for thread safety.
     *
     * @return the SpriteManager instance
     */
    public static SpriteManager getInstance() {
        if (instance == null) {
            synchronized (SpriteManager.class) {
                if (instance == null) {
                    instance = new SpriteManager();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new SpriteManager with a custom base path (for testing).
     * Does not affect the singleton instance.
     *
     * @param spriteBasePath the base directory path for sprite assets
     * @return a new SpriteManager instance
     */
    public static SpriteManager createWithPath(String spriteBasePath) {
        return new SpriteManager(spriteBasePath);
    }

    /**
     * Initializes the sprite manager by pre-loading all sprites.
     * Attempts to load from PNG files first, then falls back to procedural generation.
     * Must be called on the JavaFX application thread.
     */
    public void initialize() {
        if (initialized) {
            LOG.warn("SpriteManager already initialized");
            return;
        }

        LOG.info("Initializing SpriteManager with base path: {}", spriteBasePath);

        // Pre-generate all unit sprites (all unit types x 8 directions)
        for (UnitType unitType : UnitType.values()) {
            for (Direction direction : Direction.values()) {
                String cacheKey = unitSpriteKey(unitType, direction);
                Image sprite = loadUnitSpriteFromDisk(unitType, direction);
                if (sprite == null) {
                    sprite = generator.generateUnitSprite(unitType, direction);
                }
                unitSpriteCache.put(cacheKey, sprite);
            }
        }

        // Pre-generate all building sprites (per faction)
        for (BuildingType buildingType : BuildingType.values()) {
            Faction faction = buildingType.faction();
            String cacheKey = buildingSpriteKey(buildingType, faction);
            Image sprite = loadBuildingSpriteFromDisk(buildingType, faction);
            if (sprite == null) {
                sprite = generator.generateBuildingSprite(buildingType, faction);
            }
            buildingSpriteCache.put(cacheKey, sprite);
        }

        // Pre-generate all terrain sprites
        for (TerrainType terrainType : TerrainType.values()) {
            String cacheKey = terrainSpriteKey(terrainType);
            Image sprite = loadTerrainSpriteFromDisk(terrainType);
            if (sprite == null) {
                sprite = generator.generateTerrainSprite(terrainType);
            }
            terrainSpriteCache.put(cacheKey, sprite);
        }

        initialized = true;
        LOG.info("SpriteManager initialized: {} unit sprites, {} building sprites, {} terrain sprites",
            unitSpriteCache.size(), buildingSpriteCache.size(), terrainSpriteCache.size());
    }

    /**
     * Gets a unit sprite for the given unit type and direction.
     * If the manager is not initialized, initializes it first.
     *
     * @param unitType  the unit type
     * @param direction the facing direction
     * @return the cached sprite image, or null if not available
     */
    public Image getUnitSprite(UnitType unitType, Direction direction) {
        ensureInitialized();
        String key = unitSpriteKey(unitType, direction);
        Image sprite = unitSpriteCache.get(key);
        if (sprite == null) {
            LOG.warn("Unit sprite not found in cache: {}", key);
            sprite = generator.generateUnitSprite(unitType, direction);
            unitSpriteCache.put(key, sprite);
        }
        return sprite;
    }

    /**
     * Gets a building sprite for the given building type.
     * If the manager is not initialized, initializes it first.
     *
     * @param buildingType the building type
     * @param faction      the owning faction (used for fallback generation)
     * @return the cached sprite image, or null if not available
     */
    public Image getBuildingSprite(BuildingType buildingType, Faction faction) {
        ensureInitialized();
        String key = buildingSpriteKey(buildingType, faction);
        Image sprite = buildingSpriteCache.get(key);
        if (sprite == null) {
            LOG.warn("Building sprite not found in cache: {}", key);
            sprite = generator.generateBuildingSprite(buildingType, faction);
            buildingSpriteCache.put(key, sprite);
        }
        return sprite;
    }

    /**
     * Gets a terrain sprite for the given terrain type.
     * If the manager is not initialized, initializes it first.
     *
     * @param terrainType the terrain type
     * @return the cached sprite image, or null if not available
     */
    public Image getTerrainSprite(TerrainType terrainType) {
        ensureInitialized();
        String key = terrainSpriteKey(terrainType);
        Image sprite = terrainSpriteCache.get(key);
        if (sprite == null) {
            LOG.warn("Terrain sprite not found in cache: {}", key);
            sprite = generator.generateTerrainSprite(terrainType);
            terrainSpriteCache.put(key, sprite);
        }
        return sprite;
    }

    /**
     * Attempts to load a unit sprite from disk.
     * File path: {spriteBasePath}/units/{unitType.name()}_{direction.name()}.png
     *
     * @param unitType  the unit type
     * @param direction the direction
     * @return the loaded image, or null if the file does not exist
     */
    private Image loadUnitSpriteFromDisk(UnitType unitType, Direction direction) {
        String path = spriteBasePath + "/units/" + unitType.name() + "_" + direction.name() + ".png";
        return loadSpriteFromPath(path);
    }

    /**
     * Attempts to load a building sprite from disk.
     * File path: {spriteBasePath}/buildings/{buildingType.name()}_{faction.name()}.png
     *
     * @param buildingType the building type
     * @param faction      the faction for this building
     * @return the loaded image, or null if the file does not exist
     */
    private Image loadBuildingSpriteFromDisk(BuildingType buildingType, Faction faction) {
        // Try faction-specific path first, then fallback to type-only path
        String factionPath = spriteBasePath + "/buildings/" + buildingType.name() + "_" + faction.name() + ".png";
        Image sprite = loadSpriteFromPath(factionPath);
        if (sprite != null) return sprite;
        return loadSpriteFromPath(spriteBasePath + "/buildings/" + buildingType.name() + ".png");
    }

    /**
     * Attempts to load a terrain sprite from disk.
     * File path: {spriteBasePath}/terrain/{terrainType.name()}.png
     *
     * @param terrainType the terrain type
     * @return the loaded image, or null if the file does not exist
     */
    private Image loadTerrainSpriteFromDisk(TerrainType terrainType) {
        String path = spriteBasePath + "/terrain/" + terrainType.name() + ".png";
        return loadSpriteFromPath(path);
    }

    /**
     * Loads an image from the given path.
     * Tries the filesystem first, then the classpath.
     *
     * @param path the image path
     * @return the loaded image, or null if not found
     */
    private Image loadSpriteFromPath(String path) {
        // Try filesystem first
        Path fsPath = Paths.get(path);
        if (Files.exists(fsPath)) {
            try (InputStream is = Files.newInputStream(fsPath)) {
                Image image = new Image(is);
                if (!image.isError()) {
                    LOG.debug("Loaded sprite from filesystem: {}", path);
                    return image;
                }
            } catch (Exception e) {
                LOG.debug("Failed to load sprite from filesystem: {}", path);
            }
        }

        // Try classpath
        String classpathPath = "/" + path;
        try (InputStream is = getClass().getResourceAsStream(classpathPath)) {
            if (is != null) {
                Image image = new Image(is);
                if (!image.isError()) {
                    LOG.debug("Loaded sprite from classpath: {}", classpathPath);
                    return image;
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to load sprite from classpath: {}", classpathPath);
        }

        return null;
    }

    /**
     * Ensures the sprite manager is initialized before accessing sprites.
     */
    private void ensureInitialized() {
        if (!initialized) {
            LOG.warn("SpriteManager accessed before initialization - initializing now");
            initialize();
        }
    }

    /**
     * Generates the cache key for a unit sprite.
     *
     * @param unitType  the unit type
     * @param direction the facing direction
     * @return the cache key string
     */
    private static String unitSpriteKey(UnitType unitType, Direction direction) {
        return unitType.name() + "_" + direction.name();
    }

    /**
     * Generates the cache key for a building sprite, including faction.
     *
     * @param buildingType the building type
     * @param faction      the faction
     * @return the cache key string
     */
    private static String buildingSpriteKey(BuildingType buildingType, Faction faction) {
        return buildingType.name() + "_" + faction.name();
    }

    /**
     * Generates the cache key for a terrain sprite.
     *
     * @param terrainType the terrain type
     * @return the cache key string
     */
    private static String terrainSpriteKey(TerrainType terrainType) {
        return terrainType.name();
    }

    /**
     * Clears all sprite caches. Useful for testing or when sprite assets change.
     */
    public void clearCache() {
        unitSpriteCache.clear();
        buildingSpriteCache.clear();
        terrainSpriteCache.clear();
        initialized = false;
        LOG.info("Sprite caches cleared");
    }

    /**
     * Returns the number of cached unit sprites.
     *
     * @return unit sprite cache size
     */
    public int getUnitSpriteCount() {
        return unitSpriteCache.size();
    }

    /**
     * Returns the number of cached building sprites.
     *
     * @return building sprite cache size
     */
    public int getBuildingSpriteCount() {
        return buildingSpriteCache.size();
    }

    /**
     * Returns the number of cached terrain sprites.
     *
     * @return terrain sprite cache size
     */
    public int getTerrainSpriteCount() {
        return terrainSpriteCache.size();
    }

    /**
     * Returns whether the sprite manager has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Resets the singleton instance. Used for testing.
     */
    public static void resetInstance() {
        synchronized (SpriteManager.class) {
            if (instance != null) {
                instance.clearCache();
            }
            instance = null;
        }
    }
}
