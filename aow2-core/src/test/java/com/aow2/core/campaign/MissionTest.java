package com.aow2.core.campaign;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Mission record.
 * REF: campaign_guide.md Section 2 - Mission Data Structure
 */
@DisplayName("Mission")
class MissionTest {

    private List<Objective> sampleObjectives() {
        return List.of(
            new Objective.DestroyObjective("Destroy enemies", 10, 0),
            new Objective.CaptureObjective("Capture point", new GridPosition(64, 64), false)
        );
    }

    private List<Trigger> sampleTriggers() {
        return List.of(
            new Trigger.TimeTrigger(1, 600L, false)
        );
    }

    @Test
    @DisplayName("creates mission with valid parameters")
    void createsValidMission() {
        Mission mission = new Mission(
            1, "Test Mission", "A test", "Briefing text",
            Faction.CONFEDERATION, "maps/test.map",
            sampleObjectives(), sampleTriggers(), List.of("scripts/test.lua"), 3);

        assertEquals(1, mission.id());
        assertEquals("Test Mission", mission.name());
        assertEquals("A test", mission.description());
        assertEquals("Briefing text", mission.briefing());
        assertEquals(Faction.CONFEDERATION, mission.playerFaction());
        assertEquals("maps/test.map", mission.mapFile());
        assertEquals(2, mission.objectives().size());
        assertEquals(1, mission.triggers().size());
        assertEquals(List.of("scripts/test.lua"), mission.scriptFiles());
        assertEquals(3, mission.difficulty());
    }

    @Test
    @DisplayName("defensive copies of mutable list fields")
    void defensiveCopies() {
        List<Objective> objectives = new java.util.ArrayList<>(sampleObjectives());
        List<Trigger> triggers = new java.util.ArrayList<>(sampleTriggers());
        List<String> scripts = new java.util.ArrayList<>(List.of("scripts/test.lua"));

        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            objectives, triggers, scripts, 1);

        // Mutating original lists should not affect the mission
        objectives.add(new Objective.DestroyObjective("Extra", 1, 0));
        triggers.add(new Trigger.TimeTrigger(99, 999L, false));
        scripts.add("scripts/extra.lua");

        assertEquals(2, mission.objectives().size());
        assertEquals(1, mission.triggers().size());
        assertEquals(1, mission.scriptFiles().size());
    }

    @Test
    @DisplayName("null objectives default to empty list")
    void nullObjectivesDefault() {
        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            null, null, null, 1);

        assertTrue(mission.objectives().isEmpty());
        assertTrue(mission.triggers().isEmpty());
        assertTrue(mission.scriptFiles().isEmpty());
    }

    @Test
    @DisplayName("rejects negative mission ID")
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            -1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            List.of(), List.of(), List.of(), 1));
    }

    @Test
    @DisplayName("rejects blank name")
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            1, "", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            List.of(), List.of(), List.of(), 1));
    }

    @Test
    @DisplayName("rejects null name")
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            1, null, "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            List.of(), List.of(), List.of(), 1));
    }

    @Test
    @DisplayName("rejects null faction")
    void rejectsNullFaction() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            1, "Test", "Desc", "Brief",
            null, "maps/test.map",
            List.of(), List.of(), List.of(), 1));
    }

    @Test
    @DisplayName("rejects blank map file")
    void rejectsBlankMapFile() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "",
            List.of(), List.of(), List.of(), 1));
    }

    @Test
    @DisplayName("rejects difficulty below 1")
    void rejectsLowDifficulty() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            List.of(), List.of(), List.of(), 0));
    }

    @Test
    @DisplayName("rejects difficulty above 5")
    void rejectsHighDifficulty() {
        assertThrows(IllegalArgumentException.class, () -> new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            List.of(), List.of(), List.of(), 6));
    }

    @Test
    @DisplayName("allObjectivesCompleted returns true when all completed")
    void allObjectivesCompleted() {
        List<Objective> objectives = List.of(
            new Objective.DestroyObjective("Destroy", 5, 5),
            new Objective.CaptureObjective("Capture", new GridPosition(10, 10), true)
        );
        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            objectives, List.of(), List.of(), 1);

        assertTrue(mission.allObjectivesCompleted());
    }

    @Test
    @DisplayName("allObjectivesCompleted returns false when some incomplete")
    void someObjectivesIncomplete() {
        List<Objective> objectives = List.of(
            new Objective.DestroyObjective("Destroy", 5, 3),
            new Objective.CaptureObjective("Capture", new GridPosition(10, 10), true)
        );
        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            objectives, List.of(), List.of(), 1);

        assertFalse(mission.allObjectivesCompleted());
    }

    @Test
    @DisplayName("anyObjectiveFailed returns true when one failed")
    void anyObjectiveFailed() {
        List<Objective> objectives = List.of(
            new Objective.DestroyObjective("Destroy", 5, 3),
            new Objective.DefendObjective("Defend", 1, 6000L, 3000L, true)
        );
        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            objectives, List.of(), List.of(), 1);

        assertTrue(mission.anyObjectiveFailed());
    }

    @Test
    @DisplayName("anyObjectiveFailed returns false when none failed")
    void noObjectiveFailed() {
        List<Objective> objectives = List.of(
            new Objective.DestroyObjective("Destroy", 5, 3),
            new Objective.CaptureObjective("Capture", new GridPosition(10, 10), false)
        );
        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            objectives, List.of(), List.of(), 1);

        assertFalse(mission.anyObjectiveFailed());
    }

    @Test
    @DisplayName("allObjectivesCompleted with empty objectives returns true")
    void emptyObjectivesCompleted() {
        Mission mission = new Mission(
            1, "Test", "Desc", "Brief",
            Faction.CONFEDERATION, "maps/test.map",
            List.of(), List.of(), List.of(), 1);

        assertTrue(mission.allObjectivesCompleted());
    }

    @Test
    @DisplayName("accepts all valid difficulty levels 1-5")
    void acceptsAllDifficulties() {
        for (int diff = 1; diff <= 5; diff++) {
            Mission mission = new Mission(
                diff, "Mission " + diff, "Desc", "Brief",
                Faction.CONFEDERATION, "maps/test.map",
                List.of(), List.of(), List.of(), diff);
            assertEquals(diff, mission.difficulty());
        }
    }
}
