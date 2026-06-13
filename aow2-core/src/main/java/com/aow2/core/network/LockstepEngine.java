package com.aow2.core.network;

import com.aow2.common.model.CommandType;
import com.aow2.core.command.CommandProcessor;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * Lockstep simulation engine for deterministic multiplayer.
 * Both clients run identical game state, exchanging only player commands.
 * The engine buffers commands with an input delay to ensure both players'
 * inputs are available before the simulation advances.
 * <p>
 * REF: multiplayer_architecture.md - Lockstep P2P model, both clients run identical state
 * REF: protocol_specification.md - 34 message types, server-authoritative lockstep model
 * REF: protocol_specification.md - Game advances in discrete turns tracked by y.Q[0]
 * REF: protocol_specification.md - Sync interval: default 15s, range 2-60s
 */
public final class LockstepEngine {

    private static final Logger log = LoggerFactory.getLogger(LockstepEngine.class);

    /** Default input delay in frames (2 frames ≈ 66ms at 30 FPS) */
    private static final int DEFAULT_INPUT_DELAY = 2;

    /** Default buffer size in frames */
    private static final int DEFAULT_BUFFER_SIZE = 16;

    /** Default sync check interval in ticks */
    private static final int DEFAULT_SYNC_INTERVAL = 30;

    /** Command buffer for managing frame-based command storage */
    private final CommandBuffer commandBuffer;

    /** Sync checker for desync detection */
    private final SyncChecker syncChecker;

    /** Command processor for routing commands to game systems */
    private final CommandProcessor commandProcessor;

    /** Callback for sending commands to the opponent */
    private Consumer<byte[]> sendCallback;

    /** Callback for desync notification */
    private Consumer<Long> desyncCallback;

    /** Game systems needed for command routing */
    private GameMap gameMap;
    private MovementSystem movementSystem;
    private CombatSystem combatSystem;
    private EconomySystem economySystem;
    private ProductionSystem productionSystem;
    private ResearchSystem researchSystem;
    private BuildingPlacementSystem buildingPlacementSystem;

    /** Whether the engine is currently running */
    private boolean running;

    /** Frame counter for the current lockstep frame */
    private long lockstepFrame;

    /**
     * Constructs a LockstepEngine with default parameters.
     */
    public LockstepEngine() {
        this(DEFAULT_INPUT_DELAY, DEFAULT_BUFFER_SIZE, DEFAULT_SYNC_INTERVAL);
    }

    /**
     * Constructs a LockstepEngine with custom parameters.
     *
     * @param inputDelay    number of frames to delay input
     * @param bufferSize    ring buffer capacity for commands
     * @param syncInterval  ticks between sync hash checks
     */
    public LockstepEngine(int inputDelay, int bufferSize, int syncInterval) {
        this.commandBuffer = new CommandBuffer(inputDelay, bufferSize);
        this.syncChecker = new SyncChecker(syncInterval);
        this.commandProcessor = new CommandProcessor();
        this.running = false;
        this.lockstepFrame = 0;
    }

    /**
     * Starts the lockstep engine.
     *
     * @param sendCallback callback to send serialized commands to the opponent
     */
    public void start(Consumer<byte[]> sendCallback) {
        this.sendCallback = sendCallback;
        this.running = true;
        this.lockstepFrame = 0;
        log.info("Lockstep engine started (inputDelay={}, syncInterval={})",
                commandBuffer.currentTick(), syncChecker.getCheckCount());
    }

    /**
     * Stops the lockstep engine.
     */
    public void stop() {
        this.running = false;
        log.info("Lockstep engine stopped at frame {}", lockstepFrame);
    }

    /**
     * Submits a command for the current frame.
     * Commands are buffered and processed after the input delay.
     * The command is also serialized and sent to the opponent via the send callback.
     * REF: protocol_specification.md - Outbound data via m.java sender thread
     *
     * @param command the command to submit
     */
    public void submitCommand(CommandType command) {
        if (!running) {
            log.warn("Cannot submit command: engine not running");
            return;
        }

        commandBuffer.submitCommand(command);

        // Serialize and send to opponent
        if (sendCallback != null) {
            byte[] data = CommandSerializer.serialize(command);
            sendCallback.accept(data);
        }
    }

