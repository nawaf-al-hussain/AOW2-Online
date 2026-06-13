package com.aow2.core.research;

import com.aow2.common.model.Faction;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Defines the technology tree structure for both factions.
 * <p>
 * Each faction has 8 base technology nodes with prerequisites forming
 * a tree structure. Technologies provide stat upgrades, unit unlocks,
 * and building radius increases.
 * <p>
 * REF: complete_unit_stats.json technologies section
 * REF: tech_tree_confederation.md and tech_tree_rebels.md (if available)
 * REF: combat_formulas.md "Research/Upgrade Effects" — research IDs 0-47
 */
public final class TechTree {

    /**
     * Represents a single technology node in the research tree.
     *
     * @param id            unique research ID (0-47)
     * @param name          display name of the technology
     * @param description   brief description of the effect
     * @param faction       which faction this tech belongs to
     * @param cost          credit cost to research
     * @param duration      ticks required to complete the research
     * @param prerequisites list of research IDs that must be completed first
     * @param unlocks       list of research IDs that this tech unlocks
     */
    public record ResearchNode(
        int id,
        String name,
        String description,
        Faction faction,
        int cost,
        int duration,
        List<Integer> prerequisites,
        List<Integer> unlocks
    ) {}

    /** All technology nodes indexed by faction. */
    private static final Map<Faction, List<ResearchNode>> TECH_NODES = Map.of(
        Faction.CONFEDERATION, createConfederationTechs(),
        Faction.RESISTANCE, createResistanceTechs()
    );

    /**
     * Constructs a TechTree with the default faction technology definitions.
     */
    public TechTree() {
        // Default constructor uses the static TECH_NODES map
    }

    /**
     * Get all tech nodes for a faction.
     *
     * @param faction the faction to query
     * @return unmodifiable list of tech nodes for the faction
     */
    public List<ResearchNode> getTechNodes(Faction faction) {
        return TECH_NODES.getOrDefault(faction, List.of());
    }

    /**
     * Get a specific tech node by ID and faction.
     *
     * @param faction the faction
     * @param id      the research ID
     * @return the tech node, or null if not found
     */
    public ResearchNode getTechNode(Faction faction, int id) {
        List<ResearchNode> nodes = TECH_NODES.get(faction);
        if (nodes == null) {
            return null;
        }
        for (ResearchNode node : nodes) {
            if (node.id() == id) {
                return node;
            }
        }
        return null;
    }

    /**
     * Get prerequisites for a tech node.
     *
     * @param faction the faction
     * @param id      the research ID
     * @return list of prerequisite research IDs, or empty list if none
     */
    public List<Integer> getPrerequisites(Faction faction, int id) {
        ResearchNode node = getTechNode(faction, id);
        if (node == null) {
            return List.of();
        }
        return node.prerequisites();
    }

    /**
     * Get techs unlocked by completing a research.
     *
     * @param faction     the faction
     * @param completedId the completed research ID
     * @return list of research IDs that become available
     */
    public List<Integer> getUnlockedTechs(Faction faction, int completedId) {
        ResearchNode node = getTechNode(faction, completedId);
        if (node == null) {
            return List.of();
        }
        return node.unlocks();
    }

