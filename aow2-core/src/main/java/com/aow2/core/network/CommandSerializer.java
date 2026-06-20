package com.aow2.core.network;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.CommandType;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitType;

import java.nio.ByteBuffer;

/**
 * Serializes and deserializes CommandType records to/from byte arrays.
 * Uses a compact binary format optimized for network transmission in lockstep multiplayer.
 * REF: protocol_specification.md - Wire format (Section 2)
 * REF: protocol_specification.md - Record format with type byte prefix
 * REF: multiplayer_architecture.md - Sender/receiver thread architecture
 */
public final class CommandSerializer {

    /** Command type IDs matching the wire protocol */
    private static final byte TYPE_MOVE = 0x01;
    private static final byte TYPE_ATTACK = 0x02;
    private static final byte TYPE_BUILD = 0x03;
    private static final byte TYPE_PRODUCE = 0x04;
    private static final byte TYPE_RESEARCH = 0x05;
    private static final byte TYPE_GARRISON = 0x06;
    private static final byte TYPE_UNGARRISON = 0x07;
    private static final byte TYPE_CANCEL = 0x08;
    private static final byte TYPE_SIEGE_MODE = 0x09;
    private static final byte TYPE_STOP = 0x0A;
    private static final byte TYPE_PATROL = 0x0B;
    private static final byte TYPE_ATTACK_MOVE = 0x0C;

    private CommandSerializer() {
        // Utility class, no instantiation
    }

    /**
     * Serializes a CommandType to a byte array.
     * Format: [typeId:1][tick:8][playerId:4][payload:variable]
     *
     * @param command the command to serialize
     * @return the serialized byte array
     */
    public static byte[] serialize(CommandType command) {
        return switch (command) {
            case CommandType.Move m -> serializeMove(m);
            case CommandType.Attack a -> serializeAttack(a);
            case CommandType.Build b -> serializeBuild(b);
            case CommandType.Produce p -> serializeProduce(p);
            case CommandType.Research r -> serializeResearch(r);
            case CommandType.Garrison g -> serializeGarrison(g);
            case CommandType.Ungarrison u -> serializeUngarrison(u);
            case CommandType.Cancel c -> serializeCancel(c);
            case CommandType.SiegeMode s -> serializeSiegeMode(s);
            case CommandType.Stop st -> serializeStop(st);
            case CommandType.Patrol pt -> serializePatrol(pt);
            case CommandType.AttackMove am -> serializeAttackMove(am);
        };
    }

    /**
     * Deserializes a CommandType from a byte array.
     *
     * @param data the serialized byte array
     * @return the deserialized CommandType
     * @throws IllegalArgumentException if the data is malformed
     */
    public static CommandType deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte typeId = buf.get();
        long tick = buf.getLong();
        int playerId = buf.getInt();

