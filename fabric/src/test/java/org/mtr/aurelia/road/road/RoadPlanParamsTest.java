/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.proto.MalformedMessage;
import org.mtr.aurelia.road.proto.ProtoWriter;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanParams.Junction;
import org.mtr.aurelia.road.road.RoadPlanParams.Vertical;

/** The roads module's parameter record and its encoding (editor-protocol §4, task F-3). */
class RoadPlanParamsTest {
    static final RoadPlanParams SIMPLE = RoadPlanParams.straight("city-17", End.at(10, 64, -20, Heading.E), End.at(30, 64, -20, Heading.E));

    /** Every optional field present, negative and extreme coordinates, every enum away from its first value. */
    static final RoadPlanParams FULL = new RoadPlanParams(RoadPlanParams.Tool.JUNCTION, "city-17", Presets.Preset.UK_FAITHFUL, "brick",
            RoadPlanParams.DrivingSide.RHT,
            List.of(new End(-RoadPlanParams.MAX_XZ, RoadPlanParams.MIN_Y, RoadPlanParams.MAX_XZ, Heading.ENE, Optional.of(new UUID(1, 2)), OptionalInt.of(3)),
                    new End(0, RoadPlanParams.MAX_Y, -1, Heading.SSE, Optional.empty(), OptionalInt.empty()),
                    End.at(5, 70, 5, Heading.N)),
            new Vertical(Vertical.Shape.TWO_RADII, OptionalDouble.of(123.25)), RoadPlanParams.NodeAids.PREORIENTED, true,
            Optional.of(new Junction("tee", 2, List.of("city-17", "city-17", "residential-14"))), Optional.of(new UUID(-3, 4)),
            Optional.of(RoadPlanParams.ManualEdits.KEEP), new RoadPlanParams.Dashes(-12.375, RoadPlanParams.Dashes.Anchor.END), List.of());

    private static RoadPlanParams decode(byte[] bytes) {
        try {
            return RoadPlanParams.decode(bytes);
        } catch (MalformedMessage e) {
            throw new AssertionError("unexpectedly malformed: " + e.getMessage(), e);
        }
    }

    @Test
    void roundTripsWithOptionalFieldsPresentAndAbsent() {
        for (RoadPlanParams params : List.of(SIMPLE, FULL)) {
            byte[] bytes = params.encode();
            assertEquals(params, decode(bytes));
            assertArrayEquals(bytes, decode(bytes).encode());
        }
    }

    @Test
    void everyTruncationIsMalformed() {
        for (RoadPlanParams params : List.of(SIMPLE, FULL)) {
            byte[] bytes = params.encode();
            for (int length = 0; length < bytes.length; length++) {
                byte[] cut = Arrays.copyOf(bytes, length);
                assertThrows(MalformedMessage.class, () -> RoadPlanParams.decode(cut), "cut to " + length);
            }
            byte[] longer = Arrays.copyOf(bytes, bytes.length + 1);
            assertThrows(MalformedMessage.class, () -> RoadPlanParams.decode(longer), "a byte too many");
        }
    }

    /** The start of a straight road's encoding, up to its ends. */
    private static ProtoWriter header(int endCount) {
        return new ProtoWriter().varInt(0).string("city-17", 64).varInt(0).string("vanilla", 64).varInt(0).varInt(endCount);
    }

    private static ProtoWriter tail(ProtoWriter out) {
        return out.varInt(0).bool(false).varInt(2).bool(false).bool(false).bool(false).bool(false).bool(false).varInt(0); // varInt(0): no crossings (#193)
    }

    private static ProtoWriter end(ProtoWriter out, int x, int y, int z, int heading) {
        return out.varInt(x).varInt(y).varInt(z).varInt(heading).bool(false).bool(false);
    }

