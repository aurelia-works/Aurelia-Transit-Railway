/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

import java.util.List;
import java.util.Locale;

/**
 * Stratos's four painted lines (task B-2; ADR-0044, ADR-0046), each a block of its own: {@code stratos:<id>}, the ids
 * IndusShaders lists. A line is one or more stripes, each at an offset from the line's centre (in blocks, positive to
 * the line's left as it runs) with a width; one block is a metre. UK widths (research 13 §3.1):
 * <ul>
 *   <li>{@link #WHITE}: 100 mm (centre and lane lines on roads up to 40 mph; City street's centre line);</li>
 *   <li>{@link #WHITE_WIDE}: 150 mm (the same lines on faster roads, edge lines);</li>
 *   <li>{@link #YELLOW}: 75 mm (waiting restrictions);</li>
 *   <li>{@link #DOUBLE_YELLOW}: two 75 mm lines with a 75 mm gap.</li>
 * </ul>
 * Only City street paints today, with {@link #WHITE}; the others wait for Phase 2's layouts.
 */
public enum LineKind {
    WHITE(List.of(new Stripe(0, 0.100))),
    WHITE_WIDE(List.of(new Stripe(0, 0.150))),
    YELLOW(List.of(new Stripe(0, 0.075))),
    DOUBLE_YELLOW(List.of(new Stripe(-0.075, 0.075), new Stripe(0.075, 0.075)));

    /** A stripe of paint: its centre's offset from the line's centre (positive to the left) and its width, in blocks. */
    public record Stripe(double offset, double width) {
    }

    private final List<Stripe> stripes;

    LineKind(List<Stripe> stripes) {
        this.stripes = stripes;
    }

    /** The block's name in Stratos's namespace, such as {@code white_line} or {@code double_yellow_line}. */
    public String id() {
        return switch (this) {
            case WHITE -> "white_line";
            case WHITE_WIDE -> "white_line_wide";
            case YELLOW -> "yellow_line";
            case DOUBLE_YELLOW -> "double_yellow_line";
        };
    }

    /** Its paint's colour, lower case: {@code white} or {@code yellow} (the paint texture is {@code paint_<colour>}). */
    public String paint() {
        return this == WHITE || this == WHITE_WIDE ? "white" : "yellow";
    }

    public List<Stripe> stripes() {
        return this.stripes;
    }

    /** How far the paint reaches from the line's centre, on its wider side: at most {@link LineChords#MARGIN}. */
    public double halfExtent() {
        double reach = 0;
        for (Stripe stripe : this.stripes) {
            reach = StrictMath.max(reach, StrictMath.abs(stripe.offset()) + stripe.width() / 2);
        }
        return reach;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
