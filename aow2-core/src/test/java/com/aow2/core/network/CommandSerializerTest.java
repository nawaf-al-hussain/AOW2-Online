package com.aow2.core.network;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.CommandType;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the CommandSerializer binary serialization/deserialization.
 * Verifies round-trip integrity for all 11 CommandType variants.
 * REF: protocol_specification.md - Wire format (Section 2)
 */
class CommandSerializerTest {

    @Test
    @DisplayName("Move command round-trip serialization")
    void moveRoundTrip() {
        var original = new CommandType.Move(100, 0, new int[]{1, 2, 3}, new GridPosition(10, 20));
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Move) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertArrayEquals(original.unitIds(), restored.unitIds());
        assertEquals(original.target(), restored.target());
    }

    @Test
    @DisplayName("Attack command round-trip serialization")
    void attackRoundTrip() {
        var original = new CommandType.Attack(200, 1, new int[]{5, 6}, 99);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Attack) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertArrayEquals(original.unitIds(), restored.unitIds());
        assertEquals(original.targetId(), restored.targetId());
    }

    @Test
    @DisplayName("Build command round-trip serialization")
    void buildRoundTrip() {
        var original = new CommandType.Build(300, 0, BuildingType.REBEL_BARRACKS, new GridPosition(15, 25));
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Build) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(original.buildingType(), restored.buildingType());
        assertEquals(original.position(), restored.position());
    }

    @Test
    @DisplayName("Produce command round-trip serialization")
    void produceRoundTrip() {
        var original = new CommandType.Produce(400, 1, 42, UnitType.CONFED_INFANTRY);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Produce) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(original.producerId(), restored.producerId());
        assertEquals(original.unitType(), restored.unitType());
    }

    @Test
    @DisplayName("Research command round-trip serialization")
    void researchRoundTrip() {
        var original = new CommandType.Research(500, 0, 10, 5);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Research) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(original.techCentreId(), restored.techCentreId());
        assertEquals(original.researchId(), restored.researchId());
    }

    @Test
    @DisplayName("Garrison command round-trip serialization")
    void garrisonRoundTrip() {
        var original = new CommandType.Garrison(600, 1, new int[]{10, 20}, 30);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Garrison) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertArrayEquals(original.unitIds(), restored.unitIds());
        assertEquals(original.buildingId(), restored.buildingId());
    }

    @Test
    @DisplayName("Ungarrison command round-trip serialization")
    void ungarrisonRoundTrip() {
        var original = new CommandType.Ungarrison(700, 0, 15);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Ungarrison) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(original.buildingId(), restored.buildingId());
    }

    @Test
    @DisplayName("Cancel command round-trip serialization")
    void cancelRoundTrip() {
        var original = new CommandType.Cancel(800, 1, 77);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Cancel) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(original.entityId(), restored.entityId());
    }

    @Test
    @DisplayName("SiegeMode command round-trip serialization (enabled)")
    void siegeModeEnabledRoundTrip() {
        var original = new CommandType.SiegeMode(900, 0, 55, true);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.SiegeMode) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(original.unitId(), restored.unitId());
        assertEquals(original.enabled(), restored.enabled());
    }

    @Test
    @DisplayName("SiegeMode command round-trip serialization (disabled)")
    void siegeModeDisabledRoundTrip() {
        var original = new CommandType.SiegeMode(950, 1, 55, false);
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.SiegeMode) deserialized;
        assertEquals(original.enabled(), restored.enabled());
    }

    @Test
    @DisplayName("Stop command round-trip serialization")
    void stopRoundTrip() {
        var original = new CommandType.Stop(1000, 0, new int[]{3, 4, 5});
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Stop) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertArrayEquals(original.unitIds(), restored.unitIds());
    }

    @Test
    @DisplayName("Patrol command round-trip serialization")
    void patrolRoundTrip() {
        var original = new CommandType.Patrol(1100, 1, new int[]{8}, new GridPosition(25, 35));
        byte[] data = CommandSerializer.serialize(original);
        CommandType deserialized = CommandSerializer.deserialize(data);

        var restored = (CommandType.Patrol) deserialized;
        assertEquals(original.tick(), restored.tick());
        assertEquals(original.playerId(), restored.playerId());
        assertArrayEquals(original.unitIds(), restored.unitIds());
        assertEquals(original.waypoint(), restored.waypoint());
    }

    @Test
    @DisplayName("Deserialization rejects unknown type ID")
    void rejectUnknownType() {
        byte[] invalid = new byte[13];
        invalid[0] = 0x7F; // unknown type
        assertThrows(IllegalArgumentException.class, () -> CommandSerializer.deserialize(invalid));
    }
}
