/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

/**
 * A line block's {@code chord} (task B-2; ADR-0046): where the line's centre enters and leaves the block's band square,
 * the block and {@link #MARGIN} around it, each end on a grid of points (k + ½) / {@link #GRID} along the square's edge.
 * The one place the encoding lives: the tracer ({@link LineTracer}), the blocks and their models all use it.
 * <ul>
 *   <li>{@link #PER_EDGE} = 40 points an edge (1.25 blocks at 1/32), six pairs of edges: {@link #COUNT} = 9,600 chords.</li>
 *   <li>Every block the paint touches is a line block, and each draws the line as it crosses its own square; the margin is
 *       wider than the widest line's half (the double yellow's 0.1125), so its paint always lies inside the square.</li>
 *   <li>Edges: 0 north, 1 east, 2 south, 3 west. Coordinates are the block's own, from its north-west corner: x east and
 *       z south, 0 to 1 across the block; a north or south point is measured along x, an east or west one along z.</li>
 * </ul>
 * S15 measured this encoding (round 2, V1); B-2 builds it.
 */
public final class LineChords {
    /** Grid points a block: crossings are on the 1/32 block grid. */
    public static final int GRID = 32;
    /** How far the band square reaches past the block on every side. */
    public static final double MARGIN = 0.125;
    /** Points on each edge of the band square. */
    public static final int PER_EDGE = GRID + GRID / 4;
    /** Chords: a pair of different edges and a point on each. */
    public static final int COUNT = 6 * PER_EDGE * PER_EDGE;

    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    /** The six pairs of different edges, in index order. */
    private static final int[][] PAIRS = {{NORTH, EAST}, {NORTH, SOUTH}, {NORTH, WEST}, {EAST, SOUTH}, {EAST, WEST}, {SOUTH, WEST}};

    private LineChords() {
    }

    /** The chord from point {@code ka} on edge {@code a} to point {@code kb} on edge {@code b}, either way round. */
    public static int index(int a, int ka, int b, int kb) {
        if (a == b) {
            throw new IllegalArgumentException("A chord joins two different edges, not edge " + a + " to itself");
        }
        if (a > b) {
            return index(b, kb, a, ka);
        }
        checkPoint(ka);
        checkPoint(kb);
        for (int pair = 0; pair < PAIRS.length; pair++) {
            if (PAIRS[pair][0] == a && PAIRS[pair][1] == b) {
                return pair * PER_EDGE * PER_EDGE + ka * PER_EDGE + kb;
            }
        }
        throw new IllegalArgumentException("No edges " + a + " and " + b);
    }

    /** The chord's two ends, {x1, z1, x2, z2}, in the block's own coordinates (outside 0–1 by up to the margin). */
    public static double[] ends(int chord) {
        checkChord(chord);
        int pair = chord / (PER_EDGE * PER_EDGE);
        int ka = chord % (PER_EDGE * PER_EDGE) / PER_EDGE;
        int kb = chord % PER_EDGE;
        double[] p = point(PAIRS[pair][0], ka);
        double[] q = point(PAIRS[pair][1], kb);
        return new double[] {p[0], p[1], q[0], q[1]};
    }

    /** The edges a chord joins: {a, b}, a < b. */
    public static int[] edges(int chord) {
        checkChord(chord);
        return PAIRS[chord / (PER_EDGE * PER_EDGE)].clone();
    }

    /** Point {@code k} of {@code edge}: {x, z} in the block's own coordinates. */
    public static double[] point(int edge, int k) {
        checkPoint(k);
        double t = -MARGIN + (k + 0.5) / GRID;
        return switch (edge) {
            case NORTH -> new double[] {t, -MARGIN};
            case EAST -> new double[] {1 + MARGIN, t};
            case SOUTH -> new double[] {t, 1 + MARGIN};
            case WEST -> new double[] {-MARGIN, t};
            default -> throw new IllegalArgumentException("No edge " + edge);
        };
    }

    /** The grid point nearest {@code t}, measured along an edge from the block's corner (clamped to the square's ends). */
    public static int snap(double t) {
        return (int) StrictMath.max(0, StrictMath.min(PER_EDGE - 1, StrictMath.floor((t + MARGIN) * GRID)));
    }

    private static void checkChord(int chord) {
        if (chord < 0 || chord >= COUNT) {
            throw new IllegalArgumentException("Chords go from 0 to " + (COUNT - 1) + ", not " + chord);
        }
    }

    private static void checkPoint(int k) {
        if (k < 0 || k >= PER_EDGE) {
            throw new IllegalArgumentException("Points go from 0 to " + (PER_EDGE - 1) + ", not " + k);
        }
    }
}