    /**
     * Receives a serialized command from the opponent.
     * Deserializes and buffers it for the appropriate frame.
     * REF: protocol_specification.md - Inbound data via o.java receiver thread
     *
     * @param data the serialized command bytes
     */
    public void receiveCommand(byte[] data) {
        if (!running) {
            log.warn("Cannot receive command: engine not running");
            return;
        }

        CommandType command = CommandSerializer.deserialize(data);
        commandBuffer.submitOpponentCommand(command, command.tick());
    }

    /**
     * Processes one lockstep frame.
     * 1. Receives opponent's commands for this frame
     * 2. Applies both players' commands
     * 3. Advances game state
     * 4. Checks for desync
     * REF: protocol_specification.md - Game loop D() method manages timing
     *
     * @param state    the current game state
     * @param entities the entity manager
     * @return the list of commands processed this frame
     */
    public List<CommandType> processFrame(GameState state, EntityManager entities) {
        if (!running) {
            return List.of();
        }

        // Drain commands for the current frame
        List<CommandType> commands = commandBuffer.drainFrame();

        // Apply commands to game state
        for (CommandType command : commands) {
            applyCommand(command, state, entities);
        }

        // Advance game state
        state.advanceTick();
        lockstepFrame++;

        // Check for desync at sync interval
        if (syncChecker.shouldCheck(lockstepFrame)) {
            long hash = syncChecker.computeStateHash(state, entities);
            syncChecker.setLocalHash(hash);
        }

        return commands;
    }

    /**
     * Reports the opponent's sync hash for desync detection.
     * REF: multiplayer_architecture.md - Data integrity, state comparison
     *
     * @param remoteHash the opponent's state hash
     * @return true if a desync was detected
     */
    public boolean reportRemoteSyncHash(long remoteHash) {
        boolean desync = syncChecker.setRemoteHash(remoteHash);
        if (desync && desyncCallback != null) {
            desyncCallback.accept(lockstepFrame);
            log.error("Desync detected at frame {} (local: {}, remote: {})",
                    lockstepFrame, syncChecker.getLocalHash(), remoteHash);
        }
        return desync;
    }

    /**
     * Sets the callback for desync detection events.
     *
     * @param callback called with the frame number when a desync is detected
     */
    public void setDesyncCallback(Consumer<Long> callback) {
        this.desyncCallback = callback;
    }

    /**
     * Injects game systems needed for command routing.
     * Must be called before processFrame() if economy/research/garrison commands are expected.
     *
     * @param map          the game map
     * @param movement     the movement system
     * @param combat       the combat system
     * @param economy      the economy system
     * @param production   the production system
     * @param research     the research system
     * @param placement    the building placement system
     */
    public void setGameSystems(GameMap map, MovementSystem movement, CombatSystem combat,
                               EconomySystem economy, ProductionSystem production,
                               ResearchSystem research, BuildingPlacementSystem placement) {
        this.gameMap = map;
        this.movementSystem = movement;
        this.combatSystem = combat;
        this.economySystem = economy;
        this.productionSystem = production;
        this.researchSystem = research;
        this.buildingPlacementSystem = placement;
    }

