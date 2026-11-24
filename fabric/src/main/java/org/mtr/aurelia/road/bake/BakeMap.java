/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.bake;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.line.LineKind;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteRole;

/**
 * What each Stratos block becomes when it is baked to vanilla (task B-3; ADR-0044; owner, 2026-10-15): block for block,
 * in place, the nearest vanilla block, so a road keeps its shape and its lines stay where they were.
 * <ul>
 *   <li><b>Heights:</b> {@code layers} 1–5 become the family's bottom slab, 6–8 its full block: the nearer of the two,
 *       never removed (as S15's offline rename did).</li>
 *   <li><b>Blocks:</b> asphalt, footway asphalt and gullies become the Vanilla palette's surface (polished deepslate),
 *       footway flags its footway (smooth stone), kerbs its kerb (polished andesite); dropped kerbs keep their tactile
 *       paving's colour, smooth sandstone (buff) or polished granite (red).</li>
 *   <li><b>Lines:</b> a white line block becomes the Vanilla palette's white marking (smooth quartz) where the line's
 *       centre runs through the block itself, and its surface where only the paint's edge reaches in, so a line comes out
 *       a block wide, as the Vanilla palette paints its dashes. A yellow line block likewise becomes yellow concrete, or a
 *       smooth sandstone slab at half height (vanilla has no yellow slab).</li>
 * </ul>
 * Every block in Stratos's namespace is a Stratos block ({@link #isStratos}); one this Stratos doesn't know has no vanilla
 * block and stays. Pure: states are strings, properties in any order.
 */
public final class BakeMap {
    /** Stratos's blocks' namespace, with its colon. */
    public static final String NAMESPACE = "stratos:";
    /** A block of at least this many layers becomes a full block, else a bottom slab. */
    public static final int FULL_FROM = 6;

    private static final String SLAB = "[type=bottom,waterlogged=false]";
    private static final Family SANDSTONE = new Family("minecraft:smooth_sandstone", Optional.of("minecraft:smooth_sandstone_slab" + SLAB),
            Optional.empty(), 0, false, false);
    private static final Family GRANITE = new Family("minecraft:polished_granite", Optional.of("minecraft:polished_granite_slab" + SLAB),
            Optional.empty(), 0, false, false);
    private static final Family YELLOW = new Family("minecraft:yellow_concrete", Optional.of("minecraft:smooth_sandstone_slab" + SLAB),
            Optional.empty(), 0, false, false);

    private BakeMap() {
    }

    /** Whether {@code state} is one of Stratos's blocks: in its namespace. */
    public static boolean isStratos(String state) {
        return state.startsWith(NAMESPACE);
    }

    /** The vanilla state {@code state} bakes to; empty when it isn't a Stratos block, or is one this Stratos doesn't know. */
    public static Optional<String> vanilla(String state) {
        if (!isStratos(state)) {
            return Optional.empty();
        }
        int open = state.indexOf('[');
        String id = open < 0 ? state : state.substring(0, open);
        Map<String, String> properties = properties(state, open);
        Optional<Family> family = family(id.substring(NAMESPACE.length()), properties);
        if (family.isEmpty()) {
            return Optional.empty();
        }
        boolean full = number(properties.get("layers"), 8) >= FULL_FROM;
        return Optional.of(full ? family.get().full() : family.get().half().orElse(family.get().full()));
    }

    private static Optional<Family> family(String name, Map<String, String> properties) {
        Palette vanilla = BuiltInPalettes.vanilla();
        switch (name) {
            case "asphalt", "footway_asphalt", "gully" -> {
                return vanilla.family(PaletteRole.SURFACE);
            }
            case "footway_flags" -> {
                return vanilla.family(PaletteRole.FOOTWAY);
            }
            case "kerb" -> {
                return vanilla.family(PaletteRole.KERB);
            }
            case "dropped_kerb" -> {
                return Optional.of("red".equals(properties.get("colour")) ? GRANITE : SANDSTONE);
            }
            default -> {
                for (LineKind kind : LineKind.values()) {
                    if (kind.id().equals(name)) {
                        if (!painted(number(properties.get(Family.CHORD), -1))) {
                            return vanilla.family(PaletteRole.SURFACE);
                        }
                        return kind.paint().equals("white") ? vanilla.family(PaletteRole.MARKING_WHITE) : Optional.of(YELLOW);
                    }
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Whether a line block with {@code chord} is painted when baked: whether the line's centre, from one end of the chord
     * to the other, runs through the block's own square (x and z in [0, 1)), not only through the margin around it.
     */
    static boolean painted(int chord) {
        if (chord < 0 || chord >= LineChords.COUNT) {
            return false;
        }
        double[] ends = LineChords.ends(chord);
        double x1 = ends[0];
        double z1 = ends[1];
        double dx = ends[2] - x1;
        double dz = ends[3] - z1;
        // Liang–Barsky: the part of the chord inside the closed square [0, 1]².
        double[] range = {0, 1};
        if (!clip(-dx, x1, range) || !clip(dx, 1 - x1, range) || !clip(-dz, z1, range) || !clip(dz, 1 - z1, range)) {
            return false;
        }
        // Inside the half-open square: some point of that part has x < 1 and z < 1 (a part lying along the far edges
        // belongs to the next block). The part is a segment, so its ends and middle tell.
        for (double t : new double[] {range[0], (range[0] + range[1]) / 2, range[1]}) {
            if (x1 + t * dx < 1 && z1 + t * dz < 1) {
                return true;
            }
        }
        return false;
    }

    /** One Liang–Barsky edge: narrows {@code range} to where {@code p * t <= q}; false when nothing is left. */
    private static boolean clip(double p, double q, double[] range) {
        if (p == 0) {
            return q >= 0;
        }
        double t = q / p;
        if (p < 0) {
            range[0] = StrictMath.max(range[0], t);
        } else {
            range[1] = StrictMath.min(range[1], t);
        }
        return range[0] <= range[1];
    }

    private static Map<String, String> properties(String state, int open) {
        Map<String, String> properties = new HashMap<>();
        if (open < 0 || !state.endsWith("]")) {
            return properties;
        }
        for (String pair : state.substring(open + 1, state.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        return properties;
    }

    private static int number(String text, int otherwise) {
        if (text == null) {
            return otherwise;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }
}
