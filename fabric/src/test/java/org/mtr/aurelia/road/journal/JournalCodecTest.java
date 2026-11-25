/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;

/** The journal's records and segments (F-2+; world-data-format §5). */
class JournalCodecTest {
    static final UUID RUN = new UUID(3, 1);
    static final SideEffect RAIL = new SideEffect.TrackRail(1, 65, -3, 2, 40, 65, 36, 4, 40, 0, SideEffect.TrackRail.Shape.TWO_RADII, 120.5);

    static List<JournalRecord> everyKind() {
        long[] bits = new long[64];
        bits[0] = 0b1011;
        bits[63] = Long.MIN_VALUE;
        return List.of(
                new JournalRecord.Begin(RUN, Operations.Kind.APPLY, new UUID(7, 1), "minecraft:overworld", "alice", "ab".repeat(32), 1_791_460_800_000L),
                new JournalRecord.Preimage(RUN, new BlockPlan.SectionPos(0, 4, -1), 3465, List.of(
                        new BlockChange(1, 64, -16, "minecraft:air", "minecraft:stone"),
                        new BlockChange(2, 64, -16, "minecraft:chest[facing=north]", "minecraft:stone", "{Items:[{id:\"minecraft:apple\",Count:3b}]}", null))),
                new JournalRecord.SectionDone(RUN, new BlockPlan.SectionPos(0, 4, -1), bits),
                new JournalRecord.End(RUN, JournalRecord.EndStatus.FAILED, "CONFLICT", 12),
                new JournalRecord.Track(RUN, JournalRecord.TrackAction.MAKE, List.of(RAIL)),
                new JournalRecord.TrackDone(RUN, JournalRecord.TrackAction.MAKE, List.of(RAIL), List.of(SideEffectWriter.Outcome.ALREADY_THERE)),
                new JournalRecord.Recovered(RUN, JournalRecord.Outcome.NEEDS_ATTENTION, "its dimension isn't in this world"),
                new JournalRecord.Retired(RUN));
    }

    static byte[] segment(List<JournalRecord> records) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        records.forEach(record -> bytes.writeBytes(JournalCodec.encode(record)));
        return bytes.toByteArray();
    }

    @Test
    void everyRecordReadsBackAsItWasWritten() {
        JournalCodec.Segment read = JournalCodec.read(segment(everyKind()));
        assertEquals(JournalCodec.Ending.CLEAN, read.ending());
        assertEquals(everyKind(), read.records());
        assertArrayEquals(((JournalRecord.SectionDone) everyKind().get(2)).written(), ((JournalRecord.SectionDone) read.records().get(2)).written());
    }

    @Test
    void aRecordCutShortByACrashEndsTheSegmentTorn() {
        byte[] whole = segment(everyKind());
        int lastStart = whole.length - JournalCodec.encode(everyKind().get(7)).length;
        for (int cut = lastStart + 1; cut < whole.length; cut++) {
            JournalCodec.Segment read = JournalCodec.read(Arrays.copyOf(whole, cut));
            assertEquals(JournalCodec.Ending.TORN, read.ending(), "cut at " + cut);
            assertEquals(7, read.records().size());
            assertEquals(lastStart, read.readUpTo());
        }
        byte[] zeros = Arrays.copyOf(whole, whole.length + 100); // a file grown with nothing written into it
        assertEquals(JournalCodec.Ending.TORN, JournalCodec.read(zeros).ending());
        assertEquals(8, JournalCodec.read(zeros).records().size());
    }

    @Test
    void aBadRecordWithAWholeOneAfterItIsDamage() {
        byte[] bytes = segment(everyKind());
        int third = JournalCodec.encode(everyKind().get(0)).length + JournalCodec.encode(everyKind().get(1)).length;
        bytes[third + 20] ^= 0x40; // a bit flipped inside the SECTION_DONE record
        JournalCodec.Segment read = JournalCodec.read(bytes);
        assertEquals(JournalCodec.Ending.DAMAGED, read.ending());
        assertEquals(2, read.records().size(), "nothing is read past the damage");
        assertEquals(third, read.readUpTo());
    }
}
