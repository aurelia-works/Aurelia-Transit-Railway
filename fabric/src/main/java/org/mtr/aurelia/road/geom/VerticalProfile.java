/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

import java.util.Objects;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;

/**
 * The grade facts of an MTR rail's vertical profile (spec roads-v1 §6.1; task G-3), for the two shapes roads use:
 * <ul>
 *   <li><b>QUADRATIC</b> (MTR's default): flat at both nodes, steepest (2|H|/L) in the middle;</li>
 *   <li><b>TWO_RADII</b>: circular vertical curves of radius R at both ends and a constant grade between; R ≤ 0 means
 *       one constant grade.</li>
 * </ul>
 * L is the rail's horizontal length (MTR's {@code getLength()}), H its rise in whole blocks (MTR nodes sit on whole
 * blocks), R the requested vertical radius.
 *
 * <p>This is Stratos code, not MTR's. The heights themselves come only from the literal port: a lane's height at
 * {@code u} is its own rail's {@code getPosition(u, false).y()}. This class derives what roads need from the shapes
 * (steepest grade, the radius limit, the shortest piece for a climb), and {@code VerticalProfileTest} checks each fact
 * against the port's heights, which {@code VerticalOracleTest} checks against MTR 4.0.5. Pure; {@code StrictMath} only.
 *
 * <p><b>MTR's quirks</b> (found in G-3), reported as MTR draws them rather than as the ideal curve would be:
 * <ul>
 *   <li>A TWO_RADII radius at MTR's own limit can make MTR's square root slightly negative, and then every height of the
 *       rail is NaN: {@link #steepestGrade()} is NaN and {@link #mtrCanDraw()} false.</li>
 *   <li>A TWO_RADII radius of exactly a quarter of the rise makes MTR's angle {@code atan2(√(L²) − L, 0)}, which is
 *       {@code atan2(0, 0) = 0} (a square root of x² rounds back to x exactly): MTR then draws one constant grade,
 *       |H|/L, with no end curves.</li>
 * </ul>
 */
public record VerticalProfile(Rail.Shape shape, double length, long rise, double radius) {
    /** How finely {@link #minLength} searches, in blocks. */
    private static final double SEARCH_PRECISION = 1e-9;

    public VerticalProfile {
        Objects.requireNonNull(shape, "shape");
        if (shape == Rail.Shape.CABLE) {
            throw new IllegalArgumentException("Roads don't use MTR's CABLE shape");
        }
        if (!(length > 0) || length == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("A vertical profile needs a positive, finite length, not " + length);
        }
        if (Double.isNaN(radius)) {
            throw new IllegalArgumentException("The vertical radius can't be NaN");
        }
    }

    /**
     * The profile of a lane's rail, which rises {@code rise} blocks from its first node to its second: its shape, length
     * and (already clamped) vertical radius. The rise comes from the caller, who knows the nodes: MTR doesn't expose a
     * rail's end heights, and its heights can all be NaN (see {@link #mtrCanDraw()}).
     */
    public static VerticalProfile of(RailMath rail, long rise) {
        return new VerticalProfile(rail.getShape(), rail.getLength(), rise, rail.getVerticalRadius());
    }

    /**
     * MTR's largest vertical radius for this length and rise, {@code (L² + H²) / (4|H|)} rounded down to 0.01; infinite
     * when the rail is level. The same expression as the port's {@code getMaxVerticalRadius()}, so the same number
     * (checked in {@code VerticalProfileTest}).
     */
    public double maxRadius() {
        double height = this.rise;
        return StrictMath.floor((this.length * this.length + height * height) * 100 / StrictMath.abs(4 * height)) / 100;
    }

    /** The radius MTR uses: the requested one, clamped to {@link #maxRadius()} (MTR keeps it for any shape; only TWO_RADII uses it). */
    public double effectiveRadius() {
        return StrictMath.min(this.radius, maxRadius());
    }

