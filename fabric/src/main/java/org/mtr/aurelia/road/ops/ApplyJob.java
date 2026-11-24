/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import org.mtr.aurelia.road.spi.ChunkXZ;
import org.mtr.aurelia.road.spi.WorldWriter;

/**
 * Places one block plan in a world, a slice at a time (spec mod-suite-v1 §6.5): each {@link #step} does as much as
 * fits in its time budget and the next one carries on where it stopped. It moves through:
 * <ol>
 *   <li><b>loading</b>: checks the DataVersion and every state string, then retains the plan's chunks and waits until
 *       they are loaded;</li>
 *   <li><b>checking</b>: refuses the plan, with nothing written, if any planned position holds a block entity the world
 *       won't let Stratos replace (F-1+: another mod's), or, with {@link Guard#REFUSE_CHANGED}, if any block isn't in its
 *       expected state;</li>
 *   <li><b>applying</b>: sets each block in canonical order, skipping blocks that already have their new state (and,
 *       with {@link Guard#SKIP_CHANGED}, blocks that aren't in their expected state), and records each changed block's
 *       before and after state, and its block entity's data before and after (F-1+), around writing it;</li>
 *   <li><b>late</b> (F-2+, with a journal): writes the blocks that changed between checking and applying, once their
 *       new pre-images are on disk;</li>
 *   <li><b>the outer shell</b> (F-1+): tells each block next to a written block, unless the job wrote it too, that its
 *       neighbour changed, so redstone and machines beside the edit react ({@link WorldWriter#neighbourChanged});</li>
 *   <li><b>done</b> or <b>failed</b>, releasing the chunks either way.</li>
 * </ol>
 * <b>Block-entity data</b> (F-1+) counts with the state: a block whose expected data (an undo's or redo's) differs is
 * not in its expected state, and a plan block with data is written when only its data differs.
 * The clock is checked before every block, and no block is started once the budget is used up. {@link #undoPlan()}
 * is the plan that puts every changed block back.
 *
 * <p><b>The journal (F-2+;</b> world-data-format §5), when the job has one ({@link #journal(JobJournal)}): checking
 * notes every block the job may change (its state and data as found, and as planned) and hands each section's list to
 * the journal as a pre-image. Applying writes a section only once its pre-image is on disk, and waits for the next tick
 * otherwise. A block found different from its pre-image when it is written (a mob, water or a player changed it since)
 * isn't written then: it is noted again as found, and written in the late phase once that is on disk; if it changed yet
 * again, it is left as it is ({@link #changedWhilePlacing}). After each section, the journal hears which blocks were
 * written. If the journal can't be written, the job fails {@link Failure#JOURNAL_FAILED} where it is.
 *
 * <p><b>A repair job</b> ({@link #repair}, F-2+) puts a crashed run's blocks back: it writes a block only where it is in
 * its expected state, leaves any other block whatever it is (another mod's block entity included), and refuses nothing.
 *
 * <p>A job may be built on any thread: building reads only the plan (F-2b: the helper builds them, as a million-block
 * job takes 50–110 ms to build). It runs on the server thread only.
 */
public final class ApplyJob {
    /** Where the job is. */
    public enum Status {
        LOADING,
        CHECKING,
        APPLYING,
        /** The blocks that changed between checking and applying are being written (F-2+). */
        LATE,
        /** The blocks are written; the blocks just outside are being told (F-1+). */
        SHELL,
        DONE,
        FAILED;

        public boolean finished() {
            return this == DONE || this == FAILED;
        }
    }

    /** What the job checks against the expected states (undo and redo, spec mod-suite-v1 §6.6). */
    public enum Guard {
        /** Write every block. */
        NONE,
        /** Write a block only if it is still in its expected state; leave and list it otherwise (undo keeps hand edits). */
        SKIP_CHANGED,
        /** Write nothing unless every block is in its expected state (redo's REDO_CONFLICT). */
        REFUSE_CHANGED,
        /**
         * Write nothing unless every block is in its expected state, as {@link #REFUSE_CHANGED}, but check every block
         * first and count those that aren't ({@link #manualEdits}; R-7: a delete or rebuild asks about hand edits). A block
         * already in its new state isn't counted: nothing would be written there anyway.
         */
        ASK
    }

    /**
     * An expected state any block meets (R-7): a rebuild's blocks that the road never placed have none, and are written
     * like a new road's.
     */
    public static final String ANY = "*";

