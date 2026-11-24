/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.Objects;
import org.mtr.aurelia.road.geom.Vec2;

/**
 * A curved lane piece (task G-6+): the arc of radius {@code radius} around {@code centre}, from {@code startAngle}
 * through {@code sweep} radians. Angles are {@code atan2(z, x)} in the block-centre frame (x east, z south), so a
 * positive sweep turns clockwise seen from above: a right-hand bend, with the centre on the right.
 *
 * <p>{@link #local} measures {@code s} as arc length from the start, around the arc's middle, so a point on the start's
 * radial line is at exactly 0 and points before the start are negative; {@code d} is positive to the right, like
 * {@link LineSegment}.
 */
public record ArcSegment(Vec2 centre, double radius, double startAngle, double sweep) implements LaneCurve {
    private static final double TURN = 2 * StrictMath.PI;

    public ArcSegment {
        Objects.requireNonNull(centre, "centre");
        if (!(radius > 0) || Double.isInfinite(radius)) {
            throw new IllegalArgumentException("An arc needs a positive, finite radius, not " + radius);
        }
        if (!(StrictMath.abs(sweep) > 0) || !(StrictMath.abs(sweep) < TURN) || !Double.isFinite(startAngle)) {
            throw new IllegalArgumentException("An arc's sweep must be non-zero and less than a full turn, not " + sweep);
        }
    }

    @Override
    public double length() {
        return this.radius * StrictMath.abs(this.sweep);
    }

    @Override
    public Vec2 point(double s) {
        return at(this.startAngle + StrictMath.signum(this.sweep) * s / this.radius);
    }

    public Vec2 start() {
        return at(this.startAngle);
    }

    public Vec2 end() {
        return at(this.startAngle + this.sweep);
    }

    /** The direction of travel at the start. */
    public Vec2 startTangent() {
        return tangent(this.startAngle);
    }

    /** The direction of travel at the end. */
    public Vec2 endTangent() {
        return tangent(this.startAngle + this.sweep);
    }

    @Override
    public Local local(Vec2 point) {
        double dx = point.x() - this.centre.x();
        double dz = point.z() - this.centre.z();
        double angle = StrictMath.atan2(dz, dx);
        double middle = this.startAngle + this.sweep / 2;
        double fromMiddle = normalise(angle - middle); // in [−π, π)
        double travelled = StrictMath.signum(this.sweep) * fromMiddle + StrictMath.abs(this.sweep) / 2;
        double distance = StrictMath.sqrt(dx * dx + dz * dz);
        // Right-hand bend: the centre is on the right, so a point nearer to it is to the right (d > 0).
        double d = this.sweep > 0 ? this.radius - distance : distance - this.radius;
        return new Local(travelled * this.radius, d);
    }

    private Vec2 at(double angle) {
        return new Vec2(this.centre.x() + this.radius * StrictMath.cos(angle), this.centre.z() + this.radius * StrictMath.sin(angle));
    }

    private Vec2 tangent(double angle) {
        double sign = StrictMath.signum(this.sweep);
        return new Vec2(-sign * StrictMath.sin(angle), sign * StrictMath.cos(angle));
    }

    /** {@code angle} moved into [−π, π) by whole turns. */
    static double normalise(double angle) {
        return angle - TURN * StrictMath.floor((angle + StrictMath.PI) / TURN);
    }
}
