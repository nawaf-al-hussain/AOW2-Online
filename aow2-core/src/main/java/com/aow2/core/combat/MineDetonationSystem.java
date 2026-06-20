package com.aow2.core.combat;

import com.aow2.common.event.BuildingDestroyedEvent;
import com.aow2.common.model.GridPosition;
import com.aow2.common.event.DamageAppliedEvent;
import com.aow2.common.event.UnitKilledEvent;
import com.aow2.common.model.Faction;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Mine;
import com.aow2.core.entity.Unit;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles mine proximity detection and detonation.
 * REF: MASTER_DOCUMENTATION.md lines 1781-1829 — mine types and trigger behavior
 * REF: unit_stats.md lines 258-272 — mine trigger mechanics
 * REF: combat_formulas.md — mines use nuclear/explosion area damage formula
 *
 * Mine Scorpio (Type 11): Anti-tank — triggers ONLY on enemy machinery
 * Mine Frog (Type 9): Proximity-triggered — area damage within 1 cell
 * Mine Lizard (Type 10): Proximity-triggered — multi-charge, can have siege mode
 */
public final class MineDetonationSystem {
    private static final Logger LOG = LoggerFactory.getLogger(MineDetonationSystem.class);

    /** Mine Frog area damage radius. REF: unit_stats.md — "within 1 cell" */
    private static final int FROG_BLAST_RADIUS = 1;

    /** Mine Lizard multi-charge count. REF: unit_stats.md — "Fragments and additional charges" */
    private static final int LIZARD_CHARGES = 2;

    // UNVERIFIED (L-3): Arm delay of 10 ticks is assumed — RE confirms arm delay exists but not the exact tick count.
    // ASSUMPTION (L10): 10-tick arm delay — RE spec confirms mines have an arm delay but doesn't specify exact tick count
    // The original game's arm delay could be shorter or longer. This affects how quickly mines become active after placement.
    // REF: unit_stats.md lines 258-272 — mine trigger mechanics
    private static final int ARM_DELAY_TICKS = 10;

    private final CombatSystem combatSystem;

    public MineDetonationSystem(CombatSystem combatSystem) {
        this.combatSystem = combatSystem;
    }

    /**
     * Process mine proximity checks for all armed mines.
     * Checks if any enemy unit is within trigger radius of each armed mine.
     *
     * @param entities the entity manager
     * @param state    the game state
     */
    public void processTick(EntityManager entities, GameState state) {
        List<Mine> toDetonate = new ArrayList<>();

        for (Mine mine : entities.getAllMines()) {
            if (!mine.isArmed() || mine.isTriggered() || !mine.isAlive()) continue;

            // Check if any enemy unit is within trigger radius
            Faction enemyFaction = (mine.getFaction() == Faction.CONFEDERATION)
                ? Faction.RESISTANCE : Faction.CONFEDERATION;

            boolean shouldDetonate = false;
            Unit triggerUnit = null;

            for (Unit enemy : entities.getAliveUnitsForPlayer(enemyFaction)) {
                if (mine.checkTrigger(enemy.getPosition())) {
                    // Type-specific trigger logic
                    if (shouldTriggerMine(mine, enemy)) {
                        shouldDetonate = true;
                        triggerUnit = enemy;
                        break;
                    }
                }
            }

            if (shouldDetonate && triggerUnit != null) {
                toDetonate.add(mine);
            }
        }

        // Detonate all triggered mines
        for (Mine mine : toDetonate) {
            detonateMine(mine, entities, state);
        }
    }

    /**
     * Check if a specific mine type should trigger for a given enemy unit.
     * REF: unit_stats.md — Mine Scorpio only triggers on machinery
     *
     * @param mine  the mine
     * @param enemy the potential trigger unit
     * @return true if the mine should detonate
     */
    private boolean shouldTriggerMine(Mine mine, Unit enemy) {
        UnitType mineType = mine.getMineType();

        if (mineType == UnitType.CONFED_MINE_SCORPIO) {
            // REF: unit_stats.md — "Detonates only when enemy machines are nearby"
            // Anti-tank mine: only triggers on machinery (vehicles and special machinery)
            return enemy.isVehicle() || enemy.isMachinery();
        }

        // Mine Frog and Mine Lizard: trigger on any enemy unit (infantry or machinery)
        return true;
    }

    /**
     * Detonate a mine, dealing damage based on mine type.
     * REF: combat_formulas.md — mines use nuclear/explosion area damage formula
     *
     * @param mine     the mine to detonate
     * @param entities the entity manager
     * @param state    the game state
     */
    private void detonateMine(Mine mine, EntityManager entities, GameState state) {
        int damage = mine.detonate();
        if (damage <= 0) return;

        Faction enemyFaction = (mine.getFaction() == Faction.CONFEDERATION)
            ? Faction.RESISTANCE : Faction.CONFEDERATION;

        UnitType mineType = mine.getMineType();

        if (mineType == UnitType.CONFED_MINE_FROG) {
            // REF: unit_stats.md — "damaging infantry and machinery within 1 cell"
            // Area damage with 1-cell radius
            applyAreaDamage(mine, damage, FROG_BLAST_RADIUS, entities, state, enemyFaction);
        } else if (mineType == UnitType.CONFED_MINE_LIZARD) {
            // REF: unit_stats.md — "Fragments and additional charges scatter"
            // Multi-charge: apply damage LIZARD_CHARGES times to simulate
            // multiple detonation waves (jumps before detonation).
            for (int wave = 0; wave < LIZARD_CHARGES; wave++) {
                applyAreaDamage(mine, damage, FROG_BLAST_RADIUS, entities, state, enemyFaction);
            }
        } else {
            // Mine Scorpio: single-target anti-tank
            // Damage is applied to the triggering unit directly
            applyDirectDamage(mine, damage, entities, state, enemyFaction);
        }
    }

