/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import java.util.Optional;
import org.mtr.aurelia.road.road.raster.Column;

/**
 * A part of a road that a palette gives blocks to (spec roads-v1 §8; task R-9): each band's role
 * ({@link org.mtr.aurelia.road.road.Band#paletteRole()}), a painted line's colour
 * ({@link org.mtr.aurelia.road.road.LineMarking.Colour#paletteRole()}), the foundation and the node marker.
 *
 * <p>roads-v1 §8's {@code surface_half} and {@code surface_layers} are not roles of their own: every role's family says how
 * it stands part-way up a block ({@link Family}). The urban layouts need the {@link #required()} roles; the others belong
 * to Phase 2's layouts and are optional until then.
 */
public enum PaletteRole {
    SURFACE("surface", true, true),
    MARKING_WHITE("marking_white", true, true),
    MARKING_YELLOW("marking_yellow", true, false),
    KERB("kerb", true, true),
    FOOTWAY("footway", true, true),
    GUTTER("gutter", true, true),
    /** B-4: a gully beside the kerb. Optional: a palette without it places none. */
    GULLY("gully", true, false),
    /** Crossings (ATR P11): a dropped kerb with tactile paving. Optional: a palette without it places no crossings. */
    DROPPED_KERB("dropped_kerb", true, false),
    VERGE("verge", true, false),
    HARD_STRIP("hard_strip", true, false),
    CENTRAL_RESERVATION("central_reservation", true, false),
    BARRIER("barrier", true, false),
    FOUNDATION("foundation", false, true),
    NODE_MARKER("node_marker", false, true);

    private final String id;
    private final boolean surface;
    private final boolean required;

    PaletteRole(String id, boolean surface, boolean required) {
        this.id = id;
        this.surface = surface;
        this.required = required;
    }

    /** Its name in palette files, such as {@code marking_white}. */
    public String id() {
        return this.id;
    }

    /**
     * Whether it is part of the road's surface, which can stand part-way up a block (a slab, layers); the foundation and
     * the node marker are full blocks only.
     */
    public boolean surface() {
        return this.surface;
    }

    /** Whether every palette must give it: the urban layouts (City street, residential, alley) use it. */
    public boolean required() {
        return this.required;
    }

    /** The role named {@code id} in a palette file. */
    public static Optional<PaletteRole> byId(String id) {
        for (PaletteRole role : values()) {
            if (role.id.equals(id)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /** The role giving {@code column} its blocks: its detail's, else its paint's colour where a line is painted, else its band's. */
    public static PaletteRole of(Column column) {
        String id = column.detail().map(Column.Detail::paletteRole)
                .orElseGet(() -> column.paint().map(colour -> colour.paletteRole()).orElse(column.band().paletteRole()));
        return byId(id).orElseThrow(() -> new IllegalStateException("No palette role called " + id));
    }
}
