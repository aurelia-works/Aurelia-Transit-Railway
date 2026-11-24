/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

import java.util.ArrayList;
import java.util.List;

/**
 * A line block's top face (task B-2; ADR-0046), cut along its paint's edges into asphalt and paint pieces that never
 * overlap (overlapping faces flicker under shader packs). Each paint edge is the chord moved square to it by the stripe's
 * offset ± half its width, so the stripes are their real width at any angle. The chord may pass beside the block (the
 * paint only grazes it), so a top may be cut by one paint edge only.
 *
 * <p>Every piece is a convex polygon in the block's own coordinates ({@link LineChords}), its corners in the order
 * Minecraft's up faces use: north-west, south-west, south-east, north-east for a whole face. Asphalt pieces come first,
 * the biggest first: Distant Horizons colours a block from its first upward face (research 13 §3.4).
 */
public final class LineTop {
    private static final double EPSILON = 1e-9;

    /** A convex piece of the top: paint or asphalt, its corners' x and z. */
    public record Piece(boolean paint, double[] x, double[] z) {
        public double area() {
            double twice = 0;
            for (int i = 0; i < this.x.length; i++) {
                int j = (i + 1) % this.x.length;
                twice += this.x[i] * this.z[j] - this.x[j] * this.z[i];
            }
            return StrictMath.abs(twice) / 2;
        }
    }

    private LineTop() {
    }

    /** The top's pieces for {@code kind} painted along {@code chord}. */
    public static List<Piece> pieces(LineKind kind, int chord) {
        double[] ends = LineChords.ends(chord);
        double dx = ends[2] - ends[0];
        double dz = ends[3] - ends[1];
        double length = StrictMath.hypot(dx, dz);
        // The unit normal to the chord's left (x east, z south): the side a stripe's positive offset is on.
        double nx = dz / length;
        double nz = -dx / length;
        List<Double> cuts = new ArrayList<>();
        for (LineKind.Stripe stripe : kind.stripes()) {
            cuts.add(stripe.offset() - stripe.width() / 2);
            cuts.add(stripe.offset() + stripe.width() / 2);
        }
        cuts.sort(Double::compare);
        List<double[]> lines = new ArrayList<>();
        for (double c : cuts) {
            lines.add(new double[] {ends[0] + c * nx, ends[1] + c * nz, ends[2] + c * nx, ends[3] + c * nz});
        }
        List<Piece> asphalt = new ArrayList<>();
        List<Piece> painted = new ArrayList<>();
        for (int i = 0; i <= lines.size(); i++) {
            // Between cut i − 1 and cut i: paint between a stripe's two cuts (odd i), asphalt elsewhere.
            double[] low = i == 0 ? null : lines.get(i - 1);
            double[] high = i == lines.size() ? null : lines.get(i);
            add(i % 2 == 1 ? painted : asphalt, i % 2 == 1, low, high);
        }
        asphalt.sort((a, b) -> Double.compare(b.area(), a.area()));
        List<Piece> all = new ArrayList<>(asphalt);
        all.addAll(painted);
        return all;
    }

    /** The part of the block's top left of {@code low} and right of {@code high} (each a line {x1, z1, x2, z2}, or null). */
    private static void add(List<Piece> out, boolean paint, double[] low, double[] high) {
        List<double[]> polygon = new ArrayList<>(List.of(new double[] {0, 0}, new double[] {0, 1}, new double[] {1, 1}, new double[] {1, 0}));
        if (low != null) {
            polygon = clip(polygon, low, 1);
        }
        if (high != null) {
            polygon = clip(polygon, high, -1);
        }
        if (polygon.size() < 3) {
            return;
        }
        double[] x = new double[polygon.size()];
        double[] z = new double[polygon.size()];
        for (int i = 0; i < polygon.size(); i++) {
            x[i] = polygon.get(i)[0];
            z[i] = polygon.get(i)[1];
        }
        Piece piece = new Piece(paint, x, z);
        if (piece.area() > EPSILON) {
            out.add(piece);
        }
    }

    /** Sutherland–Hodgman: keeps the part where {@code sign} × (the side of {@code line} a point is on, left positive) ≥ 0; drops repeated corners. */
    private static List<double[]> clip(List<double[]> polygon, double[] line, int sign) {
        double lx = line[3] - line[1];
        double lz = -(line[2] - line[0]);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            double[] p = polygon.get(i);
            double[] q = polygon.get((i + 1) % polygon.size());
            double fp = sign * ((p[0] - line[0]) * lx + (p[1] - line[1]) * lz);
            double fq = sign * ((q[0] - line[0]) * lx + (q[1] - line[1]) * lz);
            if (fp >= 0) {
                keep(out, p);
            }
            if ((fp < 0 && fq > 0) || (fp > 0 && fq < 0)) {
                double t = fp / (fp - fq);
                keep(out, new double[] {p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])});
            }
        }
        if (out.size() > 1 && same(out.get(0), out.get(out.size() - 1))) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static void keep(List<double[]> out, double[] p) {
        if (out.isEmpty() || !same(out.get(out.size() - 1), p)) {
            out.add(p);
        }
    }

    private static boolean same(double[] a, double[] b) {
        return StrictMath.abs(a[0] - b[0]) < EPSILON && StrictMath.abs(a[1] - b[1]) < EPSILON;
    }
}
