/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

/**
 * One of MTR's 16 directions (spec roads-v1 §1): index {@code i} is the angle {@code θ = i · 22.5°}, with
 * {@code E} (+x) at 0 and {@code S} (+z) at 4, turning clockwise seen from above.
 *
 * <p>The cosines and sines are exactly 0 and ±1 at the four cardinals, so a point that lies exactly on a block
 * edge never drifts into the wrong block; elsewhere they are {@code StrictMath}'s values, the same as MTR's
 * {@code Angle}. A heading never draws a curve: every curve a lane or rail follows comes from the literal
 * RailMath port (G-2), which uses MTR's own angle values.
 */
public enum Heading {
    E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW, N, NNE, NE, ENE;

    /** The number of headings. */
    public static final int COUNT = 16;

    /** The angle between neighbouring headings, in degrees. */
    public static final double STEP_DEGREES = 22.5;

    private static final Heading[] VALUES = values();

    private final double cos;
    private final double sin;

    Heading() {
        switch (ordinal()) {
            case 0 -> { this.cos = 1; this.sin = 0; }
            case 4 -> { this.cos = 0; this.sin = 1; }
            case 8 -> { this.cos = -1; this.sin = 0; }
            case 12 -> { this.cos = 0; this.sin = -1; }
            default -> {
                // As MTR's Angle does: normalise into [−180°, 180°) first (202.5° becomes −157.5°), so the values
                // are MTR's bit for bit, not 1 ulp off (checked in MtrRailsTest).
                double degrees = ordinal() * STEP_DEGREES;
                double radians = StrictMath.toRadians(degrees >= 180 ? degrees - 360 : degrees);
                this.cos = StrictMath.cos(radians);
                this.sin = StrictMath.sin(radians);
            }
        }
    }

    /** The heading with index {@code index}, taken mod 16: {@code of(16)} is {@code E}, {@code of(-1)} is {@code ENE}. */
    public static Heading of(int index) {
        return VALUES[StrictMath.floorMod(index, COUNT)];
    }

    /** The index, 0 to 15. */
    public int index() {
        return ordinal();
    }

    /** The angle θ in degrees, 0 to 337.5. */
    public double degrees() {
        return ordinal() * STEP_DEGREES;
    }

    /** The angle θ in radians. */
    public double radians() {
        return StrictMath.toRadians(degrees());
    }

    public double cos() {
        return this.cos;
    }

    public double sin() {
        return this.sin;
    }

    /** The unit vector along the heading, {@code (cos θ, sin θ)}. */
    public Vec2 tangent() {
        return new Vec2(this.cos, this.sin);
    }

    /** The right-hand normal {@code (−sin θ, cos θ)}: it points to the driver's right (facing east, right is south). */
    public Vec2 normal() {
        return new Vec2(-this.sin, this.cos);
    }

    /** The heading {@code steps} further clockwise (anticlockwise when negative). */
    public Heading rotate(int steps) {
        return of(ordinal() + steps);
    }

    /** The heading pointing the other way (8 steps round). */
    public Heading opposite() {
        return rotate(COUNT / 2);
    }

    /**
     * The signed change from this heading to {@code other}, in steps from −7 to +8: positive is clockwise. A turn
     * of exactly 8 steps (the opposite heading) is always +8, never −8.
     */
    public int stepsTo(Heading other) {
        int steps = StrictMath.floorMod(other.ordinal() - ordinal(), COUNT);
        return steps > COUNT / 2 ? steps - COUNT : steps;
    }

    /** The right-hand-traffic mirror (spec roads-v1 §11.4): index {@code i → (16 − i) mod 16}. */
    public Heading mirrored() {
        return of(-ordinal());
    }

    /** True for E, S, W and N. */
    public boolean isCardinal() {
        return ordinal() % 4 == 0;
    }

    /** True for the 45° diagonals SE, SW, NW and NE (MTR's "45° family": headings 2, 6, 10, 14). */
    public boolean isDiagonal() {
        return ordinal() % 4 == 2;
    }

    /** True for the eight headings 22.5° off a cardinal (odd indices). */
    public boolean isIntermediate() {
        return ordinal() % 2 == 1;
    }

    /** True when {@code other} is this heading or its opposite. */
    public boolean isParallel(Heading other) {
        return other == this || other == opposite();
    }

    /**
     * Minecraft's yaw for a player facing this heading (spec roads-v1 §1): {@code θ − 90°}, wrapped into
     * [−180, 180) as Minecraft wraps degrees. E is −90, S is 0, W is 90 and N is −180.
     */
    public double minecraftYaw() {
        double yaw = degrees() - 90;
        return yaw - 360 * StrictMath.floor((yaw + 180) / 360);
    }
}
