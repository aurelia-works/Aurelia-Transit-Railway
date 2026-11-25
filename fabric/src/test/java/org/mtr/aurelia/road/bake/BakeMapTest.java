/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.bake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.PaletteRole;

/** What each Stratos block bakes to (task B-3; owner, 2026-10-15: block for block, 1–5 layers a slab, 6–8 a full block). */
class BakeMapTest {
    private static final String SLAB = "[type=bottom,waterlogged=false]";
    private static final String DEEPSLATE = "minecraft:polished_deepslate";
    private static final String DEEPSLATE_SLAB = "minecraft:polished_deepslate_slab" + SLAB;
    private static final String QUARTZ = "minecraft:smooth_quartz";
    private static final String QUARTZ_SLAB = "minecraft:smooth_quartz_slab" + SLAB;

    /** A chord across the block from west to east at {@code z}, on the band square's grid (z in blocks, snapped). */
    private static int westToEast(double z) {
        int k = LineChords.snap(z);
        return LineChords.index(LineChords.WEST, k, LineChords.EAST, k);
    }

    /** A chord across the block from north to south at {@code x}. */
    private static int northToSouth(double x) {
        int k = LineChords.snap(x);
        return LineChords.index(LineChords.NORTH, k, LineChords.SOUTH, k);
    }

    @Test
    void heightsOneToFiveBecomeASlabAndSixToEightAFullBlock() {
        for (int layers = 1; layers <= 8; layers++) {
            String expected = layers >= 6 ? DEEPSLATE : DEEPSLATE_SLAB;
            assertEquals(Optional.of(expected), BakeMap.vanilla("stratos:asphalt[layers=" + layers + "]"), "asphalt of " + layers + " layers");
        }
    }

    @Test
    void eachBlockBecomesItsVanillaFamily() {
        assertEquals(Optional.of("minecraft:smooth_stone"), BakeMap.vanilla("stratos:footway_flags[layers=8]"));
        assertEquals(Optional.of("minecraft:smooth_stone_slab" + SLAB), BakeMap.vanilla("stratos:footway_flags[layers=1]"));
        assertEquals(Optional.of(DEEPSLATE), BakeMap.vanilla("stratos:footway_asphalt[layers=7]"));
        assertEquals(Optional.of(DEEPSLATE_SLAB), BakeMap.vanilla("stratos:gully[facing=east,layers=4]"));
        assertEquals(Optional.of("minecraft:polished_andesite"), BakeMap.vanilla("stratos:kerb[facing=north,layers=8,shape=straight]"));
        assertEquals(Optional.of("minecraft:polished_andesite_slab" + SLAB), BakeMap.vanilla("stratos:kerb[facing=west,layers=1,shape=inner]"));
        assertEquals(Optional.of("minecraft:smooth_sandstone"), BakeMap.vanilla("stratos:dropped_kerb[colour=buff,facing=north,layers=8]"));
        assertEquals(Optional.of("minecraft:polished_granite_slab" + SLAB), BakeMap.vanilla("stratos:dropped_kerb[colour=red,facing=south,layers=2]"));
    }

    @Test
    void theFamiliesAreTheVanillaPalettes() {
        // Into the vanilla palette's blocks (ADR-0044): its surface, footway, kerb and white marking.
        for (PaletteRole role : List.of(PaletteRole.SURFACE, PaletteRole.FOOTWAY, PaletteRole.KERB, PaletteRole.MARKING_WHITE)) {
            Family family = BuiltInPalettes.vanilla().family(role).orElseThrow();
            assertTrue(Set.of(DEEPSLATE, "minecraft:smooth_stone", "minecraft:polished_andesite", QUARTZ).contains(family.full()), role.id());
        }
    }

    @Test
    void propertiesMayComeInAnyOrder() {
        assertEquals(BakeMap.vanilla("stratos:kerb[facing=north,layers=3,shape=straight]"),
                BakeMap.vanilla("stratos:kerb[shape=straight,layers=3,facing=north]"));
    }

    @Test
    void onlyStratosBlocksAreBaked() {
        assertEquals(Optional.empty(), BakeMap.vanilla("minecraft:stone"));
        assertEquals(Optional.empty(), BakeMap.vanilla("stratos_transit:something"), "another namespace");
        assertEquals(Optional.empty(), BakeMap.vanilla("stratos:a_block_from_a_later_stratos[layers=8]"), "unknown: left as it is");
        assertTrue(BakeMap.isStratos("stratos:asphalt[layers=8]"));
        assertFalse(BakeMap.isStratos("stratos_transit:x"));
    }

