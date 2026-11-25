/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.RoadPlanParams.Dashes;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/** Roads joined end to end (task R-5): road ends, the planner's checks of a joined end, the dash rules, turning a road. */
class RoadJoinsTest {
    private static final WorldLimits WORLD = WorldLimits.OVERWORLD;
    private static final UUID A = new UUID(1, 1);
    /** A City street 40 blocks east from (0, 64, 0): its start runs on west, its end east. */
    private static final RoadPlanParams ROAD_A = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));
    private static final Map<UUID, RoadPlanParams> PLACED = Map.of(A, ROAD_A);

    private static End joined(int x, int y, int z, Heading heading, UUID road, int port) {
        return new End(x, y, z, heading, Optional.of(road), OptionalInt.of(port));
    }

    /** A straight road from {@code start} to {@code end} with the dashes its joins call for. */
    private static RoadPlanParams straight(String profile, End start, End end) {
        RoadPlanParams params = RoadPlanParams.straight(profile, start, end);
        return params.withDashes(RoadJoins.dashes(params, id -> Optional.ofNullable(PLACED.get(id)).flatMap(RoadJoins::ports)));
    }

    private static void assertRefused(String code, Map<String, String> args, PlanResult result) {
        assertFalse(result.isPlanned(), "refused");
        assertEquals(Optional.of(code), result.code());
        assertEquals(args, result.args());
    }

    @Test
    void aRoadHasTwoEndsWithTheirHeadingsAndDashPhases() {
        assertEquals(List.of(new RoadJoins.Port(0, 0, 64, 0, Heading.W, "city-17", 0), new RoadJoins.Port(1, 40, 64, 0, Heading.E, "city-17", 40)),
                RoadJoins.ports(ROAD_A).orElseThrow());
        // A road whose dashes are timed to its end: its start is that phase less its length.
        RoadPlanParams intoA = RoadPlanParams.straight("city-17", End.at(-30, 64, 0, Heading.E), End.at(0, 64, 0, Heading.E))
                .withDashes(new Dashes(0, Dashes.Anchor.END));
        assertEquals(List.of(-30.0, 0.0), RoadJoins.ports(intoA).orElseThrow().stream().map(RoadJoins.Port::phase).toList());
        // Only straight and curved roads that plan have ends.
        assertEquals(Optional.empty(), RoadJoins.ports(RoadPlanParams.clearMarkers(ROAD_A)));
        assertEquals(Optional.empty(), RoadJoins.ports(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(15, 64, 15, Heading.S))));
    }

    @Test
    void aRoadOnFromAnotherIsTheSameRoadCarriedOn() {
        RoadPlanParams b = straight("city-17", joined(40, 64, 0, Heading.E, A, 1), End.at(80, 64, 0, Heading.E));
        assertEquals(new Dashes(40, Dashes.Anchor.START), b.dashes(), "the dashes carry on from A's end");
        PlanResult planB = RoadPlanner.plan(b, WORLD, PLACED);
        assertTrue(planB.isPlanned(), planB.toString());
        // The two together are the same 80-block road, block for block.
        Map<List<Integer>, String> together = blocks(RoadPlanner.plan(ROAD_A, WORLD).plan().orElseThrow());
        together.putAll(blocks(planB.plan().orElseThrow()));
        assertEquals(blocks(RoadPlanner.plan(RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(80, 64, 0, Heading.E)), WORLD)
                .plan().orElseThrow()), together);
    }

    @Test
    void aRoadRunningIntoAnotherLeavesItsFirstRowAlone() {
        RoadPlanParams into = straight("city-17", End.at(-40, 64, 0, Heading.E), joined(0, 64, 0, Heading.E, A, 0));
        assertEquals(new Dashes(0, Dashes.Anchor.END), into.dashes(), "timed to run into A's start");
        PlanResult plan = RoadPlanner.plan(into, WORLD, PLACED);
        assertTrue(plan.isPlanned());
        // A keeps its first row: all the road running into it writes there is the road under its lane nodes (R-6), as A has it.
        Map<List<Integer>, String> ofA = blocks(RoadPlanner.plan(ROAD_A, WORLD).plan().orElseThrow());
        for (BlockPlan.Block block : plan.plan().orElseThrow().blocks()) {
            if (block.x() == 0) {
                assertTrue(block.z() == -3 || block.z() == 3, "only under a lane node: " + block);
                assertEquals(ofA.get(List.of(block.x(), block.y(), block.z())), block.state());
            }
        }
        Map<List<Integer>, String> together = blocks(RoadPlanner.plan(ROAD_A, WORLD).plan().orElseThrow());
        together.putAll(blocks(plan.plan().orElseThrow()));
        assertEquals(blocks(RoadPlanner.asPlaced(RoadPlanParams.straight("city-17", End.at(-40, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E))
                .withDashes(new Dashes(-40, Dashes.Anchor.START)), WORLD).plan().orElseThrow()), together, "the same road, its dashes in step");
    }

    @Test
    void aJoinedEndIsCheckedBeforeTheRoadIsSolved() {
        End onward = End.at(80, 64, 0, Heading.E);
        assertRefused(RoadJoins.ROAD_END_GONE, Map.of(), RoadPlanner.plan(straight("city-17", joined(40, 64, 0, Heading.E, A, 1), onward), WORLD));
        assertRefused(RoadJoins.ROAD_END_GONE, Map.of(),
                RoadPlanner.plan(straight("city-17", joined(40, 64, 0, Heading.E, new UUID(9, 9), 1), onward), WORLD, PLACED));
        assertRefused(RoadJoins.PORT_MISMATCH, Map.of(), RoadPlanner.plan(straight("city-17", joined(41, 64, 0, Heading.E, A, 1), onward), WORLD, PLACED));
        assertRefused(RoadJoins.PORT_MISMATCH, Map.of(), RoadPlanner.plan(straight("city-17", joined(40, 65, 0, Heading.E, A, 1), onward), WORLD, PLACED));
        assertRefused(RoadJoins.PORT_MISMATCH, Map.of(), RoadPlanner.plan(RoadPlanParams.curve("city-17", joined(40, 64, 0, Heading.S, A, 1),
                End.at(60, 64, 60, Heading.S)), WORLD, PLACED));
        assertRefused(PieceSolver.REVERSE_HEADING, Map.of(), RoadPlanner.plan(RoadPlanParams.curve("city-17", joined(40, 64, 0, Heading.W, A, 1),
                End.at(0, 64, 40, Heading.S)), WORLD, PLACED));
        // Master (c29fec35c): the server works out a joined road's dashes, so default ones are not refused as PORT_MISMATCH any more.
        assertTrue(RoadPlanner.plan(RoadPlanParams.straight("city-17", joined(40, 64, 0, Heading.E, A, 1), onward), WORLD, PLACED).isPlanned(),
                "its dashes are worked out to carry on A's");
        Map<String, String> layouts = new java.util.LinkedHashMap<>();
        layouts.put("layout", "alley-7");
        layouts.put("joins", "city-17");
        assertRefused(RoadJoins.PROFILE_MISMATCH, layouts, RoadPlanner.plan(straight("alley-7", joined(40, 64, 0, Heading.E, A, 1), onward), WORLD,
                PLACED));
    }

    private static void assertRefused(String code, Map<String, String> args, PlanResult result, String why) {
        assertFalse(result.isPlanned(), why);
        assertEquals(Optional.of(code), result.code(), why);
    }

    @Test
    void anEndAnotherRoadJoinsIsAJunction() {
        UUID d = new UUID(2, 2);
        Map<UUID, RoadPlanParams> placed = new HashMap<>(PLACED);
        placed.put(d, RoadPlanParams.straight("city-17", joined(40, 64, 0, Heading.E, A, 1), End.at(70, 64, 0, Heading.E))
                .withDashes(new Dashes(40, Dashes.Anchor.START)));
        assertTrue(RoadGraph.of(placed).taken(A, 1));
        assertFalse(RoadGraph.of(placed).taken(A, 0));
        RoadPlanParams b = straight("city-17", joined(40, 64, 0, Heading.E, A, 1), End.at(80, 64, 0, Heading.E));
        assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "junction"), RoadPlanner.plan(b, WORLD, placed));
        // A's own end joining a road still placed is taken too; once that road has gone, it is free again.
        UUID x = new UUID(3, 3);
        RoadPlanParams a = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), joined(40, 64, 0, Heading.E, x, 0));
        assertTrue(RoadGraph.of(Map.of(A, a, x, RoadPlanParams.straight("city-17", End.at(40, 64, 0, Heading.E), End.at(60, 64, 0, Heading.E)))).taken(A, 1));
        assertFalse(RoadGraph.of(Map.of(A, a)).taken(A, 1));
    }

    @Test
    void residentialAndCityStreetJoinWithTheirOwnDashes() {
        for (String layout : List.of("residential-15", "residential-17")) {
            RoadPlanParams b = straight(layout, joined(40, 64, 0, Heading.E, A, 1), End.at(80, 64, 0, Heading.E));
            assertEquals(Dashes.FROM_START, b.dashes(), "a change of layout starts afresh");
            assertTrue(RoadPlanner.plan(b, WORLD, PLACED).isPlanned(), layout);
        }
        assertTrue(RoadJoins.sameLanes(RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_15));
        assertTrue(RoadJoins.sameLanes(RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17));
        assertFalse(RoadJoins.sameLanes(RoadProfiles.ALLEY_7, RoadProfiles.CITY_17));
    }

    @Test
    void aRoadBuiltOffAnotherRoadsStartIsStoredRunningIntoIt() {
        RoadPlanParams away = RoadPlanParams.straight("city-17", joined(0, 64, 0, Heading.W, A, 0), End.at(-40, 64, 0, Heading.W));
        RoadPlanParams stored = RoadJoins.orient(away);
        assertEquals(List.of(End.at(-40, 64, 0, Heading.E), joined(0, 64, 0, Heading.E, A, 0)), stored.ends());
        assertEquals(away, RoadJoins.turned(stored), "turning it round twice gives it back");
        // Joined the right way already, or not at all: as it is.
        RoadPlanParams onward = RoadPlanParams.straight("city-17", joined(40, 64, 0, Heading.E, A, 1), End.at(80, 64, 0, Heading.E));
        assertEquals(onward, RoadJoins.orient(onward));
        assertEquals(ROAD_A, RoadJoins.orient(ROAD_A));
        // Against the grain the dashes start afresh; stored running into A they carry on.
        assertEquals(Dashes.FROM_START, RoadJoins.dashes(away, id -> RoadJoins.ports(ROAD_A)));
        assertEquals(new Dashes(0, Dashes.Anchor.END), RoadJoins.dashes(stored, id -> RoadJoins.ports(ROAD_A)));
    }

    @Test
    void aRoadAsPlacedIsPlannedWhateverItJoined() {
        RoadPlanParams b = straight("city-17", joined(40, 64, 0, Heading.E, A, 1), End.at(80, 64, 0, Heading.E));
        PlanResult withA = RoadPlanner.plan(b, WORLD, PLACED);
        assertFalse(RoadPlanner.plan(b, WORLD).isPlanned(), "A has gone");
        assertEquals(withA.plan(), RoadPlanner.asPlaced(b, WORLD).plan(), "as placed, the same blocks");
    }

    private static Map<List<Integer>, String> blocks(BlockPlan plan) {
        Map<List<Integer>, String> blocks = new HashMap<>();
        for (BlockPlan.Block block : plan.blocks()) {
            blocks.put(List.of(block.x(), block.y(), block.z()), block.state());
        }
        return blocks;
    }
}
