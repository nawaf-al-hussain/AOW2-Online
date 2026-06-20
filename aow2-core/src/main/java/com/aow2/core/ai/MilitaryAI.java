package com.aow2.core.ai;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.MovementState;
import com.aow2.common.model.UnitCategory;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.FogOfWarSystem;
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
 * <p>
 * FOG OF WAR: All enemy-related decisions use visibility-filtered data.
 * The AI only "knows" about enemy entities it can currently see (VISIBLE tiles).
 * For entities in EXPLORED or UNEXPLORED tiles, the AI acts as if they don't exist.
 * The AI always has full knowledge of its own units and buildings (friendly fog is always visible).
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

    /** Distance threshold for considering siege mode activation (attack range + buffer). */
    private static final int SIEGE_PROXIMITY_BUFFER = 2;

    /** Distance threshold for considering garrison (units within this range of a bunker). */
    private static final int GARRISON_DISTANCE = 3;

    /**
     * Assess military strength comparison between AI and enemy.
     * <p>
     * Calculates the ratio of AI military strength to enemy military strength.
     * Strength is measured as total HP * damage of all alive units.
     * <p>
     * REF: ai_analysis.md — AI counts HP in range for military decisions.
     * "totalHP += ca[unitRef + 1616]" from target search mode 2.
     * <p>
     * FOG OF WAR: Only counts VISIBLE enemy units and buildings. If fogOfWar is null,
     * full information is used (for testing).
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return ratio of AI strength to enemy strength. >1.0 means AI has advantage
     */
    public double assessMilitaryAdvantage(EntityManager entities, int playerId, FogOfWarSystem fogOfWar) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);

        // AI always has full knowledge of its own forces (friendly fog is always visible)
        double aiStrength = calculateMilitaryStrength(entities, aiFaction, fogOfWar, playerId);
        double enemyStrength = calculateEnemyMilitaryStrength(entities, playerId, fogOfWar);

        if (enemyStrength <= 0) {
            return Double.MAX_VALUE; // No visible enemy military
        }
        return aiStrength / enemyStrength;
    }

    /**
     * Assess military strength comparison without fog of war (full information).
     * Provided for backward compatibility.
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @return ratio of AI strength to enemy strength
     */
    public double assessMilitaryAdvantage(EntityManager entities, int playerId) {
        return assessMilitaryAdvantage(entities, playerId, null);
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
     * <p>
     * FOG OF WAR: Only considers VISIBLE enemy entities. The AI cannot react to
     * threats it cannot see, and cannot attack targets in unexplored territory.
     *
     * @param entities the entity manager
     * @param map      the game map
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return the military action to execute
     */
    public MilitaryAction decideAction(EntityManager entities, GameMap map, int playerId, FogOfWarSystem fogOfWar) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);

        // AI always has full knowledge of its own units
        List<Unit> aiUnits = entities.getAliveUnitsForPlayer(aiFaction);

        // Only consider VISIBLE enemy units for decision-making
        List<Unit> visibleEnemyUnits = entities.getVisibleEnemyUnitsForPlayer(playerId, fogOfWar);

        // 1. Check if base is under attack — defend (only react to visible enemies)
        GridPosition basePosition = findBasePosition(entities, playerId);
        if (basePosition != null && isBaseUnderAttack(visibleEnemyUnits, basePosition)) {
            List<Integer> defenderIds = selectDefenders(aiUnits, basePosition);
            if (!defenderIds.isEmpty()) {
                LOG.debug("Player {} defending base at {} (visible enemies nearby)", playerId, basePosition);
                return new MilitaryAction.Defend(basePosition, defenderIds);
            }
        }

        // Calculate military advantage based on visible enemy forces only
        double advantage = assessMilitaryAdvantage(entities, playerId, fogOfWar);

        // 2. Retreat when outnumbered (based on visible enemy strength)
        if (advantage < RETREAT_DISADVANTAGE_THRESHOLD && !aiUnits.isEmpty()) {
            GridPosition rallyPoint = basePosition != null ? basePosition : new GridPosition(50, 50);
            List<Integer> retreatingIds = aiUnits.stream()
                .filter(Unit::isAlive)
                .map(Unit::getId)
                .toList();
            LOG.debug("Player {} retreating to {} (visible advantage={})", playerId, rallyPoint, advantage);
            return new MilitaryAction.Retreat(rallyPoint, retreatingIds);
        }

        // 3. Attack when military advantage > 1.5x (only target visible enemies)
        if (advantage >= ATTACK_ADVANTAGE_THRESHOLD && !aiUnits.isEmpty()) {
            GridPosition attackTarget = findAttackTarget(entities, map, playerId, fogOfWar);
            if (attackTarget != null) {
                int desiredSize = Math.max(5, aiUnits.size() / 2);
                List<Unit> attackGroup = selectAttackGroup(entities, playerId, desiredSize);
                List<Integer> attackIds = attackGroup.stream().map(Unit::getId).toList();
                if (!attackIds.isEmpty()) {
                    LOG.debug("Player {} attacking {} with {} units (visible advantage={})",
                        playerId, attackTarget, attackIds.size(), advantage);
                    return new MilitaryAction.Attack(attackTarget, attackIds);
                }
            }
        }

        // 4. Harass enemy economy with small group if possible (only visible targets)
        if (aiUnits.size() >= 8 && hasFastUnits(aiUnits)) {
            GridPosition harassTarget = findHarassTarget(entities, playerId, fogOfWar);
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
     * Decide the best military action without fog of war (full information).
     * Provided for backward compatibility.
     *
     * @param entities the entity manager
     * @param map      the game map
     * @param playerId the AI player ID
     * @return the military action to execute
     */
    public MilitaryAction decideAction(EntityManager entities, GameMap map, int playerId) {
        return decideAction(entities, map, playerId, null);
    }

    /**
     * Score a target preference for an attacker unit vs an enemy unit.
     * <p>
     * REF: ai_analysis.md — AI combat preferences:
     * Infantry (1,2,3) → targets other infantry
     * Light Machinery (4,21) → scouts/raids, hit-and-run
     * Heavy Machinery (7,16) → buildings/heavy, siege warfare
     * Artillery (19,20) → area targets, long-range bombardment
     *
     * @param attacker the attacking unit
     * @param target  the enemy unit to evaluate
     * @return preference score (higher = more preferred target)
     */
    public double scoreTargetPreference(Unit attacker, Unit target) {
        double score = 100.0; // base score

        // Distance factor: prefer closer targets
        int distance = GridPosition.distanceClass(
            attacker.getPosition().x() - target.getPosition().x(),
            attacker.getPosition().y() - target.getPosition().y());
        score -= distance * 2.0;

        // Prefer damaged targets (finish off weakened enemies)
        if (target.getHp() < target.getMaxHp()) {
            score += 20.0 * (1.0 - (double) target.getHp() / target.getMaxHp());
        }

        // Per-category targeting preferences (REF: ai_analysis.md)
        UnitCategory attackerCat = attacker.getUnitType().category();
        UnitCategory targetCat = target.getUnitType().category();

        if (attackerCat == UnitCategory.INFANTRY) {
            // Infantry strongly prefer targeting other infantry
            if (targetCat == UnitCategory.INFANTRY) {
                score += 50.0;
            }
            // Slight preference against heavy vehicles (ineffective)
            if (targetCat == UnitCategory.VEHICLE) {
                score -= 15.0;
            }
        } else if (attackerCat == UnitCategory.VEHICLE) {
            // Vehicles (light) prefer targeting infantry (raiding)
            if (targetCat == UnitCategory.INFANTRY) {
                score += 30.0;
            }
            // Heavy vehicles prefer targeting other vehicles
            if (isHeavyVehicle(attacker)) {
                if (targetCat == UnitCategory.VEHICLE) {
                    score += 40.0;
                }
                // Heavy vehicles are good against buildings
                if (!isHeavyVehicle(target) && targetCat != UnitCategory.INFANTRY) {
                    score += 10.0;
                }
            }
        } else if (attackerCat == UnitCategory.SPECIAL_MACHINERY) {
            // Special machinery (artillery-like) prefer buildings and clustered enemies
            if (targetCat == UnitCategory.INFANTRY) {
                score += 20.0;
            }
        }

        return score;
    }

    /**
     * Score a target preference for an attacker unit vs an enemy building.
     * <p>
     * REF: ai_analysis.md — Heavy Machinery targets buildings, Artillery bombards.
     *
     * @param attacker the attacking unit
     * @param target   the enemy building to evaluate
     * @return preference score (higher = more preferred target)
     */
    public double scoreTargetPreference(Unit attacker, Building target) {
        double score = 80.0; // base score for buildings (valuable targets)

        // Distance factor: prefer closer targets
        int distance = GridPosition.distanceClass(
            attacker.getPosition().x() - target.getPosition().x(),
            attacker.getPosition().y() - target.getPosition().y());
        score -= distance * 2.0;

        // Prefer damaged buildings
        if (target.getHp() < target.getMaxHp()) {
            score += 25.0 * (1.0 - (double) target.getHp() / target.getMaxHp());
        }

        // Heavy vehicles and artillery prefer buildings (siege warfare)
        UnitCategory attackerCat = attacker.getUnitType().category();
        if (attackerCat == UnitCategory.VEHICLE && isHeavyVehicle(attacker)) {
            score += 40.0; // Heavy machinery excels at destroying buildings
        }

        // Prefer production buildings (economic damage)
        if (target.getBuildingType().producesUnits()) {
            score += 15.0;
        }

        // Prefer HQ over other buildings
        if (target.getBuildingType().isHQ()) {
            score += 10.0;
        }

        return score;
    }

    /**
     * Find the best individual target position for a specific unit.
     * Uses per-unit targeting preferences to pick the optimal enemy.
     * <p>
     * FOG OF WAR: Only considers VISIBLE enemy entities.
     *
     * @param unit     the unit seeking a target
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return the best target position, or null if no visible targets
     */
    public GridPosition findBestTargetForUnit(Unit unit, EntityManager entities,
                                               int playerId, FogOfWarSystem fogOfWar) {
        List<Unit> visibleEnemies = entities.getVisibleEnemyUnitsForPlayer(playerId, fogOfWar);
        List<Building> visibleEnemyBuildings = entities.getVisibleEnemyBuildingsForPlayer(playerId, fogOfWar);

        GridPosition bestPos = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Unit enemy : visibleEnemies) {
            if (!enemy.isAlive() || enemy.isGarrisoned()) continue;
            double score = scoreTargetPreference(unit, enemy);
            if (score > bestScore) {
                bestScore = score;
                bestPos = enemy.getPosition();
            }
        }

        for (Building building : visibleEnemyBuildings) {
            if (!building.isAlive()) continue;
            double score = scoreTargetPreference(unit, building);
            if (score > bestScore) {
                bestScore = score;
                bestPos = building.getPosition();
            }
        }

        return bestPos;
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
            .comparing((Unit u) -> u.isMachinery() ? 0 : 1) // Machinery (vehicles + SPECIAL_MACHINERY) first
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
     * FOG OF WAR: Only considers VISIBLE enemy buildings and units.
     * The AI cannot attack targets it cannot see.
     * <p>
     * REF: ai_analysis.md — target selection priorities:
     * - Buildings have priority based on max HP (bS[bT[53] + unitType])
     * - Closer targets preferred when priorities equal
     * - Search through spatial hash grid
     * - Unit-type targeting preferences (H-11)
     *
     * @param entities the entity manager
     * @param map      the game map
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return the best target position, or null if no visible targets
     */
    public GridPosition findAttackTarget(EntityManager entities, GameMap map, int playerId, FogOfWarSystem fogOfWar) {
        Faction aiFaction = EconomySystem.playerFaction(playerId);
        List<Unit> aiUnits = entities.getAliveUnitsForPlayer(aiFaction);

        // Only consider VISIBLE enemy buildings
        List<Building> visibleEnemyBuildings = entities.getVisibleEnemyBuildingsForPlayer(playerId, fogOfWar);
        Building bestTarget = null;
        double bestCompositeScore = -1;
        GridPosition aiBase = findBasePosition(entities, playerId);

        for (Building building : visibleEnemyBuildings) {
            if (!building.isAlive()) continue;

            // REF: ai_analysis.md — building priority = max HP
            int priority = building.getMaxHp();
            int distance = aiBase != null
                ? GridPosition.distanceClass(
                    building.getPosition().x() - aiBase.x(),
                    building.getPosition().y() - aiBase.y()) : 0;

            // H-11: Compute composite score using per-unit targeting preferences.
            // Sum up how well this building matches the AI's force composition.
            double preferenceBonus = 0;
            for (Unit aiUnit : aiUnits) {
                if (aiUnit.isAlive() && !aiUnit.isGarrisoned()) {
                    preferenceBonus += scoreTargetPreference(aiUnit, building);
                }
            }
            // Normalize: only consider preference bonus when multiple units benefit
            if (!aiUnits.isEmpty()) {
                preferenceBonus /= aiUnits.size();
            }

            double compositeScore = priority + preferenceBonus;

            // Distance tiebreaker: prefer closer
            if (distance > 0) {
                compositeScore -= distance * 0.5;
            }

            if (compositeScore > bestCompositeScore) {
                bestCompositeScore = compositeScore;
                bestTarget = building;
            }
        }

        if (bestTarget != null) {
            return bestTarget.getPosition();
        }

        // Only consider VISIBLE enemy units
        List<Unit> visibleEnemyUnits = entities.getVisibleEnemyUnitsForPlayer(playerId, fogOfWar);
        if (!visibleEnemyUnits.isEmpty()) {
            // H-11: Find the centroid of visible enemy units weighted by targeting preference.
            // Units that are better targets for the AI's force get more weight.
            double sumX = 0, sumY = 0, totalWeight = 0;
            for (Unit enemy : visibleEnemyUnits) {
                if (!enemy.isAlive() || enemy.isGarrisoned()) continue;

                // Compute preference weight across all AI units
                double weight = 1.0; // base weight
                for (Unit aiUnit : aiUnits) {
                    if (aiUnit.isAlive() && !aiUnit.isGarrisoned()) {
                        weight += scoreTargetPreference(aiUnit, enemy) * 0.01;
                    }
                }
                sumX += enemy.getPosition().x() * weight;
                sumY += enemy.getPosition().y() * weight;
                totalWeight += weight;
            }
            if (totalWeight > 0) {
                int centroidX = Math.clamp((int) (sumX / totalWeight), 0, 127);
                int centroidY = Math.clamp((int) (sumY / totalWeight), 0, 127);
                return new GridPosition(centroidX, centroidY);
            }
        }

        return null;
    }

    /**
     * Find siege mode decisions for AI units.
     * <p>
     * REF: ai_analysis.md — units with siege capability auto-enter siege mode when enemies are nearby.
     * - Enable siege mode when enemy is within attack range + SIEGE_PROXIMITY_BUFFER tiles
     * - Disable siege mode when no enemies within sight range (for movement)
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return list of SiegeDecision records indicating which units should toggle siege mode
     */
    public List<SiegeDecision> findSiegeDecisions(EntityManager entities, int playerId, FogOfWarSystem fogOfWar) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Unit> aiUnits = entities.getAliveUnitsForPlayer(faction);
        List<Unit> visibleEnemies = entities.getVisibleEnemyUnitsForPlayer(playerId, fogOfWar);
        List<SiegeDecision> decisions = new ArrayList<>();

        for (Unit unit : aiUnits) {
            if (!unit.canSiege() || unit.isGarrisoned()) continue;

            // Find nearest visible enemy
            int nearestEnemyDist = Integer.MAX_VALUE;
            for (Unit enemy : visibleEnemies) {
                if (!enemy.isAlive()) continue;
                int dist = GridPosition.distanceClass(
                    unit.getPosition().x() - enemy.getPosition().x(),
                    unit.getPosition().y() - enemy.getPosition().y());
                nearestEnemyDist = Math.min(nearestEnemyDist, dist);
            }

            int siegeRange = unit.getStats().attackRange() + SIEGE_PROXIMITY_BUFFER;
            int sightRange = unit.getStats().sightRange();

            if (!unit.isSiegeMode()) {
                // Enable siege mode if enemy within attack range + buffer
                if (nearestEnemyDist <= siegeRange) {
                    decisions.add(new SiegeDecision(unit.getId(), true));
                }
            } else {
                // Disable siege mode if no enemies within sight range (allow movement)
                if (nearestEnemyDist > sightRange) {
                    decisions.add(new SiegeDecision(unit.getId(), false));
                }
            }
        }

        return decisions;
    }

    /**
     * Find garrison decisions for idle AI infantry.
     * <p>
     * REF: ai_analysis.md — AI garrisons units in bunkers/towers for defense.
     * - Only garrison idle infantry (lower priority than combat)
     * - Only garrison when not actively attacking/defending
     * - Bunker must be nearby (within GARRISON_DISTANCE tiles) and have capacity
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @return list of GarrisonDecision records indicating which units should garrison where
     */
    public List<GarrisonDecision> findGarrisonDecisions(EntityManager entities, int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        List<Unit> aiUnits = entities.getAliveUnitsForPlayer(faction);
        List<Building> aiBuildings = entities.getBuildingsForPlayer(faction);
        List<GarrisonDecision> decisions = new ArrayList<>();

        for (Building building : aiBuildings) {
            if (!building.isAlive() || building.isUnderConstruction()) continue;
            if (!building.getBuildingType().isDefensive()) continue;
            // Only bunker-type buildings can garrison (not walls/rocket launchers that lack garrison)
            if (building.getGarrisonedUnitRef() != null) continue;

            // Find the closest idle infantry within garrison range
            Unit bestCandidate = null;
            int bestDist = Integer.MAX_VALUE;

            for (Unit unit : aiUnits) {
                if (!unit.isAlive() || !unit.isInfantry()) continue;
                if (unit.isGarrisoned()) continue;
                if (unit.getMovementState() != MovementState.IDLE) continue;

                int dist = GridPosition.distanceClass(
                    unit.getPosition().x() - building.getPosition().x(),
                    unit.getPosition().y() - building.getPosition().y());
                if (dist <= GARRISON_DISTANCE && dist < bestDist) {
                    bestDist = dist;
                    bestCandidate = unit;
                }
            }

            if (bestCandidate != null) {
                decisions.add(new GarrisonDecision(bestCandidate.getId(), building.getId()));
            }
        }

        return decisions;
    }

    /**
     * Check if a unit is a heavy vehicle (higher HP threshold).
     * Heavy vehicles target other vehicles and buildings.
     *
     * @param unit the unit to check
     * @return true if this is a heavy vehicle
     */
    private boolean isHeavyVehicle(Unit unit) {
        return unit.getUnitType().category() == UnitCategory.VEHICLE
            && unit.getStats().hp() >= 60;
    }

    /**
     * Find the best attack target without fog of war (full information).
     * Provided for backward compatibility.
     *
     * @param entities the entity manager
     * @param map      the game map
     * @param playerId the AI player ID
     * @return the best target position, or null if no targets
     */
    public GridPosition findAttackTarget(EntityManager entities, GameMap map, int playerId) {
        return findAttackTarget(entities, map, playerId, null);
    }

    /**
     * Calculate military strength for a faction.
     * Strength = sum of (HP * damage) for all alive units + defensive buildings.
     * <p>
     * For friendly faction: always uses full data (friendly fog is always visible).
     * For enemy faction: use {@link #calculateEnemyMilitaryStrength} instead.
     *
     * @param entities the entity manager
     * @param faction  the faction
     * @param fogOfWar the fog of war system (unused for friendly faction, always full info)
     * @param playerId the player ID (for determining friendly vs enemy)
     * @return the total military strength value
     */
    private double calculateMilitaryStrength(EntityManager entities, Faction faction, FogOfWarSystem fogOfWar, int playerId) {
        // Friendly units: always full visibility
        List<Unit> units = entities.getAliveUnitsForPlayer(faction);
        double strength = 0;
        for (Unit unit : units) {
            // REF: ai_analysis.md — strength measured by HP and damage
            strength += (double) unit.getHp() * unit.getStats().damage();
        }
        // Add defensive building strength (friendly buildings always visible)
        List<Building> buildings = entities.getBuildingsForPlayer(faction);
        for (Building building : buildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().isDefensive()) {
                strength += building.getHp() * 0.5; // Defensive buildings add half their HP
            }
        }
        return strength;
    }

    /**
     * Calculate military strength of the enemy faction based on visibility.
     * Only counts VISIBLE enemy units and buildings. If fogOfWar is null,
     * full information is used.
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return the total visible enemy military strength
     */
    private double calculateEnemyMilitaryStrength(EntityManager entities, int playerId, FogOfWarSystem fogOfWar) {
        // Only count visible enemy units
        List<Unit> visibleEnemyUnits = entities.getVisibleEnemyUnitsForPlayer(playerId, fogOfWar);
        double strength = 0;
        for (Unit unit : visibleEnemyUnits) {
            strength += (double) unit.getHp() * unit.getStats().damage();
        }

        // Only count visible enemy defensive buildings
        List<Building> visibleEnemyBuildings = entities.getVisibleEnemyBuildingsForPlayer(playerId, fogOfWar);
        for (Building building : visibleEnemyBuildings) {
            if (building.isAlive() && !building.isUnderConstruction() && building.getBuildingType().isDefensive()) {
                strength += building.getHp() * 0.5;
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
     * Check if the base is under attack by visible enemy units.
     * <p>
     * FOG OF WAR: Only considers VISIBLE enemy units. The AI cannot react to
     * enemies it cannot see (realistic behavior).
     * <p>
     * REF: ai_analysis.md — AI defends base when enemy units are nearby
     *
     * @param visibleEnemyUnits the list of visible enemy units (pre-filtered by caller)
     * @param basePosition      the base position
     * @return true if visible enemy units are within defense distance
     */
    private boolean isBaseUnderAttack(List<Unit> visibleEnemyUnits, GridPosition basePosition) {
        for (Unit enemy : visibleEnemyUnits) {
            if (GridPosition.distanceClass(
                    enemy.getPosition().x() - basePosition.x(),
                    enemy.getPosition().y() - basePosition.y()) <= BASE_DEFENSE_DISTANCE) {
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
                .comparing((Unit u) -> u.isMachinery() ? 0 : 1) // Machinery (vehicles + SPECIAL_MACHINERY) first
                .thenComparingInt(u -> GridPosition.distanceClass(
                    u.getPosition().x() - basePosition.x(),
                    u.getPosition().y() - basePosition.y())) // Closest first
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
        return aiUnits.stream().anyMatch(u -> u.isMachinery());
    }

    /**
     * Find a harassment target (visible enemy economy building).
     * <p>
     * FOG OF WAR: Only targets VISIBLE enemy production buildings.
     * REF: ai_analysis.md — harass enemy economy when possible
     *
     * @param entities the entity manager
     * @param playerId the AI player ID
     * @param fogOfWar the fog of war system (may be null for full information)
     * @return the position of a visible enemy production building, or null
     */
    private GridPosition findHarassTarget(EntityManager entities, int playerId, FogOfWarSystem fogOfWar) {
        // Only target visible enemy buildings
        List<Building> visibleEnemyBuildings = entities.getVisibleEnemyBuildingsForPlayer(playerId, fogOfWar);
        for (Building building : visibleEnemyBuildings) {
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

    // --- Decision record types for siege and garrison ---

    /**
     * A siege mode decision: which unit should toggle siege mode and in which direction.
     * Used by {@link #findSiegeDecisions} to communicate decisions to the AISystem.
     */
    public record SiegeDecision(int unitId, boolean enableSiege) {}

    /**
     * A garrison decision: which unit should garrison into which building.
     * Used by {@link #findGarrisonDecisions} to communicate decisions to the AISystem.
     */
    public record GarrisonDecision(int unitId, int buildingId) {}
}
