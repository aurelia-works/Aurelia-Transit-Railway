package org.mtr.aurelia.road.road.junction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.geom.mtr.Vector;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.tool.JoinableRoads;
import org.mtr.aurelia.road.road.tool.JunctionTool;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.road.tool.StraightTool;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.road.raster.Column;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.world.Migrations;
import org.mtr.aurelia.road.world.WorldData;

/**
 * G-11's in-world half (owner, 2026-11-24): a junction's footprint carries every lane it lays, it is placed onto a road
 * end as one operation whose undo takes its blocks and track away, a road drawn from a free arm can be deleted without
 * touching the junction, a road end an arm took can't be dragged away, and (#189) deleting a road that followed a moved
 * end leaves the edited road's track.
 */
class JunctionPlanTest {
    private static final String ALICE = "00000000-0000-0000-0000-00000000a11c";
    private static final WorldLimits TRACKED = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));

    private final AtomicLong clock = new AtomicLong();
    private final FakeWorld world = new FakeWorld();
    private final Map<SideEffect, Boolean> rails = new HashMap<>();
    private Operations operations;

    /** Every lane every template lays runs on its carriageway, node to node, in both driving sides. */
    @Test
    void everyLaneRunsOnTheCarriageway() {
        for (RoadPlanParams.DrivingSide side : RoadPlanParams.DrivingSide.values()) {
            for (String id : List.of(JunctionTemplates.T_JUNCTION, JunctionTemplates.CROSSROADS, JunctionTemplates.ROUNDABOUT)) {
                List<Integer> sizes = id.equals(JunctionTemplates.ROUNDABOUT) ? JunctionTemplates.RING_RADII : JunctionTemplates.CORNER_RADII;
                for (int size : sizes) {
                    List<String> profiles = new ArrayList<>();
                    for (int i = 0; i < JunctionTemplates.armCount(id); i++) {
                        profiles.add(i % 2 == 0 ? "city-17" : "residential-15");
                    }
                    JunctionTemplate template = JunctionTemplates.of(id, size, profiles, side).orElseThrow();
                    Map<BlockStep, Column> columns = JunctionFootprint.columns(template);
                    String name = id + " " + size + " " + side;
                    for (JunctionTemplate.Link link : template.links()) {
                        JunctionTemplate.Node from = template.nodes().get(link.from());
                        JunctionTemplate.Node to = template.nodes().get(link.to());
                        RailMath rail = PieceSolver.laneLink(new BlockStep(to.x() - from.x(), to.z() - from.z()), from.heading(), to.heading()).orElseThrow();
                        for (double s = 0; s <= rail.getLength(); s += 0.25) {
                            Vector at = rail.getPosition(s, false);
                            BlockStep block = new BlockStep(from.x() + (int) StrictMath.floor(at.x()), from.z() + (int) StrictMath.floor(at.z())); // block centres are at ½
                            Column column = columns.get(block);
                            assertTrue(column != null && column.band() == Band.LANE, () -> name + ": link " + link + " leaves the carriageway at " + block);
                        }
                    }
                    long giveWay = columns.values().stream().filter(Column::painted).count();
                    assertTrue(giveWay > 0, name + " has give-way lines");
                }
            }
        }
    }

    /**
     * Each template's carriageway ends at its kerbs: across the main road from a T's stem there is kerb, then footway;
     * a crossroads keeps its kerbs between the arms; a roundabout has a kerb round the outside of its ring between the
     * arms. (Each corner once filled its whole quadrant with carriageway.)
     */
    @Test
    void everyTemplateEndsAtItsKerbs() {
        List<String> city = List.of("city-17", "city-17", "city-17", "city-17");
        for (int size : JunctionTemplates.CORNER_RADII) {
            Map<BlockStep, Column> t = JunctionFootprint.columns(JunctionTemplates.of(JunctionTemplates.T_JUNCTION, size, city.subList(0, 3),
                    RoadPlanParams.DrivingSide.LHT).orElseThrow());
            assertEquals(Band.LANE, t.get(new BlockStep(0, -5)).band(), "T " + size + ": the main road");
            assertEquals(Band.KERB, t.get(new BlockStep(0, -6)).band(), "T " + size + ": its far kerb");
            assertEquals(Band.FOOTWAY, t.get(new BlockStep(0, -7)).band(), "T " + size + ": its far footway");
            Map<BlockStep, Column> x = JunctionFootprint.columns(JunctionTemplates.of(JunctionTemplates.CROSSROADS, size, city,
                    RoadPlanParams.DrivingSide.LHT).orElseThrow());
            int out = x.keySet().stream().mapToInt(BlockStep::dx).max().orElseThrow();
            for (int sx : new int[] {-1, 1}) {
                for (int sz : new int[] {-1, 1}) {
                    Column corner = x.get(new BlockStep(sx * (out - 1), sz * (out - 1)));
                    assertTrue(corner == null || corner.band() != Band.LANE, "crossroads " + size + ": no carriageway in the corner past its kerb");
                }
            }
        }
        Map<BlockStep, Column> ring = JunctionFootprint.columns(JunctionTemplates.of(JunctionTemplates.ROUNDABOUT, 13, city,
                RoadPlanParams.DrivingSide.LHT).orElseThrow());
        int out = ring.keySet().stream().mapToInt(BlockStep::dx).max().orElseThrow();
        Column between = ring.get(new BlockStep(out - 1, out - 1));
        assertTrue(between == null || between.band() != Band.LANE, "roundabout: no carriageway between the arms past the kerb");
    }

    /**
     * A T-junction placed on a road's end through the operations engine: its blocks and track go in as one operation, its
     * undo takes them away and leaves the road; a road drawn from a free arm and deleted leaves the junction as it was;
     * the road end the junction took can't be dragged away.
     */
    @Test
    void aJunctionOnARoadEndIsPlacedUndoneAndKeptByTheRoadsDrawnFromIt() throws IOException {
        start();
        UUID road = new UUID(0, 1);
        RoadPlanParams a = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.TRACK);
        place(road, a);
        Map<String, String> roadOnly = solid(this.world.blocks);
        Set<SideEffect> roadRails = made();

        JunctionTemplate template = JunctionTemplates.of(JunctionTemplates.T_JUNCTION, 6, List.of("city-17", "city-17", "city-17"), RoadPlanParams.DrivingSide.LHT)
                .orElseThrow();
        JunctionSnap.Result snap = JunctionSnap.snap(template, RoadGraph.of(RoadsModule.roads(this.operations.records())), 31, 64, 0, 1);
        RoadPlanParams junction = ((JunctionSnap.Placed) snap).params().withNodeAids(RoadPlanParams.NodeAids.TRACK);
        UUID placed = new UUID(0, 2);
        PlanResult planned = place(placed, junction);
        assertEquals(template.links().size(), planned.sideEffects().size(), "one rail per link");
        assertTrue(made().containsAll(planned.sideEffects()), "the junction's track is made");
        assertTrue(made().containsAll(roadRails), "and the road's stays");
        Map<String, String> withJunction = solid(this.world.blocks);

        // A road drawn from the junction's south arm (its port 2 + 2), then deleted: the junction stays as it was.
        RoadPlanParams.End south = ((JunctionSnap.Placed) snap).junction().arm(2);
        RoadPlanParams stem = RoadPlanParams.curve("city-17", new End(south.x(), south.y(), south.z(), south.heading(), Optional.of(placed),
                OptionalInt.of(RoadJoins.ARM_PORTS + 2)), End.at(south.x(), 64, south.z() + 20, south.heading()));
        UUID stemId = new UUID(0, 3);
        place(stemId, stem);
        assertTrue(RoadGraph.of(RoadsModule.roads(this.operations.records())).armTaken(placed, 2));
        replace(new UUID(0, 4), RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, stemId, RoadsModule.roads(this.operations.records()).get(stemId),
                Optional.empty()));
        assertEquals(Map.of(), diff(withJunction, solid(this.world.blocks)), "deleting the road leaves the junction");

        // A's end, which the junction took, can't be dragged away from it.
        RoadPlanParams pulled = a.withEnds(List.of(a.ends().get(0), End.at(26, 64, 0, Heading.E)));
        PlanResult refused = RoadsModule.INSTANCE.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, road, pulled, Optional.empty()), TRACKED,
                this.operations.records());
        assertEquals(Optional.of(RoadPlanner.ROAD_JOINED), refused.code());

        this.operations.undo(ALICE); // the delete
        run();
        this.operations.undo(ALICE); // the stem
        run();
        this.operations.undo(ALICE); // the junction
        run();
        assertEquals(Map.of(), diff(roadOnly, solid(this.world.blocks)), "undone, the junction's blocks go and the road is as it was");
        Set<SideEffect> left = made();
        assertTrue(left.containsAll(roadRails), "the road keeps its track");
        planned.sideEffects().forEach(rail -> assertFalse(left.contains(rail) && !roadRails.contains(rail), "the junction's track goes"));
    }

    /** #189: B follows A's moved end, then B alone is deleted: A's track, made by the move, stays. */
    /**
     * The in-world half: the Junction tool snaps a T onto a road's end the player aims near, and once it stands the road
     * tools offer its free arms (port {@code ARM_PORTS} + arm), so a road drawn there joins it and plans.
     */
    @Test
    void theJunctionToolSnapsOntoARoadEndAndItsFreeArmsCanBeJoined() {
        UUID road = new UUID(0, 0x51);
        UUID junction = new UUID(0, 0x52);
        RoadPlanParams a = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E));
        PlacedRoads placed = new PlacedRoads();
        placed.placed(road, a, RoadPlanner.plan(a, WorldLimits.OVERWORLD).plan().orElseThrow());
        JoinableRoads joins = new JoinableRoads(placed::roads, placed::junctions);
        RoadSettings settings = new RoadSettings();
        settings.setNodeAids(RoadPlanParams.NodeAids.NONE);
        JunctionTool tool = new JunctionTool(settings, joins);
        tool.input(new ToolInput.Aim(Optional.of(new Target(31, 64, 1, 5))));
        RoadPlanParams t = tool.params().orElseThrow();
        assertEquals(Optional.of(road), t.ends().stream().flatMap(end -> end.snapRoadId().stream()).findFirst(), "snapped onto A's end");
        PlanResult planned = new StraightTool.RoadJob(t, joins.placed()).run(WorldLimits.OVERWORLD).result();
        assertTrue(planned.isPlanned(), () -> "refused " + planned.code());

        placed.placed(junction, t, planned.plan().orElseThrow());
        int free = -1;
        for (int i = 0; i < t.ends().size(); i++) {
            free = t.ends().get(i).snapRoadId().isEmpty() ? i : free;
        }
        End arm = t.ends().get(free);
        JoinableRoads.Join join = joins.near(arm.x(), arm.y(), arm.z()).orElseThrow();
        assertEquals(junction, join.road());
        assertEquals(RoadJoins.ARM_PORTS + free, join.port().index());
        BlockStep out = switch (arm.heading()) {
            case E -> new BlockStep(1, 0);
            case W -> new BlockStep(-1, 0);
            case S -> new BlockStep(0, 1);
            default -> new BlockStep(0, -1);
        };
        End from = new End(arm.x(), arm.y(), arm.z(), arm.heading(), Optional.of(junction), OptionalInt.of(join.port().index()));
        RoadPlanParams stem = joins.finish(RoadPlanParams.curve("city-17", from, End.at(arm.x() + 20 * out.dx(), 64, arm.z() + 20 * out.dz(), arm.heading())));
        PlanResult drawn = StraightTool.RoadJob.of(stem, joins).run(WorldLimits.OVERWORLD).result();
        assertTrue(drawn.isPlanned(), () -> "refused " + drawn.code());
    }

    @Test
    void deletingTheRoadThatFollowedKeepsTheEditedRoadsTrack() throws IOException {
        start();
        UUID a = new UUID(0, 11);
        UUID b = new UUID(0, 12);
        RoadPlanParams roadA = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E))
                .withNodeAids(RoadPlanParams.NodeAids.TRACK);
        place(a, roadA);
        RoadPlanParams roadB = RoadPlanParams.curve("city-17", new End(30, 64, 0, Heading.E, Optional.of(a), OptionalInt.of(1)), End.at(60, 64, 0, Heading.E))
                .withNodeAids(RoadPlanParams.NodeAids.TRACK);
        place(b, RoadPlanner.settled(roadB, RoadsModule.roads(this.operations.records())));
        replace(new UUID(0, 13), RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, a, roadA.withEnds(List.of(roadA.ends().get(0),
                End.at(34, 64, 0, Heading.E))), Optional.empty()));
        RoadPlanParams movedA = RoadsModule.roads(this.operations.records()).get(a);
        List<SideEffect> aRails = RoadPlanner.asPlaced(movedA, TRACKED).sideEffects();
        assertFalse(aRails.isEmpty());
        assertTrue(made().containsAll(aRails));
        replace(new UUID(0, 14), RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, b, RoadsModule.roads(this.operations.records()).get(b),
                Optional.empty()));
        assertTrue(made().containsAll(aRails), "A's track stays when B goes");
    }

    private void start() throws IOException {
        SideEffectWriter writer = new SideEffectWriter() {
            @Override
            public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
                List<Result> results = new ArrayList<>();
                for (SideEffect effect : effects) {
                    results.add(JunctionPlanTest.this.rails.put(effect, true) == null ? Result.DONE : new Result(Outcome.ALREADY_THERE, "there"));
                }
                return CompletableFuture.completedFuture(results);
            }

            @Override
            public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
                List<Result> results = new ArrayList<>();
                for (SideEffect effect : effects) {
                    results.add(JunctionPlanTest.this.rails.remove(effect) != null ? Result.DONE : new Result(Outcome.NOT_THERE, "gone"));
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

    private PlanResult place(UUID id, RoadPlanParams request) {
        RoadPlanParams params = RoadsModule.INSTANCE.settle(request, this.operations.records());
        PlanResult result = RoadsModule.INSTANCE.plan(params, TRACKED, this.operations.records());
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        assertTrue(this.operations.apply(ALICE, result.plan().orElseThrow(), id, Optional.empty(), draft(params, result)).isAccepted());
        run();
        return result;
    }

    private void replace(UUID id, RoadPlanParams request) {
        RoadPlanParams params = RoadsModule.INSTANCE.settle(request, this.operations.records());
        PlanResult result = RoadsModule.INSTANCE.plan(params, TRACKED, this.operations.records());
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        BlockPlan plan = params.tool() == RoadPlanParams.Tool.DELETE ? BlockPlan.builder(3465).build() : result.plan().orElseThrow();
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
