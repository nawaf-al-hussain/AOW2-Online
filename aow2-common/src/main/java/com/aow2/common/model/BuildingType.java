package com.aow2.common.model;

/**
 * All building types from the original game.
 * REF: complete_building_stats.json - 8 buildings per faction
 * Type IDs from building_stats.md: CommandCentre=1, Barracks=2, MachineFactory=3,
 * Generator=4, TechCentre=5, Headquarters=6, Bunker=7, RocketLauncher=8, Wall=9, Locator=10
 */
public enum BuildingType {
    // Confederation Buildings
    CONFED_COMMAND_CENTRE(1, "Command Centre", Faction.CONFEDERATION),
    CONFED_GENERATOR(4, "Generator", Faction.CONFEDERATION),
    CONFED_INFANTRY_CENTRE(2, "Infantry Centre", Faction.CONFEDERATION),
    CONFED_MACHINE_FACTORY(3, "Machine Factory", Faction.CONFEDERATION),
    CONFED_TECH_CENTRE(5, "Technology Centre", Faction.CONFEDERATION),
    CONFED_BUNKER(7, "Bunker", Faction.CONFEDERATION),
    CONFED_LOCATOR(10, "Locator", Faction.CONFEDERATION),
    CONFED_ROCKET_LAUNCHER(8, "Rocket Launcher", Faction.CONFEDERATION),

    // Resistance Buildings
    REBEL_HEADQUARTERS(6, "Headquarters", Faction.RESISTANCE),
    REBEL_POWERPLANT(4, "Powerplant", Faction.RESISTANCE),
    REBEL_BARRACKS(2, "Barracks", Faction.RESISTANCE),
    REBEL_FACTORY(3, "Factory", Faction.RESISTANCE),
    REBEL_LABORATORY(5, "Laboratory", Faction.RESISTANCE),
    REBEL_BUNKER(7, "Bunker", Faction.RESISTANCE),
    REBEL_TOWER(8, "Tower", Faction.RESISTANCE),
    REBEL_WALL(9, "Wall", Faction.RESISTANCE);

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
