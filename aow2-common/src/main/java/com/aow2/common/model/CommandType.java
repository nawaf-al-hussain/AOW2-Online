package com.aow2.common.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Sealed interface for all player commands in the game.
 * Used by lockstep networking and replay system to ensure deterministic simulation.
 * Each command records the tick at which it should be processed and the issuing player.
 * REF: protocol_specification.md - 34 multiplayer message types
 * REF: MASTER_DOCUMENTATION.md Section 3.3 - Lockstep networking
 *
 * FIX LOG:
 * - Added defensive copy (clone) for int[] unitIds in compact constructors
 * - Overrode equals() to use Arrays.equals() for unitIds comparison
 * - Overrode hashCode() to use Arrays.hashCode() for unitIds field
 */
public sealed interface CommandType permits
    CommandType.Move, CommandType.Attack, CommandType.AttackMove, CommandType.Build,
    CommandType.Produce, CommandType.Research, CommandType.Garrison,
    CommandType.Ungarrison, CommandType.Cancel, CommandType.SiegeMode,
    CommandType.Stop, CommandType.Patrol, CommandType.Upgrade {

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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (target == null) {
                throw new IllegalArgumentException("target must not be null");
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Move that)) return false;
            return tick == that.tick && playerId == that.playerId
                && Arrays.equals(unitIds, that.unitIds)
                && Objects.equals(target, that.target);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            result = 31 * result + Objects.hashCode(target);
            return result;
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (targetId < 0) {
                throw new IllegalArgumentException("targetId must not be negative, got: " + targetId);
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Attack that)) return false;
            return tick == that.tick && playerId == that.playerId
                && targetId == that.targetId
                && Arrays.equals(unitIds, that.unitIds);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            result = 31 * result + targetId;
            return result;
        }
    }

    /**
     * Order one or more units to move to a position, engaging any enemies encountered along the way.
     * Units will pursue and attack enemies within their sight range during the move,
     * then resume moving to the target position once no enemies remain.
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitIds  entity IDs of units to attack-move
     * @param target   target grid position to move toward
     */
    record AttackMove(long tick, int playerId, int[] unitIds, GridPosition target) implements CommandType {
        public AttackMove {
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (target == null) {
                throw new IllegalArgumentException("target must not be null");
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AttackMove that)) return false;
            return tick == that.tick && playerId == that.playerId
                && Arrays.equals(unitIds, that.unitIds)
                && Objects.equals(target, that.target);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            result = 31 * result + Objects.hashCode(target);
            return result;
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (buildingId < 0) {
                throw new IllegalArgumentException("buildingId must not be negative, got: " + buildingId);
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Garrison that)) return false;
            return tick == that.tick && playerId == that.playerId
                && buildingId == that.buildingId
                && Arrays.equals(unitIds, that.unitIds);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            result = 31 * result + buildingId;
            return result;
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Stop that)) return false;
            return tick == that.tick && playerId == that.playerId
                && Arrays.equals(unitIds, that.unitIds);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            return result;
        }
    }

    /**
     * Hold position command. Distinct from Stop: units clear their movement path
     * but retain their attack target, allowing them to attack enemies in range
     * without moving. Stop clears both path and attack target.
     * <p>
     * FIX (F-11): Previously H key issued CommandType.Stop, making hold functionally
     * identical to stop. Added this Hold record so the two commands are distinct.
     *
     * @param tick     game tick
     * @param playerId issuing player
     * @param unitIds  entity IDs of units to hold position
     */
    record Hold(long tick, int playerId, int[] unitIds) implements CommandType {
        public Hold {
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Hold that)) return false;
            return tick == that.tick && playerId == that.playerId
                && Arrays.equals(unitIds, that.unitIds);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            return result;
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
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (unitIds == null || unitIds.length == 0) {
                throw new IllegalArgumentException("unitIds must not be null or empty");
            }
            if (waypoint == null) {
                throw new IllegalArgumentException("waypoint must not be null");
            }
            unitIds = unitIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Patrol that)) return false;
            return tick == that.tick && playerId == that.playerId
                && Arrays.equals(unitIds, that.unitIds)
                && Objects.equals(waypoint, that.waypoint);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(tick);
            result = 31 * result + playerId;
            result = 31 * result + Arrays.hashCode(unitIds);
            result = 31 * result + Objects.hashCode(waypoint);
            return result;
        }
    }

    /**
     * Upgrade a building to the next level.
     * <p>
     * Deducts the upgrade cost from the player's credits and increments the
     * building's upgrade level. Upgrade levels provide:
     * <ul>
     *   <li>Generators: increased power radius (10→20→30→40→60→127)</li>
     *   <li>Command Centres: increased income per cycle</li>
     *   <li>All buildings: increased max HP</li>
     * </ul>
     * REF: complete_building_stats.json — upgradeCosts per building type
     * REF: GameConstants.BUILDING_POWER_RADIUS — power radius per upgrade level
     *
     * @param tick       the game tick when this command was issued
     * @param playerId   the issuing player's ID (0 or 1)
     * @param buildingId the entity ID of the building to upgrade
     */
    record Upgrade(long tick, int playerId, int buildingId) implements CommandType {
        // FIX (F-23): Added input validation — all other command types validate
        // tick >= 0 and ID fields in their compact constructors. Upgrade was the
        // only one missing validation, allowing negative tick/buildingId to pass
        // construction and fail later with unclear errors.
        public Upgrade {
            if (tick < 0) {
                throw new IllegalArgumentException("tick must not be negative, got: " + tick);
            }
            if (playerId < 0) {
                throw new IllegalArgumentException("playerId must not be negative, got: " + playerId);
            }
            if (buildingId < 0) {
                throw new IllegalArgumentException("buildingId must not be negative, got: " + buildingId);
            }
        }
    }
}
