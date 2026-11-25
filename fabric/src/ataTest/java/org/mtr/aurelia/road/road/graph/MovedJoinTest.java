/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.world.Migrations;
import org.mtr.aurelia.road.world.WorldData;

/**
 * Owner, 2026-11-24: moving the end of a road another road is joined to re-plans that road in the same operation. Run
 * through the operations engine on a fake world: the move changes both roads' blocks, its undo and redo put both back
 * together, and a later delete of the edited road alone leaves the road that followed it as it is and doesn't bring back
 * that road's old surface where the edited road had taken it over.
 */
class MovedJoinTest {
    private static final String ALICE = "00000000-0000-0000-0000-00000000a11c";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID MOVE = new UUID(0, 3);
    private static final UUID DELETE = new UUID(0, 4);

    private final AtomicLong clock = new AtomicLong();
    private final FakeWorld world = new FakeWorld();
    private Operations operations;

    @Test
    void movingAJoinedEndTakesTheJoinedRoadWithItAndUndoesTogether() throws IOException {
        WorldData.Settings settings = new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(0x5d, this.clock.incrementAndGet()), SideEffectWriter.UNAVAILABLE,
                () -> Instant.ofEpochSecond(this.clock.incrementAndGet()), 3465, "stratos 0.1.0+mc1.20.1");
        this.operations = WorldData.load(new FakeStorage(), settings, Migrations.ALL, line -> {
        }).operations(Operations.OVERWORLD);

        // A from (0, 64, 0) to (30, 64, 0) eastward, and B drawn on from A's end to (60, 64, 0).
        RoadPlanParams a = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E));
        place(A, a);
        RoadPlanParams b = RoadPlanParams.curve("city-17", new End(30, 64, 0, Heading.E, Optional.of(A), OptionalInt.of(1)), End.at(60, 64, 0, Heading.E));
        place(B, RoadPlanner.settled(b, RoadsModule.roads(this.operations.records())));
        Map<String, String> placed = new HashMap<>(this.world.blocks);

        // A's end, where B starts, moves 4 blocks on: B's start goes with it.
        RoadPlanParams moved = a.withEnds(List.of(a.ends().get(0), End.at(34, 64, 0, Heading.E)));
        PlanResult move = replace(MOVE, RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, moved, Optional.empty()));
        assertEquals(1, move.replacement().orElseThrow().joined().size(), "B is replaced in the same job");
        Map<UUID, RoadPlanParams> after = RoadsModule.roads(this.operations.records());
        End start = after.get(B).ends().get(0);
        assertEquals(List.of(34, 64, 0), List.of(start.x(), start.y(), start.z()), "B now starts where A ends");
        assertTrue(RoadGraph.of(after).taken(A, 1), "still joined");
        assertWorld(move.plan().orElseThrow(), "the move leaves both roads as planned, and nothing else");
        Map<String, String> movedWorld = new HashMap<>(this.world.blocks);

        this.operations.undo(ALICE);
        run();
        assertEquals(nonAir(placed), nonAir(this.world.blocks), "undo puts both roads back as placed");
        assertEquals(30, RoadsModule.roads(this.operations.records()).get(B).ends().get(0).x());
        this.operations.redo(ALICE);
        run();
        assertEquals(nonAir(movedWorld), nonAir(this.world.blocks), "redo moves both again");

        // A deleted alone: B stays as it followed, and where A had taken over B's old start, there is ground again.
        PlanResult delete = replace(DELETE, RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, A, after.get(A), Optional.empty()));
        assertTrue(delete.replacement().orElseThrow().joined().isEmpty());
        assertWorld(RoadPlanner.asPlaced(after.get(B), WorldLimits.OVERWORLD).plan().orElseThrow(), "only B is left");
    }

    private void place(UUID id, RoadPlanParams params) {
        PlanResult result = RoadsModule.INSTANCE.plan(params, WorldLimits.OVERWORLD, this.operations.records());
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        assertTrue(this.operations.apply(ALICE, result.plan().orElseThrow(), id, Optional.empty(), draft(params, result)).isAccepted());
        run();
    }

    private PlanResult replace(UUID id, RoadPlanParams request) {
        RoadPlanParams params = RoadsModule.INSTANCE.settle(request, this.operations.records());
        PlanResult result = RoadsModule.INSTANCE.plan(params, WorldLimits.OVERWORLD, this.operations.records());
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        BlockPlan plan = params.tool() == RoadPlanParams.Tool.DELETE ? BlockPlan.builder(3465).build() : result.plan().orElseThrow();
        Operations.Request request1 = this.operations.replace(ALICE, plan, result.replacement().orElseThrow(), id, draft(params, result));
        assertTrue(request1.isAccepted(), () -> "refused " + request1.refusal());
        run();
        assertEquals(OperationRecord.Status.APPLIED, this.operations.record(id).orElseThrow().status());
        return result;
    }

    private static OperationRecord.Draft draft(RoadPlanParams params, PlanResult result) {
        return new OperationRecord.Draft("roads", params.tool().name(), RoadsModule.INSTANCE.paramsToJson(params), "ab".repeat(32), result.sideEffects());
    }

    private void run() {
        List<Operations.Result> results = new ArrayList<>();
        for (int tick = 0; tick < 1000 && (this.operations.isBusy(ALICE) || !this.operations.scheduler().isIdle()); tick++) {
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            results.addAll(this.operations.afterTick());
        }
        results.forEach(result -> assertEquals(Optional.empty(), result.code(), () -> "failed: " + result));
    }

    /** Every block of the world is {@code plan}'s where it has one, and air everywhere else. */
    private void assertWorld(BlockPlan plan, String message) {
        Map<String, String> expected = new HashMap<>();
        plan.blocks().forEach(block -> expected.put(FakeWorld.key(block.x(), block.y(), block.z()), block.state()));
        assertEquals(nonAir(expected), nonAir(this.world.blocks), message);
    }

    private static Map<String, String> nonAir(Map<String, String> blocks) {
        Map<String, String> solid = new HashMap<>();
        Set<String> air = new HashSet<>(Set.of("minecraft:air", "minecraft:cave_air"));
        blocks.forEach((key, state) -> {
            if (!air.contains(state)) {
                solid.put(key, state);
            }
        });
        return solid;
    }
}
