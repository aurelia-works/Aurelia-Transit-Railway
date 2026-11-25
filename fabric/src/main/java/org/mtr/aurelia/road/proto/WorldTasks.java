/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.mtr.aurelia.road.bake.Bake;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.proto.EditorServer.EditWorld;
import org.mtr.aurelia.road.proto.Message.TaskProgress;
import org.mtr.aurelia.road.proto.Message.TaskResult;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.RoadHistory;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.SideSwitch;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.spi.BlockFinder;
import org.mtr.aurelia.road.spi.MessageChannel;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;

/**
 * B-3's world tasks, which {@link EditorServer} runs for its players, one at a time each: a step a tick, every change an
 * ordinary operation of the player's, so undo takes it back.
 * <ul>
 *   <li><b>Bake world</b> ({@link Protocol#TASK_BAKE_WORLD}): in each dimension in turn, the {@link BlockFinder} reads
 *       every chunk for Stratos blocks, and those found are baked ({@link Bake}) as one operation there.</li>
 *   <li><b>Bake road</b> ({@link Protocol#TASK_BAKE_ROAD}): the same, for the blocks of one of the player's roads (its plan
 *       as placed today) in their dimension.</li>
 *   <li><b>Redraw markings</b> ({@link Protocol#TASK_REDRAW_MARKINGS}; owner, 2026-10-14): each of the player's roads, in
 *       each dimension, whose palette paints line blocks is rebuilt from its record with today's rules (R-7's rebuild),
 *       keeping blocks changed by hand, each road its own operation (owner, 2026-10-15). A road that would change nothing
 *       is left; another player's is skipped and counted.</li>
 *   <li><b>Switch driving side</b> ({@link Protocol#TASK_DRIVE_LEFT}, {@link Protocol#TASK_DRIVE_RIGHT}; P9): the world's side is
 *       set (its game rule), then each of the player's roads and junctions that keeps to the other side is rebuilt for it
 *       ({@link SideSwitch}), junctions first, each its own operation. It selects by the side each road keeps, so running it
 *       again carries on where a stopped one left off. A road that changes no block has nothing for undo to take back (its
 *       record and track change; the server's log counts those). The operator's override ({@link Protocol#TASK_DRIVE_LEFT_ALL},
 *       {@link Protocol#TASK_DRIVE_RIGHT_ALL}) rebuilds every player's roads, each as an operation of the player who drew it, so
 *       that player can undo it; a road built by more than one player is left and counted.</li>
 * </ul>
 * Progress goes to the player about once a second ({@code TaskProgress}), then one {@code TaskResult}. A world bake that
 * leaves no Stratos blocks anywhere, none kept and none placed while it ran, turns the world's mark off (UI-1c). A bake stops at
 * the first operation that fails, saying why; a redraw counts a road that fails (the log says why) and carries on. What a
 * task did stays, and can be undone. Server thread only.
 */
final class WorldTasks {
    private final Supplier<List<EditWorld>> worlds;
    private final BlockFinder finder;
    private final Executor planners;
    private final LongSupplier clock;
    private final Supplier<UUID> operationIds;
    private final String coreVersion;
    private final int dataVersion;
    private final Consumer<String> log;
    /** UI-1c: Stratos blocks placed so far, a count that only grows. */
    private final LongSupplier placements;
    /** UI-1c: a world bake left no Stratos blocks. */
    private final Runnable noneLeft;
    /** P9: where a switch of driving side sets the world's side ({@code true} for right-hand). */
    private final Consumer<Boolean> setSide;
    /** How many ticks (10 s) a rebuild waits for its road's author to finish an operation of their own before it counts as failed. */
    private static final int BUSY_WAIT_TICKS = 200;
    private final Map<UUID, Task> tasks = new LinkedHashMap<>();

