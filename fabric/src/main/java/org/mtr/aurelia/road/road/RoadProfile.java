/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A road's cross-section (spec roads-v1 §2): its id (such as {@code city-17}), its group and its sections from left to
 * right. The reference line runs down the middle: through the centre of the middle column for odd widths, along the
 * block edge between the two middle columns for even widths. Built-in profiles live in {@link RoadProfiles} and are
 * checked by {@link ProfileValidator}; a profile made here may be invalid until it is validated.
 */
public record RoadProfile(String id, RoadGroup group, List<Section> sections) {
    public RoadProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(group, "group");
        sections = List.copyOf(sections);
    }

    /** The total width in blocks. */
    public int width() {
        int width = 0;
        for (Section section : this.sections) {
            width += section.width();
        }
        return width;
    }

    /** True for odd widths: the reference line runs through a block centre. False for even widths: along a block edge. */
    public boolean referenceOnBlockCentre() {
        return this.width() % 2 == 1;
    }

    /**
     * The lanes, left to right. A lane's centre is {@code 2·start + width} half blocks from the road's left edge and
     * the reference line is {@code width()} half blocks from it, so the offset is the difference (exact).
     */
    public List<Lane> lanes() {
        List<Lane> lanes = new ArrayList<>();
        int total = this.width();
        int start = 0;
        for (Section section : this.sections) {
            if (section.band() == Band.LANE) {
                int offset = 2 * start + section.width() - total;
                lanes.add(new Lane(lanes.size(), offset, Lane.Direction.of(offset)));
            }
            start += section.width();
        }
        return List.copyOf(lanes);
    }
}
