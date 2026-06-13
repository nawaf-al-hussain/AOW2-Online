package com.aow2.client.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of map validation for playability.
 * Contains lists of errors (must fix) and warnings (should fix).
 * <p>
 * REF: phases.md Phase 9 - map validation: missing starting positions,
 * unreachable areas, invalid building placement, etc.
 */
public final class MapValidationResult {

    /** Validation errors — map cannot be played with these issues. */
    private final List<String> errors;

    /** Validation warnings — map can be played but may have issues. */
    private final List<String> warnings;

    /**
     * Constructs an empty validation result.
     */
    public MapValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Adds an error to the result.
     *
     * @param error the error description
     */
    public void addError(String error) {
        errors.add(error);
    }

    /**
     * Adds a warning to the result.
     *
     * @param warning the warning description
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Returns whether the map is valid (no errors).
     *
     * @return true if no errors exist
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Returns whether the map has any warnings.
     *
     * @return true if warnings exist
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Returns an unmodifiable list of errors.
     *
     * @return error list
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Returns an unmodifiable list of warnings.
     *
     * @return warning list
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Returns the total number of issues (errors + warnings).
     *
     * @return total issue count
     */
    public int totalIssues() {
        return errors.size() + warnings.size();
    }

    @Override
    public String toString() {
        return "MapValidationResult{errors=" + errors.size() +
               ", warnings=" + warnings.size() +
               ", valid=" + isValid() + "}";
    }
}
