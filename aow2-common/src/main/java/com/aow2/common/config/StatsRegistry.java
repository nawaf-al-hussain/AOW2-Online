package com.aow2.common.config;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized registry for all unit and building stats loaded from reverse-engineered data.
 * Replaces all hardcoded stat tables scattered across ProductionSystem, BuildingPlacementSystem,
 * EconomyAI, and GameScene.
 * <p>
 * This is a singleton — access via {@link #getInstance()}.
 * All stats are initialized from verified RE data and are immutable after construction.
 * <p>
 * REF: complete_unit_stats.json — all unit stat values
 * REF: complete_building_stats.json — all building stat values
 */
public final class StatsRegistry {

    private static final StatsRegistry INSTANCE = new StatsRegistry();

    private final Map<UnitType, UnitStats> unitStats;
    private final Map<BuildingType, BuildingStats> buildingStats;
    private final Map<UnitType, Integer> unitTechRequirements;

    private StatsRegistry() {
        unitStats = new EnumMap<>(UnitType.class);
        buildingStats = new EnumMap<>(BuildingType.class);
        unitTechRequirements = new EnumMap<>(UnitType.class);
        initUnitStats();
        initBuildingStats();
        initUnitTechRequirements();
    }

    /**
     * Returns the singleton instance of the StatsRegistry.
     *
     * @return the stats registry instance
     */
    public static StatsRegistry getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    // Lookup methods
    // =========================================================================

    /**
     * Returns the stats for the given unit type.
     *
     * @param type the unit type to look up
     * @return the unit stats
     * @throws IllegalArgumentException if the unit type is not registered
     */
    public UnitStats getUnitStats(UnitType type) {
        UnitStats stats = unitStats.get(type);
        if (stats == null) {
            throw new IllegalArgumentException("No stats registered for unit type: " + type);
        }
        return stats;
    }

    /**
     * Returns the stats for the given building type.
     *
     * @param type the building type to look up
     * @return the building stats
     * @throws IllegalArgumentException if the building type is not registered
     */
    public BuildingStats getBuildingStats(BuildingType type) {
        BuildingStats stats = buildingStats.get(type);
        if (stats == null) {
            throw new IllegalArgumentException("No stats registered for building type: " + type);
        }
        return stats;
    }

    /**
     * Returns the credit cost to produce the given unit type.
     * Delegates to {@link UnitStats#costCredits()}.
     *
     * @param type the unit type
     * @return the credit cost
     */
    public int getUnitCost(UnitType type) {
        return getUnitStats(type).costCredits();
    }

    /**
     * Returns the credit cost to construct the given building type.
     * Delegates to {@link BuildingStats#costCredits()}.
     *
     * @param type the building type
     * @return the credit cost
     */
    public int getBuildingCost(BuildingType type) {
        return getBuildingStats(type).costCredits();
    }

    /**
     * Returns the build time (in ticks) for the given unit type.
     * Delegates to {@link UnitStats#buildTime()}.
     *
     * @param type the unit type
     * @return the build time in ticks
     */
    public int getUnitBuildTime(UnitType type) {
        return getUnitStats(type).buildTime();
    }

    /**
     * Returns the tech requirement ID for the given unit type.
     * A value of 0 means no tech requirement (available from start).
     *
     * @param type the unit type
     * @return the tech requirement ID
     */
    public int getUnitTechRequirement(UnitType type) {
        return unitTechRequirements.getOrDefault(type, 0);
    }

    /**
     * Returns an unmodifiable view of all registered unit stats.
     *
     * @return map of unit type to unit stats
     */
    public Map<UnitType, UnitStats> getAllUnitStats() {
        return Collections.unmodifiableMap(unitStats);
    }

    /**
     * Returns an unmodifiable view of all registered building stats.
     *
     * @return map of building type to building stats
     */
    public Map<BuildingType, BuildingStats> getAllBuildingStats() {
        return Collections.unmodifiableMap(buildingStats);
    }

    // =========================================================================
    // Unit stats initialization
    // REF: complete_unit_stats.json
    // =========================================================================

    private void initUnitStats() {
        // --- Confederation Infantry ---
        // REF: complete_unit_stats.json — CONFED_INFANTRY
        unitStats.put(UnitType.CONFED_INFANTRY, new UnitStats(
            UnitType.CONFED_INFANTRY, "Infantry",
            40, 2, 5, 5, 0, 4, 4,
            WeaponType.BULLET, 5,
            4, 10, 650, 6, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — CONFED_GRENADIER
        unitStats.put(UnitType.CONFED_GRENADIER, new UnitStats(
            UnitType.CONFED_GRENADIER, "Grenadier",
            40, 2, 6, 5, 0, 4, 4,
            WeaponType.ROCKET, 8,
            5, 10, 200, 6, 255, 0, -1
        ));

        // --- Confederation Vehicles ---
        // REF: complete_unit_stats.json — CONFED_FLAME_ASSAULT
        unitStats.put(UnitType.CONFED_FLAME_ASSAULT, new UnitStats(
            UnitType.CONFED_FLAME_ASSAULT, "Flame Assault",
            50, 4, 6, 5, 0, 5, 6,
            WeaponType.FLAME, 3,
            9, 20, 300, 12, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — CONFED_FORTRESS
        unitStats.put(UnitType.CONFED_FORTRESS, new UnitStats(
            UnitType.CONFED_FORTRESS, "AV-40 Fortress",
            50, 4, 7, 5, 1, 5, 9,
            WeaponType.MACHINE_GUN, 10,
            10, 20, 350, 12, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — CONFED_HAMMER
        unitStats.put(UnitType.CONFED_HAMMER, new UnitStats(
            UnitType.CONFED_HAMMER, "T-21 Hammer",
            50, 8, 7, 9, 0, 6, 6,
            WeaponType.ARTILLERY, 8,
            11, 40, 350, 8, 14, 1, 10
        ));

        // REF: complete_unit_stats.json — CONFED_ZEUS
        unitStats.put(UnitType.CONFED_ZEUS, new UnitStats(
            UnitType.CONFED_ZEUS, "T-22 Zeus",
            70, 6, 7, 5, 0, 2, 6,
            WeaponType.MACHINE_GUN, 2,
            14, 30, 300, 8, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — CONFED_TORRENT
        unitStats.put(UnitType.CONFED_TORRENT, new UnitStats(
            UnitType.CONFED_TORRENT, "MLRS Torrent",
            80, 15, 4, 7, 2, 6, 6,
            WeaponType.ROCKET, 12,
            7, 50, 250, 8, 255, 0, -1
        ));

        // --- Confederation Mines ---
        // REF: complete_unit_stats.json — CONFED_MINE_SCORPIO
        // ASSUMPTION: attackRange = sightRange (8) for mines; not explicitly in RE data
        unitStats.put(UnitType.CONFED_MINE_SCORPIO, new UnitStats(
            UnitType.CONFED_MINE_SCORPIO, "Mine Scorpio",
            110, 60, 6, 9, 0, 8, 8,
            WeaponType.NONE, 1,
            1, 150, 0, 0, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — CONFED_MINE_FROG
        // ASSUMPTION: attackRange = sightRange (13) for mines; not explicitly in RE data
        unitStats.put(UnitType.CONFED_MINE_FROG, new UnitStats(
            UnitType.CONFED_MINE_FROG, "Mine Frog",
            100, 80, 9, 12, 0, 13, 13,
            WeaponType.NONE, 1,
            1, 250, 0, 0, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — CONFED_MINE_LIZARD
        // ASSUMPTION: attackRange = sightRange (7) for mines; not explicitly in RE data
        unitStats.put(UnitType.CONFED_MINE_LIZARD, new UnitStats(
            UnitType.CONFED_MINE_LIZARD, "Mine Lizard",
            120, 90, 8, 8, 0, 7, 7,
            WeaponType.NONE, 1,
            1, 220, 0, 0, 255, 0, -1
        ));

        // --- Resistance Infantry ---
        // REF: complete_unit_stats.json — REBEL_INFANTRY
        unitStats.put(UnitType.REBEL_INFANTRY, new UnitStats(
            UnitType.REBEL_INFANTRY, "Infantry",
            40, 2, 5, 4, 0, 9, 5,
            WeaponType.BULLET, 5,
            4, 10, 650, 6, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — REBEL_GRENADIER
        unitStats.put(UnitType.REBEL_GRENADIER, new UnitStats(
            UnitType.REBEL_GRENADIER, "Grenadier",
            40, 2, 6, 4, 0, 10, 5,
            WeaponType.ROCKET, 8,
            5, 10, 200, 6, 255, 0, -1
        ));

        // --- Resistance Vehicles ---
        // REF: complete_unit_stats.json — REBEL_SNIPER
        unitStats.put(UnitType.REBEL_SNIPER, new UnitStats(
            UnitType.REBEL_SNIPER, "Sniper",
            35, 8, 4, 6, 0, 15, 7,
            WeaponType.SNIPER_RIFLE, 15,
            8, 25, 300, 8, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — REBEL_COYOTE
        unitStats.put(UnitType.REBEL_COYOTE, new UnitStats(
            UnitType.REBEL_COYOTE, "Coyote",
            45, 4, 8, 6, 0, 25, 7,
            WeaponType.MACHINE_GUN, 4,
            9, 20, 350, 7, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — REBEL_ARMADILLO
        unitStats.put(UnitType.REBEL_ARMADILLO, new UnitStats(
            UnitType.REBEL_ARMADILLO, "Armadillo",
            50, 4, 7, 6, 1, 18, 10,
            WeaponType.MACHINE_GUN, 6,
            10, 20, 350, 12, 255, 0, -1
        ));

        // REF: complete_unit_stats.json — REBEL_RHINO
        unitStats.put(UnitType.REBEL_RHINO, new UnitStats(
            UnitType.REBEL_RHINO, "Rhino",
            50, 8, 7, 4, 0, 11, 7,
            WeaponType.ARTILLERY, 8,
            11, 40, 350, 8, 14, 1, -1
        ));

        // REF: complete_unit_stats.json — REBEL_PORCUPINE
        unitStats.put(UnitType.REBEL_PORCUPINE, new UnitStats(
            UnitType.REBEL_PORCUPINE, "MMC Porcupine",
            80, 15, 4, 6, 2, 35, 12,
            WeaponType.ROCKET, 10,
            7, 50, 250, 8, 255, 0, -1
        ));
    }

    // =========================================================================
    // Building stats initialization
    // REF: complete_building_stats.json
    // =========================================================================

    private void initBuildingStats() {
        // --- Confederation Buildings ---

        // REF: complete_building_stats.json — CONFED_COMMAND_CENTRE
        buildingStats.put(BuildingType.CONFED_COMMAND_CENTRE, new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE,
            120, 22, 0, 7, 4, 2, 20, 7, 8,
            2, 6, 0, 0,
            100, 450, 0,
            WeaponType.NONE,
            List.of(300, 200, 200)
        ));

        // REF: complete_building_stats.json — CONFED_GENERATOR
        buildingStats.put(BuildingType.CONFED_GENERATOR, new BuildingStats(
            BuildingType.CONFED_GENERATOR,
            100, 14, 0, 7, 3, 6, 8, 7, 8,
            2, 6, 0, 0,
            80, 550, 0,
            WeaponType.NONE,
            List.of(300, 350, 250)
        ));

        // REF: complete_building_stats.json — CONFED_INFANTRY_CENTRE
        buildingStats.put(BuildingType.CONFED_INFANTRY_CENTRE, new BuildingStats(
            BuildingType.CONFED_INFANTRY_CENTRE,
            100, 28, 0, 7, 3, 6, 18, 11, 12,
            2, 2, 0, 0,
            110, 200, 0,
            WeaponType.NONE,
            List.of(300, 500, 500)
        ));

        // REF: complete_building_stats.json — CONFED_MACHINE_FACTORY
        buildingStats.put(BuildingType.CONFED_MACHINE_FACTORY, new BuildingStats(
            BuildingType.CONFED_MACHINE_FACTORY,
            110, 36, 0, 8, 4, 6, 25, 3, 2,
            0, 0, 0, 3,
            160, 250, 0,
            WeaponType.NONE,
            List.of(500, 400, 500)
        ));

        // REF: complete_building_stats.json — CONFED_TECH_CENTRE
        buildingStats.put(BuildingType.CONFED_TECH_CENTRE, new BuildingStats(
            BuildingType.CONFED_TECH_CENTRE,
            120, 65, 0, 8, 4, 7, 30, 6, 16,
            0, 0, 4, 1,
            250, 300, 0,
            WeaponType.NONE,
            List.of(600, 800, 700)
        ));

        // REF: complete_building_stats.json — CONFED_BUNKER
        buildingStats.put(BuildingType.CONFED_BUNKER, new BuildingStats(
            BuildingType.CONFED_BUNKER,
            120, 50, 7, 8, 4, 7, 30, 6, 16,
            0, 0, 4, 1,
            220, 250, 5,
            WeaponType.BULLET,
            List.of(300, 500, 250)
        ));

        // REF: complete_building_stats.json — CONFED_LOCATOR
        buildingStats.put(BuildingType.CONFED_LOCATOR, new BuildingStats(
            BuildingType.CONFED_LOCATOR,
            100, 55, 0, 8, 4, 7, 50, 7, 16,
            0, 0, 6, 1,
            300, 200, 0,
            WeaponType.NONE,
            List.of(400, 300, 500)
        ));

        // REF: complete_building_stats.json — CONFED_ROCKET_LAUNCHER
        buildingStats.put(BuildingType.CONFED_ROCKET_LAUNCHER, new BuildingStats(
            BuildingType.CONFED_ROCKET_LAUNCHER,
            50, 4, 7, 12, 1, 9, 12, 8, 16,
            0, 0, 6, 1,
            40, 250, 0,
            WeaponType.ROCKET,
            List.of(1000, 700, 350)
        ));

        // --- Resistance Buildings ---
        // REF: complete_building_stats.json — symmetrical to Confederation

        // ASSUMPTION: REBEL_HEADQUARTERS stats copied from CONFED_COMMAND_CENTRE — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_HEADQUARTERS, new BuildingStats(
            BuildingType.REBEL_HEADQUARTERS,
            120, 22, 0, 7, 4, 2, 20, 7, 8,
            2, 6, 0, 0,
            100, 450, 0,
            WeaponType.NONE,
            List.of(300, 200, 200)
        ));

        // ASSUMPTION: REBEL_POWERPLANT stats copied from CONFED_GENERATOR — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_POWERPLANT, new BuildingStats(
            BuildingType.REBEL_POWERPLANT,
            100, 14, 0, 7, 3, 6, 8, 7, 8,
            2, 6, 0, 0,
            80, 550, 0,
            WeaponType.NONE,
            List.of(300, 350, 250)
        ));

        // ASSUMPTION: REBEL_BARRACKS stats copied from CONFED_INFANTRY_CENTRE — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_BARRACKS, new BuildingStats(
            BuildingType.REBEL_BARRACKS,
            100, 28, 0, 7, 3, 6, 18, 11, 12,
            2, 2, 0, 0,
            110, 200, 0,
            WeaponType.NONE,
            List.of(300, 500, 500)
        ));

        // ASSUMPTION: REBEL_FACTORY stats copied from CONFED_MACHINE_FACTORY — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_FACTORY, new BuildingStats(
            BuildingType.REBEL_FACTORY,
            110, 36, 0, 8, 4, 6, 25, 3, 2,
            0, 0, 0, 3,
            160, 250, 0,
            WeaponType.NONE,
            List.of(500, 400, 500)
        ));

        // ASSUMPTION: REBEL_LABORATORY stats copied from CONFED_TECH_CENTRE — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_LABORATORY, new BuildingStats(
            BuildingType.REBEL_LABORATORY,
            120, 65, 0, 8, 4, 7, 30, 6, 16,
            0, 0, 4, 1,
            250, 300, 0,
            WeaponType.NONE,
            List.of(600, 800, 700)
        ));

        // ASSUMPTION: REBEL_BUNKER stats copied from CONFED_BUNKER — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_BUNKER, new BuildingStats(
            BuildingType.REBEL_BUNKER,
            120, 50, 7, 8, 4, 7, 30, 6, 16,
            0, 0, 4, 1,
            220, 250, 5,
            WeaponType.BULLET,
            List.of(300, 500, 250)
        ));

        // REBEL_TOWER: Same as CONFED_ROCKET_LAUNCHER but type=REBEL, weaponType=MACHINE_GUN
        // FIX: upgrade_costs corrected to [400, 300, 500] per RE spec (was incorrectly [1000, 700, 350] which is Wall's costs)
        // ASSUMPTION: REBEL_TOWER stats copied from CONFED_ROCKET_LAUNCHER — RE only provides upgrade_costs for rebel buildings
        buildingStats.put(BuildingType.REBEL_TOWER, new BuildingStats(
            BuildingType.REBEL_TOWER,
            50, 4, 7, 12, 1, 9, 12, 8, 16,
            0, 0, 6, 1,
            40, 250, 0,
            WeaponType.MACHINE_GUN,
            List.of(400, 300, 500)
        ));

        // ASSUMPTION: REBEL_WALL stats fabricated — RE only provides upgrade_costs [1000, 700, 350]
        buildingStats.put(BuildingType.REBEL_WALL, new BuildingStats(
            BuildingType.REBEL_WALL,
            200, 10, 0, 15, 0, 0, 10, 0, 20,
            0, 0, 0, 0,
            10, 5, 0,
            WeaponType.NONE,
            List.of(1000, 700, 350)
        ));
    }

    // =========================================================================
    // Unit tech requirements
    // REF: complete_unit_stats.json — availabilityFlag and tech tree mapping
    // =========================================================================

    private void initUnitTechRequirements() {
        // CONFED_FLAME_ASSAULT -> 5 (Forced light missiles = tech ID 5)
        // FIX: Changed from 6 to 5 per RE spec
        unitTechRequirements.put(UnitType.CONFED_FLAME_ASSAULT, 5);

        // CONFED_HAMMER -> 0 (available from start)
        // FIX: availability_flag=10 is in a different namespace than the 8-tech system.
        // The Hammer requires Machine Factory (tech_requirement=3 on the building), not a specific research.
        unitTechRequirements.put(UnitType.CONFED_HAMMER, 0);

        // CONFED_TORRENT: REMOVED — availability_flag=-1 means available from start, no research needed

        // REBEL_SNIPER -> 4 (Snipers tech)
        // (correct — no change needed)
        unitTechRequirements.put(UnitType.REBEL_SNIPER, 4);

        // REBEL_RHINO -> 7 (Reinforced engine = tech ID 7)
        // FIX: Changed from 12 to 7 per RE spec
        unitTechRequirements.put(UnitType.REBEL_RHINO, 7);

        // REBEL_PORCUPINE -> 0 (available from start)
        // ASSUMPTION: RE spec doesn't have a specific 8-tech ID that unlocks Porcupine.
        // Set to 0 (available from start) pending further RE investigation.
        unitTechRequirements.put(UnitType.REBEL_PORCUPINE, 0);

        // All other units have tech requirement 0 (available from start)
        // EnumMap defaults to 0 via getOrDefault in getUnitTechRequirement()
    }
}
