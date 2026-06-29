package com.aow2.client.input;

import com.aow2.client.input.InputHandler.CommandMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InputHandler.CommandMode enum: values, naming.
 * Pure enum test — no JavaFX dependency.
 */
class InputHandlerCommandModeTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("has exactly 5 values")  // FIX (F-06): Added GARRISON mode
        void hasFourValues() {
            assertEquals(5, CommandMode.values().length);
        }

        @Test
        @DisplayName("contains NORMAL mode")
        void containsNormal() {
            assertNotNull(CommandMode.valueOf("NORMAL"));
        }

        @Test
        @DisplayName("contains ATTACK_MOVE mode")
        void containsAttackMove() {
            assertNotNull(CommandMode.valueOf("ATTACK_MOVE"));
        }

        @Test
        @DisplayName("contains PATROL mode")
        void containsPatrol() {
            assertNotNull(CommandMode.valueOf("PATROL"));
        }

        @Test
        @DisplayName("contains BUILD_PLACEMENT mode")
        void containsBuildPlacement() {
            assertNotNull(CommandMode.valueOf("BUILD_PLACEMENT"));
        }
    }

    @Nested
    @DisplayName("Value Of Edge Cases")
    class ValueOfEdgeCases {

        @Test
        @DisplayName("valueOf is case sensitive")
        void valueOfCaseSensitive() {
            assertThrows(IllegalArgumentException.class, () -> CommandMode.valueOf("normal"));
            assertThrows(IllegalArgumentException.class, () -> CommandMode.valueOf("Attack_Move"));
        }

        @Test
        @DisplayName("valueOf with invalid name throws")
        void valueOfInvalidThrows() {
            assertThrows(IllegalArgumentException.class, () -> CommandMode.valueOf("INVALID"));
            assertThrows(IllegalArgumentException.class, () -> CommandMode.valueOf(""));
        }
    }

    @Nested
    @DisplayName("Iteration")
    class Iteration {

        @ParameterizedTest(name = "{0} is iterable")
        @EnumSource(CommandMode.class)
        @DisplayName("all values are iterable")
        void allValuesIterable(CommandMode mode) {
            assertNotNull(mode);
            assertNotNull(mode.name());
        }

        @Test
        @DisplayName("ordinal values are sequential")
        void ordinalValuesSequential() {
            for (int i = 0; i < CommandMode.values().length; i++) {
                assertEquals(i, CommandMode.values()[i].ordinal());
            }
        }
    }
}
