package com.aow2.common.config;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StatsRegistry}.
 * Verifies that all unit and building stats match the RE data from
 * complete_unit_stats.json and complete_building_stats.json.
 */
class StatsRegistryTest {

    private static StatsRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = StatsRegistry.getInstance();
    }

    // =========================================================================
    // Unit stats completeness
    // =========================================================================

    @Nested
    @DisplayName("Unit stats completeness")
    class UnitStatsCompleteness {

        @Test
        @DisplayName("All 17 unit types have stats in the registry")
        void allUnitTypesRegistered() {
            Map<UnitType, UnitStats> allStats = registry.getAllUnitStats();
            assertEquals(17, allStats.size(), "Expected 17 unit types registered");
            for (UnitType type : UnitType.values()) {
                assertTrue(allStats.containsKey(type), "Missing stats for: " + type);
            }
        }

        @Test
        @DisplayName("getAllUnitStats returns unmodifiable map")
        void unitStatsMapIsUnmodifiable() {
            Map<UnitType, UnitStats> stats = registry.getAllUnitStats();
            assertThrows(UnsupportedOperationException.class, () ->
                stats.put(UnitType.CONFED_INFANTRY, stats.get(UnitType.CONFED_INFANTRY))
            );
        }
    }

    // =========================================================================
    // Building stats completeness
    // =========================================================================

    @Nested
    @DisplayName("Building stats completeness")
    class BuildingStatsCompleteness {

        @Test
        @DisplayName("All 16 building types have stats in the registry")
        void allBuildingTypesRegistered() {
            Map<BuildingType, BuildingStats> allStats = registry.getAllBuildingStats();
            assertEquals(16, allStats.size(), "Expected 16 building types registered");
            for (BuildingType type : BuildingType.values()) {
                assertTrue(allStats.containsKey(type), "Missing stats for: " + type);
            }
        }

        @Test
        @DisplayName("getAllBuildingStats returns unmodifiable map")
        void buildingStatsMapIsUnmodifiable() {
            Map<BuildingType, BuildingStats> stats = registry.getAllBuildingStats();
            assertThrows(UnsupportedOperationException.class, () ->
                stats.put(BuildingType.CONFED_COMMAND_CENTRE, stats.get(BuildingType.CONFED_COMMAND_CENTRE))
            );
        }
    }

    // =========================================================================
    // Confederation Infantry specific stat values
    // REF: complete_unit_stats.json — CONFED_INFANTRY
    // =========================================================================

    @Nested
    @DisplayName("Confederation Infantry stats (spot-check)")
    class ConfedInfantryStats {

        @Test
        @DisplayName("Confederation Infantry stats match RE data exactly")
        void confedInfantryExactStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_INFANTRY);
            assertAll("CONFED_INFANTRY stats",
                () -> assertEquals(40, s.hp()),
                () -> assertEquals(2, s.damage()),
                () -> assertEquals(5, s.speed()),
                () -> assertEquals(5, s.armor()),
                () -> assertEquals(4, s.sightRange()),
                () -> assertEquals(4, s.attackRange()),
                () -> assertEquals(10, s.costCredits()),
                () -> assertEquals(650, s.rewardCredits()),
                () -> assertEquals(WeaponType.BULLET, s.weaponType()),
                () -> assertEquals(5, s.attackSpeed()),
                () -> assertEquals(4, s.buildTime()),
                () -> assertEquals(0, s.attackBonus()),
                () -> assertEquals(6, s.extendedArmor()),
                () -> assertEquals(255, s.siegeTargets()),
                () -> assertEquals(0, s.upgradeLevel()),
                () -> assertEquals(-1, s.availabilityFlag())
            );
        }
    }

    // =========================================================================
    // Command Centre stats
    // REF: complete_building_stats.json — CONFED_COMMAND_CENTRE
    // =========================================================================

    @Nested
    @DisplayName("Command Centre stats (spot-check)")
    class CommandCentreStats {

        @Test
        @DisplayName("Command Centre stats match RE data exactly")
        void commandCentreExactStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.CONFED_COMMAND_CENTRE);
            assertAll("CONFED_COMMAND_CENTRE stats",
                () -> assertEquals(120, s.hp()),
                () -> assertEquals(6, s.powerProduce()),
                () -> assertEquals(2, s.powerConsume()),
                () -> assertEquals(100, s.costCredits()),
                () -> assertEquals(0, s.queueSlots()),
                () -> assertEquals(450, s.rewardCredits()),
                () -> assertEquals(WeaponType.NONE, s.weaponType()),
                () -> assertEquals(22, s.baseCost()),
                () -> assertEquals(7, s.attackSpeed()),
                () -> assertEquals(7, s.armor()),
                () -> assertEquals(4, s.attackBonus()),
                () -> assertEquals(2, s.sightRange()),
                () -> assertEquals(20, s.buildTime()),
                () -> assertEquals(7, s.attackRange()),
                () -> assertEquals(8, s.extendedArmor()),
                () -> assertEquals(0, s.techRequirement()),
                () -> assertEquals(0, s.garrisonCapacity())
            );
        }
    }

    // =========================================================================
    // Generator stats
    // REF: complete_building_stats.json — CONFED_GENERATOR
    // =========================================================================

    @Nested
    @DisplayName("Generator stats (spot-check)")
    class GeneratorStats {

        @Test
        @DisplayName("Generator stats match RE data exactly")
        void generatorExactStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.CONFED_GENERATOR);
            assertAll("CONFED_GENERATOR stats",
                () -> assertEquals(100, s.hp()),
                () -> assertEquals(6, s.powerProduce()),
                () -> assertEquals(2, s.powerConsume()),
                () -> assertEquals(80, s.costCredits()),
                () -> assertEquals(WeaponType.NONE, s.weaponType())
            );
        }
    }

    // =========================================================================
    // Mine Scorpio stats
    // REF: complete_unit_stats.json — CONFED_MINE_SCORPIO
    // =========================================================================

    @Nested
    @DisplayName("Mine Scorpio stats (spot-check)")
    class MineScorpioStats {

        @Test
        @DisplayName("Mine Scorpio stats match RE data exactly")
        void mineScorpioExactStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_MINE_SCORPIO);
            assertAll("CONFED_MINE_SCORPIO stats",
                () -> assertEquals(110, s.hp()),
                () -> assertEquals(60, s.damage()),
                () -> assertEquals(150, s.costCredits()),
                () -> assertEquals(WeaponType.NONE, s.weaponType()),
                () -> assertEquals(1, s.attackSpeed()),
                () -> assertEquals(1, s.buildTime()),
                () -> assertEquals(0, s.rewardCredits()),
                () -> assertEquals(0, s.extendedArmor()),
                () -> assertEquals(255, s.siegeTargets()),
                () -> assertEquals(9, s.armor()),
                () -> assertEquals(8, s.sightRange())
            );
        }
    }

    // =========================================================================
    // Delegation methods
    // =========================================================================

    @Nested
    @DisplayName("Delegation methods")
    class DelegationMethods {

        @Test
        @DisplayName("getUnitCost delegates to UnitStats.costCredits()")
        void getUnitCostDelegates() {
            assertEquals(10, registry.getUnitCost(UnitType.CONFED_INFANTRY));
            assertEquals(40, registry.getUnitCost(UnitType.CONFED_HAMMER));
            assertEquals(50, registry.getUnitCost(UnitType.CONFED_TORRENT));
            assertEquals(150, registry.getUnitCost(UnitType.CONFED_MINE_SCORPIO));
            assertEquals(25, registry.getUnitCost(UnitType.REBEL_SNIPER));
        }

        @Test
        @DisplayName("getBuildingCost delegates to BuildingStats.costCredits()")
        void getBuildingCostDelegates() {
            assertEquals(100, registry.getBuildingCost(BuildingType.CONFED_COMMAND_CENTRE));
            assertEquals(80, registry.getBuildingCost(BuildingType.CONFED_GENERATOR));
            assertEquals(110, registry.getBuildingCost(BuildingType.CONFED_INFANTRY_CENTRE));
            assertEquals(160, registry.getBuildingCost(BuildingType.CONFED_MACHINE_FACTORY));
            assertEquals(40, registry.getBuildingCost(BuildingType.CONFED_ROCKET_LAUNCHER));
            assertEquals(100, registry.getBuildingCost(BuildingType.REBEL_HEADQUARTERS));
            assertEquals(10, registry.getBuildingCost(BuildingType.REBEL_WALL));
        }

        @Test
        @DisplayName("getUnitBuildTime returns correct values")
        void getUnitBuildTimeReturnsCorrectValues() {
            assertEquals(4, registry.getUnitBuildTime(UnitType.CONFED_INFANTRY));
            assertEquals(5, registry.getUnitBuildTime(UnitType.CONFED_GRENADIER));
            assertEquals(9, registry.getUnitBuildTime(UnitType.CONFED_FLAME_ASSAULT));
            assertEquals(10, registry.getUnitBuildTime(UnitType.CONFED_FORTRESS));
            assertEquals(11, registry.getUnitBuildTime(UnitType.CONFED_HAMMER));
            assertEquals(14, registry.getUnitBuildTime(UnitType.CONFED_ZEUS));
            assertEquals(7, registry.getUnitBuildTime(UnitType.CONFED_TORRENT));
            assertEquals(1, registry.getUnitBuildTime(UnitType.CONFED_MINE_SCORPIO));
            assertEquals(8, registry.getUnitBuildTime(UnitType.REBEL_SNIPER));
            assertEquals(7, registry.getUnitBuildTime(UnitType.REBEL_PORCUPINE));
        }
    }

    // =========================================================================
    // Unit tech requirements
    // =========================================================================

    @Nested
    @DisplayName("Unit tech requirements")
    class UnitTechRequirements {

        @Test
        @DisplayName("Units with tech requirements return correct values")
        void techRequirementsWithValues() {
            assertEquals(5, registry.getUnitTechRequirement(UnitType.CONFED_FLAME_ASSAULT));
            assertEquals(4, registry.getUnitTechRequirement(UnitType.REBEL_SNIPER));
            assertEquals(7, registry.getUnitTechRequirement(UnitType.REBEL_RHINO));
        }

        @Test
        @DisplayName("Units without tech requirements return 0")
        void techRequirementsZero() {
            assertEquals(0, registry.getUnitTechRequirement(UnitType.CONFED_INFANTRY));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.CONFED_GRENADIER));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.CONFED_FORTRESS));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.CONFED_HAMMER));
            // CONFED_TORRENT: availability_flag=-1 means available from start
            assertEquals(0, registry.getUnitTechRequirement(UnitType.CONFED_TORRENT));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.CONFED_ZEUS));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.REBEL_INFANTRY));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.REBEL_GRENADIER));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.REBEL_COYOTE));
            assertEquals(0, registry.getUnitTechRequirement(UnitType.REBEL_ARMADILLO));
            // ASSUMPTION: REBEL_PORCUPINE — no specific tech ID found in RE spec
            assertEquals(0, registry.getUnitTechRequirement(UnitType.REBEL_PORCUPINE));
        }
    }

    // =========================================================================
    // Additional spot-checks for key unit stats
    // REF: complete_unit_stats.json
    // =========================================================================

    @Nested
    @DisplayName("Key unit stat spot-checks")
    class KeyUnitStatSpotChecks {

        @Test
        @DisplayName("CONFED_HAMMER has ARTILLERY weapon and siegeTargets=14")
        void confedHammerStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_HAMMER);
            assertEquals(WeaponType.ARTILLERY, s.weaponType());
            assertEquals(14, s.siegeTargets());
            assertEquals(1, s.upgradeLevel());
            assertEquals(10, s.availabilityFlag());
            assertEquals(8, s.attackSpeed());
        }

        @Test
        @DisplayName("CONFED_FORTRESS has MACHINE_GUN and attackBonus=1")
        void confedFortressStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_FORTRESS);
            assertEquals(WeaponType.MACHINE_GUN, s.weaponType());
            assertEquals(1, s.attackBonus());
            assertEquals(9, s.attackRange());
            assertEquals(10, s.attackSpeed());
        }

        @Test
        @DisplayName("CONFED_TORRENT has ROCKET weapon and attackBonus=2")
        void confedTorrentStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_TORRENT);
            assertEquals(WeaponType.ROCKET, s.weaponType());
            assertEquals(2, s.attackBonus());
            assertEquals(12, s.attackSpeed());
        }

        @Test
        @DisplayName("REBEL_SNIPER has SNIPER_RIFLE and high sightRange=15")
        void rebelSniperStats() {
            UnitStats s = registry.getUnitStats(UnitType.REBEL_SNIPER);
            assertEquals(WeaponType.SNIPER_RIFLE, s.weaponType());
            assertEquals(15, s.sightRange());
            assertEquals(15, s.attackSpeed());
            assertEquals(8, s.damage());
        }

        @Test
        @DisplayName("REBEL_PORCUPINE has ROCKET and sightRange=35, attackRange=12")
        void rebelPorcupineStats() {
            UnitStats s = registry.getUnitStats(UnitType.REBEL_PORCUPINE);
            assertEquals(WeaponType.ROCKET, s.weaponType());
            assertEquals(35, s.sightRange());
            assertEquals(12, s.attackRange());
            assertEquals(10, s.attackSpeed());
        }

        @Test
        @DisplayName("Mines have NONE weapon type and buildTime=1")
        void minesStats() {
            assertEquals(WeaponType.NONE, registry.getUnitStats(UnitType.CONFED_MINE_SCORPIO).weaponType());
            assertEquals(WeaponType.NONE, registry.getUnitStats(UnitType.CONFED_MINE_FROG).weaponType());
            assertEquals(WeaponType.NONE, registry.getUnitStats(UnitType.CONFED_MINE_LIZARD).weaponType());
            assertEquals(1, registry.getUnitStats(UnitType.CONFED_MINE_SCORPIO).buildTime());
            assertEquals(1, registry.getUnitStats(UnitType.CONFED_MINE_FROG).buildTime());
            assertEquals(1, registry.getUnitStats(UnitType.CONFED_MINE_LIZARD).buildTime());
        }

        @Test
        @DisplayName("Mine Frog has damage=80, armor=12, speed=9")
        void mineFrogStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_MINE_FROG);
            assertEquals(80, s.damage());
            assertEquals(12, s.armor());
            assertEquals(9, s.speed());
            assertEquals(250, s.costCredits());
        }

        @Test
        @DisplayName("Mine Lizard has damage=90, hp=120, armor=8")
        void mineLizardStats() {
            UnitStats s = registry.getUnitStats(UnitType.CONFED_MINE_LIZARD);
            assertEquals(90, s.damage());
            assertEquals(120, s.hp());
            assertEquals(8, s.armor());
            assertEquals(220, s.costCredits());
        }
    }

    // =========================================================================
    // Additional building stat spot-checks
    // REF: complete_building_stats.json
    // =========================================================================

    @Nested
    @DisplayName("Key building stat spot-checks")
    class KeyBuildingStatSpotChecks {

        @Test
        @DisplayName("CONFED_BUNKER has BULLET weapon and garrisonCapacity=5")
        void confedBunkerStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.CONFED_BUNKER);
            assertEquals(WeaponType.BULLET, s.weaponType());
            assertEquals(5, s.garrisonCapacity());
            assertEquals(220, s.costCredits());
            assertEquals(1, s.techRequirement());
        }

        @Test
        @DisplayName("CONFED_ROCKET_LAUNCHER has ROCKET weapon")
        void confedRocketLauncherStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.CONFED_ROCKET_LAUNCHER);
            assertEquals(WeaponType.ROCKET, s.weaponType());
            assertEquals(50, s.hp());
            assertEquals(12, s.armor());
            assertEquals(40, s.costCredits());
            assertEquals(8, s.attackRange());
        }

        @Test
        @DisplayName("CONFED_MACHINE_FACTORY has techRequirement=3 and queueSlots=0")
        void confedMachineFactoryStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.CONFED_MACHINE_FACTORY);
            assertEquals(3, s.techRequirement());
            assertEquals(0, s.queueSlots());
            assertEquals(160, s.costCredits());
        }

        @Test
        @DisplayName("CONFED_TECH_CENTRE has queueSlots=4 and techRequirement=1")
        void confedTechCentreStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.CONFED_TECH_CENTRE);
            assertEquals(4, s.queueSlots());
            assertEquals(1, s.techRequirement());
            assertEquals(250, s.costCredits());
        }

        @Test
        @DisplayName("REBEL_TOWER has MACHINE_GUN weapon (not ROCKET)")
        void rebelTowerStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.REBEL_TOWER);
            assertEquals(WeaponType.MACHINE_GUN, s.weaponType());
            assertEquals(40, s.costCredits());
        }

        @Test
        @DisplayName("REBEL_WALL has high hp=200 and armor=15")
        void rebelWallStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.REBEL_WALL);
            assertEquals(200, s.hp());
            assertEquals(15, s.armor());
            assertEquals(20, s.extendedArmor());
            assertEquals(0, s.attackSpeed());
            assertEquals(0, s.sightRange());
            assertEquals(0, s.attackRange());
            assertEquals(WeaponType.NONE, s.weaponType());
            assertEquals(10, s.costCredits());
            assertEquals(5, s.rewardCredits());
        }

        @Test
        @DisplayName("Rebel buildings have correct costCredits")
        void rebelBuildingCosts() {
            assertEquals(100, registry.getBuildingCost(BuildingType.REBEL_HEADQUARTERS));
            assertEquals(80, registry.getBuildingCost(BuildingType.REBEL_POWERPLANT));
            assertEquals(110, registry.getBuildingCost(BuildingType.REBEL_BARRACKS));
            assertEquals(160, registry.getBuildingCost(BuildingType.REBEL_FACTORY));
            assertEquals(250, registry.getBuildingCost(BuildingType.REBEL_LABORATORY));
            assertEquals(220, registry.getBuildingCost(BuildingType.REBEL_BUNKER));
            assertEquals(40, registry.getBuildingCost(BuildingType.REBEL_TOWER));
            assertEquals(10, registry.getBuildingCost(BuildingType.REBEL_WALL));
        }

        @Test
        @DisplayName("REBEL_BUNKER has BULLET weapon and garrisonCapacity=5")
        void rebelBunkerStats() {
            BuildingStats s = registry.getBuildingStats(BuildingType.REBEL_BUNKER);
            assertEquals(WeaponType.BULLET, s.weaponType());
            assertEquals(5, s.garrisonCapacity());
        }
    }

    // =========================================================================
    // Singleton behavior
    // =========================================================================

    @Nested
    @DisplayName("Singleton behavior")
    class SingletonBehavior {

        @Test
        @DisplayName("getInstance returns same instance")
        void singletonReturnsSameInstance() {
            StatsRegistry a = StatsRegistry.getInstance();
            StatsRegistry b = StatsRegistry.getInstance();
            assertSame(a, b);
        }
    }
}
