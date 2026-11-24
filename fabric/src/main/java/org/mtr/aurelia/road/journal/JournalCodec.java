/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32C;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.undo.UndoCodec;

/**
 * The journal's file format (world-data-format §5; task F-2+). A segment, {@code operations/journal/<8 digits>.log}, is
 * a run of records, each framed as:
 * <ol>
 *   <li>its length (32-bit, big-endian), counting what follows the checksum;</li>
 *   <li>its CRC32C checksum (32-bit) over that;</li>
 *   <li>its type (a byte: 1 {@code BEGIN}, 2 {@code PREIMAGE}, 3 {@code SECTION_DONE}, 4 {@code END}, 5 {@code TRACK},
 *       6 {@code TRACK_DONE}, 7 {@code RECOVERED}, 8 {@code RETIRED}) and its fields.</li>
 * </ol>
 * Fields: integers 32-bit and times 64-bit, big-endian; a run or operation id as two 64-bit halves; a string as its UTF-8
 * byte length (32-bit) and the bytes; an enum by name. A {@code PREIMAGE} holds its section's blocks as an undo image
 * ({@link UndoCodec}), each block's state and data as found and as planned. A side effect is its kind
 * ({@code trackRail}) and its fields as in the operation record.
 *
 * <p><b>Reading</b> stops at the first record that is cut short or doesn't check out. If nothing whole follows it, it is
 * the crash point (a <b>torn</b> end, ignored); if another whole record follows, the segment is <b>damaged</b> there.
 */
public final class JournalCodec {
    /** The biggest record allowed: a section's pre-image may hold long block-entity data, but not this much. */
    static final int MAX_RECORD = 64 << 20;

    private static final byte BEGIN = 1;
    private static final byte PREIMAGE = 2;
    private static final byte SECTION_DONE = 3;
    private static final byte END = 4;
    private static final byte TRACK = 5;
    private static final byte TRACK_DONE = 6;
    private static final byte RECOVERED = 7;
    private static final byte RETIRED = 8;
    private static final String TRACK_RAIL = "trackRail";
    private static final int MAX_STRING = 1 << 20;
    private static final int MAX_EFFECTS = 100_000;

    /** How a segment ended. */
    public enum Ending {
        /** Every byte was whole records. */
        CLEAN,
        /** Its last record is cut short or doesn't check out, with nothing whole after it: where a crash stopped writing. */
        TORN,
        /** A record doesn't check out, and a whole one follows it: the file was damaged there. */
        DAMAGED
    }

    /** One segment as read: its records, how it ended, and where reading stopped (bytes from its start). */
    public record Segment(List<JournalRecord> records, Ending ending, int readUpTo) {
        public Segment {
            records = List.copyOf(records);
        }
    }

    private JournalCodec() {
    }

