/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * The undo-image format (world-data-format §6, task F-2): one chunk section of one operation, holding only the blocks
 * the operation changed, compressed with Java's {@link Deflater} (owner, 2026-10-05). Before compression, all integers
 * 32-bit big-endian unless noted and every string as its UTF-8 byte length (32-bit) followed by the bytes:
 * <ol>
 *   <li>the tag {@code "SUND"} and the format version (16-bit, currently 1);</li>
 *   <li>the DataVersion, then the section position x, y, z;</li>
 *   <li>the palette: its size, then each state string, in first-use order;</li>
 *   <li>the number of changes, then per change its position inside the section (16-bit: {@code y << 8 | z << 4 | x},
 *       each 0–15), its before state's palette index and its after state's palette index;</li>
 *   <li><b>version 2 only</b> (F-1+: some change carries block-entity data): the data palette, its size then each SNBT
 *       string, in first-use order; then per change, in the same order, its before data's and its after data's palette
 *       index, -1 for none.</li>
 * </ol>
 * An image with no block-entity data is written as version 1, byte for byte as before F-1+. Both versions are read.
 */
public final class UndoCodec {
    static final int TAG = 0x53554E44; // "SUND"
    static final int VERSION = 1;
    /** The version of an image whose changes carry block-entity data (F-1+). */
    static final int VERSION_WITH_DATA = 2;
    /** A section has 4,096 blocks; anything bigger is a corrupt file. */
    private static final int MAX_CHANGES = 4096;
    private static final int MAX_PALETTE = 2 * MAX_CHANGES;
    private static final int MAX_STATE_BYTES = 32_768;
    /** A block entity's SNBT may be long (a chest of written books), but not this long. */
    private static final int MAX_DATA_BYTES = 8 << 20;

    /** One decoded section image. */
    public record Section(int dataVersion, BlockPlan.SectionPos pos, List<BlockChange> changes) {
    }

    private UndoCodec() {
    }

    /** {@code changes} grouped by chunk section, sections in canonical order. */
    public static SortedMap<BlockPlan.SectionPos, List<BlockChange>> bySection(List<BlockChange> changes) {
        SortedMap<BlockPlan.SectionPos, List<BlockChange>> sections = new TreeMap<>(
                (a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.y(), b.y()));
        for (BlockChange change : changes) {
            sections.computeIfAbsent(BlockPlan.SectionPos.of(change.x(), change.y(), change.z()), s -> new ArrayList<>()).add(change);
        }
        return sections;
    }

