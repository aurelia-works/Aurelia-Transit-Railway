/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;

/** The built-in profiles and the parity rule (spec roads-v1 §2, ADR-0012, ADR-0039, ADR-0041). */
class RoadProfileTest {
    /**
     * ADR-0012's residential 14 (footway 1 · kerb 1 · lane 5 · lane 5 · kerb 1 · footway 1), an even width that ADR-0041
     * replaced: a test fixture for the lane maths and the even-width rule.
     */
    static final RoadProfile RESIDENTIAL_14 = new RoadProfile("residential-14", RoadGroup.RESIDENTIAL, List.of(
            Section.of(Band.FOOTWAY, 1), Section.of(Band.KERB, 1), Section.of(Band.LANE, 5),
            Section.of(Band.LANE, 5), Section.of(Band.KERB, 1), Section.of(Band.FOOTWAY, 1)));

    @Test
    void cityStreetValidates() {
        RoadProfile city = RoadProfiles.CITY_17;
        assertEquals(List.of(), ProfileValidator.validate(city));
        assertSame(city, RoadProfiles.byId("city-17").orElseThrow());
        assertTrue(RoadProfiles.all().contains(city));
        assertFalse(RoadProfiles.byId("city-21").isPresent(), "the 21-wide City street is gone (ADR-0039)");

        assertEquals(17, city.width());
        assertEquals(RoadGroup.CITY_STREET, city.group());
        assertTrue(city.referenceOnBlockCentre());
        assertEquals(List.of(
                new Lane(0, -6, Lane.Direction.WITH_REFERENCE),
                new Lane(1, 6, Lane.Direction.AGAINST_REFERENCE)), city.lanes());
        assertEquals(-3, city.lanes().get(0).offset());
        assertEquals(3, city.lanes().get(1).offset());

        // The centre line is the middle column (8 of 0..16), dashed white 2 on / 4 off; no other markings.
        int start = 0;
        int markedSections = 0;
        for (Section section : city.sections()) {
            if (section.band() == Band.CENTRE_LINE) {
                assertEquals(8, start);
                assertEquals(1, section.width());
                assertEquals(LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4), section.marking().orElseThrow());
            }
            markedSections += section.marking().isPresent() ? 1 : 0;
            start += section.width();
        }
        assertEquals(1, markedSections);
        // Footway 3 = footway 2 + its kerb next to the road, on both sides; no edge strips (ADR-0039).
        assertEquals(List.of(Band.FOOTWAY, Band.KERB, Band.LANE, Band.CENTRE_LINE, Band.LANE, Band.KERB, Band.FOOTWAY),
                city.sections().stream().map(Section::band).collect(Collectors.toList()));
        assertEquals("surface", Band.LANE.paletteRole());
        assertEquals("kerb", Band.KERB.paletteRole());
        assertEquals("marking_white", LineMarking.Colour.WHITE.paletteRole());
    }

    @Test
    void residentialAndAlleyValidate() {
        // ADR-0041 (owner, 2026-10-07): footway, kerb, lane 5, a plain centre column, lane 5, kerb, footway.
        for (RoadProfile profile : List.of(RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17)) {
            int footway = profile == RoadProfiles.RESIDENTIAL_15 ? 1 : 2;
            assertEquals(List.of(), ProfileValidator.validate(profile));
            assertSame(profile, RoadProfiles.byId(profile.id()).orElseThrow());
            assertEquals(RoadGroup.RESIDENTIAL, profile.group());
            assertEquals(2 * footway + 13, profile.width());
            assertEquals(List.of(Section.of(Band.FOOTWAY, footway), Section.of(Band.KERB, 1), Section.of(Band.LANE, 5), Section.of(Band.CENTRE_LINE, 1),
                    Section.of(Band.LANE, 5), Section.of(Band.KERB, 1), Section.of(Band.FOOTWAY, footway)), profile.sections());
            assertTrue(profile.sections().stream().allMatch(section -> section.marking().isEmpty()), "no centre line is painted");
            assertEquals(RoadProfiles.CITY_17.lanes(), profile.lanes(), "the lanes sit at ±3, as City street's do");
        }
        RoadProfile alley = RoadProfiles.ALLEY_7;
        assertEquals(List.of(), ProfileValidator.validate(alley));
        assertSame(alley, RoadProfiles.byId("alley-7").orElseThrow());
        assertEquals(List.of(Section.of(Band.GUTTER, 1), Section.of(Band.LANE, 5), Section.of(Band.GUTTER, 1)), alley.sections());
        assertEquals(List.of(new Lane(0, 0, Lane.Direction.BOTH)), alley.lanes(), "one shared lane on the centre line");
        assertEquals("gutter", Band.GUTTER.paletteRole());

        assertEquals(List.of("city-17", "city-29", "residential-15", "residential-17", "alley-7", "a-road-21", "dual-33"),
                RoadProfiles.all().stream().map(RoadProfile::id).collect(Collectors.toList()), "the hub's order");
        assertFalse(RoadProfiles.byId("residential-14").isPresent(), "ADR-0041");
        assertFalse(RoadProfiles.byId("residential-16").isPresent(), "ADR-0041");
    }

    @Test
    void parityRuleOffsetsAreWholeForOddWidthsAndHalvesForEvenWidths() {
        for (RoadProfile profile : RoadProfiles.all()) {
            assertEquals(1, profile.width() % 2, profile.id() + " is an odd width (ADR-0041)");
            assertTrue(profile.referenceOnBlockCentre(), profile.id());
            for (Lane lane : profile.lanes()) {
                assertEquals(0, lane.offsetHalfBlocks() % 2, profile.id() + " lane " + lane.index());
            }
        }
        // The lane maths still gives an even width's half-block offsets; the validator refuses the width itself.
        assertFalse(RESIDENTIAL_14.referenceOnBlockCentre());
        assertEquals(List.of(-2.5, 2.5), RESIDENTIAL_14.lanes().stream().map(Lane::offset).collect(Collectors.toList()));
        for (Lane lane : RESIDENTIAL_14.lanes()) {
            assertEquals(1, StrictMath.abs(lane.offsetHalfBlocks() % 2), "residential-14 lane " + lane.index());
        }
    }

    @Test
    void parityRuleEveryLaneCentreIsABlockCentreOnTheCardinals() {
        BlockXZ anchor = new BlockXZ(7, -3);
        List<RoadProfile> profiles = new java.util.ArrayList<>(RoadProfiles.all());
        profiles.add(RESIDENTIAL_14);
        for (RoadProfile profile : profiles) {
            for (Heading heading : List.of(Heading.E, Heading.S, Heading.W, Heading.N)) {
                Vec2 normal = heading.normal();
                // Odd widths: the reference line runs through the anchor block's centre. Even widths: along the block
                // edge half a block to the right of it.
                Vec2 reference = anchor.centre().plus(normal.times(profile.referenceOnBlockCentre() ? 0 : 0.5));
                for (Lane lane : profile.lanes()) {
                    Vec2 centre = reference.plus(normal.times(lane.offset()));
                    assertEquals(centre, centre.block().centre(), profile.id() + " " + heading + " lane " + lane.index());
                }
            }
        }
    }

    @Test
    void aFourWideLaneBreaksTheParityRule() {
        RoadProfile bad = new RoadProfile("city-17", RoadGroup.CITY_STREET, List.of(
                Section.of(Band.FOOTWAY, 2), Section.of(Band.KERB, 1), Section.of(Band.EDGE, 1), Section.of(Band.LANE, 4),
                Section.marked(Band.CENTRE_LINE, 1, LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4)),
                Section.of(Band.LANE, 4), Section.of(Band.EDGE, 1), Section.of(Band.KERB, 1), Section.of(Band.FOOTWAY, 2)));
        assertEquals(17, bad.width());
        assertEquals(Set.of(ProfileValidator.Code.LANE_WIDTH, ProfileValidator.Code.LANE_OFF_BLOCK_CENTRE),
                ProfileValidatorTest.codes(bad));
        assertEquals(2.5, bad.lanes().get(1).offset(), "the lane centre falls on a block edge");
    }
}