    /**
     * The steepest grade along the profile, as rise over run (0.0833… for 1:12); 0 when level. Infinite for a TWO_RADII
     * climb so steep that its end curves would pass the vertical; NaN where MTR's heights are NaN (see the class
     * comment).
     * <ul>
     *   <li>QUADRATIC: 2|H|/L, at the middle.</li>
     *   <li>TWO_RADII, R ≤ 0: |H|/L everywhere.</li>
     *   <li>TWO_RADII, R &gt; 0: the constant middle grade tan θ, where the end curves meet the middle at angle θ.
     *       Tangency gives {@code L sin θ + (2R − |H|) cos θ = 2R}; with t = tan(θ/2), the root that keeps the curves
     *       within the rail is {@code t = |H| / (L + √(L² − 4R|H| + H²))}, and tan θ = 2t / (1 − t²).</li>
     * </ul>
     */
    public double steepestGrade() {
        if (this.rise == 0) {
            return 0;
        }
        double h = StrictMath.abs((double) this.rise);
        if (this.shape == Rail.Shape.QUADRATIC) {
            return 2 * h / this.length;
        }
        double r = effectiveRadius();
        if (!(r > 0)) {
            return h / this.length;
        }
        // The same discriminant as MTR's angle, in MTR's order, so it is negative exactly when MTR's heights are NaN.
        double discriminant = h * h - 4 * r * h + this.length * this.length;
        if (discriminant < 0) {
            return Double.NaN;
        }
        if (h - 4 * r == 0) {
            return h / this.length; // MTR's quirk: its angle is atan2(0, 0) = 0, so one constant grade
        }
        double t = h / (this.length + StrictMath.sqrt(discriminant));
        return t >= 1 ? Double.POSITIVE_INFINITY : 2 * t / (1 - t * t);
    }

    /** Whether MTR's heights for this profile are numbers (false only for the NaN quirk, see the class comment). */
    public boolean mtrCanDraw() {
        return !Double.isNaN(steepestGrade());
    }

    /**
     * The shortest horizontal length at which a rail of {@code shape} rising {@code rise} blocks, with vertical radius
     * {@code radius}, is nowhere steeper than {@code maxGrade} (rise over run): {@code GRADE_TOO_STEEP}'s
     * {@code {minLength}} (roads-v1 §12). 0 for a level rail.
     * <ul>
     *   <li>QUADRATIC: 2|H| / g (roads-v1 §6.1's table);</li>
     *   <li>TWO_RADII, R ≤ 0: |H| / g;</li>
     *   <li>TWO_RADII, R &gt; 0: searched to within 1e-9 block, with MTR's radius clamp and quirks (a quirk counts as
     *       too steep). The steepest grade falls as the rail gets longer, apart from tiny steps where the clamped radius
     *       moves up by MTR's 0.01, so the answer is the shortest length to within those steps.</li>
     * </ul>
     */
    public static double minLength(Rail.Shape shape, long rise, double radius, double maxGrade) {
        Objects.requireNonNull(shape, "shape");
        if (!(maxGrade > 0) || maxGrade == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("A grade limit must be positive and finite, not " + maxGrade);
        }
        if (shape == Rail.Shape.CABLE) {
            throw new IllegalArgumentException("Roads don't use MTR's CABLE shape");
        }
        if (rise == 0) {
            return 0;
        }
        double h = StrictMath.abs((double) rise);
        if (shape == Rail.Shape.QUADRATIC) {
            return 2 * h / maxGrade;
        }
        if (!(radius > 0)) {
            return h / maxGrade;
        }
        // Somewhere is at least as steep as the average, so h / g is too short (or just enough); double until long enough.
        double low = h / maxGrade;
        double high = StrictMath.max(2 * low, 1);
        while (tooSteep(new VerticalProfile(shape, high, rise, radius), maxGrade)) {
            low = high;
            high *= 2;
        }
        while (high - low > SEARCH_PRECISION * StrictMath.max(1, high)) {
            double middle = low + (high - low) / 2;
            if (tooSteep(new VerticalProfile(shape, middle, rise, radius), maxGrade)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    /** Steeper than {@code maxGrade}, or a quirk MTR can't draw well (NaN counts as too steep). */
    private static boolean tooSteep(VerticalProfile profile, double maxGrade) {
        return !(profile.steepestGrade() <= maxGrade);
    }
}
