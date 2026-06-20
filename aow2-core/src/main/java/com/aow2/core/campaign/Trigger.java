package com.aow2.core.campaign;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;

/**
 * Mission trigger that activates based on game events.
 * Triggers are checked each game tick and fire callbacks when their conditions are met.
 * REF: campaign_guide.md Section 3.1 - reinforcement scheduling based on game ticks
 * REF: campaign_guide.md Section 3.2 - event types: area entry, unit count, time, building destroyed
 */
public sealed interface Trigger permits
    Trigger.AreaTrigger, Trigger.UnitCountTrigger,
    Trigger.TimeTrigger, Trigger.BuildingDestroyedTrigger {

    /**
     * Returns the unique trigger identifier.
     *
     * @return trigger ID
     */
    int triggerId();

    /**
     * Returns whether this trigger has been activated.
     *
     * @return true if activated
     */
    boolean isActivated();

    /**
     * Checks whether this trigger should activate based on the current game state.
     * Returns this trigger if not activated, or the activated version if the
     * condition is met. The caller should replace the old trigger with the
     * returned one.
     *
     * @param state     current game state
     * @param entities  entity manager for spatial and faction queries
     * @return this trigger if not activated, or the activated version
     */
    Trigger check(GameState state, EntityManager entities);

    /**
     * Area trigger: fires when any unit of the specified faction enters a circular area.
     * REF: campaign_guide.md Section 3.1 - am[1]/am[2] X/Y position, am[4] trigger time
     */
    record AreaTrigger(
        int triggerId,
        GridPosition center,
        int radius,
        int factionId,
        boolean activated
    ) implements Trigger {

        @Override
        public boolean isActivated() {
            return activated;
        }

        @Override
        public Trigger check(GameState state, EntityManager entities) {
            if (activated) {
                return this;
            }
            // Check if any unit of the specified faction is within the radius
            for (var unit : entities.getAliveUnitsForPlayer(
                    factionId == 0 ? Faction.CONFEDERATION : Faction.RESISTANCE)) {
                int dx = unit.getPosition().x() - center.x();
                int dy = unit.getPosition().y() - center.y();
                if (dx * dx + dy * dy <= radius * radius) {
                    return activate();
                }
            }
            return this;
        }

        /**
         * Creates a new AreaTrigger with the activated flag set.
         *
         * @return new trigger with activated = true
         */
        public AreaTrigger activate() {
            return new AreaTrigger(triggerId, center, radius, factionId, true);
        }
    }

    /**
     * Unit count trigger: fires when a faction's unit count of a specific type exceeds a threshold.
     * REF: campaign_guide.md Section 4.3 - AI reinforcement logic checks unit composition
     */
    record UnitCountTrigger(
        int triggerId,
        Faction faction,
        UnitType unitType,
        int threshold,
        boolean activated
    ) implements Trigger {

        @Override
        public boolean isActivated() {
            return activated;
        }

        @Override
        public Trigger check(GameState state, EntityManager entities) {
            if (activated) {
                return this;
            }
            long count = entities.getAliveUnitsForPlayer(faction).stream()
                .filter(u -> u.getUnitType() == unitType)
                .count();
            if (count >= threshold) {
                return activate();
            }
            return this;
        }

        /**
         * Creates a new UnitCountTrigger with the activated flag set.
         *
         * @return new trigger with activated = true
         */
        public UnitCountTrigger activate() {
            return new UnitCountTrigger(triggerId, faction, unitType, threshold, true);
        }
    }

    /**
     * Time trigger: fires at a specific game tick.
     * REF: campaign_guide.md Section 3.1 - am[4] trigger time in game ticks / 10
     */
    record TimeTrigger(
        int triggerId,
        long triggerTick,
        boolean activated
    ) implements Trigger {

        @Override
        public boolean isActivated() {
            return activated;
        }

        @Override
        public Trigger check(GameState state, EntityManager entities) {
            if (activated) {
                return this;
            }
            if (state.currentTick() >= triggerTick) {
                return activate();
            }
            return this;
        }

        /**
         * Creates a new TimeTrigger with the activated flag set.
         *
         * @return new trigger with activated = true
         */
        public TimeTrigger activate() {
            return new TimeTrigger(triggerId, triggerTick, true);
        }
    }

    /**
     * Building destroyed trigger: fires when a building of the specified type and faction is destroyed.
     * REF: campaign_guide.md Section 3.4 - building destruction triggers victory/defeat checks
     */
    record BuildingDestroyedTrigger(
        int triggerId,
        BuildingType buildingType,
        Faction faction,
        boolean activated
    ) implements Trigger {

        @Override
        public boolean isActivated() {
            return activated;
        }

        @Override
        public Trigger check(GameState state, EntityManager entities) {
            if (activated) {
                return this;
            }
            // Check if any building of the specified type and faction is no longer alive
            for (var building : entities.getBuildingsForPlayer(faction)) {
                if (building.getBuildingType() == buildingType && !building.isAlive()) {
                    return activate();
                }
            }
            return this;
        }

        /**
         * Creates a new BuildingDestroyedTrigger with the activated flag set.
         *
         * @return new trigger with activated = true
         */
        public BuildingDestroyedTrigger activate() {
            return new BuildingDestroyedTrigger(triggerId, buildingType, faction, true);
        }
    }
}
