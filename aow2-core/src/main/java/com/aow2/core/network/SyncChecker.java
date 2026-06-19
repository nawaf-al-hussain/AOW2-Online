package com.aow2.core.network;

import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.engine.GameState;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes and compares state hashes for desync detection in lockstep multiplayer.
 * Both clients independently compute a hash of their game state each frame;
 * if hashes diverge, a desync has occurred.
 * REF: multiplayer_architecture.md - Data integrity: turn sequence validation
 * REF: protocol_specification.md - Integrity checksum (F() method)
 * REF: protocol_specification.md - XOR checksum built into outbound encoding
 */
public class SyncChecker {

    /** Interval (in ticks) between sync hash computations */
    private final int syncInterval;

    /** Last computed state hash for the local client */
    private long localHash;

    /** Last received state hash from the opponent */
    private long remoteHash;

    /** Number of desync events detected */
    private int desyncCount;

    /** Total number of sync checks performed */
    private int checkCount;

    /**
     * Constructs a SyncChecker with the specified sync interval.
     *
     * @param syncInterval number of ticks between hash computations
     */
    public SyncChecker(int syncInterval) {
        if (syncInterval <= 0) {
            throw new IllegalArgumentException("syncInterval must be > 0, got: " + syncInterval);
        }
        this.syncInterval = syncInterval;
        this.localHash = 0;
        this.remoteHash = 0;
        this.desyncCount = 0;
        this.checkCount = 0;
    }

    /**
     * Computes a hash of the current game state for desync detection.
     * The hash incorporates entity positions, health values, resource counts,
     * economy (credits), and research state to ensure any divergence is caught.
     *
     * @param state    the current game state
     * @param entities the entity manager
     * @return a 64-bit hash of the game state
     */
    public long computeStateHash(GameState state, EntityManager entities) {
        return computeStateHash(state, entities, null, null);
    }

    /**
     * Computes a hash of the current game state for desync detection.
     * The hash incorporates entity positions, health values, resource counts,
     * economy (credits per player), and research state (completed and active)
     * to ensure any divergence is caught.
     *
     * @param state    the current game state
     * @param entities the entity manager
     * @param economy  the economy system (may be null)
     * @param research the research system (may be null)
     * @return a 64-bit hash of the game state
     */
    public long computeStateHash(GameState state, EntityManager entities,
                                  EconomySystem economy, ResearchSystem research) {
        long hash = 17L;

        // Include tick count
        hash = hash * 31 + state.currentTick();

        // Include all living unit positions and health
        // FIX (P1-H7): Sort entities by ID before hashing to ensure deterministic order
        // across JVM instances. ConcurrentHashMap.values() iteration order is non-deterministic,
        // which caused false desync reports in multiplayer.
        List<Unit> sortedUnits = new ArrayList<>(entities.getAllUnits());
        sortedUnits.sort(Comparator.comparingInt(Unit::getId));
        for (var unit : sortedUnits) {
            if (!unit.isAlive()) continue;
            hash = hash * 31 + unit.getId();
            hash = hash * 31 + unit.getPosition().x();
            hash = hash * 31 + unit.getPosition().y();
            hash = hash * 31 + unit.getHp();
            hash = hash * 31 + unit.getFaction().ordinal();
        }

        // Include all living building positions and health
        // FIX (P1-H7): Sort buildings by ID for deterministic hash order.
        List<Building> sortedBuildings = new ArrayList<>(entities.getAllBuildings());
        sortedBuildings.sort(Comparator.comparingInt(Building::getId));
        for (var building : sortedBuildings) {
            if (!building.isAlive()) continue;
            hash = hash * 31 + building.getId();
            hash = hash * 31 + building.getPosition().x();
            hash = hash * 31 + building.getPosition().y();
            hash = hash * 31 + building.getHp();
            hash = hash * 31 + building.getFaction().ordinal();
        }

        // Include projectile count
        hash = hash * 31 + entities.projectileCount();

        // Include economy state (credits per player)
        if (economy != null) {
            for (int playerId = 0; playerId < 2; playerId++) {
                hash = hash * 31 + economy.getCredits(playerId);
            }
        }

        // Include research state
        if (research != null) {
            // Completed research IDs per player
            for (int playerId = 0; playerId < 2; playerId++) {
                for (int researchId : research.getCompletedResearch(playerId)) {
                    hash = hash * 31 + researchId;
                }
            }
            // Active research progress
            for (var active : research.getActiveResearchEntries()) {
                hash = hash * 31 + active.researchId();
                hash = hash * 31 + active.playerId();
                hash = hash * 31 + active.progress();
            }
        }

        return hash;
    }

    /**
     * Checks if a sync check should be performed for the given tick.
     *
     * @param tick the current tick
     * @return true if a sync check is due
     */
    public boolean shouldCheck(long tick) {
        return tick % syncInterval == 0;
    }

    /**
     * Records the local state hash for the current sync point.
     *
     * @param hash the computed local state hash
     */
    public void setLocalHash(long hash) {
        this.localHash = hash;
        this.checkCount++;
    }

    /**
     * Records the remote state hash received from the opponent.
     * Automatically compares with the local hash and increments the desync counter if different.
     *
     * @param hash the opponent's state hash
     * @return true if a desync was detected (hashes differ)
     */
    public boolean setRemoteHash(long hash) {
        this.remoteHash = hash;
        boolean desync = localHash != remoteHash;
        if (desync) {
            desyncCount++;
        }
        return desync;
    }

    /**
     * Returns the last local state hash.
     *
     * @return the local hash
     */
    public long getLocalHash() {
        return localHash;
    }

    /**
     * Returns the last remote state hash.
     *
     * @return the remote hash
     */
    public long getRemoteHash() {
        return remoteHash;
    }

    /**
     * Returns the number of desync events detected.
     *
     * @return desync count
     */
    public int getDesyncCount() {
        return desyncCount;
    }

    /**
     * Returns the total number of sync checks performed.
     *
     * @return check count
     */
    public int getCheckCount() {
        return checkCount;
    }

    /**
     * Resets all sync tracking state.
     */
    public void reset() {
        localHash = 0;
        remoteHash = 0;
        desyncCount = 0;
        checkCount = 0;
    }
}
