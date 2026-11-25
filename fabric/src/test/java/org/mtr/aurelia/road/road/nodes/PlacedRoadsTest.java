/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.solve.LaneNode;

/** The roads placed this visit (task R-6). */
class PlacedRoadsTest {
    static final RoadPlanParams EAST = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E))
            .withNodeAids(RoadPlanParams.NodeAids.WOOL);
    static final RoadPlanParams SOUTH = RoadPlanParams.straight("city-17", End.at(0, 64, 100, Heading.S), End.at(0, 64, 140, Heading.S));

    static BlockPlan plan(RoadPlanParams params) {
        return RoadPlanner.plan(params, WorldLimits.OVERWORLD).plan().orElseThrow();
    }

    @Test
    void roadsAreRememberedSetAsideOnUndoAndBackOnRedo() {
        PlacedRoads roads = new PlacedRoads();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlacedRoads.Road east = roads.placed(first, EAST, plan(EAST)).orElseThrow();
        roads.placed(second, SOUTH, plan(SOUTH));
        assertEquals(List.of(second, first), roads.roads().stream().map(PlacedRoads.Road::operationId).toList(), "newest first");
        assertEquals(RoadPlanner.laneNodes(EAST).orElseThrow(), east.nodes());
        assertEquals(4, east.nodes().size(), "City street: 2 lanes, 2 nodes each");
        assertEquals(40.0, east.length(), 1e-9);
        assertTrue(east.markers());

        roads.undone(second);
        assertEquals(List.of(first), roads.roads().stream().map(PlacedRoads.Road::operationId).toList());
        roads.undone(UUID.randomUUID()); // another operation (a clear, say): nothing changes
        roads.redone(second);
        assertEquals(2, roads.roads().size());
        roads.clear();
        assertEquals(List.of(), roads.roads(), "leaving the world forgets them");
    }

    @Test
    void aRebuildChangesTheRoadAndADeleteSetsItAsideUntilUndone() {
        PlacedRoads roads = new PlacedRoads();
        UUID road = UUID.randomUUID();
        roads.placed(road, EAST, plan(EAST));
        UUID rebuild = UUID.randomUUID();
        RoadPlanParams rebuilt = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, road, EAST, Optional.of(RoadPlanParams.ManualEdits.KEEP));
        PlacedRoads.Road after = roads.placed(rebuild, rebuilt, plan(EAST)).orElseThrow();
        assertEquals(road, after.operationId(), "a rebuild keeps the road's id");
        assertEquals(rebuild, after.latest());
        assertEquals(rebuilt, after.params());
        assertEquals(List.of(road), roads.roads().stream().map(PlacedRoads.Road::operationId).toList(), "still one road");
        assertEquals(RoadPlanner.laneNodes(EAST).orElseThrow(), after.nodes());

        UUID delete = UUID.randomUUID();
        assertEquals(Optional.empty(), roads.placed(delete, RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, road, rebuilt, Optional.empty()), null));
        assertEquals(List.of(), roads.roads(), "deleted");
        roads.undone(delete);
        assertEquals(rebuild, roads.roads().get(0).latest(), "the delete undone: the rebuilt road is back");
        roads.undone(rebuild);
        assertEquals(road, roads.roads().get(0).latest(), "the rebuild undone: the road as first placed");

        roads.replace(List.of(new PlacedRoads.Known(road, EAST, plan(EAST), false), new PlacedRoads.Known(rebuild, rebuilt, plan(EAST), false),
                new PlacedRoads.Known(delete, RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, road, rebuilt, Optional.empty()), null, false)));
        assertEquals(List.of(), roads.roads(), "the server's list, after a restart: deleted");
    }

    @Test
    void onlyStraightAndCurvedRoadsTheSolverAccepts() {
        PlacedRoads roads = new PlacedRoads();
        RoadPlanParams clear = RoadPlanParams.clearMarkers(EAST);
        assertEquals(Optional.empty(), roads.placed(UUID.randomUUID(), clear, plan(EAST)));
        assertEquals(List.of(), roads.roads());
    }

    @Test
    void whichRoadIsAimedAtAndWhichIsNearest() {
        PlacedRoads roads = new PlacedRoads();
        PlacedRoads.Road east = roads.placed(UUID.randomUUID(), EAST, plan(EAST)).orElseThrow();
        PlacedRoads.Road south = roads.placed(UUID.randomUUID(), SOUTH, plan(SOUTH)).orElseThrow();
        LaneNode node = east.nodes().get(1);
        assertEquals(Optional.of(new PlacedRoads.Aimed(east, Optional.of(1))), roads.nodeAt(node.x(), node.y(), node.z()), "its marker spot");
        assertEquals(Optional.of(new PlacedRoads.Aimed(east, Optional.of(1))), roads.nodeAt(node.x(), node.nodeY(), node.z()), "its node block");
        assertEquals(Optional.empty(), roads.nodeAt(node.x(), node.nodeY() + 1, node.z()));
        assertEquals(Optional.of(east), roads.containing(20, 64, 0), "the road's surface");
        assertEquals(Optional.empty(), roads.containing(20, 64, 50));
        assertEquals(Optional.of(south), roads.nearest(5, 120, 48));
        assertEquals(Optional.of(east), roads.nearest(20, 10, 48));
        assertEquals(Optional.empty(), roads.nearest(500, 500, 48));
    }
}