    @Test
    void outOfRangeFieldsAreMalformed() {
        // The hand-written encoding is right when nothing is out of range...
        byte[] good = tail(end(end(header(2), 0, 64, 0, 0), 20, 64, 0, 0)).toByteArray();
        assertEquals(RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E)), decode(good));
        // ...and each of these is malformed.
        List<byte[]> bad = List.of(
                tail(end(end(header(2), 0, 64, 0, 16), 20, 64, 0, 0)).toByteArray(), // heading 16
                tail(end(end(header(2), 0, 64, 0, -1), 20, 64, 0, 0)).toByteArray(), // heading -1
                tail(end(end(header(2), RoadPlanParams.MAX_XZ + 1, 64, 0, 0), 20, 64, 0, 0)).toByteArray(), // beyond any border
                tail(end(end(header(2), 0, 64, -RoadPlanParams.MAX_XZ - 1, 0), 20, 64, 0, 0)).toByteArray(),
                tail(end(end(header(2), 0, RoadPlanParams.MAX_Y + 1, 0, 0), 20, 64, 0, 0)).toByteArray(), // above any world
                tail(end(end(header(2), 0, RoadPlanParams.MIN_Y - 1, 0, 0), 20, 64, 0, 0)).toByteArray(),
                tail(end(header(1), 0, 64, 0, 0)).toByteArray(), // a straight road with one end
                tail(end(end(end(header(3), 0, 64, 0, 0), 20, 64, 0, 0), 40, 64, 0, 0)).toByteArray(), // or three
                tail(header(17)).toByteArray(), // more than 16 ends
                new ProtoWriter().varInt(7).toByteArray(), // tool 7 doesn't exist
                end(end(header(2), 0, 64, 0, 0), 20, 64, 0, 0).varInt(0).bool(true).doubleValue(Double.NaN).varInt(2).bool(false).bool(false)
                        .bool(false).bool(false).toByteArray(), // a NaN radius
                end(end(header(2), 0, 64, 0, 0), 20, 64, 0, 0).varInt(0).bool(true).doubleValue(-5).varInt(2).bool(false).bool(false)
                        .bool(false).bool(false).toByteArray(), // a negative radius
                new ProtoWriter().varInt(0).string("p".repeat(65), 100).toByteArray()); // a profile id over 64 bytes
        for (int i = 0; i < bad.size(); i++) {
            byte[] bytes = bad.get(i);
            assertThrows(MalformedMessage.class, () -> RoadPlanParams.decode(bytes), "bad input " + i);
        }
    }

    @Test
    void theModuleRefusesParametersForAnotherTool() throws MalformedMessage {
        byte[] bytes = SIMPLE.encode();
        assertEquals(SIMPLE, RoadsModule.INSTANCE.decode("STRAIGHT", bytes));
        assertThrows(MalformedMessage.class, () -> RoadsModule.INSTANCE.decode("CURVE", bytes));
        assertThrows(MalformedMessage.class, () -> RoadsModule.INSTANCE.decode("straight", bytes));
    }

    @Test
    void fuzzedInputOnlyEverThrowsMalformedMessage() {
        Random random = new Random(5);
        int decoded = 0;
        for (int i = 0; i < 100_000; i++) {
            byte[] bytes = (i % 2 == 0 ? SIMPLE : FULL).encode();
            if (i % 3 == 0) {
                bytes = new byte[random.nextInt(60)];
                random.nextBytes(bytes);
            } else {
                for (int c = 1 + random.nextInt(3); c > 0; c--) {
                    bytes[random.nextInt(bytes.length)] = (byte) random.nextInt(256);
                }
            }
            try {
                RoadPlanParams params = RoadPlanParams.decode(bytes);
                assertArrayEquals(bytes, params.encode());
                decoded++;
            } catch (MalformedMessage expected) {
                // the only allowed failure
            } catch (RuntimeException e) {
                fail("decoding " + Arrays.toString(bytes) + " threw " + e, e);
            }
        }
        assertTrue(decoded > 1000, "the fuzzer also produced valid parameters (" + decoded + ")");
    }
}
