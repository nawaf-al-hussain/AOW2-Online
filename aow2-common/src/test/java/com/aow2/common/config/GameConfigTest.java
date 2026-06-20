package com.aow2.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameConfigTest {

    @AfterEach
    void resetSingleton() {
        GameConfig.resetInstance();
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Should build config with default values")
        void shouldBuildWithDefaults() {
            GameConfig config = GameConfig.builder().build();
            assertArrayEquals(new int[]{30, 20, 30, 20}, config.getTurnTimeSettings());
            assertArrayEquals(new int[]{10, 20, 30, 40, 60, 127}, config.getBuildingPowerRadius());
            assertArrayEquals(new int[]{20, 35, 50}, config.getRankExpThresholds());
            assertArrayEquals(new int[]{10, 25, 51}, config.getRankCreditRewards());
            assertArrayEquals(new int[]{0, 3, 6}, config.getRankBonusPoints());
        }

        @Test
        @DisplayName("Should build config with custom values")
        void shouldBuildWithCustomValues() {
            GameConfig config = GameConfig.builder()
                .turnTimeSettings(new int[]{60, 30, 15})
                .rankExpThresholds(new int[]{25, 40, 60})
                .build();

            assertArrayEquals(new int[]{60, 30, 15}, config.getTurnTimeSettings());
            assertArrayEquals(new int[]{25, 40, 60}, config.getRankExpThresholds());
        }
    }

    @Nested
    @DisplayName("JSON Parsing")
    class JsonParsingTests {

        @Test
        @DisplayName("Should parse from JSON string")
        void shouldParseFromJson() throws IOException {
            String json = """
                {
                  "turnTimeSettings": [30, 20, 30, 20],
                  "buildingFootprintWidths": [2, 2, 3],
                  "buildingFootprintHeights": [2, 2, 3],
                  "buildingPowerRadius": [10, 20, 30, 40, 60, 127],
                  "rankExpThresholds": [20, 35, 50],
                  "rankCreditRewards": [10, 25, 51],
                  "rankBonusPoints": [0, 3, 6],
                  "battleTimeLimits": [300, 600, 900, 1200]
                }
                """;
            GameConfig config = GameConfig.parseJson(json);
            assertArrayEquals(new int[]{30, 20, 30, 20}, config.getTurnTimeSettings());
            assertArrayEquals(new int[]{20, 35, 50}, config.getRankExpThresholds());
            assertArrayEquals(new int[]{300, 600, 900, 1200}, config.getBattleTimeLimits());
        }

        @Test
        @DisplayName("Should load from file")
        void shouldLoadFromFile() throws IOException {
            String json = """
                {
                  "turnTimeSettings": [60, 30, 15],
                  "buildingFootprintWidths": [2],
                  "buildingFootprintHeights": [2],
                  "buildingPowerRadius": [10],
                  "rankExpThresholds": [25, 40, 60],
                  "rankCreditRewards": [15, 30, 55],
                  "rankBonusPoints": [0, 5, 10],
                  "battleTimeLimits": [300]
                }
                """;
            Path tempFile = Files.createTempFile("game_config_test", ".json");
            try {
                Files.writeString(tempFile, json);
                GameConfig config = GameConfig.loadFromFile(tempFile);
                assertArrayEquals(new int[]{60, 30, 15}, config.getTurnTimeSettings());
                assertArrayEquals(new int[]{25, 40, 60}, config.getRankExpThresholds());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    @DisplayName("Singleton")
    class SingletonTests {

        @Test
        @DisplayName("Should return same instance from getInstance")
        void shouldReturnSameInstance() {
            GameConfig a = GameConfig.getInstance();
            GameConfig b = GameConfig.getInstance();
            assertSame(a, b);
        }

        @Test
        @DisplayName("Should reset singleton and create new instance")
        void shouldResetSingleton() {
            GameConfig first = GameConfig.getInstance();
            GameConfig.resetInstance();
            GameConfig second = GameConfig.getInstance();
            // Both are loaded from same resource, so they should be equal but not same
            assertEquals(first, second);
        }

        @Test
        @DisplayName("Should set custom instance for testing")
        void shouldSetCustomInstance() {
            GameConfig custom = GameConfig.builder()
                .turnTimeSettings(new int[]{99})
                .build();
            GameConfig.setInstance(custom);
            assertSame(custom, GameConfig.getInstance());
            assertArrayEquals(new int[]{99}, GameConfig.getInstance().getTurnTimeSettings());
        }
    }

    @Nested
    @DisplayName("Defensive Copies")
    class DefensiveCopyTests {

        @Test
        @DisplayName("Should return defensive copies of arrays")
        void shouldReturnDefensiveCopies() {
            GameConfig config = GameConfig.builder().build();
            int[] original = config.getTurnTimeSettings();
            original[0] = 999;
            assertNotEquals(999, config.getTurnTimeSettings()[0]);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            GameConfig a = GameConfig.builder().build();
            GameConfig b = GameConfig.builder().build();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            GameConfig a = GameConfig.builder().build();
            GameConfig b = GameConfig.builder().turnTimeSettings(new int[]{99}).build();
            assertNotEquals(a, b);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("Should include key fields in toString")
        void shouldIncludeKeyFields() {
            GameConfig config = GameConfig.builder().build();
            String str = config.toString();
            assertTrue(str.contains("turnTimeSettings"));
            assertTrue(str.contains("rankExpThresholds"));
            assertTrue(str.contains("battleTimeLimits"));
        }
    }
}
