package com.aow2.core.campaign;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SaveManager save/load system.
 * REF: campaign_guide.md Section 1.1 - 3 save slots
 */
@DisplayName("SaveManager")
class SaveManagerTest {

    private SaveManager saveManager;
    private GameState gameState;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        saveManager = new SaveManager();
        gameState = new GameState();
        entityManager = new EntityManager();
    }

    private Unit createTestUnit(int id, Faction faction) {
        UnitStats stats = new UnitStats(
            UnitType.CONFED_INFANTRY, "Test Infantry", 100, 10, 50,
            8, 3, 0, 7, 3, 20, 50, 15, 0, 0, 0, 1);
        return new Unit(id, faction, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, stats);
    }

    private Building createTestBuilding(int id, Faction faction) {
        BuildingStats stats = new BuildingStats(
            BuildingType.CONFED_COMMAND_CENTRE, 500, 500, 0, 5,
            0, 10, 120, 0, 0, 0, 10, 0, 0, 200, 100, List.of());
        return new Building(id, faction, new GridPosition(20, 20),
            BuildingType.CONFED_COMMAND_CENTRE, stats);
    }

    @Nested
    @DisplayName("Save operations")
    class SaveOperations {

        @Test
        @DisplayName("save to valid slot returns true")
        void saveValidSlot() {
            boolean result = saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);
            assertTrue(result);
        }

        @Test
        @DisplayName("save to all three slots succeeds")
        void saveAllSlots() {
            for (int slot = 0; slot < 3; slot++) {
                assertTrue(saveManager.save(slot, gameState, entityManager,
                    CampaignEpisode.GLOBAL_CONFEDERATION, slot));
            }
        }

        @Test
        @DisplayName("save to invalid negative slot returns false")
        void saveNegativeSlot() {
            assertFalse(saveManager.save(-1, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0));
        }

        @Test
        @DisplayName("save to slot 3 or higher returns false")
        void saveSlotTooHigh() {
            assertFalse(saveManager.save(3, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0));
        }

        @Test
        @DisplayName("save captures entities")
        void saveCapturesEntities() {
            entityManager.addUnit(createTestUnit(1, Faction.CONFEDERATION));
            entityManager.addBuilding(createTestBuilding(2, Faction.CONFEDERATION));

            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);

            SaveData loaded = saveManager.load(0);
            assertNotNull(loaded);
            assertEquals(1, loaded.units().size());
            assertEquals(1, loaded.buildings().size());
        }

        @Test
        @DisplayName("save captures episode and mission index")
        void saveCapturesProgress() {
            saveManager.save(1, gameState, entityManager,
                CampaignEpisode.LIBERATION_OF_PERU, 3);

            SaveData loaded = saveManager.load(1);
            assertNotNull(loaded);
            assertEquals(CampaignEpisode.LIBERATION_OF_PERU, loaded.episode());
            assertEquals(3, loaded.missionIndex());
        }

        @Test
        @DisplayName("save captures game tick")
        void saveCapturesTick() {
            gameState.advanceTick();
            gameState.advanceTick();
            gameState.advanceTick();

            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);

            SaveData loaded = saveManager.load(0);
            assertNotNull(loaded);
            assertEquals(3, loaded.gameTick());
        }

        @Test
        @DisplayName("save with objectives and research")
        void saveWithObjectivesAndResearch() {
            List<Objective> objectives = List.of(
                new Objective.DestroyObjective("Destroy", 5, 2)
            );
            List<String> research = List.of("infantry_hp_1", "vehicle_armor_1");
            List<Integer> credits = List.of(500, 300);

            boolean result = saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 2,
                research, credits, objectives);
            assertTrue(result);

            SaveData loaded = saveManager.load(0);
            assertNotNull(loaded);
            assertEquals(1, loaded.objectives().size());
            assertEquals(2, loaded.completedResearch().size());
            assertEquals(2, loaded.credits().size());
        }
    }

    @Nested
    @DisplayName("Load operations")
    class LoadOperations {

        @Test
        @DisplayName("load from empty slot returns null")
        void loadEmptySlot() {
            assertNull(saveManager.load(0));
        }

        @Test
        @DisplayName("load from invalid slot returns null")
        void loadInvalidSlot() {
            assertNull(saveManager.load(-1));
            assertNull(saveManager.load(5));
        }

        @Test
        @DisplayName("load returns previously saved data")
        void loadReturnsSaveData() {
            entityManager.addUnit(createTestUnit(1, Faction.CONFEDERATION));
            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 1);

            SaveData loaded = saveManager.load(0);
            assertNotNull(loaded);
            assertEquals(0, loaded.slot());
            assertEquals(CampaignEpisode.GLOBAL_CONFEDERATION, loaded.episode());
            assertEquals(1, loaded.missionIndex());
        }

        @Test
        @DisplayName("load preserves unit details")
        void loadPreservesUnits() {
            Unit unit = createTestUnit(42, Faction.CONFEDERATION);
            entityManager.addUnit(unit);

            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);

            SaveData loaded = saveManager.load(0);
            assertNotNull(loaded);
            assertEquals(1, loaded.units().size());
            SaveData.UnitSave savedUnit = loaded.units().getFirst();
            assertEquals(42, savedUnit.entityId());
            assertEquals(UnitType.CONFED_INFANTRY, savedUnit.unitType());
            assertEquals(Faction.CONFEDERATION, savedUnit.faction());
            assertEquals(100, savedUnit.hp());
        }

        @Test
        @DisplayName("load preserves building details")
        void loadPreservesBuildings() {
            Building building = createTestBuilding(10, Faction.CONFEDERATION);
            entityManager.addBuilding(building);

            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);

            SaveData loaded = saveManager.load(0);
            assertNotNull(loaded);
            assertEquals(1, loaded.buildings().size());
            SaveData.BuildingSave savedBuilding = loaded.buildings().getFirst();
            assertEquals(10, savedBuilding.entityId());
            assertEquals(BuildingType.CONFED_COMMAND_CENTRE, savedBuilding.buildingType());
        }
    }

    @Nested
    @DisplayName("hasSave operations")
    class HasSaveOperations {

        @Test
        @DisplayName("hasSave returns false for empty slot")
        void emptySlot() {
            assertFalse(saveManager.hasSave(0));
        }

        @Test
        @DisplayName("hasSave returns true after saving")
        void afterSaving() {
            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);
            assertTrue(saveManager.hasSave(0));
        }

        @Test
        @DisplayName("hasSave returns false for invalid slot")
        void invalidSlot() {
            assertFalse(saveManager.hasSave(-1));
            assertFalse(saveManager.hasSave(3));
        }
    }

    @Nested
    @DisplayName("deleteSave operations")
    class DeleteOperations {

        @Test
        @DisplayName("deleteSave removes save from slot")
        void deleteRemovesSave() {
            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);
            assertTrue(saveManager.hasSave(0));

            assertTrue(saveManager.deleteSave(0));
            assertFalse(saveManager.hasSave(0));
            assertNull(saveManager.load(0));
        }

        @Test
        @DisplayName("deleteSave on empty slot succeeds")
        void deleteEmptySlot() {
            assertTrue(saveManager.deleteSave(0));
        }

        @Test
        @DisplayName("deleteSave on invalid slot returns false")
        void deleteInvalidSlot() {
            assertFalse(saveManager.deleteSave(-1));
            assertFalse(saveManager.deleteSave(3));
        }

        @Test
        @DisplayName("deleting one slot does not affect others")
        void deleteOneDoesNotAffectOthers() {
            saveManager.save(0, gameState, entityManager,
                CampaignEpisode.GLOBAL_CONFEDERATION, 0);
            saveManager.save(1, gameState, entityManager,
                CampaignEpisode.LIBERATION_OF_PERU, 2);

            saveManager.deleteSave(0);

            assertFalse(saveManager.hasSave(0));
            assertTrue(saveManager.hasSave(1));
        }
    }

    @Nested
    @DisplayName("SaveData validation")
    class SaveDataValidation {

        @Test
        @DisplayName("SaveData rejects invalid slot")
        void rejectsInvalidSlot() {
            assertThrows(IllegalArgumentException.class, () -> new SaveData(
                5, CampaignEpisode.GLOBAL_CONFEDERATION, 0, null, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        }

        @Test
        @DisplayName("SaveData rejects null episode")
        void rejectsNullEpisode() {
            assertThrows(IllegalArgumentException.class, () -> new SaveData(
                0, null, 0, null, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        }

        @Test
        @DisplayName("SaveData rejects negative mission index")
        void rejectsNegativeMissionIndex() {
            assertThrows(IllegalArgumentException.class, () -> new SaveData(
                0, CampaignEpisode.GLOBAL_CONFEDERATION, -1, null, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        }

        @Test
        @DisplayName("SaveData makes defensive copies of lists")
        void defensiveCopies() {
            java.util.List<Objective> objectives = new java.util.ArrayList<>();
            objectives.add(new Objective.DestroyObjective("Test", 1, 0));

            SaveData data = new SaveData(0, CampaignEpisode.GLOBAL_CONFEDERATION, 0,
                null, 0, List.of(), List.of(), List.of(), List.of(), objectives, List.of());

            objectives.add(new Objective.CaptureObjective("Extra",
                new GridPosition(0, 0), false));

            assertEquals(1, data.objectives().size());
        }
    }
}
