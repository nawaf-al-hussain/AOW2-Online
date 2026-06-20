package com.aow2.core.ai;

import com.aow2.common.model.GridPosition;

import java.util.List;

/**
 * Sealed interface representing AI military decisions.
 * <p>
 * REF: ai_analysis.md — AI military priorities:
 * 1. Defend base when under attack
 * 2. Attack when military advantage > 1.5x
 * 3. Harass enemy economy when possible
 * 4. Retreat when outnumbered
 * 5. Hold position when no clear action
 * <p>
 * Uses Java 21 sealed classes and records for exhaustive pattern matching.
 */
public sealed interface MilitaryAction permits
    MilitaryAction.Attack,
    MilitaryAction.Defend,
    MilitaryAction.Retreat,
    MilitaryAction.HoldPosition,
    MilitaryAction.Harass {

    /**
     * Attack a specific target position with selected units.
     * REF: ai_analysis.md — AI attacks when military advantage > 1.5x
     *
     * @param target  the grid position to attack (enemy building or unit cluster)
     * @param unitIds the IDs of units assigned to this attack
     */
    record Attack(GridPosition target, List<Integer> unitIds) implements MilitaryAction {}

    /**
     * Defend a specific position against incoming attack.
     * REF: ai_analysis.md — defend base when under attack
     *
     * @param defendPoint the position to defend (usually near base)
     * @param unitIds     the IDs of units assigned to defense
     */
    record Defend(GridPosition defendPoint, List<Integer> unitIds) implements MilitaryAction {}

    /**
     * Retreat units to a rally point.
     * REF: ai_analysis.md — retreat when outnumbered
     *
     * @param rallyPoint the position to retreat to (usually near base)
     * @param unitIds    the IDs of units to retreat
     */
    record Retreat(GridPosition rallyPoint, List<Integer> unitIds) implements MilitaryAction {}

    /**
     * Hold current positions with specified units.
     * Used when no clear military action is needed.
     *
     * @param unitIds the IDs of units to hold position
     */
    record HoldPosition(List<Integer> unitIds) implements MilitaryAction {}

    /**
     * Harass enemy economy with a small strike force.
     * REF: ai_analysis.md — harass enemy economy when possible
     * REF: ai_analysis.md — light machinery (types 4, 21) used for scouts/raids
     *
     * @param target  the target position (enemy economy buildings)
     * @param unitIds the IDs of units for the harassment
     */
    record Harass(GridPosition target, List<Integer> unitIds) implements MilitaryAction {}
}
