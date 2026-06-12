package com.aow2.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {

    @Test
    @DisplayName("Should create game loop with state and callback")
    void shouldCreateGameLoop() {
        var state = new GameState();
        var loop = new GameLoop(state, () -> {});
        assertFalse(loop.isRunning());
    }

    @Test
    @DisplayName("Should start and stop the game loop")
    void shouldStartAndStop() throws InterruptedException {
        var state = new GameState();
        var loop = new GameLoop(state, () -> {});
        loop.start();
        Thread.sleep(100);
        assertTrue(loop.isRunning());
        assertTrue(state.isRunning());
        loop.stop();
        Thread.sleep(50);
        assertFalse(loop.isRunning());
        assertFalse(state.isRunning());
        assertTrue(state.currentTick() > 0);
    }
}