        return switch (typeId) {
            case TYPE_MOVE -> deserializeMove(buf, tick, playerId);
            case TYPE_ATTACK -> deserializeAttack(buf, tick, playerId);
            case TYPE_BUILD -> deserializeBuild(buf, tick, playerId);
            case TYPE_PRODUCE -> deserializeProduce(buf, tick, playerId);
            case TYPE_RESEARCH -> deserializeResearch(buf, tick, playerId);
            case TYPE_GARRISON -> deserializeGarrison(buf, tick, playerId);
            case TYPE_UNGARRISON -> deserializeUngarrison(buf, tick, playerId);
            case TYPE_CANCEL -> deserializeCancel(buf, tick, playerId);
            case TYPE_SIEGE_MODE -> deserializeSiegeMode(buf, tick, playerId);
            case TYPE_STOP -> deserializeStop(buf, tick, playerId);
            case TYPE_PATROL -> deserializePatrol(buf, tick, playerId);
            case TYPE_ATTACK_MOVE -> deserializeAttackMove(buf, tick, playerId);
            default -> throw new IllegalArgumentException("Unknown command type ID: " + typeId);
        };
    }

    // --- Serialization helpers ---

    private static byte[] serializeMove(CommandType.Move m) {
        int unitIdBytes = 4 + m.unitIds().length * 4;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + unitIdBytes + 8);
        buf.put(TYPE_MOVE);
        buf.putLong(m.tick());
        buf.putInt(m.playerId());
        buf.putInt(m.unitIds().length);
        for (int id : m.unitIds()) buf.putInt(id);
        buf.putInt(m.target().x());
        buf.putInt(m.target().y());
        return buf.array();
    }

    private static byte[] serializeAttack(CommandType.Attack a) {
        int unitIdBytes = 4 + a.unitIds().length * 4;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + unitIdBytes + 4);
        buf.put(TYPE_ATTACK);
        buf.putLong(a.tick());
        buf.putInt(a.playerId());
        buf.putInt(a.unitIds().length);
        for (int id : a.unitIds()) buf.putInt(id);
        buf.putInt(a.targetId());
        return buf.array();
    }

    private static byte[] serializeBuild(CommandType.Build b) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 4 + 8);
        buf.put(TYPE_BUILD);
        buf.putLong(b.tick());
        buf.putInt(b.playerId());
        buf.putInt(b.buildingType().ordinal());
        buf.putInt(b.position().x());
        buf.putInt(b.position().y());
        return buf.array();
    }

    private static byte[] serializeProduce(CommandType.Produce p) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 4 + 4);
        buf.put(TYPE_PRODUCE);
        buf.putLong(p.tick());
        buf.putInt(p.playerId());
        buf.putInt(p.producerId());
        buf.putInt(p.unitType().ordinal());
        return buf.array();
    }

    private static byte[] serializeResearch(CommandType.Research r) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 4 + 4);
        buf.put(TYPE_RESEARCH);
        buf.putLong(r.tick());
        buf.putInt(r.playerId());
        buf.putInt(r.techCentreId());
        buf.putInt(r.researchId());
        return buf.array();
    }

    private static byte[] serializeGarrison(CommandType.Garrison g) {
        int unitIdBytes = 4 + g.unitIds().length * 4;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + unitIdBytes + 4);
        buf.put(TYPE_GARRISON);
        buf.putLong(g.tick());
        buf.putInt(g.playerId());
        buf.putInt(g.unitIds().length);
        for (int id : g.unitIds()) buf.putInt(id);
        buf.putInt(g.buildingId());
        return buf.array();
    }

    private static byte[] serializeUngarrison(CommandType.Ungarrison u) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 4);
        buf.put(TYPE_UNGARRISON);
        buf.putLong(u.tick());
        buf.putInt(u.playerId());
        buf.putInt(u.buildingId());
        return buf.array();
    }

    private static byte[] serializeCancel(CommandType.Cancel c) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 4);
        buf.put(TYPE_CANCEL);
        buf.putLong(c.tick());
        buf.putInt(c.playerId());
        buf.putInt(c.entityId());
        return buf.array();
    }

    private static byte[] serializeSiegeMode(CommandType.SiegeMode s) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 4 + 1);
        buf.put(TYPE_SIEGE_MODE);
        buf.putLong(s.tick());
        buf.putInt(s.playerId());
        buf.putInt(s.unitId());
        buf.put((byte) (s.enabled() ? 1 : 0));
        return buf.array();
    }

    private static byte[] serializeStop(CommandType.Stop st) {
        int unitIdBytes = 4 + st.unitIds().length * 4;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + unitIdBytes);
        buf.put(TYPE_STOP);
        buf.putLong(st.tick());
        buf.putInt(st.playerId());
        buf.putInt(st.unitIds().length);
        for (int id : st.unitIds()) buf.putInt(id);
        return buf.array();
    }

    private static byte[] serializePatrol(CommandType.Patrol pt) {
        int unitIdBytes = 4 + pt.unitIds().length * 4;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + unitIdBytes + 8);
        buf.put(TYPE_PATROL);
        buf.putLong(pt.tick());
        buf.putInt(pt.playerId());
        buf.putInt(pt.unitIds().length);
        for (int id : pt.unitIds()) buf.putInt(id);
        buf.putInt(pt.waypoint().x());
        buf.putInt(pt.waypoint().y());
        return buf.array();
    }

    private static byte[] serializeAttackMove(CommandType.AttackMove am) {
        int unitIdBytes = 4 + am.unitIds().length * 4;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + unitIdBytes + 8);
        buf.put(TYPE_ATTACK_MOVE);
        buf.putLong(am.tick());
        buf.putInt(am.playerId());
        buf.putInt(am.unitIds().length);
        for (int id : am.unitIds()) buf.putInt(id);
        buf.putInt(am.target().x());
        buf.putInt(am.target().y());
        return buf.array();
    }

    // --- Deserialization helpers ---

    private static CommandType.Move deserializeMove(ByteBuffer buf, long tick, int playerId) {
        int count = buf.getInt();
        int[] unitIds = new int[count];
        for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
        int x = buf.getInt();
        int y = buf.getInt();
        return new CommandType.Move(tick, playerId, unitIds, new GridPosition(x, y));
    }

    private static CommandType.Attack deserializeAttack(ByteBuffer buf, long tick, int playerId) {
        int count = buf.getInt();
        int[] unitIds = new int[count];
        for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
        int targetId = buf.getInt();
        return new CommandType.Attack(tick, playerId, unitIds, targetId);
    }

    private static CommandType.Build deserializeBuild(ByteBuffer buf, long tick, int playerId) {
        int buildingOrdinal = buf.getInt();
        int x = buf.getInt();
        int y = buf.getInt();
        return new CommandType.Build(tick, playerId,
                BuildingType.values()[buildingOrdinal], new GridPosition(x, y));
    }

    private static CommandType.Produce deserializeProduce(ByteBuffer buf, long tick, int playerId) {
        int producerId = buf.getInt();
        int unitOrdinal = buf.getInt();
        return new CommandType.Produce(tick, playerId, producerId, UnitType.values()[unitOrdinal]);
    }

    private static CommandType.Research deserializeResearch(ByteBuffer buf, long tick, int playerId) {
        int techCentreId = buf.getInt();
        int researchId = buf.getInt();
        return new CommandType.Research(tick, playerId, techCentreId, researchId);
    }

    private static CommandType.Garrison deserializeGarrison(ByteBuffer buf, long tick, int playerId) {
        int count = buf.getInt();
        int[] unitIds = new int[count];
        for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
        int buildingId = buf.getInt();
        return new CommandType.Garrison(tick, playerId, unitIds, buildingId);
    }

    private static CommandType.Ungarrison deserializeUngarrison(ByteBuffer buf, long tick, int playerId) {
        int buildingId = buf.getInt();
        return new CommandType.Ungarrison(tick, playerId, buildingId);
    }

    private static CommandType.Cancel deserializeCancel(ByteBuffer buf, long tick, int playerId) {
        int entityId = buf.getInt();
        return new CommandType.Cancel(tick, playerId, entityId);
    }

    private static CommandType.SiegeMode deserializeSiegeMode(ByteBuffer buf, long tick, int playerId) {
        int unitId = buf.getInt();
        boolean enabled = buf.get() != 0;
        return new CommandType.SiegeMode(tick, playerId, unitId, enabled);
    }

    private static CommandType.Stop deserializeStop(ByteBuffer buf, long tick, int playerId) {
        int count = buf.getInt();
        int[] unitIds = new int[count];
        for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
        return new CommandType.Stop(tick, playerId, unitIds);
    }

    private static CommandType.AttackMove deserializeAttackMove(ByteBuffer buf, long tick, int playerId) {
        int count = buf.getInt();
        int[] unitIds = new int[count];
        for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
        int x = buf.getInt();
        int y = buf.getInt();
        return new CommandType.AttackMove(tick, playerId, unitIds, new GridPosition(x, y));
    }

    private static CommandType.Patrol deserializePatrol(ByteBuffer buf, long tick, int playerId) {
        int count = buf.getInt();
        int[] unitIds = new int[count];
        for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
        int x = buf.getInt();
        int y = buf.getInt();
        return new CommandType.Patrol(tick, playerId, unitIds, new GridPosition(x, y));
    }
}
