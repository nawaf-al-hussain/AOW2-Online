package com.aow2.core.engine;

import com.aow2.common.event.GameEvent;
import com.aow2.common.model.Faction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the complete game state for a single match.
 * This is the single source of truth for all game data.
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Game Loop
 */
public class GameState {

    /** Maximum number of processed events retained before evicting oldest. */
    private static final int MAX_PROCESSED_EVENTS = 10_000;

    private long currentTick;
    private final ArrayDeque<GameEvent> eventQueue;
    private final ArrayDeque<GameEvent> processedEvents;
    private boolean running;

    public GameState() {
        this.currentTick = 0;
        this.eventQueue = new ArrayDeque<>();
        this.processedEvents = new ArrayDeque<>();
        this.running = false;
    }

    public long currentTick() { return currentTick; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public void advanceTick() {
        currentTick++;
    }

    public void enqueueEvent(GameEvent event) {
        eventQueue.add(event);
    }

    public List<GameEvent> drainEvents() {
        List<GameEvent> events = new ArrayList<>(eventQueue);
        eventQueue.clear();
        processedEvents.addAll(events);
        // FIX(M-15): Cap processed events list to prevent unbounded memory growth.
        // Remove oldest events when the cap is exceeded.
        while (processedEvents.size() > MAX_PROCESSED_EVENTS) {
            processedEvents.pollFirst();
        }
        return events;
    }

    public List<GameEvent> processedEvents() {
        return List.copyOf(processedEvents);
    }

    /**
     * Resets the game state for a new match.
     */
    public void reset() {
        currentTick = 0;
        eventQueue.clear();
        processedEvents.clear();
        running = false;
    }
}
