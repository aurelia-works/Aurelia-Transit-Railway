/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.PlanModule;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.spi.ToolEnvironment;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * A {@link ToolEnvironment} cranked by hand: background work and client-thread tasks wait in queues until a test runs
 * them, so it decides exactly when a plan "arrives". Sent plans are recorded with the request ids they got.
 */
public final class ManualEnvironment implements ToolEnvironment {
    /** A plan the session sent. */
    public record Sent(int requestId, String module, Object params, BlockPlan plan, List<SideEffect> sideEffects) {
    }

    public final List<FutureTask<?>> background = new ArrayList<>();
    public final List<Runnable> clientThread = new ArrayList<>();
    public final List<Sent> sent = new ArrayList<>();
    /** World tasks the session sent (B-3), by request id. */
    public final Map<Integer, WorldTask> tasks = new java.util.LinkedHashMap<>();
    public final List<Throwable> problems = new ArrayList<>();
    /** Commits the session reported placed (R-6), with their operation ids. */
    public final List<Map.Entry<Commit<?>, UUID>> placed = new ArrayList<>();
    public WorldLimits limits = WorldLimits.OVERWORLD;
    public boolean editing = true;
    private int nextId = 7;

    @Override
    public WorldLimits limits() {
        return this.limits;
    }

    @Override
    public Future<?> background(Runnable work) {
        FutureTask<Void> task = new FutureTask<>(work, null);
        this.background.add(task);
        return task;
    }

    @Override
    public void onClientThread(Runnable task) {
        this.clientThread.add(task);
    }

    @Override
    public <P> EditorClient.Attempt send(PlanModule<P> module, P params, BlockPlan plan, List<SideEffect> sideEffects) {
        if (!this.editing) {
            return new EditorClient.Attempt(OptionalInt.empty(), Optional.of(plan), Optional.of(Protocol.EDITING_OFF), Map.of());
        }
        int id = this.nextId++;
        this.sent.add(new Sent(id, module.id(), params, plan, sideEffects));
        return new EditorClient.Attempt(OptionalInt.of(id), Optional.of(plan), Optional.empty(), Map.of());
    }

    @Override
    public OptionalInt sendTask(WorldTask task) {
        if (!this.editing) {
            return OptionalInt.empty();
        }
        int id = this.nextId++;
        this.tasks.put(id, task);
        return OptionalInt.of(id);
    }

    @Override
    public void problem(String message, Throwable error) {
        this.problems.add(error);
    }

    @Override
    public void placed(Commit<?> commit, UUID operationId) {
        this.placed.add(Map.entry(commit, operationId));
    }

    /** Runs the waiting background work (a cancelled task does nothing); returns how many weren't cancelled. */
    public int runBackground() {
        List<FutureTask<?>> tasks = new ArrayList<>(this.background);
        this.background.clear();
        int ran = 0;
        for (FutureTask<?> task : tasks) {
            if (!task.isCancelled()) {
                ran++;
            }
            task.run();
        }
        return ran;
    }

    /** Runs the waiting client-thread tasks. */
    public void runClientThread() {
        List<Runnable> tasks = new ArrayList<>(this.clientThread);
        this.clientThread.clear();
        tasks.forEach(Runnable::run);
    }

    /** Runs everything until nothing waits: the plans "arrive". */
    public void runAll() {
        while (!this.background.isEmpty() || !this.clientThread.isEmpty()) {
            runBackground();
            runClientThread();
        }
    }
}
