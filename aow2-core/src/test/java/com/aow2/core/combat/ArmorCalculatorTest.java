package com.aow2.core.combat;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ArmorCalculator}.
 * Naming convention: shouldXxxWhenYyy
 * Structure: Given-When-Then
 */
class ArmorCalculatorTest {

    private ArmorCalculator armorCalculator;

    // Test unit stats
    private static final UnitStats INFANTRY_STATS = new UnitStats(
        UnitType.CONFED_INFANTRY, "Infantry", 40, 2, 1, 5, 5,
        0, 4, 4, 4, 10, 650, 6, 255, 0, -1);

    private static final UnitStats VEHICLE_STATS = new UnitStats(
        UnitType.CONFED_ZEUS, "T-22 Zeus", 70, 6, 3, 7, 5,
        0, 2, 6, 14, 30, 300, 8, 255, 0, -1);

    private static final UnitStats REBEL_INFANTRY_STATS = new UnitStats(
        UnitType.REBEL_INFANTRY, "Infantry", 40, 2, 1, 5, 5,
        0, 4, 4, 4, 10, 650, 6, 255, 0, -1);

    private static final UnitStats REBEL_VEHICLE_STATS = new UnitStats(
        UnitType.REBEL_RHINO, "Rhino", 80, 10, 6, 4, 7,
        2, 6, 5, 9, 50, 350, 8, 255, 0, -1);

    private static final BuildingStats BUILDING_STATS = new BuildingStats(
        BuildingType.CONFED_COMMAND_CENTRE, 120, 22, 7, 7,
        4, 2, 20, 7, 8, 2, 6, 0, 0, 100, 450, List.of(300, 200, 200));

    @BeforeEach
    void setUp() {
        armorCalculator = new ArmorCalculator();
    }

    @Test
    @DisplayName("Should return base armor without research")
    void shouldReturnBaseArmorWithoutResearch() {
        // Given
        Unit infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, INFANTRY_STATS);
        Set<Integer> noResearch = Set.of();

        // When
        int armor = armorCalculator.calculateEffectiveArmor(infantry, noResearch);

