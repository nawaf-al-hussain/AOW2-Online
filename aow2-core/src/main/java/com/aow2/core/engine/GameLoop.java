package com.aow2.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aow2.common.config.GameConstants;

/**
 * Fixed-timestep game loop running at 60 TPS.
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Game Loop
 * The original game used a single game thread with fixed timestep.
 */
public class GameLoop {

    private static final Logger LOG = LoggerFactory.getLogger(GameLoop.class);

    private final GameState gameState;
    private final Runnable updateCallback;
    private volatile boolean running;
    private Thread gameThread;

    public GameLoop(GameState gameState, Runnable updateCallback) {
        this.gameState = gameState;
        this.updateCallback = updateCallback;
        this.running = false;
    }

    public void start() {
        if (running) {
            LOG.warn("Game loop already running");
            return;
        }
        running = true;
        gameState.setRunning(true);
        gameThread = Thread.ofPlatform().name("game-loop").start(this::loop);
        LOG.info("Game loop started at {} TPS", GameConstants.TICK_RATE);
    }

    public void stop() {
        running = false;
        gameState.setRunning(false);
        if (gameThread != null) {
            gameThread.interrupt();
        }
        LOG.info("Game loop stopped at tick {}", gameState.currentTick());
    }

    private void loop() {
        final double tickDuration = GameConstants.TICK_DURATION_MS;
        long lastTime = System.nanoTime();
        double accumulator = 0.0;

        while (running) {
            long now = System.nanoTime();
            double elapsed = (now - lastTime) / 1_000_000.0;
            lastTime = now;

            accumulator += elapsed;

            while (accumulator >= tickDuration) {
                updateCallback.run();
                // TickManager.processTick() already calls advanceTick(), so we must not
                // advance again here or the game state will advance by 2 ticks per frame.
                accumulator -= tickDuration;
            }

            // Sleep to prevent busy-waiting
            double sleepTime = tickDuration - accumulator;
            if (sleepTime > 1.0) {
                try {
                    Thread.sleep((long) sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public boolean isRunning() { return running; }
}
