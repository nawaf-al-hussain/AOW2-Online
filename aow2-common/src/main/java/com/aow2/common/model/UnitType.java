package com.aow2.common.model;

/**
 * All unit types from the original game.
 * REF: complete_unit_stats.json - 9 Confederation units + 7 Rebels units + 3 mines
 * REF: game_data.json - unit type definitions with categories
 * REF: unit_stats.md - Type IDs: Infantry=1, Grenadier=2, Sniper=3, LightAssault=4,
 * HeavyAssault=7, FlameAssault=8, MineFrog=9, MineLizard=10, MineScorpio=11,
 * Coyote=15, Zeus=16, Hammer=17, Rhino=18, Fortress=19, Torrent=20, Armadillo=21, Porcupine=22
 *
 * FIX LOG:
 * - Added CONFED_LIGHT_ASSAULT (typeId=4) and CONFED_HEAVY_ASSAULT (typeId=7)
 * - Changed CONFED_FLAME_ASSAULT category from INFANTRY to VEHICLE
 *   (REF: game_data.json categorizes as "machinery", unit_stats.md as "Special machinery")
 * - Added isSiegeCapable() method for Hammer, Rhino, Fortress, Torrent, Sniper
 */
public enum UnitType {
    // Confederation Infantry
    CONFED_INFANTRY(1, "Infantry", Faction.CONFEDERATION, UnitCategory.INFANTRY),
    CONFED_GRENADIER(2, "Grenadier", Faction.CONFEDERATION, UnitCategory.INFANTRY),

    // Confederation Vehicles (including Light Assault, Heavy Assault, Flame Assault)
    CONFED_LIGHT_ASSAULT(4, "Light Assault", Faction.CONFEDERATION, UnitCategory.VEHICLE),
    CONFED_HEAVY_ASSAULT(7, "Heavy Assault", Faction.CONFEDERATION, UnitCategory.VEHICLE),
    CONFED_FLAME_ASSAULT(8, "Flame Assault", Faction.CONFEDERATION, UnitCategory.VEHICLE),
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

    /**
     * Whether this unit type occupies 2 cells (larger collision footprint).
     * REF: unit_stats.md - bitmask 65536 (0x10000) for 2-cell collision units
     *
     * @return true if this unit occupies 2 cells
     */
    public boolean isLargeUnit() {
        return this == CONFED_FORTRESS;
    }

    /**
     * Whether this unit type can enter siege mode.
     * REF: combat_formulas.md - siege mode for Fortress, Hammer, Torrent
     * REF: unit_stats.md - Hammer: "Upgrade allows to switch to the siege mode"
     * REF: unit_stats.md - Rhino: "Upgrade allows siege mode which increases damage and firing rate"
     * REF: unit_stats.md - Fortress: "For rocket salvo you need to activate siege mode"
     *
     * @return true if this unit can enter siege mode
     */
    public boolean isSiegeCapable() {
        return this == CONFED_FORTRESS || this == CONFED_HAMMER || this == CONFED_TORRENT
            || this == REBEL_RHINO || this == REBEL_SNIPER;
    }
}
