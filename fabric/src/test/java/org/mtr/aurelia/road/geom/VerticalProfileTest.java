/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.mtr.Angle;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.Position;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;

/**
 * The grade facts of {@link VerticalProfile} (task G-3, roads-v1 §6.1), checked against the RailMath port's own heights,
 * which {@code VerticalOracleTest} checks against MTR 4.0.5.
 */
class VerticalProfileTest {
    private static final long Y = 64;

    private static RailMath rail(long dx, long dz, int angle, long rise, Rail.Shape shape, double radius) {
        return new RailMath(new Position(0, Y, 0), Angle.values()[angle], new Position(dx, Y + rise, dz), Angle.values()[angle], shape, radius);
    }

    private static double height(RailMath rail, double u) {
        return rail.getPosition(u, false).y();
    }

    /**
     * Random valid rails with a rise, QUADRATIC and TWO_RADII at several radii: no slope between neighbouring samples of
     * MTR's heights is steeper than {@code steepestGrade()}, and the slope across the middle matches it.
     */
    @Test
    void theSteepestGradeIsWhatMtrsHeightsShow() {
        Random random = new Random(20261006L);
        int checked = 0;
        int middles = 0;
        double worstMiddle = 0;
        for (int i = 0; i < 3_000; i++) {
            int a1 = random.nextInt(16);
            int a2 = random.nextInt(16);
            long dx = random.nextInt(401) - 200;
            long dz = random.nextInt(401) - 200;
            long rise = (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(30));
            Rail.Shape shape = random.nextBoolean() ? Rail.Shape.QUADRATIC : Rail.Shape.TWO_RADII;
            double radius = new double[] {-1, 0, 5, 40, 300, 1e9}[random.nextInt(6)];
            RailMath rail = new RailMath(new Position(0, Y, 0), Angle.values()[a1], new Position(dx, Y + rise, dz), Angle.values()[a2], shape, radius);
            if (!MtrRails.isValid(rail) || rail.getLength() < 1) {
                continue;
            }
            VerticalProfile profile = VerticalProfile.of(rail, rise);
            double steepest = profile.steepestGrade();
            if (!(steepest < 2)) {
                continue; // steeper than 2:1 is no road; G-7 refuses far gentler ones
            }
            String what = String.format(Locale.ROOT, "%s R=%s rise %d over %.3f", shape, radius, rise, rail.getLength());
            double length = rail.getLength();
            int steps = 2_000;
            for (int m = 0; m < steps; m++) {
                double u0 = length * m / steps;
                double u1 = length * (m + 1) / steps;
                double slope = StrictMath.abs(height(rail, u1) - height(rail, u0)) / (u1 - u0);
                // Heights near y = 100 carry rounding of about 1e-12, so allow that much over the step.
                assertTrue(slope <= steepest * (1 + 1e-9) + 5e-12 / (u1 - u0), what + ": slope " + slope + " at u=" + u0 + " is steeper than "
                        + steepest);
            }
            checked++;
            // Across the middle: QUADRATIC's central difference is exactly steepest * (1 - h / L); a constant middle grade
            // is the steepest grade itself, where the straight part is wider than the step.
            if (length < 10) {
                continue;
            }
            double h = length * 1e-3;
            double expected = steepest;
            if (shape == Rail.Shape.QUADRATIC) {
                expected = steepest * (1 - h / length);
            } else if (profile.effectiveRadius() > 0 && length / 2 - profile.effectiveRadius() * StrictMath.sin(StrictMath.atan(steepest)) <= h) {
                continue; // the end curves (nearly) meet in the middle: covered by the bound above
            }
            double middle = StrictMath.abs(height(rail, length / 2 + h) - height(rail, length / 2 - h)) / (2 * h);
            double off = StrictMath.abs(middle - expected) / steepest;
            worstMiddle = StrictMath.max(worstMiddle, off);
            assertTrue(off <= 1e-6, what + ": the slope across the middle is " + middle + ", not " + expected);
            middles++;
        }
        System.out.printf(Locale.ROOT, "G-3: steepest grade bounded on %,d rails and matched across the middle on %,d; worst relative gap %.2e%n",
                checked, middles, worstMiddle);
        assertTrue(checked > 1_000 && middles > 500, "enough valid rails: " + checked + ", middles " + middles);
    }

