/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import org.mtr.aurelia.road.road.palette.BlockLookup;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.road.palette.PaletteFile;
import org.mtr.aurelia.road.road.palette.PaletteRole;

import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.road.Band;

/** A column's surface fitted to the palette's steps, with its foundation and clearance (task G-7). */
class SurfaceFitTest {
    private static final Column LANE = new Column(Band.LANE, false, false);
    private static final Column DASH = new Column(Band.CENTRE_LINE, true, false);

    /**
     * A palette with eighth layers (R-9: ADR-0044's layered blocks), as a palette file: every surface role a layered
     * test block, falling back to vanilla slabs; resolved in a game that has the test blocks.
     */
    private static final BlockPalette LAYERS = layered();

    private static BlockPalette layered() {
        Map<PaletteRole, List<Family>> roles = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            if (role.required()) {
                roles.put(role, role.surface()
                        ? List.of(Family.withLayers("test:asphalt", "test:asphalt_slope[layers={layers}]"),
                                Family.withHalf("minecraft:stone", "minecraft:stone_slab[type=bottom,waterlogged=false]"))
                        : List.of(Family.full(role == PaletteRole.FOUNDATION ? "minecraft:stone" : "minecraft:blue_wool")));
            }
        }
        BlockLookup game = new BlockLookup() {
            @Override
            public boolean has(String block) {
                return true;
            }

            @Override
            public String canonical(String state) {
                return state;
            }
        };
        try {
            return Palette.resolve(new PaletteFile("layered", Optional.empty(), 3465, roles), game);
        } catch (PaletteException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<Integer, String> column(double top, BlockPalette palette, Column column, int minY, int maxY) {
        BlockPlan.Builder builder = BlockPlan.builder(3465);
        SurfaceFit.stack(builder, 3, -2, column, top, palette, minY, maxY);
        Map<Integer, String> byY = new LinkedHashMap<>();
        builder.build().blocks().forEach(b -> {
            assertEquals(3, b.x());
            assertEquals(-2, b.z());
            byY.put(b.y(), b.state());
        });
        return byY;
    }

    @Test
    void topsRoundToThePalettesStepsHalvesUp() {
        assertEquals(65.0, SurfaceFit.fit(65.24, 2));
        assertEquals(65.5, SurfaceFit.fit(65.25, 2), "a half step rounds up");
        assertEquals(66.0, SurfaceFit.fit(65.75, 2));
        assertEquals(65.375, SurfaceFit.fit(65.4, 8));
        assertEquals(65.0, SurfaceFit.fit(65.49, 1));
        assertEquals(-3.5, SurfaceFit.fit(-3.6, 2), "below zero too");
    }

    @Test
    void aWholeTopIsTheSurfaceBlockWithFoundationAndClearance() {
        Map<Integer, String> stack = column(65.0, BuiltInPalettes.vanilla(), LANE, -64, 320);
        assertEquals(Map.of(62, "minecraft:stone", 63, "minecraft:stone", 64, "minecraft:polished_deepslate", 65, SurfaceFit.AIR, 66, SurfaceFit.AIR,
                67, SurfaceFit.AIR, 68, SurfaceFit.AIR, 69, SurfaceFit.AIR), stack);
    }

    @Test
    void aHalfTopIsASlabOnTheFullBlock() {
        assertEquals(Map.of(62, "minecraft:stone", 63, "minecraft:stone", 64, "minecraft:polished_deepslate",
                65, "minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]", 66, SurfaceFit.AIR, 67, SurfaceFit.AIR, 68, SurfaceFit.AIR,
                69, SurfaceFit.AIR, 70, SurfaceFit.AIR), column(65.3, BuiltInPalettes.vanilla(), LANE, -64, 320));
        assertEquals("minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]", column(65.5, BuiltInPalettes.vanilla(), DASH, -64, 320).get(65));
        assertEquals("minecraft:smooth_quartz", column(65.5, BuiltInPalettes.vanilla(), DASH, -64, 320).get(64));
        assertEquals("minecraft:polished_andesite_slab[type=bottom,waterlogged=false]",
                column(65.5, BuiltInPalettes.vanilla(), new Column(Band.KERB, false, false), -64, 320).get(65));
        assertEquals("minecraft:smooth_stone_slab[type=bottom,waterlogged=false]",
                column(65.5, BuiltInPalettes.vanilla(), new Column(Band.FOOTWAY, false, false), -64, 320).get(65));
    }

    @Test
    void eighthLayersFitToAnEighth() {
        Map<Integer, String> stack = column(65.4, LAYERS, LANE, -64, 320);
        assertEquals("test:asphalt_slope[layers=3]", stack.get(65));
        assertEquals("test:asphalt", stack.get(64));
        assertEquals(SurfaceFit.AIR, stack.get(66));
        assertEquals(9, stack.size());
    }

    @Test
    void theFoundationAndClearanceStopAtTheBuildHeight() {
        assertEquals(Map.of(63, "minecraft:stone", 64, "minecraft:polished_deepslate", 65, SurfaceFit.AIR, 66, SurfaceFit.AIR, 67, SurfaceFit.AIR),
                column(65.0, BuiltInPalettes.vanilla(), LANE, 63, 68));
        assertEquals("minecraft:polished_deepslate", column(65.0, BuiltInPalettes.vanilla(), LANE, 100, 120).get(64), "the surface is never cut off");
    }
}
