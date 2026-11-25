/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Block plans (spec mod-suite-v1 §6.4): canonical order, sections, double claims, and the plan hash. */
class BlockPlanTest {
    @Test
    void blocksAreSortedCanonicallyIntoSections() {
        BlockPlan plan = BlockPlan.builder(3465)
                .set(17, 64, -1, "minecraft:stone")
                .set(0, 64, 0, "minecraft:dirt")
                .set(-1, 64, 0, "minecraft:dirt")
                .set(0, 63, 0, "minecraft:stone")
                .set(1, 64, 0, "minecraft:dirt")
                .build();
        assertEquals(List.of(new BlockPlan.SectionPos(-1, 4, 0), new BlockPlan.SectionPos(0, 3, 0), new BlockPlan.SectionPos(0, 4, 0),
                new BlockPlan.SectionPos(1, 4, -1)), List.copyOf(plan.sections().keySet()), "sections by x, then z, then y; negatives round down");
        assertEquals(List.of(new BlockPlan.Block(0, 64, 0, "minecraft:dirt"), new BlockPlan.Block(1, 64, 0, "minecraft:dirt")),
                plan.sections().get(new BlockPlan.SectionPos(0, 4, 0)));
        assertEquals(5, plan.blockCount());
        assertEquals(3465, plan.dataVersion());
    }

    @Test
    void aSecondBlockAtTheSamePositionIsRefused() {
        BlockPlan.Builder builder = BlockPlan.builder(3465).set(5, 64, -20, "minecraft:stone");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> builder.set(5, 64, -20, "minecraft:stone"));
        assertEquals(true, e.getMessage().startsWith("Double claim: (5, 64, -20)"), e.getMessage());
        builder.set(5, 65, -20, "minecraft:stone"); // a different height is a different position
    }

    @Test
    void theHashDependsOnContentNotOnTheOrderBlocksWereAdded() {
        BlockPlan a = BlockPlan.builder(3465).set(1, 64, 1, "minecraft:stone").set(40, 64, -3, "minecraft:dirt").build();
        BlockPlan b = BlockPlan.builder(3465).set(40, 64, -3, "minecraft:dirt").set(1, 64, 1, "minecraft:stone").build();
        assertEquals(a, b);
        assertArrayEquals(PlanHash.of(a, "0.1.0"), PlanHash.of(b, "0.1.0"));
        assertEquals(32, PlanHash.of(a, "0.1.0").length);
        assertFalse(Arrays.equals(PlanHash.of(a, "0.1.0"), PlanHash.of(a, "0.1.1")), "the core version is part of the hash");
        BlockPlan otherState = BlockPlan.builder(3465).set(1, 64, 1, "minecraft:stone").set(40, 64, -3, "minecraft:stone").build();
        assertFalse(Arrays.equals(PlanHash.of(a, "0.1.0"), PlanHash.of(otherState, "0.1.0")));
        BlockPlan otherVersion = BlockPlan.builder(3466).set(1, 64, 1, "minecraft:stone").set(40, 64, -3, "minecraft:dirt").build();
        assertFalse(Arrays.equals(PlanHash.of(a, "0.1.0"), PlanHash.of(otherVersion, "0.1.0")), "the DataVersion is part of the hash");
    }

    @Test
    void theHashIsSha256OfTheDocumentedEncoding() throws Exception {
        // Built here byte by byte from the encoding written in PlanHash and spec mod-suite-v1 §6.4, so a change to the
        // encoding can't slip through unnoticed.
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        for (String text : new String[] {"stratos-plan/1", "0.1.0"}) {
            out.writeInt(text.length());
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        out.writeInt(3465); // DataVersion
        out.writeInt(1); // one section
        out.writeInt(-1); out.writeInt(4); out.writeInt(0); // section (-1, 4, 0)
        out.writeInt(1); // one block
        out.writeInt(-3); out.writeInt(64); out.writeInt(5); // block (-3, 64, 5)
        String state = "minecraft:gray_concrete";
        out.writeInt(state.length());
        out.write(state.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());

        BlockPlan plan = BlockPlan.builder(3465).set(-3, 64, 5, state).build();
        assertArrayEquals(expected, PlanHash.of(plan, "0.1.0"));
        assertEquals(java.util.HexFormat.of().formatHex(expected), PlanHash.hex(plan, "0.1.0"));
    }

    @Test
    void blocksAlreadyInCanonicalOrderMakeTheSamePlanWithoutTheBuilder() {
        BlockPlan.Builder builder = BlockPlan.builder(3465);
        for (int x = -20; x < 20; x += 3) {
            for (int y = 60; y < 70; y += 4) {
                for (int z = -18; z < 18; z += 5) {
                    builder.set(x, y, z, "minecraft:stone[" + (x + y + z) + "]");
                }
            }
        }
        BlockPlan built = builder.build();
        List<BlockPlan.Block> canonical = built.blocks();
        assertEquals(java.util.Optional.of(built), BlockPlan.ofCanonical(3465, canonical));
        assertEquals(java.util.Optional.of(BlockPlan.builder(3465).build()), BlockPlan.ofCanonical(3465, List.of()));

        java.util.List<BlockPlan.Block> swapped = new java.util.ArrayList<>(canonical);
        java.util.Collections.swap(swapped, 0, 1); // two blocks of one section out of order
        assertEquals(java.util.Optional.empty(), BlockPlan.ofCanonical(3465, swapped));
        java.util.List<BlockPlan.Block> twice = new java.util.ArrayList<>(canonical);
        twice.add(1, canonical.get(0)); // a position twice
        assertEquals(java.util.Optional.empty(), BlockPlan.ofCanonical(3465, twice));
        java.util.List<BlockPlan.Block> sections = new java.util.ArrayList<>(canonical);
        java.util.Collections.reverse(sections); // sections out of order
        assertEquals(java.util.Optional.empty(), BlockPlan.ofCanonical(3465, sections));
    }

    @Test
    void aPlanWithBlockEntityDataHashesApartAndOneWithoutAsBefore() {
        BlockPlan plain = BlockPlan.builder(3465).set(1, 64, 1, "minecraft:chest[facing=north,type=single,waterlogged=false]").build();
        BlockPlan withData = BlockPlan.builder(3465).set(1, 64, 1, "minecraft:chest[facing=north,type=single,waterlogged=false]", "{Items:[]}").build();
        assertEquals(PlanHash.hex(plain, "0.1.0"), PlanHash.hex(BlockPlan.builder(3465).set(1, 64, 1,
                "minecraft:chest[facing=north,type=single,waterlogged=false]", null).build(), "0.1.0"), "no data: the hash as before");
        assertFalse(PlanHash.hex(plain, "0.1.0").equals(PlanHash.hex(withData, "0.1.0")), "data changes the hash");
        assertFalse(plain.equals(withData));
    }
}
