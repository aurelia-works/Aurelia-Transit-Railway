/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

/**
 * The start frame of spec roads-v1 §4.2: positions measured from {@code origin}, along {@code heading} ({@code s})
 * and sideways from it ({@code d}, positive to the driver's right). MTR calls these {@code deltaSide} and
 * {@code deltaForward}; the RailMath port (G-2) keeps MTR's names and does its own maths.
 */
public record Frame(Vec2 origin, Heading heading) {
    /** {@code s = Δx·cos θ + Δz·sin θ}: how far {@code point} is along the heading. */
    public double along(Vec2 point) {
        double dx = point.x() - this.origin.x();
        double dz = point.z() - this.origin.z();
        return dx * this.heading.cos() + dz * this.heading.sin();
    }

    /** {@code d = Δz·cos θ − Δx·sin θ}: how far {@code point} is to the right of the heading's line. */
    public double side(Vec2 point) {
        double dx = point.x() - this.origin.x();
        double dz = point.z() - this.origin.z();
        return dz * this.heading.cos() - dx * this.heading.sin();
    }

    /** The point {@code along} ahead and {@code side} to the right: {@code origin + along·tangent + side·normal}. */
    public Vec2 point(double along, double side) {
        double cos = this.heading.cos();
        double sin = this.heading.sin();
        return new Vec2(this.origin.x() + along * cos - side * sin, this.origin.z() + along * sin + side * cos);
    }
}
