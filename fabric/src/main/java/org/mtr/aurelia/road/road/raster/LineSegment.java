/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.Objects;
import org.mtr.aurelia.road.geom.Vec2;

/**
 * A straight lane piece: from {@code start}, along the unit vector {@code direction}, for {@code length} blocks.
 * Keeping the direction (rather than recomputing it from two end points) keeps a heading's exact values, such as
 * east = (1, 0).
 */
public record LineSegment(Vec2 start, Vec2 direction, double length) implements LaneCurve {
    public LineSegment {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(direction, "direction");
        if (!(length > 0) || Double.isInfinite(length)) {
            throw new IllegalArgumentException("A lane piece needs a positive length, got " + length);
        }
        if (StrictMath.abs(direction.length() - 1) > 1e-12) {
            throw new IllegalArgumentException("The direction must be a unit vector, got " + direction);
        }
    }

    /** The line from {@code from} to {@code to}. */
    public static LineSegment between(Vec2 from, Vec2 to) {
        Vec2 delta = to.minus(from);
        double length = delta.length();
        return new LineSegment(from, delta.times(1 / length), length);
    }

    public Vec2 end() {
        return this.start.plus(this.direction.times(this.length));
    }

    @Override
    public Vec2 point(double s) {
        return this.start.plus(this.direction.times(s));
    }

    @Override
    public Local local(Vec2 point) {
        double dx = point.x() - this.start.x();
        double dz = point.z() - this.start.z();
        // d uses the right-hand normal (−dir.z, dir.x), so it is positive to the right (spec roads-v1 §1).
        return new Local(dx * this.direction.x() + dz * this.direction.z(), dz * this.direction.x() - dx * this.direction.z());
    }
}