    /** Why a job failed. The protocol's reason codes can map onto these (F-3). */
    public enum Failure {
        DATA_VERSION,
        INVALID_STATE,
        BLOCK_ENTITY,
        BLOCK_ENTITY_APPEARED,
        CONFLICT,
        /** {@link Guard#ASK}: blocks aren't in their expected state ({@link #manualEdits}); nothing was written (R-7). */
        MANUAL_EDITS,
        /** A job made with {@link #refusingNothing}: no block would change; nothing was written (R-7). */
        NOTHING_TO_CHANGE,
        STOPPED,
        /** The journal couldn't be written (F-2+): nothing more is written. */
        JOURNAL_FAILED
    }

    /** One call to {@link #step}: blocks it set, and how long it took. */
    public record Slice(int blocks, long nanos) {
    }

    /** A block found different from its pre-image when it was to be written (F-2+), as found then. */
    private record Late(int index, String found, String foundData) {
    }

    private final BlockPlan plan;
    private final Guard guard;
    private final boolean repair;
    private final List<BlockPlan.Block> blocks;
    private final List<BlockPlan.Block> expected; // same positions and order as blocks, or null without a guard
    private final List<String> states; // the distinct states, in first-use order
    private final List<ChunkXZ> chunks; // sorted by x, then z
    private final int[] sectionEnds; // per section in canonical order, the index just past its last block
    private final BlockPlan.SectionPos[] sectionPositions; // per section in canonical order
    private final List<BlockChange> changes = new ArrayList<>();
    private final List<BlockChange> skipped = new ArrayList<>();
    private final List<Slice> slices = new ArrayList<>();
    private Status status = Status.LOADING;
    private int stateIndex;
    private int retainIndex;
    private int loadedIndex;
    private boolean released;
    private int checkIndex;
    private int applyIndex;
    private long latestStart; // the most budget used when any block was started, over all slices
    private Failure failureCode;
    private String failure;
    // The outer shell (F-1+): which blocks of each section the job wrote, a bit per block (y << 8 | z << 4 | x).
    private final long[][] written;
    private int writeSection; // the section of the block at applyIndex
    private Map<Long, Integer> sectionIndex; // section key to index, made when the shell starts
    private int shellSection;
    private int shellWord;
    private long shellBits;
    private boolean shellStarted;
    private Failure pendingCode; // a failure that waits for the shell of what was written
    private String pendingReason;
    // The journal (F-2+): null without one.
    private JobJournal journal;
    private String[] found; // per block, its state as checking found it where the job may change it; null: not noted
    private Map<Integer, String> foundData; // per block, its block-entity data as found, where it had some
    private long[] sectionTickets; // per section, the ticket of its last pre-image part; 0 for none
    private final List<BlockChange> captured = new ArrayList<>(); // the pre-image part being collected
    private int checkSection; // the section of the block at checkIndex
    private int doneSection; // sections before this one have been reported done to the journal
    private final List<Late> late = new ArrayList<>();
    private final Map<Integer, List<BlockChange>> lateCaptured = new TreeMap<>(); // per section, late pre-images not yet queued
    private long lateTicket;
    private int lateIndex;
    private final Set<Integer> lateSections = new TreeSet<>();
    private int changedWhilePlacing;
    private int journalWaits;
    // R-7: refuse a job that would change nothing; how many blocks checking found to write, and not as expected (ASK).
    private boolean refuseNothing;
    private int toWrite;
    private int manualEdits;

    public ApplyJob(BlockPlan plan) {
        this(plan, null, Guard.NONE);
    }

    /**
     * A job whose blocks are checked against {@code expected}, a plan with exactly the same positions (the states each
     * block should have before it is written), according to {@code guard}.
     */
    public ApplyJob(BlockPlan plan, BlockPlan expected, Guard guard) {
        this(plan, expected, guard, false);
    }

