/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import java.util.Objects;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Lane;
import org.mtr.aurelia.road.road.RoadPlanParams;

/**
 * One lane of a solved piece: its number (1 is the leftmost), the profile lane, its two nodes (block centres, with the
 * piece's headings) and the MTR rail between them, which a bus on hidden track follows.
 */
public record LaneRail(int number, Lane lane, RoadPlanParams.End start, RoadPlanParams.End end, RailMath rail) {
    public LaneRail {
        if (number < 1) {
            throw new IllegalArgumentException("Lanes are numbered from 1, not " + number);
        }
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(rail, "rail");
    }
}
