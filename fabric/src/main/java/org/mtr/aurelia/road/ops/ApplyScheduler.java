/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;
import org.mtr.aurelia.road.spi.WorldWriter;

/**
 * The apply jobs of one world, first in, first out, sharing one time budget per tick. A job that is waiting (for
 * chunks, or for the next tick's budget) holds up the ones behind it, so an undo submitted after its plan always runs
 * after it.
 */
public final class ApplyScheduler {
    private final Deque<ApplyJob> queue = new ArrayDeque<>();

    /** Queues {@code plan} and returns its job, to watch or to undo later. */
    public ApplyJob submit(BlockPlan plan) {
        return submit(new ApplyJob(plan));
    }

    /** Queues a job made elsewhere (for example a guarded undo or redo) and returns it. */
    public ApplyJob submit(ApplyJob job) {
        this.queue.add(job);
        return job;
    }

    /** Runs jobs for up to {@code budgetNanos}; returns the time used. */
    public long tick(WorldWriter world, long budgetNanos, LongSupplier clock) {
        long start = clock.getAsLong();
        while (!this.queue.isEmpty()) {
            long left = budgetNanos - (clock.getAsLong() - start);
            if (left <= 0) {
                break;
            }
            ApplyJob job = this.queue.peek();
            if (!job.step(world, left, clock).finished()) {
                break; // out of budget, or waiting for chunks
            }
            this.queue.poll();
        }
        return clock.getAsLong() - start;
    }

    public boolean isIdle() {
        return this.queue.isEmpty();
    }

    /** The queued jobs, the running one first. */
    public List<ApplyJob> jobs() {
        return List.copyOf(this.queue);
    }

    /** Stops every queued job and releases its chunks (the server is stopping). */
    public void abortAll(WorldWriter world) {
        for (ApplyJob job : this.queue) {
            job.abort(world);
        }
        this.queue.clear();
    }
}
