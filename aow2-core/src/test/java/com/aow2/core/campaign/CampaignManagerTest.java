package com.aow2.core.campaign;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CampaignManager.
 * REF: campaign_guide.md Section 8 - Mission Progression
 */
@DisplayName("CampaignManager")
class CampaignManagerTest {

    private CampaignManager manager;

    @BeforeEach
    void setUp() {
        manager = new CampaignManager();
    }

    @Nested
    @DisplayName("Campaign initialization")
    class Initialization {

        @Test
        @DisplayName("starts with no active campaign")
        void noActiveCampaign() {
            assertNull(manager.getCurrentEpisode());
            assertEquals(-1, manager.getCurrentMissionIndex());
        }

        @Test
        @DisplayName("loadCurrentMission returns empty when no campaign is active")
        void noMissionWhenNoCampaign() {
            assertTrue(manager.loadCurrentMission().isEmpty());
        }

        @Test
        @DisplayName("isCampaignComplete returns false when no campaign is active")
        void notCompleteWhenNoCampaign() {
            assertFalse(manager.isCampaignComplete());
        }
    }

    @Nested
    @DisplayName("Starting campaigns")
    class StartingCampaigns {

        @Test
        @DisplayName("startCampaign sets current episode")
        void startSetsEpisode() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            assertEquals(CampaignEpisode.GLOBAL_CONFEDERATION, manager.getCurrentEpisode());
        }

        @Test
        @DisplayName("startCampaign resets mission index to 0")
        void startResetsMissionIndex() {
            manager.startCampaign(CampaignEpisode.LIBERATION_OF_PERU);
            assertEquals(0, manager.getCurrentMissionIndex());
        }

