package com.aow2.core.campaign;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages campaign progression, mission loading, and save/load integration.
 * Provides the primary API for starting, advancing, and tracking campaign state.
 * REF: campaign_guide.md - 14 main missions (7 Ep1 + 7 Ep2) + 15 custom missions
 * REF: campaign_guide.md Section 8.1 - campaign state machine with mission completion
 * REF: campaign_guide.md Section 8.2 - mission selection screen (screen ID 16)
 */
public final class CampaignManager {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignManager.class);

    /** File paths for campaign data. */
    private static final String EP1_RESOURCE = "data/campaigns/episode1_global_confederation.json";
    private static final String EP2_RESOURCE = "data/campaigns/episode2_liberation_of_peru.json";
    private static final String CUSTOM_RESOURCE = "data/campaigns/custom_missions.json";

    /** The currently active campaign, or null if none is active. */
    private Campaign currentCampaign;

    /** Index of the current mission within the campaign. */
    private int currentMissionIndex;

    /** The save manager for campaign persistence. */
    private final SaveManager saveManager;

    /** The mission script engine for Lua scripting. */
    private final ScriptEngine scriptEngine;

    /** Jackson object mapper for loading campaign data. */
    private final ObjectMapper objectMapper;

    /** Loaded missions for each episode. */
    private final List<Mission> episode1Missions;
    private final List<Mission> episode2Missions;
    private final List<Mission> customMissions;

    /** Completed mission indices per episode (persisted via save). */
    private final List<Integer> completedEpisode1Missions;
    private final List<Integer> completedEpisode2Missions;
    private final List<Integer> completedCustomMissions;

    /**
     * Constructs a new CampaignManager with the specified save directory.
     *
     * @param saveDirectory directory for save file storage
     */
    public CampaignManager(java.nio.file.Path saveDirectory) {
        this.saveManager = new SaveManager(saveDirectory);
        this.scriptEngine = new ScriptEngine() {
            private boolean active = false;
            public boolean loadScript(String f, GameState s, EntityManager e) { active = true; return true; }
            public boolean loadScriptFromString(String c, String n, GameState s, EntityManager e) { active = true; return true; }
            public void processTick(GameState s, EntityManager e) {}
            public void registerTriggerCallback(int id, Runnable cb) {}
            public void fireTrigger(int id) {}
            public void setScriptVariable(String n, int v) {}
            public void setScriptVariable(String n, String v) {}
            public int getScriptVariableInt(String n) { return 0; }
            public String getScriptVariableString(String n) { return ""; }
            public boolean isScriptActive() { return active; }
            public void reset() { active = false; }
        };
        this.objectMapper = new ObjectMapper();
        this.currentCampaign = null;
        this.currentMissionIndex = 0;
        this.episode1Missions = new ArrayList<>();
        this.episode2Missions = new ArrayList<>();
        this.customMissions = new ArrayList<>();
        this.completedEpisode1Missions = new ArrayList<>();
        this.completedEpisode2Missions = new ArrayList<>();
        this.completedCustomMissions = new ArrayList<>();

        loadAllCampaignData();
    }

    /**
     * Constructs a new CampaignManager with in-memory save storage.
     */
    public CampaignManager() {
        this.saveManager = new SaveManager();
        this.scriptEngine = new ScriptEngine() {
            private boolean active = false;
            public boolean loadScript(String f, GameState s, EntityManager e) { active = true; return true; }
            public boolean loadScriptFromString(String c, String n, GameState s, EntityManager e) { active = true; return true; }
            public void processTick(GameState s, EntityManager e) {}
            public void registerTriggerCallback(int id, Runnable cb) {}
            public void fireTrigger(int id) {}
            public void setScriptVariable(String n, int v) {}
            public void setScriptVariable(String n, String v) {}
            public int getScriptVariableInt(String n) { return 0; }
            public String getScriptVariableString(String n) { return ""; }
            public boolean isScriptActive() { return active; }
            public void reset() { active = false; }
        };
        this.objectMapper = new ObjectMapper();
        this.currentCampaign = null;
        this.currentMissionIndex = 0;
        this.episode1Missions = new ArrayList<>();
        this.episode2Missions = new ArrayList<>();
        this.customMissions = new ArrayList<>();
        this.completedEpisode1Missions = new ArrayList<>();
        this.completedEpisode2Missions = new ArrayList<>();
        this.completedCustomMissions = new ArrayList<>();

        loadAllCampaignData();
    }

    /**
     * Starts a campaign episode.
     * Sets the current campaign and resets mission progression to the first mission.
     *
     * @param episode the campaign episode to start
     */
    public void startCampaign(CampaignEpisode episode) {
        List<Mission> missions = getMissionsForEpisode(episode);
        this.currentCampaign = new Campaign(episode, missions);
        this.currentMissionIndex = 0;
        this.scriptEngine.reset();
        LOG.info("Started campaign: {} with {} missions", episode.title(), missions.size());
    }

    /**
     * Loads the current mission from the active campaign.
     *
     * @return the current mission, or empty if no campaign is active
     */
    public Optional<Mission> loadCurrentMission() {
        if (currentCampaign == null) {
            LOG.warn("No active campaign");
            return Optional.empty();
        }
        List<Mission> missions = currentCampaign.missions();
        if (currentMissionIndex < 0 || currentMissionIndex >= missions.size()) {
            LOG.warn("Mission index out of range: {} (max {})", currentMissionIndex, missions.size() - 1);
            return Optional.empty();
        }
        return Optional.of(missions.get(currentMissionIndex));
    }

    /**
     * Completes the current mission and advances to the next.
     * Records the completed mission and increments the mission index.
     */
    public void completeCurrentMission() {
        if (currentCampaign == null) {
            LOG.warn("No active campaign to complete mission in");
            return;
        }

        CampaignEpisode episode = currentCampaign.episode();
        List<Integer> completed = getCompletedListForEpisode(episode);
        if (!completed.contains(currentMissionIndex)) {
            completed.add(currentMissionIndex);
        }

        LOG.info("Completed mission {} in {} (total completed: {})",
            currentMissionIndex, episode.title(), completed.size());

        currentMissionIndex++;

        if (isCampaignComplete()) {
            LOG.info("Campaign complete: {}", episode.title());
        }
    }

    /**
     * Checks if the current campaign has been fully completed.
     *
     * @return true if all missions in the current campaign are completed
     */
    public boolean isCampaignComplete() {
        if (currentCampaign == null) {
            return false;
        }
        return currentMissionIndex >= currentCampaign.missions().size();
    }

    /**
     * Returns the current mission index within the active campaign.
     *
     * @return current mission index, or -1 if no campaign is active
     */
    public int getCurrentMissionIndex() {
        return currentCampaign != null ? currentMissionIndex : -1;
    }

    /**
     * Returns the current campaign episode, or null if none is active.
     *
     * @return current episode
     */
    public CampaignEpisode getCurrentEpisode() {
        return currentCampaign != null ? currentCampaign.episode() : null;
    }

    /**
     * Returns the number of completed missions for the specified episode.
     *
     * @param episode the episode to query
     * @return count of completed missions
     */
    public int getCompletedMissionCount(CampaignEpisode episode) {
        return getCompletedListForEpisode(episode).size();
    }

    /**
     * Checks if a specific mission in an episode has been completed.
     *
     * @param episode      the episode
     * @param missionIndex the mission index within the episode
     * @return true if the mission has been completed
     */
    public boolean isMissionCompleted(CampaignEpisode episode, int missionIndex) {
        return getCompletedListForEpisode(episode).contains(missionIndex);
    }

    /**
     * Checks if a mission is available to play (previous mission completed or first mission).
     *
     * @param episode      the episode
     * @param missionIndex the mission index
     * @return true if the mission is available
     */
    public boolean isMissionAvailable(CampaignEpisode episode, int missionIndex) {
        if (missionIndex < 0) {
            return false;
        }
        List<Mission> missions = getMissionsForEpisode(episode);
        if (missionIndex >= missions.size()) {
            return false;
        }
        // First mission is always available
        if (missionIndex == 0) {
            return true;
        }
        // Subsequent missions require the previous one to be completed
        return isMissionCompleted(episode, missionIndex - 1);
    }

    /**
     * Returns all missions for the specified episode.
     *
     * @param episode the episode
     * @return list of missions
     */
    public List<Mission> getMissionsForEpisode(CampaignEpisode episode) {
        return switch (episode) {
            case GLOBAL_CONFEDERATION -> List.copyOf(episode1Missions);
            case LIBERATION_OF_PERU -> List.copyOf(episode2Missions);
            case CUSTOM_MISSIONS -> List.copyOf(customMissions);
        };
    }

    /**
     * Saves the current campaign state to the specified slot.
     *
     * @param slot     save slot (0-2)
     * @param state    current game state
     * @param entities entity manager
     * @return true if save was successful
     */
    public boolean saveGame(int slot, GameState state, EntityManager entities) {
        if (currentCampaign == null) {
            LOG.warn("No active campaign to save");
            return false;
        }
        return saveManager.save(slot, state, entities,
            currentCampaign.episode(), currentMissionIndex);
    }

    /**
     * Loads a campaign from the specified save slot.
     *
     * @param slot save slot (0-2)
     * @return true if the load was successful
     */
    public boolean loadGame(int slot) {
        SaveData saveData = saveManager.load(slot);
        if (saveData == null) {
            LOG.warn("No save found in slot {}", slot);
            return false;
        }

        CampaignEpisode episode = saveData.episode();
        startCampaign(episode);
        currentMissionIndex = saveData.missionIndex();
        LOG.info("Loaded save from slot {} (episode={}, mission={})",
            slot, episode.title(), currentMissionIndex);
        return true;
    }

    /**
     * Returns the save manager for direct save slot queries.
     *
     * @return the save manager
     */
    public SaveManager getSaveManager() {
        return saveManager;
    }

    /**
     * Returns the mission script engine.
     *
     * @return the script engine
     */
    public ScriptEngine getScriptEngine() {
        return scriptEngine;
    }

    // --- Private helpers ---

    private void loadAllCampaignData() {
        loadCampaignData(EP1_RESOURCE, episode1Missions);
        loadCampaignData(EP2_RESOURCE, episode2Missions);
        loadCampaignData(CUSTOM_RESOURCE, customMissions);
        LOG.info("Campaign data loaded: Ep1={} missions, Ep2={} missions, Custom={} missions",
            episode1Missions.size(), episode2Missions.size(), customMissions.size());
    }

    private void loadCampaignData(String resourcePath, List<Mission> targetList) {
        try (InputStream is = CampaignManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Campaign data resource not found: {}", resourcePath);
                return;
            }
            JsonNode root = objectMapper.readTree(is);
            JsonNode missionsNode = root.get("missions");
            if (missionsNode == null || !missionsNode.isArray()) {
                LOG.warn("No missions array found in {}", resourcePath);
                return;
            }
            for (JsonNode missionNode : missionsNode) {
                Mission mission = parseMission(missionNode);
                targetList.add(mission);
            }
        } catch (IOException e) {
            LOG.error("Failed to load campaign data from {}", resourcePath, e);
        }
    }

    private Mission parseMission(JsonNode node) {
        List<Objective> objectives = new ArrayList<>();
        JsonNode objNode = node.get("objectives");
        if (objNode != null && objNode.isArray()) {
            for (JsonNode o : objNode) {
                objectives.add(parseObjective(o));
            }
        }

        List<Trigger> triggers = new ArrayList<>();
        JsonNode trigNode = node.get("triggers");
        if (trigNode != null && trigNode.isArray()) {
            for (JsonNode t : trigNode) {
                triggers.add(parseTrigger(t));
            }
        }

        List<String> scriptFiles = new ArrayList<>();
        JsonNode scriptsNode = node.get("scriptFiles");
        if (scriptsNode != null && scriptsNode.isArray()) {
            for (JsonNode s : scriptsNode) {
                scriptFiles.add(s.asText());
            }
        }

        return new Mission(
            node.path("id").asInt(0),
            node.path("name").asText("Unnamed Mission"),
            node.path("description").asText(""),
            node.path("briefing").asText(""),
            Faction.valueOf(node.path("playerFaction").asText("CONFEDERATION")),
            node.path("mapFile").asText(""),
            objectives,
            triggers,
            scriptFiles,
            node.path("difficulty").asInt(1)
        );
    }

    private Objective parseObjective(JsonNode node) {
        String type = node.path("type").asText("destroy");
        String name = node.path("name").asText("Objective");

        return switch (type) {
            case "destroy" -> new Objective.DestroyObjective(
                name,
                node.path("targetCount").asInt(1),
                0
            );
            case "defend" -> new Objective.DefendObjective(
                name,
                node.path("entityId").asInt(0),
                node.path("durationTicks").asLong(6000L),
                0L,
                false
            );
            case "escort" -> new Objective.EscortObjective(
                name,
                node.path("unitId").asInt(0),
                new GridPosition(
                    node.path("destX").asInt(0),
                    node.path("destY").asInt(0)
                ),
                true,
                false
            );
            case "timed" -> new Objective.TimedObjective(
                name,
                node.path("durationTicks").asLong(6000L),
                0L,
                false
            );
            case "capture" -> new Objective.CaptureObjective(
                name,
                new GridPosition(
                    node.path("targetX").asInt(0),
                    node.path("targetY").asInt(0)
                ),
                false
            );
            default -> new Objective.DestroyObjective(name, 1, 0);
        };
    }

    private Trigger parseTrigger(JsonNode node) {
        String type = node.path("type").asText("time");
        int triggerId = node.path("triggerId").asInt(0);

        return switch (type) {
            case "area" -> new Trigger.AreaTrigger(
                triggerId,
                new GridPosition(
                    node.path("centerX").asInt(0),
                    node.path("centerY").asInt(0)
                ),
                node.path("radius").asInt(5),
                node.path("factionId").asInt(0),
                false
            );
            case "unitCount" -> new Trigger.UnitCountTrigger(
                triggerId,
                Faction.valueOf(node.path("faction").asText("CONFEDERATION")),
                UnitType.valueOf(node.path("unitType").asText("CONFED_INFANTRY")),
                node.path("threshold").asInt(5),
                false
            );
            case "time" -> new Trigger.TimeTrigger(
                triggerId,
                node.path("triggerTick").asLong(600L),
                false
            );
            case "buildingDestroyed" -> new Trigger.BuildingDestroyedTrigger(
                triggerId,
                BuildingType.valueOf(node.path("buildingType").asText("CONFED_COMMAND_CENTRE")),
                Faction.valueOf(node.path("faction").asText("CONFEDERATION")),
                false
            );
            default -> new Trigger.TimeTrigger(triggerId, 600L, false);
        };
    }

    private List<Integer> getCompletedListForEpisode(CampaignEpisode episode) {
        return switch (episode) {
            case GLOBAL_CONFEDERATION -> completedEpisode1Missions;
            case LIBERATION_OF_PERU -> completedEpisode2Missions;
            case CUSTOM_MISSIONS -> completedCustomMissions;
        };
    }

    /**
     * Internal campaign state holder.
     */
    private record Campaign(CampaignEpisode episode, List<Mission> missions) {}
}
