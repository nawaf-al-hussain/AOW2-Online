package com.aow2.core.ai;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI military decision-making: attack, defend, retreat, flank, harass.
 * <p>
 * REF: ai_analysis.md — AI military priorities:
 * 1. Defend base when under attack
 * 2. Attack when military advantage > 1.5x
 * 3. Harass enemy economy when possible
 * 4. Retreat when outnumbered
 * <p>
 * REF: ai_analysis.md — AI target selection uses spatial hash grid search.
 * Units prioritize targets based on:
 * - Building priority (max HP = priority value)
 * - Distance (closer targets preferred when priorities equal)
 * - Visibility (only targets in fog-of-war visible area)
 * - Infantry vs Machinery targeting preferences
 * <p>
 * REF: ai_analysis.md — AI combat preferences:
 * Infantry (1,2,3) → targets other infantry
 * Light Machinery (4,21) → scouts/raids, hit-and-run
 * Heavy Machinery (7,16) → buildings/heavy, siege warfare
 * Artillery (19,20) → area targets, long-range bombardment
 */
public final class MilitaryAI {

    private static final Logger LOG = LoggerFactory.getLogger(MilitaryAI.class);

    /** Military advantage threshold to trigger attack. REF: ai_analysis.md — advantage > 1.5x */
    private static final double ATTACK_ADVANTAGE_THRESHOLD = 1.5;

    /** Military disadvantage threshold to trigger retreat. ASSUMPTION: retreat when ratio < 0.5 */
    private static final double RETREAT_DISADVANTAGE_THRESHOLD = 0.5;

    /** Maximum number of units for a harassment group. REF: ai_analysis.md — light machinery for raids */
    private static final int HARASS_GROUP_SIZE = 3;

    /** Distance threshold for base defense. */
    private static final int BASE_DEFENSE_DISTANCE = 20;

    /**
     * Assess military strength comparison between AI and enemy.
     * <p>
     * Calculates the ratio of AI military strength to enemy military strength.
     * Strength is measured as total HP * damage of all alive units.
     * <p>
     * REF: ai_analysis.md — AI counts HP in range for military decisions.
     * "totalHP += ca[unitRef + 1616]" from target search mode 2.
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @return ratio of AI strength to enemy strength. >1.0 means AI has advantage
     */
    public double assessMilitaryAdvantage(EntityManager entities, int playerId) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;

        double aiStrength = calculateMilitaryStrength(entities, aiFaction);
        double enemyStrength = calculateMilitaryStrength(entities, enemyFaction);

