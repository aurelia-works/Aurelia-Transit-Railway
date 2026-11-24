/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

/**
 * A horizontal point or vector in the block-centre frame (see package-info): x east, z south, with each block's
 * centre on whole numbers. Both parts must be finite; {@code −0.0} is stored as {@code 0.0}, so equal vectors
 * compare equal.
 */
public record Vec2(double x, double z) {
    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vec2 needs finite numbers, got (" + x + ", " + z + ")");
        }
        x += 0.0; // −0.0 + 0.0 is +0.0
        z += 0.0;
    }

    public Vec2 plus(Vec2 other) {
        return new Vec2(this.x + other.x, this.z + other.z);
    }

    public Vec2 minus(Vec2 other) {
        return new Vec2(this.x - other.x, this.z - other.z);
    }

    public Vec2 times(double factor) {
        return new Vec2(this.x * factor, this.z * factor);
    }

    public double dot(Vec2 other) {
        return this.x * other.x + this.z * other.z;
    }

    /** {@code x·other.z − z·other.x}: positive when {@code other} points clockwise of this vector, seen from above. */
    public double cross(Vec2 other) {
        return this.x * other.z - this.z * other.x;
    }

    public double length() {
        return StrictMath.sqrt(this.x * this.x + this.z * this.z);
    }

    public double distanceTo(Vec2 other) {
        return minus(other).length();
    }

    /** Turned 90° clockwise seen from above: {@code (x, z) → (−z, x)}, so east becomes south. */
    public Vec2 rotateQuarterClockwise() {
        return new Vec2(-this.z, this.x);
    }

    /** Reflected in the x axis, {@code (x, z) → (x, −z)}: the right-hand-traffic mirror (spec roads-v1 §11.4). */
    public Vec2 mirrored() {
        return new Vec2(this.x, -this.z);
    }

    /** The world x coordinate: the frame coordinate + 0.5 (MTR's +0.5, spec roads-v1 §4.1). */
    public double worldX() {
        return this.x + 0.5;
    }

    /** The world z coordinate: the frame coordinate + 0.5. */
    public double worldZ() {
        return this.z + 0.5;
    }

    /**
     * The block containing this point. Block {@code b} covers {@code [b − 0.5, b + 0.5)}, so a point exactly on
     * an edge belongs to the block with the larger index, and negative coordinates round down, never towards 0.
     */
    public BlockXZ block() {
        return new BlockXZ(blockIndex(this.x), blockIndex(this.z));
    }

    /**
     * {@code floor(coordinate + 0.5)}, computed without the addition: for the number just below 0.5 that sum rounds
     * up to exactly 1.0 and picks the wrong block. {@code coordinate − floor(coordinate)} is always exact here.
     */
    private static int blockIndex(double coordinate) {
        double floor = StrictMath.floor(coordinate);
        double index = coordinate - floor >= 0.5 ? floor + 1 : floor;
        if (index < Integer.MIN_VALUE || index > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Coordinate " + coordinate + " is outside the block range");
        }
        return (int) index;
    }
}
