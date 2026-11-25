/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.RoadPlanParams.End;

/** Where a road stands through placing, rebuilds and deletes, undone or not (task R-7; {@link RoadHistory}). */
class RoadHistoryTest {
    private static final UUID A = new UUID(1, 1);
    private static final UUID R = new UUID(1, 2);
    private static final UUID D = new UUID(1, 3);
    private static final RoadPlanParams ROAD = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));
    private static final RoadPlanParams REBUILD = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, ROAD, Optional.of(RoadPlanParams.ManualEdits.KEEP));
    private static final RoadPlanParams DELETE = RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, A, ROAD, Optional.empty());

    private static Map<UUID, RoadHistory.Road> resolve(RoadHistory.Op... ops) {
        return RoadHistory.resolve(List.of(ops));
    }

    @Test
    void aRebuildKeepsTheRoadsIdAndADeleteRemovesIt() {
        RoadHistory.Road placed = resolve(new RoadHistory.Op(A, ROAD, true)).get(A);
        assertEquals(new RoadHistory.Road(A, ROAD, List.of(A), true, List.of(ROAD), Set.of(), Map.of()), placed);

        RoadHistory.Road rebuilt = resolve(new RoadHistory.Op(A, ROAD, true), new RoadHistory.Op(R, REBUILD, true)).get(A);
        assertEquals(new RoadHistory.Road(A, REBUILD, List.of(A, R), true, List.of(ROAD, REBUILD), Set.of(), Map.of()), rebuilt, "the rebuild's parameters, the road's id");
        assertEquals(R, rebuilt.latest());

        assertEquals(Map.of(), resolve(new RoadHistory.Op(A, ROAD, true), new RoadHistory.Op(R, REBUILD, true), new RoadHistory.Op(D, DELETE, true)));
    }

    @Test
    void undoingARebuildOrDeleteBringsTheRoadBackAsItWas() {
        assertEquals(List.of(A, R), resolve(new RoadHistory.Op(A, ROAD, true), new RoadHistory.Op(R, REBUILD, true),
                new RoadHistory.Op(D, DELETE, false)).get(A).chain(), "the delete undone");
        assertEquals(new RoadHistory.Road(A, ROAD, List.of(A), true, List.of(ROAD), Set.of(), Map.of()), resolve(new RoadHistory.Op(A, ROAD, true), new RoadHistory.Op(R, REBUILD,
                false)).get(A), "the rebuild undone");
        assertEquals(Map.of(), resolve(new RoadHistory.Op(A, ROAD, false)), "the road itself undone");
    }

    @Test
    void aRoadWhosePlacingIsNoLongerKnownIsIncomplete() {
        RoadHistory.Road road = resolve(new RoadHistory.Op(R, REBUILD, true)).get(A);
        assertEquals(List.of(R), road.chain());
        assertFalse(road.complete(), "the cap dropped the placing operation before a restart");
        assertTrue(resolve(new RoadHistory.Op(A, ROAD, true)).get(A).complete());
    }

    @Test
    void onlyRoadOperationsAreAboutARoad() {
        assertEquals(Optional.of(A), RoadHistory.roadOf(A, ROAD));
        assertEquals(Optional.of(A), RoadHistory.roadOf(R, REBUILD));
        assertEquals(Optional.of(A), RoadHistory.roadOf(D, DELETE));
        assertEquals(Optional.empty(), RoadHistory.roadOf(A, RoadPlanParams.clearMarkers(ROAD)));
    }

    @Test
    void theRoadOfAnEditIsTheRoadItEdits() {
        assertEquals(ROAD.road(), REBUILD.road());
        assertEquals(ROAD.road(), DELETE.road());
        assertEquals(RoadPlanParams.Tool.CURVE, ROAD.road().tool());
        assertEquals(Optional.empty(), REBUILD.road().targetRoadId());
        assertEquals(Optional.empty(), REBUILD.road().manualEdits());
        assertTrue(REBUILD.isRoad());
        assertFalse(DELETE.isRoad());
    }
}
