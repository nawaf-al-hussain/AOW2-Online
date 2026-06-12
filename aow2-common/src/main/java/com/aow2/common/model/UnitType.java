package com.aow2.common.model;

/**
 * All unit types from the original game.
 * REF: complete_unit_stats.json - 7 Confederation units + 7 Rebels units + 3 mines
 * Type IDs from unit_stats.md: Infantry=1, Grenadier=2, Sniper=3, LightAssault=4,
 * HeavyAssault=7, FlameAssault=8, MineFrog=9, MineLizard=10, MineScorpio=11,
 * Coyote=15, Zeus=16, Hammer=17, Rhino=18, Fortress=19, Torrent=20, Armadillo=21, Porcupine=22
 */
public enum UnitType {
    // Confederation Infantry
    CONFED_INFANTRY(1, "Infantry", Faction.CONFEDERATION, UnitCategory.INFANTRY),
    CONFED_GRENADIER(2, "Grenadier", Faction.CONFEDERATION, UnitCategory.INFANTRY),
    CONFED_FLAME_ASSAULT(8, "Flame Assault", Faction.CONFEDERATION, UnitCategory.INFANTRY),

    // Confederation Vehicles
    CONFED_FORTRESS(19, "AV-40 Fortress", Faction.CONFEDERATION, UnitCategory.VEHICLE),
    CONFED_HAMMER(17, "T-21 Hammer", Faction.CONFEDERATION, UnitCategory.VEHICLE),
    CONFED_ZEUS(16, "T-22 Zeus", Faction.CONFEDERATION, UnitCategory.VEHICLE),
    CONFED_TORRENT(20, "MLRS Torrent", Faction.CONFEDERATION, UnitCategory.VEHICLE),

    // Confederation Mines
    CONFED_MINE_SCORPIO(11, "Mine Scorpio", Faction.CONFEDERATION, UnitCategory.MINE),
    CONFED_MINE_FROG(9, "Mine Frog", Faction.CONFEDERATION, UnitCategory.MINE),
    CONFED_MINE_LIZARD(10, "Mine Lizard", Faction.CONFEDERATION, UnitCategory.MINE),

    // Resistance Infantry
    REBEL_INFANTRY(1, "Infantry", Faction.RESISTANCE, UnitCategory.INFANTRY),
    REBEL_GRENADIER(2, "Grenadier", Faction.RESISTANCE, UnitCategory.INFANTRY),
    REBEL_SNIPER(3, "Sniper", Faction.RESISTANCE, UnitCategory.INFANTRY),

    // Resistance Vehicles
    REBEL_COYOTE(15, "Coyote", Faction.RESISTANCE, UnitCategory.VEHICLE),
    REBEL_ARMADILLO(21, "Armadillo", Faction.RESISTANCE, UnitCategory.VEHICLE),
    REBEL_RHINO(18, "Rhino", Faction.RESISTANCE, UnitCategory.VEHICLE),
    REBEL_PORCUPINE(22, "MMC Porcupine", Faction.RESISTANCE, UnitCategory.VEHICLE);

    private final int typeId;
    private final String displayName;
    private final Faction faction;
    private final UnitCategory category;

    UnitType(int typeId, String displayName, Faction faction, UnitCategory category) {
        this.typeId = typeId;
        this.displayName = displayName;
        this.faction = faction;
        this.category = category;
    }

    public int typeId() { return typeId; }
    public String displayName() { return displayName; }
    public Faction faction() { return faction; }
    public UnitCategory category() { return category; }

    public boolean isInfantry() { return category == UnitCategory.INFANTRY; }
    public boolean isVehicle() { return category == UnitCategory.VEHICLE; }
    public boolean isMine() { return category == UnitCategory.MINE; }
    public boolean isLargeUnit() {
        // REF: unit_stats.md - bitmask 65536 (0x10000) for 2-cell collision units
        return this == CONFED_FORTRESS;
    }
}
