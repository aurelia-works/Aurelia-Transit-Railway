/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.paint;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.line.LineKind;

/**
 * Road paint in ATR (owner, 2026-11-24: "drawn like rails"). Stratos painted its lines as blocks, {@code stratos:white_line}
 * and three more, each with 9,600 chords × 8 layers of states (307,200 states, about 100 MB and seconds of startup). ATR
 * has no line blocks. The core still plans, journals and undoes a painted column as the state
 * {@code stratos:<line>[chord=…,layers=…]}; the world writer keeps it as an asphalt block with that many layers and the
 * line and chord in {@link PaintStore}, and reads it back as the same state. Clients draw the paint over the asphalt
 * ({@code PaintRenderer}), as MTR draws its rails.
 *
 * <p>A painted cell's value is {@code (kind + 1) << 16 | chord}: never 0, which stands for no paint.
 */
public final class PaintLines {
    /** The block a painted column is in the world. */
    public static final String SURFACE = StratosMod.MOD_ID + ":asphalt";

    private PaintLines() {
    }

    /** A painted column: its line, its chord and the asphalt's layers. */
    public record Line(LineKind kind, int chord, int layers) {
        public int value() {
            return PaintLines.value(this.kind, this.chord);
        }

        /** The state the core knows it by, properties in the game's order. */
        public String state() {
            return StratosMod.MOD_ID + ":" + this.kind.id() + "[chord=" + this.chord + ",layers=" + this.layers + "]";
        }

        /** The asphalt it is in the world. */
        public String surface() {
            return SURFACE + "[layers=" + this.layers + "]";
        }
    }

    public static int value(LineKind kind, int chord) {
        return (kind.ordinal() + 1) << 16 | chord;
    }

    /** The line of a cell's value; null for 0 or a value that isn't one. */
    @Nullable
    public static LineKind kind(int value) {
        int kind = (value >>> 16) - 1;
        return kind >= 0 && kind < LineKind.values().length ? LineKind.values()[kind] : null;
    }

    public static int chord(int value) {
        return value & 0xFFFF;
    }

    /** Whether {@code value} is a painted cell's. */
    public static boolean valid(int value) {
        return kind(value) != null && chord(value) < LineChords.COUNT;
    }

    /** The line a block id names ({@code stratos:white_line}…), or null. */
    @Nullable
    public static LineKind kindOf(String block) {
        String prefix = StratosMod.MOD_ID + ":";
        if (!block.startsWith(prefix)) {
            return null;
        }
        String id = block.substring(prefix.length());
        for (LineKind kind : LineKind.values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The painted column {@code state} names, such as {@code stratos:white_line[chord=12,layers=8]}; null if it names
     * something else or can't be one. A property left out takes the block's default (chord 0, 8 layers), as the game's
     * parser does.
     */
    @Nullable
    public static Line parse(String state) {
        String text = state.trim().toLowerCase(Locale.ROOT);
        int open = text.indexOf('[');
        LineKind kind = kindOf(open < 0 ? text : text.substring(0, open));
        if (kind == null) {
            return null;
        }
        int chord = 0;
        int layers = 8;
        if (open >= 0) {
            if (!text.endsWith("]")) {
                return null;
            }
            String body = text.substring(open + 1, text.length() - 1);
            if (!body.isEmpty()) {
                for (String pair : body.split(",", -1)) {
                    int equals = pair.indexOf('=');
                    if (equals < 0) {
                        return null;
                    }
                    String name = pair.substring(0, equals).trim();
                    int number;
                    try {
                        number = Integer.parseInt(pair.substring(equals + 1).trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    switch (name) {
                        case "chord" -> chord = number;
                        case "layers" -> layers = number;
                        default -> {
                            return null;
                        }
                    }
                }
            }
        }
        if (chord < 0 || chord >= LineChords.COUNT || layers < 1 || layers > 8) {
            return null;
        }
        return new Line(kind, chord, layers);
    }
}
