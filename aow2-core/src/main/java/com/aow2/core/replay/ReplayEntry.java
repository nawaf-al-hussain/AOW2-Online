package com.aow2.core.replay;

/**
 * Record of a single command in a replay.
 * Each entry captures the tick, command type ordinal, player ID, and
 * serialized payload data for full replay reconstruction.
 * <p>
 * REF: phases.md Phase 11 - replay entry format
 *
 * @param tick     the game tick when the command was issued
 * @param typeOrd  ordinal of the CommandType variant (1-12, matching CommandSerializer type IDs)
 * @param playerId the player who issued the command (0 or 1)
 * @param payload  full serialized command data from CommandSerializer (variable length)
 */
public record ReplayEntry(
    long tick,
    int typeOrd,
    int playerId,
    byte[] payload
) {
    /**
     * Compact constructor with validation.
     */
    public ReplayEntry {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative, got: " + tick);
        }
        // FIX (C2 from CRITICAL_ANALYSIS_REPORT.md): Validate against 13, not 12.
        // TYPE_ATTACK_MOVE = 0x0C = 12, TYPE_UPGRADE = 0x0D = 13
        // REF: ReplayRecorder.java TYPE_UPGRADE = 0x0D
        if (typeOrd < 0 || typeOrd > 13) {
            throw new IllegalArgumentException("typeOrd must be 0-13, got: " + typeOrd);
        }
        if (playerId < 0 || playerId > 1) {
            throw new IllegalArgumentException("playerId must be 0 or 1, got: " + playerId);
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }

    /**
     * Returns a copy of the payload bytes.
     *
     * @return payload bytes
     */
    public byte[] getPayload() {
        return payload.clone();
    }

    @Override
    public String toString() {
        return "ReplayEntry{tick=" + tick + ", type=" + typeOrd +
               ", player=" + playerId + ", payloadSize=" + payload.length + "}";
    }
}
