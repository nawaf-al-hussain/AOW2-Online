package com.aow2.core.engine;

import com.aow2.common.event.GameEvent;
import com.aow2.common.model.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the complete game state for a single match.
 * This is the single source of truth for all game data.
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Game Loop
 */
public class GameState {

    private long currentTick;
    private final CopyOnWriteArrayList<GameEvent> eventQueue;
    private final List<GameEvent> processedEvents;
    private boolean running;

    public GameState() {
        this.currentTick = 0;
        this.eventQueue = new CopyOnWriteArrayList<>();
        this.processedEvents = new ArrayList<>();
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
