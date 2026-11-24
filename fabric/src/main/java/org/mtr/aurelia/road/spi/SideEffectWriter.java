/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * Carries out an operation's side effects (task G-9) in the game: MTR track (T-2; in ATR the {@code TrackWriter}). The
 * answers are futures because MTR answers a few ticks later (spike S14). Called on the server thread; the futures may
 * complete on any thread.
 */
public interface SideEffectWriter {
    /** What happened to one side effect. */
    enum Outcome {
        /** Made, or removed. */
        DONE,
        /** Making: something is already there that isn't ours (a player's rail). It is left alone and never owned. */
        ALREADY_THERE,
        /** Removing: it is gone already, or what is there now is a player's (T-2: a rail with a style), which is left. */
        NOT_THERE,
        /** Making: it can't exist here and isn't needed (T-2: a lane whose ends aren't MTR node blocks). Never owned. */
        SKIPPED,
        /** Nothing in this game can carry it out (no MTR). */
        UNAVAILABLE,
        /** It was tried and went wrong ({@link Result#reason}). */
        FAILED
    }

    /** One side effect's outcome, with a reason for anything but {@link Outcome#DONE}. */
    record Result(Outcome outcome, String reason) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(reason, "reason");
        }

        public static final Result DONE = new Result(Outcome.DONE, "");
    }

    /** Makes {@code effects} in {@code dimension} (such as {@code minecraft:overworld}); one result per effect, in order. */
    CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects);

    /** Removes {@code effects} from {@code dimension}; one result per effect, in order. */
    CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects);

    /** Whether this writer can carry out track at all (T-2: the server's {@code track} capability). */
    default boolean available() {
        return true;
    }

    /** A writer for a game that can't carry out any side effect (without MTR; tests). */
    SideEffectWriter UNAVAILABLE = new SideEffectWriter() {
        @Override
        public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
            return CompletableFuture.completedFuture(unavailable(effects));
        }

        @Override
        public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
            return CompletableFuture.completedFuture(unavailable(effects));
        }

        @Override
        public boolean available() {
            return false;
        }

        private List<Result> unavailable(List<SideEffect> effects) {
            List<Result> results = new ArrayList<>();
            effects.forEach(effect -> results.add(new Result(Outcome.UNAVAILABLE, "nothing in this game can carry it out")));
            return results;
        }
    };
}
