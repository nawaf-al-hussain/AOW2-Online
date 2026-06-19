package com.aow2.client.editor;

import com.aow2.client.editor.TilePainter.BrushSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TilePainter.BrushSize enum: sizes, ordering, values.
 * Pure enum test — no JavaFX dependency.
 */
class TilePainterBrushSizeTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("has exactly 3 values")
        void hasThreeValues() {
            assertEquals(3, BrushSize.values().length);
        }

        @Test
        @DisplayName("contains SMALL")
        void containsSmall() {
            assertNotNull(BrushSize.valueOf("SMALL"));
        }

        @Test
        @DisplayName("contains MEDIUM")
        void containsMedium() {
            assertNotNull(BrushSize.valueOf("MEDIUM"));
        }

        @Test
        @DisplayName("contains LARGE")
        void containsLarge() {
            assertNotNull(BrushSize.valueOf("LARGE"));
        }
    }

    @Nested
    @DisplayName("Size Values")
    class SizeValues {

        @Test
        @DisplayName("SMALL has size 1")
        void smallSize() {
            assertEquals(1, BrushSize.SMALL.getSize());
        }

        @Test
        @DisplayName("MEDIUM has size 3")
        void mediumSize() {
            assertEquals(3, BrushSize.MEDIUM.getSize());
        }

        @Test
        @DisplayName("LARGE has size 5")
        void largeSize() {
            assertEquals(5, BrushSize.LARGE.getSize());
        }
    }

    @Nested
    @DisplayName("Size Progression")
    class SizeProgression {

        @Test
        @DisplayName("sizes increase from SMALL to LARGE")
        void sizesIncrease() {
            assertTrue(BrushSize.SMALL.getSize() < BrushSize.MEDIUM.getSize());
            assertTrue(BrushSize.MEDIUM.getSize() < BrushSize.LARGE.getSize());
        }

        @Test
        @DisplayName("all sizes are positive")
        void allSizesPositive() {
            for (BrushSize size : BrushSize.values()) {
                assertTrue(size.getSize() > 0,
                    () -> size.name() + " should have positive size");
            }
        }

        @Test
        @DisplayName("all sizes are odd numbers")
        void allSizesOdd() {
            for (BrushSize size : BrushSize.values()) {
                assertEquals(1, size.getSize() % 2,
                    () -> size.name() + " should have odd size");
            }
        }
    }

    @Nested
    @DisplayName("Ordinal Values")
    class OrdinalValues {

        @Test
        @DisplayName("SMALL has ordinal 0")
        void smallOrdinal() {
            assertEquals(0, BrushSize.SMALL.ordinal());
        }

        @Test
        @DisplayName("MEDIUM has ordinal 1")
        void mediumOrdinal() {
            assertEquals(1, BrushSize.MEDIUM.ordinal());
        }

        @Test
        @DisplayName("LARGE has ordinal 2")
        void largeOrdinal() {
            assertEquals(2, BrushSize.LARGE.ordinal());
        }
    }

    @Nested
    @DisplayName("Value Of Edge Cases")
    class ValueOfEdgeCases {

        @Test
        @DisplayName("valueOf is case sensitive")
        void valueOfCaseSensitive() {
            assertThrows(IllegalArgumentException.class, () -> BrushSize.valueOf("small"));
            assertThrows(IllegalArgumentException.class, () -> BrushSize.valueOf("Medium"));
        }

        @Test
        @DisplayName("valueOf with invalid name throws")
        void valueOfInvalidThrows() {
            assertThrows(IllegalArgumentException.class, () -> BrushSize.valueOf("EXTRA_LARGE"));
            assertThrows(IllegalArgumentException.class, () -> BrushSize.valueOf(""));
        }
    }
}
