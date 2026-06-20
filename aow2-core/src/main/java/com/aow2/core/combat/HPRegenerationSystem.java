package com.aow2.core.combat;

import com.aow2.common.config.GameConstants;
import com.aow2.core.entity.Unit;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.PowerSystem;
import com.aow2.core.world.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles HP regeneration for infantry and machinery repair.
 * REF: MASTER_DOCUMENTATION.md lines 750-767 — infantry HP recovery
 * REF: combat_formulas.md lines 259-277 — HP recovery mechanics
 *
 * Infantry regenerate when powered (near a powered building).
 * Bio Suit (R1) / First-aid Kit (R9) triples recovery rate.
 * Machinery repair requires proximity to a production building.
 */
public final class HPRegenerationSystem {
    private static final Logger LOG = LoggerFactory.getLogger(HPRegenerationSystem.class);

    /** Base HP recovery per regeneration cycle for infantry. ASSUMPTION: 1 HP per cycle */
    private static final int INFANTRY_BASE_RECOVERY = 1;

    /** Base HP repair per regeneration cycle for machinery at a production building. ASSUMPTION: 2 HP per cycle */
    private static final int MACHINERY_BASE_REPAIR = 2;

    /** Regeneration occurs every CREDIT_GENERATION_CYCLE ticks (128 ticks). */
    private static final int REGEN_CYCLE = GameConstants.CREDIT_GENERATION_CYCLE;

    /** Power system for proximity checks. Set after construction. */
    private PowerSystem powerSystem;

    public HPRegenerationSystem() {
    }

    /**
     * Set the power system for power proximity checks.
     * @param powerSystem the power system
     */
    public void setPowerSystem(PowerSystem powerSystem) {
        this.powerSystem = powerSystem;
    }

    /**
     * Process HP regeneration for all units.
     * Called from the tick loop. Only regenerates on the correct cycle tick.
     * REF: MASTER_DOCUMENTATION.md — infantry regen when powered
     *
     * @param entities the entity manager
     * @param currentTick the current game tick
     */
    public void processTick(EntityManager entities, int currentTick) {
        if (currentTick % REGEN_CYCLE != 0) return;

        for (Unit unit : entities.getAllUnits()) {
            if (!unit.isAlive()) continue;

            if (unit.isInfantry()) {
                processInfantryRegen(unit, entities);
            }
            // Machinery repair happens at production buildings, handled by ProductionSystem
        }
    }

    /**
     * Process HP regeneration for an infantry unit.
     * Infantry regenerates only when near a powered friendly building.
     * FIX: Added power proximity check using PowerSystem.isPositionPowered().
     * REF: MASTER_DOCUMENTATION.md — "isInfantry && powered"
     * <p>
     * NOTE: The original game's 48 research effects (combat_formulas.md IDs 0-47)
     * do not include any HP recovery boost research. The previous implementation
     * incorrectly referenced IDs 1 ("Player 0 attack range reduction /3") and
     * 9 ("Infantry armour +2 for heavy types") as HP recovery boosts.
     * Base recovery rate is used for all infantry until RE data confirms otherwise.
     *
     * @param unit the infantry unit
     * @param entities the entity manager (for power system queries)
     */
    private void processInfantryRegen(Unit unit, EntityManager entities) {
        if (unit.getHp() >= unit.getMaxHp()) return; // already full

        // FIX: Check if unit is near a powered friendly building before regenerating.
        // REF: MASTER_DOCUMENTATION.md — "isInfantry && powered"
        if (powerSystem != null) {
            int playerId = EconomySystem.playerId(unit.getFaction());
            boolean powered = powerSystem.isPositionPowered(unit.getPosition(), playerId, entities);
            if (!powered) return; // No regeneration without power
        }

        int recovery = INFANTRY_BASE_RECOVERY;

        // NOTE: No research in the RE spec (IDs 0-47) affects HP recovery rate.
        // Previous code incorrectly used ID 1 (Confed) and ID 9 (Rebel) for this.
        // If future RE analysis identifies HP recovery research, add it here.

        unit.heal(recovery);
    }

    /**
     * Repair a machinery unit at a production building.
     * Called by ProductionSystem when a machinery unit is near a factory.
     * REF: combat_formulas.md — machinery repair rate tripled by repair research
     *
     * @param unit the machinery unit to repair
     * @param playerId the player who owns the unit
     */
    public void repairMachinery(Unit unit, int playerId) {
        if (!unit.isAlive() || !unit.isMachinery()) return;
        if (unit.getHp() >= unit.getMaxHp()) return;

        int repair = MACHINERY_BASE_REPAIR;
        // REF: combat_formulas.md — repair research triples repair rate
        // Confederation ID 1 (Bio suit) and Rebel ID 9 (First-aid kit)
        // are the infantry HP research; machinery repair uses different IDs.
        // For now, use base repair rate. ASSUMPTION: exact repair research IDs not confirmed

        unit.heal(repair);
    }
}
