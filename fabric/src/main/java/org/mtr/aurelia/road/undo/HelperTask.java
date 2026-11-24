/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A task for the helper thread ({@link org.mtr.aurelia.road.spi.Helper}) that the server thread can call off
 * or take over when the server stops (F-2b): whoever starts it first does it, and the other gets its result.
 */
public final class HelperTask<T> implements Runnable {
    private final Callable<T> body;
    private final AtomicBoolean started = new AtomicBoolean();
    private final CompletableFuture<T> result = new CompletableFuture<>();

    public HelperTask(Callable<T> body) {
        this.body = body;
    }

    @Override
    public void run() {
        if (!this.started.compareAndSet(false, true)) {
            return;
        }
        try {
            this.result.complete(this.body.call());
        } catch (Throwable t) {
            this.result.completeExceptionally(t);
            if (t instanceof Error error) {
                throw error;
            }
        }
    }

    /** Makes sure it never runs; false if it has started already. */
    public boolean cancel() {
        return this.started.compareAndSet(false, true);
    }

    /** Does it on this thread if nothing has started it; otherwise waits up to {@code seconds} for it to end. */
    public void finishHere(long seconds) {
        run();
        try {
            this.result.get(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            // the outcome (a failure, or none yet) is read where it is used
        }
    }

    public boolean isDone() {
        return this.result.isDone();
    }

    /** Its outcome, once done. */
    public CompletableFuture<T> result() {
        return this.result;
    }
}