        if (enemyStrength <= 0) {
            return Double.MAX_VALUE; // No enemy military
        }
        return aiStrength / enemyStrength;
    }

    /**
     * Decide the best military action for this tick.
     * <p>
     * Decision priority (REF: ai_analysis.md):
     * 1. Defend base when under attack
     * 2. Attack when military advantage > 1.5x
     * 3. Harass enemy economy when possible with light units
     * 4. Hold position when no clear action
     * 5. Retreat when outnumbered
     *
     * @param entities the entity manager
     * @param map      the game map
     * @param playerId the AI player ID
     * @return the military action to execute
     */
    public MilitaryAction decideAction(EntityManager entities, GameMap map, int playerId) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;

        List<Unit> aiUnits = entities.getAliveUnitsForPlayer(aiFaction);
        List<Unit> enemyUnits = entities.getAliveUnitsForPlayer(enemyFaction);

        // 1. Check if base is under attack — defend
        GridPosition basePosition = findBasePosition(entities, playerId);
        if (basePosition != null && isBaseUnderAttack(entities, playerId, basePosition)) {
            List<Integer> defenderIds = selectDefenders(aiUnits, basePosition);
            if (!defenderIds.isEmpty()) {
                LOG.debug("Player {} defending base at {}", playerId, basePosition);
                return new MilitaryAction.Defend(basePosition, defenderIds);
            }
        }

        // Calculate military advantage
        double advantage = assessMilitaryAdvantage(entities, playerId);

        // 2. Retreat when outnumbered
        if (advantage < RETREAT_DISADVANTAGE_THRESHOLD && !aiUnits.isEmpty()) {
            GridPosition rallyPoint = basePosition != null ? basePosition : new GridPosition(50, 50);
            List<Integer> retreatingIds = aiUnits.stream()
                .filter(Unit::isAlive)
                .map(Unit::getId)
                .toList();
            LOG.debug("Player {} retreating to {} (advantage={})", playerId, rallyPoint, advantage);
            return new MilitaryAction.Retreat(rallyPoint, retreatingIds);
        }

        // 3. Attack when military advantage > 1.5x
        if (advantage >= ATTACK_ADVANTAGE_THRESHOLD && !aiUnits.isEmpty()) {
            GridPosition attackTarget = findAttackTarget(entities, map, playerId);
            if (attackTarget != null) {
                int desiredSize = Math.max(5, aiUnits.size() / 2);
                List<Unit> attackGroup = selectAttackGroup(entities, playerId, desiredSize);
                List<Integer> attackIds = attackGroup.stream().map(Unit::getId).toList();
                if (!attackIds.isEmpty()) {
                    LOG.debug("Player {} attacking {} with {} units (advantage={})",
                        playerId, attackTarget, attackIds.size(), advantage);
                    return new MilitaryAction.Attack(attackTarget, attackIds);
                }
            }
        }

        // 4. Harass enemy economy with small group if possible
        if (aiUnits.size() >= 8 && hasFastUnits(aiUnits)) {
            GridPosition harassTarget = findHarassTarget(entities, playerId);
            if (harassTarget != null) {
                List<Unit> harassGroup = selectHarassGroup(aiUnits);
                if (!harassGroup.isEmpty()) {
                    List<Integer> harassIds = harassGroup.stream().map(Unit::getId).toList();
                    LOG.debug("Player {} harassing {} with {} units",
                        playerId, harassTarget, harassIds.size());
                    return new MilitaryAction.Harass(harassTarget, harassIds);
                }
            }
        }

        // 5. Hold position — no clear action
        List<Integer> idleUnitIds = aiUnits.stream()
            .filter(u -> u.getMovementState() == MovementState.IDLE)
            .map(Unit::getId)
            .toList();
        if (!idleUnitIds.isEmpty()) {
            return new MilitaryAction.HoldPosition(idleUnitIds);
        }

        return new MilitaryAction.HoldPosition(List.of());
    }

    /**
     * Select units for an attack group.
     * <p>
     * REF: ai_analysis.md — AI prioritizes: vehicles > infantry, healthy > damaged.
     * Selects the strongest units available up to the desired group size.
     *
     * @param entities    the entity manager
     * @param playerId    the AI player ID
     * @param desiredSize the desired number of units in the attack group
     * @return list of units selected for the attack
     */
    public List<Unit> selectAttackGroup(EntityManager entities, int playerId, int desiredSize) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Unit> units = entities.getAliveUnitsForPlayer(faction);

        // Sort: vehicles first, then by health percentage (healthy first), then by damage
        List<Unit> sorted = new ArrayList<>(units);
        sorted.sort(Comparator
            .comparing((Unit u) -> u.isVehicle() ? 0 : 1) // Vehicles first
            .thenComparing(Comparator.comparingDouble((Unit u) -> (double) u.getHp() / u.getMaxHp()).reversed()) // Healthy first
            .thenComparing(Comparator.comparingInt((Unit u) -> u.getStats().damage()).reversed()) // High damage
        );

        return sorted.stream()
            .limit(desiredSize)
            .toList();
    }

    /**
     * Find the best attack target (weakest enemy building or unit cluster).
     * <p>
     * REF: ai_analysis.md — target selection priorities:
     * - Buildings have priority based on max HP (bS[bT[53] + unitType])
     * - Closer targets preferred when priorities equal
     * - Search through spatial hash grid
     *
     * @param entities the entity manager
     * @param map      the game map
     * @param playerId the AI player ID
     * @return the best target position, or null if no targets
     */
    public GridPosition findAttackTarget(EntityManager entities, GameMap map, int playerId) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;

        // Priority 1: Enemy Command Centre (highest priority)
        List<Building> enemyBuildings = entities.getBuildingsForPlayer(enemyFaction);
        Building bestTarget = null;
        int bestPriority = -1;
        double bestDistance = Double.MAX_VALUE;
        GridPosition aiBase = findBasePosition(entities, playerId);

        for (Building building : enemyBuildings) {
            if (!building.isAlive()) continue;

            // REF: ai_analysis.md — building priority = max HP
            int priority = building.getMaxHp();
            double distance = aiBase != null ? building.getPosition().distanceTo(aiBase) : 0;

            // Higher priority wins; on tie, closer wins
            if (priority > bestPriority || (priority == bestPriority && distance < bestDistance)) {
                bestPriority = priority;
                bestDistance = distance;
                bestTarget = building;
            }
        }

        if (bestTarget != null) {
            return bestTarget.getPosition();
        }

        // Priority 2: Largest cluster of enemy units
        List<Unit> enemyUnits = entities.getAliveUnitsForPlayer(enemyFaction);
        if (!enemyUnits.isEmpty()) {
            // Find the centroid of enemy units as the attack target
            double sumX = 0, sumY = 0;
            for (Unit unit : enemyUnits) {
                sumX += unit.getPosition().x();
                sumY += unit.getPosition().y();
            }
            int centroidX = (int) (sumX / enemyUnits.size());
            int centroidY = (int) (sumY / enemyUnits.size());
            centroidX = Math.clamp(centroidX, 0, 127);
            centroidY = Math.clamp(centroidY, 0, 127);
            return new GridPosition(centroidX, centroidY);
        }

        return null;
    }

    /**
     * Calculate military strength for a faction.
     * Strength = sum of (HP * damage) for all alive units.
     *
     * @param entities the entity manager
     * @param faction  the faction
     * @return the total military strength value
     */
    private double calculateMilitaryStrength(EntityManager entities, Faction faction) {
        List<Unit> units = entities.getAliveUnitsForPlayer(faction);
        double strength = 0;
        for (Unit unit : units) {
            // REF: ai_analysis.md — strength measured by HP and damage
            strength += (double) unit.getHp() * unit.getStats().damage();
        }
        // Add defensive building strength
        List<Building> buildings = entities.getBuildingsForPlayer(faction);
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().isDefensive()) {
                strength += building.getHp() * 0.5; // Defensive buildings add half their HP
            }
        }
        return strength;
    }

    /**
     * Find the position of the player's primary base (first Command Centre).
     *
     * @param entities the entity manager
     * @param playerId the player ID
     * @return the base position, or null if no base
     */
    private GridPosition findBasePosition(EntityManager entities, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Building> buildings = entities.getBuildingsForPlayer(faction);
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().isHQ()) {
                return building.getPosition();
            }
        }
        return null;
    }

    /**
     * Check if the base is under attack by enemy units.
     * <p>
     * REF: ai_analysis.md — AI defends base when enemy units are nearby
     *
     * @param entities    the entity manager
     * @param playerId    the player ID
     * @param basePosition the base position
     * @return true if enemy units are within defense distance
     */
    private boolean isBaseUnderAttack(EntityManager entities, int playerId, GridPosition basePosition) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;

        List<Unit> enemyUnits = entities.getAliveUnitsForPlayer(enemyFaction);
        for (Unit enemy : enemyUnits) {
            if (enemy.getPosition().distanceTo(basePosition) <= BASE_DEFENSE_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Select units for base defense.
     * Picks the closest units to the base, prioritizing vehicles.
     *
     * @param aiUnits      the AI player's alive units
     * @param basePosition the position to defend
     * @return list of unit IDs for defense
     */
    private List<Integer> selectDefenders(List<Unit> aiUnits, GridPosition basePosition) {
        return aiUnits.stream()
            .sorted(Comparator
                .comparing((Unit u) -> u.isVehicle() ? 0 : 1) // Vehicles first
                .thenComparingDouble(u -> u.getPosition().distanceTo(basePosition)) // Closest first
            )
            .limit(10) // ASSUMPTION: max 10 defenders
            .map(Unit::getId)
            .toList();
    }

    /**
     * Check if the AI has fast units suitable for harassment.
     * REF: ai_analysis.md — light machinery (types 4, 21) used for scouts/raids
     */
    private boolean hasFastUnits(List<Unit> aiUnits) {
        return aiUnits.stream().anyMatch(u -> u.isVehicle());
    }

    /**
     * Find a harassment target (enemy economy building).
     * REF: ai_analysis.md — harass enemy economy when possible
     */
    private GridPosition findHarassTarget(EntityManager entities, int playerId) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);
        Faction enemyFaction = aiFaction == Faction.CONFEDERATION ? Faction.RESISTANCE : Faction.CONFEDERATION;

        // Target enemy production buildings (Infantry Centre, Machine Factory)
        List<Building> enemyBuildings = entities.getBuildingsForPlayer(enemyFaction);
        for (Building building : enemyBuildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().producesUnits()) {
                return building.getPosition();
            }
        }
        return null;
    }

    /**
     * Select a small group of fast units for harassment.
     * REF: ai_analysis.md — light machinery for hit-and-run
     */
    private List<Unit> selectHarassGroup(List<Unit> aiUnits) {
        return aiUnits.stream()
            .filter(Unit::isVehicle)
            .limit(HARASS_GROUP_SIZE)
            .toList();
    }
}
