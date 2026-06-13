package com.aow2.core.engine;

import com.aow2.common.config.GameConstants;
import com.aow2.common.model.CommandType;
import com.aow2.core.ai.AISystem;
import com.aow2.core.command.CommandProcessor;
import com.aow2.core.combat.CombatSystem;
import com.aow2.core.combat.HPRegenerationSystem;
import com.aow2.core.combat.MineDetonationSystem;
import com.aow2.core.combat.ProjectileSystem;
import com.aow2.core.economy.BuildingPlacementSystem;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.ProductionSystem;
import com.aow2.core.movement.MovementSystem;
import com.aow2.core.movement.PathfindingSystem;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import com.aow2.core.world.FogOfWarSystem;
import com.aow2.core.world.GameMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Master tick manager that orchestrates all game systems per tick.
 * <p>
 * Processing order (matching original game):
 * 1. Process incoming commands
 * 2. Process movement
 * 3. Process combat (attacks + projectiles)
 * 4. Process production
 * 5. Process research
 * 6. Process economy (credit generation)
 * 7. Process AI
 * 8. Update fog of war
 * 9. Remove dead entities
 * 10. Advance tick
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Game Loop
 * REF: ai_analysis.md — original game tick processing order
 */
public final class TickManager {

    private static final Logger LOG = LoggerFactory.getLogger(TickManager.class);

    /** The command processor for dispatching player commands. */
    private final CommandProcessor commandProcessor;

    /** Pending commands to be processed on the next tick. */
    private final java.util.concurrent.ConcurrentLinkedQueue<CommandType> pendingCommands;

    /** The fog of war system. */
    private FogOfWarSystem fogOfWar;

    /** The AI system for player 1 (if AI-controlled). */
    private AISystem aiSystem;

    /** The HP regeneration system. Optional — set after construction. */
    private HPRegenerationSystem hpRegenerationSystem;

    /** The mine detonation system. Optional — set after construction. */
    private MineDetonationSystem mineDetonationSystem;

    /**
     * Constructs a TickManager with a default command processor.
     */
    public TickManager() {
        this.commandProcessor = new CommandProcessor();
        this.pendingCommands = new java.util.concurrent.ConcurrentLinkedQueue<>();
    }

    /**
     * Constructs a TickManager with explicit subsystems.
     *
     * @param commandProcessor the command processor
     * @param fogOfWar         the fog of war system
     */
    public TickManager(CommandProcessor commandProcessor, FogOfWarSystem fogOfWar) {
        this.commandProcessor = commandProcessor;
        this.pendingCommands = new java.util.concurrent.ConcurrentLinkedQueue<>();
        this.fogOfWar = fogOfWar;
    }

    /**
     * Set the fog of war system.
     * Also passes it to the AI system if one is set.
     *
     * @param fogOfWar the fog of war system
     */
    public void setFogOfWar(FogOfWarSystem fogOfWar) {
        this.fogOfWar = fogOfWar;
        // Wire fog of war to AI if both are set
        if (aiSystem != null && fogOfWar != null) {
            aiSystem.setFogOfWar(fogOfWar);
        }
    }

    /**
     * Set the AI system for the non-human player.
     * Also passes the fog of war system to the AI if one is set.
     *
     * @param aiSystem the AI system
     */
    public void setAISystem(AISystem aiSystem) {
        this.aiSystem = aiSystem;
        // Wire fog of war to AI if both are set
        if (aiSystem != null && fogOfWar != null) {
            aiSystem.setFogOfWar(fogOfWar);
        }
    }

    /**
     * Set the HP regeneration system.
     *
     * @param hpRegenerationSystem the HP regeneration system
     */
    public void setHPRegenerationSystem(HPRegenerationSystem hpRegenerationSystem) {
        this.hpRegenerationSystem = hpRegenerationSystem;
    }

    /**
     * Set the mine detonation system.
     *
     * @param mineDetonationSystem the mine detonation system
     */
    public void setMineDetonationSystem(MineDetonationSystem mineDetonationSystem) {
        this.mineDetonationSystem = mineDetonationSystem;
    }

