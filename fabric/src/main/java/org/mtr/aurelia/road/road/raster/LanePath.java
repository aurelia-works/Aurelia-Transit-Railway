/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.ArrayList;
import java.util.List;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.RailMath;

/**
 * A whole lane (task G-6+): its MTR rail's pieces in order, each a {@link LineSegment} or an {@link ArcSegment}.
 *
 * <p>{@link #local} measures a point against the piece whose half-open range holds it; {@code s} counts from the
 * lane's start. Before the first piece and after the last, the lane carries on along its tangent, so a lane reaches
 * its piece's square ends even where diagonal lane offsets stagger the nodes (owner, 2026-10-06).
 *
 * <p>MTR's two pieces can miss each other at the join by up to 4.4 × 10⁻⁵ block (spike S5), and S-curves can put the
 * join on whole-block points. A point within {@link #JOIN} before a later piece's start belongs to that piece, so no
 * point falls between two pieces.
 */
public record LanePath(List<LaneCurve> pieces) implements LaneCurve {
    /** How far before its start a later piece still claims a point: MTR's own {@code ACCEPT_THRESHOLD}. */
    static final double JOIN = 1e-4;
    /**
     * MTR pieces shorter than this (MTR's {@code ACCEPT_THRESHOLD}) are float leftovers of an empty piece and are left
     * out: MTR's "no line before the arc" can come out 7e-15 long, or 1.3e-6 long and pointing backwards (G-6+).
     */
    private static final double TINY = JOIN;

    public LanePath {
        pieces = List.copyOf(pieces);
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("A lane needs at least one piece");
        }
        for (LaneCurve piece : pieces) {
            if (piece instanceof LanePath) {
                throw new IllegalArgumentException("A lane's pieces are lines and arcs");
            }
        }
    }

    /** The lane following {@code rail} exactly: MTR's pieces, in the rail's block-centre frame (MTR's positions − 0.5). */
    public static LanePath of(RailMath rail) {
        List<MtrRails.Piece> mtrPieces = MtrRails.pieces(rail);
        List<LaneCurve> curves = new ArrayList<>();
        for (MtrRails.Piece piece : mtrPieces) {
            if (piece.length() < TINY) {
                continue; // a float leftover of an empty piece (see TINY)
            }
            if (piece.straight()) {
                Vec2 a = MtrRails.horizontal(MtrRails.point(piece, piece.tStart()));
                Vec2 b = MtrRails.horizontal(MtrRails.point(piece, piece.tEnd()));
                curves.add(LineSegment.between(a, b));
            } else {
                // MTR's arc point is (h + r cos(t/r), k + r sin(t/r)) + 0.5, so in the block-centre frame the centre is (h, k).
                curves.add(new ArcSegment(new Vec2(piece.h(), piece.k()), piece.r(), piece.tStart() / piece.r(),
                        (piece.tEnd() - piece.tStart()) / piece.r()));
            }
        }
        if (curves.isEmpty()) {
            throw new IllegalArgumentException("MTR can't build this rail, so it has no lane");
        }
        return new LanePath(curves);
    }

    @Override
    public double length() {
        double length = 0;
        for (LaneCurve piece : this.pieces) {
            length += piece.length();
        }
        return length;
    }

    @Override
    public Vec2 point(double s) {
        double offset = 0;
        for (int i = 0; i < this.pieces.size(); i++) {
            LaneCurve piece = this.pieces.get(i);
            if (s <= offset + piece.length() || i == this.pieces.size() - 1) {
                return piece.point(s - offset);
            }
            offset += piece.length();
        }
        throw new IllegalStateException("unreachable");
    }

    public Vec2 start() {
        return startOf(this.pieces.get(0));
    }

    public Vec2 end() {
        return endOf(this.pieces.get(this.pieces.size() - 1));
    }

    public Vec2 startTangent() {
        LaneCurve first = this.pieces.get(0);
        return first instanceof ArcSegment arc ? arc.startTangent() : ((LineSegment) first).direction();
    }

    public Vec2 endTangent() {
        LaneCurve last = this.pieces.get(this.pieces.size() - 1);
        return last instanceof ArcSegment arc ? arc.endTangent() : ((LineSegment) last).direction();
    }

    @Override
    public Local local(Vec2 point) {
        double offset = 0;
        for (int i = 0; i < this.pieces.size(); i++) {
            LaneCurve piece = this.pieces.get(i);
            Local local = piece.local(point);
            double s = Rasteriser.snap(local.s());
            if (i == 0 && s < 0) {
                return along(point, start(), startTangent(), 0); // before the lane: its start tangent, carried back
            }
            if (s >= (i == 0 ? 0 : -JOIN) && s < Rasteriser.snap(piece.length())) {
                return new Local(offset + local.s(), local.d());
            }
            offset += piece.length();
        }
        return along(point, end(), endTangent(), length()); // after the lane: its end tangent, carried on
    }

    /** {@code point} measured against the straight line through {@code origin} along {@code tangent}, {@code s0} at the origin. */
    private static Local along(Vec2 point, Vec2 origin, Vec2 tangent, double s0) {
        Vec2 v = point.minus(origin);
        return new Local(s0 + v.dot(tangent), v.z() * tangent.x() - v.x() * tangent.z());
    }

    private static Vec2 startOf(LaneCurve piece) {
        return piece instanceof ArcSegment arc ? arc.start() : ((LineSegment) piece).start();
    }

    private static Vec2 endOf(LaneCurve piece) {
        return piece instanceof ArcSegment arc ? arc.end() : ((LineSegment) piece).end();
    }
}
