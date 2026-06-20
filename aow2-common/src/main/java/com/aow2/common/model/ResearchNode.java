package com.aow2.common.model;

import java.util.List;

/**
 * Represents a research/tech tree node in the technology tree.
 * There are 48 research IDs (0-47), with asymmetric effects between Confederation and Resistance.
 * REF: combat_formulas.md section "Research/Upgrade Effects" - 48 research IDs
 * REF: complete_unit_stats.json technologies section
 *
 * @param id               unique research identifier (0-47)
 * @param name             human-readable research name
 * @param faction          the faction that can research this node
 * @param description      lore/functional description
 * @param cost             credit cost to start this research
 * @param researchTime     ticks required to complete this research
 * @param prerequisites    list of prerequisite research IDs (anyOf semantics — any one satisfies the requirement), empty if none
 * @param category         the research category branch
 * @param effect           the stat modification this research applies
 */
public record ResearchNode(
    int id,
    String name,
    Faction faction,
    String description,
    int cost,
    int researchTime,
    List<Integer> prerequisites,
    ResearchCategory category,
    ResearchEffect effect
) {
    /**
     * Compact constructor validating research node fields.
     */
    public ResearchNode {
        if (id < 0 || id > 47) {
            throw new IllegalArgumentException("Research ID must be 0-47, got: " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (faction == null) {
            throw new IllegalArgumentException("faction must not be null");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("cost must not be negative, got: " + cost);
        }
        if (researchTime < 0) {
            throw new IllegalArgumentException("researchTime must not be negative, got: " + researchTime);
        }
        if (prerequisites == null) {
            prerequisites = List.of();
        }
        for (int p : prerequisites) {
            if (p < -1 || p > 47) {
                throw new IllegalArgumentException("prerequisite ID must be -1 or 0-47, got: " + p);
            }
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (effect == null) {
            throw new IllegalArgumentException("effect must not be null");
        }
        // Defensive copy
        prerequisites = List.copyOf(prerequisites);
        if (description == null) {
            description = "";
        }
    }

    /**
     * Returns whether this research node has any prerequisites.
     *
     * @return true if at least one prerequisite research is required
     * @deprecated FIX (L-NEW-2): Use {@code !prerequisites().isEmpty()} directly — the
     * record accessor already provides this. This method adds no value.
     */
    @Deprecated(since = "2026-06-21", forRemoval = true)
    public boolean hasPrerequisite() {
        return !prerequisites.isEmpty();
    }

    /**
     * Returns an unmodifiable view of the prerequisite IDs.
     * Any one of these IDs being completed satisfies the requirement (anyOf semantics).
     *
     * @return list of prerequisite research IDs
     * @deprecated FIX (L-NEW-2): Use {@code prerequisites()} directly — the record accessor
     * already returns an unmodifiable list (created via List.copyOf in compact constructor).
     */
    @Deprecated(since = "2026-06-21", forRemoval = true)
    public List<Integer> getPrerequisites() {
        return prerequisites;
    }
}
