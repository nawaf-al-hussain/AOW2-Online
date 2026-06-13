package com.aow2.common.model;

/**
 * Sealed interface for all player commands in the game.
 * Used by lockstep networking and replay system to ensure deterministic simulation.
 * Each command records the tick at which it should be processed and the issuing player.
 * REF: protocol_specification.md - 34 multiplayer message types
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Lockstep networking
 */
public sealed interface CommandType permits
    CommandType.Move, CommandType.Attack, CommandType.Build,
    CommandType.Produce, CommandType.Research, CommandType.Garrison,
    CommandType.Ungarrison, CommandType.Cancel, CommandType.SiegeMode,
    CommandType.Stop, CommandType.Patrol {

    /**
     * The tick at which this command should be processed.
     *
     * @return game tick number
     */
    long tick();

    /**
     * The ID of the player who issued this command.
     *
     * @return player identifier (0 or 1)
     */
    int playerId();

    /**
     * Move one or more units to a target position.
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitIds  entity IDs of units to move
     * @param target   target grid position
     */
    record Move(long tick, int playerId, int[] unitIds, GridPosition target) implements CommandType {
        public Move {
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (target == null) {
                throw new IllegalArgumentException("target must not be null");
            }
        }
    }

    /**
     * Order one or more units to attack a target entity.
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitIds  entity IDs of attacking units
     * @param targetId entity ID of the target
     */
    record Attack(long tick, int playerId, int[] unitIds, int targetId) implements CommandType {
        public Attack {
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (targetId < 0) {
                throw new IllegalArgumentException("targetId must not be negative, got: " + targetId);
            }
        }
    }

    /**
     * Construct a building at a specified position.
     *
     * @param tick         game tick
     * @param playerId     issuing player
     * @param buildingType type of building to construct
     * @param position     grid position for placement
     */
    record Build(long tick, int playerId, BuildingType buildingType, GridPosition position) implements CommandType {
        public Build {
            if (buildingType == null) {
                throw new IllegalArgumentException("buildingType must not be null");
            }
            if (position == null) {
                throw new IllegalArgumentException("position must not be null");
            }
        }
    }

    /**
     * Produce a unit from a production building.
     *
     * @param tick        game tick
     * @param playerId    issuing player
     * @param producerId  entity ID of the producing building
     * @param unitType    type of unit to produce
     */
    record Produce(long tick, int playerId, int producerId, UnitType unitType) implements CommandType {
        public Produce {
            if (producerId < 0) {
                throw new IllegalArgumentException("producerId must not be negative, got: " + producerId);
            }
            if (unitType == null) {
                throw new IllegalArgumentException("unitType must not be null");
            }
        }
    }

    /**
     * Start a research project at a technology centre.
     *
     * @param tick          game tick
     * @param playerId      issuing player
     * @param techCentreId  entity ID of the technology centre
     * @param researchId    ID of the research to start (0-47)
     */
    record Research(long tick, int playerId, int techCentreId, int researchId) implements CommandType {
        public Research {
            if (techCentreId < 0) {
                throw new IllegalArgumentException("techCentreId must not be negative, got: " + techCentreId);
            }
            if (researchId < 0 || researchId > 47) {
                throw new IllegalArgumentException("researchId must be 0-47, got: " + researchId);
            }
        }
    }

    /**
     * Garrison one or more units inside a building.
     *
     * @param tick       game tick
     * @param playerId   issuing player
     * @param unitIds    entity IDs of units to garrison
     * @param buildingId entity ID of the target building
     */
    record Garrison(long tick, int playerId, int[] unitIds, int buildingId) implements CommandType {
        public Garrison {
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (buildingId < 0) {
                throw new IllegalArgumentException("buildingId must not be negative, got: " + buildingId);
            }
        }
    }

    /**
     * Ungarrison all units from a building.
     *
     * @param tick       game tick
     * @param playerId   issuing player
     * @param buildingId entity ID of the building to ungarrison from
     */
    record Ungarrison(long tick, int playerId, int buildingId) implements CommandType {
        public Ungarrison {
            if (buildingId < 0) {
                throw new IllegalArgumentException("buildingId must not be negative, got: " + buildingId);
            }
        }
    }

    /**
     * Cancel the current production or research of an entity.
     *
     * @param tick      game tick
     * @param playerId  issuing player
     * @param entityId  entity ID of the entity to cancel
     */
    record Cancel(long tick, int playerId, int entityId) implements CommandType {
        public Cancel {
            if (entityId < 0) {
                throw new IllegalArgumentException("entityId must not be negative, got: " + entityId);
            }
        }
    }

    /**
     * Toggle siege mode on a vehicle unit.
     * REF: unit_stats.md - siege mode for Fortress and Hammer
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitId   entity ID of the vehicle
     * @param enabled  true to enable siege mode, false to disable
     */
    record SiegeMode(long tick, int playerId, int unitId, boolean enabled) implements CommandType {
        public SiegeMode {
            if (unitId < 0) {
                throw new IllegalArgumentException("unitId must not be negative, got: " + unitId);
            }
        }
    }

    /**
     * Stop one or more units, cancelling their current orders.
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitIds  entity IDs of units to stop
     */
    record Stop(long tick, int playerId, int[] unitIds) implements CommandType {
        public Stop {
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
        }
    }

    /**
     * Set a patrol route for one or more units between their current position and a waypoint.
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitIds  entity IDs of units to patrol
     * @param waypoint patrol waypoint position
     */
    record Patrol(long tick, int playerId, int[] unitIds, GridPosition waypoint) implements CommandType {
        public Patrol {
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (waypoint == null) {
                throw new IllegalArgumentException("waypoint must not be null");
            }
        }
    }
}
