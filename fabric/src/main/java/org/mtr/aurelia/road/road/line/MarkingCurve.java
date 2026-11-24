/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

import java.util.Objects;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.road.raster.LaneCurve;

/**
 * The curve a painted line follows (task B-2): midway between the two lane curves it lies between, the same middle the
 * rasteriser centres the sections between two lanes on, moved {@code offset} blocks to the right for a section off the
 * middle. In a piece's frame (relative to its anchor, the block-centre frame).
 * <ul>
 *   <li>A point {@code s} along it is the middle of the points the same fraction along each lane: on a bend, the two
 *       lanes' points at the same angle. Its length is the mean of the lanes', so {@code s} is measured as the rasteriser
 *       measures dashes ((left s + right s) / 2) and as {@code RoadPiece.phaseAfter} carries them on.</li>
 *   <li>Before its start and past its end it carries on straight along its end tangents, so the tracer finds where the
 *       line leaves the road's first and last blocks (a joined road carries on the same way).</li>
 * </ul>
 */
public final class MarkingCurve {
    /** The step for the tangents at the ends and the normal for an offset, in blocks. */
    private static final double H = 1e-3;

    private final LaneCurve left;
    private final LaneCurve right;
    private final double offset;
    private final double length;
    private final Vec2 start;
    private final Vec2 startTangent;
    private final Vec2 end;
    private final Vec2 endTangent;

    /** The line between lane curves {@code left} and {@code right} (both running from the piece's start), {@code offset} to the right of their middle. */
    public MarkingCurve(LaneCurve left, LaneCurve right, double offset) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        if (!Double.isFinite(offset)) {
            throw new IllegalArgumentException("offset must be finite");
        }
        this.offset = offset;
        this.length = (left.length() + right.length()) / 2;
        if (!(this.length > 2 * H)) {
            throw new IllegalArgumentException("A marking needs some length, not " + this.length);
        }
        this.start = inside(0);
        this.end = inside(this.length);
        this.startTangent = unit(inside(H).minus(this.start));
        this.endTangent = unit(this.end.minus(inside(this.length - H)));
    }

    /** Its length in blocks: the mean of the two lanes'. */
    public double length() {
        return this.length;
    }

    /** The point {@code s} blocks along it; before 0 and past {@link #length()}, along its end tangents. */
    public Vec2 at(double s) {
        if (s < 0) {
            return this.start.plus(this.startTangent.times(s));
        }
        if (s > this.length) {
            return this.end.plus(this.endTangent.times(s - this.length));
        }
        return inside(s);
    }

    /** The unit tangent at {@code s} (the end tangents outside the curve). */
    public Vec2 tangent(double s) {
        if (s <= H) {
            return s < 0 ? this.startTangent : unit(inside(s + H).minus(inside(StrictMath.max(0, s - H))));
        }
        if (s >= this.length - H) {
            return s > this.length ? this.endTangent : unit(inside(StrictMath.min(this.length, s + H)).minus(inside(s - H)));
        }
        return unit(inside(s + H).minus(inside(s - H)));
    }

    private Vec2 inside(double s) {
        double t = StrictMath.max(0, StrictMath.min(1, s / this.length));
        Vec2 a = this.left.point(StrictMath.min(this.left.length(), t * this.left.length()));
        Vec2 b = this.right.point(StrictMath.min(this.right.length(), t * this.right.length()));
        Vec2 middle = a.plus(b).times(0.5);
        if (this.offset == 0) {
            return middle;
        }
        Vec2 ahead = this.left.point(StrictMath.min(this.left.length(), (t + H / this.length) * this.left.length()))
                .plus(this.right.point(StrictMath.min(this.right.length(), (t + H / this.length) * this.right.length()))).times(0.5);
        Vec2 behind = this.left.point(StrictMath.max(0, (t - H / this.length) * this.left.length()))
                .plus(this.right.point(StrictMath.max(0, (t - H / this.length) * this.right.length()))).times(0.5);
        return middle.plus(unit(ahead.minus(behind)).rotateQuarterClockwise().times(this.offset));
    }

    private static Vec2 unit(Vec2 v) {
        double length = v.length();
        if (!(length > 0)) {
            throw new IllegalArgumentException("A marking's curve has no direction here");
        }
        return v.times(1 / length);
    }
}