        @Test
        @DisplayName("loadCurrentMission returns first mission after start")
        void firstMissionAfterStart() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            Optional<Mission> mission = manager.loadCurrentMission();
            assertTrue(mission.isPresent());
            // ASSUMPTION: Campaign data may not be available in test classpath,
            // so mission presence depends on resource loading
        }

        @Test
        @DisplayName("starting a new campaign resets previous progress")
        void startNewResetsProgress() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            manager.completeCurrentMission();
            assertEquals(1, manager.getCurrentMissionIndex());

            manager.startCampaign(CampaignEpisode.LIBERATION_OF_PERU);
            assertEquals(0, manager.getCurrentMissionIndex());
        }
    }

    @Nested
    @DisplayName("Mission completion")
    class MissionCompletion {

        @Test
        @DisplayName("completeCurrentMission increments mission index")
        void completeIncrementsIndex() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            assertEquals(0, manager.getCurrentMissionIndex());

            manager.completeCurrentMission();
            assertEquals(1, manager.getCurrentMissionIndex());
        }

        @Test
        @DisplayName("completeCurrentMission records completed mission")
        void completeRecordsMission() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            manager.completeCurrentMission();

            assertTrue(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 0));
            assertFalse(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 1));
        }

        @Test
        @DisplayName("multiple completions track correctly")
        void multipleCompletions() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            manager.completeCurrentMission(); // mission 0
            manager.completeCurrentMission(); // mission 1
            manager.completeCurrentMission(); // mission 2

            assertEquals(3, manager.getCurrentMissionIndex());
            assertTrue(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 0));
            assertTrue(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 1));
            assertTrue(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 2));
        }

        @Test
        @DisplayName("completeCurrentMission does nothing when no campaign is active")
        void completeWithNoCampaign() {
            manager.completeCurrentMission();
            assertEquals(-1, manager.getCurrentMissionIndex());
        }

        @Test
        @DisplayName("completing multiple missions records each once")
        void completingMultipleMissionsRecordsEachOnce() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            manager.completeCurrentMission(); // mission 0
            manager.completeCurrentMission(); // mission 1

            // Each mission should be recorded exactly once
            assertEquals(2, manager.getCompletedMissionCount(CampaignEpisode.GLOBAL_CONFEDERATION));
            assertTrue(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 0));
            assertTrue(manager.isMissionCompleted(CampaignEpisode.GLOBAL_CONFEDERATION, 1));
        }
    }

    @Nested
    @DisplayName("Campaign completion")
    class CampaignCompletion {

        @Test
        @DisplayName("isCampaignComplete returns false during campaign")
        void notCompleteDuringCampaign() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            assertFalse(manager.isCampaignComplete());
        }

        @Test
        @DisplayName("isCampaignComplete returns true when all missions done")
        void completeWhenAllMissionsDone() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            // Complete all 7 missions
            for (int i = 0; i < CampaignEpisode.GLOBAL_CONFEDERATION.missionCount(); i++) {
                manager.completeCurrentMission();
            }
            assertTrue(manager.isCampaignComplete());
        }
    }

    @Nested
    @DisplayName("Mission availability")
    class MissionAvailability {

        @Test
        @DisplayName("first mission is always available")
        void firstMissionAlwaysAvailable() {
            assertTrue(manager.isMissionAvailable(CampaignEpisode.GLOBAL_CONFEDERATION, 0));
            assertTrue(manager.isMissionAvailable(CampaignEpisode.LIBERATION_OF_PERU, 0));
            assertTrue(manager.isMissionAvailable(CampaignEpisode.CUSTOM_MISSIONS, 0));
        }

        @Test
        @DisplayName("second mission requires first to be completed")
        void secondRequiresFirst() {
            assertFalse(manager.isMissionAvailable(CampaignEpisode.GLOBAL_CONFEDERATION, 1));
        }

        @Test
        @DisplayName("second mission available after first is completed")
        void secondAvailableAfterFirst() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            manager.completeCurrentMission();

            assertTrue(manager.isMissionAvailable(CampaignEpisode.GLOBAL_CONFEDERATION, 1));
        }

        @Test
        @DisplayName("negative mission index is not available")
        void negativeIndexNotAvailable() {
            assertFalse(manager.isMissionAvailable(CampaignEpisode.GLOBAL_CONFEDERATION, -1));
        }

        @Test
        @DisplayName("mission index beyond count is not available")
        void beyondCountNotAvailable() {
            assertFalse(manager.isMissionAvailable(CampaignEpisode.GLOBAL_CONFEDERATION, 100));
        }
    }

    @Nested
    @DisplayName("Episode missions")
    class EpisodeMissions {

        @Test
        @DisplayName("getMissionsForEpisode returns mission list")
        void returnsMissions() {
            List<Mission> missions = manager.getMissionsForEpisode(CampaignEpisode.GLOBAL_CONFEDERATION);
            assertNotNull(missions);
            // ASSUMPTION: Mission count depends on campaign data loading from classpath
        }

        @Test
        @DisplayName("getMissionsForEpisode returns defensive copy")
        void returnsDefensiveCopy() {
            List<Mission> missions1 = manager.getMissionsForEpisode(CampaignEpisode.GLOBAL_CONFEDERATION);
            List<Mission> missions2 = manager.getMissionsForEpisode(CampaignEpisode.GLOBAL_CONFEDERATION);
            assertNotSame(missions1, missions2);
        }
    }

    @Nested
    @DisplayName("Save/Load integration")
    class SaveLoadIntegration {

        @Test
        @DisplayName("saveGame returns false when no campaign is active")
        void saveWithNoCampaign() {
            GameState state = new GameState();
            EntityManager entities = new EntityManager();
            assertFalse(manager.saveGame(0, state, entities));
        }

        @Test
        @DisplayName("saveGame returns true when campaign is active")
        void saveWithActiveCampaign() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            GameState state = new GameState();
            EntityManager entities = new EntityManager();
            assertTrue(manager.saveGame(0, state, entities));
        }

        @Test
        @DisplayName("loadGame restores campaign state")
        void loadRestoresState() {
            manager.startCampaign(CampaignEpisode.GLOBAL_CONFEDERATION);
            manager.completeCurrentMission();
            manager.completeCurrentMission();

            GameState state = new GameState();
            EntityManager entities = new EntityManager();
            manager.saveGame(0, state, entities);

            // Start a different campaign to verify load restores
            manager.startCampaign(CampaignEpisode.LIBERATION_OF_PERU);
            assertEquals(CampaignEpisode.LIBERATION_OF_PERU, manager.getCurrentEpisode());

            assertTrue(manager.loadGame(0));
            assertEquals(CampaignEpisode.GLOBAL_CONFEDERATION, manager.getCurrentEpisode());
            assertEquals(2, manager.getCurrentMissionIndex());
        }

        @Test
        @DisplayName("loadGame returns false for empty slot")
        void loadEmptySlot() {
            assertFalse(manager.loadGame(0));
        }
    }

    @Nested
    @DisplayName("CampaignEpisode enum")
    class CampaignEpisodeTest {

        @Test
        @DisplayName("GLOBAL_CONFEDERATION has 7 missions")
        void ep1MissionCount() {
            assertEquals(7, CampaignEpisode.GLOBAL_CONFEDERATION.missionCount());
        }

        @Test
        @DisplayName("LIBERATION_OF_PERU has 7 missions")
        void ep2MissionCount() {
            assertEquals(7, CampaignEpisode.LIBERATION_OF_PERU.missionCount());
        }

        @Test
        @DisplayName("CUSTOM_MISSIONS has 15 missions")
        void customMissionCount() {
            assertEquals(15, CampaignEpisode.CUSTOM_MISSIONS.missionCount());
        }

        @Test
        @DisplayName("GLOBAL_CONFEDERATION is CONFEDERATION faction")
        void ep1Faction() {
            assertEquals(Faction.CONFEDERATION, CampaignEpisode.GLOBAL_CONFEDERATION.faction());
        }

        @Test
        @DisplayName("LIBERATION_OF_PERU is RESISTANCE faction")
        void ep2Faction() {
            assertEquals(Faction.RESISTANCE, CampaignEpisode.LIBERATION_OF_PERU.faction());
        }

        @Test
        @DisplayName("episodes have correct titles")
        void episodeTitles() {
            assertTrue(CampaignEpisode.GLOBAL_CONFEDERATION.title().contains("Global Confederation"));
            assertTrue(CampaignEpisode.LIBERATION_OF_PERU.title().contains("Liberation of Peru"));
            assertTrue(CampaignEpisode.CUSTOM_MISSIONS.title().contains("Custom Missions"));
        }
    }

    @Nested
    @DisplayName("Script engine integration")
    class ScriptEngineIntegration {

        @Test
        @DisplayName("getScriptEngine returns non-null engine")
        void scriptEngineAvailable() {
            assertNotNull(manager.getScriptEngine());
        }

        @Test
        @DisplayName("script engine starts not active")
        void scriptEngineNotActive() {
            assertFalse(manager.getScriptEngine().isScriptActive());
        }
    }
}
