/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

/**
 * A road gradient as an exact fraction, rise over run, kept in lowest terms: 1:12 is {@code 1/12} and 6% is
 * {@code 3/50}. Exact, so 1:12 is never mistaken for a rounded 0.0833.
 */
public record Grade(long rise, long run) {
    public Grade {
        if (rise < 0 || run <= 0) {
            throw new IllegalArgumentException("A grade needs rise >= 0 and run > 0, got " + rise + "/" + run);
        }
        long divisor = gcd(rise, run);
        rise /= divisor;
        run /= divisor;
    }

    /** "1 in n", such as 1:12. */
    public static Grade oneIn(long run) {
        return new Grade(1, run);
    }

    /** A percentage, such as 6%. */
    public static Grade percent(long percent) {
        return new Grade(percent, 100);
    }

    /** The grade as a number (1:12 → 0.0833…). For display and for comparing with measured grades. */
    public double value() {
        return (double) this.rise / this.run;
    }

    /** True when a slope rising {@code rise} over {@code run} blocks is steeper than this grade. */
    public boolean isExceededBy(double rise, double run) {
        return rise * this.run > this.rise * run;
    }

    private static long gcd(long a, long b) {
        return b == 0 ? (a == 0 ? 1 : a) : gcd(b, a % b);
    }
}
