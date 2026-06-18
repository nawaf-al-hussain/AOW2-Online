package com.aow2.common.config;

/**
 * Global game constants extracted from the reverse-engineered data.
 * REF: MASTER_DOCUMENTATION.md - all constants verified against RE data
 * REF: complete_building_stats.json game_config section
 */
public final class GameConstants {
    private GameConstants() {}

    // Game loop
    // REF: combat_formulas.md — original game runs at ~10 ticks/second (100ms per tick)
    public static final int TICK_RATE = 10;
    public static final double TICK_DURATION_MS = 1000.0 / TICK_RATE;

    // Map constraints
    /** Max map coordinate. REF: map_system.md — 128x128 grid (indices 0-127). */
    public static final int MIN_MAP_SIZE = 0;
    public static final int MAX_MAP_SIZE = 127;

    // Entity limits
    public static final int MAX_UNITS_PER_PLAYER = 50;
    // REF: MASTER_DOCUMENTATION.md Section 4 — 22 building slots per player
    public static final int MAX_BUILDINGS_PER_PLAYER = 22;
    public static final int UNIT_SLOTS_PLAYER_0_START = 1;
    // Units start after building slots (MAX_BUILDINGS_PER_PLAYER + 1)
    public static final int UNIT_SLOTS_PLAYER_1_START = 23;

    // Combat
    public static final int ARMOR_DIVISOR = 10;
    public static final int MIN_DAMAGE = 1;

    // Economy
    // REF: combat_formulas.md — base income = (baseIncome * 7) / 10
    public static final int CREDIT_GENERATION_BASE = 7;
    // REF: combat_formulas.md — (aL.ah & 127) == 127 means 128-tick cycle
    public static final int CREDIT_GENERATION_CYCLE = 128;
    // REF: MASTER_DOCUMENTATION.md — credit cap
    public static final int MAX_CREDITS = 30000;
    // REF: MASTER_DOCUMENTATION.md — Q[i] = 100
    public static final int STARTING_CREDITS = 100;

    // Fog of War
    // REF: MASTER_DOCUMENTATION.md — fog updates every 4 ticks: (gameTick & 3) == 0
    public static final int FOG_UPDATE_INTERVAL = 4;

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
    // FIX: Changed from 200 to 50 to match original game's al[0][unit][0..49] limit
    public static final int MAX_PATH_LENGTH = 50;
    // REF: pathfinding.md — stuckCounter >= 5 triggers path recalculation
    public static final int STUCK_THRESHOLD = 5;
    // REF: pathfinding.md — max 10 obstacle segments per path calculation
    public static final int MAX_OBSTACLE_SEGMENTS = 10;
    // REF: pathfinding.md — 8x8 spatial hash grid per player
    public static final int SPATIAL_HASH_GRID_SIZE = 8;
    // REF: pathfinding.md — diagonal costs use a lookup table, not a simple multiplier
    // (REMOVED DIAGONAL_COST_MULTIPLIER — original game uses lookup table approach)
    // Terrain movement costs (indexed by TerrainType ordinal)
    // REF: map_system.md Section 3.1 — terrain IDs corrected to RE spec
    // FIX: Updated to match new TerrainType enum order after removing DIRT/ICE/RUINS
    // Ordinals: DEEP_WATER(0), SHALLOW_WATER(1), SAND(2), GRASS(3), FOREST(4),
    //           HILLS(5), MOUNTAIN(6), ROAD(7), BRIDGE(8), SWAMP(9), SNOW(10), RESOURCE_DEPOSIT(11)
    public static final int[] TERRAIN_MOVEMENT_COSTS = {
        Integer.MAX_VALUE,  // DEEP_WATER (ordinal 0) — impassable
        Integer.MAX_VALUE,  // SHALLOW_WATER (ordinal 1) — impassable except infantry
        2,                  // SAND (ordinal 2)
        1,                  // GRASS (ordinal 3) — baseline
        2,                  // FOREST (ordinal 4) — cover, slower
        3,                  // HILLS (ordinal 5) — slower
        Integer.MAX_VALUE,  // MOUNTAIN (ordinal 6) — impassable
        0,                  // ROAD (ordinal 7) — fastest
        1,                  // BRIDGE (ordinal 8) — same as grass
        4,                  // SWAMP (ordinal 9) — very slow for vehicles
        3,                  // SNOW (ordinal 10) — slow
        1                   // RESOURCE_DEPOSIT (ordinal 11) — same as grass
    };

    // Network
    public static final int DEFAULT_PORT = 47584;
    public static final int PORT_RANGE = 5;
    public static final int MAX_PLAYERS_PER_MATCH = 2;
}