    /** The compressed image of {@code changes}, which must all lie in section {@code pos}. */
    public static byte[] encode(int dataVersion, BlockPlan.SectionPos pos, List<BlockChange> changes) {
        if (changes.size() > MAX_CHANGES) {
            throw new IllegalArgumentException("A section has at most 4,096 blocks, not " + changes.size());
        }
        Map<String, Integer> palette = new HashMap<>();
        List<String> order = new ArrayList<>();
        Map<String, Integer> dataPalette = new HashMap<>();
        List<String> dataOrder = new ArrayList<>();
        for (BlockChange change : changes) {
            if (!BlockPlan.SectionPos.of(change.x(), change.y(), change.z()).equals(pos)) {
                throw new IllegalArgumentException("(" + change.x() + ", " + change.y() + ", " + change.z() + ") is not in section " + pos);
            }
            for (String state : new String[] {change.before(), change.after()}) {
                if (palette.putIfAbsent(state, order.size()) == null) {
                    order.add(state);
                }
            }
            for (String data : new String[] {change.beforeData(), change.afterData()}) {
                if (data != null && dataPalette.putIfAbsent(data, dataOrder.size()) == null) {
                    dataOrder.add(data);
                }
            }
        }
        boolean withData = !dataOrder.isEmpty();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater))) {
            out.writeInt(TAG);
            out.writeShort(withData ? VERSION_WITH_DATA : VERSION);
            out.writeInt(dataVersion);
            out.writeInt(pos.x());
            out.writeInt(pos.y());
            out.writeInt(pos.z());
            out.writeInt(order.size());
            for (String state : order) {
                byte[] utf8 = state.getBytes(StandardCharsets.UTF_8);
                out.writeInt(utf8.length);
                out.write(utf8);
            }
            out.writeInt(changes.size());
            for (BlockChange change : changes) {
                int local = (StrictMath.floorMod(change.y(), 16) << 8) | (StrictMath.floorMod(change.z(), 16) << 4) | StrictMath.floorMod(change.x(), 16);
                out.writeShort(local);
                out.writeInt(palette.get(change.before()));
                out.writeInt(palette.get(change.after()));
            }
            if (withData) {
                out.writeInt(dataOrder.size());
                for (String data : dataOrder) {
                    byte[] utf8 = data.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(utf8.length);
                    out.write(utf8);
                }
                for (BlockChange change : changes) {
                    out.writeInt(change.beforeData() == null ? -1 : dataPalette.get(change.beforeData()));
                    out.writeInt(change.afterData() == null ? -1 : dataPalette.get(change.afterData()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // writing to memory doesn't fail
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    /** The section image in {@code compressed}. Throws {@link IOException} if it isn't a valid image. */
    public static Section decode(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        try (DataInputStream in = new DataInputStream(limited(new InflaterInputStream(new ByteArrayInputStream(compressed), inflater)))) {
            if (in.readInt() != TAG) {
                throw new IOException("not a Stratos undo image");
            }
            int version = in.readUnsignedShort();
            if (version != VERSION && version != VERSION_WITH_DATA) {
                throw new IOException("undo image format " + version + " is not supported (this Stratos reads " + VERSION + " and "
                        + VERSION_WITH_DATA + ")");
            }
            int dataVersion = in.readInt();
            BlockPlan.SectionPos pos = new BlockPlan.SectionPos(in.readInt(), in.readInt(), in.readInt());
            int paletteSize = in.readInt();
            if (paletteSize < 0 || paletteSize > MAX_PALETTE) {
                throw new IOException("bad palette size " + paletteSize);
            }
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                int length = in.readInt();
                if (length < 0 || length > MAX_STATE_BYTES) {
                    throw new IOException("bad state length " + length);
                }
                palette[i] = new String(in.readNBytes(length), StandardCharsets.UTF_8);
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_CHANGES) {
                throw new IOException("bad change count " + count);
            }
            List<BlockChange> changes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int local = in.readUnsignedShort();
                int before = in.readInt();
                int after = in.readInt();
                if (local > 0xFFF || before < 0 || before >= paletteSize || after < 0 || after >= paletteSize) {
                    throw new IOException("bad change " + i);
                }
                changes.add(new BlockChange(pos.x() * 16 + (local & 15), pos.y() * 16 + (local >> 8), pos.z() * 16 + ((local >> 4) & 15),
                        palette[before], palette[after]));
            }
            if (version == VERSION_WITH_DATA) {
                int dataSize = in.readInt();
                if (dataSize < 1 || dataSize > 2 * count) {
                    throw new IOException("bad data palette size " + dataSize);
                }
                String[] data = new String[dataSize];
                for (int i = 0; i < dataSize; i++) {
                    int length = in.readInt();
                    if (length < 0 || length > MAX_DATA_BYTES) {
                        throw new IOException("bad data length " + length);
                    }
                    data[i] = new String(in.readNBytes(length), StandardCharsets.UTF_8);
                }
                for (int i = 0; i < count; i++) {
                    int before = in.readInt();
                    int after = in.readInt();
                    if (before < -1 || before >= dataSize || after < -1 || after >= dataSize) {
                        throw new IOException("bad data of change " + i);
                    }
                    BlockChange change = changes.get(i);
                    changes.set(i, new BlockChange(change.x(), change.y(), change.z(), change.before(), change.after(), before < 0 ? null : data[before],
                            after < 0 ? null : data[after]));
                }
            }
            return new Section(dataVersion, pos, changes);
        } finally {
            inflater.end();
        }
    }

    /** Stops reading after a generous limit, so a corrupt file can't make Stratos inflate gigabytes. */
    private static InputStream limited(InputStream in) {
        return new InputStream() {
            private long left = 64L << 20;

            @Override
            public int read() throws IOException {
                if (this.left-- <= 0) {
                    throw new IOException("undo image too large");
                }
                return in.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (this.left <= 0) {
                    throw new IOException("undo image too large");
                }
                int read = in.read(buffer, offset, (int) StrictMath.min(length, this.left));
                if (read > 0) {
                    this.left -= read;
                }
                return read;
            }
        };
    }
}
