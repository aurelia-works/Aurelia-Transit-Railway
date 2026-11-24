/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks a road profile against the rules of spec roads-v1 §2 and ADR-0012. These catch mistakes in Stratos's own
 * profile definitions; they are not the player-facing road checks of §12.
 */
public final class ProfileValidator {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    /** Lanes are 5 blocks wide in v1 (ADR-0012). */
    public static final int LANE_WIDTH = 5;

    /** What can be wrong with a profile. */
    public enum Code {
        ID_FORMAT,
        ID_WIDTH_MISMATCH,
        WIDTH_NOT_ALLOWED,
        EVEN_WIDTH,
        EMPTY_SECTION,
        NOT_SYMMETRIC,
        NO_LANES,
        LANE_WIDTH,
        LANE_OFF_BLOCK_CENTRE,
        LANE_AT_EDGE,
        CENTRE_LINE_WIDTH,
        MARKING_ON_WRONG_BAND,
        BAD_PATTERN
    }

    /** One problem: its code and a plain description naming the profile. */
    public record Problem(Code code, String message) {
    }

    private ProfileValidator() {
    }

    /** Every problem with {@code profile}; an empty list means it is valid. */
    public static List<Problem> validate(RoadProfile profile) {
        List<Problem> problems = new ArrayList<>();
        String id = profile.id();
        int width = profile.width();
        List<Section> sections = profile.sections();

        if (!ID.matcher(id).matches()) {
            problems.add(new Problem(Code.ID_FORMAT, "'" + id + "': ids are lowercase words joined by hyphens, such as city-17"));
        } else if (!id.equals(profile.group().slug() + "-" + width)) {
            problems.add(new Problem(Code.ID_WIDTH_MISMATCH, "'" + id + "': a " + width + "-wide " + profile.group()
                    + " profile must be called " + profile.group().slug() + "-" + width));
        }
        if (!profile.group().widths().contains(width)) {
            problems.add(new Problem(Code.WIDTH_NOT_ALLOWED, id + ": " + profile.group() + " allows widths "
                    + profile.group().widths() + ", not " + width + " (ADR-0012)"));
        }
        if (width % 2 == 0) {
            problems.add(new Problem(Code.EVEN_WIDTH, id + " is " + width + " wide; every profile is an odd width, so its centre line runs"
                    + " through block centres as the lane-node geometry needs (ADR-0041)"));
        }
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).width() < 1) {
                problems.add(new Problem(Code.EMPTY_SECTION, id + ": section " + i + " (" + sections.get(i).band() + ") is " + sections.get(i).width() + " wide"));
            }
        }
        if (!sections.equals(reversed(sections))) {
            problems.add(new Problem(Code.NOT_SYMMETRIC, id + ": the sections must read the same from both ends, so the road can be mirrored (§11.4)"));
        }

        checkLanes(id, sections, problems);
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (section.band() == Band.CENTRE_LINE && section.width() != 1) {
                problems.add(new Problem(Code.CENTRE_LINE_WIDTH, id + ": the centre line is " + section.width() + " wide; it must be 1"));
            }
            if (section.marking().isPresent()) {
                if (section.band() != Band.CENTRE_LINE && section.band() != Band.EDGE) {
                    problems.add(new Problem(Code.MARKING_ON_WRONG_BAND, id + ": section " + i + " (" + section.band() + ") has a line marking; only centre lines and edges carry one"));
                }
                LineMarking marking = section.marking().get();
                if (marking.mark() < 1 || marking.gap() < 0) {
                    problems.add(new Problem(Code.BAD_PATTERN, id + ": section " + i + " has the pattern " + marking.mark() + "/" + marking.gap()
                            + "; marks are at least 1 block and gaps at least 0"));
                }
            }
        }
        return List.copyOf(problems);
    }

    /** Lanes: at least one, each 5 wide, centred on a block centre (the parity rule), never the outermost section. */
    private static void checkLanes(String id, List<Section> sections, List<Problem> problems) {
        int start = 0;
        int lanes = 0;
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (section.band() == Band.LANE) {
                lanes++;
                if (section.width() != LANE_WIDTH) {
                    problems.add(new Problem(Code.LANE_WIDTH, id + ": lane " + (lanes - 1) + " is " + section.width() + " wide; lanes are " + LANE_WIDTH + " (ADR-0012)"));
                }
                // Block centres are an odd number of half blocks from the road's left edge, which is a block edge.
                if ((2 * start + section.width()) % 2 == 0) {
                    problems.add(new Problem(Code.LANE_OFF_BLOCK_CENTRE, id + ": lane " + (lanes - 1)
                            + "'s centre is on a block edge, so its MTR nodes couldn't sit on block centres (parity rule, §2)"));
                }
                if (i == 0 || i == sections.size() - 1) {
                    problems.add(new Problem(Code.LANE_AT_EDGE, id + ": lane " + (lanes - 1) + " is the outermost section; a road needs a band beyond its lanes, such as a kerb, verge or gutter"));
                }
            }
            start += section.width();
        }
        if (lanes == 0) {
            problems.add(new Problem(Code.NO_LANES, id + ": a road profile needs at least one lane"));
        }
    }

    private static List<Section> reversed(List<Section> sections) {
        List<Section> copy = new ArrayList<>(sections);
        java.util.Collections.reverse(copy);
        return copy;
    }
}
