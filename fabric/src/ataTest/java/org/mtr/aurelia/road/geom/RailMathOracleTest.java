/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.mtr.Angle;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.Position;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.geom.mtr.Vector;

/**
 * The RailMath port against the oracle, MTR 4.0.5's own {@code org.mtr.core.data.RailMath}, on spike S5's cases
 * (task G-2, spec roads-v1 §4.3). Owner's tolerances (2026-10-04): validity agrees 100%; lengths within 1e-6;
 * positions within 1e-6. A sample that lands exactly on the join between a rail's two pieces is the one exception a
 * tolerance cannot cover: MTR's own pieces miss each other there (by up to 2.2e-5 on some geometry), and the port's
 * {@code count1 + count2} can differ from the oracle's by one ulp, which puts the same sample on the other piece.
 * So on a join the port's position is compared with both of MTR's piece values and must match the nearer within 1e-6.
 */
@Tag("slow")
class RailMathOracleTest {
    private static final long SEED = 20261002L;
    private static final int ANGLES = 16;
    private static final int ENDPOINTS_PER_PAIR = 200;
    private static final int MAX_DISTANCE = 512;
    private static final int START_RANGE = 30_000;
    private static final long START_Y = 64;
    /** End y minus start y; endpoint j of each angle pair uses HEIGHT_DIFFERENCES[j % 5]. */
    private static final long[] HEIGHT_DIFFERENCES = {0, 1, -3, 8, -20};
    /** getPosition is compared at u = L*m/U_STEPS (m = 0..U_STEPS), u = -1 and u = L + 1, both directions. */
    private static final int U_STEPS = 64;
    private static final double TOLERANCE = 1e-6;
    /** How far either side of a join to read the oracle's other piece: well past its 1-ulp count difference, well under a sample step. */
    private static final double JOIN_PROBE = 1e-9;

    @Test
    void portMatchesMtr405() {
        List<Case> random = randomCases();
        List<Case> edges = edgeCases();
        Stats randomStats = new Stats("random cases");
        Stats edgeStats = new Stats("edge cases");
        random.forEach(randomStats::add);
        edges.forEach(edgeStats::add);
        System.out.println(String.join("\n", "",
                "=== G-2: the RailMath port (StrictMath) vs MTR 4.0.5's org.mtr.core.data.RailMath ===",
                String.format(Locale.ROOT, "Random: 16 x 16 heading pairs x %d endpoints within %d blocks (seed %d), dy cycling %s, QUADRATIC",
                        ENDPOINTS_PER_PAIR, MAX_DISTANCE, SEED, java.util.Arrays.toString(HEIGHT_DIFFERENCES)),
                randomStats.report(),
                "Edge: 64 collinear straights, 512 start = end, 1,024 starts 29-30 million blocks out",
                edgeStats.report(), ""));

        assertEquals(51_200, random.size(), "random case count (S5)");
        assertEquals(1_600, edges.size(), "edge case count (S5)");
        assertAll(randomStats.checks());
        assertAll(edgeStats.checks());
    }

    // ---- Cases (as spike S5) ----

