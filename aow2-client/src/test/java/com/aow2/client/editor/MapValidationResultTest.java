package com.aow2.client.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MapValidationResult: error/warning tracking, immutability, validity checks.
 */
class MapValidationResultTest {

    private MapValidationResult result;

    @BeforeEach
    void setUp() {
        result = new MapValidationResult();
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("should be valid when empty")
        void shouldBeValidWhenEmpty() {
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should have no warnings when empty")
        void shouldHaveNoWarningsWhenEmpty() {
            assertFalse(result.hasWarnings());
        }

        @Test
        @DisplayName("should have zero total issues")
        void shouldHaveZeroTotalIssues() {
            assertEquals(0, result.totalIssues());
        }

        @Test
        @DisplayName("should return empty error list")
        void shouldReturnEmptyErrorList() {
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("should return empty warning list")
        void shouldReturnEmptyWarningList() {
            assertTrue(result.getWarnings().isEmpty());
        }
    }

    @Nested
    @DisplayName("Error Management")
    class ErrorManagement {

        @Test
        @DisplayName("adding an error makes result invalid")
        void addingErrorMakesInvalid() {
            result.addError("Missing starting position");
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("adding multiple errors accumulates them")
        void addingMultipleErrors() {
            result.addError("Error 1");
            result.addError("Error 2");
            result.addError("Error 3");
            assertEquals(3, result.getErrors().size());
            assertTrue(result.getErrors().contains("Error 1"));
            assertTrue(result.getErrors().contains("Error 2"));
            assertTrue(result.getErrors().contains("Error 3"));
        }

        @Test
        @DisplayName("errors should count toward total issues")
        void errorsCountTowardTotalIssues() {
            result.addError("E1");
            result.addError("E2");
            assertEquals(2, result.totalIssues());
        }
    }

    @Nested
    @DisplayName("Warning Management")
    class WarningManagement {

        @Test
        @DisplayName("adding a warning sets hasWarnings")
        void addingWarningSetsHasWarnings() {
            result.addWarning("Starting positions too close");
            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("warnings should not affect validity")
        void warningsDoNotAffectValidity() {
            result.addWarning("Some warning");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("multiple warnings accumulate")
        void multipleWarningsAccumulate() {
            result.addWarning("W1");
            result.addWarning("W2");
            assertEquals(2, result.getWarnings().size());
        }
    }

    @Nested
    @DisplayName("Combined Errors and Warnings")
    class CombinedErrorsAndWarnings {

        @Test
        @DisplayName("total issues counts both errors and warnings")
        void totalIssuesCountsBoth() {
            result.addError("E1");
            result.addError("E2");
            result.addWarning("W1");
            result.addWarning("W2");
            result.addWarning("W3");
            assertEquals(5, result.totalIssues());
        }

        @Test
        @DisplayName("errors and warnings are independent")
        void errorsAndWarningsIndependent() {
            result.addError("E1");
            result.addWarning("W1");
            assertFalse(result.isValid());
            assertTrue(result.hasWarnings());
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("getErrors returns unmodifiable list")
        void getErrorsReturnsUnmodifiableList() {
            result.addError("E1");
            List<String> errors = result.getErrors();
            assertThrows(UnsupportedOperationException.class, () -> errors.add("hack"));
        }

        @Test
        @DisplayName("getWarnings returns unmodifiable list")
        void getWarningsReturnsUnmodifiableList() {
            result.addWarning("W1");
            List<String> warnings = result.getWarnings();
            assertThrows(UnsupportedOperationException.class, () -> warnings.add("hack"));
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("toString includes counts")
        void toStringIncludesCounts() {
            result.addError("E1");
            result.addWarning("W1");
            String str = result.toString();
            assertTrue(str.contains("errors=1"));
            assertTrue(str.contains("warnings=1"));
            assertTrue(str.contains("valid=false"));
        }

        @Test
        @DisplayName("toString shows valid=true when no errors")
        void toStringShowsValidWhenNoErrors() {
            result.addWarning("W1");
            String str = result.toString();
            assertTrue(str.contains("valid=true"));
        }
    }
}
