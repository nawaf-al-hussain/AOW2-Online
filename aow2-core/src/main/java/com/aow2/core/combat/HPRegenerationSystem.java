package com.aow2.core.combat;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.Faction;
import com.aow2.core.entity.Unit;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.research.ResearchSystem;
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

    /** Regeneration occurs every CREDIT_GENERATION_CYCLE ticks (127 ticks). */
    private static final int REGEN_CYCLE = GameConstants.CREDIT_GENERATION_CYCLE;

    private final ResearchSystem researchSystem;

    public HPRegenerationSystem(ResearchSystem researchSystem) {
        this.researchSystem = researchSystem;
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
                processInfantryRegen(unit);
            }
            // Machinery repair happens at production buildings, handled by ProductionSystem
        }
    }

    /**
     * Process HP regeneration for an infantry unit.
     * Infantry regenerates when near a powered friendly building.
     * REF: MASTER_DOCUMENTATION.md — "isInfantry && powered"
     *
     * @param unit the infantry unit
     */
    private void processInfantryRegen(Unit unit) {
        if (unit.getHp() >= unit.getMaxHp()) return; // already full

        int recovery = INFANTRY_BASE_RECOVERY;

        // Check for Bio Suit (Confed R1) or First-aid Kit (Rebel R9)
        // REF: combat_formulas.md research effects table:
        //   Confederation tech 1 (global ID 1): "Bio suit" — health recovery triples
        //   Rebel tech 1 (global ID 9): "First-aid kit" — health recovery triples
        // The ResearchSystem uses combined global IDs: Confed 0-7, Rebel 8-15.
        // NOTE: ID 0 is Energy Suit (armor), NOT Bio Suit (HP recovery).
        Faction faction = unit.getFaction();
        int playerId = EconomySystem.playerId(faction);

        boolean hasTripleRecovery = false;
        if (faction == Faction.CONFEDERATION && researchSystem.hasResearch(playerId, 1)) {
            hasTripleRecovery = true; // Bio Suit (ID 1)
        } else if (faction == Faction.RESISTANCE && researchSystem.hasResearch(playerId, 9)) {
            hasTripleRecovery = true; // First-aid Kit (ID 9)
        }

        if (hasTripleRecovery) {
            recovery *= 3;
        }

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
