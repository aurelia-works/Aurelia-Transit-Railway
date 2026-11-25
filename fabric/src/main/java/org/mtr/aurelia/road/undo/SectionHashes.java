/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * The post-image hash of each section an operation changed (spec world-data-format §6; task G-9): SHA-256 over the
 * after state of every changed block in the section, so R-7 can tell which sections were changed by hand since.
 *
 * <p>The encoding, all integers 32-bit big-endian and every string as its UTF-8 byte length followed by the bytes: the
 * tag {@code "stratos-section/1"}, the section's x, y and z, the number of changed blocks, and per block in canonical
 * order (y, z, x) its x, y, z and after state.
 */
public final class SectionHashes {
    static final String FORMAT = "stratos-section/1";

    private SectionHashes() {
    }

    /** One hash per section {@code changes} touch, in canonical order (x, z, y). */
    public static List<OperationRecord.SectionHash> of(List<BlockChange> changes) {
        Map<BlockPlan.SectionPos, List<BlockChange>> bySection = new TreeMap<>(Comparator.comparingInt(BlockPlan.SectionPos::x)
                .thenComparingInt(BlockPlan.SectionPos::z).thenComparingInt(BlockPlan.SectionPos::y));
        for (BlockChange change : changes) {
            bySection.computeIfAbsent(BlockPlan.SectionPos.of(change.x(), change.y(), change.z()), s -> new ArrayList<>()).add(change);
        }
        List<OperationRecord.SectionHash> hashes = new ArrayList<>();
        for (Map.Entry<BlockPlan.SectionPos, List<BlockChange>> section : bySection.entrySet()) {
            BlockPlan.SectionPos pos = section.getKey();
            hashes.add(new OperationRecord.SectionHash(pos.x(), pos.y(), pos.z(), hash(pos, section.getValue())));
        }
        return hashes;
    }

    private static String hash(BlockPlan.SectionPos pos, List<BlockChange> changes) {
        List<BlockChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingInt(BlockChange::y).thenComparingInt(BlockChange::z).thenComparingInt(BlockChange::x));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeString(out, FORMAT);
            out.writeInt(pos.x());
            out.writeInt(pos.y());
            out.writeInt(pos.z());
            out.writeInt(sorted.size());
            Map<String, byte[]> utf8 = new HashMap<>(); // each state's bytes once per section (F-2b: less garbage)
            for (BlockChange change : sorted) {
                out.writeInt(change.x());
                out.writeInt(change.y());
                out.writeInt(change.z());
                byte[] state = utf8.computeIfAbsent(change.after(), after -> after.getBytes(StandardCharsets.UTF_8));
                out.writeInt(state.length);
                out.write(state);
            }
            out.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Couldn't hash the section", e);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }
}