    /**
     * Create the Confederation technology tree.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects"
     * Confederation research IDs: 0-7 (base), with chains extending to 14, 21-23
     *
     * @return list of Confederation tech nodes
     */
    private static List<ResearchNode> createConfederationTechs() {
        return List.of(
            // REF: combat_formulas.md research ID 0 - Infantry armour +2; Unlocks research chain
            new ResearchNode(0, "Reinforced Infantry Armour", "Infantry armour +2, Sniper armour +2, Light armour +2",
                Faction.CONFEDERATION, 50, 300, List.of(), List.of(1)),
            // REF: combat_formulas.md research ID 1 - Attack range reduction /3
            new ResearchNode(1, "Advanced Targeting", "Enemy attack range reduction /3",
                Faction.CONFEDERATION, 75, 400, List.of(0), List.of(2)),
            // REF: combat_formulas.md research ID 2 - Attack speed -2 (faster)
            new ResearchNode(2, "Rapid Fire", "Attack speed -2 (faster) for specific unit types",
                Faction.CONFEDERATION, 75, 400, List.of(1), List.of(3)),
            // REF: combat_formulas.md research ID 3 - Attack damage +2, Production damage +2
            new ResearchNode(3, "Enhanced Munitions", "Attack damage +2, Production damage +2",
                Faction.CONFEDERATION, 100, 500, List.of(2), List.of(4)),
            // REF: combat_formulas.md research ID 4 - Building armour +4, Production armour +4
            new ResearchNode(4, "Fortified Structures", "Building armour +4, Production armour +4",
                Faction.CONFEDERATION, 80, 450, List.of(3), List.of(5)),
            // REF: combat_formulas.md research ID 5 - Building radius +1; Unlocks research chain
            new ResearchNode(5, "Power Grid Expansion", "Building radius +1",
                Faction.CONFEDERATION, 60, 350, List.of(4), List.of(6)),
            // REF: combat_formulas.md research ID 6 - Upgrades unit type 18 → type 7
            new ResearchNode(6, "Rhino Mk.II Upgrade", "Upgrades Rhino to improved variant",
                Faction.CONFEDERATION, 120, 600, List.of(5), List.of(7)),
            // REF: combat_formulas.md research ID 7 - Attack speed upgrades for vehicles
            new ResearchNode(7, "Vehicle Propulsion", "Attack speed +5 for type 11, +8 for type 13",
                Faction.CONFEDERATION, 100, 500, List.of(6), List.of())
        );
    }

    /**
     * Create the Resistance technology tree.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects"
     * Resistance research IDs: 8-14 (base), with chains extending to 38, 45-47
     *
     * @return list of Resistance tech nodes
     */
    private static List<ResearchNode> createResistanceTechs() {
        return List.of(
            // REF: combat_formulas.md research ID 8 - Attack range -1; Unlocks chain 9-13; Building radius +1
            new ResearchNode(8, "Guerrilla Tactics", "Attack range -1 for enemy types; Building radius +1",
                Faction.RESISTANCE, 50, 300, List.of(), List.of(9)),
            // REF: combat_formulas.md research ID 9 - Infantry armour +2
            new ResearchNode(9, "Composite Armour", "Infantry armour +2 for vehicle types",
                Faction.RESISTANCE, 60, 350, List.of(8), List.of(10)),
            // REF: combat_formulas.md research ID 10 - Attack range reduction /3
            new ResearchNode(10, "Signal Jamming", "Enemy attack range reduction /3",
                Faction.RESISTANCE, 75, 400, List.of(9), List.of(11)),
            // REF: combat_formulas.md research ID 11 - Attack speed -2 (faster)
            new ResearchNode(11, "Quick Reload", "Attack speed -2 (faster) for specific types",
                Faction.RESISTANCE, 75, 400, List.of(10), List.of(12)),
            // REF: combat_formulas.md research ID 12 - Upgrades unit type 17 → type 11
            new ResearchNode(12, "Hammer Mk.II Upgrade", "Upgrades Hammer to improved variant",
                Faction.RESISTANCE, 120, 600, List.of(11), List.of(13)),
            // REF: combat_formulas.md research ID 13 - Building radius +1; Unlocks chain 14
            new ResearchNode(13, "Power Network", "Building radius +1",
                Faction.RESISTANCE, 60, 350, List.of(12), List.of(14)),
            // REF: combat_formulas.md research ID 14 - Attack damage +10 for type 21
            new ResearchNode(14, "Siege Artillery", "Attack damage +10 for type 21, +2 range; Production upgrades",
                Faction.RESISTANCE, 100, 500, List.of(13), List.of())
        );
    }
}
