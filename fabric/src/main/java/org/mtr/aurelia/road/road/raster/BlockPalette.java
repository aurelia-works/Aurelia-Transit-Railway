/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import org.mtr.aurelia.road.road.LineMarking;

/**
 * Gives each rasterised column its block states (strings, spec mod-suite-v1 §4.1). From R-9 the palettes are data files
 * (spec roads-v1 §8; {@code road.palette.Palette}): candidates per role, with a vanilla fallback.
 *
 * <p>From G-7 a palette also says how finely its surface can follow a slope ({@link #steps()}), gives the partial block
 * for each step ({@link #partial}), and the foundation under the road.
 */
public interface BlockPalette {
    /** The DataVersion the state strings belong to (3465 for Minecraft 1.20.1). */
    int dataVersion();

    /** The full block for {@code column}, such as {@code minecraft:polished_deepslate}. */
    String state(Column column);

    /** Steps per block the surface can stand at: 1 (full blocks only), 2 (slabs) or 8 (layers). */
    default int steps() {
        return 1;
    }

    /** The partial block for {@code column} reaching {@code part}/{@link #steps()} of a block, {@code 0 < part < steps()}. */
    default String partial(Column column, int part) {
        throw new IllegalArgumentException("This palette has no partial blocks");
    }

    /**
     * Steps of {@link #steps()} that {@code column} stands above the road's surface (B-1: the Stratos UK footway and kerb,
     * one layer up); 0 for most.
     */
    default int raise(Column column) {
        return 0;
    }

    /**
     * Whether its kerb turns to face the road (B-1), so the plan works out where each kerb column meets it
     * ({@link Column#kerb()}, {@link KerbEdge}).
     */
    default boolean kerbEdges() {
        return false;
    }

    /**
     * Whether its {@code colour} marking is drawn with line blocks (B-2; ADR-0046): the plan then traces each such line to
     * every block its paint touches ({@link Column#chord()}), instead of painting whole columns.
     */
    default boolean lines(LineMarking.Colour colour) {
        return false;
    }

    /**
     * The full block under {@code column}'s partial one: its {@link #state}, but plain where a line is painted only on top
     * (B-2: a line block's paint is its top's, so the block hidden under it is the column's own surface).
     */
    default String under(Column column) {
        return state(column);
    }

    /** The block under the surface (roads-v1 §8, role {@code foundation}). */
    String foundation();

    /**
     * The lane-node marker (roads-v1 §8 and §9, role {@code node_marker}; R-6): set in the surface block under each node's
     * spot.
     */
    String nodeMarker();
}