    /**
     * Enqueue a command for processing on the next tick.
     *
     * @param command the command to enqueue
     */
    public void enqueueCommand(CommandType command) {
        pendingCommands.add(command);
    }

    /**
     * Process one complete game tick, updating all systems in order.
     * <p>
     * Processing order matches the original game's tick function.
     *
     * @param state       the current game state
     * @param entities    the entity manager
     * @param map         the game map
     * @param movement    the movement system
     * @param combat      the combat system
     * @param economy     the economy system
     * @param production  the production system
     * @param research    the research system
     * @param placement   the building placement system
     * @param pathfinding the pathfinding system
     * @param projectiles the projectile system
     */
    public void processTick(GameState state, EntityManager entities, GameMap map,
                            MovementSystem movement, CombatSystem combat,
                            EconomySystem economy, ProductionSystem production,
                            ResearchSystem research, BuildingPlacementSystem placement,
                            PathfindingSystem pathfinding, ProjectileSystem projectiles) {
        // Step 1: Process incoming commands
        processCommands(state, entities, map, movement, combat, economy, production, research, placement);

        // Step 2: Process movement
        movement.processTick(entities, map);

        // Step 3: Process combat (attacks + projectiles)
        combat.processTick();

        // Step 3.1: Process mine detonation (after combat, before HP regen)
        // REF: unit_stats.md — mines detonate on proximity, checked each tick
        if (mineDetonationSystem != null) {
            mineDetonationSystem.processTick(entities, state);
        }

        // Step 3.5: Process HP regeneration (after combat, before production)
        // REF: combat_formulas.md — HP recovery applied periodically every 127 ticks
        if (hpRegenerationSystem != null) {
            hpRegenerationSystem.processTick(entities, (int) state.currentTick());
        }

        // Step 4: Process production
        production.processTick(entities, state, economy);

        // Step 5: Process research
        research.processTick(entities, state);

        // Step 6: Process economy (credit generation)
        economy.processTick(entities, state);

        // Step 7: Process AI
        if (aiSystem != null) {
            aiSystem.processTick(entities, map, economy, research,
                production, placement, movement, combat, state);
        }

        // Step 8: Update fog of war (every 4 ticks)
        // REF: MASTER_DOCUMENTATION.md — fog updates every 4 ticks: (gameTick & 3) == 0
        if (fogOfWar != null && (state.currentTick() % GameConstants.FOG_UPDATE_INTERVAL) == 0) {
            for (int playerId = 0; playerId < GameConstants.MAX_PLAYERS_PER_MATCH; playerId++) {
                fogOfWar.updateVisibility(playerId, entities, map);
            }
        }

        // Step 9: Remove dead entities
        entities.removeDeadEntities();

        // Step 10: Advance tick
        state.advanceTick();
    }

    /**
     * Process all pending commands from the command queue.
     * Commands are drained from the queue and dispatched to the CommandProcessor.
     *
     * @param state       the game state
     * @param entities    the entity manager
     * @param map         the game map
     * @param movement    the movement system
     * @param combat      the combat system
     * @param economy     the economy system
     * @param production  the production system
     * @param research    the research system
     * @param placement   the building placement system
     */
    private void processCommands(GameState state, EntityManager entities, GameMap map,
                                 MovementSystem movement, CombatSystem combat,
                                 EconomySystem economy, ProductionSystem production,
                                 ResearchSystem research, BuildingPlacementSystem placement) {
        CommandType command;
        while ((command = pendingCommands.poll()) != null) {
            // Only process commands for the current tick
            if (command.tick() <= state.currentTick()) {
                commandProcessor.process(command, state, entities, map,
                    movement, combat, economy, production, research, placement);
            } else {
                // Re-queue future commands
                pendingCommands.add(command);
                break; // Commands are ordered by tick
            }
        }
    }

    /**
     * Get the number of pending commands.
     *
     * @return pending command count
     */
    public int pendingCommandCount() {
        return pendingCommands.size();
    }

    /**
     * Clear all pending commands.
     */
    public void clearCommands() {
        pendingCommands.clear();
    }
}
