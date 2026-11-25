/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.undo.SideEffect.TrackRail;

/**
 * The Track node aid (task T-2; ADR-0040): joined MTR nodes in the plan, and one hidden rail per lane in its side
 * effects, one way with traffic for the road's driving side, at the road group's speed, with the road's vertical shape.
 */
class RoadPlannerTrackTest {
    private static final WorldLimits MTR = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));

    private static PlanResult plan(RoadPlanParams params) {
        PlanResult result = RoadPlanner.plan(params, MTR);
        assertTrue(result.isPlanned(), result.toString());
        return result;
    }

    private static RoadPlanParams track(String profile, End start, End end) {
        return RoadPlanParams.curve(profile, start, end, RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
    }

    private static RoadPlanParams withSide(RoadPlanParams p, RoadPlanParams.DrivingSide side) {
        return new RoadPlanParams(p.tool(), p.profileId(), p.preset(), p.paletteId(), side, p.ends(), p.vertical(), p.nodeAids(), p.overlay(),
                p.junction(), p.targetRoadId(), p.manualEdits());
    }

    private static List<TrackRail> rails(PlanResult result) {
        List<TrackRail> rails = new ArrayList<>();
        result.sideEffects().forEach(effect -> rails.add((TrackRail) effect));
        return rails;
    }

    /** Each rail's speeds from a and from b. */
    private static List<List<Integer>> speeds(List<TrackRail> rails) {
        return rails.stream().map(r -> List.of(r.speedFromA(), r.speedFromB())).toList();
    }

    @Test
    void trackPlacesJoinedNodesAndOneHiddenRailPerLane() {
        RoadPlanParams params = track("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));
        PlanResult result = plan(params);
        BlockPlan preoriented = plan(params.withNodeAids(RoadPlanParams.NodeAids.PREORIENTED)).plan().orElseThrow();
        BlockPlan joined = result.plan().orElseThrow();
        assertEquals(preoriented.blockCount(), joined.blockCount(), "the same blocks as MTR nodes, only joined");
        List<LaneNode> nodes = RoadPlanner.laneNodes(params).orElseThrow();
        for (LaneNode node : nodes) {
            String state = joined.blocks().stream().filter(b -> b.x() == node.x() && b.y() == node.nodeY() && b.z() == node.z())
                    .map(BlockPlan.Block::state).findFirst().orElseThrow();
            assertEquals(MtrNodes.state(node.mtrFacing(), true), state, "joined, as MTR's connector leaves a node: " + node);
            assertTrue(state.contains("is_connected=true"), state);
        }

        List<TrackRail> rails = rails(result);
        assertEquals(2, rails.size(), "one rail per lane");
        // Lane 1 (north of an eastward road) runs with the road under left-hand traffic, lane 2 against it.
        assertEquals(new TrackRail(0, 65, -3, 0, 40, 65, -3, 0, 40, 0, TrackRail.Shape.QUADRATIC, 0), rails.get(0));
        assertEquals(new TrackRail(0, 65, 3, 0, 40, 65, 3, 0, 0, 40, TrackRail.Shape.QUADRATIC, 0), rails.get(1));
        // Every rail joins two of the road's lane nodes, lane by lane in order along the road.
        for (int lane = 0; lane < rails.size(); lane++) {
            LaneNode a = nodes.get(2 * lane);
            LaneNode b = nodes.get(2 * lane + 1);
            TrackRail rail = rails.get(lane);
            assertArrayEquals(new int[] {a.x(), a.nodeY(), a.z(), a.mtrFacing(), b.x(), b.nodeY(), b.z(), b.mtrFacing()},
                    new int[] {rail.ax(), rail.ay(), rail.az(), rail.aFacing(), rail.bx(), rail.by(), rail.bz(), rail.bFacing()});
        }
    }

    @Test
    void onlyTrackHasSideEffectsAndItNeedsMtrsNodeBlock() {
        RoadPlanParams params = track("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));
        for (RoadPlanParams.NodeAids aids : List.of(RoadPlanParams.NodeAids.WOOL, RoadPlanParams.NodeAids.PREORIENTED, RoadPlanParams.NodeAids.NONE)) {
            assertEquals(List.of(), plan(params.withNodeAids(aids)).sideEffects(), aids + " has no track");
        }
        PlanResult without = RoadPlanner.plan(params, WorldLimits.OVERWORLD);
        assertFalse(without.isPlanned());
        assertEquals(Optional.of(RoadPlanner.MTR_MISSING), without.code(), "no mtr:rail in this game");
        assertEquals(List.of(), without.sideEffects());
        WorldLimits border = new WorldLimits(-64, 320, -100, 30, -100, 100, Set.of(MtrNodes.BLOCK));
        PlanResult refused = RoadPlanner.plan(params, border);
        assertEquals(Optional.of(RoadPlanner.WORLD_LIMIT), refused.code(), "a refused road has no track");
        assertEquals(List.of(), refused.sideEffects());
        assertTrue(RoadPlanner.plan(RoadPlanParams.clearMarkers(params), WorldLimits.OVERWORLD).sideEffects().isEmpty(),
                "clearing a Track road's markers makes no track");
    }

    @Test
    void rightHandTrafficSwapsEachLanesDirection() {
        RoadPlanParams lht = track("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 40, Heading.S));
        RoadPlanParams rht = withSide(lht, RoadPlanParams.DrivingSide.RHT);
        assertEquals(List.of(List.of(40, 0), List.of(0, 40)), speeds(rails(plan(lht))));
        assertEquals(List.of(List.of(0, 40), List.of(40, 0)), speeds(rails(plan(rht))));
        assertEquals(plan(lht).plan(), plan(rht).plan(), "the blocks don't change with the driving side (yet)");
    }

    @Test
    void speedsFollowTheRoadGroupAndTheAlleysSharedLaneRunsBothWays() {
        End start = End.at(0, 64, 0, Heading.E);
        End end = End.at(40, 64, 0, Heading.E);
        assertEquals(List.of(List.of(40, 0), List.of(0, 40)), speeds(rails(plan(track("city-17", start, end)))), "City street 40 km/h");
        assertEquals(List.of(List.of(40, 0), List.of(0, 40)), speeds(rails(plan(track("residential-15", start, end)))), "residential 40 km/h");
        assertEquals(List.of(List.of(40, 0), List.of(0, 40)), speeds(rails(plan(track("residential-17", start, end)))), "residential 40 km/h");
        List<TrackRail> alley = rails(plan(track("alley-7", start, end)));
        assertEquals(List.of(new TrackRail(0, 65, 0, 0, 40, 65, 0, 0, 20, 20, TrackRail.Shape.QUADRATIC, 0)), alley,
                "the alley's one lane, on the centre line: both ways at 20 km/h (owner, 2026-10-08)");
        assertEquals(alley, rails(plan(withSide(track("alley-7", start, end), RoadPlanParams.DrivingSide.RHT))), "both ways either side");
        for (RoadProfile profile : RoadProfiles.all()) {
            for (TrackRail rail : rails(plan(track(profile.id(), start, end)))) {
                assertEquals(profile.group().trackSpeed(), rail.speed(), profile.id());
            }
        }
    }

    @Test
    void railsCarryTheRoadsVerticalShape() {
        End start = End.at(0, 64, 0, Heading.E);
        End end = End.at(80, 67, 0, Heading.E);
        PlanResult quadratic = plan(RoadPlanParams.curve("city-17", start, end, RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK));
        for (TrackRail rail : rails(quadratic)) {
            assertEquals(TrackRail.Shape.QUADRATIC, rail.shape());
            assertEquals(0, rail.radius());
            assertEquals(List.of(65, 68), List.of(rail.ay(), rail.by()), "the node blocks above each end's surface");
        }
        RoadPlanParams.Vertical twoRadii = new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII, OptionalDouble.of(500));
        for (TrackRail rail : rails(plan(RoadPlanParams.curve("city-17", start, end, twoRadii, RoadPlanParams.NodeAids.TRACK)))) {
            assertEquals(TrackRail.Shape.TWO_RADII, rail.shape());
            assertEquals(500, rail.radius(), "the radius the solver built the lanes with");
        }
    }

    @Test
    void thePlanHashCoversTheTrackAndIsUnchangedWithout() {
        PlanResult result = plan(track("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 40, Heading.S)));
        BlockPlan blocks = result.plan().orElseThrow();
        String version = CoreInfo.version();
        assertArrayEquals(PlanHash.of(blocks, version), PlanHash.of(blocks, List.of(), version), "no side effects: the hash before T-2");
        byte[] withTrack = PlanHash.of(blocks, result.sideEffects(), version);
        assertFalse(Arrays.equals(PlanHash.of(blocks, version), withTrack), "the track changes the hash");
        assertArrayEquals(withTrack, PlanHash.of(blocks, plan(track("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 40, Heading.S)))
                .sideEffects(), version), "the same road, the same hash");
        TrackRail first = (TrackRail) result.sideEffects().get(0);
        for (TrackRail changed : List.of(
                new TrackRail(first.ax(), first.ay(), first.az(), first.aFacing(), first.bx(), first.by(), first.bz(), first.bFacing(), 20, 0,
                        first.shape(), first.radius()),
                new TrackRail(first.ax(), first.ay(), first.az(), first.aFacing(), first.bx(), first.by(), first.bz(), first.bFacing(),
                        first.speedFromA(), first.speedFromB(), TrackRail.Shape.TWO_RADII, first.radius()),
                new TrackRail(first.ax(), first.ay(), first.az(), first.aFacing(), first.bx(), first.by(), first.bz(), first.bFacing(),
                        first.speedFromA(), first.speedFromB(), first.shape(), 0.5))) {
            List<SideEffect> effects = new ArrayList<>(result.sideEffects());
            effects.set(0, changed);
            assertFalse(Arrays.equals(withTrack, PlanHash.of(blocks, effects, version)), "a changed rail changes the hash: " + changed);
        }
        assertEquals(Map.of(), result.args());
    }

    @Test
    void aRailNeedsTwoNodesASpeedAndAShape() {
        TrackRail ok = new TrackRail(0, 65, 0, 0, 40, 65, 0, 0, 40, 0, TrackRail.Shape.QUADRATIC, 0);
        assertEquals(40, ok.speed());
        for (Runnable bad : List.<Runnable>of(
                () -> new TrackRail(0, 65, 0, 0, 40, 65, 0, 0, 0, 0, TrackRail.Shape.QUADRATIC, 0),
                () -> new TrackRail(0, 65, 0, 8, 40, 65, 0, 0, 40, 0, TrackRail.Shape.QUADRATIC, 0),
                () -> new TrackRail(0, 65, 0, 0, 0, 65, 0, 0, 40, 0, TrackRail.Shape.QUADRATIC, 0),
                () -> new TrackRail(0, 65, 0, 0, 40, 65, 0, 0, 40, 0, null, 0),
                () -> new TrackRail(0, 65, 0, 0, 40, 65, 0, 0, 40, 0, TrackRail.Shape.TWO_RADII, -1),
                () -> new TrackRail(0, 65, 0, 0, 40, 65, 0, 0, 40, 0, TrackRail.Shape.TWO_RADII, Double.NaN))) {
            try {
                bad.run();
                throw new AssertionError("accepted a bad rail");
            } catch (IllegalArgumentException | NullPointerException expected) {
                // refused
            }
        }
    }
}