    /** {@code record} framed for a segment: length, checksum, type and fields. Compresses a pre-image. Any thread. */
    public static byte[] encode(JournalRecord record) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            // Java 17 has no pattern switch: one branch per record type.
            if (record instanceof JournalRecord.Begin begin) {
                out.writeByte(BEGIN);
                id(out, begin.run());
                out.writeUTF(begin.kind().name());
                id(out, begin.operation());
                string(out, begin.dimension());
                string(out, begin.owner());
                string(out, begin.planHash());
                out.writeLong(begin.time());
            } else if (record instanceof JournalRecord.Preimage preimage) {
                out.writeByte(PREIMAGE);
                id(out, preimage.run());
                section(out, preimage.section());
                byte[] image = UndoCodec.encode(preimage.dataVersion(), preimage.section(), preimage.changes());
                out.writeInt(image.length);
                out.write(image);
            } else if (record instanceof JournalRecord.SectionDone done) {
                out.writeByte(SECTION_DONE);
                id(out, done.run());
                section(out, done.section());
                for (long bits : done.written()) {
                    out.writeLong(bits);
                }
            } else if (record instanceof JournalRecord.End end) {
                out.writeByte(END);
                id(out, end.run());
                out.writeUTF(end.status().name());
                string(out, end.failure());
                out.writeInt(end.blocksWritten());
            } else if (record instanceof JournalRecord.Track track) {
                out.writeByte(TRACK);
                id(out, track.run());
                out.writeUTF(track.action().name());
                effects(out, track.effects());
            } else if (record instanceof JournalRecord.TrackDone done) {
                out.writeByte(TRACK_DONE);
                id(out, done.run());
                out.writeUTF(done.action().name());
                effects(out, done.effects());
                for (SideEffectWriter.Outcome outcome : done.outcomes()) {
                    out.writeUTF(outcome.name());
                }
            } else if (record instanceof JournalRecord.Recovered recovered) {
                out.writeByte(RECOVERED);
                id(out, recovered.run());
                out.writeUTF(recovered.outcome().name());
                string(out, recovered.detail());
            } else if (record instanceof JournalRecord.Retired retired) {
                out.writeByte(RETIRED);
                id(out, retired.run());
            } else {
                throw new IllegalArgumentException("No journal form for " + record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // writing to memory doesn't fail
        }
        byte[] payload = body.toByteArray();
        if (payload.length > MAX_RECORD) {
            throw new IllegalArgumentException("A journal record of " + payload.length + " bytes is too big");
        }
        CRC32C crc = new CRC32C();
        crc.update(payload);
        byte[] framed = new byte[8 + payload.length];
        writeInt(framed, 0, payload.length);
        writeInt(framed, 4, (int) crc.getValue());
        System.arraycopy(payload, 0, framed, 8, payload.length);
        return framed;
    }

    /** The records of one segment's bytes, up to its first bad record ({@link Ending}). */
    public static Segment read(byte[] bytes) {
        List<JournalRecord> records = new ArrayList<>();
        int at = 0;
        while (at < bytes.length) {
            JournalRecord record = recordAt(bytes, at);
            if (record == null) {
                return new Segment(records, wholeRecordAfter(bytes, at) ? Ending.DAMAGED : Ending.TORN, at);
            }
            records.add(record);
            at += 8 + readInt(bytes, at);
        }
        return new Segment(records, Ending.CLEAN, at);
    }

