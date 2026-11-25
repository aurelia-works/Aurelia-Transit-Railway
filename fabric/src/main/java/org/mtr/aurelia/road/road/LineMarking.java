/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import org.mtr.aurelia.road.road.line.LineKind;

/**
 * A painted line along a section (spec roads-v1 §7.2): its colour and its dash pattern. A column is painted when
 * {@code (s + phase) mod (mark + gap) < mark}, measured along the line's own curve, so patterns are whole blocks.
 */
public record LineMarking(Colour colour, int mark, int gap) {
    /** The marking colours, with the palette roles of §8. */
    public enum Colour {
        WHITE("marking_white"),
        YELLOW("marking_yellow");

        private final String paletteRole;

        Colour(String paletteRole) {
            this.paletteRole = paletteRole;
        }

        public String paletteRole() {
            return this.paletteRole;
        }
    }

    /** A dashed line, {@code mark} blocks painted then {@code gap} blocks left. */
    public static LineMarking dashed(Colour colour, int mark, int gap) {
        return new LineMarking(colour, mark, gap);
    }

    /** A solid line (no gaps). */
    public static LineMarking solid(Colour colour) {
        return new LineMarking(colour, 1, 0);
    }

    public boolean isSolid() {
        return this.gap == 0;
    }

    /**
     * The line block it is drawn with where the palette's markings are line blocks (B-2): white 100 mm (City street's centre
     * line, UK roads up to 40 mph), yellow 75 mm. The wider and double lines wait for layouts that paint them (Phase 2).
     */
    public LineKind line() {
        return this.colour == Colour.WHITE ? LineKind.WHITE : LineKind.YELLOW;
    }
}
