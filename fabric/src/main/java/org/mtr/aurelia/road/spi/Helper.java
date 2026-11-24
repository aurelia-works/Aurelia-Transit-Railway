/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

/**
 * Where Stratos's background work runs (task F-2b): the platform's one "Stratos helper" thread per server. It takes two
 * kinds of work, each in the order given:
 * <ul>
 *   <li>{@link #work}: what a player is waiting for (building a job, unpacking and compressing undo images, hashing
 *       sections). It always goes before disk work that hasn't started.</li>
 *   <li>{@link #disk}: writing files once the player has their answer (undo images, saves). Callers hand it over in
 *       small pieces (a file at a time), so work never waits long behind it.</li>
 * </ul>
 * <b>From F-2+</b> a third lane, {@link #journal}, runs on a thread of its own ("Stratos journal"): appending the
 * journal's records and flushing them, which placing waits for, so it never queues behind the helper's work.
 * Each may throw {@link java.util.concurrent.RejectedExecutionException} once the helper has stopped; callers then do
 * the task themselves.
 */
public interface Helper {
    /** Runs {@code task}, which a player is waiting for, before any disk work not yet started. */
    void work(Runnable task);

    /** Runs {@code task}, which writes to disk, after all work and disk tasks given before it. */
    void disk(Runnable task);

    /** Runs {@code task} on the journal's own thread, after the journal tasks given before it (F-2+). */
    void journal(Runnable task);

    /** Does every task at once, on the calling thread: for tests, and wherever there is no helper. */
    Helper AT_ONCE = new Helper() {
        @Override
        public void work(Runnable task) {
            task.run();
        }

        @Override
        public void disk(Runnable task) {
            task.run();
        }

        @Override
        public void journal(Runnable task) {
            task.run();
        }
    };
}
