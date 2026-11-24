/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Vec2;

/**
 * Traces a painted line to its blocks (task B-2; ADR-0046, as spike S15's {@code traceBand}): every block the paint
 * touches, and for each the chord where the line's centre enters and leaves its band square ({@link LineChords}).
 * <ul>
 *   <li>The curve is walked once in {@link #STEP}s, from {@link #EXTEND} before its start to as far past its end (a
 *       {@link MarkingCurve} carries on straight there), so the road's first and last blocks get both crossings.</li>
 *   <li>A block is touched where the paint ({@link LineKind#halfExtent()} either side of the centre) comes within it at
 *       a step; it can miss a block it grazes by less than half a step.</li>
 *   <li>Where the centre crosses a band square's edge is found by bisection, then snapped (2⁻²⁰, then the 1/32 grid),
 *       so the same road gives the same chords wherever it is (CLAUDE.md: "same input, different place").</li>
 *   <li>A block whose crossings don't give a chord (none, or both on one edge: only a curve far tighter than any road's
 *       could do that) takes the tangent at the middle of its stretch instead; if that misses its square, the block is
 *       left out. The tracer never throws for a road's shape.</li>
 *   <li>Each block also gets the stretch of the curve whose paint touches it ({@code from} to {@code to}): dashes are
 *       whole blocks (ADR-0044), and a block is in a dash when most of its stretch is ({@code Rasteriser.withLines}), so
 *       two blocks the line runs between, whose stretches are the same stretch of line, agree.</li>
 * </ul>
 * Coordinates are a piece's (the block-centre frame, relative to its anchor): block {@code b} covers
 * {@code [b − ½, b + ½)}. Like the rasteriser, it stops when its thread is interrupted (the plan time limit).
 */
public final class LineTracer {
    /** How far the curve is walked past each end, in blocks: more than a band square's width. */
    public static final double EXTEND = 1.5;
    /** The walk's step, in blocks. */
    public static final double STEP = 0.01;
    private static final double SNAP = 0x1p20;
    private static final int BISECTIONS = 48;

    /** A traced block: where it is, its chord, and the stretch of the curve whose paint touches it. */
    public record Traced(BlockXZ block, int chord, double from, double to) {
    }

    private static final class Track {
        boolean touched;
        double from = Double.NaN;
        double to;
        boolean entered;
        double entry;
        double exit = Double.NaN;
    }

    private LineTracer() {
    }

    /** The blocks {@code kind} painted along {@code curve} touches, sorted by z, then x. */
    public static List<Traced> trace(MarkingCurve curve, LineKind kind) {
        double reach = kind.halfExtent();
        double from = -EXTEND;
        double to = curve.length() + EXTEND;
        int steps = (int) StrictMath.ceil((to - from) / STEP);
        Map<BlockXZ, Track> tracks = new LinkedHashMap<>();
        Set<BlockXZ> wasInside = Set.of();
        Set<BlockXZ> wasTouched = Set.of();
        double previous = from;
        for (int k = 0; k <= steps; k++) {
            if ((k & 1023) == 0 && Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Stopped by the plan time limit");
            }
            double s = k == steps ? to : from + k * STEP;
            Vec2 p = curve.at(s);
            int cx = (int) StrictMath.rint(p.x());
            int cz = (int) StrictMath.rint(p.z());
            Set<BlockXZ> touched = new HashSet<>(9);
            for (int bx = cx - 1; bx <= cx + 1; bx++) {
                for (int bz = cz - 1; bz <= cz + 1; bz++) {
                    if (touches(p, bx, bz, reach)) {
                        touched.add(new BlockXZ(bx, bz));
                    }
                }
            }
            // The stretch whose paint touches each block: its ends found exactly, so a block centred on a dash's end has
            // exactly half its stretch in the dash (Rasteriser.inDash settles that tie by the block's middle).
            for (BlockXZ block : touched) {
                if (!wasTouched.contains(block)) {
                    Track track = tracks.computeIfAbsent(block, b -> new Track());
                    if (Double.isNaN(track.from)) {
                        track.touched = true;
                        track.from = k == 0 ? s : touchEdge(curve, previous, s, block, reach);
                    }
                }
            }
            for (BlockXZ block : wasTouched) {
                if (!touched.contains(block)) {
                    tracks.get(block).to = touchEdge(curve, s, previous, block, reach);
                }
            }
            if (k == steps) {
                for (BlockXZ block : touched) {
                    tracks.get(block).to = s;
                }
            }
            wasTouched = touched;
            Set<BlockXZ> inside = new HashSet<>(4);
            for (int bx = cx - 1; bx <= cx + 1; bx++) {
                for (int bz = cz - 1; bz <= cz + 1; bz++) {
                    if (inSquare(p, bx, bz)) {
                        inside.add(new BlockXZ(bx, bz));
                    }
                }
            }
            for (BlockXZ block : inside) {
                if (!wasInside.contains(block)) {
                    Track track = tracks.computeIfAbsent(block, b -> new Track());
                    if (k > 0 && !track.entered) {
                        track.entered = true;
                        track.entry = crossing(curve, previous, s, block);
                    }
                }
            }
            for (BlockXZ block : wasInside) {
                if (!inside.contains(block)) {
                    tracks.get(block).exit = crossing(curve, s, previous, block);
                }
            }
            wasInside = inside;
            previous = s;
        }
        List<Traced> traced = new ArrayList<>();
        for (Map.Entry<BlockXZ, Track> entry : tracks.entrySet()) {
            Track track = entry.getValue();
            if (!track.touched) {
                continue;
            }
            BlockXZ block = entry.getKey();
            int chord = -1;
            if (track.entered && !Double.isNaN(track.exit)) {
                int[] a = squarePoint(curve.at(track.entry), block);
                int[] b = squarePoint(curve.at(track.exit), block);
                if (a[0] != b[0]) {
                    chord = LineChords.index(a[0], a[1], b[0], b[1]);
                }
            }
            if (chord < 0) {
                chord = tangentChord(curve, (track.from + track.to) / 2, block);
            }
            if (chord >= 0) {
                traced.add(new Traced(block, chord, track.from, track.to));
            }
        }
        traced.sort((a, b) -> a.block().z() != b.block().z() ? Integer.compare(a.block().z(), b.block().z()) : Integer.compare(a.block().x(), b.block().x()));
        return traced;
    }