    @Test
    void theGradeOfEachShape() {
        assertEquals(2.0 * 6 / 30, VerticalProfile.of(rail(30, 0, 0, 6, Rail.Shape.QUADRATIC, 0), 6).steepestGrade(), 1e-15, "QUADRATIC: 2H/L");
        assertEquals(6.0 / 30, VerticalProfile.of(rail(30, 0, 0, -6, Rail.Shape.TWO_RADII, 0), -6).steepestGrade(), 1e-15, "TWO_RADII, R = 0: H/L");
        assertEquals(6.0 / 30, VerticalProfile.of(rail(30, 0, 0, 6, Rail.Shape.TWO_RADII, -5), 6).steepestGrade(), 1e-15, "R < 0 too");
        assertEquals(0, VerticalProfile.of(rail(30, 0, 0, 0, Rail.Shape.QUADRATIC, 0), 0).steepestGrade(), "level");
        assertEquals(0, VerticalProfile.of(rail(30, 0, 0, 0, Rail.Shape.TWO_RADII, 20), 0).steepestGrade(), "level");
        // At MTR's largest radius the end curves meet in the middle: tan θ = 2HL / (L² − H²), steeper than QUADRATIC.
        VerticalProfile atMax = new VerticalProfile(Rail.Shape.TWO_RADII, 30, 6, 1e9);
        assertEquals(StrictMath.floor((900 + 36) * 100 / 24.0) / 100, atMax.effectiveRadius(), "clamped to MTR's limit");
        assertTrue(atMax.steepestGrade() > 2.0 * 6 / 30);
        assertEquals(2.0 * 6 * 30 / (900 - 36), atMax.steepestGrade(), 1e-3, "close to the curves meeting (the limit is rounded down to 0.01)");
        // End curves that would pass the vertical: no finite grade.
        assertEquals(Double.POSITIVE_INFINITY, new VerticalProfile(Rail.Shape.TWO_RADII, 5, 10, 1e9).steepestGrade());
    }

    @Test
    void ofReadsTheRailAndTheRadiusLimitIsMtrs() {
        RailMath rail = rail(30, 0, 0, 6, Rail.Shape.TWO_RADII, 20);
        VerticalProfile profile = VerticalProfile.of(rail, 6);
        assertEquals(new VerticalProfile(Rail.Shape.TWO_RADII, 30, 6, 20), profile, "below this rail's limit of 39");
        assertEquals(rail.getMaxVerticalRadius(), profile.maxRadius());
        assertEquals(rail.getVerticalRadius(), profile.effectiveRadius());
        RailMath clamped = rail(30, 0, 0, -6, Rail.Shape.TWO_RADII, 1e6);
        assertEquals(clamped.getVerticalRadius(), VerticalProfile.of(clamped, -6).radius(), "a rail's radius is already clamped");
        // The limit, for many lengths and rises, is the port's to the last bit.
        for (int n = 2; n <= 60; n += 3) {
            for (long rise : new long[] {1, -4, 9, -25}) {
                for (int angle : new int[] {0, 2, 6}) {
                    long d = angle == 0 ? 0 : n;
                    RailMath r = rail(angle == 6 ? -n : n, d, angle, rise, Rail.Shape.TWO_RADII, 1e9);
                    VerticalProfile p = VerticalProfile.of(r, rise);
                    assertEquals(r.getMaxVerticalRadius(), p.maxRadius(), n + " " + rise + " " + angle);
                    assertEquals(r.getVerticalRadius(), p.effectiveRadius(), n + " " + rise + " " + angle);
                }
            }
        }
        assertEquals(Double.POSITIVE_INFINITY, new VerticalProfile(Rail.Shape.QUADRATIC, 10, 0, 0).maxRadius(), "level: no limit");
    }

