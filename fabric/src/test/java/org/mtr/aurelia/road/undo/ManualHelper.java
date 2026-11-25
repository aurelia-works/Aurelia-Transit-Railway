/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.util.ArrayDeque;
import java.util.Deque;
import org.mtr.aurelia.road.spi.Helper;

/**
 * A helper thread for tests (F-2b): it runs its tasks only when the test says, work before disk, as the real one does.
 * From F-2+ also the journal's lane, which the real helper runs on a thread of its own.
 */
public final class ManualHelper implements Helper {
    public final Deque<Runnable> work = new ArrayDeque<>();
    public final Deque<Runnable> disk = new ArrayDeque<>();
    public final Deque<Runnable> journal = new ArrayDeque<>();

    @Override
    public void work(Runnable task) {
        this.work.add(task);
    }

    @Override
    public void disk(Runnable task) {
        this.disk.add(task);
    }

    @Override
    public void journal(Runnable task) {
        this.journal.add(task);
    }

    /** Runs every task queued, the journal's first, then work, then disk, until none is left; how many there were. */
    public int runAll() {
        int count = 0;
        while (!this.journal.isEmpty() || !this.work.isEmpty() || !this.disk.isEmpty()) {
            (!this.journal.isEmpty() ? this.journal : this.work.isEmpty() ? this.disk : this.work).poll().run();
            count++;
        }
        return count;
    }

    /** Runs the work queued (no disk tasks), and the journal's; how many work tasks there were. */
    public int runWork() {
        int count = 0;
        runJournal();
        while (!this.work.isEmpty()) {
            this.work.poll().run();
            count++;
            runJournal();
        }
        return count;
    }

    /** Runs the disk tasks queued, and the journal's; how many disk tasks there were. */
    public int runDisk() {
        int count = 0;
        runJournal();
        while (!this.disk.isEmpty()) {
            this.disk.poll().run();
            count++;
        }
        return count;
    }

    /** Runs the journal's tasks queued (F-2+); how many there were. */
    public int runJournal() {
        int count = 0;
        while (!this.journal.isEmpty()) {
            this.journal.poll().run();
            count++;
        }
        return count;
    }
}