    /** Whether the paint around {@code p} (within {@code reach} of it) comes into block {@code (bx, bz)}. */
    private static boolean touches(Vec2 p, int bx, int bz, double reach) {
        double dx = StrictMath.max(0, StrictMath.abs(p.x() - bx) - 0.5);
        double dz = StrictMath.max(0, StrictMath.abs(p.z() - bz) - 0.5);
        return dx * dx + dz * dz < reach * reach;
    }

    /** Where between {@code out} (paint not touching the block) and {@code in} (touching) the touching starts or stops, snapped. */
    private static double touchEdge(MarkingCurve curve, double out, double in, BlockXZ block, double reach) {
        for (int i = 0; i < BISECTIONS; i++) {
            double mid = (out + in) / 2;
            if (touches(curve.at(mid), block.x(), block.z(), reach)) {
                in = mid;
            } else {
                out = mid;
            }
        }
        return snap((out + in) / 2);
    }

    /** Whether {@code p} is inside block {@code (bx, bz)}'s band square (strictly: a point on its edge is outside). */
    private static boolean inSquare(Vec2 p, int bx, int bz) {
        return StrictMath.abs(p.x() - bx) < 0.5 + LineChords.MARGIN && StrictMath.abs(p.z() - bz) < 0.5 + LineChords.MARGIN;
    }

    /** Where between {@code out} (outside the block's band square) and {@code in} (inside) the curve crosses its edge. */
    private static double crossing(MarkingCurve curve, double out, double in, BlockXZ block) {
        for (int i = 0; i < BISECTIONS; i++) {
            double mid = (out + in) / 2;
            if (inSquare(curve.at(mid), block.x(), block.z())) {
                in = mid;
            } else {
                out = mid;
            }
        }
        return (out + in) / 2;
    }

    /** The band square's edge a point on (or next to) it lies on, and the grid point there: {edge, k}. */
    private static int[] squarePoint(Vec2 p, BlockXZ block) {
        double m = LineChords.MARGIN;
        double u = snap(p.x() - (block.x() - 0.5)); // the block's own coordinates, from its north-west corner
        double v = snap(p.z() - (block.z() - 0.5));
        double[] gaps = {StrictMath.abs(v + m), StrictMath.abs(u - 1 - m), StrictMath.abs(v - 1 - m), StrictMath.abs(u + m)};
        int edge = 0;
        for (int i = 1; i < 4; i++) {
            if (gaps[i] < gaps[edge]) {
                edge = i;
            }
        }
        double t = edge == LineChords.NORTH || edge == LineChords.SOUTH ? u : v;
        return new int[] {edge, LineChords.snap(t)};
    }

    /**
     * The chord of the tangent at {@code s} through the block's band square, or −1 if it misses it: for a block whose
     * crossings give none.
     */
    private static int tangentChord(MarkingCurve curve, double s, BlockXZ block) {
        Vec2 p = curve.at(s);
        Vec2 t = curve.tangent(s);
        double m = LineChords.MARGIN;
        double u = p.x() - (block.x() - 0.5);
        double v = p.z() - (block.z() - 0.5);
        // Where the line u + λ t.x, v + λ t.z meets each edge of the square [−m, 1 + m]².
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        int[] loPoint = null;
        int[] hiPoint = null;
        double[][] edges = {{LineChords.NORTH, -m}, {LineChords.EAST, 1 + m}, {LineChords.SOUTH, 1 + m}, {LineChords.WEST, -m}};
        for (double[] edge : edges) {
            int e = (int) edge[0];
            boolean alongX = e == LineChords.NORTH || e == LineChords.SOUTH;
            double towards = alongX ? t.z() : t.x();
            if (StrictMath.abs(towards) < 1e-12) {
                continue;
            }
            double lambda = (edge[1] - (alongX ? v : u)) / towards;
            double along = alongX ? u + lambda * t.x() : v + lambda * t.z();
            if (along < -m || along > 1 + m) {
                continue;
            }
            int[] point = {e, LineChords.snap(snap(along))};
            if (lambda < lo) {
                lo = lambda;
                loPoint = point;
            }
            if (lambda > hi) {
                hi = lambda;
                hiPoint = point;
            }
        }
        if (loPoint == null || hiPoint == null || loPoint[0] == hiPoint[0]) {
            return -1;
        }
        return LineChords.index(loPoint[0], loPoint[1], hiPoint[0], hiPoint[1]);
    }

    private static double snap(double value) {
        return StrictMath.rint(value * SNAP) / SNAP;
    }
}
