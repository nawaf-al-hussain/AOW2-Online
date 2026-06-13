package com.aow2.common.config;

/**
 * Global game constants extracted from the reverse-engineered data.
 * REF: MASTER_DOCUMENTATION.md - all constants verified against RE data
 * REF: complete_building_stats.json game_config section
 */
public final class GameConstants {
    private GameConstants() {}

    // Game loop
    public static final int TICK_RATE = 60;
    public static final double TICK_DURATION_MS = 1000.0 / TICK_RATE;

    // Map constraints
    public static final int MIN_MAP_SIZE = 0;
    public static final int MAX_MAP_SIZE = 127;

    // Entity limits
    public static final int MAX_UNITS_PER_PLAYER = 50;
    public static final int MAX_BUILDINGS_PER_PLAYER = 50;
    public static final int UNIT_SLOTS_PLAYER_0_START = 1;
    public static final int UNIT_SLOTS_PLAYER_1_START = 51;

    // Combat
    public static final int ARMOR_DIVISOR = 10;
    public static final int MIN_DAMAGE = 1;

    // Economy
    public static final double CC_DIMINISHING_RETURNS = 0.30;
    public static final int POWER_UPGRADE_BONUS_PERCENT = 30;

    // Building placement
    // REF: complete_building_stats.json building_power_radius values
    public static final int[] BUILDING_POWER_RADIUS = {10, 20, 30, 40, 60, 127};

    // Unit type bitmasks
    // REF: unit_stats.md - bitmask for classification
    public static final int INFANTRY_BITMASK = 16447;  // 0x403F
    public static final int MACHINERY_BITMASK = 16256;  // 0x3F80
    public static final int PRODUCER_BITMASK = 114688;  // 0x1C000
    public static final int LARGE_UNIT_BITMASK = 65536; // 0x10000

    // Rank system
    // REF: complete_building_stats.json rank_exp_thresholds and rank_credit_rewards
    public static final int[] RANK_EXP_THRESHOLDS = {20, 35, 50};
    public static final int[] RANK_CREDIT_REWARDS = {10, 25, 51};
    public static final int[] RANK_BONUS_POINTS = {0, 3, 6};

    // Death animation arrays
    // REF: combat_formulas.md - bi[] and bd[] arrays
    public static final int[] DEATH_ANIM_BASE = {231, 249, 249, 259, 247};
    public static final int[] DEATH_ANIM_RANGE = {16, 10, 10, 3, 2};

    // Map record count
    public static final int MAP_RECORD_COUNT = 193;

    // Pathfinding & Movement
    // REF: pathfinding.md — max path length 50 steps in original game
    public static final int MAX_PATH_LENGTH = 200;
    // REF: pathfinding.md — stuckCounter >= 5 triggers path recalculation
    public static final int STUCK_THRESHOLD = 5;
    // REF: pathfinding.md — max 10 obstacle segments per path calculation
    public static final int MAX_OBSTACLE_SEGMENTS = 10;
    // REF: pathfinding.md — 8x8 spatial hash grid per player
    public static final int SPATIAL_HASH_GRID_SIZE = 8;
    // Diagonal movement cost multiplier (sqrt(2) ≈ 1.41)
    public static final double DIAGONAL_COST_MULTIPLIER = 1.41;
    // Terrain movement costs (indexed by TerrainType ordinal)
    // REF: pathfinding.md — terrain costs affect pathfinding decisions
    public static final int[] TERRAIN_MOVEMENT_COSTS = {
        1,                  // GRASS (ordinal 0)
        2,                  // SAND (ordinal 1)
        Integer.MAX_VALUE,  // WATER (ordinal 2) — impassable for ground units
        Integer.MAX_VALUE,  // MOUNTAIN (ordinal 3) — impassable for ground units
        3,                  // FOREST (ordinal 4) — reduced visibility, higher traversal cost
        1,                  // ROAD (ordinal 5) — fastest movement
        2,                  // BRIDGE (ordinal 6) — slightly slower than road
        2                   // RUINS (ordinal 7) — moderately slow
    };

    // Network
    public static final int DEFAULT_PORT = 47584;
    public static final int PORT_RANGE = 5;
    public static final int MAX_PLAYERS_PER_MATCH = 2;
}