        // Then
        assertEquals(5, armor, "Base armor should be 5 for infantry");
    }

    @Test
    @DisplayName("Should apply infantry armor research from Confederation")
    void shouldApplyInfantryArmorResearch() {
        // Given
        Unit infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, INFANTRY_STATS);
        // Research ID 0: Energy Suit, +2 infantry armor
        Set<Integer> research = Set.of(0);

        // When
        int armor = armorCalculator.calculateEffectiveArmor(infantry, research);

        // Then
        assertEquals(7, armor, "Armor should be 5 (base) + 2 (research) = 7");
    }

    @Test
    @DisplayName("Should apply vehicle armor research from Resistance")
    void shouldApplyVehicleArmorResearch() {
        // Given
        Unit vehicle = new Unit(2, Faction.RESISTANCE, new GridPosition(20, 20),
            UnitType.REBEL_RHINO, REBEL_VEHICLE_STATS);
        // Research ID 9: Composite Armour, +2 vehicle armor
        Set<Integer> research = Set.of(9);

        // When
        int armor = armorCalculator.calculateEffectiveArmor(vehicle, research);

        // Then
        assertEquals(9, armor, "Armor should be 7 (base) + 2 (research) = 9");
    }

    @Test
    @DisplayName("Should return zero for buildings")
    void shouldReturnZeroForBuildings() {
        // Given
        Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, BUILDING_STATS);

        // When
        int armor = armorCalculator.calculateEffectiveArmor(building);

        // Then
        assertEquals(0, armor, "Buildings have 0 base armor per RE docs");
    }

    @Test
    @DisplayName("Should stack multiple armor bonuses from different researches")
    void shouldStackMultipleArmorBonuses() {
        // Given
        Unit infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, INFANTRY_STATS);
        // Research ID 0: +2 infantry armor
        // Research ID 24: +1 infantry armor
        Set<Integer> research = Set.of(0, 24);

        // When
        int armor = armorCalculator.calculateEffectiveArmor(infantry, research);

        // Then
        assertEquals(8, armor, "Armor should be 5 (base) + 2 (ID 0) + 1 (ID 24) = 8");
    }

    @Test
    @DisplayName("Should return zero bonus for vehicle research on infantry unit")
    void shouldNotApplyVehicleResearchToInfantry() {
        // Given
        Unit infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, INFANTRY_STATS);
        // Research ID 9: +2 vehicle armor (should not apply to infantry)
        Set<Integer> research = Set.of(9);

        // When
        int bonus = armorCalculator.getResearchArmorBonus(infantry, research);

        // Then
        assertEquals(0, bonus, "Vehicle armor research should not apply to infantry");
    }

    @Test
    @DisplayName("Should return zero bonus for infantry research on vehicle unit")
    void shouldNotApplyInfantryResearchToVehicle() {
        // Given
        Unit vehicle = new Unit(2, Faction.CONFEDERATION, new GridPosition(20, 20),
            UnitType.CONFED_ZEUS, VEHICLE_STATS);
        // Research ID 0: +2 infantry armor (should not apply to vehicles)
        Set<Integer> research = Set.of(0);

        // When
        int bonus = armorCalculator.getResearchArmorBonus(vehicle, research);

        // Then
        assertEquals(0, bonus, "Infantry armor research should not apply to vehicles");
    }

    @Test
    @DisplayName("Should return zero bonus when no research completed")
    void shouldReturnZeroBonusWithoutResearch() {
        // Given
        Unit infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, INFANTRY_STATS);
        Set<Integer> noResearch = Set.of();

        // When
        int bonus = armorCalculator.getResearchArmorBonus(infantry, noResearch);

        // Then
        assertEquals(0, bonus, "No research means no armor bonus");
    }

    @Test
    @DisplayName("Should calculate building armor with research override")
    void shouldCalculateBuildingArmorWithResearchOverride() {
        // Given
        Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, BUILDING_STATS);
        // Research ID 16: Player 0 building armour = 9
        Set<Integer> research = Set.of(16);

        // When
        int armor = armorCalculator.calculateEffectiveBuildingArmor(building, research);

        // Then
        assertEquals(9, armor, "Building armor should be overridden to 9");
    }

    @Test
    @DisplayName("Should calculate building armor with additive research")
    void shouldCalculateBuildingArmorWithAdditiveResearch() {
        // Given
        Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, BUILDING_STATS);
        // Research ID 4: Building armour +4
        Set<Integer> research = Set.of(4);

        // When
        int armor = armorCalculator.calculateEffectiveBuildingArmor(building, research);

        // Then
        assertEquals(4, armor, "Building armor should be 0 + 4 from additive research");
    }

    @Test
    @DisplayName("Should stack override and additive building armor research")
    void shouldStackOverrideAndAdditiveBuildingArmor() {
        // Given
        Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, BUILDING_STATS);
        // Research ID 4: +4 building armor
        // Research ID 16: building armor = 9 (override)
        Set<Integer> research = Set.of(4, 16);

        // When
        int armor = armorCalculator.calculateEffectiveBuildingArmor(building, research);

        // Then
        assertEquals(13, armor, "Building armor should be 9 (override) + 4 (additive) = 13");
    }

    @Test
    @DisplayName("Should return zero building armor without research")
    void shouldReturnZeroBuildingArmorWithoutResearch() {
        // Given
        Building building = new Building(1, Faction.CONFEDERATION, new GridPosition(32, 32),
            BuildingType.CONFED_COMMAND_CENTRE, BUILDING_STATS);
        Set<Integer> noResearch = Set.of();

        // When
        int armor = armorCalculator.calculateEffectiveBuildingArmor(building, noResearch);

        // Then
        assertEquals(0, armor, "Building armor should be 0 without research");
    }

    @Test
    @DisplayName("Should return zero bonus for null research set")
    void shouldReturnZeroBonusForNullResearch() {
        // Given
        Unit infantry = new Unit(1, Faction.CONFEDERATION, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, INFANTRY_STATS);

        // When
        int bonus = armorCalculator.getResearchArmorBonus(infantry, null);

        // Then
        assertEquals(0, bonus, "Null research set should produce zero bonus");
    }
}
