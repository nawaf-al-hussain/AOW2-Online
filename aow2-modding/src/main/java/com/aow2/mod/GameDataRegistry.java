package com.aow2.mod;

import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all game data (unit stats, building stats, etc.)
 * that can be modified by mods. Stores base (original) stats and allows
 * mods to override individual fields.
 * <p>
 * REF: phases.md Phase 10 - GameDataRegistry for moddable stats
 */
public final class GameDataRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(GameDataRegistry.class);

    /** Base unit stats keyed by UnitType. */
    private final Map<UnitType, UnitStats> baseUnitStats;

    /** Base building stats keyed by BuildingType. */
    private final Map<BuildingType, BuildingStats> baseBuildingStats;

    /** Active overrides keyed by "targetType:targetId:field". */
    private final Map<String, DataOverride> activeOverrides;

    /** Modified (overridden) unit stats cache. */
    private final Map<UnitType, UnitStats> modifiedUnitStats;

    /** Modified (overridden) building stats cache. */
    private final Map<BuildingType, BuildingStats> modifiedBuildingStats;

    /**
     * Constructs an empty GameDataRegistry.
     */
    public GameDataRegistry() {
        this.baseUnitStats = new ConcurrentHashMap<>();
        this.baseBuildingStats = new ConcurrentHashMap<>();
        this.activeOverrides = new ConcurrentHashMap<>();
        this.modifiedUnitStats = new ConcurrentHashMap<>();
        this.modifiedBuildingStats = new ConcurrentHashMap<>();
    }

    /**
     * Registers base unit stats.
     *
     * @param type  unit type
     * @param stats base stats for this unit type
     */
    public void registerUnitStats(UnitType type, UnitStats stats) {
        baseUnitStats.put(type, stats);
        modifiedUnitStats.put(type, stats);
    }

    /**
     * Registers base building stats.
     *
     * @param type     building type
     * @param stats    base stats for this building type
     */
    public void registerBuildingStats(BuildingType type, BuildingStats stats) {
        baseBuildingStats.put(type, stats);
        modifiedBuildingStats.put(type, stats);
    }

    /**
     * Gets the current (possibly overridden) unit stats for a type.
     *
     * @param type unit type
     * @return current stats, or null if not registered
     */
    public UnitStats getUnitStats(UnitType type) {
        return modifiedUnitStats.get(type);
    }

    /**
     * Gets the current (possibly overridden) building stats for a type.
     *
     * @param type building type
     * @return current stats, or null if not registered
     */
    public BuildingStats getBuildingStats(BuildingType type) {
        return modifiedBuildingStats.get(type);
    }

    /**
     * Gets the base (unmodified) unit stats for a type.
     *
     * @param type unit type
     * @return base stats, or null if not registered
     */
    public UnitStats getBaseUnitStats(UnitType type) {
        return baseUnitStats.get(type);
    }

    /**
     * Gets the base (unmodified) building stats for a type.
     *
     * @param type building type
     * @return base stats, or null if not registered
     */
    public BuildingStats getBaseBuildingStats(BuildingType type) {
        return baseBuildingStats.get(type);
    }

    /**
     * Applies a data override to the registry.
     * The override modifies the cached stats object.
     *
     * @param override the data override to apply
     */
    public void applyOverride(DataOverride override) {
        String key = override.targetType() + ":" + override.targetId() + ":" + override.field();
        activeOverrides.put(key, override);

        rebuildModifiedStats(override.targetType(), override.targetId());
        LOG.debug("Applied override: {} -> {} = {}", override.targetId(), override.field(), override.value());
    }

    /**
     * Removes all overrides and resets to base stats.
     */
    public void resetAllOverrides() {
        activeOverrides.clear();
        modifiedUnitStats.clear();
        modifiedBuildingStats.clear();

        for (var entry : baseUnitStats.entrySet()) {
            modifiedUnitStats.put(entry.getKey(), entry.getValue());
        }
        for (var entry : baseBuildingStats.entrySet()) {
            modifiedBuildingStats.put(entry.getKey(), entry.getValue());
        }

        LOG.info("All overrides reset to base stats");
    }

    /**
     * Returns an unmodifiable view of all active overrides.
     *
     * @return active overrides map
     */
    public Map<String, DataOverride> getActiveOverrides() {
        return Collections.unmodifiableMap(activeOverrides);
    }

    /**
     * Returns the number of registered unit types.
     *
     * @return unit type count
     */
    public int unitTypeCount() {
        return baseUnitStats.size();
    }

    /**
     * Returns the number of registered building types.
     *
     * @return building type count
     */
    public int buildingTypeCount() {
        return baseBuildingStats.size();
    }

    /**
     * Returns the number of active overrides.
     *
     * @return override count
     */
    public int overrideCount() {
        return activeOverrides.size();
    }

    /**
     * Rebuilds modified stats for a target by applying all matching overrides
     * to the base stats.
     *
     * @param targetType the type of data ("unit" or "building")
     * @param targetId   the specific type name
     */
    private void rebuildModifiedStats(String targetType, String targetId) {
        if ("unit".equalsIgnoreCase(targetType)) {
            rebuildUnitStats(targetId);
        } else if ("building".equalsIgnoreCase(targetType)) {
            rebuildBuildingStats(targetId);
        }
    }

    /**
     * Rebuilds unit stats by applying overrides to base stats using reflection.
     * ASSUMPTION: override field names match UnitStats record component names.
     */
    private void rebuildUnitStats(String targetId) {
        try {
            UnitType type = UnitType.valueOf(targetId);
            UnitStats base = baseUnitStats.get(type);
            if (base == null) return;

            // Collect overrides for this unit
            Map<String, DataOverride> unitOverrides = new HashMap<>();
            for (var entry : activeOverrides.entrySet()) {
                if ("unit".equalsIgnoreCase(entry.getValue().targetType()) &&
                    targetId.equals(entry.getValue().targetId())) {
                    unitOverrides.put(entry.getValue().field(), entry.getValue());
                }
            }

            if (unitOverrides.isEmpty()) {
                modifiedUnitStats.put(type, base);
                return;
            }

            // Apply overrides to create new stats record
            UnitStats modified = applyUnitOverrides(base, unitOverrides);
            modifiedUnitStats.put(type, modified);

        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown unit type in override: {}", targetId);
        }
    }

    /**
     * Applies override values to a UnitStats record.
     * FIX (M-NEW-15): Replaced manual field-by-field copying with reflection-based approach.
     * When new fields are added to UnitStats, this method automatically handles them
     * without requiring code changes here. Falls back to manual approach if reflection fails.
     */
    @SuppressWarnings("unchecked")
    private UnitStats applyUnitOverrides(UnitStats base, Map<String, DataOverride> overrides) {
        if (overrides.isEmpty()) return base;

        // Try reflection-based approach for forward compatibility
        try {
            var components = UnitStats.class.getRecordComponents();
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                var comp = components[i];
                String name = comp.getName();
                if (overrides.containsKey(name)) {
                    args[i] = overrides.get(name).intValue();
                } else {
                    args[i] = comp.getAccessor().invoke(base);
                }
            }
            var ctor = UnitStats.class.getDeclaredConstructor(
                java.util.Arrays.stream(components).map(c -> c.getType()).toArray(Class[]::new));
            return (UnitStats) ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            LOG.warn("Reflection-based override failed, falling back to manual: {}", e.getMessage());
        }

        // Fallback: manual field copying (kept for robustness)
        return applyUnitOverridesManual(base, overrides);
    }

    /** Manual fallback for applyUnitOverrides when reflection fails. */
    private UnitStats applyUnitOverridesManual(UnitStats base, Map<String, DataOverride> overrides) {
        int hp = base.hp();
        int damage = base.damage();
        int speed = base.speed();
        int armor = base.armor();
        int attackBonus = base.attackBonus();
        int sightRange = base.sightRange();
        int attackRange = base.attackRange();
        WeaponType weaponType = base.weaponType();
        int attackSpeed = base.attackSpeed();
        int buildTime = base.buildTime();
        int costCredits = base.costCredits();
        int rewardCredits = base.rewardCredits();
        int extendedArmor = base.extendedArmor();
        int siegeTargets = base.siegeTargets();
        int upgradeLevel = base.upgradeLevel();
        int availabilityFlag = base.availabilityFlag();

        if (overrides.containsKey("hp")) hp = overrides.get("hp").intValue();
        if (overrides.containsKey("damage")) damage = overrides.get("damage").intValue();
        if (overrides.containsKey("speed")) speed = overrides.get("speed").intValue();
        if (overrides.containsKey("armor")) armor = overrides.get("armor").intValue();
        if (overrides.containsKey("attackBonus")) attackBonus = overrides.get("attackBonus").intValue();
        if (overrides.containsKey("sightRange")) sightRange = overrides.get("sightRange").intValue();
        if (overrides.containsKey("attackRange")) attackRange = overrides.get("attackRange").intValue();
        if (overrides.containsKey("attackSpeed")) attackSpeed = overrides.get("attackSpeed").intValue();
        if (overrides.containsKey("buildTime")) buildTime = overrides.get("buildTime").intValue();
        if (overrides.containsKey("costCredits")) costCredits = overrides.get("costCredits").intValue();
        if (overrides.containsKey("rewardCredits")) rewardCredits = overrides.get("rewardCredits").intValue();
        if (overrides.containsKey("extendedArmor")) extendedArmor = overrides.get("extendedArmor").intValue();
        if (overrides.containsKey("siegeTargets")) siegeTargets = overrides.get("siegeTargets").intValue();
        if (overrides.containsKey("upgradeLevel")) upgradeLevel = overrides.get("upgradeLevel").intValue();
        if (overrides.containsKey("availabilityFlag")) availabilityFlag = overrides.get("availabilityFlag").intValue();

        return new UnitStats(
            base.unitType(), base.description(), hp, damage, speed, armor,
            attackBonus, sightRange, attackRange, weaponType, attackSpeed,
            buildTime, costCredits, rewardCredits,
            extendedArmor, siegeTargets, upgradeLevel, availabilityFlag
        );
    }

    /**
     * Rebuilds building stats by applying overrides to base stats.
     */
    private void rebuildBuildingStats(String targetId) {
        try {
            BuildingType type = BuildingType.valueOf(targetId);
            BuildingStats base = baseBuildingStats.get(type);
            if (base == null) return;

            Map<String, DataOverride> buildingOverrides = new HashMap<>();
            for (var entry : activeOverrides.entrySet()) {
                if ("building".equalsIgnoreCase(entry.getValue().targetType()) &&
                    targetId.equals(entry.getValue().targetId())) {
                    buildingOverrides.put(entry.getValue().field(), entry.getValue());
                }
            }

            if (buildingOverrides.isEmpty()) {
                modifiedBuildingStats.put(type, base);
                return;
            }

            BuildingStats modified = applyBuildingOverrides(base, buildingOverrides);
            modifiedBuildingStats.put(type, modified);

        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown building type in override: {}", targetId);
        }
    }

    /**
     * Applies override values to a BuildingStats record.
     */
    private BuildingStats applyBuildingOverrides(BuildingStats base, Map<String, DataOverride> overrides) {
        int hp = base.hp();
        int baseCost = base.baseCost();
        int attackSpeed = base.attackSpeed();
        int armor = base.armor();
        int attackBonus = base.attackBonus();
        int sightRange = base.sightRange();
        int buildTime = base.buildTime();
        int attackRange = base.attackRange();
        int extendedArmor = base.extendedArmor();
        int powerConsume = base.powerConsume();
        int powerProduce = base.powerProduce();
        int queueSlots = base.queueSlots();
        int techRequirement = base.techRequirement();
        int costCredits = base.costCredits();
        int rewardCredits = base.rewardCredits();
        int garrisonCapacity = base.garrisonCapacity();
        WeaponType weaponType = base.weaponType();

        if (overrides.containsKey("hp")) hp = overrides.get("hp").intValue();
        if (overrides.containsKey("baseCost")) baseCost = overrides.get("baseCost").intValue();
        if (overrides.containsKey("attackSpeed")) attackSpeed = overrides.get("attackSpeed").intValue();
        if (overrides.containsKey("armor")) armor = overrides.get("armor").intValue();
        if (overrides.containsKey("attackBonus")) attackBonus = overrides.get("attackBonus").intValue();
        if (overrides.containsKey("sightRange")) sightRange = overrides.get("sightRange").intValue();
        if (overrides.containsKey("buildTime")) buildTime = overrides.get("buildTime").intValue();
        if (overrides.containsKey("attackRange")) attackRange = overrides.get("attackRange").intValue();
        if (overrides.containsKey("extendedArmor")) extendedArmor = overrides.get("extendedArmor").intValue();
        if (overrides.containsKey("powerConsume")) powerConsume = overrides.get("powerConsume").intValue();
        if (overrides.containsKey("powerProduce")) powerProduce = overrides.get("powerProduce").intValue();
        if (overrides.containsKey("queueSlots")) queueSlots = overrides.get("queueSlots").intValue();
        if (overrides.containsKey("techRequirement")) techRequirement = overrides.get("techRequirement").intValue();
        if (overrides.containsKey("costCredits")) costCredits = overrides.get("costCredits").intValue();
        if (overrides.containsKey("rewardCredits")) rewardCredits = overrides.get("rewardCredits").intValue();
        if (overrides.containsKey("garrisonCapacity")) garrisonCapacity = overrides.get("garrisonCapacity").intValue();

        return new BuildingStats(
            base.buildingType(), hp, baseCost, attackSpeed, armor, attackBonus,
            sightRange, buildTime, attackRange, extendedArmor, powerConsume,
            powerProduce, queueSlots, techRequirement, costCredits, rewardCredits,
            garrisonCapacity, weaponType, base.upgradeCosts()
        );
    }
}
