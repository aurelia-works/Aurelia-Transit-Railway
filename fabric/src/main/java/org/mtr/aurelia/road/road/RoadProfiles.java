/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in road profiles (owner, 2026-10-04: profiles are built into the code). Client and server run the same
 * jar, so a {@code profileId} always means the same cross-section on both. Every profile is validated when this
 * class loads; a broken one stops Stratos loudly instead of placing a wrong road.
 */
public final class RoadProfiles {
    /**
     * City street, 17 wide (spec roads-v1 §2, ADR-0039): footway 3 (footway 2 + its kerb next to the road) · lane 5 ·
     * centre line 1 · lane 5 · footway 3, with no edge strips (owner, 2026-10-05). The centre line is dashed white,
     * 2 on / 4 off (UK, 40 mph and under; §7.2). The lane centres are 3 blocks either side of it.
     */
    public static final RoadProfile CITY_17 = new RoadProfile("city-17", RoadGroup.CITY_STREET, List.of(
            Section.of(Band.FOOTWAY, 2),
            Section.of(Band.KERB, 1),
            Section.of(Band.LANE, 5),
            Section.marked(Band.CENTRE_LINE, 1, LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4)),
            Section.of(Band.LANE, 5),
            Section.of(Band.KERB, 1),
            Section.of(Band.FOOTWAY, 2)));

    /**
     * Residential, 15 wide (ADR-0041): footway 1 · kerb 1 · lane 5 · centre 1 · lane 5 · kerb 1 · footway 1. The centre
     * column is plain asphalt with no line (owner, 2026-10-07), so the lanes sit at ±3 like City street's.
     */
    public static final RoadProfile RESIDENTIAL_15 = residential(1);

    /** Residential, 17 wide (ADR-0041): City street's columns with a plain centre column and 2-block footways. */
    public static final RoadProfile RESIDENTIAL_17 = residential(2);

    /**
     * Alley, 7 wide (ADR-0041 confirms ADR-0012's columns): gutter 1 · shared lane 5 · gutter 1, two-way, with no
     * markings. Its one lane is on the centre line, so its nodes are the road's own end nodes.
     */
    public static final RoadProfile ALLEY_7 = new RoadProfile("alley-7", RoadGroup.ALLEY, List.of(
            Section.of(Band.GUTTER, 1),
            Section.of(Band.LANE, 5),
            Section.of(Band.GUTTER, 1)));

    /**
     * City street with bus lanes, 29 wide: footway 3 · bus lane 5 · solid white line 1 · lane 5 · centre line 1 · lane 5 ·
     * solid white line 1 · bus lane 5 · footway 3. The bus lanes run along the kerbs, kept apart from the general lanes by
     * an unbroken white line (UK bus lane boundary). Its centre line is City street's.
     */
    public static final RoadProfile CITY_BUS_29 = new RoadProfile("city-29", RoadGroup.CITY_STREET, List.of(
            Section.of(Band.FOOTWAY, 2),
            Section.of(Band.KERB, 1),
            Section.of(Band.LANE, 5),
            Section.marked(Band.EDGE, 1, LineMarking.solid(LineMarking.Colour.WHITE)),
            Section.of(Band.LANE, 5),
            Section.marked(Band.CENTRE_LINE, 1, LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4)),
            Section.of(Band.LANE, 5),
            Section.marked(Band.EDGE, 1, LineMarking.solid(LineMarking.Colour.WHITE)),
            Section.of(Band.LANE, 5),
            Section.of(Band.KERB, 1),
            Section.of(Band.FOOTWAY, 2)));

    /**
     * Rural road, 21 wide (the A-road group): gutter 2 · hard strip 3 · lane 5 · centre line 1 · lane 5 · hard strip 3 ·
     * gutter 2, with no kerbs or footways. The centre line is dashed longer than in town, 4 on / 8 off, as on UK roads
     * above 40 mph. Edge lines wait for edge-line markings outside the lanes (RoadPiece doesn't draw them yet).
     */
    public static final RoadProfile RURAL_21 = new RoadProfile("a-road-21", RoadGroup.A_ROAD, List.of(
            Section.of(Band.GUTTER, 2),
            Section.of(Band.HARD_SHOULDER, 3),
            Section.of(Band.LANE, 5),
            Section.marked(Band.CENTRE_LINE, 1, LineMarking.dashed(LineMarking.Colour.WHITE, 4, 8)),
            Section.of(Band.LANE, 5),
            Section.of(Band.HARD_SHOULDER, 3),
            Section.of(Band.GUTTER, 2)));

    /**
     * Highway, 33 wide (the dual carriageway group): two lanes each way. Gutter 1 · hard shoulder 3 · lane 5 · lane line 1 ·
     * lane 5 · solid white line 1 · central strip 1 · solid white line 1 · lane 5 · lane line 1 · lane 5 · hard shoulder 3 ·
     * gutter 1. The lane lines are dashed 3 on / 9 off, as on UK high-speed roads.
     */
    public static final RoadProfile HIGHWAY_33 = new RoadProfile("dual-33", RoadGroup.DUAL_CARRIAGEWAY, List.of(
            Section.of(Band.GUTTER, 1),
            Section.of(Band.HARD_SHOULDER, 3),
            Section.of(Band.LANE, 5),
            Section.marked(Band.EDGE, 1, LineMarking.dashed(LineMarking.Colour.WHITE, 3, 9)),
            Section.of(Band.LANE, 5),
            Section.marked(Band.EDGE, 1, LineMarking.solid(LineMarking.Colour.WHITE)),
            Section.of(Band.GUTTER, 1),
            Section.marked(Band.EDGE, 1, LineMarking.solid(LineMarking.Colour.WHITE)),
            Section.of(Band.LANE, 5),
            Section.marked(Band.EDGE, 1, LineMarking.dashed(LineMarking.Colour.WHITE, 3, 9)),
            Section.of(Band.LANE, 5),
            Section.of(Band.HARD_SHOULDER, 3),
            Section.of(Band.GUTTER, 1)));

    private static final Map<String, RoadProfile> BY_ID = new LinkedHashMap<>();

    static {
        // Registration order is the order the hub lists them in.
        for (RoadProfile profile : List.of(CITY_17, CITY_BUS_29, RESIDENTIAL_15, RESIDENTIAL_17, ALLEY_7, RURAL_21, HIGHWAY_33)) {
            List<ProfileValidator.Problem> problems = ProfileValidator.validate(profile);
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Built-in road profile " + profile.id() + " is invalid: " + problems);
            }
            if (BY_ID.put(profile.id(), profile) != null) {
                throw new IllegalStateException("Two built-in road profiles are called " + profile.id());
            }
        }
    }

    private RoadProfiles() {
    }

    private static RoadProfile residential(int footway) {
        int width = 2 * footway + 2 + 5 + 1 + 5;
        return new RoadProfile("residential-" + width, RoadGroup.RESIDENTIAL, List.of(
                Section.of(Band.FOOTWAY, footway),
                Section.of(Band.KERB, 1),
                Section.of(Band.LANE, 5),
                Section.of(Band.CENTRE_LINE, 1),
                Section.of(Band.LANE, 5),
                Section.of(Band.KERB, 1),
                Section.of(Band.FOOTWAY, footway)));
    }

    /** The built-in profile with this id, such as {@code city-17}. */
    public static Optional<RoadProfile> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** Every built-in profile, in registration order. */
    public static List<RoadProfile> all() {
        return List.copyOf(BY_ID.values());
    }
}