    /**
     * Applies a single command to the game state.
     * Commands are dispatched by type and applied to the appropriate entities/systems.
     * All command types are routed through CommandProcessor for system-level commands,
     * while movement and attack commands are applied directly for low-latency response.
     *
     * @param command  the command to apply
     * @param state    the game state
     * @param entities the entity manager
     */
    private void applyCommand(CommandType command, GameState state, EntityManager entities) {
        switch (command) {
            case CommandType.Move m -> {
                for (int unitId : m.unitIds()) {
                    var unit = entities.getUnit(unitId);
                    if (unit != null && unit.getFaction().ordinal() == m.playerId()) {
                        // REF: pathfinding.md — compute path via MovementSystem instead of just setting target
                        if (movementSystem != null && gameMap != null) {
                            movementSystem.issueMoveCommand(unit, m.target(), gameMap);
                        } else {
                            // Fallback: set target directly if MovementSystem not available
                            unit.setTargetPosition(m.target());
                            unit.setMovementState(
                                    com.aow2.common.model.MovementState.MOVING);
                        }
                    }
                }
            }
            case CommandType.Attack a -> {
                for (int unitId : a.unitIds()) {
                    var unit = entities.getUnit(unitId);
                    if (unit != null) {
                        unit.setTargetUnitRef(a.targetId());
                    }
                }
            }
            case CommandType.Stop s -> {
                for (int unitId : s.unitIds()) {
                    var unit = entities.getUnit(unitId);
                    if (unit != null) {
                        unit.clearPath();
                    }
                }
            }
            case CommandType.SiegeMode sm -> {
                var unit = entities.getUnit(sm.unitId());
                if (unit != null) {
                    unit.setSiegeMode(sm.enabled());
                }
            }
            // Route Build, Produce, Research, Garrison, Ungarrison, Cancel commands
            // to CommandProcessor which dispatches to the appropriate game systems.
            // Game systems must be injected via setGameSystems() before these commands
            // can be processed. If not set, commands are logged and skipped.
            case CommandType.Build b -> {
                if (economySystem == null || buildingPlacementSystem == null) {
                    log.warn("Build command skipped: game systems not injected (call setGameSystems())");
                } else {
                    commandProcessor.process(b, state, entities, gameMap, movementSystem,
                            combatSystem, economySystem, productionSystem, researchSystem,
                            buildingPlacementSystem);
                }
            }
            case CommandType.Produce p -> {
                if (economySystem == null || productionSystem == null) {
                    log.warn("Produce command skipped: game systems not injected (call setGameSystems())");
                } else {
                    commandProcessor.process(p, state, entities, gameMap, movementSystem,
                            combatSystem, economySystem, productionSystem, researchSystem,
                            buildingPlacementSystem);
                }
            }
            case CommandType.Research r -> {
                if (economySystem == null || researchSystem == null) {
                    log.warn("Research command skipped: game systems not injected (call setGameSystems())");
                } else {
                    commandProcessor.process(r, state, entities, gameMap, movementSystem,
                            combatSystem, economySystem, productionSystem, researchSystem,
                            buildingPlacementSystem);
                }
            }
            case CommandType.Garrison g -> {
                commandProcessor.process(g, state, entities, gameMap, movementSystem,
                        combatSystem, economySystem, productionSystem, researchSystem,
                        buildingPlacementSystem);
            }
            case CommandType.Ungarrison u -> {
                commandProcessor.process(u, state, entities, gameMap, movementSystem,
                        combatSystem, economySystem, productionSystem, researchSystem,
                        buildingPlacementSystem);
            }
            case CommandType.Cancel c -> {
                if (economySystem == null || productionSystem == null) {
                    log.warn("Cancel command skipped: game systems not injected (call setGameSystems())");
                } else {
                    commandProcessor.process(c, state, entities, gameMap, movementSystem,
                            combatSystem, economySystem, productionSystem, researchSystem,
                            buildingPlacementSystem);
                }
            }
            case CommandType.Patrol pt -> {
                log.debug("Patrol command at tick {}: units -> waypoint {}",
                        pt.tick(), pt.waypoint());
                // Patrol: move units to the patrol waypoint via MovementSystem
                for (int unitId : pt.unitIds()) {
                    var unit = entities.getUnit(unitId);
                    if (unit != null && unit.isAlive()) {
                        if (movementSystem != null && gameMap != null) {
                            movementSystem.issueMoveCommand(unit, pt.waypoint(), gameMap);
                        } else {
                            unit.setTargetPosition(pt.waypoint());
                            unit.setMovementState(
                                    com.aow2.common.model.MovementState.MOVING);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the current lockstep frame number.
     *
     * @return the frame number
     */
    public long getLockstepFrame() {
        return lockstepFrame;
    }

    /**
     * Returns whether the engine is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the sync checker for direct access to sync statistics.
     *
     * @return the sync checker
     */
    public SyncChecker getSyncChecker() {
        return syncChecker;
    }

    /**
     * Resets the engine to its initial state.
     */
    public void reset() {
        commandBuffer.reset();
        syncChecker.reset();
        running = false;
        lockstepFrame = 0;
        sendCallback = null;
        desyncCallback = null;
    }
}
