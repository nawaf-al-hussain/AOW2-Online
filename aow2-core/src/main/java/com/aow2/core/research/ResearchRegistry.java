package com.aow2.core.research;

import com.aow2.common.model.Faction;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param prerequisite prerequisite global ID (-1 = none)
     * @param effects      raw effect data as key-value pairs
     */
    public record ResearchEffect(
        int id,
        String name,
        String description,
        Faction faction,
        String category,
        int prerequisite,
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
     * Get the prerequisite global ID for a research effect.
     *
     * @param globalId the research ID
     * @return the prerequisite global ID, or -1 if none
     */
    public int getPrerequisite(int globalId) {
        ResearchEffect effect = researchEffects.get(globalId);
        if (effect == null) {
            return -1;
        }
        return effect.prerequisite();
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
     * Falls back to empty data if the resource is not found.
     */
    @SuppressWarnings("unchecked")
    private void loadFromJson() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("data/tech_tree.json")) {
            if (is == null) {
                System.err.println("WARNING: tech_tree.json not found on classpath. Research registry will be empty.");
                return;
            }

            // Simple JSON parsing without external dependencies
            String json = new String(is.readAllBytes());
            Map<String, Object> root = parseJson(json);

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
                    int prerequisite = parsePrerequisite(map.get("prerequisite"));
                    Map<String, Object> effects = (Map<String, Object>) map.get("effects");

                    researchEffects.put(id, new ResearchEffect(
                        id, name, description, faction, category, prerequisite, effects
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
     * Parse prerequisite which can be -1 (no prereq) or an object with "anyOf" key.
     */
    private int parsePrerequisite(Object prereqObj) {
        if (prereqObj == null) return -1;
        if (prereqObj instanceof Number) return ((Number) prereqObj).intValue();
        // If it's a map with "anyOf", use the first element as representative prerequisite
        if (prereqObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) prereqObj;
            List<Object> anyOf = (List<Object>) map.get("anyOf");
            if (anyOf != null && !anyOf.isEmpty()) {
                return toInt(anyOf.get(0));
            }
        }
        return -1;
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

    // =========================================================================
    // Minimal JSON parser for the known tech_tree.json structure
    // Avoids external library dependencies. Handles: objects, arrays, strings,
    // numbers, booleans, null.
    // =========================================================================

    private static Map<String, Object> parseJson(String json) {
        return (Map<String, Object>) parseValue(new JsonParser(json.trim()), 0).value();
    }

    private static record ParseResult(Object value, int endPos) {}

    private static ParseResult parseValue(JsonParser p, int pos) {
        pos = p.skipWhitespace(pos);
        char c = p.json.charAt(pos);

        if (c == '{') return parseObject(p, pos);
        if (c == '[') return parseArray(p, pos);
        if (c == '"') return parseString(p, pos);
        if (c == 't' || c == 'f') return parseBoolean(p, pos);
        if (c == 'n') return parseNull(p, pos);
        return parseNumber(p, pos);
    }

    private static ParseResult parseObject(JsonParser p, int pos) {
        Map<String, Object> map = new HashMap<>();
        pos++; // skip '{'
        pos = p.skipWhitespace(pos);

        if (p.json.charAt(pos) == '}') {
            return new ParseResult(map, pos + 1);
        }

        while (true) {
            pos = p.skipWhitespace(pos);
            ParseResult keyResult = parseString(p, pos);
            String key = (String) keyResult.value();
            pos = keyResult.endPos();
            pos = p.skipWhitespace(pos);
            pos++; // skip ':'
            ParseResult valResult = parseValue(p, pos);
            map.put(key, valResult.value());
            pos = valResult.endPos();
            pos = p.skipWhitespace(pos);

            if (p.json.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (p.json.charAt(pos) == '}') {
                return new ParseResult(map, pos + 1);
            }
        }
    }

    private static ParseResult parseArray(JsonParser p, int pos) {
        java.util.List<Object> list = new java.util.ArrayList<>();
        pos++; // skip '['
        pos = p.skipWhitespace(pos);

        if (p.json.charAt(pos) == ']') {
            return new ParseResult(list, pos + 1);
        }

        while (true) {
            ParseResult elemResult = parseValue(p, pos);
            list.add(elemResult.value());
            pos = elemResult.endPos();
            pos = p.skipWhitespace(pos);

            if (p.json.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (p.json.charAt(pos) == ']') {
                return new ParseResult(list, pos + 1);
            }
        }
    }

    private static ParseResult parseString(JsonParser p, int pos) {
        pos++; // skip opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos < p.json.length()) {
            char c = p.json.charAt(pos);
            if (c == '\\') {
                pos++;
                char esc = p.json.charAt(pos);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(esc);
                }
            } else if (c == '"') {
                return new ParseResult(sb.toString(), pos + 1);
            } else {
                sb.append(c);
            }
            pos++;
        }
        return new ParseResult(sb.toString(), pos);
    }

    private static ParseResult parseNumber(JsonParser p, int pos) {
        int start = pos;
        while (pos < p.json.length()) {
            char c = p.json.charAt(pos);
            if (Character.isDigit(c) || c == '-' || c == '.' || c == 'e' || c == 'E' || c == '+') {
                pos++;
            } else {
                break;
            }
        }
        String numStr = p.json.substring(start, pos);
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
            return new ParseResult(Double.parseDouble(numStr), pos);
        }
        return new ParseResult(Integer.parseInt(numStr), pos);
    }

    private static ParseResult parseBoolean(JsonParser p, int pos) {
        if (p.json.startsWith("true", pos)) {
            return new ParseResult(true, pos + 4);
        }
        return new ParseResult(false, pos + 5);
    }

    private static ParseResult parseNull(JsonParser p, int pos) {
        return new ParseResult(null, pos + 4);
    }

    private record JsonParser(String json) {
        int skipWhitespace(int pos) {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            return pos;
        }
    }
}