    /**
     * MTR's quirks are reported as MTR draws them: NaN heights at its own radius limit, and one constant grade for a radius
     * of exactly a quarter of the rise.
     */
    @Test
    void mtrsQuirksAreReportedAsMtrDrawsThem() {
        RailMath nan = rail(2, 2, 2, -4, Rail.Shape.TWO_RADII, 1e9); // the radius lands exactly on MTR's limit, 1.5
        assertTrue(Double.isNaN(height(nan, 1)), "MTR's own heights are NaN here (VerticalOracleTest: so are MTR 4.0.5's)");
        VerticalProfile nanProfile = VerticalProfile.of(nan, -4);
        assertTrue(Double.isNaN(nanProfile.steepestGrade()));
        assertTrue(!nanProfile.mtrCanDraw());
        double length = VerticalProfile.minLength(Rail.Shape.TWO_RADII, -4, 1.5, 0.5);
        assertTrue(new VerticalProfile(Rail.Shape.TWO_RADII, length, -4, 1.5).mtrCanDraw(), "minLength never ends on a NaN profile");

        int checked = 0;
        for (int n = 1; n <= 300; n++) {
            for (int angle : new int[] {0, 2, 1}) {
                long dx = angle == 1 ? 2L * n : n;
                long dz = angle == 0 ? 0 : angle == 2 ? n : n;
                RailMath straight = rail(dx, dz, angle, 8, Rail.Shape.TWO_RADII, 2); // R = H / 4
                if (!MtrRails.isValid(straight) || straight.getLength() <= 16) {
                    continue;
                }
                double along = straight.getLength();
                assertEquals(8 / along, VerticalProfile.of(straight, 8).steepestGrade(), 1e-15, n + "");
                assertEquals(2, height(straight, along / 4) - Y, 1e-9, n + ": one constant grade, a quarter of the rise a quarter of the way");
                assertEquals(1, height(straight, along / 8) - Y, 1e-9, n + ": no end curve");
                checked++;
            }
        }
        assertTrue(checked > 500, "R = H/4 rails checked: " + checked);
    }

    /** roads-v1 §6.1's table: the shortest QUADRATIC piece for a rise of 1, 5 and 10 at 8%, 12.5% and 25%. */
    @Test
    void minimumLengthsForARiseMatchTheSpecsTable() {
        long[] rises = {1, 5, 10};
        double[] grades = {0.08, 0.125, 0.25};
        double[][] table = {{25, 16, 8}, {125, 80, 40}, {250, 160, 80}};
        for (int i = 0; i < rises.length; i++) {
            for (int j = 0; j < grades.length; j++) {
                assertEquals(table[i][j], VerticalProfile.minLength(Rail.Shape.QUADRATIC, rises[i], 0, grades[j]), 1e-9, rises[i] + " at " + grades[j]);
                assertEquals(table[i][j], VerticalProfile.minLength(Rail.Shape.QUADRATIC, -rises[i], 0, grades[j]), 1e-9, "a fall too");
            }
        }
        assertEquals(12, VerticalProfile.minLength(Rail.Shape.QUADRATIC, 1, 0, 1.0 / 6), 1e-9, "alley, 1:6");
        assertEquals(24, VerticalProfile.minLength(Rail.Shape.QUADRATIC, 1, 0, 1.0 / 12), 1e-9, "city street, 1:12");
        assertEquals(0, VerticalProfile.minLength(Rail.Shape.QUADRATIC, 0, 0, 0.08), "level");
        assertEquals(12.5, VerticalProfile.minLength(Rail.Shape.TWO_RADII, 1, 0, 0.08), 1e-9, "TWO_RADII, R = 0: H/g");
    }

    @Test
    void theTwoRadiiMinimumLengthIsTheShortestWithinTheLimit() {
        for (long rise : new long[] {1, 3, 8, 20, -12}) {
            for (double radius : new double[] {2, 10, 50, 200, 1e6}) {
                for (double grade : new double[] {1.0 / 6, 1.0 / 12, 0.04}) {
                    double length = VerticalProfile.minLength(Rail.Shape.TWO_RADII, rise, radius, grade);
                    String what = rise + " R=" + radius + " g=" + grade + ": " + length;
                    assertTrue(new VerticalProfile(Rail.Shape.TWO_RADII, length, rise, radius).steepestGrade() <= grade, what + " is too steep");
                    assertTrue(new VerticalProfile(Rail.Shape.TWO_RADII, length - 1e-6, rise, radius).steepestGrade() > grade, what + " isn't the shortest");
                    assertTrue(length >= StrictMath.abs(rise) / grade, what + ": somewhere is at least as steep as the average");
                }
            }
        }
    }

    @Test
    void badProfilesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new VerticalProfile(Rail.Shape.CABLE, 10, 1, 0), "roads don't use CABLE");
        assertThrows(IllegalArgumentException.class, () -> new VerticalProfile(Rail.Shape.QUADRATIC, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new VerticalProfile(Rail.Shape.QUADRATIC, Double.NaN, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new VerticalProfile(Rail.Shape.TWO_RADII, 10, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> VerticalProfile.minLength(Rail.Shape.QUADRATIC, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> VerticalProfile.minLength(Rail.Shape.CABLE, 1, 0, 0.1));
    }
}