    @Test
    void aLineBlockIsPaintedWhereTheLinesCentreRunsThroughItself() {
        int across = westToEast(0.5);
        assertEquals(Optional.of(QUARTZ), BakeMap.vanilla("stratos:white_line[chord=" + across + ",layers=8]"));
        assertEquals(Optional.of(QUARTZ_SLAB), BakeMap.vanilla("stratos:white_line_wide[chord=" + across + ",layers=2]"));
        assertEquals(Optional.of("minecraft:yellow_concrete"), BakeMap.vanilla("stratos:yellow_line[chord=" + across + ",layers=8]"));
        assertEquals(Optional.of("minecraft:smooth_sandstone_slab" + SLAB), BakeMap.vanilla("stratos:double_yellow_line[chord=" + across + ",layers=4]"));
        // Only the paint's edge reaches in: the line's centre is in the margin, west of the block.
        int margin = northToSouth(-0.06);
        assertEquals(Optional.of(DEEPSLATE), BakeMap.vanilla("stratos:white_line[chord=" + margin + ",layers=8]"));
        assertEquals(Optional.of(DEEPSLATE_SLAB), BakeMap.vanilla("stratos:yellow_line[chord=" + margin + ",layers=1]"));
    }

    @Test
    void aStraightLineComesOutOneBlockWide() {
        // A line running north–south through every x across a row of blocks: for each position of its centre, exactly one
        // of the blocks it touches (the one its centre is in) is painted.
        for (int k = 0; k < LineChords.PER_EDGE; k++) {
            double[] point = LineChords.point(LineChords.NORTH, k);
            double x = point[0];
            int painted = 0;
            for (int block = -1; block <= 1; block++) {
                double local = x - block; // the centre in that block's own coordinates
                if (local < -LineChords.MARGIN || local > 1 + LineChords.MARGIN) {
                    continue; // not one of the blocks it touches
                }
                if (BakeMap.painted(northToSouth(local))) {
                    painted++;
                }
            }
            assertEquals(1, painted, "a line with its centre at x = " + x);
        }
    }

    @Test
    void everyChordBakesToQuartzOrDeepslate() {
        for (int chord = 0; chord < LineChords.COUNT; chord++) {
            String baked = BakeMap.vanilla("stratos:white_line[chord=" + chord + ",layers=8]").orElseThrow();
            assertTrue(baked.equals(QUARTZ) || baked.equals(DEEPSLATE), baked);
        }
    }

    @Test
    void everyStateTheStratosUkPaletteCanPlaceBakes() {
        for (Family family : BuiltInPalettes.stratosUk().families().values()) {
            List<String> states = new ArrayList<>();
            states.add(family.full());
            family.half().ifPresent(states::add);
            family.layers().ifPresent(template -> {
                for (int layers = 1; layers <= 8; layers++) {
                    states.add(template.replace(Family.LAYERS, Integer.toString(layers)));
                }
            });
            for (String state : states) {
                assertEquals(BakeMap.isStratos(state), BakeMap.vanilla(state).isPresent(), state);
            }
        }
    }

    @Test
    void aBakePlanWritesVanillaWhereTheStratosBlocksAreStill() {
        List<Bake.Found> found = List.of(new Bake.Found(1, 64, 2, "stratos:asphalt[layers=8]"),
                new Bake.Found(1, 65, 2, "stratos:kerb[facing=north,layers=1,shape=straight]"),
                new Bake.Found(3, 64, 2, "stratos:unknown_thing"));
        Bake.Plan plan = Bake.plan(3465, found).orElseThrow();
        assertEquals(2, plan.blockCount());
        assertEquals(List.of(new BlockPlan.Block(1, 64, 2, DEEPSLATE, null), new BlockPlan.Block(1, 65, 2, "minecraft:polished_andesite_slab" + SLAB, null)),
                plan.writes().blocks());
        assertEquals(List.of(new BlockPlan.Block(1, 64, 2, "stratos:asphalt[layers=8]", null),
                new BlockPlan.Block(1, 65, 2, "stratos:kerb[facing=north,layers=1,shape=straight]", null)), plan.expected().blocks());
        assertEquals(List.of("stratos:unknown_thing"), plan.unknown());
        assertEquals(Optional.empty(), Bake.plan(3465, List.of()));
        assertEquals(Optional.empty(), Bake.plan(3465, List.of(new Bake.Found(0, 0, 0, "stratos:unknown_thing"))), "nothing it can bake");
    }
}