    private ApplyJob(BlockPlan plan, BlockPlan expected, Guard guard, boolean repair) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.repair = repair;
        this.blocks = plan.blocks();
        if (guard == Guard.NONE) {
            this.expected = null;
        } else {
            this.expected = Objects.requireNonNull(expected, "expected").blocks();
            if (this.expected.size() != this.blocks.size()) {
                throw new IllegalArgumentException("The expected plan has " + this.expected.size() + " blocks, the plan " + this.blocks.size());
            }
            for (int i = 0; i < this.blocks.size(); i++) {
                BlockPlan.Block a = this.blocks.get(i);
                BlockPlan.Block b = this.expected.get(i);
                if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z()) {
                    throw new IllegalArgumentException("The expected plan's positions differ from the plan's at block " + i);
                }
            }
        }
        Set<String> states = new LinkedHashSet<>();
        for (BlockPlan.Block block : this.blocks) {
            states.add(block.state());
        }
        // A section's chunk is its x and z (F-2b: one per section, not one object per block).
        Set<ChunkXZ> chunks = new TreeSet<>((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        for (BlockPlan.SectionPos section : plan.sections().keySet()) {
            chunks.add(new ChunkXZ(section.x(), section.z()));
        }
        this.states = List.copyOf(states);
        this.chunks = List.copyOf(chunks);
        // blocks() lists the sections' blocks one section after another, in canonical order.
        this.sectionEnds = new int[plan.sections().size()];
        int end = 0;
        int i = 0;
        for (List<BlockPlan.Block> section : plan.sections().values()) {
            end += section.size();
            this.sectionEnds[i++] = end;
        }
        this.sectionPositions = plan.sections().keySet().toArray(new BlockPlan.SectionPos[0]);
        this.written = new long[this.sectionEnds.length][];
    }

    /**
     * A repair job (F-2+): writes each block of {@code plan} only where the world has its state (and data, where given)
     * in {@code expected}, and leaves every other block as it is, whatever it is. It refuses nothing.
     */
    public static ApplyJob repair(BlockPlan plan, BlockPlan expected) {
        return new ApplyJob(plan, expected, Guard.SKIP_CHANGED, true);
    }

    /**
     * A job as {@link #ApplyJob(BlockPlan, BlockPlan, Guard)} that fails {@link Failure#NOTHING_TO_CHANGE}, with nothing
     * written, when checking finds no block it would write (R-7: a delete or rebuild with nothing to do). Checking then
     * reads every block, and a block the guard would leave doesn't stop the job for another mod's block entity.
     */
    public static ApplyJob refusingNothing(BlockPlan plan, BlockPlan expected, Guard guard) {
        ApplyJob job = new ApplyJob(plan, expected, guard, false);
        job.refuseNothing = true;
        return job;
    }

    /**
     * Gives the job its journal (F-2+), before its first step: it will hand over pre-images while checking and write a
     * section only once its pre-image is on disk.
     */
    public void journal(JobJournal journal) {
        if (this.status != Status.LOADING || this.stateIndex > 0 || this.retainIndex > 0) {
            throw new IllegalStateException("A job gets its journal before it starts");
        }
        if (this.repair) {
            throw new IllegalStateException("A repair job has no journal of its own");
        }
        this.journal = Objects.requireNonNull(journal, "journal");
        this.found = new String[this.blocks.size()];
        this.foundData = new HashMap<>();
        this.sectionTickets = new long[this.sectionEnds.length];
    }

    /** Whether the job is a repair job ({@link #repair}). */
    public boolean isRepair() {
        return this.repair;
    }

    /**
     * Does as much as fits in {@code budgetNanos} of {@code clock} time, on the server thread. Returns the status after
     * this slice; call again next tick until it is finished.
     */
    public Status step(WorldWriter world, long budgetNanos, LongSupplier clock) {
        long start = clock.getAsLong();
        int changed = 0;
        try {
            while (!this.status.finished()) {
                switch (this.status) {
                    case LOADING -> {
                        // Budgeted like everything else: a plan can name many states and cover thousands of chunks.
                        if (world.dataVersion() != this.plan.dataVersion()) {
                            fail(world, Failure.DATA_VERSION, "the plan is for DataVersion " + this.plan.dataVersion() + " but the world is "
                                    + world.dataVersion() + "; nothing was changed");
                            return this.status;
                        }
                        while (this.stateIndex < this.states.size()) {
                            if (clock.getAsLong() - start >= budgetNanos) {
                                return this.status;
                            }
                            String state = this.states.get(this.stateIndex);
                            if (!world.isValidState(state)) {
                                fail(world, Failure.INVALID_STATE, "'" + state + "' is not a block state in this game; nothing was changed");
                                return this.status;
                            }
                            this.stateIndex++;
                        }
                        while (this.retainIndex < this.chunks.size()) {
                            if (clock.getAsLong() - start >= budgetNanos) {
                                return this.status;
                            }
                            world.retainChunks(List.of(this.chunks.get(this.retainIndex)));
                            this.retainIndex++;
                        }
                        while (this.loadedIndex < this.chunks.size()) {
                            if (clock.getAsLong() - start >= budgetNanos || !world.isLoaded(this.chunks.get(this.loadedIndex))) {
                                return this.status; // out of budget, or waiting for the chunk
                            }
                            this.loadedIndex++;
                        }
                        this.status = Status.CHECKING;
                    }
                    case CHECKING -> {
                        while (this.checkIndex < this.blocks.size()) {
                            if (clock.getAsLong() - start >= budgetNanos) {
                                queueCaptured(); // a section's part so far: the journal writes while the next slice checks on
                                return this.status;
                            }
                            while (this.checkIndex >= this.sectionEnds[this.checkSection]) {
                                queueCaptured();
                                this.checkSection++;
                            }
                            BlockPlan.Block block = this.blocks.get(this.checkIndex);
                            String current = this.journal != null || this.guard == Guard.REFUSE_CHANGED || this.guard == Guard.ASK || this.refuseNothing
                                    ? world.blockState(block.x(), block.y(), block.z()) : null;
                            if (this.guard == Guard.REFUSE_CHANGED) {
                                BlockPlan.Block want = this.expected.get(this.checkIndex);
                                if (!asExpected(world, want, current)) {
                                    fail(world, Failure.CONFLICT, "(" + block.x() + ", " + block.y() + ", " + block.z() + ") is " + current
                                            + (current.equals(want.state()) ? " with other stored data" : ", not " + want.state()) + " as expected; nothing was changed");
                                    return this.status;
                                }
                            }
                            boolean left = false; // R-7: a block the guard leaves isn't written, so its block entity doesn't matter
                            if (this.guard == Guard.ASK || this.refuseNothing) {
                                boolean expectedHere = this.expected == null || asExpected(world, this.expected.get(this.checkIndex), current);
                                boolean differs = differs(world, block, current);
                                if (!expectedHere && differs && this.guard == Guard.ASK) {
                                    this.manualEdits++;
                                }
                                left = this.refuseNothing && !expectedHere && this.guard == Guard.SKIP_CHANGED;
                                if (differs && (expectedHere || this.guard == Guard.NONE)) {
                                    this.toWrite++;
                                }
                            }
                            if (!this.repair && !left && world.hasBlockEntity(block.x(), block.y(), block.z())
                                    && !world.canReplaceBlockEntity(block.x(), block.y(), block.z())) {
                                fail(world, Failure.BLOCK_ENTITY, "the plan would overwrite " + world.blockState(block.x(), block.y(), block.z()) + " at ("
                                        + block.x() + ", " + block.y() + ", " + block.z() + "), another mod's block with stored data that Stratos can't put back; "
                                        + "nothing was changed");
                                return this.status;
                            }
                            if (this.journal != null) {
                                capture(world, block, current);
                            }
                            this.checkIndex++;
                        }
                        queueCaptured();
                        if (this.guard == Guard.ASK && this.manualEdits > 0) {
                            fail(world, Failure.MANUAL_EDITS, this.manualEdits + " blocks aren't as expected; nothing was changed");
                            return this.status;
                        }
                        if (this.refuseNothing && this.toWrite == 0) {
                            fail(world, Failure.NOTHING_TO_CHANGE, "every block is already as it would be; nothing was changed");
                            return this.status;
                        }
                        this.status = Status.APPLYING;
                    }
                    case APPLYING -> {
                        while (this.applyIndex < this.blocks.size()) {
                            long used = clock.getAsLong() - start;
                            if (used >= budgetNanos) {
                                queueLate();
                                return this.status;
                            }
                            while (this.applyIndex >= this.sectionEnds[this.writeSection]) {
                                this.writeSection++;
                            }
                            if (this.journal != null) {
                                reportDone(this.writeSection);
                                long ticket = this.sectionTickets[this.writeSection];
                                if (ticket != 0 && !this.journal.onDisk(ticket)) {
                                    queueLate();
                                    if (this.journal.failed()) {
                                        failJournal(world);
                                    } else {
                                        this.journalWaits++;
                                    }
                                    return this.status; // its pre-image isn't on disk yet: next tick
                                }
                            }
                            BlockPlan.Block block = this.blocks.get(this.applyIndex);
                            String old = world.blockState(block.x(), block.y(), block.z());
                            BlockPlan.Block want = this.expected == null ? null : this.expected.get(this.applyIndex);
                            if (want != null && !asExpected(world, want, old)) {
                                if (this.guard == Guard.REFUSE_CHANGED) {
                                    fail(world, Failure.CONFLICT, "(" + block.x() + ", " + block.y() + ", " + block.z() + ") changed while the plan was being placed; "
                                            + this.changes.size() + " blocks were placed and can be undone");
                                    return this.status;
                                }
                                this.skipped.add(new BlockChange(block.x(), block.y(), block.z(), old, block.state()));
                            } else if (!old.equals(block.state()) || block.data() != null) {
                                boolean newState = !old.equals(block.state());
                                // A block entity's data is read only where there is one (F-1+).
                                String oldData = world.hasBlockEntity(block.x(), block.y(), block.z()) ? world.blockEntityData(block.x(), block.y(), block.z())
                                        : null;
                                if (newState || !block.data().equals(oldData)) {
                                    if (oldData != null && !world.canReplaceBlockEntity(block.x(), block.y(), block.z())) {
                                        if (this.repair) {
                                            this.skipped.add(new BlockChange(block.x(), block.y(), block.z(), old, block.state()));
                                            this.applyIndex++;
                                            continue;
                                        }
                                        fail(world, Failure.BLOCK_ENTITY_APPEARED, old + " (another mod's block with stored data) appeared at (" + block.x() + ", "
                                                + block.y() + ", " + block.z() + ") while the plan was being placed; " + this.changes.size()
                                                + " blocks were placed and can be undone");
                                        return this.status;
                                    }
                                    if (this.journal != null && !asFound(this.applyIndex, old, oldData)) {
                                        // Changed since checking (F-2+): its pre-image isn't what is there now, so it waits.
                                        noteLate(block, old, oldData);
                                    } else {
                                        this.latestStart = StrictMath.max(this.latestStart, used);
                                        String newData = world.setBlockState(block.x(), block.y(), block.z(), block.state(), block.data());
                                        this.changes.add(new BlockChange(block.x(), block.y(), block.z(), old, block.state(), oldData, newData));
                                        markWritten(this.writeSection, block);
                                        changed++;
                                    }
                                }
                            }
                            this.applyIndex++;
                        }
                        if (this.journal != null) {
                            reportDone(this.sectionEnds.length);
                            queueLate();
                        }
                        this.status = this.late.isEmpty() ? Status.SHELL : Status.LATE;
                    }
                    case LATE -> {
                        if (!this.journal.onDisk(this.lateTicket)) {
                            if (this.journal.failed()) {
                                failJournal(world);
                            } else {
                                this.journalWaits++;
                            }
                            return this.status;
                        }
                        while (this.lateIndex < this.late.size()) {
                            long used = clock.getAsLong() - start;
                            if (used >= budgetNanos) {
                                return this.status;
                            }
                            Late note = this.late.get(this.lateIndex);
                            BlockPlan.Block block = this.blocks.get(note.index());
                            String old = world.blockState(block.x(), block.y(), block.z());
                            String oldData = world.hasBlockEntity(block.x(), block.y(), block.z()) ? world.blockEntityData(block.x(), block.y(), block.z()) : null;
                            BlockPlan.Block want = this.expected == null ? null : this.expected.get(note.index());
                            if (!old.equals(note.found()) || !Objects.equals(oldData, note.foundData())) {
                                this.changedWhilePlacing++; // changed yet again: left as it is
                            } else if (want != null && !asExpected(world, want, old)) {
                                if (this.guard == Guard.REFUSE_CHANGED) {
                                    fail(world, Failure.CONFLICT, "(" + block.x() + ", " + block.y() + ", " + block.z() + ") changed while the plan was being placed; "
                                            + this.changes.size() + " blocks were placed and can be undone");
                                    return this.status;
                                }
                                this.skipped.add(new BlockChange(block.x(), block.y(), block.z(), old, block.state()));
                            } else if (oldData != null && !world.canReplaceBlockEntity(block.x(), block.y(), block.z())) {
                                fail(world, Failure.BLOCK_ENTITY_APPEARED, old + " (another mod's block with stored data) appeared at (" + block.x() + ", "
                                        + block.y() + ", " + block.z() + ") while the plan was being placed; " + this.changes.size()
                                        + " blocks were placed and can be undone");
                                return this.status;
                            } else if (!old.equals(block.state()) || (block.data() != null && !block.data().equals(oldData))) {
                                this.latestStart = StrictMath.max(this.latestStart, used);
                                String newData = world.setBlockState(block.x(), block.y(), block.z(), block.state(), block.data());
                                this.changes.add(new BlockChange(block.x(), block.y(), block.z(), old, block.state(), oldData, newData));
                                int section = sectionOf(note.index());
                                markWritten(section, block);
                                this.lateSections.add(section);
                                changed++;
                            }
                            this.lateIndex++;
                        }
                        for (int section : this.lateSections) {
                            this.journal.sectionDone(this.sectionPositions[section], this.written[section].clone());
                        }
                        this.status = Status.SHELL;
                    }
                    case SHELL -> {
                        if (!shell(world, budgetNanos, clock, start)) {
                            return this.status;
                        }
                        if (this.pendingCode != null) {
                            this.status = Status.FAILED;
                            this.failureCode = this.pendingCode;
                            this.failure = this.pendingReason;
                        } else {
                            this.status = Status.DONE;
                        }
                        if (!this.changes.isEmpty()) {
                            world.blocksChanged(this.chunks.subList(0, this.retainIndex)); // F-7: while they are still loaded
                        }
                        release(world);
                    }
                    default -> throw new IllegalStateException("unreachable: " + this.status);
                }
            }
            return this.status;
        } finally {
            this.slices.add(new Slice(changed, clock.getAsLong() - start));
        }
    }

    /**
     * Checking, with a journal (F-2+): notes {@code block} as found if the job may change it (its state or its planned
     * data differs; with a guard, only if it is in its expected state), for the section's pre-image.
     */
    private void capture(WorldWriter world, BlockPlan.Block block, String current) {
        String data = world.hasBlockEntity(block.x(), block.y(), block.z()) ? world.blockEntityData(block.x(), block.y(), block.z()) : null;
        boolean differs = !current.equals(block.state()) || (block.data() != null && !block.data().equals(data));
        if (!differs) {
            return;
        }
        if (this.expected != null) {
            BlockPlan.Block want = this.expected.get(this.checkIndex);
            if (!want.state().equals(ANY) && (!current.equals(want.state()) || (want.data() != null && !want.data().equals(data)))) {
                return; // it won't be written unless it changes back; then it is noted late
            }
        }
        this.found[this.checkIndex] = current;
        if (data != null) {
            this.foundData.put(this.checkIndex, data);
        }
        this.captured.add(new BlockChange(block.x(), block.y(), block.z(), current, block.state(), data, block.data()));
    }

    /** Hands the pre-image part collected for the section being checked to the journal. */
    private void queueCaptured() {
        if (this.journal == null || this.captured.isEmpty()) {
            return;
        }
        this.sectionTickets[this.checkSection] = this.journal.preimage(this.sectionPositions[this.checkSection], this.plan.dataVersion(),
                List.copyOf(this.captured));
        this.captured.clear();
    }

    /** Whether the block at {@code index} is as checking found it, so its pre-image holds (F-2+). */
    private boolean asFound(int index, String state, String data) {
        return state.equals(this.found[index]) && Objects.equals(data, this.foundData.get(index));
    }

    /** A block that changed since checking (F-2+): noted again as it is now, and written in the late phase. */
    private void noteLate(BlockPlan.Block block, String old, String oldData) {
        this.late.add(new Late(this.applyIndex, old, oldData));
        this.lateCaptured.computeIfAbsent(this.writeSection, s -> new ArrayList<>())
                .add(new BlockChange(block.x(), block.y(), block.z(), old, block.state(), oldData, block.data()));
    }

    /** Hands the late pre-image parts noted so far to the journal. */
    private void queueLate() {
        if (this.journal == null || this.lateCaptured.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<BlockChange>> part : this.lateCaptured.entrySet()) {
            this.lateTicket = this.journal.preimage(this.sectionPositions[part.getKey()], this.plan.dataVersion(), List.copyOf(part.getValue()));
        }
        this.lateCaptured.clear();
    }

    /** Tells the journal about every section before {@code upTo} not yet reported, that had any block written. */
    private void reportDone(int upTo) {
        while (this.doneSection < upTo) {
            if (this.written[this.doneSection] != null) {
                this.journal.sectionDone(this.sectionPositions[this.doneSection], this.written[this.doneSection].clone());
            }
            this.doneSection++;
        }
    }

    /** The journal can't be written (F-2+): the job stops where it is; what it wrote can be undone. */
    private void failJournal(WorldWriter world) {
        fail(world, Failure.JOURNAL_FAILED, "the journal couldn't be written, so placing stopped; " + this.changes.size()
                + " blocks were placed and can be undone");
    }

    /** The section of the block at {@code index}. */
    private int sectionOf(int index) {
        int found = Arrays.binarySearch(this.sectionEnds, index);
        return found >= 0 ? found + 1 : -found - 1;
    }

    /**
     * Ends the job with {@code code}. If blocks were written (and the server isn't stopping), the blocks around them are
     * told first, in the shell phase, and the job fails after it.
     */
    private void fail(WorldWriter world, Failure code, String reason) {
        if (!this.changes.isEmpty() && code != Failure.STOPPED && this.status != Status.SHELL) {
            this.pendingCode = code;
            this.pendingReason = reason;
            this.status = Status.SHELL;
            return;
        }
        this.status = Status.FAILED;
        this.failureCode = code;
        this.failure = reason;
        release(world);
    }

    /** Whether the block at {@code want}'s position, now {@code current}, is in {@code want}'s state and data; {@link #ANY} always is. */
    private static boolean asExpected(WorldWriter world, BlockPlan.Block want, String current) {
        return want.state().equals(ANY) || current.equals(want.state()) && dataAsExpected(world, want);
    }

    /** Whether writing {@code block} would change the world, where the block is now {@code current} (R-7). */
    private static boolean differs(WorldWriter world, BlockPlan.Block block, String current) {
        if (!current.equals(block.state())) {
            return true;
        }
        if (block.data() == null) {
            return false;
        }
        String data = world.hasBlockEntity(block.x(), block.y(), block.z()) ? world.blockEntityData(block.x(), block.y(), block.z()) : null;
        return !block.data().equals(data);
    }

    /** Whether the block at {@code want}'s position has {@code want}'s block-entity data, where it names some (F-1+). */
    private static boolean dataAsExpected(WorldWriter world, BlockPlan.Block want) {
        if (want.data() == null) {
            return true;
        }
        return world.hasBlockEntity(want.x(), want.y(), want.z()) && want.data().equals(world.blockEntityData(want.x(), want.y(), want.z()));
    }

    /** Notes that the job wrote {@code block}, in section {@code section}, for the shell. */
    private void markWritten(int section, BlockPlan.Block block) {
        long[] bits = this.written[section];
        if (bits == null) {
            bits = new long[64];
            this.written[section] = bits;
        }
        int local = local(block.x(), block.y(), block.z());
        bits[local >>> 6] |= 1L << (local & 63);
    }

    private static int local(int x, int y, int z) {
        return (StrictMath.floorMod(y, 16) << 8) | (StrictMath.floorMod(z, 16) << 4) | StrictMath.floorMod(x, 16);
    }

    private static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sz & 0x3FFFFF) << 20) | (sy & 0xFFFFF);
    }

    /** Whether the job wrote the block at (x, y, z). */
    private boolean wrote(int x, int y, int z) {
        Integer index = this.sectionIndex.get(sectionKey(StrictMath.floorDiv(x, 16), StrictMath.floorDiv(y, 16), StrictMath.floorDiv(z, 16)));
        if (index == null || this.written[index] == null) {
            return false;
        }
        int local = local(x, y, z);
        return (this.written[index][local >>> 6] & (1L << (local & 63))) != 0;
    }

    private static final int[][] SIDES = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    /**
     * The outer shell (F-1+): for each block the job wrote, a section at a time, tells each neighbour the job didn't write
     * that it changed. Within the budget; true once every written block is done.
     */
    private boolean shell(WorldWriter world, long budgetNanos, LongSupplier clock, long start) {
        if (!this.shellStarted) {
            this.shellStarted = true;
            this.sectionIndex = new HashMap<>();
            for (int i = 0; i < this.sectionPositions.length; i++) {
                BlockPlan.SectionPos pos = this.sectionPositions[i];
                this.sectionIndex.put(sectionKey(pos.x(), pos.y(), pos.z()), i);
            }
            this.shellWord = -1;
        }
        while (this.shellSection < this.written.length) {
            long[] bits = this.written[this.shellSection];
            if (bits == null) {
                this.shellSection++;
                this.shellWord = -1;
                continue;
            }
            while (this.shellBits == 0) {
                this.shellWord++;
                if (this.shellWord >= 64) {
                    break;
                }
                this.shellBits = bits[this.shellWord];
            }
            if (this.shellWord >= 64) {
                this.shellSection++;
                this.shellWord = -1;
                continue;
            }
            if (clock.getAsLong() - start >= budgetNanos) {
                return false;
            }
            int local = this.shellWord * 64 + Long.numberOfTrailingZeros(this.shellBits);
            BlockPlan.SectionPos section = this.sectionPositions[this.shellSection];
            int x = section.x() * 16 + (local & 15);
            int y = section.y() * 16 + (local >> 8);
            int z = section.z() * 16 + ((local >> 4) & 15);
            int lx = local & 15;
            int ly = local >> 8;
            int lz = (local >> 4) & 15;
            for (int[] side : SIDES) {
                int nlx = lx + side[0];
                int nly = ly + side[1];
                int nlz = lz + side[2];
                boolean inside = nlx >= 0 && nlx < 16 && nly >= 0 && nly < 16 && nlz >= 0 && nlz < 16;
                int nx = x + side[0];
                int ny = y + side[1];
                int nz = z + side[2];
                // Within the section the bits answer at once; only a neighbour across its face needs the lookup.
                boolean written = inside ? (bits[(nly << 8 | nlz << 4 | nlx) >>> 6] & (1L << ((nly << 8 | nlz << 4 | nlx) & 63))) != 0 : wrote(nx, ny, nz);
                if (!written) {
                    world.neighbourChanged(nx, ny, nz, x, y, z);
                }
            }
            this.shellBits &= this.shellBits - 1;
        }
        return true;
    }

    /** Releases the chunks retained so far, once. */
    private void release(WorldWriter world) {
        if (this.retainIndex > 0 && !this.released) {
            world.releaseChunks(this.chunks.subList(0, this.retainIndex));
            this.released = true;
        }
    }

    /**
     * Stops the job where it is (the server is stopping) and releases its chunks. What was placed can still be undone. A
     * job whose blocks are all written ends as it would have, without the rest of its shell (F-1+).
     */
    public void abort(WorldWriter world) {
        if (this.status == Status.SHELL) {
            this.status = this.pendingCode != null ? Status.FAILED : Status.DONE;
            this.failureCode = this.pendingCode;
            this.failure = this.pendingReason;
            release(world);
        } else if (!this.status.finished()) {
            fail(world, Failure.STOPPED, "stopped before it finished; " + this.changes.size() + " blocks were placed and can be undone");
        }
    }

    public Status status() {
        return this.status;
    }

    public Optional<Failure> failureCode() {
        return Optional.ofNullable(this.failureCode);
    }

    public Optional<String> failure() {
        return Optional.ofNullable(this.failure);
    }

    public BlockPlan plan() {
        return this.plan;
    }

    public Guard guard() {
        return this.guard;
    }

    /** The chunks this job keeps loaded while it runs. */
    public List<ChunkXZ> chunks() {
        return this.chunks;
    }

    /** How many chunk sections the plan touches. */
    public int sectionCount() {
        return this.sectionEnds.length;
    }

    /**
     * How many sections the job has finished (each of their blocks written, skipped or already right), for progress
     * reports ({@code ApplyProgress}, editor-protocol §4). 0 until it starts writing; all of them once it is done.
     */
    public int sectionsDone() {
        if (this.status == Status.DONE) {
            return this.sectionEnds.length;
        }
        int done = 0;
        while (done < this.sectionEnds.length && this.sectionEnds[done] <= this.applyIndex) {
            done++;
        }
        return done;
    }

    /** How many blocks have been changed so far. */
    public int changedBlocks() {
        return this.changes.size();
    }

    /**
     * How many blocks {@link Guard#ASK} found not in their expected state, and not already in their new state (R-7: the
     * {@code MANUAL_EDITS} question's count). 0 for other guards.
     */
    public int manualEdits() {
        return this.manualEdits;
    }

    /** How many blocks {@link Guard#SKIP_CHANGED} has left alone so far ({@link #skipped}). */
    public int skippedBlocks() {
        return this.skipped.size();
    }

    /**
     * How many blocks changed between checking and applying and then again before the late phase could write them, so
     * they were left as they are (F-2+; with a journal only).
     */
    public int changedWhilePlacing() {
        return this.changedWhilePlacing;
    }

    /** How many slices ended waiting for a pre-image to reach the disk (F-2+: S12 reports it). */
    public int journalWaits() {
        return this.journalWaits;
    }

    /** How many blocks were found changed since checking, and so went to the late phase (F-2+). */
    public int lateBlocks() {
        return this.late.size();
    }

    /**
     * Every block changed so far, in the order they were written, with its state before and after. Once the job is
     * finished nothing changes this list any more, so another thread may read it then (F-2b: the helper files it).
     */
    public List<BlockChange> changes() {
        return List.copyOf(this.changes);
    }

    /**
     * The blocks left alone by {@link Guard#SKIP_CHANGED} because they weren't in their expected state; {@code before} is
     * the state found, {@code after} the state that would have been written.
     */
    public List<BlockChange> skipped() {
        return List.copyOf(this.skipped);
    }

    /** Every slice so far, in order. */
    public List<Slice> slices() {
        return List.copyOf(this.slices);
    }

    /** The most budget that had been used when any block was started (always less than the budget given). */
    public long latestBlockStartNanos() {
        return this.latestStart;
    }

    /** The plan that puts every block this job changed back to its previous state (spec mod-suite-v1 §6.6). */
    public BlockPlan undoPlan() {
        BlockPlan.Builder undo = BlockPlan.builder(this.plan.dataVersion());
        for (BlockChange change : this.changes) {
            undo.set(change.x(), change.y(), change.z(), change.before(), change.beforeData());
        }
        return undo.build();
    }
}