    /** The record framed at {@code at}, or null if it is cut short or doesn't check out. */
    private static JournalRecord recordAt(byte[] bytes, int at) {
        if (bytes.length - at < 8) {
            return null;
        }
        int length = readInt(bytes, at);
        if (length < 1 || length > MAX_RECORD || length > bytes.length - at - 8) {
            return null;
        }
        CRC32C crc = new CRC32C();
        crc.update(bytes, at + 8, length);
        if ((int) crc.getValue() != readInt(bytes, at + 4)) {
            return null;
        }
        try {
            return decode(bytes, at + 8, length);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Whether a whole record follows the bad one at {@code at}: it can only be found where the bad one's own length says
     * it ends, so a torn end (whose length may be anything) is never mistaken for damage unless that lands on a record.
     */
    private static boolean wholeRecordAfter(byte[] bytes, int at) {
        if (bytes.length - at < 8) {
            return false;
        }
        long next = (long) at + 8 + readInt(bytes, at);
        return next > at + 8 && next < bytes.length && recordAt(bytes, (int) next) != null;
    }

    private static JournalRecord decode(byte[] bytes, int offset, int length) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, offset, length));
        byte type = in.readByte();
        UUID run = id(in);
        JournalRecord record;
        switch (type) {
            case BEGIN -> record = new JournalRecord.Begin(run, Operations.Kind.valueOf(in.readUTF()), id(in), string(in), string(in), string(in), in.readLong());
            case PREIMAGE -> {
                BlockPlan.SectionPos section = section(in);
                int size = in.readInt();
                if (size < 0 || size > length) {
                    throw new IOException("bad pre-image size " + size);
                }
                UndoCodec.Section image = UndoCodec.decode(in.readNBytes(size));
                if (!image.pos().equals(section)) {
                    throw new IOException("a pre-image of " + image.pos() + " filed under " + section);
                }
                record = new JournalRecord.Preimage(run, section, image.dataVersion(), image.changes());
            }
            case SECTION_DONE -> {
                BlockPlan.SectionPos section = section(in);
                long[] written = new long[64];
                for (int i = 0; i < 64; i++) {
                    written[i] = in.readLong();
                }
                record = new JournalRecord.SectionDone(run, section, written);
            }
            case END -> record = new JournalRecord.End(run, JournalRecord.EndStatus.valueOf(in.readUTF()), string(in), in.readInt());
            case TRACK -> record = new JournalRecord.Track(run, JournalRecord.TrackAction.valueOf(in.readUTF()), effects(in));
            case TRACK_DONE -> {
                JournalRecord.TrackAction action = JournalRecord.TrackAction.valueOf(in.readUTF());
                List<SideEffect> effects = effects(in);
                List<SideEffectWriter.Outcome> outcomes = new ArrayList<>();
                for (int i = 0; i < effects.size(); i++) {
                    outcomes.add(SideEffectWriter.Outcome.valueOf(in.readUTF()));
                }
                record = new JournalRecord.TrackDone(run, action, effects, outcomes);
            }
            case RECOVERED -> record = new JournalRecord.Recovered(run, JournalRecord.Outcome.valueOf(in.readUTF()), string(in));
            case RETIRED -> record = new JournalRecord.Retired(run);
            default -> throw new IOException("unknown record type " + type);
        }
        if (in.available() != 0) {
            throw new IOException(in.available() + " bytes left over");
        }
        return record;
    }

    private static void id(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID id(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void string(DataOutputStream out, String text) throws IOException {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    private static String string(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING) {
            throw new IOException("bad string length " + length);
        }
        byte[] utf8 = in.readNBytes(length);
        if (utf8.length != length) {
            throw new EOFException();
        }
        return new String(utf8, StandardCharsets.UTF_8);
    }

    private static void section(DataOutputStream out, BlockPlan.SectionPos section) throws IOException {
        out.writeInt(section.x());
        out.writeInt(section.y());
        out.writeInt(section.z());
    }

    private static BlockPlan.SectionPos section(DataInputStream in) throws IOException {
        return new BlockPlan.SectionPos(in.readInt(), in.readInt(), in.readInt());
    }

    private static void effects(DataOutputStream out, List<SideEffect> effects) throws IOException {
        out.writeInt(effects.size());
        for (SideEffect effect : effects) {
            if (effect instanceof SideEffect.TrackRail rail) {
                out.writeUTF(TRACK_RAIL);
                out.writeInt(rail.ax());
                out.writeInt(rail.ay());
                out.writeInt(rail.az());
                out.writeInt(rail.aFacing());
                out.writeInt(rail.bx());
                out.writeInt(rail.by());
                out.writeInt(rail.bz());
                out.writeInt(rail.bFacing());
                out.writeInt(rail.speedFromA());
                out.writeInt(rail.speedFromB());
                out.writeUTF(rail.shape().name());
                out.writeDouble(rail.radius());
            } else {
                throw new IllegalArgumentException("No journal form for " + effect);
            }
        }
    }

    private static List<SideEffect> effects(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_EFFECTS) {
            throw new IOException("bad side-effect count " + count);
        }
        List<SideEffect> effects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String kind = in.readUTF();
            if (!kind.equals(TRACK_RAIL)) {
                throw new IOException("unknown side effect " + kind);
            }
            effects.add(new SideEffect.TrackRail(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt(), SideEffect.TrackRail.Shape.valueOf(in.readUTF()), in.readDouble()));
        }
        return effects;
    }

    private static void writeInt(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 24);
        bytes[at + 1] = (byte) (value >>> 16);
        bytes[at + 2] = (byte) (value >>> 8);
        bytes[at + 3] = (byte) value;
    }

    private static int readInt(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8 | (bytes[at + 3] & 0xFF);
    }
}
