/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * Repairs a world after a crash (world-data-format §5; task F-2+), so that Stratos's blocks and track match its saved
 * undo history (owner, 2026-10-09). Every run the journal still keeps is either:
 * <ul>
 *   <li><b>rolled back</b>, if the saved undo index doesn't list it (cut off halfway, or finished after the last save),
 *       newest first: each block it wrote goes back to its state and data before, <b>where it still has the run's
 *       state after</b>. Where the run's {@code END} is missing (cut off), every block of its pre-images is tried, as
 *       its last {@code SECTION_DONE} may not have reached the disk;</li>
 *   <li><b>completed</b>, if the index lists it, oldest first: each block it wrote goes to its state and data after,
 *       where it still has its state before (Minecraft hadn't written all its chunks).</li>
 * </ul>
 * Every write is guarded, so repairing twice changes nothing more: a crash during a repair is repaired again. A block
 * in any other state (changed since) is left alone. The repair's jobs are ordinary {@link ApplyJob#repair repair jobs}
 * in each dimension's apply engine, with no journal and no undo history of their own.
 *
 * <p><b>Track</b> (owner, 2026-10-09: kept in step) comes after a run's blocks, through the side-effect writer, which
 * asks MTR first and never touches a player's rail: a rolled-back run's rails it made are removed and those it removed
 * are made again; a completed run's are made or removed again where MTR lost them. A request with no answer in the
 * journal is unclear: left alone, and logged.
 *
 * <p><b>Never a guess:</b> a run whose records can't all be read, or whose dimension isn't there, has what can be
 * repaired repaired and the rest reported ({@code NEEDS_ATTENTION}).
 *
 * <p>Server thread only.
 */
public final class Recovery {
    /** Which way a run is repaired. */
    public enum Direction {
        ROLL_BACK,
        COMPLETE
    }

    /** One run's repair: its blocks, its track, and how it went. */
    public static final class Step {
        private final RunLog run;
        private final Direction direction;
        private final BlockPlan plan; // null: no block to write
        private final BlockPlan expected;
        private final List<SideEffect> remove;
        private final List<SideEffect> make;
        private final List<String> notes = new ArrayList<>();
        private ApplyJob job;
        private CompletableFuture<List<SideEffectWriter.Result>> removing;
        private CompletableFuture<List<SideEffectWriter.Result>> making;
        private boolean trackStarted;
        private boolean done;
        private int trackChanged;
        private boolean attention;

        private Step(RunLog run, Direction direction, BlockPlan plan, BlockPlan expected, List<SideEffect> remove, List<SideEffect> make) {
            this.run = run;
            this.direction = direction;
            this.plan = plan;
            this.expected = expected;
            this.remove = List.copyOf(remove);
            this.make = List.copyOf(make);
        }

        public RunLog run() {
            return this.run;
        }

        public Direction direction() {
            return this.direction;
        }

        /** Its dimension; empty if its {@code BEGIN} is lost. */
        public Optional<String> dimension() {
            return this.run.begin().map(JournalRecord.Begin::dimension);
        }

        /** The blocks it would write (its expected states are the run's other side); empty if none. */
        public Optional<BlockPlan> plan() {
            return Optional.ofNullable(this.plan);
        }

        /** Its repair job, once started. */
        public Optional<ApplyJob> job() {
            return Optional.ofNullable(this.job);
        }

        public boolean done() {
            return this.done;
        }

        /** Whether the repair changed anything: a block, or a rail. */
        public boolean changedSomething() {
            return (this.job != null && this.job.changedBlocks() > 0) || this.trackChanged > 0;
        }

        /** Whether it couldn't be fully repaired. */
        public boolean needsAttention() {
            return this.attention;
        }

        /** What happened, for the log and the journal's {@code RECOVERED}. */
        public String detail() {
            List<String> parts = new ArrayList<>();
            if (this.job != null) {
                parts.add(this.job.changedBlocks() + " of " + this.plan.blockCount() + " blocks " + (this.direction == Direction.ROLL_BACK ? "put back" : "written")
                        + ", " + this.job.skippedBlocks() + " left as they were");
            } else {
                parts.add("no block to write");
            }
            if (!this.remove.isEmpty() || !this.make.isEmpty()) {
                parts.add("track: " + this.trackChanged + " of " + (this.remove.size() + this.make.size()) + " rails changed");
            }
            parts.addAll(this.notes);
            return String.join("; ", parts);
        }

        /** The journal's outcome for it. */
        public JournalRecord.Outcome outcome() {
            if (this.attention) {
                return JournalRecord.Outcome.NEEDS_ATTENTION;
            }
            return this.direction == Direction.ROLL_BACK ? JournalRecord.Outcome.ROLLED_BACK : JournalRecord.Outcome.COMPLETED;
        }

        private void attention(String why) {
            this.attention = true;
            this.notes.add(why);
        }
    }

    /** What a finished repair did, for the players' notice: runs rolled back and completed that changed something, and those needing attention. */
    public record Report(int rolledBack, int completed, int needAttention, int unchanged) {
        public boolean anything() {
            return this.rolledBack + this.completed + this.needAttention > 0;
        }
    }

    private final List<Step> steps;
    private final List<String> problems;
    private boolean started;

    private Recovery(List<Step> steps, List<String> problems) {
        this.steps = List.copyOf(steps);
        this.problems = List.copyOf(problems);
    }

    /**
     * The repair of every run {@code scan} still keeps, given the runs the saved undo index {@code listed}: roll-backs
     * newest first, then completions oldest first. Empty if there is nothing to repair.
     */
    public static Optional<Recovery> plan(JournalScan scan, Set<UUID> listed) {
        List<RunLog> unretired = scan.unretired();
        if (unretired.isEmpty() && scan.problems().isEmpty()) {
            return Optional.empty();
        }
        List<Step> rollBacks = new ArrayList<>();
        List<Step> completions = new ArrayList<>();
        for (RunLog run : unretired) {
            if (listed.contains(run.run())) {
                completions.add(step(run, Direction.COMPLETE));
            } else {
                rollBacks.add(step(run, Direction.ROLL_BACK));
            }
        }
        Collections.reverse(rollBacks);
        List<Step> steps = new ArrayList<>(rollBacks);
        steps.addAll(completions);
        return Optional.of(new Recovery(steps, scan.problems()));
    }

    private static Step step(RunLog run, Direction direction) {
        boolean ended = run.end().isPresent();
        Map<BlockPlan.SectionPos, long[]> written = run.written();
        BlockPlan plan = null;
        BlockPlan expected = null;
        boolean wroteNothing = run.end().map(end -> end.blocksWritten() == 0).orElse(false);
        if (!wroteNothing && run.dataVersion() >= 0) {
            BlockPlan.Builder planBuilder = BlockPlan.builder(run.dataVersion());
            BlockPlan.Builder expectedBuilder = BlockPlan.builder(run.dataVersion());
            int count = 0;
            for (Map.Entry<BlockPlan.SectionPos, List<BlockChange>> section : run.preimages().entrySet()) {
                long[] bits = written.get(section.getKey());
                // Completing needs to know which blocks were written; rolling back does too once the run ended.
                boolean trustBits = direction == Direction.COMPLETE || ended;
                if (trustBits && bits == null) {
                    continue; // nothing written in this section
                }
                for (BlockChange change : section.getValue()) {
                    if (trustBits && !RunLog.bit(bits, RunLog.local(change.x(), change.y(), change.z()))) {
                        continue;
                    }
                    if (direction == Direction.ROLL_BACK) {
                        planBuilder.set(change.x(), change.y(), change.z(), change.before(), change.beforeData());
                        expectedBuilder.set(change.x(), change.y(), change.z(), change.after(), change.afterData());
                    } else {
                        planBuilder.set(change.x(), change.y(), change.z(), change.after(), change.afterData());
                        expectedBuilder.set(change.x(), change.y(), change.z(), change.before(), change.beforeData());
                    }
                    count++;
                }
            }
            if (count > 0) {
                plan = planBuilder.build();
                expected = expectedBuilder.build();
            }
        }
        // Track: pair each request with its answer, in order.
        List<SideEffect> remove = new ArrayList<>();
        List<SideEffect> make = new ArrayList<>();
        List<String> unclear = new ArrayList<>();
        List<JournalRecord.Track> asked = run.asked();
        List<JournalRecord.TrackDone> answered = run.answered();
        for (int i = 0; i < asked.size(); i++) {
            if (i >= answered.size()) {
                unclear.add(asked.get(i).effects().size() + " rails asked to " + asked.get(i).action().name().toLowerCase(java.util.Locale.ROOT)
                        + " with no answer recorded, left as they are");
                continue;
            }
            JournalRecord.TrackDone done = answered.get(i);
            for (int j = 0; j < done.effects().size(); j++) {
                if (done.outcomes().get(j) != SideEffectWriter.Outcome.DONE) {
                    continue; // not made (a player's was there), or not removed: nothing of ours to undo or redo
                }
                SideEffect effect = done.effects().get(j);
                boolean made = done.action() == JournalRecord.TrackAction.MAKE;
                // Rolling back undoes what the run did to the track; completing does it again.
                if (made == (direction == Direction.ROLL_BACK)) {
                    remove.add(effect);
                } else {
                    make.add(effect);
                }
            }
        }
        Step step = new Step(run, direction, plan, expected, remove, make);
        run.problems().forEach(step::attention);
        if (direction == Direction.COMPLETE && !ended) {
            step.attention("the saved history lists it, but its END record is missing");
        }
        unclear.forEach(step.notes::add);
        return step;
    }

    /** Every run's repair, in the order they run. */
    public List<Step> steps() {
        return this.steps;
    }

    /** The dimensions the repair writes in. */
    public Set<String> dimensions() {
        Set<String> dimensions = new java.util.LinkedHashSet<>();
        for (Step step : this.steps) {
            step.dimension().ifPresent(dimensions::add);
        }
        return dimensions;
    }

    /** What is wrong with the journal itself (damaged segments), for the log. */
    public List<String> problems() {
        return this.problems;
    }

    /**
     * Starts every run's repair job in its dimension's apply engine, in order ({@code schedulers}: empty for a dimension
     * that isn't there). Once.
     */
    public void start(Function<String, Optional<ApplyScheduler>> schedulers) {
        if (this.started) {
            throw new IllegalStateException("A repair starts once");
        }
        this.started = true;
        for (Step step : this.steps) {
            Optional<String> dimension = step.dimension();
            if (dimension.isEmpty()) {
                continue; // nothing known to write (its problems say why)
            }
            if (step.plan == null) {
                continue;
            }
            Optional<ApplyScheduler> scheduler = schedulers.apply(dimension.get());
            if (scheduler.isEmpty()) {
                step.attention("its dimension " + dimension.get() + " isn't in this world");
                continue;
            }
            step.job = scheduler.get().submit(ApplyJob.repair(step.plan, step.expected));
        }
    }

    /** Whether {@link #start} has run. */
    public boolean started() {
        return this.started;
    }

    /**
     * Moves the repair on (after each tick): a run whose blocks are done gets its track made or removed through
     * {@code writer}; once that has settled, the run is done. Returns the steps finished now.
     */
    public List<Step> afterTick(SideEffectWriter writer) {
        Objects.requireNonNull(writer, "writer");
        List<Step> finished = new ArrayList<>();
        if (!this.started) {
            return finished;
        }
        for (Step step : this.steps) {
            if (step.done) {
                continue;
            }
            if (step.job != null && !step.job.status().finished()) {
                continue;
            }
            if (!step.trackStarted) {
                step.trackStarted = true;
                if (step.job != null && step.job.status() == ApplyJob.Status.FAILED) {
                    step.attention("its repair job ended " + step.job.failureCode().map(Enum::name).orElse("FAILED") + ": "
                            + step.job.failure().orElse(""));
                }
                String dimension = step.dimension().orElse(null);
                if (dimension != null && !step.remove.isEmpty()) {
                    step.removing = ask(() -> writer.remove(dimension, step.remove));
                }
                if (dimension != null && !step.make.isEmpty()) {
                    step.making = ask(() -> writer.make(dimension, step.make));
                }
            }
            if (step.removing != null && !step.removing.isDone() || step.making != null && !step.making.isDone()) {
                continue;
            }
            settleTrack(step, step.removing, step.remove, true);
            settleTrack(step, step.making, step.make, false);
            step.done = true;
            finished.add(step);
        }
        return finished;
    }

    private static CompletableFuture<List<SideEffectWriter.Result>> ask(java.util.function.Supplier<CompletableFuture<List<SideEffectWriter.Result>>> call) {
        try {
            return Objects.requireNonNull(call.get(), "the side-effect writer's answer");
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Counts the rails changed; any that went wrong (not "already as wanted") needs attention. */
    private static void settleTrack(Step step, CompletableFuture<List<SideEffectWriter.Result>> future, List<SideEffect> effects, boolean removing) {
        if (future == null) {
            return;
        }
        List<SideEffectWriter.Result> results;
        try {
            results = future.join();
        } catch (RuntimeException e) {
            step.attention("the track writer failed: " + (e.getCause() != null ? e.getCause() : e));
            return;
        }
        int wrong = 0;
        for (int i = 0; i < effects.size(); i++) {
            SideEffectWriter.Result result = results != null && i < results.size() ? results.get(i) : null;
            SideEffectWriter.Outcome outcome = result == null ? SideEffectWriter.Outcome.FAILED : result.outcome();
            if (outcome == SideEffectWriter.Outcome.DONE) {
                step.trackChanged++;
            } else if (removing ? outcome != SideEffectWriter.Outcome.NOT_THERE
                    : outcome != SideEffectWriter.Outcome.ALREADY_THERE && outcome != SideEffectWriter.Outcome.SKIPPED) {
                wrong++;
            }
        }
        if (wrong > 0) {
            step.attention(wrong + " rails couldn't be " + (removing ? "removed" : "made"));
        }
    }

    /** Whether every run's repair is done. */
    public boolean finished() {
        return this.started && this.steps.stream().allMatch(Step::done);
    }

    /**
     * The counts for the players' notice, once {@link #finished}. A run a repair dealt with before (a crash during or
     * soon after the last one) is repaired again quietly: players were told then.
     */
    public Report report() {
        int rolledBack = 0;
        int completed = 0;
        int attention = 0;
        int unchanged = 0;
        for (Step step : this.steps) {
            if (step.run.recovered().isPresent() && !step.attention) {
                unchanged++;
            } else if (step.attention) {
                attention++;
            } else if (!step.changedSomething()) {
                unchanged++;
            } else if (step.direction == Direction.ROLL_BACK) {
                rolledBack++;
            } else {
                completed++;
            }
        }
        if (!this.problems.isEmpty() && attention == 0) {
            attention = 1; // the journal itself is damaged
        }
        return new Report(rolledBack, completed, attention, unchanged);
    }

    /** The operations whose apply runs were rolled back here: their records, if saved, now say {@code ROLLED_BACK}. */
    public List<UUID> rolledBackApplies() {
        List<UUID> ids = new ArrayList<>();
        for (Step step : this.steps) {
            Optional<JournalRecord.Begin> begin = step.run.begin();
            if (step.direction == Direction.ROLL_BACK && begin.isPresent() && begin.get().kind() == Operations.Kind.APPLY) {
                ids.add(begin.get().operation());
            }
        }
        return ids;
    }
}
