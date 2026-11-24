/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.apply;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.mtr.aurelia.road.spi.Helper;
import org.mtr.aurelia.road.fabric.StratosMod;

/**
 * The "Stratos helper" (F-2b): one daemon thread per server for the work that grows with an edit. A task a player is
 * waiting for ({@link #work}) goes before any disk task not yet started ({@link #disk}); each kind runs in the order
 * given. Disk work comes a file at a time, so waiting work is held up by one file at most.
 *
 * <p><b>From F-2+</b> a second daemon thread, "Stratos journal", appends and flushes the journal ({@link #journal}), in
 * order, so placing (which waits for each section's pre-image) never waits behind the helper's work.
 */
final class HelperThread implements Helper {
    private static final int WORK = 0;
    private static final int DISK = 1;

    /** A task with its kind and place in line: work first, then disk, each first come, first served. */
    private record Ranked(int kind, long place, Runnable task) implements Runnable, Comparable<Ranked> {
        @Override
        public void run() {
            this.task.run();
        }

        @Override
        public int compareTo(Ranked other) {
            return this.kind != other.kind ? Integer.compare(this.kind, other.kind) : Long.compare(this.place, other.place);
        }
    }

    private final AtomicLong places = new AtomicLong();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), task -> {
        Thread thread = new Thread(task, "Stratos helper");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> StratosMod.LOGGER.error("[stratos] the helper thread failed", e));
        return thread;
    });
    private final ThreadPoolExecutor journal = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
        Thread thread = new Thread(task, "Stratos journal");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> StratosMod.LOGGER.error("[stratos] the journal thread failed", e));
        return thread;
    });

    @Override
    public void work(Runnable task) {
        this.executor.execute(new Ranked(WORK, this.places.incrementAndGet(), task));
    }

    @Override
    public void disk(Runnable task) {
        this.executor.execute(new Ranked(DISK, this.places.incrementAndGet(), task));
    }

    @Override
    public void journal(Runnable task) {
        this.journal.execute(task);
    }

    /** Stops the threads, dropping anything still queued (the server's stop has done it already). */
    void shutdownNow() {
        this.executor.shutdownNow();
        this.journal.shutdownNow();
    }
}
