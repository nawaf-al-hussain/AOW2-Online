package com.aow2.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResearchNodeTest {

    // --- ResearchEffect tests ---

    @Nested
    @DisplayName("ResearchEffect")
    class ResearchEffectTests {

        @Test
        @DisplayName("Should create valid research effect")
        void shouldCreateValidEffect() {
            ResearchEffect effect = new ResearchEffect("armor", 2, new int[]{0, 1, 2});
            assertEquals("armor", effect.statName());
            assertEquals(2, effect.value());
            assertArrayEquals(new int[]{0, 1, 2}, effect.affectedUnitTypes());
        }

        @Test
        @DisplayName("Should reject null or blank stat name")
        void shouldRejectBlankStatName() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchEffect(null, 2, new int[]{0}));
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchEffect("", 2, new int[]{0}));
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchEffect("  ", 2, new int[]{0}));
        }

        @Test
        @DisplayName("Should default to empty array when affectedUnitTypes is null")
        void shouldDefaultToEmptyWhenNull() {
            ResearchEffect effect = new ResearchEffect("damage", 5, null);
            assertNotNull(effect.affectedUnitTypes());
            assertEquals(0, effect.affectedUnitTypes().length);
        }

        @Test
        @DisplayName("Should detect affected unit type")
        void shouldDetectAffectedUnitType() {
            ResearchEffect effect = new ResearchEffect("armor", 2, new int[]{0, 1, 2});
            assertTrue(effect.affectsUnitType(0));
            assertTrue(effect.affectsUnitType(1));
            assertTrue(effect.affectsUnitType(2));
            assertFalse(effect.affectsUnitType(3));
        }

        @Test
        @DisplayName("Should implement equals and hashCode correctly")
        void shouldImplementEqualsAndHashCode() {
            ResearchEffect a = new ResearchEffect("armor", 2, new int[]{0, 1});
            ResearchEffect b = new ResearchEffect("armor", 2, new int[]{0, 1});
            ResearchEffect c = new ResearchEffect("damage", 2, new int[]{0, 1});

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertNotEquals(a, c);
        }

        @Test
        @DisplayName("Should produce meaningful toString")
        void shouldProduceToString() {
            ResearchEffect effect = new ResearchEffect("speed", 1, new int[]{3, 4});
            String str = effect.toString();
            assertTrue(str.contains("speed"));
            assertTrue(str.contains("1"));
        }
    }

    // --- ResearchNode tests ---

    @Nested
    @DisplayName("ResearchNode")
    class ResearchNodeTests {

        private ResearchEffect createInfantryArmorEffect() {
            return new ResearchEffect("armor", 2, new int[]{0, 1, 2});
        }

        @Test
        @DisplayName("Should create valid research node")
        void shouldCreateValidNode() {
            ResearchEffect effect = createInfantryArmorEffect();
            ResearchNode node = new ResearchNode(0, "Reinforced Plating", Faction.CONFEDERATION,
                "Increases infantry armor by 2", 30, 300, -1,
                ResearchCategory.INFANTRY_ARMOR, effect);

            assertEquals(0, node.id());
            assertEquals("Reinforced Plating", node.name());
            assertEquals(Faction.CONFEDERATION, node.faction());
            assertEquals("Increases infantry armor by 2", node.description());
            assertEquals(30, node.cost());
            assertEquals(300, node.researchTime());
            assertEquals(-1, node.prerequisiteId());
            assertEquals(ResearchCategory.INFANTRY_ARMOR, node.category());
            assertEquals(effect, node.effect());
        }

        @Test
        @DisplayName("Should reject ID below 0")
        void shouldRejectIdBelowZero() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(-1, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject ID above 47")
        void shouldRejectIdAbove47() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(48, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should accept boundary IDs 0 and 47")
        void shouldAcceptBoundaryIds() {
            assertDoesNotThrow(() -> new ResearchNode(0, "First", Faction.CONFEDERATION, "desc",
                10, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
            assertDoesNotThrow(() -> new ResearchNode(47, "Last", Faction.RESISTANCE, "desc",
                10, 100, -1, ResearchCategory.SPECIAL, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null or blank name")
        void shouldRejectBlankName() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, null, Faction.CONFEDERATION, "desc",
                    10, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "", Faction.CONFEDERATION, "desc",
                    10, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null faction")
        void shouldRejectNullFaction() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", null, "desc",
                    10, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject negative cost")
        void shouldRejectNegativeCost() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    -1, 100, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject negative research time")
        void shouldRejectNegativeResearchTime() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, -1, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject invalid prerequisite ID")
        void shouldRejectInvalidPrerequisiteId() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, -2, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, 48, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null category")
        void shouldRejectNullCategory() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, -1, null, createInfantryArmorEffect()));
        }

        @Test
        @DisplayName("Should reject null effect")
        void shouldRejectNullEffect() {
            assertThrows(IllegalArgumentException.class,
                () -> new ResearchNode(0, "Test", Faction.CONFEDERATION, "desc",
                    10, 100, -1, ResearchCategory.INFANTRY_ARMOR, null));
        }

        @Test
        @DisplayName("Should report hasPrerequisite when prerequisiteId >= 0")
        void shouldReportHasPrerequisiteWhenSet() {
            ResearchNode withPrereq = new ResearchNode(8, "Advanced", Faction.CONFEDERATION, "desc",
                50, 500, 0, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect());
            assertTrue(withPrereq.hasPrerequisite());

            ResearchNode noPrereq = new ResearchNode(0, "Basic", Faction.CONFEDERATION, "desc",
                30, 300, -1, ResearchCategory.INFANTRY_ARMOR, createInfantryArmorEffect());
            assertFalse(noPrereq.hasPrerequisite());
        }
    }

    // --- ResearchCategory tests ---

    @Nested
    @DisplayName("ResearchCategory")
    class ResearchCategoryTests {

        @Test
        @DisplayName("Should have exactly 8 categories")
        void shouldHave8Categories() {
            assertEquals(8, ResearchCategory.values().length);
        }

        @Test
        @DisplayName("Should contain all expected categories")
        void shouldContainAllCategories() {
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
        @DisplayName("Should be convertible from string name")
        void shouldConvertFromString() {
            ResearchCategory category = ResearchCategory.valueOf("INFANTRY_ARMOR");
            assertEquals(ResearchCategory.INFANTRY_ARMOR, category);
        }
    }
}
