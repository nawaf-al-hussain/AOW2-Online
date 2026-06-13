package com.aow2.common.model;

/**
 * Represents a research/tech tree node in the technology tree.
 * There are 48 research IDs (0-47), with 8 base researches per faction
 * and asymmetric effects between Confederation and Resistance.
 * REF: combat_formulas.md section "Research/Upgrade Effects" - 48 research IDs
 * REF: complete_unit_stats.json technologies section
 *
 * @param id              unique research identifier (0-47)
 * @param name            human-readable research name
 * @param faction         the faction that can research this node
 * @param description     lore/functional description
 * @param cost            credit cost to start this research
 * @param researchTime    ticks required to complete this research
 * @param prerequisiteId  ID of prerequisite research, or -1 if none
 * @param category        the research category branch
 * @param effect          the stat modification this research applies
 */
public record ResearchNode(
    int id,
    String name,
    Faction faction,
    String description,
    int cost,
    int researchTime,
    int prerequisiteId,
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
        if (prerequisiteId < -1 || prerequisiteId > 47) {
            throw new IllegalArgumentException("prerequisiteId must be -1 or 0-47, got: " + prerequisiteId);
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (effect == null) {
            throw new IllegalArgumentException("effect must not be null");
        }
    }

    /**
     * Returns whether this research node has a prerequisite.
     *
     * @return true if a prerequisite research is required
     */
    public boolean hasPrerequisite() {
        return prerequisiteId >= 0;
    }
}
