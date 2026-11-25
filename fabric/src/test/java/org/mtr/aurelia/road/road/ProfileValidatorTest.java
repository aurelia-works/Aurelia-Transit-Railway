/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.road.ProfileValidator.Code;

/** Every validator rule, each proven by a broken fixture that produces exactly its code. */
class ProfileValidatorTest {
    private static final LineMarking CENTRE = LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4);

    static Set<Code> codes(RoadProfile profile) {
        return ProfileValidator.validate(profile).stream().map(ProfileValidator.Problem::code).collect(Collectors.toSet());
    }

    /** City street 17's sides (footway 2, kerb 1) around {@code middle}. */
    private static List<Section> city(Section... middle) {
        List<Section> sections = new ArrayList<>(List.of(Section.of(Band.FOOTWAY, 2), Section.of(Band.KERB, 1)));
        sections.addAll(List.of(middle));
        sections.addAll(List.of(Section.of(Band.KERB, 1), Section.of(Band.FOOTWAY, 2)));
        return sections;
    }

    private static List<Section> cityMiddle() {
        return city(Section.of(Band.LANE, 5), Section.marked(Band.CENTRE_LINE, 1, CENTRE), Section.of(Band.LANE, 5));
    }

    @Test
    void theFixturesStartFromAValidProfile() {
        assertEquals(Set.of(), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET, cityMiddle())));
    }

    @Test
    void badIdFormat() {
        assertEquals(Set.of(Code.ID_FORMAT), codes(new RoadProfile("City-17", RoadGroup.CITY_STREET, cityMiddle())));
        assertEquals(Set.of(Code.ID_FORMAT), codes(new RoadProfile("city_17", RoadGroup.CITY_STREET, cityMiddle())));
    }

    @Test
    void idMustBeTheGroupSlugAndTheWidth() {
        assertEquals(Set.of(Code.ID_WIDTH_MISMATCH), codes(new RoadProfile("city-21", RoadGroup.CITY_STREET, cityMiddle())));
        assertEquals(Set.of(Code.ID_WIDTH_MISMATCH), codes(new RoadProfile("a-road-17", RoadGroup.CITY_STREET, cityMiddle())));
    }

    @Test
    void widthMustBeOneTheGroupAllows() {
        List<Section> wider = new ArrayList<>(cityMiddle());
        wider.set(0, Section.of(Band.FOOTWAY, 3));
        wider.set(wider.size() - 1, Section.of(Band.FOOTWAY, 3));
        assertEquals(Set.of(Code.WIDTH_NOT_ALLOWED), codes(new RoadProfile("city-19", RoadGroup.CITY_STREET, wider)));
    }

    @Test
    void sectionsAreAtLeastOneWide() {
        List<Section> sections = new ArrayList<>(cityMiddle());
        sections.add(2, Section.of(Band.EDGE, 0));
        sections.add(sections.size() - 2, Section.of(Band.EDGE, 0));
        assertEquals(Set.of(Code.EMPTY_SECTION), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET, sections)));
    }

    @Test
    void profilesMustBeSymmetric() {
        List<Section> sections = new ArrayList<>(cityMiddle());
        sections.set(0, Section.of(Band.KERB, 1));
        sections.set(1, Section.of(Band.FOOTWAY, 2)); // the left side now reads kerb, footway; the right footway, kerb
        assertEquals(Set.of(Code.NOT_SYMMETRIC), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET, sections)));
        // A marking on one side only also breaks symmetry.
        assertEquals(Set.of(Code.NOT_SYMMETRIC), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET,
                city(Section.marked(Band.EDGE, 5, CENTRE), Section.marked(Band.CENTRE_LINE, 1, CENTRE), Section.of(Band.LANE, 5)))));
    }

    @Test
    void aProfileNeedsLanes() {
        assertEquals(Set.of(Code.NO_LANES), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET,
                city(Section.of(Band.EDGE, 5), Section.marked(Band.CENTRE_LINE, 1, CENTRE), Section.of(Band.EDGE, 5)))));
    }

    @Test
    void lanesAreFiveWide() {
        assertEquals(Set.of(Code.LANE_WIDTH), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET, List.of(
                Section.of(Band.KERB, 1), Section.of(Band.LANE, 7), Section.marked(Band.CENTRE_LINE, 1, CENTRE),
                Section.of(Band.LANE, 7), Section.of(Band.KERB, 1)))));
    }

    @Test
    void aLaneIsNeverTheOutermostSection() {
        assertEquals(Set.of(Code.LANE_AT_EDGE), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET, List.of(
                Section.of(Band.LANE, 5), Section.of(Band.EDGE, 3), Section.marked(Band.CENTRE_LINE, 1, CENTRE),
                Section.of(Band.EDGE, 3), Section.of(Band.LANE, 5)))));
    }

    @Test
    void theCentreLineIsOneWide() {
        // A 3-wide centre keeps the width odd (17) and the lanes on block centres (±4), so only the centre's width is wrong.
        assertEquals(Set.of(Code.CENTRE_LINE_WIDTH), codes(new RoadProfile("residential-17", RoadGroup.RESIDENTIAL, List.of(
                Section.of(Band.FOOTWAY, 1), Section.of(Band.KERB, 1), Section.of(Band.LANE, 5), Section.of(Band.CENTRE_LINE, 3),
                Section.of(Band.LANE, 5), Section.of(Band.KERB, 1), Section.of(Band.FOOTWAY, 1)))));
    }

    @Test
    void everyProfileIsAnOddWidth() {
        // ADR-0041: two 5-wide lanes side by side make an even width, whose centre line runs along a block edge.
        assertEquals(Set.of(Code.EVEN_WIDTH, Code.WIDTH_NOT_ALLOWED), codes(RoadProfileTest.RESIDENTIAL_14));
    }

    @Test
    void onlyCentreLinesAndEdgesCarryMarkings() {
        assertEquals(Set.of(Code.MARKING_ON_WRONG_BAND), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET,
                city(Section.marked(Band.LANE, 5, CENTRE), Section.marked(Band.CENTRE_LINE, 1, CENTRE), Section.marked(Band.LANE, 5, CENTRE)))));
        // Solid edge lines are allowed (later profiles may use them).
        List<Section> edged = List.of(Section.of(Band.FOOTWAY, 1), Section.of(Band.KERB, 1),
                Section.marked(Band.EDGE, 1, LineMarking.solid(LineMarking.Colour.WHITE)), Section.of(Band.LANE, 5),
                Section.marked(Band.CENTRE_LINE, 1, CENTRE), Section.of(Band.LANE, 5),
                Section.marked(Band.EDGE, 1, LineMarking.solid(LineMarking.Colour.WHITE)), Section.of(Band.KERB, 1),
                Section.of(Band.FOOTWAY, 1));
        assertEquals(Set.of(), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET, edged)));
    }

    @Test
    void patternsAreWholeBlocks() {
        for (LineMarking bad : List.of(LineMarking.dashed(LineMarking.Colour.WHITE, 0, 4), LineMarking.dashed(LineMarking.Colour.WHITE, 2, -1))) {
            assertEquals(Set.of(Code.BAD_PATTERN), codes(new RoadProfile("city-17", RoadGroup.CITY_STREET,
                    city(Section.of(Band.LANE, 5), Section.marked(Band.CENTRE_LINE, 1, bad), Section.of(Band.LANE, 5)))), bad.toString());
        }
    }

    @Test
    void everyProblemNamesTheProfile() {
        for (ProfileValidator.Problem problem : ProfileValidator.validate(new RoadProfile("city-23", RoadGroup.CITY_STREET, List.of()))) {
            assertTrue(problem.message().contains("city-23"), problem.toString());
        }
    }
}
