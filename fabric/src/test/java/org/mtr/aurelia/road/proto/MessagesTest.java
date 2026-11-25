/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.Direction;
import org.mtr.aurelia.road.proto.Message.Hello;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.HelloResult;
import org.mtr.aurelia.road.proto.Message.Limits;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.Redo;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.proto.Message.YourRoads;

/** The record codec of editor-protocol §2 and §4 (task F-3): round trips, size limits and fuzzing. */
class MessagesTest {
    private static final Digest HASH = digest(7);
    private static final UUID OPERATION = new UUID(0x1234_5678_9ABC_DEF0L, -42L);

    private static Digest digest(int seed) {
        byte[] bytes = new byte[Digest.LENGTH];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (seed * 31 + i * 7);
        }
        return Digest.of(bytes);
    }

    /** One or more samples of every record, with optional fields both present and absent, and edge values. */
    static List<Message> samples() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("node", "stratos.roads.apply");
        args.put("ünïcode", "ß·→ 道路");
        return List.of(
                new Hello(1, "0.1.0", "0.1.0", 3465, 1, List.of("roads"), Limits.NONE),
                new Hello(-5, "", "x".repeat(64), Integer.MAX_VALUE, 0, List.of(), new Limits(250_000, 1_000_000)),
                new HelloAck(1, "0.1.0", List.of("roads", "transfers")),
                new HelloAck(Integer.MIN_VALUE, "9.9.9", List.of()),
                new HelloResult(true, Optional.empty()),
                new HelloResult(false, Optional.of(Protocol.PROTOCOL_MISMATCH)),
                new PlanRequest(0, "roads", "STRAIGHT", new byte[] {1, 2, 3}, HASH),
                new PlanRequest(Integer.MAX_VALUE, "roads", "STRAIGHT", new byte[0], digest(1)),
                new PlanAccepted(5, OPERATION, HASH, 1234, 3),
                new PlanRejected(6, Protocol.PLAN_HASH_MISMATCH, Map.of(), Optional.of(HASH)),
                new PlanRejected(7, Protocol.PERMISSION_DENIED, args, Optional.empty()),
                new ApplyProgress(OPERATION, ApplyState.APPLYING, 2, 9, Optional.empty()),
                new ApplyProgress(OPERATION, ApplyState.FAILED, 9, 9, Optional.of("BLOCK_ENTITY")),
                new Undo(300),
                new Redo(0),
                new UndoRedoResult(8, Optional.of(OPERATION), true, Optional.empty(), 3),
                new UndoRedoResult(9, Optional.empty(), false, Optional.of("NOTHING_TO_UNDO"), 0),
                new YourRoads(List.of(new YourRoads.Road(OPERATION, "roads", "STRAIGHT", new byte[] {0, 1, 2}, false),
                        new YourRoads.Road(new UUID(-1, 1), "roads", "CURVE", new byte[0], true))),
                new YourRoads(List.of()),
                new Message.Palettes(List.of("{\"format\":1}", "é")),
                new Message.Palettes(List.of()),
                new Notice(Protocol.TRACK_NOT_BUILT, Map.of("count", "1", "total", "2")),
                new Notice(Protocol.TRACK_NOT_REMOVED, args),
                new Notice("X", Map.of()),
                new Message.TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()),
                new Message.TaskRequest(Integer.MAX_VALUE, Protocol.TASK_BAKE_ROAD, Optional.of(OPERATION)),
                new Message.TaskProgress(4, Protocol.STAGE_FINDING, 0, 0),
                new Message.TaskProgress(4, Protocol.STAGE_BAKING, 7, 9),
                new Message.TaskResult(4, true, Optional.empty(), Map.of("blocks", "120", "kept", "0")),
                new Message.TaskResult(5, false, Optional.of(Protocol.PERMISSION_DENIED), args),
                new Message.StratosBlocks(true),
                new Message.StratosBlocks(false));
    }

    @Test
    void everyRecordRoundTrips() {
        for (Message message : samples()) {
            byte[] bytes = Messages.encode(message);
            Message back = assertDoesNotThrowMalformed(bytes, message.direction());
            assertEquals(message, back, "round trip of " + message);
            assertArrayEquals(bytes, Messages.encode(back), "re-encoding " + message);
        }
    }

    @Test
    void theCatalogueCoversIdsZeroToNineAndSeventeenToTwentyThreeInTheirDirections() {
        Map<Integer, Direction> seen = new LinkedHashMap<>();
        for (Message message : samples()) {
            seen.put(message.id(), message.direction());
            assertEquals(message.id(), Messages.encode(message)[0], "the first byte is the id");
        }
        assertEquals(17, seen.size());
        for (int id : new int[] {1, 3, 7, 8, 20}) {
            assertEquals(Direction.TO_SERVER, seen.get(id), "id " + id);
        }
        for (int id : new int[] {0, 2, 4, 5, 6, 9, 17, 18, 19, 21, 22, 23}) {
            assertEquals(Direction.TO_CLIENT, seen.get(id), "id " + id);
        }
    }

    @Test
    void everyTruncationOfEveryRecordIsMalformed() {
        for (Message message : samples()) {
            byte[] bytes = Messages.encode(message);
            for (int length = 0; length < bytes.length; length++) {
                byte[] cut = Arrays.copyOf(bytes, length);
                assertThrows(MalformedMessage.class, () -> Messages.decode(cut, message.direction()), message + " cut to " + length + " bytes");
            }
        }
    }

    @Test
    void trailingBytesAreMalformed() {
        for (Message message : samples()) {
            byte[] bytes = Messages.encode(message);
            byte[] longer = Arrays.copyOf(bytes, bytes.length + 1);
            assertThrows(MalformedMessage.class, () -> Messages.decode(longer, message.direction()), message + " plus a byte");
        }
    }

    @Test
    void aRecordArrivingTheWrongWayIsMalformed() {
        for (Message message : samples()) {
            Direction wrong = message.direction() == Direction.TO_SERVER ? Direction.TO_CLIENT : Direction.TO_SERVER;
            assertThrows(MalformedMessage.class, () -> Messages.decode(Messages.encode(message), wrong), message.toString());
        }
    }

    @Test
    void unknownIdsAndEnumValuesAreMalformed() {
        for (int id : new int[] {10, 11, 16, 25, 127, -1}) {
            byte[] bytes = new ProtoWriter().varInt(id).varInt(0).toByteArray();
            assertThrows(MalformedMessage.class, () -> Messages.decode(bytes, Direction.TO_SERVER), "id " + id);
            assertThrows(MalformedMessage.class, () -> Messages.decode(bytes, Direction.TO_CLIENT), "id " + id);
        }
        // ApplyProgress with state 5 (there are five states, 0 to 4).
        byte[] progress = new ProtoWriter().varInt(6).uuid(OPERATION).varInt(5).varInt(0).varInt(0).bool(false).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(progress, Direction.TO_CLIENT));
        byte[] fine = new ProtoWriter().varInt(6).uuid(OPERATION).varInt(4).varInt(0).varInt(0).bool(false).toByteArray();
        assertDoesNotThrowMalformed(fine, Direction.TO_CLIENT);
    }

    @Test
    void oversizeStringsAndListsAreMalformed() {
        // modVersion of 65 bytes.
        byte[] longName = new ProtoWriter().varInt(1).varInt(1).string("v".repeat(65), 100).varInt(0).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(longName, Direction.TO_SERVER));
        // 33 capabilities.
        ProtoWriter many = new ProtoWriter().varInt(1).varInt(1).string("0.1.0", 64).varInt(33);
        for (int i = 0; i < 33; i++) {
            many.string("c" + i, 64);
        }
        assertThrows(MalformedMessage.class, () -> Messages.decode(many.toByteArray(), Direction.TO_SERVER));
        // A string claiming more bytes than the message has.
        byte[] lying = new ProtoWriter().varInt(1).varInt(1).varInt(60).raw(new byte[] {'a', 'b'}).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(lying, Direction.TO_SERVER));
        // 17 reason arguments.
        ProtoWriter args = new ProtoWriter().varInt(5).varInt(1).string("X", 64).varInt(17);
        for (int i = 0; i < 17; i++) {
            args.string("k" + i, 64).string("v", 256);
        }
        args.bool(false);
        assertThrows(MalformedMessage.class, () -> Messages.decode(args.toByteArray(), Direction.TO_CLIENT));
        // 401 roads in YourRoads (F-5).
        ProtoWriter roads = new ProtoWriter().varInt(18).varInt(401);
        for (int i = 0; i < 401; i++) {
            roads.uuid(OPERATION).string("roads", 64).string("STRAIGHT", 64).bytes(new byte[0], 30_720).bool(false);
        }
        assertThrows(MalformedMessage.class, () -> Messages.decode(roads.toByteArray(), Direction.TO_CLIENT));
        assertThrows(IllegalArgumentException.class, () -> new YourRoads(Collections.nCopies(401,
                new YourRoads.Road(OPERATION, "roads", "STRAIGHT", new byte[0], false))));
        // The same key twice.
        byte[] twice = new ProtoWriter().varInt(5).varInt(1).string("X", 64).varInt(2).string("k", 64).string("a", 256).string("k", 64)
                .string("b", 256).bool(false).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(twice, Direction.TO_CLIENT));
    }

    @Test
    void badPrimitivesAreMalformed() {
        // A VarInt of six bytes.
        byte[] longVarInt = {7, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0};
        assertThrows(MalformedMessage.class, () -> Messages.decode(longVarInt, Direction.TO_SERVER));
        // A negative request id (a five-byte VarInt).
        byte[] negative = new ProtoWriter().varInt(7).varInt(-1).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(negative, Direction.TO_SERVER));
        // A boolean that is 2.
        byte[] bool = {2, 2, 0};
        assertThrows(MalformedMessage.class, () -> Messages.decode(bool, Direction.TO_CLIENT));
        // Invalid UTF-8 in a string.
        byte[] utf8 = new ProtoWriter().varInt(1).varInt(1).varInt(2).raw(new byte[] {(byte) 0xC3, (byte) 0x28}).varInt(0).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(utf8, Direction.TO_SERVER));
        // sectionsDone above sectionsTotal: each field is fine, together they aren't.
        byte[] progress = new ProtoWriter().varInt(6).uuid(OPERATION).varInt(1).varInt(3).varInt(2).bool(false).toByteArray();
        assertThrows(MalformedMessage.class, () -> Messages.decode(progress, Direction.TO_CLIENT));
        // An empty message has no id.
        assertThrows(MalformedMessage.class, () -> Messages.decode(new byte[0], Direction.TO_SERVER));
    }

    @Test
    void theSizeLimitsHoldBothWays() {
        // Decoding refuses anything over the limit before reading it.
        byte[] tooBigForServer = new byte[Protocol.MAX_TO_SERVER_BYTES + 1];
        assertThrows(MalformedMessage.class, () -> Messages.decode(tooBigForServer, Direction.TO_SERVER));
        byte[] tooBigForClient = new byte[Protocol.MAX_TO_CLIENT_BYTES + 1];
        assertThrows(MalformedMessage.class, () -> Messages.decode(tooBigForClient, Direction.TO_CLIENT));

        // A request with parameters that just fit, and one byte more.
        int overhead = Messages.encode(new PlanRequest(1, "roads", "STRAIGHT", new byte[0], HASH)).length;
        int room = Protocol.MAX_TO_SERVER_BYTES - overhead - 2; // the params length grows from a 1- to a 3-byte VarInt
        PlanRequest fits = new PlanRequest(1, "roads", "STRAIGHT", new byte[room], HASH);
        assertEquals(Protocol.MAX_TO_SERVER_BYTES, Messages.encode(fits).length);
        assertEquals(fits, assertDoesNotThrowMalformed(Messages.encode(fits), Direction.TO_SERVER));
        PlanRequest over = new PlanRequest(1, "roads", "STRAIGHT", new byte[room + 1], HASH);
        assertThrows(IllegalArgumentException.class, () -> Messages.encode(over), "Stratos never sends what it would refuse");
    }

    @Test
    void recordsRefuseFieldsTheDecoderWouldRefuse() {
        assertThrows(IllegalArgumentException.class, () -> new Undo(-1));
        assertThrows(IllegalArgumentException.class, () -> new HelloAck(1, "v".repeat(65), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new HelloAck(1, "0.1.0", Collections.nCopies(33, "c")));
        assertThrows(IllegalArgumentException.class, () -> new ApplyProgress(OPERATION, ApplyState.DONE, 4, 3, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> Digest.of(new byte[31]));
        Map<String, String> tooLong = Map.of("k", "v".repeat(257));
        assertThrows(IllegalArgumentException.class, () -> new PlanRejected(1, "X", tooLong, Optional.empty()));
    }

    /** Random bytes, and valid records with random bytes changed: decoding returns a record or throws MalformedMessage. */
    @Test
    void fuzzedInputOnlyEverThrowsMalformedMessage() {
        Random random = new Random(20261005);
        List<byte[]> inputs = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            byte[] bytes = new byte[random.nextInt(80)];
            random.nextBytes(bytes);
            if (bytes.length > 0) {
                bytes[0] = (byte) random.nextInt(12); // mostly real ids, so the fields get exercised
            }
            inputs.add(bytes);
        }
        List<Message> samples = samples();
        for (int i = 0; i < 50_000; i++) {
            byte[] bytes = Messages.encode(samples.get(random.nextInt(samples.size())));
            int changes = 1 + random.nextInt(3);
            for (int c = 0; c < changes; c++) {
                bytes[random.nextInt(bytes.length)] = (byte) random.nextInt(256);
            }
            inputs.add(bytes);
        }
        int decoded = 0;
        for (byte[] bytes : inputs) {
            for (Direction direction : Direction.values()) {
                try {
                    Message message = Messages.decode(bytes, direction);
                    assertArrayEquals(bytes, Messages.encode(message), "a record that decodes re-encodes to the same bytes");
                    decoded++;
                } catch (MalformedMessage expected) {
                    // the only allowed failure
                } catch (RuntimeException e) {
                    fail("decoding " + Arrays.toString(bytes) + " threw " + e, e);
                }
            }
        }
        assertTrue(decoded > 1000, "the fuzzer also produced valid records (" + decoded + ")");
    }

    private static Message assertDoesNotThrowMalformed(byte[] bytes, Direction direction) {
        try {
            return Messages.decode(bytes, direction);
        } catch (MalformedMessage e) {
            throw new AssertionError("unexpectedly malformed: " + e.getMessage(), e);
        }
    }
}
