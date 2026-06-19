package com.aow2.common.model;

/**
 * All building types from the original game.
 * REF: complete_building_stats.json - 8 buildings per faction
 * Type IDs use faction-qualified IDs to avoid collisions:
 *   Confederation: 100-109 (1xx prefix)
 *   Resistance:    200-209 (2xx prefix)
 *
 * For lookups by faction-relative ID, use {@link #fromFactionRelativeId(int, Faction)}.
 */
public enum BuildingType {
    // Confederation Buildings (type IDs 100-109)
    CONFED_COMMAND_CENTRE(101, "Command Centre", Faction.CONFEDERATION),
    CONFED_GENERATOR(104, "Generator", Faction.CONFEDERATION),
    CONFED_INFANTRY_CENTRE(102, "Infantry Centre", Faction.CONFEDERATION),
    CONFED_MACHINE_FACTORY(103, "Machine Factory", Faction.CONFEDERATION),
    CONFED_TECH_CENTRE(105, "Technology Centre", Faction.CONFEDERATION),
    CONFED_BUNKER(107, "Bunker", Faction.CONFEDERATION),
    CONFED_LOCATOR(110, "Locator", Faction.CONFEDERATION),
    CONFED_ROCKET_LAUNCHER(108, "Rocket Launcher", Faction.CONFEDERATION),

    // Resistance Buildings (type IDs 200-209)
    REBEL_HEADQUARTERS(206, "Headquarters", Faction.RESISTANCE),
    REBEL_POWERPLANT(204, "Powerplant", Faction.RESISTANCE),
    REBEL_BARRACKS(202, "Barracks", Faction.RESISTANCE),
    REBEL_FACTORY(203, "Factory", Faction.RESISTANCE),
    REBEL_LABORATORY(205, "Laboratory", Faction.RESISTANCE),
    REBEL_BUNKER(207, "Bunker", Faction.RESISTANCE),
    REBEL_TOWER(208, "Tower", Faction.RESISTANCE),
    REBEL_WALL(209, "Wall", Faction.RESISTANCE);

    private final int typeId;
    private final String displayName;
    private final Faction faction;

    BuildingType(int typeId, String displayName, Faction faction) {
        this.typeId = typeId;
        this.displayName = displayName;
        this.faction = faction;
    }

    public int typeId() { return typeId; }
    public String displayName() { return displayName; }
    public Faction faction() { return faction; }

    /**
     * Look up a BuildingType by its unique (faction-qualified) type ID.
     *
     * @param typeId the faction-qualified type ID (100-109 for Confed, 200-209 for Rebel)
     * @return the matching BuildingType, or null if not found
     */
    public static BuildingType fromTypeId(int typeId) {
        for (BuildingType bt : values()) {
            if (bt.typeId == typeId) return bt;
        }
        return null;
    }

    /**
     * Look up a BuildingType by its faction-relative type ID.
     * Faction-relative IDs: CommandCentre=1, InfantryCentre/Barracks=2,
     * MachineFactory/Factory=3, Generator/Powerplant=4, TechCentre/Lab=5,
     * Headquarters=6, Bunker=7, RocketLauncher/Tower=8, Wall=9, Locator=10
     *
     * @param relativeId the faction-relative type ID (1-10)
     * @param faction    the faction context
     * @return the matching BuildingType, or null if not found
     */
    public static BuildingType fromFactionRelativeId(int relativeId, Faction faction) {
        int qualifiedId = faction == Faction.CONFEDERATION ? 100 + relativeId : 200 + relativeId;
        return fromTypeId(qualifiedId);
    }

    public boolean isHQ() {
        return this == CONFED_COMMAND_CENTRE || this == REBEL_HEADQUARTERS;
    }
    public boolean isDefensive() {
        return this == CONFED_BUNKER || this == CONFED_ROCKET_LAUNCHER ||
               this == REBEL_BUNKER || this == REBEL_TOWER || this == REBEL_WALL;
    }
    /**
     * Whether this building produces power.
     * REF: complete_building_stats.json — Command Centre produces powerProduce=6
     * @return true if this building generates power
     */
    public boolean producesPower() {
        return this == CONFED_GENERATOR || this == REBEL_POWERPLANT ||
               this == CONFED_COMMAND_CENTRE || this == REBEL_HEADQUARTERS;
    }
    public boolean producesUnits() {
        return this == CONFED_INFANTRY_CENTRE || this == CONFED_MACHINE_FACTORY ||
               this == REBEL_BARRACKS || this == REBEL_FACTORY;
    }
    public boolean researches() {
        return this == CONFED_TECH_CENTRE || this == REBEL_LABORATORY;
    }

    /**
     * Whether this building generates credits each credit cycle.
     * REF: combat_formulas.md — HQ/CC generates credits every cycle
     * @return true if this building is an income building
     */
    public boolean isIncomeBuilding() {
        return this == CONFED_COMMAND_CENTRE || this == REBEL_HEADQUARTERS;
    }
}