    /**
     * Apply area damage from a mine detonation.
     * REF: combat_formulas.md — nuclear/explosion area damage formula
     *
     * @param mine         the detonating mine
     * @param damage       base damage from mine stats
     * @param radius       blast radius in tiles
     * @param entities     entity manager
     * @param state        game state
     * @param enemyFaction faction to damage
     */
    private void applyAreaDamage(Mine mine, int damage, int radius,
                                  EntityManager entities, GameState state, Faction enemyFaction) {
        var pos = mine.getPosition();

        for (Unit enemy : entities.getAliveUnitsForPlayer(enemyFaction)) {
            int dx = enemy.getPosition().x() - pos.x();
            int dy = enemy.getPosition().y() - pos.y();
            int dist = Math.max(Math.abs(dx), Math.abs(dy)); // Chebyshev distance

            if (dist <= radius) {
                int targetArmor = combatSystem.getArmorCalculator().calculateEffectiveArmor(
                    enemy, combatSystem.getCompletedResearch(enemy));
                int actualDamage = DamageCalculator.calculateNuclearDamage(damage, targetArmor, dist);
                enemy.takeDamage(actualDamage);
                state.enqueueEvent(new DamageAppliedEvent(
                    state.currentTick(), enemy.getId(), actualDamage, enemy.getHp(), mine.getId()));

                if (!enemy.isAlive()) {
                    // FIX (L6/C-8): Store death anim frame for client rendering
                    enemy.setDeathAnimFrame(
                        DamageCalculator.calculateDeathAnimationFrame(enemy, 4));
                    state.enqueueEvent(new UnitKilledEvent(
                        state.currentTick(), enemy.getId(), enemy.getUnitType(), mine.getId()));
                }
            }
        }

        // Also damage enemy buildings in radius
        for (Building building : entities.getBuildingsForPlayer(enemyFaction)) {
            if (!building.isAlive()) continue;
            int dx = building.getPosition().x() - pos.x();
            int dy = building.getPosition().y() - pos.y();
            int dist = Math.max(Math.abs(dx), Math.abs(dy));

            if (dist <= radius) {
                int buildingArmor = 0; // Buildings have 0 base armor
                int actualDamage = DamageCalculator.calculateNuclearDamage(damage, buildingArmor, dist);
                building.takeDamage(actualDamage);

                if (!building.isAlive()) {
                    state.enqueueEvent(new BuildingDestroyedEvent(
                        state.currentTick(), building.getId(), building.getBuildingType(), mine.getId()));
                }
            }
        }
    }

    /**
     * Apply direct damage from a mine detonation (single-target anti-tank).
     *
     * @param mine         the detonating mine
     * @param damage       base damage from mine stats
     * @param entities     entity manager
     * @param state        game state
     * @param enemyFaction faction to damage
     */
    private void applyDirectDamage(Mine mine, int damage, EntityManager entities,
                                    GameState state, Faction enemyFaction) {
        // Find the closest enemy machinery unit within trigger radius
        Unit closest = null;
        int closestDist = Integer.MAX_VALUE;

        for (Unit enemy : entities.getAliveUnitsForPlayer(enemyFaction)) {
            if (!enemy.isVehicle() && !enemy.isMachinery()) continue; // Scorpio = anti-tank only

            int dist = GridPosition.distanceClass(
                enemy.getPosition().x() - mine.getPosition().x(),
                enemy.getPosition().y() - mine.getPosition().y());
            if (dist <= mine.getTriggerRadius() && dist < closestDist) {
                closest = enemy;
                closestDist = dist;
            }
        }

        if (closest != null) {
            int targetArmor = combatSystem.getArmorCalculator().calculateEffectiveArmor(
                closest, combatSystem.getCompletedResearch(closest));
            int actualDamage = DamageCalculator.calculateDamage(damage, targetArmor);
            closest.takeDamage(actualDamage);
            state.enqueueEvent(new DamageAppliedEvent(
                state.currentTick(), closest.getId(), actualDamage, closest.getHp(), mine.getId()));

            if (!closest.isAlive()) {
                // FIX (L6/C-8): Store death anim frame for client rendering
                closest.setDeathAnimFrame(
                    DamageCalculator.calculateDeathAnimationFrame(closest, 4));
                state.enqueueEvent(new UnitKilledEvent(
                    state.currentTick(), closest.getId(), closest.getUnitType(), mine.getId()));
            }
        }
    }
}
