package org.mtr.aurelia.road.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.DrivingSide;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.world.Migrations;
import org.mtr.aurelia.road.world.WorldData;

/**
 * P9's world task that switches the world's driving side, as run by an operator: the default rebuilds only the operator's own
 * roads, the override ({@code DRIVE_RIGHT_ALL}) rebuilds every player's, each as an operation of the player who drew it.
 */
class WorldTasksSwitchTest {
    private static final String ALICE = "00000000-0000-0000-0000-00000000a11c";
    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final WorldLimits LIMITS = WorldLimits.OVERWORLD;

    private final AtomicLong clock = new AtomicLong();
    private final FakeWorld world = new FakeWorld();
    private final List<Message> sent = new ArrayList<>();
    private final List<Boolean> sides = new ArrayList<>();
    private Operations operations;
    private WorldTasks tasks;
    private final UUID road = new UUID(0, 1);

    @BeforeEach
    void alicesRoadStands() throws IOException {
        SideEffectWriter writer = new SideEffectWriter() {
            @Override
            public CompletableFuture<List<Result>> make(String dimension, List<org.mtr.aurelia.road.undo.SideEffect> effects) {
                return CompletableFuture.completedFuture(effects.stream().map(effect -> Result.DONE).toList());
            }

            @Override
            public CompletableFuture<List<Result>> remove(String dimension, List<org.mtr.aurelia.road.undo.SideEffect> effects) {
                return CompletableFuture.completedFuture(effects.stream().map(effect -> Result.DONE).toList());
            }
        };
        WorldData.Settings settings = new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(0x5d, this.clock.incrementAndGet()), writer,
                () -> Instant.ofEpochSecond(this.clock.incrementAndGet()), 3465, "stratos 0.1.0+mc1.20.1");
        this.operations = WorldData.load(new FakeStorage(), settings, Migrations.ALL, line -> {
        }).operations(Operations.OVERWORLD);
        this.tasks = new WorldTasks(() -> List.of(new EditorServer.EditWorld(this.operations, LIMITS)), (dimension, cells) -> {
            throw new UnsupportedOperationException();
        }, Runnable::run, () -> this.clock.incrementAndGet() * 2_000_000_000L, () -> new UUID(0x7a, this.clock.incrementAndGet()), "stratos 0.1.0",
                3465, line -> {
                }, () -> 0L, () -> {
                }, this.sides::add);
        RoadPlanParams left = RoadsModule.INSTANCE.settle(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E)),
                this.operations.records());
        PlanResult result = RoadsModule.INSTANCE.plan(left, LIMITS, this.operations.records());
        assertTrue(this.operations.apply(ALICE, result.plan().orElseThrow(), this.road, Optional.empty(), new OperationRecord.Draft("roads",
                left.tool().name(), RoadsModule.INSTANCE.paramsToJson(left), "ab".repeat(32), result.sideEffects())).isAccepted());
        run(null);
    }

    /** The operator's own switch leaves Alice's road on the old side and counts it; the override rebuilds it, under Alice. */
    @Test
    void theOverrideRebuildsAnotherPlayersRoadAsItsAuthor() {
        assertEquals(Map.of("roads", "0", "unchanged", "0", "others", "1", "failed", "0"), run(Protocol.TASK_DRIVE_RIGHT).args());
        assertEquals(DrivingSide.LHT, RoadsModule.roads(this.operations.records()).get(this.road).drivingSide(), "not the operator's, so left alone");

        Message.TaskResult result = run(Protocol.TASK_DRIVE_RIGHT_ALL);
        assertTrue(result.ok(), () -> result.toString());
        assertEquals(Map.of("roads", "1", "unchanged", "0", "others", "0", "failed", "0"), result.args());
        assertEquals(DrivingSide.RHT, RoadsModule.roads(this.operations.records()).get(this.road).drivingSide());
        OperationRecord rebuild = this.operations.records().get(this.operations.records().size() - 1);
        assertEquals(ALICE, rebuild.author(), "filed as her operation, which she can undo");
        assertEquals(List.of(true, true), this.sides, "the world's side was set each time");
    }

    /** One task to its result, in the server's order: the task ticks, the operations tick, finished ones are told to the task. */
    private Message.TaskResult run(String task) {
        this.sent.clear();
        if (task != null) {
            this.tasks.start(OPERATOR, this.sent::add, 1, task, Optional.empty(), new EditorServer.EditWorld(this.operations, LIMITS));
        }
        for (int tick = 0; tick < 4000 && (task == null || this.tasks.busy(OPERATOR)) && (this.operations.isBusy(ALICE) || !this.operations.scheduler().isIdle()
                || task != null); tick++) {
            this.tasks.tick();
            this.operations.scheduler().tick(this.world, 50_000_000, () -> this.world.now);
            for (Operations.Result finished : this.operations.afterTick()) {
                this.tasks.finished(finished);
            }
        }
        return task == null ? null : this.sent.stream().filter(Message.TaskResult.class::isInstance).map(Message.TaskResult.class::cast).findFirst().orElseThrow();
    }
}
