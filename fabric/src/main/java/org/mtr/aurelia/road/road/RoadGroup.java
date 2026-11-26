/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.Set;

/**
 * The six road groups of Roads v1 (ADR-0012), with their id slugs, allowed widths in blocks and default track speeds.
 * City street is 17 wide (ADR-0039), or 29 with bus lanes; residential 15 or 17 and alley 7 (ADR-0041). The A-road, dual carriageway and
 * motorway widths are proposals until the owner confirms each group's columns (Phase 2).
 */
public enum RoadGroup {
    ALLEY("alley", Set.of(7), 20),
    RESIDENTIAL("residential", Set.of(15, 17), 40),
    CITY_STREET("city", Set.of(17, 29), 40),
    A_ROAD("a-road", Set.of(21), 60),
    DUAL_CARRIAGEWAY("dual", Set.of(33), 80),
    MOTORWAY("motorway", Set.of(51), 120);

    private final String slug;
    private final Set<Integer> widths;
    private final int trackSpeed;

    RoadGroup(String slug, Set<Integer> widths, int trackSpeed) {
        this.slug = slug;
        this.widths = widths;
        this.trackSpeed = trackSpeed;
    }

    /** The first part of a profile id, such as {@code city} in {@code city-17}. */
    public String slug() {
        return this.slug;
    }

    /** The widths this group allows (ADR-0012, amended by ADR-0039 and ADR-0041). */
    public Set<Integer> widths() {
        return this.widths;
    }

    /**
     * The speed of the hidden track Stratos lays along this group's lanes, in km/h, one of MTR's rail-connector speeds
     * (ADR-0040; residential 40, owner 2026-10-07: task G-4+). Task T-2 lays the track.
     */
    public int trackSpeed() {
        return this.trackSpeed;
    }
}
