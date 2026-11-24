/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * The plan hash (spec mod-suite-v1 §6.4, editor-protocol §5): SHA-256 over the plan's canonical encoding. The client's
 * preview and the server's commit must produce the same hash.
 *
 * <p>The encoding, all integers 32-bit big-endian, doubles as their 64-bit IEEE 754 bits, big-endian, and every string
 * as its UTF-8 byte length followed by the bytes:
 * <ol>
 *   <li>the format tag {@code "stratos-plan/1"};</li>
 *   <li>the core version (identical inputs give identical plans only with identical core code);</li>
 *   <li>the DataVersion;</li>
 *   <li>the number of sections, then per section in canonical order (x, z, y): its x, y and z, its block count, and per
 *       block in canonical order (y, z, x): x, y, z and the state string;</li>
 *   <li>only if a block carries block-entity data (F-1+: an undo's or redo's plan; never a road's): the tag is
 *       {@code "stratos-plan/2"} instead, and each block's state is followed by its data string (empty for none). A
 *       plan without data hashes exactly as before;</li>
 *   <li>only if the plan has side effects (T-2: track): the tag {@code "sideEffects"}, their number, and per side effect
 *       in the plan's order its kind ({@code "trackRail"}), then a's x, y, z and heading, b's x, y, z and heading, the
 *       speeds from a and from b, the shape's name and the radius. A plan without side effects hashes exactly as before
 *       T-2.</li>
 * </ol>
 */
public final class PlanHash {
    static final String FORMAT = "stratos-plan/1";
    static final String FORMAT_WITH_DATA = "stratos-plan/2";

    private PlanHash() {
    }

    /** The 32-byte hash of {@code plan}, with no side effects, as computed by core version {@code coreVersion}. */
    public static byte[] of(BlockPlan plan, String coreVersion) {
        return of(plan, List.of(), coreVersion);
    }

    /** The 32-byte hash of {@code plan} and its side effects {@code sideEffects}, as computed by core version {@code coreVersion}. */
    public static byte[] of(BlockPlan plan, List<SideEffect> sideEffects, String coreVersion) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            boolean data = plan.sections().values().stream().flatMap(List::stream).anyMatch(block -> block.data() != null);
            writeString(out, data ? FORMAT_WITH_DATA : FORMAT);
            writeString(out, coreVersion);
            out.writeInt(plan.dataVersion());
            out.writeInt(plan.sections().size());
            for (Map.Entry<BlockPlan.SectionPos, List<BlockPlan.Block>> section : plan.sections().entrySet()) {
                out.writeInt(section.getKey().x());
                out.writeInt(section.getKey().y());
                out.writeInt(section.getKey().z());
                out.writeInt(section.getValue().size());
                for (BlockPlan.Block block : section.getValue()) {
                    out.writeInt(block.x());
                    out.writeInt(block.y());
                    out.writeInt(block.z());
                    writeString(out, block.state());
                    if (data) {
                        writeString(out, block.data() == null ? "" : block.data());
                    }
                }
            }
            if (!sideEffects.isEmpty()) {
                writeString(out, "sideEffects");
                out.writeInt(sideEffects.size());
                for (SideEffect effect : sideEffects) {
                    // Java 17 has no pattern switch; each new kind of side effect adds a branch here.
                    if (!(effect instanceof SideEffect.TrackRail rail)) {
                        throw new IllegalArgumentException("No hash form for " + effect);
                    }
                    writeString(out, "trackRail");
                    for (int value : new int[] {rail.ax(), rail.ay(), rail.az(), rail.aFacing(), rail.bx(), rail.by(), rail.bz(), rail.bFacing(),
                            rail.speedFromA(), rail.speedFromB()}) {
                        out.writeInt(value);
                    }
                    writeString(out, rail.shape().name());
                    out.writeDouble(rail.radius());
                }
            }
            out.flush();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Couldn't hash the plan", e);
        }
    }

    /** The hash as 64 lowercase hex digits. */
    public static String hex(BlockPlan plan, String coreVersion) {
        return HexFormat.of().formatHex(of(plan, coreVersion));
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }
}
