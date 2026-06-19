package com.aow2.core.research;

import com.aow2.common.model.Faction;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Defines the technology tree structure for both factions.
 * <p>
 * Confederation has 25 research nodes (IDs 0-23 and 43).
 * Resistance has 23 research nodes (IDs 24-47, excluding 43 which is Confederation-only).
 * Total: 48 unique research IDs (0-47).
 * <p>
 * REF: combat_formulas.md "Research/Upgrade Effects" — 48 research IDs (0-47)
 * REF: tech_tree_confederation.md — Confederation tree structure and prerequisites
 * REF: tech_tree_rebels.md — Resistance tree structure and prerequisites
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
    public record TechTreeNode(
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
    private static final Map<Faction, List<TechTreeNode>> TECH_NODES = Map.of(
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
    public List<TechTreeNode> getTechNodes(Faction faction) {
        return TECH_NODES.getOrDefault(faction, List.of());
    }

    /**
     * Get a specific tech node by ID and faction.
     *
     * @param faction the faction
     * @param id      the research ID
     * @return the tech node, or null if not found
     */
    public TechTreeNode getTechNode(Faction faction, int id) {
        List<TechTreeNode> nodes = TECH_NODES.get(faction);
        if (nodes == null) {
            return null;
        }
        for (TechTreeNode node : nodes) {
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
        TechTreeNode node = getTechNode(faction, id);
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
        TechTreeNode node = getTechNode(faction, completedId);
        if (node == null) {
            return List.of();
        }
        return node.unlocks();
    }

    /**
     * Create the Confederation technology tree.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects" — research IDs 0-23, 43
     * REF: tech_tree_confederation.md — full tree structure with prerequisites
     * <p>
     * Confederation has 25 research nodes forming multiple chains:
     * <ul>
     *   <li>Infantry chain: R0 → R1, R2, R3 → R6, R7 → R8 → R9, R10 → R11, R12 → R13 → R14</li>
     *   <li>Building chain: R4 → R16 → R18 (with R5 also → R18)</li>
     *   <li>Economy chain: R5 → R17, R18; R15 → R17, R19, R20; R21 → R22, R43</li>
     *   <li>Scoring chain: R17 + R21 → R22 → R23</li>
     * </ul>
     *
     * @return list of Confederation tech nodes
     */
    private static List<TechTreeNode> createConfederationTechs() {
        return List.of(
            // === Infantry Chain (Tier 1-2) ===
            // REF: combat_formulas.md research ID 0 — Infantry armour +2; Sniper armour +2; Light armour +2
            new TechTreeNode(0, "Energy Suit",
                "Infantry armour +2, Sniper armour +2, Light armour +2",
                Faction.CONFEDERATION, 50, 300, List.of(), List.of(1, 2, 3)),

            // REF: combat_formulas.md research ID 1 — Player 0 attack range reduction /3
            new TechTreeNode(1, "Advanced Targeting",
                "Enemy attack range reduction /3",
                Faction.CONFEDERATION, 60, 350, List.of(0), List.of()),

            // REF: combat_formulas.md research ID 2 — Attack speed -2 (faster) for specific unit types
            new TechTreeNode(2, "Rapid Fire",
                "Attack speed -2 (faster) for specific unit types",
                Faction.CONFEDERATION, 60, 350, List.of(0), List.of(6)),

            // REF: combat_formulas.md research ID 3 — Attack damage +2, Production damage +2
            new TechTreeNode(3, "Enhanced Munitions",
                "Attack damage +2, Production damage +2",
                Faction.CONFEDERATION, 70, 400, List.of(0), List.of(7)),

            // === Building Defence Chain ===
            // REF: combat_formulas.md research ID 4 — Building armour +4, Production armour +4
            new TechTreeNode(4, "Fortified Structures",
                "Building armour +4, Production armour +4",
                Faction.CONFEDERATION, 70, 380, List.of(), List.of(16)),

            // === Building Radius / Economy Chain ===
            // REF: combat_formulas.md research ID 5 — Building radius +1
            new TechTreeNode(5, "Power Grid Expansion",
                "Building radius +1",
                Faction.CONFEDERATION, 55, 320, List.of(), List.of(17, 18)),

            // === Infantry Chain (Tier 3) ===
            // REF: combat_formulas.md research ID 6 — Upgrades unit type 18 → type 7 (Rhino → Heavy Assault)
            new TechTreeNode(6, "Rhino Mk.II Upgrade",
                "Upgrades Rhino to Heavy Assault variant",
                Faction.CONFEDERATION, 100, 500, List.of(2), List.of(8)),

            // REF: combat_formulas.md research ID 7 — Attack speed +5 for type 11, +8 for type 13; Production bonuses
            new TechTreeNode(7, "Vehicle Propulsion",
                "Attack speed +5 for type 11, +8 for type 13; Production bonuses",
                Faction.CONFEDERATION, 90, 480, List.of(3), List.of(8)),

            // === Heavy Machinery Chain (Tier 4) ===
            // REF: combat_formulas.md research ID 8 — Attack range -1 for types 7,18,9,11,17,13,16; Building radius +1
            new TechTreeNode(8, "Heavy Artillery Upgrade",
                "Attack range -1 for heavy units; Building radius +1",
                Faction.CONFEDERATION, 100, 520, List.of(6, 7), List.of(9, 10)),

            // === Heavy Machinery Chain (Tier 5) ===
            // REF: combat_formulas.md research ID 9 — Infantry armour +2 for types 7,18,9,11,17,13,16
            new TechTreeNode(9, "Composite Armour II",
                "Infantry armour +2 for heavy unit types",
                Faction.CONFEDERATION, 90, 480, List.of(8), List.of(11, 12)),

            // REF: combat_formulas.md research ID 10 — Player 1 attack range reduction /3
            new TechTreeNode(10, "Signal Jamming",
                "Player 1 attack range reduction /3",
                Faction.CONFEDERATION, 75, 400, List.of(8), List.of()),

            // === Heavy Machinery Chain (Tier 6) ===
            // REF: combat_formulas.md research ID 11 — Attack speed -2 (faster) for types 11, 13
            new TechTreeNode(11, "Quick Reload",
                "Attack speed -2 (faster) for types 11, 13",
                Faction.CONFEDERATION, 90, 480, List.of(9), List.of(13)),

            // REF: combat_formulas.md research ID 12 — Upgrades unit type 17 → type 11 (Hammer → Mine Scorpio)
            new TechTreeNode(12, "Hammer Mk.II Upgrade",
                "Upgrades Hammer to Mine Scorpio variant",
                Faction.CONFEDERATION, 110, 550, List.of(9), List.of(13)),

            // === Heavy Machinery Chain (Tier 7) ===
            // REF: combat_formulas.md research ID 13 — Building radius +1
            new TechTreeNode(13, "Power Network",
                "Building radius +1",
                Faction.CONFEDERATION, 80, 450, List.of(11, 12), List.of(14)),

            // === Heavy Machinery Chain (Tier 8 - finale) ===
            // REF: combat_formulas.md research ID 14 — Attack damage +10 for type 21; Range +2; Production bonuses
            new TechTreeNode(14, "Siege Artillery",
                "Attack damage +10 for type 21, Range +2; Production +2 for type 16, +5 for type 13",
                Faction.CONFEDERATION, 120, 600, List.of(13), List.of()),

            // === Production / Economy Chain ===
            // REF: combat_formulas.md research ID 15 — Player 0 supply cap = 8
            new TechTreeNode(15, "Supply Logistics",
                "Supply cap = 8",
                Faction.CONFEDERATION, 60, 350, List.of(), List.of(17, 19, 20)),

            // REF: combat_formulas.md research ID 16 — Player 0 building armour = 9
            new TechTreeNode(16, "Building Armour Override",
                "Building armour override = 9",
                Faction.CONFEDERATION, 80, 420, List.of(4), List.of(18)),

            // REF: combat_formulas.md research ID 17 — Player 0 unit limit +2; Production +1 for type 15; Speed = 20
            new TechTreeNode(17, "Enhanced Economy",
                "Unit limit +2; Production +1 for type 15; Production speed = 20",
                Faction.CONFEDERATION, 85, 440, List.of(5, 15), List.of(22)),

            // REF: combat_formulas.md research ID 18 — Building radius +1
            new TechTreeNode(18, "Advanced Building Radius",
                "Building radius +1",
                Faction.CONFEDERATION, 75, 400, List.of(5, 16), List.of()),

            // REF: combat_formulas.md research ID 19 — Player 1 production P[1] = 7
            new TechTreeNode(19, "Fast Infantry Training",
                "Production P[1] = 7 — Reduces infantry training time",
                Faction.CONFEDERATION, 65, 360, List.of(15), List.of()),

            // REF: combat_formulas.md research ID 20 — Player 1 production P[2] = 7
            new TechTreeNode(20, "Upgraded Assembly Line",
                "Production P[2] = 7 — Reduces machinery build time",
                Faction.CONFEDERATION, 65, 360, List.of(15), List.of()),

            // REF: combat_formulas.md research ID 21 — Player 0 credit limit Q[0] = 120
            new TechTreeNode(21, "Finance Department",
                "Credit limit = 120",
                Faction.CONFEDERATION, 70, 380, List.of(), List.of(22, 43)),

            // REF: combat_formulas.md research ID 22 — Player 0 score bonus S[0] = 30
            new TechTreeNode(22, "Incentive System",
                "Score bonus = 30",
                Faction.CONFEDERATION, 90, 460, List.of(17, 21), List.of(23)),

            // REF: combat_formulas.md research ID 23 — Player 0 display bonus = 25
            new TechTreeNode(23, "Communications System",
                "Display bonus = 25",
                Faction.CONFEDERATION, 100, 500, List.of(22), List.of()),

            // REF: combat_formulas.md research ID 43 — Player 0 production P[4] = 7
            new TechTreeNode(43, "Advanced Credits",
                "Production P[4] = 7",
                Faction.CONFEDERATION, 75, 400, List.of(21), List.of())
        );
    }

    /**
     * Create the Resistance technology tree.
     * <p>
     * REF: combat_formulas.md "Research/Upgrade Effects" — research IDs 24-47 (excluding 43)
     * REF: tech_tree_rebels.md — full tree structure with prerequisites
     * <p>
     * Resistance has 23 research nodes forming multiple chains:
     * <ul>
     *   <li>Infantry chain: R24 → R26, R27 → R28 → R29</li>
     *   <li>Machinery chain: R29 → R30, R31 → R32 → R33, R34 → R35, R36 → R37 → R38</li>
     *   <li>Economy chain: R39 → R41, R44, R45; R40 → R41; R41 → R42</li>
     *   <li>Scoring chain: R45 → R46 → R47</li>
     *   <li>Standalone: R25 (range reduction /3)</li>
     * </ul>
     *
     * @return list of Resistance tech nodes
     */
    private static List<TechTreeNode> createResistanceTechs() {
        return List.of(
            // === Infantry Chain (Tier 1) ===
            // REF: combat_formulas.md research ID 24 — Infantry armour +1 for types 0,2,4,14
            new TechTreeNode(24, "Titanium Jacket",
                "Infantry armour +1 for types 0, 2, 4, 14",
                Faction.RESISTANCE, 50, 300, List.of(), List.of(26, 27)),

            // REF: combat_formulas.md research ID 25 — Player 1 attack range reduction /3
            new TechTreeNode(25, "Signal Jamming",
                "Enemy attack range reduction /3",
                Faction.RESISTANCE, 55, 320, List.of(), List.of()),

            // === Infantry Chain (Tier 2) ===
            // REF: combat_formulas.md research ID 26 — Attack speed +1 for types 0,2,3; Production +1 for types 0,4
            new TechTreeNode(26, "Infantry Combat Drill",
                "Attack speed +1 for types 0, 2, 3; Production +1 for types 0, 4",
                Faction.RESISTANCE, 60, 350, List.of(24), List.of(28)),

            // REF: combat_formulas.md research ID 27 — Attack range -1 for types 0,2,4,14
            new TechTreeNode(27, "Infantry Range Upgrade",
                "Attack range -1 for types 0, 2, 4, 14",
                Faction.RESISTANCE, 65, 370, List.of(24), List.of(29)),

            // === Light Vehicle Chain (Tier 3) ===
            // REF: combat_formulas.md research ID 28 — Attack range +1 for type 15; Production +1 for types 2
            new TechTreeNode(28, "Coyote Range Upgrade",
                "Attack range +1 for type 15 (Coyote); Production +1 for types 2",
                Faction.RESISTANCE, 70, 380, List.of(26), List.of(29)),

            // === Machinery Merge Point (Tier 4) ===
            // REF: combat_formulas.md research ID 29 — Building radius +1
            new TechTreeNode(29, "Building Radius Expansion",
                "Building radius +1",
                Faction.RESISTANCE, 70, 380, List.of(27, 28), List.of(30, 31)),

            // === Machinery Chain A (Tier 5) ===
            // REF: combat_formulas.md research ID 30 — Attack speed +2 for type 3; Range +2 for type 3; Production +2 for type 14, +2 for type 4
            new TechTreeNode(30, "Sniper Upgrade",
                "Attack speed +2, Range +2 for Sniper (type 3); Production +2 for types 14, 4",
                Faction.RESISTANCE, 85, 450, List.of(29), List.of(32)),

            // === Machinery Chain B (Tier 5) ===
            // REF: combat_formulas.md research ID 31 — Attack speed +1 for types 4, 5; Production +1 for types 6, 8
            new TechTreeNode(31, "Light Vehicle Speed Upgrade",
                "Attack speed +1 for types 4, 5; Production +1 for types 6, 8",
                Faction.RESISTANCE, 80, 420, List.of(29), List.of(32)),

            // === Heavy Machinery Merge Point (Tier 6) ===
            // REF: combat_formulas.md research ID 32 — Attack range -1 for types 6,8,10,15,12; Building radius +1
            new TechTreeNode(32, "Heavy Machinery Range Adjust",
                "Attack range -1 for types 6, 8, 10, 15, 12; Building radius +1",
                Faction.RESISTANCE, 95, 500, List.of(30, 31), List.of(33, 34)),

            // === Heavy Machinery Upgrades (Tier 7) ===
            // REF: combat_formulas.md research ID 33 — Infantry armour +1 for types 6,8,10,15,12
            new TechTreeNode(33, "Machinery Armour Upgrade",
                "Infantry armour +1 for types 6, 8, 10, 15, 12",
                Faction.RESISTANCE, 85, 450, List.of(32), List.of(35, 36)),

            // REF: combat_formulas.md research ID 34 — Player 1 attack range reduction /3
            new TechTreeNode(34, "Advanced Signal Jamming",
                "Player 1 attack range reduction /3",
                Faction.RESISTANCE, 75, 400, List.of(32), List.of()),

            // === Advanced Weapons (Tier 8) ===
            // REF: combat_formulas.md research ID 35 — Attack speed -2 (faster) for types 12, 14
            new TechTreeNode(35, "Rapid Reload",
                "Attack speed -2 (faster) for types 12, 14",
                Faction.RESISTANCE, 90, 480, List.of(33), List.of(37)),

            // REF: combat_formulas.md research ID 36 — Unit type 10 siege upgrade = 15 (Mine Lizard)
            new TechTreeNode(36, "Mine Lizard Siege Mode",
                "Unit type 10 siege upgrade = 15 (Mine Lizard siege mode)",
                Faction.RESISTANCE, 100, 520, List.of(33), List.of(37)),

            // === Advanced Weapons Merge (Tier 9) ===
            // REF: combat_formulas.md research ID 37 — Building radius +1
            new TechTreeNode(37, "Advanced Building Radius",
                "Building radius +1",
                Faction.RESISTANCE, 85, 450, List.of(35, 36), List.of(38)),

            // === Artillery Finale (Tier 10) ===
            // REF: combat_formulas.md research ID 38 — Attack damage +2 for type 20; Range +2 for type 20; Production +2 for type 12
            new TechTreeNode(38, "MLRS Torrent Upgrade",
                "Attack damage +2 for type 20, Range +2; Production +2 for type 12",
                Faction.RESISTANCE, 120, 600, List.of(37), List.of()),

            // === Economy Chain ===
            // REF: combat_formulas.md research ID 39 — Player 1 supply cap = 8
            new TechTreeNode(39, "Supply Logistics",
                "Supply cap = 8",
                Faction.RESISTANCE, 60, 350, List.of(), List.of(41, 44, 45)),

            // REF: combat_formulas.md research ID 40 — Player 1 building armour = 9
            new TechTreeNode(40, "Building Armour Override",
                "Building armour override = 9",
                Faction.RESISTANCE, 70, 380, List.of(), List.of(41)),

            // REF: combat_formulas.md research ID 41 — Building radius +1
            new TechTreeNode(41, "Enhanced Building Radius",
                "Building radius +1",
                Faction.RESISTANCE, 75, 400, List.of(39, 40), List.of(42)),

            // REF: combat_formulas.md research ID 42 — Building radius +1 (cumulative)
            new TechTreeNode(42, "Cumulative Building Radius",
                "Building radius +1 (cumulative)",
                Faction.RESISTANCE, 80, 420, List.of(41), List.of()),

            // REF: combat_formulas.md research ID 44 — Player 1 production P[5] = 7
            new TechTreeNode(44, "Advanced Production",
                "Production P[5] = 7",
                Faction.RESISTANCE, 65, 360, List.of(39), List.of()),

            // === Scoring Chain ===
            // REF: combat_formulas.md research ID 45 — Player 1 credit limit Q[1] = 120
            new TechTreeNode(45, "Finance Department",
                "Credit limit = 120",
                Faction.RESISTANCE, 70, 380, List.of(39), List.of(46)),

            // REF: combat_formulas.md research ID 46 — Player 1 score bonus S[1] = 30
            new TechTreeNode(46, "Incentive System",
                "Score bonus = 30",
                Faction.RESISTANCE, 90, 460, List.of(45), List.of(47)),

            // REF: combat_formulas.md research ID 47 — Player 1 display bonus = 25
            new TechTreeNode(47, "Communications System",
                "Display bonus = 25",
                Faction.RESISTANCE, 100, 500, List.of(46), List.of())
        );
    }
}
