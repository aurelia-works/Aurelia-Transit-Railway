package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.WorldLimits;

/** Upstream BUG-2: a bend whose lanes end staggered threw an internal error from the rasteriser instead of being refused. */
class StaggeredLaneEndsTest {
    /** Upstream's example, second piece: a sharp NNW to WSW bend. The two-lane layouts are refused with a reason. */
    @Test
    void sharpBendIsRefusedNotThrown() {
        RoadPlanParams.End start = RoadPlanParams.End.at(945352, 64, 407326, Heading.NNW);
        RoadPlanParams.End end = RoadPlanParams.End.at(945249, 64, 407283, Heading.WSW);
        for (RoadProfile profile : new RoadProfile[] {RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17}) {
            assertEquals(Optional.of(RoadPlanner.LANE_ENDS_STAGGERED),
                    RoadPlanner.plan(RoadPlanParams.curve(profile.id(), start, end), WorldLimits.OVERWORLD).code(), profile.id());
        }
    }
}
