/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadHistory;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;

/**
 * G-10's road graph and R-10+'s edit of a joined road: joins appear and go with the roads standing (a deleted road's
 * ends are free again, which the junction refusal and the edit tool's pins rely on), the chunk index finds ends across
 * a chunk border, and a road another road joins is edited as one rebuild that keeps that join, then undone.
 */
class RoadGraphTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID EDIT = new UUID(0, 3);

    /** City street A from (0, 64, 0) to (30, 64, 0) eastward, and B carrying on from A's end to (60, 64, 0). */
    private static Map<UUID, RoadPlanParams> twoRoads() {
        RoadPlanParams a = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E));
        RoadPlanParams b = RoadPlanParams.curve("city-17", new End(30, 64, 0, Heading.E, Optional.of(A), OptionalInt.of(1)),
                End.at(60, 64, 0, Heading.E));
        Map<UUID, RoadPlanParams> roads = new LinkedHashMap<>();
        roads.put(A, a);
        roads.put(B, b.withDashes(RoadJoins.dashes(b, id -> id.equals(A) ? RoadJoins.ports(a) : Optional.empty())));
        return roads;
    }

    @Test
    void joinsComeAndGoWithTheRoads() {
        Map<UUID, RoadPlanParams> roads = twoRoads();
        RoadGraph graph = RoadGraph.of(roads);
        assertEquals(List.of(new RoadGraph.Join(B, 0, A, 1)), graph.joins());
        assertTrue(graph.taken(A, 1));
        assertTrue(graph.taken(B, 0));
        assertFalse(graph.taken(A, 0));
        assertFalse(graph.taken(B, 1));
        assertEquals(Set.of(B), graph.neighbours(A));
        assertEquals(List.of(new RoadGraph.Join(B, 0, A, 1)), graph.incoming(A));

        roads.remove(A); // A deleted: B's start is free again
        RoadGraph split = RoadGraph.of(roads);
        assertTrue(split.joins().isEmpty());
        assertFalse(split.taken(B, 0));
        assertTrue(split.neighbours(B).isEmpty());
    }

    @Test
    void theChunkIndexFindsEndsAcrossAChunkBorder() {
        Map<UUID, RoadPlanParams> roads = twoRoads();
        UUID far = new UUID(0, 9);
        roads.put(far, RoadPlanParams.curve("city-17", End.at(-17, 64, 300, Heading.N), End.at(-17, 64, 270, Heading.N)));
        RoadGraph graph = RoadGraph.of(roads);

        List<RoadGraph.Port> joint = graph.portsNear(31, 1, 4);
        assertEquals(List.of(A, B), joint.stream().map(RoadGraph.Port::road).toList());
        assertEquals(List.of(1, 0), joint.stream().map(RoadGraph.Port::index).toList());

        List<RoadGraph.Port> across = graph.portsNear(-15, 299, 4); // chunk -1, the port is in chunk -2
        assertEquals(1, across.size());
        assertEquals(far, across.get(0).road());
        assertEquals(Heading.S, across.get(0).outgoing()); // a start's outgoing heading is its heading turned round
        assertTrue(graph.portsNear(-15, 299, 1).isEmpty());
    }

    @Test
    void aJoinedRoadIsEditedKeepingItsJoinAndUndone() {
        Map<UUID, RoadPlanParams> placed = twoRoads();
        RoadPlanParams a = placed.get(A);

        // A's free start moves 10 blocks back; its end, where B starts, stays.
        RoadPlanParams moved = a.withEnds(List.of(End.at(-10, 64, 0, Heading.E), a.ends().get(1)));
        RoadPlanParams.Dashes dashes = RoadJoins.editDashes(moved, id -> Optional.empty(), RoadJoins.incoming(A, placed));
        RoadPlanParams edited = moved.withDashes(dashes);
        PlanResult result = RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, edited, Optional.empty()), WorldLimits.OVERWORLD,
                placed);
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        assertEquals(RoadPlanParams.Dashes.Anchor.END, dashes.at()); // B's dashes carry on from A's end as before

        // Moving the end B joins would pull A away from B.
        RoadPlanParams pulled = a.withEnds(List.of(a.ends().get(0), End.at(34, 64, 0, Heading.E)));
        PlanResult refused = RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, pulled, Optional.empty()), WorldLimits.OVERWORLD,
                placed);
        assertEquals(Optional.of(RoadPlanner.ROAD_JOINED), refused.code());

        // The edit is one operation: undone, A stands as placed and B is still joined to it.
        RoadPlanParams rebuild = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, edited, Optional.empty());
        List<RoadHistory.Op> applied = List.of(new RoadHistory.Op(A, a, true), new RoadHistory.Op(B, placed.get(B), true),
                new RoadHistory.Op(EDIT, rebuild, true));
        assertEquals(edited.road(), RoadHistory.resolve(applied).get(A).params().road());
        List<RoadHistory.Op> undone = List.of(applied.get(0), applied.get(1), new RoadHistory.Op(EDIT, rebuild, false));
        Map<UUID, RoadPlanParams> after = RoadHistory.params(RoadHistory.resolve(undone));
        assertEquals(a, after.get(A));
        assertTrue(RoadGraph.of(after).taken(A, 1));
    }
}
