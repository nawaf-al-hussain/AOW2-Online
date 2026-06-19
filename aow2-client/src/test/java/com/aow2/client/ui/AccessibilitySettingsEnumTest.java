package com.aow2.client.ui;

import com.aow2.client.ui.AccessibilitySettings.ColorblindMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AccessibilitySettings.ColorblindMode enum.
 * Tests only the enum itself (no JavaFX required).
 */
class AccessibilitySettingsEnumTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("has exactly 4 modes")
        void hasFourModes() {
            assertEquals(4, ColorblindMode.values().length);
        }

        @Test
        @DisplayName("contains NONE")
        void containsNone() {
            assertNotNull(ColorblindMode.valueOf("NONE"));
        }

        @Test
        @DisplayName("contains PROTANOPIA")
        void containsProtanopia() {
            assertNotNull(ColorblindMode.valueOf("PROTANOPIA"));
        }

        @Test
        @DisplayName("contains DEUTERANOPIA")
        void containsDeuteranopia() {
            assertNotNull(ColorblindMode.valueOf("DEUTERANOPIA"));
        }

        @Test
        @DisplayName("contains TRITANOPIA")
        void containsTritanopia() {
            assertNotNull(ColorblindMode.valueOf("TRITANOPIA"));
        }
    }

    @Nested
    @DisplayName("Display Names")
    class DisplayNames {

        @Test
        @DisplayName("NONE displays as 'None'")
        void noneDisplay() {
            assertEquals("None", ColorblindMode.NONE.toString());
        }

        @Test
        @DisplayName("PROTANOPIA displays descriptive name")
        void protanopiaDisplay() {
            String display = ColorblindMode.PROTANOPIA.toString();
            assertTrue(display.contains("Protanopia"));
            assertTrue(display.contains("Red-Blind"));
        }

        @Test
        @DisplayName("DEUTERANOPIA displays descriptive name")
        void deuteranopiaDisplay() {
            String display = ColorblindMode.DEUTERANOPIA.toString();
            assertTrue(display.contains("Deuteranopia"));
            assertTrue(display.contains("Green-Blind"));
        }

        @Test
        @DisplayName("TRITANOPIA displays descriptive name")
        void tritanopiaDisplay() {
            String display = ColorblindMode.TRITANOPIA.toString();
            assertTrue(display.contains("Tritanopia"));
            assertTrue(display.contains("Blue-Blind"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("valueOf is case sensitive")
        void valueOfCaseSensitive() {
            assertThrows(IllegalArgumentException.class, () -> ColorblindMode.valueOf("none"));
            assertThrows(IllegalArgumentException.class, () -> ColorblindMode.valueOf("protanopia"));
        }

        @Test
        @DisplayName("valueOf with invalid name throws")
        void valueOfInvalidThrows() {
            assertThrows(IllegalArgumentException.class, () -> ColorblindMode.valueOf("INVALID"));
        }

        @Test
        @DisplayName("all modes have non-empty display names")
        void allModesHaveDisplayNames() {
            for (ColorblindMode mode : ColorblindMode.values()) {
                String display = mode.toString();
                assertNotNull(display);
                assertFalse(display.isEmpty(),
                    () -> mode.name() + " should have a non-empty display name");
            }
        }

        @Test
        @DisplayName("display names contain the colorblind type name")
        void displayNamesContainTypeName() {
            for (ColorblindMode mode : ColorblindMode.values()) {
                if (mode != ColorblindMode.NONE) {
                    String display = mode.toString();
                    assertTrue(display.contains("Blind"),
                        () -> mode.name() + " display name should contain 'Blind'");
                }
            }
        }
    }
}
