package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Attack commands by issuing attack orders to units.
 * <p>
 * REF: combat_formulas.md - attack cycle and targeting system
 * REF: protocol_specification.md - Attack command
 */
public final class AttackCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AttackCommandHandler.class);

    /**
     * Handle an Attack command.
     * Sets the target reference for all specified units and transitions them to attack state.
     * If the target is a building, uses negative reference ID per original game convention.
     *
     * @param cmd       the attack command
     * @param entities  the entity manager
     * @param combat    the combat system
     */
    public void handle(CommandType.Attack cmd, EntityManager entities,
                       CombatSystem combat) {
        // FIX (ANALYSIS_V2 P2): Ownership check — only the owning player can command units to attack
        for (int unitId : cmd.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                int ownerId = com.aow2.core.economy.EconomySystem.playerId(unit.getFaction());
                if (ownerId != cmd.playerId()) {
                    LOG.warn("Player {} attempted to attack with unit {} owned by player {}",
                        cmd.playerId(), unitId, ownerId);
                    return;
                }
            }
        }
        // Determine target reference: positive for units, negative for buildings
        int targetRef;
        Unit targetUnit = entities.getUnit(cmd.targetId());
        if (targetUnit != null) {
            targetRef = cmd.targetId();
        } else {
            Building targetBuilding = entities.getBuilding(cmd.targetId());
            if (targetBuilding != null) {
                targetRef = -cmd.targetId(); // Negative for buildings
            } else {
                LOG.warn("Attack target {} not found", cmd.targetId());
                return;
            }
        }

        for (int unitId : cmd.unitIds()) {
            Unit unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                unit.setTargetUnitRef(targetRef);
                unit.setAttackState(1); // Begin approaching target
                // If in range, immediately set to attack state 3
                if (targetUnit != null && isInAttackRange(unit, targetUnit, combat)) {
                    unit.setAttackState(3);
                }
                LOG.debug("Unit {} attacking target {}", unitId, cmd.targetId());
            }
        }
    }

    /**
     * Check if a unit is within attack range of a target unit.
     *
     * @param attacker the attacking unit
     * @param target   the target unit
     * @param combat   the combat system
     * @return true if in attack range
     */
    private boolean isInAttackRange(Unit attacker, Unit target, CombatSystem combat) {
        int range = combat.getEffectiveAttackRange(attacker);
        // Use Chebyshev distance (max of axis distances) for grid-based range checks,
        // consistent with the original game's distance class system.
        var aPos = attacker.getPosition();
        var tPos = target.getPosition();
        int dx = Math.abs(aPos.x() - tPos.x());
        int dy = Math.abs(aPos.y() - tPos.y());
        return Math.max(dx, dy) <= range;
    }
}
