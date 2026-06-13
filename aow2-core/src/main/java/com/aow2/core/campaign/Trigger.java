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
     *
     * @param state     current game state
     * @param entities  entity manager for spatial and faction queries
     */
    void check(GameState state, EntityManager entities);

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
        public void check(GameState state, EntityManager entities) {
            // ASSUMPTION: Check is performed by CampaignManager after entity movement;
            // activation state is updated via withActivated() when the condition is met.
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
        public void check(GameState state, EntityManager entities) {
            if (activated) {
                return;
            }
            long count = entities.getAliveUnitsForPlayer(faction).stream()
                .filter(u -> u.getUnitType() == unitType)
                .count();
            if (count >= threshold) {
                // ASSUMPTION: Caller replaces this trigger with activate() result
            }
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
        public void check(GameState state, EntityManager entities) {
            if (activated) {
                return;
            }
            if (state.currentTick() >= triggerTick) {
                // ASSUMPTION: Caller replaces this trigger with activate() result
            }
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
        public void check(GameState state, EntityManager entities) {
            if (activated) {
                return;
            }
            // ASSUMPTION: Checked by CampaignManager when BuildingDestroyedEvent is received
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
