package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams.DrivingSide;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.junction.JunctionSide;
import org.mtr.aurelia.road.road.junction.JunctionSnap;
import org.mtr.aurelia.road.road.junction.JunctionTemplate;
import org.mtr.aurelia.road.road.junction.JunctionTemplates;
import org.mtr.aurelia.road.road.junction.PlacedJunction;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.world.Migrations;
import org.mtr.aurelia.road.world.WorldData;

/**
 * P9's world switch of driving side: the server's check of a request's side against the world's, a junction turned onto its
 * mirrored twin on the same blocks, and a road, a junction and a road drawn onto its arm rebuilt for the other side through
 * real operations (the record, the blocks and the hidden track), the way the hub's {@code DRIVE_RIGHT} task rebuilds them.
 */
class SideSwitchTest {
    private static final String ALICE = "00000000-0000-0000-0000-00000000a11c";
    private static final WorldLimits TRACKED = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
    private static final RoadPlanParams.ManualEdits KEEP = RoadPlanParams.ManualEdits.KEEP;

    private final AtomicLong clock = new AtomicLong();
    private final FakeWorld world = new FakeWorld();
    private final Map<SideEffect, Boolean> rails = new HashMap<>();
    private Operations operations;

    /** A request to draw or rebuild a road must be for the world's side; deleting and clearing markers work on either. */
    @Test
    void aRequestKeepsToTheWorldsSideExceptDeletes() {
        UUID id = new UUID(0, 1);
        RoadPlanParams left = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E));
        RoadPlanParams right = left.withDrivingSide(DrivingSide.RHT);
        assertTrue(SideSwitch.keeps(left, DrivingSide.LHT));
        assertFalse(SideSwitch.keeps(left, DrivingSide.RHT), "a new road for the left in a right-hand world");
        assertFalse(SideSwitch.keeps(right, DrivingSide.LHT));
        assertFalse(SideSwitch.keeps(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, id, left, Optional.empty()), DrivingSide.RHT),
                "a road not yet converted can't be rebuilt");
        assertTrue(SideSwitch.keeps(RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, id, left, Optional.empty()), DrivingSide.RHT));
        assertTrue(SideSwitch.keeps(RoadPlanParams.clearMarkers(left).withDrivingSide(DrivingSide.LHT), DrivingSide.RHT));
        assertTrue(RoadsModule.INSTANCE.keepsSide(right, true));
        assertFalse(RoadsModule.INSTANCE.keepsSide(right, false));
    }

    /** A road's side is saved with it, whichever it is. */
    @Test
    void aRoadsSideIsSavedWithIt() throws JsonFormatException {
        RoadPlanParams left = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E));
        for (DrivingSide side : DrivingSide.values()) {
            RoadPlanParams saved = left.withDrivingSide(side);
            assertEquals(side, RoadsModule.INSTANCE.paramsFromJson("CURVE", RoadsModule.INSTANCE.paramsToJson(saved)).drivingSide());
        }
    }

    /**
     * Every template, in each side and size, is turned onto its twin for the other side with its arms on the same blocks facing
     * the same ways, each arm keeping its layout and the road it names, and turned back it is where it began.
     */
    @Test
    void aJunctionIsTurnedOntoItsMirroredTwinOnTheSameBlocks() {
        for (String id : List.of(JunctionTemplates.T_JUNCTION, JunctionTemplates.CROSSROADS, JunctionTemplates.ROUNDABOUT)) {
            List<Integer> sizes = id.equals(JunctionTemplates.ROUNDABOUT) ? JunctionTemplates.RING_RADII : JunctionTemplates.CORNER_RADII;
            for (int size : sizes) {
                for (DrivingSide from : DrivingSide.values()) {
                    DrivingSide to = from == DrivingSide.LHT ? DrivingSide.RHT : DrivingSide.LHT;
                    List<String> layouts = new ArrayList<>();
                    for (int i = 0; i < JunctionTemplates.armCount(id); i++) {
                        layouts.add(i % 2 == 0 ? "city-17" : "residential-15");
                    }
                    RoadPlanParams junction = junction(id, size, layouts, from, 40, 70, -12);
                    String name = id + " " + size + " " + from + " → " + to;
                    JunctionSide.Converted converted = JunctionSide.convert(junction, to).orElseThrow(() -> new AssertionError(name + ": no twin"));
                    RoadPlanParams params = converted.params();
                    assertEquals(to, params.drivingSide(), name);
                    assertEquals(Set.copyOf(ports(junction)), Set.copyOf(ports(params)), name + ": the same arms, on the same blocks");
                    JunctionTemplate template = JunctionTemplates.of(params.junction().orElseThrow(), to).orElseThrow();
                    assertTrue(PlacedJunction.from(template, params.ends()).isPresent(), name + ": the twin stands on those ends");
                    for (int old = 0; old < junction.ends().size(); old++) {
                        int now = converted.armOf(old);
                        assertEquals(junction.ends().get(old), params.ends().get(now), name + ": arm " + old + " keeps its end and its road");
                        assertEquals(junction.junction().get().armProfiles().get(old), params.junction().get().armProfiles().get(now), name + ": and its layout");
                    }
                    RoadPlanParams back = JunctionSide.convert(params, from).orElseThrow().params();
                    assertEquals(junction.ends(), back.ends(), name + ": turned back");
                    assertEquals(junction.junction(), back.junction(), name + ": turned back");
                }
            }
        }
        RoadPlanParams t = junction(JunctionTemplates.T_JUNCTION, 6, List.of("city-17", "city-17", "city-17"), DrivingSide.LHT, 0, 64, 0);
        int[] armOf = JunctionSide.convert(t, DrivingSide.RHT).orElseThrow().armOf();
        assertNotEquals(List.of(0, 1, 2), List.of(armOf[0], armOf[1], armOf[2]), "a T's main road arms swap places");
        assertEquals(2, armOf[2], "and its stem stays the stem");
    }

    /**
     * One straight road with hidden track, rebuilt for the other side as the task does: its record now says so, its blocks are
     * as they were (a road's blocks are the same on both sides), and its track runs the other way round.
     */
    @Test
    void aRoadIsSwitchedWithItsRecordAndItsTrackButNotItsBlocks() throws IOException {
        start();
        UUID road = new UUID(0, 1);
        RoadPlanParams left = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.TRACK);
        place(road, left);
        Map<String, String> blocks = solid(this.world.blocks);
        Set<SideEffect> leftTrack = made();
        assertFalse(leftTrack.isEmpty());

        switchAll(DrivingSide.RHT, 10);

        RoadPlanParams stands = RoadsModule.roads(this.operations.records()).get(road);
        assertEquals(DrivingSide.RHT, stands.drivingSide(), "the record says it keeps right");
        assertEquals(Map.of(), diff(blocks, solid(this.world.blocks)), "its blocks are as they were");
        Set<SideEffect> rightTrack = new HashSet<>(RoadPlanner.asPlaced(stands, TRACKED).sideEffects());
        assertNotEquals(leftTrack, rightTrack, "its lanes run the other way round");
        assertEquals(rightTrack, made(), "and its hidden track was laid that way");
        assertTrue(SideSwitch.pending(RoadsModule.roads(this.operations.records()), DrivingSide.RHT).isEmpty(), "nothing is left to switch");
        assertEquals(List.of(road), SideSwitch.pending(RoadsModule.roads(this.operations.records()), DrivingSide.LHT), "and it is the left that it would switch");
    }

    /**
     * A T-junction on a road's end with a road drawn from its stem, switched in the task's order (junction first): the
     * junction is still a junction and takes the other side's footprint and track, and the road drawn onto its arm names
     * that arm's new port, so the three still join.
     */
    @Test
    void aJunctionAndTheRoadsOnItsArmsAreSwitchedTogether() throws IOException {
        start();
        UUID main = new UUID(0, 1);
        place(main, RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.TRACK));
        JunctionTemplate template = JunctionTemplates.of(JunctionTemplates.T_JUNCTION, 6, List.of("city-17", "city-17", "city-17"), DrivingSide.LHT).orElseThrow();
        JunctionSnap.Placed snap = (JunctionSnap.Placed) JunctionSnap.snap(template, RoadGraph.of(RoadsModule.roads(this.operations.records())), 31, 64, 0, 1);
        UUID junction = new UUID(0, 2);
        place(junction, snap.params().withNodeAids(RoadPlanParams.NodeAids.TRACK));
        End south = snap.junction().arm(2);
        UUID stem = new UUID(0, 3);
        place(stem, RoadPlanParams.curve("city-17", new End(south.x(), south.y(), south.z(), south.heading(), Optional.of(junction),
                OptionalInt.of(RoadJoins.ARM_PORTS + 2)), End.at(south.x(), 64, south.z() + 20, south.heading())).withNodeAids(RoadPlanParams.NodeAids.TRACK));
        Set<SideEffect> leftTrack = made();
        Map<String, String> leftBlocks = solid(this.world.blocks);

        List<UUID> order = SideSwitch.pending(RoadsModule.roads(this.operations.records()), DrivingSide.RHT);
        assertEquals(junction, order.get(0), "the junction goes before the roads on its arms");
        switchAll(DrivingSide.RHT, 10);

        Map<UUID, RoadPlanParams> standing = RoadsModule.roads(this.operations.records());
        assertEquals(RoadPlanParams.Tool.JUNCTION, standing.get(junction).tool(), "a switched junction is still a junction");
        assertEquals(DrivingSide.RHT, standing.get(junction).drivingSide());
        assertTrue(SideSwitch.pending(standing, DrivingSide.RHT).isEmpty(), "every road, the junction and the stem now keep right");
        RoadGraph graph = RoadGraph.of(standing);
        assertEquals(3, graph.arms(junction).size());
        for (int arm = 0; arm < 3; arm++) {
            assertTrue(graph.armTaken(junction, arm) == (arm != armOfFree(standing.get(junction))), "arm " + arm + " is joined unless it's the free one");
        }
        End stemEnd = standing.get(stem).ends().get(0);
        int port = stemEnd.snapPortIndex().orElseThrow() - RoadJoins.ARM_PORTS;
        RoadPlanParams.End arm = standing.get(junction).ends().get(port);
        assertEquals(List.of(arm.x(), arm.z()), List.of(stemEnd.x(), stemEnd.z()), "the road from the stem names the arm that stands where it starts");
        assertFalse(diff(leftBlocks, solid(this.world.blocks)).isEmpty(), "its give-way lines and kerbs are the other side's, so undo has something to take back");
        assertNotEquals(leftTrack, made(), "the junction's track runs the other way");
        Set<SideEffect> expected = new HashSet<>();
        standing.forEach((id, params) -> expected.addAll(RoadPlanner.asPlaced(params, TRACKED).sideEffects()));
        assertEquals(expected, made(), "every road's and the junction's track is the right side's");
    }

    /** The arm of {@code junction} no road is joined to. */
    private static int armOfFree(RoadPlanParams junction) {
        for (int arm = 0; arm < junction.ends().size(); arm++) {
            if (junction.ends().get(arm).snapRoadId().isEmpty()) {
                return arm;
            }
        }
        return -1;
    }

    /** What the task does, one road at a time: junctions first, each rebuilt for {@code side} from the roads as they stand then. */
    private void switchAll(DrivingSide side, int firstId) {
        int next = firstId;
        for (UUID id : SideSwitch.pending(RoadsModule.roads(this.operations.records()), side)) {
            RoadPlanParams switched = SideSwitch.converted(id, RoadsModule.roads(this.operations.records()), side).orElseThrow();
            replace(new UUID(0, next++), RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, id, switched, Optional.of(KEEP)));
        }
    }

    private static RoadPlanParams junction(String template, int size, List<String> layouts, DrivingSide side, int x, int y, int z) {
        JunctionTemplate authored = JunctionTemplates.of(template, size, layouts, side).orElseThrow();
        List<Optional<RoadGraph.Port>> none = new ArrayList<>();
        for (int i = 0; i < layouts.size(); i++) {
            none.add(Optional.empty());
        }
        return new PlacedJunction(authored, x, y, z, 0).params(none);
    }

    private static List<List<Object>> ports(RoadPlanParams junction) {
        List<List<Object>> ports = new ArrayList<>();
        for (End end : junction.ends()) {
            ports.add(List.of(end.x(), end.y(), end.z(), end.heading()));
        }
        return ports;
    }

    private void start() throws IOException {
        SideEffectWriter writer = new SideEffectWriter() {
            @Override
            public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
                List<Result> results = new ArrayList<>();
                for (SideEffect effect : effects) {
                    results.add(SideSwitchTest.this.rails.put(effect, true) == null ? Result.DONE : new Result(Outcome.ALREADY_THERE, "there"));
                }
                return CompletableFuture.completedFuture(results);
            }

            @Override
            public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
                List<Result> results = new ArrayList<>();
                for (SideEffect effect : effects) {
                    results.add(SideSwitchTest.this.rails.remove(effect) != null ? Result.DONE : new Result(Outcome.NOT_THERE, "gone"));
                }
                return CompletableFuture.completedFuture(results);
            }
        };
        WorldData.Settings settings = new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(0x5d, this.clock.incrementAndGet()), writer,
                () -> Instant.ofEpochSecond(this.clock.incrementAndGet()), 3465, "stratos 0.1.0+mc1.20.1");
        this.operations = WorldData.load(new FakeStorage(), settings, Migrations.ALL, line -> {
        }).operations(Operations.OVERWORLD);
        this.world.modBlocks.add(MtrNodes.BLOCK);
    }

    private Set<SideEffect> made() {
        return new HashSet<>(this.rails.keySet());
    }

    private void place(UUID id, RoadPlanParams request) {
        RoadPlanParams params = RoadsModule.INSTANCE.settle(request, this.operations.records());
        PlanResult result = RoadsModule.INSTANCE.plan(params, TRACKED, this.operations.records());
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        assertTrue(this.operations.apply(ALICE, result.plan().orElseThrow(), id, Optional.empty(), draft(params, result)).isAccepted());
        run();
    }

    private void replace(UUID id, RoadPlanParams request) {
        RoadPlanParams params = RoadsModule.INSTANCE.settle(request, this.operations.records());
        PlanResult result = RoadsModule.INSTANCE.plan(params, TRACKED, this.operations.records());
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        BlockPlan plan = result.plan().orElseThrow();
        Operations.Request accepted = this.operations.replace(ALICE, plan, result.replacement().orElseThrow(), id, draft(params, result));
        assertTrue(accepted.isAccepted(), () -> "refused " + accepted.refusal());
        run();
        assertEquals(OperationRecord.Status.APPLIED, this.operations.record(id).orElseThrow().status());
    }

    private static OperationRecord.Draft draft(RoadPlanParams params, PlanResult result) {
        return new OperationRecord.Draft("roads", params.tool().name(), RoadsModule.INSTANCE.paramsToJson(params), "ab".repeat(32), result.sideEffects());
    }

    private void run() {
        List<Operations.Result> results = new ArrayList<>();
        for (int tick = 0; tick < 2000 && (this.operations.isBusy(ALICE) || !this.operations.scheduler().isIdle()); tick++) {
            this.operations.scheduler().tick(this.world, 50_000_000, () -> this.world.now);
            results.addAll(this.operations.afterTick());
        }
        results.forEach(result -> assertEquals(Optional.empty(), result.code(), () -> "failed: " + result));
    }

    /** The blocks that differ: each key with what {@code expected} has, then what {@code actual} has. */
    private static Map<String, String> diff(Map<String, String> expected, Map<String, String> actual) {
        Map<String, String> diff = new java.util.TreeMap<>();
        Set<String> keys = new HashSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        for (String key : keys) {
            if (!java.util.Objects.equals(expected.get(key), actual.get(key))) {
                diff.put(key, expected.get(key) + " -> " + actual.get(key));
            }
        }
        return diff;
    }

    private static Map<String, String> solid(Map<String, String> blocks) {
        Map<String, String> solid = new HashMap<>();
        blocks.forEach((key, state) -> {
            if (!state.equals("minecraft:air") && !state.equals("minecraft:cave_air")) {
                solid.put(key, state);
            }
        });
        return solid;
    }
}