    record Case(long x1, long y1, long z1, int a1, long x2, long y2, long z2, int a2) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "(%d,%d,%d)@%d -> (%d,%d,%d)@%d", x1, y1, z1, a1, x2, y2, z2, a2);
        }
    }

    /** 16 x 16 angle pairs x 200 random endpoints within 512 blocks (horizontally) of a random start. */
    private static List<Case> randomCases() {
        Random random = new Random(SEED);
        List<Case> cases = new ArrayList<>(ANGLES * ANGLES * ENDPOINTS_PER_PAIR);
        for (int a1 = 0; a1 < ANGLES; a1++) {
            for (int a2 = 0; a2 < ANGLES; a2++) {
                for (int j = 0; j < ENDPOINTS_PER_PAIR; j++) {
                    long x1 = between(random, -START_RANGE, START_RANGE);
                    long z1 = between(random, -START_RANGE, START_RANGE);
                    long[] offset = offsetWithinMaxDistance(random);
                    cases.add(new Case(x1, START_Y, z1, a1, x1 + offset[0],
                            START_Y + HEIGHT_DIFFERENCES[j % HEIGHT_DIFFERENCES.length], z1 + offset[1], a2));
                }
            }
        }
        return cases;
    }

    /** Branches that random endpoints rarely or never hit, plus coordinates near the world border. */
    private static List<Case> edgeCases() {
        Random random = new Random(SEED + 1);
        List<Case> cases = new ArrayList<>();
        // Collinear straights along the 8 axis and diagonal headings, ending in the same or the opposite heading.
        int[][] directions = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        for (int d = 0; d < directions.length; d++) {
            int angle = d * 2;
            for (int n : new int[] {1, 7, 100, 362}) {
                for (int endAngle : new int[] {angle, (angle + ANGLES / 2) % ANGLES}) {
                    long x1 = between(random, -START_RANGE, START_RANGE);
                    long z1 = between(random, -START_RANGE, START_RANGE);
                    cases.add(new Case(x1, START_Y, z1, angle, x1 + (long) directions[d][0] * n, START_Y, z1 + (long) directions[d][1] * n, endAngle));
                }
            }
        }
        // Start and end in the same block (flat, and 5 blocks apart vertically), all 256 angle pairs.
        for (int a1 = 0; a1 < ANGLES; a1++) {
            for (int a2 = 0; a2 < ANGLES; a2++) {
                for (long dy : new long[] {0, 5}) {
                    long x1 = between(random, -START_RANGE, START_RANGE);
                    long z1 = between(random, -START_RANGE, START_RANGE);
                    cases.add(new Case(x1, START_Y, z1, a1, x1, START_Y + dy, z1, a2));
                }
            }
        }
        // Starts 29 to 30 million blocks from the origin (random signs), 4 random endpoints per angle pair.
        for (int a1 = 0; a1 < ANGLES; a1++) {
            for (int a2 = 0; a2 < ANGLES; a2++) {
                for (int j = 0; j < 4; j++) {
                    long x1 = (random.nextBoolean() ? 1 : -1) * between(random, 29_000_000, 29_999_999);
                    long z1 = (random.nextBoolean() ? 1 : -1) * between(random, 29_000_000, 29_999_999);
                    long[] offset = offsetWithinMaxDistance(random);
                    cases.add(new Case(x1, START_Y, z1, a1, x1 + offset[0], START_Y + HEIGHT_DIFFERENCES[j], z1 + offset[1], a2));
                }
            }
        }
        return cases;
    }

    /** A uniformly random whole-block offset (dx, dz) with dx^2 + dz^2 <= MAX_DISTANCE^2. */
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

    // ---- Comparison ----

    private static final class Stats {
        private final String name;
        private long cases;
        private long oracleValid;
        private long validityAgree;
        private long samples;
        private long joinSamples;
        private long oneSidedNaN;
        private double maxDLen;
        private double maxDPosOnJoin;
        private double maxDPosElsewhere;
        private Case worstLen;
        private Case worstOnJoin;
        private Case worstElsewhere;
        private final List<Case> validityMismatches = new ArrayList<>();

        private Stats(String name) {
            this.name = name;
        }

        private void add(Case c) {
            org.mtr.core.data.RailMath oracle = new org.mtr.core.data.RailMath(
                    new org.mtr.core.data.Position(c.x1(), c.y1(), c.z1()), org.mtr.core.tool.Angle.values()[c.a1()],
                    new org.mtr.core.data.Position(c.x2(), c.y2(), c.z2()), org.mtr.core.tool.Angle.values()[c.a2()],
                    org.mtr.core.data.Rail.Shape.QUADRATIC, 0);
            RailMath port = new RailMath(new Position(c.x1(), c.y1(), c.z1()), Angle.values()[c.a1()],
                    new Position(c.x2(), c.y2(), c.z2()), Angle.values()[c.a2()], Rail.Shape.QUADRATIC, 0);

            this.cases++;
            boolean oracleIsValid = oracleIsValid(oracle);
            this.oracleValid += oracleIsValid ? 1 : 0;
            if (oracleIsValid == MtrRails.isValid(port)) {
                this.validityAgree++;
            } else {
                this.validityMismatches.add(c);
            }

            double length = oracle.getLength();
            double dLen = difference(length, port.getLength());
            if (dLen > this.maxDLen) {
                this.maxDLen = dLen;
                this.worstLen = c;
            }

            // Where the oracle's two pieces meet: a sample exactly on the join may come from either piece.
            double count1 = StrictMath.abs(oracleField(oracle, "tEnd1") - oracleField(oracle, "tStart1"));
            double count2 = StrictMath.abs(oracleField(oracle, "tEnd2") - oracleField(oracle, "tStart2"));
            boolean twoPieces = count1 > 0 && count2 > 0;

            for (int m = -1; m <= U_STEPS + 1; m++) {
                double u = m < 0 ? -1 : m > U_STEPS ? length + 1 : length * m / U_STEPS;
                for (boolean reverse : new boolean[] {false, true}) {
                    org.mtr.core.tool.Vector expected = oracle.getPosition(u, reverse);
                    Vector actual = port.getPosition(u, reverse);
                    double dPos = distance(expected.x(), expected.y(), expected.z(), actual.x(), actual.y(), actual.z());
                    this.samples++;
                    if (dPos == Double.POSITIVE_INFINITY) {
                        this.oneSidedNaN++;
                    }
                    double clamped = StrictMath.min(length, StrictMath.max(0, u));
                    double value = reverse ? length - clamped : clamped;
                    if (twoPieces && StrictMath.abs(value - count1) <= 1e-6) {
                        // On the join either piece is MTR's own answer (see the class comment): compare with both.
                        dPos = StrictMath.min(dPos, StrictMath.min(
                                joinDistance(oracle, u - JOIN_PROBE, reverse, actual),
                                joinDistance(oracle, u + JOIN_PROBE, reverse, actual)));
                        this.joinSamples++;
                        if (dPos > this.maxDPosOnJoin) {
                            this.maxDPosOnJoin = dPos;
                            this.worstOnJoin = c;
                        }
                    } else if (dPos > this.maxDPosElsewhere) {
                        this.maxDPosElsewhere = dPos;
                        this.worstElsewhere = c;
                    }
                }
            }
        }

        private List<org.junit.jupiter.api.function.Executable> checks() {
            return List.of(
                    () -> assertEquals(this.cases, this.validityAgree, this.name + ": validity agreement; first mismatches "
                            + this.validityMismatches.subList(0, StrictMath.min(5, this.validityMismatches.size()))),
                    () -> assertEquals(0, this.oneSidedNaN, this.name + ": samples with NaN on one side only"),
                    () -> assertTrue(this.maxDLen <= TOLERANCE, this.name + ": max |dLen| " + this.maxDLen + " at " + this.worstLen),
                    () -> assertTrue(this.maxDPosElsewhere <= TOLERANCE, this.name + ": max |dPos| off the joins " + this.maxDPosElsewhere + " at " + this.worstElsewhere),
                    () -> assertTrue(this.maxDPosOnJoin <= TOLERANCE, this.name + ": max |dPos| on a join " + this.maxDPosOnJoin + " at " + this.worstOnJoin));
        }

        private String report() {
            return String.format(Locale.ROOT,
                    "  %s: %,d | MTR valid %,d | validity agreement %,d/%,d | getPosition samples %,d (one-sided NaN %,d)%n"
                            + "  max |dLen| = %s | max |dPos| off the joins = %s (limit 1e-6) | on a join (%,d samples, either piece) = %s (limit 1e-6)",
                    this.name, this.cases, this.oracleValid, this.validityAgree, this.cases, this.samples, this.oneSidedNaN,
                    this.maxDLen, this.maxDPosElsewhere, this.joinSamples, this.maxDPosOnJoin);
        }
    }

    /** |a - b|, where two NaNs count as equal and a NaN on one side only counts as infinitely different. */
    private static double difference(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Double.isNaN(a) && Double.isNaN(b) ? 0 : Double.POSITIVE_INFINITY;
        }
        return a == b ? 0 : StrictMath.abs(a - b);
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = difference(x1, x2);
        double dy = difference(y1, y2);
        double dz = difference(z1, z2);
        return StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** How far the port's position at {@code u} is from the oracle's position at {@code u} (used to read the other piece). */
    private static double joinDistance(org.mtr.core.data.RailMath oracle, double u, boolean reverse, Vector actual) {
        org.mtr.core.tool.Vector expected = oracle.getPosition(u, reverse);
        return distance(expected.x(), expected.y(), expected.z(), actual.x(), actual.y(), actual.z());
    }

    // ---- Reflection: MTR's isValid() is package-private and its segment fields are private ----

    private static final Map<String, Field> ORACLE_FIELDS = new HashMap<>();
    private static Method oracleIsValid;

    private static double oracleField(org.mtr.core.data.RailMath rail, String name) {
        try {
            Field field = ORACLE_FIELDS.computeIfAbsent(name, n -> {
                try {
                    Field f = org.mtr.core.data.RailMath.class.getDeclaredField(n);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException(e);
                }
            });
            return field.getDouble(rail);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean oracleIsValid(org.mtr.core.data.RailMath rail) {
        try {
            if (oracleIsValid == null) {
                oracleIsValid = org.mtr.core.data.RailMath.class.getDeclaredMethod("isValid");
                oracleIsValid.setAccessible(true);
            }
            return (boolean) oracleIsValid.invoke(rail);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
