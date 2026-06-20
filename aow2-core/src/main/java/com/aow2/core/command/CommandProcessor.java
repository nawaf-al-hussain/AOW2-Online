package com.aow2.core.command;

import com.aow2.common.model.CommandType;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.engine.GameState;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central command processor that dispatches CommandType records to appropriate systems.
 * This is the glue between the command pattern and the game systems.
 * <p>
 * Uses pattern matching on the CommandType sealed interface to route
 * each command type to its corresponding handler.
 * <p>
 * REF: protocol_specification.md - 34 multiplayer message types
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Lockstep networking
 */
public final class CommandProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CommandProcessor.class);

    /** Handler for move commands. */
    private final MoveCommandHandler moveHandler;

    /** Handler for attack commands. */
    private final AttackCommandHandler attackHandler;

    /** Handler for build commands. */
    private final BuildCommandHandler buildHandler;

    /** Handler for produce commands. */
    private final ProduceCommandHandler produceHandler;

    /** Handler for research commands. */
    private final ResearchCommandHandler researchHandler;

    /** Handler for garrison commands. */
    private final GarrisonCommandHandler garrisonHandler;

    /**
     * Constructs a CommandProcessor with default handlers.
     */
    public CommandProcessor() {
        this.moveHandler = new MoveCommandHandler();
        this.attackHandler = new AttackCommandHandler();
        this.buildHandler = new BuildCommandHandler();
        this.produceHandler = new ProduceCommandHandler();
        this.researchHandler = new ResearchCommandHandler();
        this.garrisonHandler = new GarrisonCommandHandler();
    }

    /**
     * Process a command by dispatching to the appropriate system.
     * Uses pattern matching on CommandType sealed interface.
     *
     * @param command    the command to process
     * @param state      the current game state
     * @param entities   the entity manager
     * @param map        the game map
     * @param movement   the movement system
     * @param combat     the combat system
     * @param economy    the economy system
     * @param production the production system
     * @param research   the research system
     * @param placement  the building placement system
     */
    public void process(CommandType command, GameState state, EntityManager entities,
                        GameMap map, MovementSystem movement, CombatSystem combat,
                        EconomySystem economy, ProductionSystem production,
                        ResearchSystem research, BuildingPlacementSystem placement) {
        switch (command) {
            case CommandType.Move cmd -> moveHandler.handle(cmd, entities, map, movement);
            case CommandType.Attack cmd -> attackHandler.handle(cmd, entities, combat);
            case CommandType.AttackMove cmd -> handleAttackMove(cmd, entities, map, movement);
            case CommandType.Build cmd -> buildHandler.handle(cmd, entities, map, economy, placement);
            case CommandType.Produce cmd -> produceHandler.handle(cmd, entities, economy, production, research);
            case CommandType.Research cmd -> researchHandler.handle(cmd, entities, economy, research);
            case CommandType.Garrison cmd -> garrisonHandler.handleGarrison(cmd, entities);
            case CommandType.Ungarrison cmd -> garrisonHandler.handleUngarrison(cmd, entities, map);
            case CommandType.Cancel cmd -> handleCancel(cmd, entities, economy, production, research);
            case CommandType.SiegeMode cmd -> handleSiegeMode(cmd, entities, combat);
            case CommandType.Stop cmd -> handleStop(cmd, entities);
            case CommandType.Patrol cmd -> handlePatrol(cmd, entities, map, movement);
        }
    }

    /**
     * Handle a cancel command.
     * Cancels production or research on the target entity.
     *
     * @param cmd        the cancel command
     * @param entities   the entity manager
     * @param economy    the economy system (for refunds)
     * @param production the production system
     * @param research   the research system
     */
    private void handleCancel(CommandType.Cancel cmd, EntityManager entities,
                              EconomySystem economy, ProductionSystem production,
                              ResearchSystem research) {
        var building = entities.getBuilding(cmd.entityId());
        if (building != null && building.isAlive()) {
            // Cancel current production
            if (building.isProducing()) {
                int playerId = EconomySystem.playerId(building.getFaction());
                production.cancelProduction(building, 0, playerId, economy);
                LOG.debug("Cancelled production for building {}", cmd.entityId());
            }
            // Cancel current research
            if (building.isResearching()) {
                building.setResearchId(null);
                LOG.debug("Cancelled research for building {}", cmd.entityId());
            }
        }
    }

    /**
     * Handle a siege mode toggle command.
     *
     * @param cmd      the siege mode command
     * @param entities the entity manager
     * @param combat   the combat system
     */
    private void handleSiegeMode(CommandType.SiegeMode cmd, EntityManager entities,
                                  CombatSystem combat) {
        var unit = entities.getUnit(cmd.unitId());
        if (unit != null && unit.isAlive()) {
            if (cmd.enabled()) {
                combat.enterSiegeMode(unit);
            } else {
                combat.exitSiegeMode(unit);
            }
        }
    }

    /**
     * Handle a stop command.
     * Cancels all current orders for the specified units.
     *
     * @param cmd      the stop command
     * @param entities the entity manager
     */
    private void handleStop(CommandType.Stop cmd, EntityManager entities) {
        for (int unitId : cmd.unitIds()) {
            var unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                unit.clearPath();
                unit.setTargetUnitRef(null);
                unit.setAttackState(0);
                LOG.debug("Unit {} stopped", unitId);
            }
        }
    }

    /**
     * Handle a patrol command.
     * Units will move back and forth between their current position and the waypoint.
     * FIX (M-NEW-12): Store the origin position so units can return after reaching the waypoint.
     * The unit's path is set to the waypoint; when it arrives, MovementSystem will issue
     * a return move command to the patrol origin (stored via setPatrolOrigin on the unit).
     *
     * @param cmd       the patrol command
     * @param entities  the entity manager
     * @param map       the game map
     * @param movement  the movement system
     */
    private void handlePatrol(CommandType.Patrol cmd, EntityManager entities,
                               GameMap map, MovementSystem movement) {
        for (int unitId : cmd.unitIds()) {
            var unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                // FIX (M-NEW-12): Store patrol origin for return path.
                // When the unit reaches the waypoint, the MovementSystem (or a patrol
                // completion check) will issue a move back to this origin position.
                unit.setPatrolOrigin(unit.getPosition());
                movement.issueMoveCommand(unit, cmd.waypoint(), map, entities);
                LOG.debug("Unit {} patrolling from {} to {}", unitId, unit.getPosition(), cmd.waypoint());
            }
        }
    }

    /**
     * Handle an AttackMove command.
     * Issues a move command toward the target position. Units will engage enemies
     * encountered along the way via the combat system's auto-targeting.
     * FIX (M-NEW-11): Set autoEngage flag so MovementSystem knows to stop and fight
     * when enemies are encountered, then resume moving after combat.
     *
     * @param cmd       the attack-move command
     * @param entities  the entity manager
     * @param map       the game map
     * @param movement  the movement system
     */
    private void handleAttackMove(CommandType.AttackMove cmd, EntityManager entities,
                                   GameMap map, MovementSystem movement) {
        for (int unitId : cmd.unitIds()) {
            var unit = entities.getUnit(unitId);
            if (unit != null && unit.isAlive()) {
                // FIX (M-NEW-11): Set autoEngage state so the unit will engage enemies
                // encountered along the path, then resume movement after combat.
                unit.setAutoEngage(true);
                unit.setAutoEngageTarget(cmd.target());
                movement.issueMoveCommand(unit, cmd.target(), map, entities);
                LOG.debug("Unit {} attack-moving to {} (autoEngage=true)", unitId, cmd.target());
            }
        }
    }
}