    WorldTasks(Supplier<List<EditWorld>> worlds, BlockFinder finder, Executor planners, LongSupplier clock, Supplier<UUID> operationIds,
            String coreVersion, int dataVersion, Consumer<String> log, LongSupplier placements, Runnable noneLeft, Consumer<Boolean> setSide) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.finder = Objects.requireNonNull(finder, "finder");
        this.planners = Objects.requireNonNull(planners, "planners");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
        this.coreVersion = Objects.requireNonNull(coreVersion, "coreVersion");
        this.dataVersion = dataVersion;
        this.log = Objects.requireNonNull(log, "log");
        this.placements = Objects.requireNonNull(placements, "placements");
        this.noneLeft = Objects.requireNonNull(noneLeft, "noneLeft");
        this.setSide = Objects.requireNonNull(setSide, "setSide");
    }

    /** Whether {@code player} has a task running. */
    boolean busy(UUID player) {
        return this.tasks.containsKey(player);
    }

    /**
     * Starts {@code task} for {@code player}, who may run it and has nothing else running; {@code here} is the world they
     * are in. A road to bake must be one of theirs standing there, else the result says why at once.
     */
    void start(UUID player, MessageChannel channel, int requestId, String task, Optional<UUID> roadId, EditWorld here) {
        Task started = switch (task) {
            case Protocol.TASK_BAKE_WORLD -> new BakeTask(player, channel, requestId, this.worlds.get(), null);
            case Protocol.TASK_BAKE_ROAD -> bakeRoad(player, channel, requestId, roadId.orElseThrow(), here);
            case Protocol.TASK_REDRAW_MARKINGS -> new RedrawTask(player, channel, requestId, this.worlds.get());
            case Protocol.TASK_DRIVE_LEFT, Protocol.TASK_DRIVE_RIGHT, Protocol.TASK_DRIVE_LEFT_ALL, Protocol.TASK_DRIVE_RIGHT_ALL ->
                    new SwitchSideTask(player, channel, requestId, this.worlds.get(), Protocol.drivesRight(task), Protocol.isDriveAllTask(task));
            default -> throw new IllegalArgumentException("No world task " + task);
        };
        if (started != null) {
            this.tasks.put(player, started);
        }
    }

    private Task bakeRoad(UUID player, MessageChannel channel, int requestId, UUID roadId, EditWorld here) {
        List<OperationRecord> records = EditorServer.applied(here.operations(), RoadsModule.ID);
        RoadHistory.Road road = RoadsModule.standing(records).get(roadId);
        if (road == null) {
            channel.send(new TaskResult(requestId, false, Optional.of(Operations.Refusal.ROAD_GONE.name()), Map.of()));
            return null;
        }
        if (!yours(road, here.operations(), player.toString())) {
            channel.send(new TaskResult(requestId, false, Optional.of(Operations.Refusal.NOT_YOUR_ROAD.name()), Map.of()));
            return null;
        }
        RoadPlanParams params = road.params();
        // The road's blocks: its plan as placed, worked out off the server thread.
        FutureTask<Set<Long>> cells = new FutureTask<>(() -> {
            Set<Long> keys = new HashSet<>();
            RoadPlanner.asPlaced(params, here.limits()).plan()
                    .ifPresent(plan -> plan.blocks().forEach(block -> keys.add(BlockChange.positionKey(block.x(), block.y(), block.z()))));
            return keys;
        });
        BakeTask task = new BakeTask(player, channel, requestId, List.of(here), cells);
        if (!submit(cells)) {
            channel.send(new TaskResult(requestId, false, Optional.of(Protocol.INTERNAL_ERROR), Map.of()));
            return null;
        }
        return task;
    }

    /** Whether every operation that built {@code road} is {@code owner}'s (R-7: a rebuild of another's is refused). */
    private static boolean yours(RoadHistory.Road road, Operations operations, String owner) {
        for (UUID id : road.chain()) {
            Optional<OperationRecord> record = operations.record(id);
            if (record.isEmpty() || !record.get().author().equals(owner)) {
                return false;
            }
        }
        return true;
    }

    /** The one player whose operations all built {@code road}; empty if it has more than one author, or a record is gone. */
    private static Optional<String> authorOf(RoadHistory.Road road, Operations operations) {
        String author = null;
        for (UUID id : road.chain()) {
            Optional<OperationRecord> record = operations.record(id);
            if (record.isEmpty() || (author != null && !author.equals(record.get().author()))) {
                return Optional.empty();
            }
            author = record.get().author();
        }
        return Optional.ofNullable(author);
    }

    /** Once per server tick: every task takes its next step. */
    void tick() {
        long now = this.clock.getAsLong();
        for (Task task : List.copyOf(this.tasks.values())) {
            try {
                task.tick(now);
            } catch (RuntimeException e) {
                this.log.accept("[stratos] a world task of " + task.player + " failed: " + e);
                task.end(false, Optional.of(Protocol.INTERNAL_ERROR), Map.of());
            }
            if (task.ended) {
                this.tasks.remove(task.player);
            }
        }
    }

    /** An operation finished: the player whose task it was, which then carries on; empty if it was no task's. */
    Optional<UUID> finished(Operations.Result result) {
        for (Task task : this.tasks.values()) {
            if (task.running == result.handle()) {
                task.running = null;
                task.finished(result);
                if (task.ended) {
                    this.tasks.remove(task.player);
                }
                return Optional.of(task.player);
            }
        }
        return Optional.empty();
    }

    /** {@code player} left: their task stops after the operation running now, if any, which finishes on its own. */
    void cancel(UUID player) {
        Task task = this.tasks.remove(player);
        if (task != null) {
            task.stop();
        }
    }

    /** The server is stopping. */
    void stop() {
        this.tasks.values().forEach(Task::stop);
        this.tasks.clear();
    }

    private boolean submit(FutureTask<?> work) {
        try {
            this.planners.execute(work);
            return true;
        } catch (RejectedExecutionException e) {
            this.log.accept("[stratos] couldn't start a world task's work: " + e);
            return false;
        }
    }

    /** {@code work}'s answer once done; null while it isn't. Throws what it threw. */
    private static <T> T doneOrNull(FutureTask<T> work) throws ExecutionException {
        if (!work.isDone()) {
            return null;
        }
        try {
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e);
        } catch (CancellationException e) {
            throw new ExecutionException(e);
        }
    }

    private abstract class Task {
        final UUID player;
        final String owner;
        final MessageChannel channel;
        final int requestId;
        long lastProgress;
        /** The operation in flight, until its result. */
        Operations.Handle running;
        boolean ended;

        Task(UUID player, MessageChannel channel, int requestId) {
            this.player = player;
            this.owner = player.toString();
            this.channel = channel;
            this.requestId = requestId;
            this.lastProgress = WorldTasks.this.clock.getAsLong() - EditorServer.PROGRESS_INTERVAL_NANOS; // the first report goes at once
        }

        abstract void tick(long now);

        abstract void finished(Operations.Result result);

        void stop() {
            this.ended = true;
        }

        /** Tells the player how far it has got, at most once a second. */
        void progress(long now, String stage, int done, int total) {
            if (now - this.lastProgress >= EditorServer.PROGRESS_INTERVAL_NANOS) {
                this.lastProgress = now;
                this.channel.send(new TaskProgress(this.requestId, stage, StrictMath.min(done, total), total));
            }
        }

        void end(boolean ok, Optional<String> code, Map<String, String> args) {
            if (!this.ended) {
                this.ended = true;
                this.channel.send(new TaskResult(this.requestId, ok, code, args));
            }
        }

        /** The operation {@code request} started, or the task ends with its refusal. */
        boolean started(Operations.Request request) {
            if (request.isAccepted()) {
                this.running = request.handle().orElseThrow();
                return true;
            }
            end(false, Optional.of(request.refusal().map(Enum::name).orElse(Protocol.BUSY)), Map.of());
            return false;
        }
    }

    /** Bake to vanilla: each world's Stratos blocks (or one road's), found, then baked as one operation there. */
    private final class BakeTask extends Task {
        private final List<EditWorld> worlds;
        /** A road's blocks, still being worked out; null for a world bake, and once known ({@link #cells}). */
        private FutureTask<Set<Long>> cellsWork;
        private Optional<Set<Long>> cells = Optional.empty();
        private final boolean road;
        private int index;
        private BlockFinder.Search search;
        private FutureTask<PlanAndHash> planning;
        private int blocks;
        private int kept;
        /** Some Stratos blocks found have no vanilla block, so they stay. */
        private boolean unknownLeft;
        /** Stratos blocks placed before the bake started (UI-1c). */
        private final long placementsAtStart;

        BakeTask(UUID player, MessageChannel channel, int requestId, List<EditWorld> worlds, FutureTask<Set<Long>> cells) {
            super(player, channel, requestId);
            this.worlds = List.copyOf(worlds);
            this.cellsWork = cells;
            this.road = cells != null;
            this.placementsAtStart = WorldTasks.this.placements.getAsLong();
        }

        @Override
        void tick(long now) {
            if (this.cellsWork != null) {
                Set<Long> known;
                try {
                    known = doneOrNull(this.cellsWork);
                } catch (ExecutionException e) {
                    WorldTasks.this.log.accept("[stratos] working out a road's blocks to bake failed: " + e.getCause());
                    end(false, Optional.of(Protocol.INTERNAL_ERROR), Map.of());
                    return;
                }
                if (known == null) {
                    return;
                }
                this.cellsWork = null;
                this.cells = Optional.of(known);
            }
            if (this.running != null) {
                this.running.job().ifPresent(job -> progress(now, Protocol.STAGE_BAKING, job.sectionsDone(), job.sectionCount()));
                return;
            }
            if (this.planning != null) {
                plan();
                return;
            }
            if (this.index >= this.worlds.size()) {
                if (!this.road && this.kept == 0 && !this.unknownLeft && WorldTasks.this.placements.getAsLong() == this.placementsAtStart) {
                    WorldTasks.this.noneLeft.run(); // UI-1c: every dimension read and baked, nothing of Stratos's left
                }
                end(true, Optional.empty(), Map.of("blocks", Integer.toString(this.blocks), "kept", Integer.toString(this.kept)));
                return;
            }
            Operations operations = this.worlds.get(this.index).operations();
            if (this.search == null) {
                this.search = WorldTasks.this.finder.find(operations.dimension(), this.cells);
            }
            this.search.tick();
            if (!this.search.finished()) {
                progress(now, Protocol.STAGE_FINDING, this.search.done(), this.search.total());
                return;
            }
            BlockFinder.Search searched = this.search;
            this.search = null;
            if (searched.failure().isPresent()) {
                WorldTasks.this.log.accept("[stratos] looking for Stratos blocks in " + operations.dimension() + " failed: " + searched.failure().get());
                end(false, Optional.of(Protocol.FIND_FAILED), args());
                return;
            }
            List<Bake.Found> found = searched.found();
            if (found.isEmpty()) {
                this.index++;
                return;
            }
            // Building and hashing a big plan takes a while: off the server thread.
            this.planning = new FutureTask<>(() -> {
                Optional<Bake.Plan> plan = Bake.plan(WorldTasks.this.dataVersion, found);
                return new PlanAndHash(plan, plan.map(p -> Digest.of(PlanHash.of(p.writes(), WorldTasks.this.coreVersion)).hex()).orElse(""));
            });
            if (!submit(this.planning)) {
                end(false, Optional.of(Protocol.INTERNAL_ERROR), args());
            }
        }

        /** Applies the plan worked out for the world at {@link #index}, once it is ready. */
        private void plan() {
            PlanAndHash planned;
            try {
                planned = doneOrNull(this.planning);
            } catch (ExecutionException e) {
                WorldTasks.this.log.accept("[stratos] working out a bake failed: " + e.getCause());
                end(false, Optional.of(Protocol.INTERNAL_ERROR), args());
                return;
            }
            if (planned == null) {
                return;
            }
            this.planning = null;
            Operations operations = this.worlds.get(this.index).operations();
            if (planned.plan().isEmpty()) {
                this.unknownLeft = true; // blocks were found, none with a vanilla block
                this.index++;
                return;
            }
            Bake.Plan plan = planned.plan().get();
            if (!plan.unknown().isEmpty()) {
                this.unknownLeft = true;
                WorldTasks.this.log.accept("[stratos] baking " + operations.dimension() + " leaves Stratos blocks this Stratos has no vanilla block for: "
                        + plan.unknown());
            }
            String params = "{\"blocks\":" + plan.blockCount() + "}";
            OperationRecord.Draft draft = new OperationRecord.Draft(EditorServer.WORLD_MODULE, this.road ? Protocol.TASK_BAKE_ROAD : Protocol.TASK_BAKE_WORLD,
                    params, planned.hash(), List.of());
            started(operations.apply(this.owner, plan.writes(), WorldTasks.this.operationIds.get(), Optional.of(plan.expected()), draft));
        }

        @Override
        void finished(Operations.Result result) {
            ApplyJob job = result.job().orElse(null);
            if (job != null) {
                this.blocks += job.changedBlocks();
                this.kept += job.skippedBlocks();
            }
            if (result.code().isPresent()) {
                end(false, result.code(), args());
                return;
            }
            this.index++;
        }

        private Map<String, String> args() {
            return Map.of("blocks", Integer.toString(this.blocks), "kept", Integer.toString(this.kept));
        }

        @Override
        void stop() {
            super.stop();
            if (this.search != null) {
                this.search.cancel();
            }
            if (this.planning != null) {
                this.planning.cancel(true);
            }
            if (this.cellsWork != null) {
                this.cellsWork.cancel(true);
            }
        }
    }

    private record PlanAndHash(Optional<Bake.Plan> plan, String hash) {
    }

    /** One road to rebuild: its world, its id, and whose operation the rebuild is (its author's: the road's operations must all be theirs). */
    private record Target(EditWorld world, UUID road, String owner) {
    }

    /** A road's rebuild, worked out: what replace needs. */
    private record Rebuild(RoadPlanParams params, PlanResult result) {
    }

    /**
     * A rebuild of each of the player's roads in turn, from its record, each its own operation: Redraw markings (B-3) and the
     * switch of driving side (P9). A subclass says which roads and what each is rebuilt as.
     */
    private abstract class RebuildTask extends Task {
        final List<Target> roads = new ArrayList<>();
        int others;
        int index;
        int changed;
        int unchanged;
        int failed;
        private final String stage;
        private FutureTask<Rebuild> planning;
        /** Ticks the road at {@link #index} has waited for its author's other operation to finish. */
        private int waited;

        RebuildTask(UUID player, MessageChannel channel, int requestId, String stage) {
            super(player, channel, requestId);
            this.stage = stage;
        }

        /** Road {@code road} as it stands among the roads {@code standing} (their parameters by id), as a rebuild; null if it can't be. */
        abstract RoadPlanParams rebuild(RoadHistory.Road road, Map<UUID, RoadPlanParams> standing);

        /** What the player is told when every road is done. */
        Map<String, String> summary() {
            Map<String, String> args = new LinkedHashMap<>();
            args.put("roads", Integer.toString(this.changed));
            args.put("unchanged", Integer.toString(this.unchanged));
            args.put("others", Integer.toString(this.others));
            args.put("failed", Integer.toString(this.failed));
            return args;
        }

        @Override
        void tick(long now) {
            if (this.running != null) {
                progress(now, this.stage, this.index, this.roads.size());
                return;
            }
            if (this.planning != null) {
                replace();
                return;
            }
            if (this.index >= this.roads.size()) {
                end(true, Optional.empty(), summary());
                return;
            }
            progress(now, this.stage, this.index, this.roads.size());
            Target target = this.roads.get(this.index);
            // The roads as they stand now (an earlier road's rebuild is among them), copied on this thread.
            List<OperationRecord> placed = EditorServer.applied(target.world().operations(), RoadsModule.ID);
            Map<UUID, RoadHistory.Road> standing = RoadsModule.standing(placed);
            RoadHistory.Road road = standing.get(target.road());
            RoadPlanParams params = road == null ? null : rebuild(road, RoadHistory.params(standing));
            if (params == null) {
                this.failed++;
                this.index++;
                return;
            }
            this.planning = new FutureTask<>(() -> new Rebuild(params, RoadsModule.INSTANCE.plan(params, target.world().limits(), placed)));
            if (!submit(this.planning)) {
                end(false, Optional.of(Protocol.INTERNAL_ERROR), Map.of());
            }
        }

        /** Sends the rebuild worked out for the road at {@link #index} to replace it, once it is ready. */
        private void replace() {
            Rebuild rebuild;
            try {
                rebuild = doneOrNull(this.planning);
            } catch (ExecutionException e) {
                WorldTasks.this.log.accept("[stratos] working out a road's rebuild failed: " + e.getCause());
                this.planning = null;
                this.failed++;
                this.index++;
                return;
            }
            if (rebuild == null) {
                return;
            }
            this.planning = null;
            Target target = this.roads.get(this.index);
            PlanResult result = rebuild.result();
            if (!result.isPlanned() || result.replacement().isEmpty()) {
                WorldTasks.this.log.accept("[stratos] rebuilding road " + target.road() + " was refused: " + result.code().orElse("?"));
                this.failed++;
                this.index++;
                return;
            }
            BlockPlan plan = result.plan().orElseThrow();
            EditWorld world = target.world();
            OperationRecord.Draft draft = new OperationRecord.Draft(RoadsModule.ID, RoadsModule.INSTANCE.tool(rebuild.params()),
                    RoadsModule.INSTANCE.paramsToJson(rebuild.params()), Digest.of(PlanHash.of(plan, result.sideEffects(), WorldTasks.this.coreVersion)).hex(),
                    result.sideEffects(), RoadsModule.INSTANCE.palettes(rebuild.params(), world.limits()));
            Operations.Request request = world.operations().replace(target.owner(), plan, result.replacement().get(), WorldTasks.this.operationIds.get(), draft);
            if (!request.isAccepted() && request.refusal().orElse(null) == Operations.Refusal.BUSY && this.waited++ < BUSY_WAIT_TICKS) {
                return; // the road's author has an operation of their own running: plan the road again next tick (the override's)
            }
            if (!request.isAccepted()) {
                WorldTasks.this.log.accept("[stratos] rebuilding road " + target.road() + " was refused: " + request.refusal().orElse(null));
                this.waited = 0;
                this.failed++;
                this.index++;
                return;
            }
            this.waited = 0;
            this.running = request.handle().orElseThrow();
        }

        @Override
        void finished(Operations.Result result) {
            this.index++;
            Optional<String> code = result.code();
            if (code.isEmpty()) {
                this.changed++;
            } else if (code.get().equals(Protocol.NOTHING_TO_CHANGE)) {
                this.unchanged++;
            } else {
                this.failed++;
                WorldTasks.this.log.accept("[stratos] rebuilding a road ended " + code.get());
            }
        }

        @Override
        void stop() {
            super.stop();
            if (this.planning != null) {
                this.planning.cancel(true);
            }
        }
    }

    /** Redraw markings: each of the player's roads with line blocks, rebuilt in turn. */
    private final class RedrawTask extends RebuildTask {
        RedrawTask(UUID player, MessageChannel channel, int requestId, List<EditWorld> worlds) {
            super(player, channel, requestId, Protocol.STAGE_REDRAWING);
            for (EditWorld world : worlds) {
                List<OperationRecord> records = EditorServer.applied(world.operations(), RoadsModule.ID);
                for (RoadHistory.Road road : RoadsModule.standing(records).values()) {
                    if (road.params().tool() == RoadPlanParams.Tool.JUNCTION) {
                        continue; // G-11: a junction isn't rebuilt (its give-way lines are its own)
                    }
                    Optional<Palette> palette = world.limits().palettes().byId(road.params().paletteId());
                    if (palette.isEmpty() || !(palette.get().lines(LineMarking.Colour.WHITE) || palette.get().lines(LineMarking.Colour.YELLOW))) {
                        continue; // no line blocks to redraw
                    }
                    if (yours(road, world.operations(), this.owner)) {
                        this.roads.add(new Target(world, road.id(), this.owner));
                    } else {
                        this.others++;
                    }
                }
            }
        }

        @Override
        RoadPlanParams rebuild(RoadHistory.Road road, Map<UUID, RoadPlanParams> standing) {
            return RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, road.id(), road.params(), Optional.of(RoadPlanParams.ManualEdits.KEEP));
        }
    }

    /**
     * Switch driving side (P9): the world's side is set first, so a plan for the old side is refused from then on, then every
     * road and junction that keeps to the other side is rebuilt for the new one ({@link SideSwitch#pending}: junctions first).
     * By default only the player's own are; another player's are skipped and counted, as Redraw skips them. With
     * {@code everyone} (the operator's override) every player's are, each as an operation of its author: the road's operations
     * must all be one author's ({@link Operations#replace}), so a road built by several is skipped and counted, and the author
     * can undo the rebuild of their road.
     */
    private final class SwitchSideTask extends RebuildTask {
        private final RoadPlanParams.DrivingSide side;
        /** Roads whose switch changed no block, so undo has nothing of theirs to take back. */
        private int noUndo;
        private final boolean everyone;

        SwitchSideTask(UUID player, MessageChannel channel, int requestId, List<EditWorld> worlds, boolean rightHand, boolean everyone) {
            super(player, channel, requestId, Protocol.STAGE_SWITCHING);
            this.everyone = everyone;
            this.side = rightHand ? RoadPlanParams.DrivingSide.RHT : RoadPlanParams.DrivingSide.LHT;
            WorldTasks.this.setSide.accept(rightHand);
            for (EditWorld world : worlds) {
                Map<UUID, RoadHistory.Road> standing = RoadsModule.standing(EditorServer.applied(world.operations(), RoadsModule.ID));
                for (UUID id : SideSwitch.pending(RoadHistory.params(standing), this.side)) {
                    RoadHistory.Road road = standing.get(id);
                    Optional<String> author = everyone ? authorOf(road, world.operations())
                            : yours(road, world.operations(), this.owner) ? Optional.of(this.owner) : Optional.empty();
                    if (author.isPresent()) {
                        this.roads.add(new Target(world, id, author.get()));
                    } else {
                        this.others++;
                    }
                }
            }
        }

        @Override
        RoadPlanParams rebuild(RoadHistory.Road road, Map<UUID, RoadPlanParams> standing) {
            return SideSwitch.converted(road.id(), standing, this.side)
                    .map(switched -> RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, road.id(), switched, Optional.of(RoadPlanParams.ManualEdits.KEEP)))
                    .orElse(null);
        }

        @Override
        void finished(Operations.Result result) {
            if (result.code().isEmpty() && result.job().map(ApplyJob::changedBlocks).orElse(0) == 0) {
                this.noUndo++;
            }
            super.finished(result);
        }

        @Override
        Map<String, String> summary() {
            WorldTasks.this.log.accept("[stratos] " + this.owner + " switched the world to " + this.side + (this.everyone ? " for everyone" : "") + ": " + this.changed + " roads and junctions rebuilt ("
                    + this.noUndo + " changed no block, so undo has nothing of theirs to take back), " + this.others + " left on the old side (" + (this.everyone ? "built by several players" : "other players'") + "), "
                    + this.failed + " failed");
            return super.summary();
        }
    }
}
