package com.aow2.core.research;

import com.aow2.common.model.Faction;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Singleton registry that loads and provides access to the tech tree data from tech_tree.json.
 * <p>
 * Contains all 48 research effects from the RE spec, plus the 8-tech-per-faction
 * simplified trees used by the original game's tech screen.
 * <p>
 * REF: combat_formulas.md "Research/Upgrade Effects" — 48 research IDs (0-47)
 * REF: aow2-common/src/main/resources/data/tech_tree.json
 */
public final class ResearchRegistry {

    private static final ResearchRegistry INSTANCE = new ResearchRegistry();

    /** All 48 research effects indexed by global ID (0-47). */
    private final Map<Integer, ResearchEffect> researchEffects;

    /** Confederation 8-tech simplified tree. */
    private final List<FactionTech> confederationTechs;

    /** Resistance 8-tech simplified tree. */
    private final List<FactionTech> rebelTechs;

    /**
     * Represents a single research effect from the 48-effect RE spec.
     *
     * @param id           global research ID (0-47)
     * @param name         display name
     * @param description  human-readable description of the effect
     * @param faction      owning faction (CONFEDERATION or RESISTANCE)
     * @param category     effect category (INFANTRY_ARMOR, BUILDING_RADIUS, etc.)
     * @param prerequisites list of prerequisite global IDs (anyOf semantics; empty if none)
     * @param effects      raw effect data as key-value pairs
     */
    public record ResearchEffect(
        int id,
        String name,
        String description,
        Faction faction,
        String category,
        List<Integer> prerequisites,
        Map<String, Object> effects
    ) {}

    /**
     * Represents a single tech node in the 8-tech-per-faction simplified tree.
     *
     * @param localId              local tech index within the faction (0-7)
     * @param name                display name
     * @param effect              description string
     * @param globalEffectId      maps to the 48-effect global ID
     * @param prerequisiteLocalId local prerequisite (-1 = none)
     * @param cost                credit cost (ASSUMPTION from tech_tree.json)
     * @param duration            ticks to complete (ASSUMPTION from tech_tree.json)
     */
    public record FactionTech(
        int localId,
        String name,
        String effect,
        int globalEffectId,
        int prerequisiteLocalId,
        int cost,
        int duration
    ) {}

    private ResearchRegistry() {
        researchEffects = new HashMap<>();
        confederationTechs = List.of();
        rebelTechs = List.of();
        loadFromJson();
    }

    /**
     * Returns the singleton instance of the ResearchRegistry.
     *
     * @return the registry instance
     */
    public static ResearchRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Get a research effect by its global ID.
     *
     * @param globalId the research ID (0-47)
     * @return the research effect, or null if not found
     */
    public ResearchEffect getResearchEffect(int globalId) {
        return researchEffects.get(globalId);
    }

    /**
     * Get the 8-tech simplified tree for a faction.
     *
     * @param faction the faction to query
     * @return list of faction techs (unmodifiable)
     */
    public List<FactionTech> getFactionTechs(Faction faction) {
        if (faction == Faction.CONFEDERATION) {
            return confederationTechs;
        } else if (faction == Faction.RESISTANCE) {
            return rebelTechs;
        }
        return List.of();
    }

    /**
     * Get the prerequisite global IDs for a research effect.
     * When the original data uses anyOf semantics, all alternatives are returned.
     *
     * @param globalId the research ID
     * @return list of prerequisite global IDs, or empty list if none
     */
    public List<Integer> getPrerequisites(int globalId) {
        ResearchEffect effect = researchEffects.get(globalId);
        if (effect == null) {
            return List.of();
        }
        return effect.prerequisites();
    }

    /**
     * Get an unmodifiable view of all research effects.
     *
     * @return map of global ID to research effect
     */
    public Map<Integer, ResearchEffect> getAllResearchEffects() {
        return Collections.unmodifiableMap(researchEffects);
    }

    /**
     * Load tech_tree.json from the classpath and populate the registry.
     * FIX(M-12): Replaced hand-rolled JSON parser with Jackson ObjectMapper
     * (already a project dependency). Jackson handles Unicode escapes, error
     * reporting, and edge cases correctly.
     * Falls back to empty data if the resource is not found.
     */
    @SuppressWarnings("unchecked")
    private void loadFromJson() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("data/tech_tree.json")) {
            if (is == null) {
                System.err.println("WARNING: tech_tree.json not found on classpath. Research registry will be empty.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(is, new TypeReference<>() {});

            // Parse research_effects array
            List<Object> effectsArray = (List<Object>) root.get("research_effects");
            if (effectsArray != null) {
                for (Object obj : effectsArray) {
                    Map<String, Object> map = (Map<String, Object>) obj;
                    int id = toInt(map.get("id"));
                    String name = (String) map.get("name");
                    String description = (String) map.get("description");
                    String factionStr = (String) map.get("faction");
                    Faction faction = Faction.valueOf(factionStr);
                    String category = (String) map.get("category");
                    List<Integer> prerequisites = parsePrerequisite(map.get("prerequisite"));
                    Map<String, Object> effects = (Map<String, Object>) map.get("effects");

                    researchEffects.put(id, new ResearchEffect(
                        id, name, description, faction, category, prerequisites, effects
                    ));
                }
            }

            // Parse confederation_techs array
            List<Object> confedArray = (List<Object>) root.get("confederation_techs");
            if (confedArray != null) {
                this.confederationTechs = parseFactionTechs(confedArray);
            }

            // Parse rebel_techs array
            List<Object> rebelArray = (List<Object>) root.get("rebel_techs");
            if (rebelArray != null) {
                this.rebelTechs = parseFactionTechs(rebelArray);
            }

        } catch (Exception e) {
            System.err.println("WARNING: Failed to load tech_tree.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse prerequisite which can be -1 (no prereq), a single integer,
     * or an object with "anyOf" key containing a list of alternatives.
     *
     * @return list of prerequisite IDs (anyOf semantics); empty if none
     */
    @SuppressWarnings("unchecked")
    private List<Integer> parsePrerequisite(Object prereqObj) {
        if (prereqObj == null) return List.of();
        if (prereqObj instanceof Number num) {
            int val = num.intValue();
            return val < 0 ? List.of() : List.of(val);
        }
        // If it's a map with "anyOf", store ALL alternatives to preserve anyOf semantics
        if (prereqObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) prereqObj;
            List<Object> anyOf = (List<Object>) map.get("anyOf");
            if (anyOf != null && !anyOf.isEmpty()) {
                List<Integer> result = new java.util.ArrayList<>();
                for (Object item : anyOf) {
                    int val = toInt(item);
                    if (val >= 0) result.add(val);
                }
                return result.isEmpty() ? List.of() : result;
            }
        }
        return List.of();
    }

    private List<FactionTech> parseFactionTechs(List<Object> array) {
        return array.stream()
            .map(obj -> {
                Map<String, Object> map = (Map<String, Object>) obj;
                return new FactionTech(
                    toInt(map.get("localId")),
                    (String) map.get("name"),
                    (String) map.get("effect"),
                    toInt(map.get("globalEffectId")),
                    toInt(map.get("prerequisiteLocalId")),
                    toInt(map.get("cost")),
                    toInt(map.get("duration"))
                );
            })
            .toList();
    }

    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }
}
