package com.aow2.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResearchNodeTest {

    // --- ResearchEffect tests ---

    @Nested
    @DisplayName("ResearchEffect")
    class ResearchEffectTests {

        @Test
        @DisplayName("Should create valid research effect")
        void shouldCreateValidEffect() {
            Set<UnitType> types = EnumSet.of(UnitType.CONFED_INFANTRY, UnitType.CONFED_GRENADIER);
            ResearchEffect effect = new ResearchEffect(ResearchEffect.StatType.ARMOR, 2, types);
            assertEquals(ResearchEffect.StatType.ARMOR, effect.statType());
            assertEquals(2, effect.value());
            assertTrue(effect.affectedUnitTypes().contains(UnitType.CONFED_INFANTRY));
            assertTrue(effect.affectedUnitTypes().contains(UnitType.CONFED_GRENADIER));
        }

        @Test
        @DisplayName("Should reject null stat type")
        void shouldRejectNullStatType() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchEffect(null, 2, EnumSet.of(UnitType.CONFED_INFANTRY)));
        }

        @Test
        @DisplayName("Should default to empty set when affectedUnitTypes is null")
        void shouldDefaultToEmptyWhenNull() {
            ResearchEffect effect = new ResearchEffect(ResearchEffect.StatType.DAMAGE, 5, null);
            assertNotNull(effect.affectedUnitTypes());
            assertTrue(effect.affectedUnitTypes().isEmpty());
        }

        @Test
        @DisplayName("Should detect affected unit type")
        void shouldDetectAffectedUnitType() {
            Set<UnitType> types = EnumSet.of(UnitType.CONFED_INFANTRY, UnitType.CONFED_GRENADIER);
            ResearchEffect effect = new ResearchEffect(ResearchEffect.StatType.ARMOR, 2, types);
            assertTrue(effect.affectsUnitType(UnitType.CONFED_INFANTRY));
            assertTrue(effect.affectsUnitType(UnitType.CONFED_GRENADIER));
            assertFalse(effect.affectsUnitType(UnitType.CONFED_FLAME_ASSAULT));
        }

        @Test
        @DisplayName("Should implement equals and hashCode correctly")
        void shouldImplementEqualsAndHashCode() {
            Set<UnitType> typesAB = EnumSet.of(UnitType.CONFED_INFANTRY, UnitType.CONFED_GRENADIER);
            ResearchEffect a = new ResearchEffect(ResearchEffect.StatType.ARMOR, 2, typesAB);
            ResearchEffect b = new ResearchEffect(ResearchEffect.StatType.ARMOR, 2, typesAB);
            ResearchEffect c = new ResearchEffect(ResearchEffect.StatType.DAMAGE, 2, typesAB);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertNotEquals(a, c);
        }

        @Test
        @DisplayName("Should produce meaningful toString")
        void shouldProduceToString() {
            Set<UnitType> types = EnumSet.of(UnitType.CONFED_FLAME_ASSAULT);
            ResearchEffect effect = new ResearchEffect(ResearchEffect.StatType.SPEED, 1, types);
            String str = effect.toString();
            assertTrue(str.contains("SPEED"));
            assertTrue(str.contains("1"));
        }
    }

    // --- ResearchNode tests ---

    @Nested
    @DisplayName("ResearchNode")
    class ResearchNodeTests {

        private ResearchEffect createInfantryArmorEffect() {
            return new ResearchEffect(ResearchEffect.StatType.ARMOR, 2,
                EnumSet.of(UnitType.CONFED_INFANTRY, UnitType.CONFED_GRENADIER));
        }

        @Test
        @DisplayName("Should create valid research node with no prerequisites")
        void shouldCreateValidNode() {
            ResearchEffect effect = createInfantryArmorEffect();
            ResearchNode node = new ResearchNode(0, "Reinforced Plating", Faction.CONFEDERATION,
                "Increases infantry armor by 2", 30, 300, List.of(),
                ResearchCategory.INFANTRY_ARMOR, effect);

            assertEquals(0, node.id());
            assertEquals("Reinforced Plating", node.name());
            assertEquals(Faction.CONFEDERATION, node.faction());
            assertEquals("Increases infantry armor by 2", node.description());
            assertEquals(30, node.cost());
            assertEquals(300, node.researchTime());
            assertFalse(node.hasPrerequisite());
            assertTrue(node.getPrerequisites().isEmpty());
            assertEquals(ResearchCategory.INFANTRY_ARMOR, node.category());
            assertEquals(effect, node.effect());
        }

        @Test
        @DisplayName("Should create valid research node with single prerequisite")
        void shouldCreateValidNodeWithPrerequisite() {
            ResearchEffect effect = createInfantryArmorEffect();
            ResearchNode node = new ResearchNode(1, "Advanced Targeting", Faction.CONFEDERATION,
                "desc", 50, 400, List.of(0),
                ResearchCategory.ENEMY_RANGE_REDUCTION, effect);

            assertTrue(node.hasPrerequisite());
            assertEquals(List.of(0), node.getPrerequisites());
        }

        @Test
        @DisplayName("Should create valid research node with anyOf prerequisites")
        void shouldCreateValidNodeWithAnyOfPrerequisites() {
            ResearchEffect effect = createInfantryArmorEffect();
            ResearchNode node = new ResearchNode(8, "Heavy Artillery", Faction.CONFEDERATION,
                "desc", 100, 500, List.of(6, 7),
                ResearchCategory.ATTACK_RANGE, effect);

            assertTrue(node.hasPrerequisite());
            assertEquals(2, node.getPrerequisites().size());
            assertTrue(node.getPrerequisites().contains(6));
            assertTrue(node.getPrerequisites().contains(7));
        }

        @Test
        @DisplayName("Should accept null prerequisites and treat as empty list")
        void shouldAcceptNullPrerequisites() {
            ResearchEffect effect = createInfantryArmorEffect();
            ResearchNode node = new ResearchNode(0, "Basic", Faction.CONFEDERATION, "desc",
                30, 300, null, ResearchCategory.INFANTRY_ARMOR, effect);

            assertFalse(node.hasPrerequisite());
            assertTrue(node.getPrerequisites().isEmpty());
        }

        @Test
        @DisplayName("Should reject ID below 0")
        void shouldRejectIdBelowZero() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(-1, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject ID above 47")
        void shouldRejectIdAbove47() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(48, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should accept boundary IDs 0 and 47")
        void shouldAcceptBoundaryIds() {
            assertDoesNotThrow(() -> new ResearchNode(0, "First", Faction.CONFEDERATION, "desc",
                10, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
            assertDoesNotThrow(() -> new ResearchNode(47, "Last", Faction.RESISTANCE, "desc",
                10, 100, null, ResearchCategory.SPECIAL, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null or blank name")
        void shouldRejectBlankName() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, null, Faction.CONFEDERATION, "desc",
                    10, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "", Faction.CONFEDERATION, "desc",
                    10, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null faction")
        void shouldRejectNullFaction() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", null, "desc",
                    10, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject negative cost")
        void shouldRejectNegativeCost() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    -1, 100, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject negative research time")
        void shouldRejectNegativeResearchTime() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, -1, null, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject invalid prerequisite ID")
        void shouldRejectInvalidPrerequisiteId() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, List.of(-2), ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, List.of(48), ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null category")
        void shouldRejectNullCategory() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, null, null, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null effect")
        void shouldRejectNullEffect() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, null, ResearchCategory.INFANTRY_ARMOR, null));
        }
    }

    // --- ResearchCategory tests ---

    @Nested
    @DisplayName("ResearchCategory")
    class ResearchCategoryTests {

        @Test
        @DisplayName("Should contain all original 8 categories")
        void shouldContainAllOriginalCategories() {
            assertNotNull(ResearchCategory.INFANTRY_ARMOR);
            assertNotNull(ResearchCategory.INFANTRY_SPEED);
            assertNotNull(ResearchCategory.INFANTRY_WEAPONRY);
            assertNotNull(ResearchCategory.VEHICLE_WEAPONRY);
            assertNotNull(ResearchCategory.VEHICLE_SPEED);
            assertNotNull(ResearchCategory.BUILDING);
            assertNotNull(ResearchCategory.ECONOMY);
            assertNotNull(ResearchCategory.SPECIAL);
        }

        @Test
        @DisplayName("Should contain tech_tree.json categories")
        void shouldContainTechTreeCategories() {
            assertNotNull(ResearchCategory.ENEMY_RANGE_REDUCTION);
            assertNotNull(ResearchCategory.ATTACK_SPEED);
            assertNotNull(ResearchCategory.ATTACK_DAMAGE);
            assertNotNull(ResearchCategory.BUILDING_ARMOR);
            assertNotNull(ResearchCategory.BUILDING_RADIUS);
            assertNotNull(ResearchCategory.BUILDING_ARMOR_OVERRIDE);
            assertNotNull(ResearchCategory.UNIT_UPGRADE);
            assertNotNull(ResearchCategory.ATTACK_RANGE);
            assertNotNull(ResearchCategory.PRODUCTION_SPEED);
            assertNotNull(ResearchCategory.SCORING);
        }

        @Test
        @DisplayName("Should parse from string case-insensitively")
        void shouldParseFromString() {
            assertEquals(ResearchCategory.INFANTRY_ARMOR, ResearchCategory.fromString("INFANTRY_ARMOR"));
            assertEquals(ResearchCategory.INFANTRY_ARMOR, ResearchCategory.fromString("infantry_armor"));
            assertEquals(ResearchCategory.ATTACK_SPEED, ResearchCategory.fromString("ATTACK_SPEED"));
            assertNull(ResearchCategory.fromString("nonexistent"));
            assertNull(ResearchCategory.fromString(null));
        }
    }
}
