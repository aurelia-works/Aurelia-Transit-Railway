/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

/**
 * A column type of a road profile (spec roads-v1 §2), with the palette role that will give it its blocks (§8,
 * task R-9). The rasteriser classifies every column of a road into one band (§7.1).
 */
public enum Band {
    LANE("surface"),
    CENTRE_LINE("surface"),
    EDGE("surface"),
    HARD_STRIP("hard_strip"),
    KERB("kerb"),
    FOOTWAY("footway"),
    VERGE("verge"),
    HARD_SHOULDER("surface"),
    CENTRAL_RESERVATION("central_reservation"),
    BARRIER("barrier"),
    GUTTER("gutter");

    private final String paletteRole;

    Band(String paletteRole) {
        this.paletteRole = paletteRole;
    }

    /** The palette role name of §8, such as {@code surface} or {@code kerb}. Line markings use their own roles. */
    public String paletteRole() {
        return this.paletteRole;
    }
}
