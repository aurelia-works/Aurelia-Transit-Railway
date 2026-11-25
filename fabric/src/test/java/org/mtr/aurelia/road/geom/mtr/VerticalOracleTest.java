/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom.mtr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Heights: the RailMath port against the oracle, MTR 4.0.5's own {@code org.mtr.core.data.RailMath}, for all three
 * vertical shapes (task G-3, spec roads-v1 §6.1). G-2's oracle covers QUADRATIC positions for rises of 0 to 20; this one
 * builds every rail as QUADRATIC, CABLE and TWO_RADII at six vertical radii (from negative to beyond MTR's limit),
 * with rises of 1 to 100 blocks up and down, and compares heights only.
 *
 * <p>A height depends only on how far along the rail a point is, not on which of the rail's two pieces it is on, so
 * every height must be within 1e-6 (no join tolerance, unlike G-2's positions). The vertical radius after MTR's clamp
 * and the largest allowed radius must be equal.
 */
class VerticalOracleTest {
    private static final long SEED = 20261006L;
    private static final int ANGLES = 16;
    private static final int ENDPOINTS_PER_PAIR = 12;
    private static final int MAX_DISTANCE = 256;
    private static final int START_RANGE = 30_000;
    private static final long START_Y = 64;
    /** End y minus start y; endpoint j of each angle pair uses RISES[j % RISES.length]. */
    private static final long[] RISES = {1, -2, 5, -13, 40, -100};
    /** TWO_RADII's requested radii: negative and 0 (one constant grade), ordinary ones, and one far above any limit. */
    private static final double[] RADII = {-3, 0, 8, 60, 400, 1e7};
    /** Heights are compared at u = L*m/U_STEPS (m = 0..U_STEPS), u = -1 and u = L + 1, both directions. */
    private static final int U_STEPS = 128;
    private static final double TOLERANCE = 1e-6;

    record Case(long x1, long z1, int a1, long x2, long y2, long z2, int a2) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "(%d,%d,%d)@%d -> (%d,%d,%d)@%d", x1, START_Y, z1, a1, x2, y2, z2, a2);
        }
    }

    /** One way a rail is built: a shape and a requested vertical radius. */
    record Build(Rail.Shape shape, double radius) {
        @Override
        public String toString() {
            return this.shape == Rail.Shape.TWO_RADII ? "TWO_RADII R=" + this.radius : this.shape.name();
        }
    }

    @Test
    void heightsMatchMtr405ForEveryShape() {
        List<Case> cases = cases();
        List<Build> builds = new ArrayList<>(List.of(new Build(Rail.Shape.QUADRATIC, 0), new Build(Rail.Shape.CABLE, 0)));
        for (double radius : RADII) {
            builds.add(new Build(Rail.Shape.TWO_RADII, radius));
        }
        Map<Rail.Shape, Stats> byShape = new EnumMap<>(Rail.Shape.class);
        for (Case c : cases) {
            for (Build build : builds) {
                byShape.computeIfAbsent(build.shape(), Stats::new).add(c, build);
            }
        }
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "%n=== G-3: heights, the RailMath port (StrictMath) vs MTR 4.0.5 ===%n"
                + "%,d rails (16 x 16 heading pairs x %d endpoints within %d blocks, seed %d, rises cycling %s, plus level, "
                + "start = end and steep short cases), each as QUADRATIC, CABLE and TWO_RADII at R = %s%n", cases.size(), ENDPOINTS_PER_PAIR,
                MAX_DISTANCE, SEED, java.util.Arrays.toString(RISES), java.util.Arrays.toString(RADII)));
        byShape.values().forEach(stats -> report.append(stats.report()).append('\n'));
        System.out.println(report);

        List<Executable> checks = new ArrayList<>();
        byShape.values().forEach(stats -> checks.addAll(stats.checks()));
        assertEquals(3, byShape.size(), "all three shapes compared");
        assertAll(checks);
    }

    /** S5-style random rails with a rise, plus level rails, start = end, and short steep ones. */
    private static List<Case> cases() {
        Random random = new Random(SEED);
        List<Case> cases = new ArrayList<>();
        for (int a1 = 0; a1 < ANGLES; a1++) {
            for (int a2 = 0; a2 < ANGLES; a2++) {
                for (int j = 0; j < ENDPOINTS_PER_PAIR; j++) {
                    long x1 = between(random, -START_RANGE, START_RANGE);
                    long z1 = between(random, -START_RANGE, START_RANGE);
                    long[] offset = offsetWithinMaxDistance(random);
                    cases.add(new Case(x1, z1, a1, x1 + offset[0], START_Y + RISES[j % RISES.length], z1 + offset[1], a2));
                }
            }
        }
        for (int a = 0; a < ANGLES; a += 2) {
            long x1 = between(random, -START_RANGE, START_RANGE);
            long z1 = between(random, -START_RANGE, START_RANGE);
            int[] step = {1, 1, 0, -1, -1, -1, 0, 1};
            int[] stepZ = {0, 1, 1, 1, 0, -1, -1, -1};
            long dx = step[a / 2];
            long dz = stepZ[a / 2];
            cases.add(new Case(x1, z1, a, x1 + 40 * dx, START_Y, z1 + 40 * dz, a)); // level
            cases.add(new Case(x1, z1, a, x1, START_Y + 5, z1, a)); // start = end, 5 up: no rail
            cases.add(new Case(x1, z1, a, x1 + 4 * dx, START_Y + 9, z1 + 4 * dz, a)); // steeper than 1:1
            cases.add(new Case(x1, z1, a, x1 + 3 * dx, START_Y - 30, z1 + 3 * dz, a)); // a sheer drop
        }
        return cases;
    }

    private static long[] offsetWithinMaxDistance(Random random) {
        long dx;
        long dz;
        do {
            dx = between(random, -MAX_DISTANCE, MAX_DISTANCE);
            dz = between(random, -MAX_DISTANCE, MAX_DISTANCE);
        } while (dx * dx + dz * dz > (long) MAX_DISTANCE * MAX_DISTANCE);
        return new long[] {dx, dz};
    }

    private static long between(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static final class Stats {
        private final Rail.Shape shape;
        private long rails;
        private long valid;
        private long samples;
        private long bothNaN;
        private long oneSidedNaN;
        private double maxDy;
        private double maxDRadius;
        private double maxDMaxRadius;
        private String worstDy;
        private String worstRadius;
        private final List<String> shapeMismatches = new ArrayList<>();

        Stats(Rail.Shape shape) {
            this.shape = shape;
        }

        void add(Case c, Build build) {
            org.mtr.core.data.RailMath oracle = new org.mtr.core.data.RailMath(
                    new org.mtr.core.data.Position(c.x1(), START_Y, c.z1()), org.mtr.core.tool.Angle.values()[c.a1()],
                    new org.mtr.core.data.Position(c.x2(), c.y2(), c.z2()), org.mtr.core.tool.Angle.values()[c.a2()],
                    org.mtr.core.data.Rail.Shape.valueOf(build.shape().name()), build.radius());
            RailMath port = new RailMath(new Position(c.x1(), START_Y, c.z1()), Angle.values()[c.a1()],
                    new Position(c.x2(), c.y2(), c.z2()), Angle.values()[c.a2()], build.shape(), build.radius());
            this.rails++;
            this.valid += MtrRails.isValid(port) ? 1 : 0;
            if (!oracle.getShape().name().equals(port.getShape().name())) {
                this.shapeMismatches.add(c + " " + build);
            }
            double dRadius = difference(oracle.getVerticalRadius(), port.getVerticalRadius());
            if (dRadius > this.maxDRadius) {
                this.maxDRadius = dRadius;
                this.worstRadius = c + " " + build;
            }
            this.maxDMaxRadius = StrictMath.max(this.maxDMaxRadius, difference(oracle.getMaxVerticalRadius(), port.getMaxVerticalRadius()));

            double length = oracle.getLength();
            for (int m = -1; m <= U_STEPS + 1; m++) {
                double u = m < 0 ? -1 : m > U_STEPS ? length + 1 : length * m / U_STEPS;
                for (boolean reverse : new boolean[] {false, true}) {
                    double expected = oracle.getPosition(u, reverse).y();
                    double actual = port.getPosition(u, reverse).y();
                    this.samples++;
                    if (Double.isNaN(expected) && Double.isNaN(actual)) {
                        this.bothNaN++;
                    }
                    double dy = difference(expected, actual);
                    if (dy == Double.POSITIVE_INFINITY) {
                        this.oneSidedNaN++;
                    } else if (dy > this.maxDy) {
                        this.maxDy = dy;
                        this.worstDy = c + " " + build + " u=" + u + (reverse ? " reversed" : "");
                    }
                }
            }
        }

        List<Executable> checks() {
            return List.of(
                    () -> assertEquals(List.of(), this.shapeMismatches, this.shape + ": shapes"),
                    () -> assertEquals(0, this.oneSidedNaN, this.shape + ": heights with NaN on one side only"),
                    () -> assertTrue(this.maxDy <= TOLERANCE, this.shape + ": max |dy| " + this.maxDy + " at " + this.worstDy),
                    () -> assertEquals(0, this.maxDRadius, this.shape + ": vertical radius differs, at " + this.worstRadius),
                    () -> assertEquals(0, this.maxDMaxRadius, this.shape + ": largest vertical radius differs"));
        }

        String report() {
            return String.format(Locale.ROOT, "  %-9s: %,d rails (%,d valid) | %,d heights (NaN on both sides %,d, on one side %,d) | max |dy| = %s "
                    + "(limit 1e-6) | max |d radius| = %s, max |d largest radius| = %s", this.shape, this.rails, this.valid, this.samples,
                    this.bothNaN, this.oneSidedNaN, this.maxDy, this.maxDRadius, this.maxDMaxRadius);
        }
    }

    /** |a - b|, where two NaNs (or two equal infinities) count as equal and a NaN on one side only as infinitely different. */
    private static double difference(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Double.isNaN(a) && Double.isNaN(b) ? 0 : Double.POSITIVE_INFINITY;
        }
        return a == b ? 0 : StrictMath.abs(a - b);
    }
}
