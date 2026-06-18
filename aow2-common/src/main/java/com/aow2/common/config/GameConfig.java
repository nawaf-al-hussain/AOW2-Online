package com.aow2.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Complete game configuration loaded from JSON data files.
 * Contains all configurable game parameters that can be tuned without code changes.
 * REF: complete_building_stats.json game_config section
 * REF: MASTER_DOCUMENTATION.md Section 4 — Game configuration values
 */
public final class GameConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GameConfig.class);

    /** Turn time settings for different game speeds (slow, normal, fast). */
    private final int[] turnTimeSettings;

    /** Building footprint widths by building type index. */
    private final int[] buildingFootprintWidths;

    /** Building footprint heights by building type index. */
    private final int[] buildingFootprintHeights;

    /** Power radius for each building tier level. */
    private final int[] buildingPowerRadius;

    /** Experience thresholds for each rank level (ranks 1-3). */
    private final int[] rankExpThresholds;

    /** Credit rewards awarded at each rank level (ranks 1-3). */
    private final int[] rankCreditRewards;

    /** Bonus points awarded at each rank level (ranks 1-3). */
    private final int[] rankBonusPoints;

    /** Battle time limits for different game modes (in seconds). */
    private final int[] battleTimeLimits;

    /** Singleton instance with lazy initialization. */
    private static volatile GameConfig INSTANCE;

    /**
     * Full constructor for GameConfig.
     *
     * @param turnTimeSettings        turn time settings per game speed
     * @param buildingFootprintWidths  footprint widths per building type
     * @param buildingFootprintHeights footprint heights per building type
     * @param buildingPowerRadius      power radius per tier
     * @param rankExpThresholds        rank experience thresholds
     * @param rankCreditRewards        rank credit rewards
     * @param rankBonusPoints          rank bonus points
     * @param battleTimeLimits         battle time limits per game mode
     */
    public GameConfig(
        @JsonProperty("turnTimeSettings") int[] turnTimeSettings,
        @JsonProperty("buildingFootprintWidths") int[] buildingFootprintWidths,
        @JsonProperty("buildingFootprintHeights") int[] buildingFootprintHeights,
        @JsonProperty("buildingPowerRadius") int[] buildingPowerRadius,
        @JsonProperty("rankExpThresholds") int[] rankExpThresholds,
        @JsonProperty("rankCreditRewards") int[] rankCreditRewards,
        @JsonProperty("rankBonusPoints") int[] rankBonusPoints,
        @JsonProperty("battleTimeLimits") int[] battleTimeLimits
    ) {
        this.turnTimeSettings = turnTimeSettings != null ? turnTimeSettings.clone() : new int[0];
        this.buildingFootprintWidths = buildingFootprintWidths != null ? buildingFootprintWidths.clone() : new int[0];
        this.buildingFootprintHeights = buildingFootprintHeights != null ? buildingFootprintHeights.clone() : new int[0];
        this.buildingPowerRadius = buildingPowerRadius != null ? buildingPowerRadius.clone() : new int[0];
        this.rankExpThresholds = rankExpThresholds != null ? rankExpThresholds.clone() : new int[0];
        this.rankCreditRewards = rankCreditRewards != null ? rankCreditRewards.clone() : new int[0];
        this.rankBonusPoints = rankBonusPoints != null ? rankBonusPoints.clone() : new int[0];
        this.battleTimeLimits = battleTimeLimits != null ? battleTimeLimits.clone() : new int[0];
    }

    // --- Getters (defensive copies) ---

    /**
     * @return turn time settings per game speed
     */
    public int[] getTurnTimeSettings() {
        return turnTimeSettings.clone();
    }

    /**
     * @return building footprint widths per building type
     */
    public int[] getBuildingFootprintWidths() {
        return buildingFootprintWidths.clone();
    }

    /**
     * @return building footprint heights per building type
     */
    public int[] getBuildingFootprintHeights() {
        return buildingFootprintHeights.clone();
    }

    /**
     * @return power radius per building tier
     */
    public int[] getBuildingPowerRadius() {
        return buildingPowerRadius.clone();
    }

    /**
     * @return rank experience thresholds
     */
    public int[] getRankExpThresholds() {
        return rankExpThresholds.clone();
    }

    /**
     * @return rank credit rewards
     */
    public int[] getRankCreditRewards() {
        return rankCreditRewards.clone();
    }

    /**
     * @return rank bonus points
     */
    public int[] getRankBonusPoints() {
        return rankBonusPoints.clone();
    }

    /**
     * @return battle time limits per game mode
     */
    public int[] getBattleTimeLimits() {
        return battleTimeLimits.clone();
    }

    // --- Singleton access ---

    /**
     * Returns the singleton GameConfig instance, loading from the default classpath
     * resource "data/game_config.json" on first access.
     *
     * @return the global GameConfig instance
     */
    public static GameConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (GameConfig.class) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = loadFromResource("data/game_config.json");
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to load GameConfig from classpath resource", e);
                    }
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Resets the singleton instance. Primarily for testing purposes.
     */
    public static void resetInstance() {
        synchronized (GameConfig.class) {
            INSTANCE = null;
        }
    }

    /**
     * Sets the singleton instance directly. Primarily for testing purposes.
     *
     * @param config the GameConfig to use as the singleton
     */
    public static void setInstance(GameConfig config) {
        synchronized (GameConfig.class) {
            INSTANCE = config;
        }
    }

    // --- Loading methods ---

    /**
     * Loads a GameConfig from a JSON file on the filesystem.
     *
     * @param path path to the JSON file
     * @return parsed GameConfig
     * @throws IOException if the file cannot be read or parsed
     */
    public static GameConfig loadFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return parseJson(json);
    }

    /**
     * Loads a GameConfig from a classpath resource.
     *
     * @param resourcePath classpath resource path (e.g., "data/game_config.json")
     * @return parsed GameConfig
     * @throws IOException if the resource cannot be read or parsed
     */
    public static GameConfig loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = GameConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            ObjectMapper mapper = new ObjectMapper();
            GameConfig config = mapper.readValue(is, GameConfig.class);
            LOG.info("Loaded GameConfig from resource: {}", resourcePath);
            return config;
        }
    }

    /**
     * Parses a JSON string into a GameConfig using Jackson.
     *
     * @param json JSON string
     * @return parsed GameConfig
     * @throws IOException if parsing fails
     */
    public static GameConfig parseJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, GameConfig.class);
    }

    // --- Builder ---

    /**
     * Creates a new builder for GameConfig.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing GameConfig instances programmatically.
     */
    public static final class Builder {
        private int[] turnTimeSettings = {30, 20, 30, 20};
        private int[] buildingFootprintWidths = {2, 2, 2, 3, 3, 3, 4, 4, 4};
        private int[] buildingFootprintHeights = {2, 3, 4, 2, 3, 4, 2, 3, 4};
        private int[] buildingPowerRadius = {10, 20, 30, 40, 60, 127};
        private int[] rankExpThresholds = {20, 35, 50};
        private int[] rankCreditRewards = {10, 25, 51};
        private int[] rankBonusPoints = {0, 3, 6};
        private int[] battleTimeLimits = {1001, 1100, 1101, 1200};

        private Builder() {}

        public Builder turnTimeSettings(int[] val) { this.turnTimeSettings = val; return this; }
        public Builder buildingFootprintWidths(int[] val) { this.buildingFootprintWidths = val; return this; }
        public Builder buildingFootprintHeights(int[] val) { this.buildingFootprintHeights = val; return this; }
        public Builder buildingPowerRadius(int[] val) { this.buildingPowerRadius = val; return this; }
        public Builder rankExpThresholds(int[] val) { this.rankExpThresholds = val; return this; }
        public Builder rankCreditRewards(int[] val) { this.rankCreditRewards = val; return this; }
        public Builder rankBonusPoints(int[] val) { this.rankBonusPoints = val; return this; }
        public Builder battleTimeLimits(int[] val) { this.battleTimeLimits = val; return this; }

        /**
         * Builds a new GameConfig from the builder's state.
         *
         * @return a new GameConfig instance
         */
        public GameConfig build() {
            return new GameConfig(
                turnTimeSettings,
                buildingFootprintWidths,
                buildingFootprintHeights,
                buildingPowerRadius,
                rankExpThresholds,
                rankCreditRewards,
                rankBonusPoints,
                battleTimeLimits
            );
        }
    }

    // --- equals / hashCode ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameConfig that)) return false;
        return Arrays.equals(turnTimeSettings, that.turnTimeSettings)
            && Arrays.equals(buildingFootprintWidths, that.buildingFootprintWidths)
            && Arrays.equals(buildingFootprintHeights, that.buildingFootprintHeights)
            && Arrays.equals(buildingPowerRadius, that.buildingPowerRadius)
            && Arrays.equals(rankExpThresholds, that.rankExpThresholds)
            && Arrays.equals(rankCreditRewards, that.rankCreditRewards)
            && Arrays.equals(rankBonusPoints, that.rankBonusPoints)
            && Arrays.equals(battleTimeLimits, that.battleTimeLimits);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(turnTimeSettings);
        result = 31 * result + Arrays.hashCode(buildingFootprintWidths);
        result = 31 * result + Arrays.hashCode(buildingFootprintHeights);
        result = 31 * result + Arrays.hashCode(buildingPowerRadius);
        result = 31 * result + Arrays.hashCode(rankExpThresholds);
        result = 31 * result + Arrays.hashCode(rankCreditRewards);
        result = 31 * result + Arrays.hashCode(rankBonusPoints);
        result = 31 * result + Arrays.hashCode(battleTimeLimits);
        return result;
    }

    @Override
    public String toString() {
        return "GameConfig{" +
               "turnTimeSettings=" + Arrays.toString(turnTimeSettings) +
               ", buildingPowerRadius=" + Arrays.toString(buildingPowerRadius) +
               ", rankExpThresholds=" + Arrays.toString(rankExpThresholds) +
               ", rankCreditRewards=" + Arrays.toString(rankCreditRewards) +
               ", rankBonusPoints=" + Arrays.toString(rankBonusPoints) +
               ", battleTimeLimits=" + Arrays.toString(battleTimeLimits) +
               '}';
    }
}
