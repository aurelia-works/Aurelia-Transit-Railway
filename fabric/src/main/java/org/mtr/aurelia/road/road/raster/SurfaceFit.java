/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * Turns a column's target top into blocks (spec roads-v1 §6.2; task G-7). The top is rounded to the palette's steps
 * ({@link BlockPalette#steps()}: 1 for full blocks, 2 for slabs, 8 for layers), halves up, and raised by
 * {@link BlockPalette#raise} (B-1: the footway one layer above the road). Then, from the top down:
 * <ul>
 *   <li>a partial block ({@link BlockPalette#partial}) when the top isn't on a whole block;</li>
 *   <li>the column's full block ({@link BlockPalette#state}) as the surface, or under the partial block
 *       ({@link BlockPalette#under}: the same, but for a line, B-2, which is painted only on top);</li>
 *   <li>{@link #FOUNDATION} blocks of {@link BlockPalette#foundation()} under that;</li>
 *   <li>{@link #AIR} from the first whole block above the top, {@link #CLEARANCE} blocks high.</li>
 * </ul>
 * The foundation and the clearance are left out outside {@code [minY, maxY)} (the world's build height); the surface
 * never is, so a road whose surface leaves the world is still refused.
 */
public final class SurfaceFit {
    /** Blocks of foundation under the surface's full block (owner, 2026-10-06: G-7). */
    public static final int FOUNDATION = 2;
    /** Blocks cleared above the road's top (owner, 2026-10-06: G-7): room for MTR buses and a margin. */
    public static final int CLEARANCE = 5;
    public static final String AIR = "minecraft:air";

    private SurfaceFit() {
    }

    /** {@code top} rounded to the nearest {@code 1/steps} of a block, halves up. */
    public static double fit(double top, int steps) {
        return StrictMath.floor(top * steps + 0.5) / steps;
    }

    /** Adds column {@code (x, z)}'s blocks to {@code plan}. */
    public static void stack(BlockPlan.Builder plan, int x, int z, Column column, double top, BlockPalette palette, int minY, int maxY) {
        int steps = palette.steps();
        // The fitted top, in steps; a raised column (B-1's footway and kerb) whole steps above it.
        long units = (long) StrictMath.floor(top * steps + 0.5) + palette.raise(column);
        int whole = (int) StrictMath.floorDiv(units, (long) steps);
        int part = (int) StrictMath.floorMod(units, (long) steps);
        int surface = whole - 1; // the full block's y
        int clearFrom = whole;
        if (part != 0) {
            plan.set(x, whole, z, palette.partial(column, part));
            clearFrom = whole + 1;
        }
        plan.set(x, surface, z, part != 0 ? palette.under(column) : palette.state(column));
        for (int y = surface - FOUNDATION; y < surface; y++) {
            if (y >= minY && y < maxY) {
                plan.set(x, y, z, palette.foundation());
            }
        }
        for (int y = clearFrom; y < clearFrom + CLEARANCE; y++) {
            if (y >= minY && y < maxY) {
                plan.set(x, y, z, AIR);
            }
        }
    }
}
