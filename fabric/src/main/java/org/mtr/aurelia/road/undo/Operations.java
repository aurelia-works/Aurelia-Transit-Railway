/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.journal.Journal;
import org.mtr.aurelia.road.journal.JournalRecord;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.Helper;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.spi.WorldWriter;

/**
 * One world's edits with per-player undo and redo (spec mod-suite-v1 §6.6, editor-protocol §5): every apply, undo and
 * redo runs through the apply engine ({@link ApplyScheduler}), and each player's history lives in the
 * {@link UndoStore}.
 * <ul>
 *   <li><b>Apply</b> records the operation's changes on the player's undo history and clears their redo history.</li>
 *   <li><b>Undo</b> puts back the before states, but leaves any block changed by hand since (owner, 2026-10-05: hand
 *       edits are kept and reported; from R-7 a delete or rebuild asks, undo still doesn't, owner 2026-10-09).</li>
 *   <li><b>Redo</b> puts back the after states, and is refused with nothing written ({@code REDO_CONFLICT}) if any of
 *       its blocks changed since the undo.</li>
 *   <li>A player has one apply, undo or redo running at a time; another is refused with {@code BUSY}.</li>
 *   <li>If an undo or redo stops halfway, the part done moves to the other history and the rest stays.</li>
 * </ul>
 *
 * <p><b>From G-9</b> every apply has an {@link OperationRecord}, kept up to date by its undo and redo ({@link #record},
 * {@link #records}), and may have <b>side effects</b> (ADR-0040: MTR track), carried out by the
 * {@link SideEffectWriter} <b>after the blocks</b> every time:
 * <ul>
 *   <li><b>applying</b> makes the planned side effects once every block is written; those made are owned, one already
 *       there (a player's) never is;</li>
 *   <li><b>undoing</b> removes the owned ones once every block is put back; any that can't be removed stay owned, and
 *       the road is undone anyway (owner, 2026-10-08), with the outcome in the {@link Result};</li>
 *   <li><b>redoing</b> makes the planned ones not owned.</li>
 * </ul>
 * A player stays busy until their operation's side effects have settled, so they hear once, at the end.
 *
 * <p><b>From F-5</b> one {@code Operations} serves one dimension, and its histories and records are saved with the
 * world ({@code world.WorldData}): {@link #histories}, {@link #unsavedRecords} and {@link #restore}. A world whose
 * Stratos data is newer than this Stratos is read-only ({@link #setReadOnly}): every request is refused with
 * {@code WORLD_READ_ONLY}.
 *
 * <p><b>From F-2b</b> the work that grows with an edit runs on a <b>helper</b> ({@link Helper}: the platform's
 * "Stratos helper" thread), so a million-block edit doesn't freeze the server (spike S12). An accepted request moves
 * through these {@link Stage stages}:
 * <ol>
 *   <li><b>preparing</b> (helper): an apply's job is built; an undo's or redo's images, captured on the server thread,
 *       are read and unpacked, and its job built;</li>
 *   <li><b>placing</b> (server thread): the job runs in the scheduler, within the tick budget, as before;</li>
 *   <li><b>filing</b> (helper): an apply's images are compressed and its sections hashed; an undo's or redo's done and
 *       remaining parts are worked out, with any images they need;</li>
 *   <li><b>filed</b> (server thread): the undo store, the histories and the records take those results;</li>
 *   <li>then its <b>side effects</b>, as from G-9.</li>
 * </ol>
 * The player is busy from the request until its result. The images are then written to disk behind, by the store
 * ({@link UndoStore#afterWrites}), while the player carries on. The helper only touches data nobody changes any more: the
 * plan, a finished job's changes, and images captured from the store ({@link UndoStore#capture}, which holds the entry
 * until the request is filed, so the cap never drops it meanwhile). One player's requests run in order; different
 * players' jobs start in the order they are ready.
 *
 * <p><b>From F-2+</b> every accepted request is a <b>run</b> in the world's {@link Journal} (world-data-format §5): its
 * {@code BEGIN} when its job starts, the job's pre-images and progress (the job writes a section only once its
 * pre-image is on disk), its {@code END} when the job ends, and its track requests and answers around the side-effect
 * writer. Once filed, the journal hears which undo entries hold its blocks, so a save may list it. While a repair after
 * a crash runs ({@link #setRepairing}), every request is refused with {@code REPAIRING}.
 *
 * <p>Server thread only, but for the helper's work. Call {@link #afterTick()} after each scheduler tick, and
 * {@link #stop} when the server stops.
 */
public final class Operations {
    /** What a request was. */
    public enum Kind {
        APPLY,
        UNDO,
        REDO
    }

    /** Where an accepted request is (F-2b). */
    public enum Stage {
        /** Its job is being built on the helper (an undo's or redo's images unpacked first). */
        PREPARING,
        /** Its job is placing blocks. */
        PLACING,
        /** Its blocks are done; the helper is filing them (compressing, hashing, working out the parts). */
        FILING,
        /** Filed; its side effects are being made or removed. */
        SIDE_EFFECTS,
        /** It has its result. */
        DONE
    }

    /** Why a request was refused before anything started. */
    public enum Refusal {
        NOTHING_TO_UNDO,
        NOTHING_TO_REDO,
        BUSY,
        /** The world's Stratos data is newer than this Stratos (F-5: the downgrade guard). */
        WORLD_READ_ONLY,
        /** Stratos is repairing the world after a crash (F-2+). */
        REPAIRING,
        /** R-7: the road to delete or rebuild isn't there now (one of its operations was undone since it was planned). */
        ROAD_GONE,
        /** R-7: the road is another player's (multiplayer later). */
        NOT_YOUR_ROAD,
        /** R-7: a delete needs the undo images of every operation that built the road, and the cap has dropped one. */
        ROAD_TOO_OLD
    }

    /** A result's code when an undo's or redo's images can't be read: nothing was placed (from F-2b a result, not a refusal). */
    public static final String UNDO_DATA_UNREADABLE = "UNDO_DATA_UNREADABLE";
    /** A result's code when the helper's work failed before anything was placed: a bug, logged. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /** A result's code when the blocks were placed but filing them failed, so the history may not undo them cleanly: a bug, logged. */
    public static final String UNDO_NOT_RECORDED = "UNDO_NOT_RECORDED";
    /** A result's code for a request the server's stop ended before it placed anything, or while it placed. */
    public static final String STOPPED = "STOPPED";

    /** How long the server's stop waits for work the helper has already started. */
    private static final long STOP_WAIT_SECONDS = 120;

    /**
     * An accepted request, from the request until its result (F-2b): what it is, where it is, and its job once built.
     * Results carry the same handle.
     */
    public static final class Handle {
        private final Kind kind;
        private final String owner;
        private final UUID operationId;
        private volatile Stage stage = Stage.PREPARING;
        private volatile ApplyJob job;
        private volatile UUID run;

        /** A handle for a request; {@code Operations} makes one for each it accepts (tests may make their own). */
        public Handle(Kind kind, String owner, UUID operationId) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.operationId = Objects.requireNonNull(operationId, "operationId");
        }

        public Kind kind() {
            return this.kind;
        }

        public String owner() {
            return this.owner;
        }

        /** The operation it is about: an apply's own id, or the id of the operation an undo or redo is for. */
        public UUID operationId() {
            return this.operationId;
        }

        public Stage stage() {
            return this.stage;
        }

        /** Its job, once built (from {@link Stage#PLACING}); empty if it ended before one was. */
        public Optional<ApplyJob> job() {
            return Optional.ofNullable(this.job);
        }

        /** Its run id in the journal (F-2+), once its job has started; empty without a journal. */
        public Optional<UUID> run() {
            return Optional.ofNullable(this.run);
        }

        @Override
        public String toString() {
            return this.kind + " " + this.operationId + " of " + this.owner + " (" + this.stage + ")";
        }
    }

    /** A request's answer: its handle, or why it was refused. */
    public record Request(Optional<Handle> handle, Optional<Refusal> refusal) {
        static Request accepted(Handle handle) {
            return new Request(Optional.of(handle), Optional.empty());
        }

        static Request refused(Refusal refusal) {
            return new Request(Optional.empty(), Optional.of(refusal));
        }

        public boolean isAccepted() {
            return this.handle.isPresent();
        }
    }

    /**
     * A finished request: its handle, whose and what it was, its operation, its job (empty if it ended before one was
     * built), how many hand-edited blocks an undo kept, a failure code ({@code REDO_CONFLICT}, the apply engine's
     * failure name, or one of this class's codes) if it didn't fully succeed, and what happened to each side effect it
     * made or removed (G-9; empty when it had none).
     */
    public record Result(Handle handle, String owner, Kind kind, Optional<UUID> operationId, Optional<ApplyJob> job, int keptByHand,
            Optional<String> code, List<SideEffectOutcome> sideEffects) {
        public Result {
            Objects.requireNonNull(handle, "handle");
            sideEffects = List.copyOf(sideEffects);
        }

        Result withSideEffects(List<SideEffectOutcome> sideEffects) {
            return new Result(this.handle, this.owner, this.kind, this.operationId, this.job, this.keptByHand, this.code, sideEffects);
        }
    }

    /**
     * One side effect, whether the writer was asked to remove it (else to make it), and what it did. An undo removes, an
     * apply or redo makes; from R-7 a delete or rebuild (and their undo and redo) do both.
     */
    public record SideEffectOutcome(SideEffect effect, SideEffectWriter.Result result, boolean removal) {
        /** The outcome of making {@code effect}. */
        public SideEffectOutcome(SideEffect effect, SideEffectWriter.Result result) {
            this(effect, result, false);
        }
    }

    /** An operation in a player's history and whether it is undone (F-5: the roads the node aids know after a restart). */
    public record HistoryEntry(OperationRecord record, boolean undone) {
    }

    /** The dimension of operations made without one: the GameTests, and the tests. */
    public static final String OVERWORLD = "minecraft:overworld";

    /** What preparing made: an undo's or redo's unpacked changes (null for an apply), and the job. */
    private record Prepared(List<BlockChange> original, ApplyJob job) {
    }

    /** What filing an apply made: how many blocks changed, their images (null if none), and the section hashes. */
    private record ApplyFiling(int changed, UndoStore.Images images, List<OperationRecord.SectionHash> hashes) {
    }

    /**
     * What filing an undo or redo made: how many of its blocks were done, how many remain, how many were kept as hand
     * edits, the operation's new images if it is re-recorded (null if not), and a part split off: its images (null if
     * none) and section hashes.
     */
    private record PartsFiling(int done, int remaining, int kept, UndoStore.Images replaceWith, UndoStore.Images part,
            List<OperationRecord.SectionHash> partHashes) {
    }

    /** One call to the side-effect writer: remove or make {@code effects} (R-7: a delete or rebuild needs both, in turn). */
    private record TrackStep(boolean remove, List<SideEffect> effects) {
    }

    /** A request in flight, from the request until its result. */
    private static final class Pending {
        final Handle handle;
        final OperationRecord.Draft draft;
        final UndoStore.Entry entry; // an undo's or redo's operation, held from the request until it is filed
        List<UndoStore.Entry> held = List.of(); // R-7: a delete's or rebuild's road operations, held until it is filed
        List<SideEffect> roadEffects; // R-7: the side effects the road a delete or rebuild replaces has (null otherwise)
        UUID repairs; // R-7: a rebuild's road's latest operation, whose track it repairs when no block needs changing
        HelperTask<Prepared> preparing;
        List<BlockChange> original; // an undo's or redo's changes, once prepared
        ApplyJob job;
        Instant finished; // when its blocks finished: its record's time
        HelperTask<?> filing;
        Result filed; // once the blocks are filed
        List<TrackStep> steps; // the side effects to remove and make, in order
        int step; // the step being carried out
        CompletableFuture<List<SideEffectWriter.Result>> outcomes; // the writer's answer to that step
        final List<SideEffectOutcome> settled = new ArrayList<>(); // the outcomes of the steps done
        Journal.Run run; // its run in the journal (F-2+), once its job has started

        Pending(Handle handle, OperationRecord.Draft draft, UndoStore.Entry entry) {
            this.handle = handle;
            this.draft = draft;
            this.entry = entry;
        }
    }

    private final ApplyScheduler scheduler;
    private final UndoStore store;
    private final Supplier<UUID> ids;
    private final SideEffectWriter writer;
    private final Supplier<Instant> clock;
    private final Helper helper;
    private final Consumer<String> log;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Map<String, Deque<UndoStore.Entry>> undo = new HashMap<>();
    private final Map<String, Deque<UndoStore.Entry>> redo = new HashMap<>();
    private final Map<UUID, OperationRecord> records = new LinkedHashMap<>();
    private final String dimension;
    private final Set<UUID> unsavedRecords = new LinkedHashSet<>();
    private long changes;
    private long savedChanges;
    private boolean readOnly;
    private boolean stopping;
    private Journal journal; // F-2+: null without one (tests, read-only worlds)
    private boolean repairing;

    /** Operations with no side-effect writer (every side effect {@code UNAVAILABLE}), the system clock, and no helper. */
    public Operations(ApplyScheduler scheduler, UndoStore store, Supplier<UUID> ids) {
        this(scheduler, store, ids, SideEffectWriter.UNAVAILABLE, Instant::now);
    }

    public Operations(ApplyScheduler scheduler, UndoStore store, Supplier<UUID> ids, SideEffectWriter writer, Supplier<Instant> clock) {
        this(scheduler, store, ids, writer, clock, OVERWORLD);
    }

    /** Operations in {@code dimension} (such as {@code minecraft:overworld}), which their records name (F-5), with no helper. */
    public Operations(ApplyScheduler scheduler, UndoStore store, Supplier<UUID> ids, SideEffectWriter writer, Supplier<Instant> clock,
            String dimension) {
        this(scheduler, store, ids, writer, clock, dimension, Helper.AT_ONCE, line -> {
        });
    }

    /**
     * Operations in {@code dimension} whose growing work runs on {@code helper} (F-2b; {@link Helper#AT_ONCE} does it on
     * the server thread), logging failures of that work to {@code log}.
     */
    public Operations(ApplyScheduler scheduler, UndoStore store, Supplier<UUID> ids, SideEffectWriter writer, Supplier<Instant> clock,
            String dimension, Helper helper, Consumer<String> log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.store = Objects.requireNonNull(store, "store");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.helper = Objects.requireNonNull(helper, "helper");
        this.log = Objects.requireNonNull(log, "log");
        store.onPruned(this::forget);
    }

    public String dimension() {
        return this.dimension;
    }

    /** Makes every request be refused with {@code WORLD_READ_ONLY} (F-5: the downgrade guard), or lifts that. */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    /** Gives these operations the world's journal (F-2+): every request from now on is a run in it. */
    public void journal(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /** Makes every request be refused with {@code REPAIRING} while a repair after a crash runs (F-2+), or lifts that. */
    public void setRepairing(boolean repairing) {
        this.repairing = repairing;
    }

    public boolean isRepairing() {
        return this.repairing;
    }

    /** The refusal every request gets now, if any: read-only first, then a repair running. */
    private Optional<Refusal> refusedNow() {
        if (this.readOnly) {
            return Optional.of(Refusal.WORLD_READ_ONLY);
        }
        return this.repairing ? Optional.of(Refusal.REPAIRING) : Optional.empty();
    }

    public ApplyScheduler scheduler() {
        return this.scheduler;
    }

    public UndoStore store() {
        return this.store;
    }

    /** Queues {@code plan} as {@code owner}'s next operation, with a new operation id. */
    public Request apply(String owner, BlockPlan plan) {
        return apply(owner, plan, this.ids.get());
    }

    /**
     * Queues {@code plan} as {@code owner}'s next operation, whose undo history entry will have the id {@code operationId}
     * (the network layer tells the client the id before the operation runs: {@code PlanAccepted}, F-3).
     */
    public Request apply(String owner, BlockPlan plan, UUID operationId) {
        return apply(owner, plan, operationId, Optional.empty());
    }

    /**
     * As {@link #apply(String, BlockPlan, UUID)}; with {@code onlyWhere} (R-6: clearing node markers), a block is written
     * only if it is now in its state there, and any other is left as it is ({@link ApplyJob.Guard#SKIP_CHANGED}). Undo
     * and redo then cover only the blocks that changed.
     */
    public Request apply(String owner, BlockPlan plan, UUID operationId, Optional<BlockPlan> onlyWhere) {
        return apply(owner, plan, operationId, onlyWhere, OperationRecord.Draft.NONE);
    }

    /**
     * As {@link #apply(String, BlockPlan, UUID, Optional)}, with what its record says it was ({@code draft}: module, tool,
     * parameters, plan hash) and the side effects to carry out once its blocks are written (G-9). Its job is built on
     * the helper (F-2b).
     */
    public Request apply(String owner, BlockPlan plan, UUID operationId, Optional<BlockPlan> onlyWhere, OperationRecord.Draft draft) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(onlyWhere, "onlyWhere");
        Objects.requireNonNull(draft, "draft");
        Optional<Refusal> refusal = refusedNow();
        if (refusal.isPresent()) {
            return Request.refused(refusal.get());
        }
        if (this.pending.containsKey(owner)) {
            return Request.refused(Refusal.BUSY);
        }
        Pending pending = new Pending(new Handle(Kind.APPLY, owner, operationId), draft, null);
        pending.preparing = new HelperTask<>(() -> new Prepared(null,
                onlyWhere.isPresent() ? new ApplyJob(plan, onlyWhere.get(), ApplyJob.Guard.SKIP_CHANGED) : new ApplyJob(plan)));
        this.pending.put(owner, pending);
        submit(pending.preparing);
        return Request.accepted(pending.handle);
    }

    /**
     * Queues a delete or rebuild of a road (task R-7; owner, 2026-10-09) as {@code owner}'s next operation
     * {@code operationId}: {@code plan} is the road's new plan (empty for a delete), {@code replacement} the road it
     * replaces, and {@code draft} what its record says it is (its plan's side effects included). Refused, with nothing
     * started, when one of the road's operations isn't applied now ({@link Refusal#ROAD_GONE}), isn't {@code owner}'s
     * ({@link Refusal#NOT_YOUR_ROAD}), or, for a delete, has lost its undo images to the cap
     * ({@link Refusal#ROAD_TOO_OLD}; owner: delete puts back exactly what was there, or not at all).
     *
     * <p>Its job is built on the helper ({@link #replacementJob}) from the undo images of the road's operations, held
     * until it is filed, and refuses to start when it would change nothing ({@code NOTHING_TO_CHANGE}) or, with no answer
     * yet, when blocks were changed by hand ({@code MANUAL_EDITS}). Such a refusal leaves no record. Once its blocks are
     * in, its side effects remove the road's rails its plan doesn't have, then make its plan's: a rail the road already
     * has stays and is inherited, not made again.
     */
    public Request replace(String owner, BlockPlan plan, Replacement replacement, UUID operationId, OperationRecord.Draft draft) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(draft, "draft");
        Optional<Refusal> refusal = refusedNow();
        if (refusal.isPresent()) {
            return Request.refused(refusal.get());
        }
        if (this.pending.containsKey(owner)) {
            return Request.refused(Refusal.BUSY);
        }
        List<UndoStore.Entry> entries = new ArrayList<>();
        for (UUID id : replacement.chain()) {
            OperationRecord record = this.records.get(id);
            if (record == null || record.status() != OperationRecord.Status.APPLIED) {
                return Request.refused(Refusal.ROAD_GONE);
            }
            if (!record.author().equals(owner)) {
                return Request.refused(Refusal.NOT_YOUR_ROAD);
            }
            Optional<UndoStore.Entry> entry = this.store.entry(id);
            if (entry.isPresent()) {
                entries.add(entry.get());
            } else if (replacement.delete() && !record.sections().isEmpty()) {
                return Request.refused(Refusal.ROAD_TOO_OLD);
            }
        }
        List<UndoStore.Captured> captured = new ArrayList<>();
        entries.forEach(entry -> captured.add(this.store.capture(entry)));
        Pending pending = new Pending(new Handle(Kind.APPLY, owner, operationId), draft, null);
        pending.held = List.copyOf(entries);
        pending.roadEffects = this.records.get(replacement.latest()).roadEffects();
        pending.repairs = replacement.delete() ? null : replacement.latest();
        pending.preparing = new HelperTask<>(() -> {
            List<List<BlockChange>> images = new ArrayList<>();
            for (UndoStore.Captured images1 : captured) {
                images.add(images1.read());
            }
            return new Prepared(null, replacementJob(plan, images, replacement));
        });
        this.pending.put(owner, pending);
        submit(pending.preparing);
        return Request.accepted(pending.handle);
    }

    /**
     * The job of a delete or rebuild (R-7), from {@code plan} (the road's new plan, empty for a delete) and
     * {@code images}, the changes of the road's operations, oldest first:
     * <ul>
     *   <li>every block of {@code plan} is written; its expected state is what the road's last operation to change it
     *       left there, else its state in the road's plan as placed, else any ({@link ApplyJob#ANY}: a block the road
     *       never placed is written like a new road's);</li>
     *   <li>every other block the road's operations changed goes back to the <b>ground</b>, its state and data before the
     *       first of them changed it, where it is as the last of them left it; except the blocks of the roads joined to
     *       it ({@link Replacement#keep}), and blocks the operations together left as they were. A block of the first
     *       operation's plan that it didn't change already had its planned state: that is the ground there, even when a
     *       rebuild later changed it ({@link Replacement#firstPlaced}).</li>
     * </ul>
     * The guard is the replacement's; the job refuses to start when it would change nothing. Any thread.
     */
    static ApplyJob replacementJob(BlockPlan plan, List<List<BlockChange>> images, Replacement replacement) {
        Map<Long, BlockChange> ground = new HashMap<>();
        Map<Long, BlockChange> last = new LinkedHashMap<>();
        Set<Long> firstChanged = new java.util.HashSet<>(); // changed by the first operation (when its images are there)
        for (int i = 0; i < images.size(); i++) {
            for (BlockChange change : images.get(i)) {
                ground.putIfAbsent(change.positionKey(), change);
                last.put(change.positionKey(), change);
                if (i == 0) {
                    firstChanged.add(change.positionKey());
                }
            }
        }
        Map<Long, BlockPlan.Block> firstPlan = new HashMap<>();
        replacement.firstPlaced().ifPresent(first -> first.blocks().forEach(block -> firstPlan.put(BlockChange.positionKey(block.x(), block.y(),
                block.z()), block)));
        boolean firstImages = images.size() == replacement.chain().size(); // every operation's images, the first's included
        Map<Long, BlockPlan.Block> placed = new HashMap<>();
        replacement.asPlaced().ifPresent(asPlaced -> asPlaced.blocks().forEach(block -> placed.put(BlockChange.positionKey(block.x(), block.y(),
                block.z()), block)));
        BlockPlan.Builder writes = BlockPlan.builder(plan.dataVersion());
        BlockPlan.Builder expects = BlockPlan.builder(plan.dataVersion());
        Set<Long> planned = new java.util.HashSet<>();
        for (BlockPlan.Block block : plan.blocks()) {
            long key = BlockChange.positionKey(block.x(), block.y(), block.z());
            planned.add(key);
            writes.set(block.x(), block.y(), block.z(), block.state(), block.data());
            BlockChange was = last.get(key);
            BlockPlan.Block asPlaced = placed.get(key);
            if (was != null) {
                expects.set(block.x(), block.y(), block.z(), was.after(), was.afterData());
            } else if (asPlaced != null) {
                expects.set(block.x(), block.y(), block.z(), asPlaced.state(), asPlaced.data());
            } else {
                expects.set(block.x(), block.y(), block.z(), ApplyJob.ANY);
            }
        }
        for (BlockChange was : last.values()) {
            long key = was.positionKey();
            BlockChange first = ground.get(key);
            String groundState = first.before();
            String groundData = first.beforeData();
            BlockPlan.Block unchanged = firstPlan.get(key);
            if (unchanged != null && firstImages && !firstChanged.contains(key)) {
                groundState = unchanged.state(); // the first operation found it in its planned state
                groundData = unchanged.data();
            }
            if (planned.contains(key) || replacement.keep().contains(key)
                    || (groundState.equals(was.after()) && Objects.equals(groundData, was.afterData()))) {
                continue;
            }
            writes.set(was.x(), was.y(), was.z(), groundState, groundData);
            expects.set(was.x(), was.y(), was.z(), was.after(), was.afterData());
        }
        ApplyJob.Guard guard = replacement.guard();
        return ApplyJob.refusingNothing(writes.build(), guard == ApplyJob.Guard.NONE ? null : expects.build(), guard);
    }

    /** Undoes {@code owner}'s latest operation. */
    public Request undo(String owner) {
        Optional<Refusal> refusal = refusedNow();
        if (refusal.isPresent()) {
            return Request.refused(refusal.get());
        }
        if (this.pending.containsKey(owner)) {
            return Request.refused(Refusal.BUSY);
        }
        UndoStore.Entry entry = stack(this.undo, owner).peek();
        if (entry == null) {
            return Request.refused(Refusal.NOTHING_TO_UNDO);
        }
        return start(owner, Kind.UNDO, entry, ApplyJob.Guard.SKIP_CHANGED);
    }

    /** Redoes {@code owner}'s latest undone operation. */
    public Request redo(String owner) {
        Optional<Refusal> refusal = refusedNow();
        if (refusal.isPresent()) {
            return Request.refused(refusal.get());
        }
        if (this.pending.containsKey(owner)) {
            return Request.refused(Refusal.BUSY);
        }
        UndoStore.Entry entry = stack(this.redo, owner).peek();
        if (entry == null) {
            return Request.refused(Refusal.NOTHING_TO_REDO);
        }
        return start(owner, Kind.REDO, entry, ApplyJob.Guard.REFUSE_CHANGED);
    }

    /** Captures {@code entry}'s images (holding it) and has the helper unpack them and build the job. */
    private Request start(String owner, Kind kind, UndoStore.Entry entry, ApplyJob.Guard guard) {
        UndoStore.Captured captured = this.store.capture(entry);
        int dataVersion = entry.dataVersion();
        Pending pending = new Pending(new Handle(kind, owner, entry.id()), OperationRecord.Draft.NONE, entry);
        pending.preparing = new HelperTask<>(() -> {
            List<BlockChange> original = captured.read();
            // Undo writes the before states where the after states still are; redo the reverse.
            List<BlockPlan.Block> writes = new ArrayList<>(original.size());
            List<BlockPlan.Block> expects = new ArrayList<>(original.size());
            // A block entity's data goes with its state (F-1+): undo puts back the data from before, redo the data from after.
            for (BlockChange change : original) {
                writes.add(kind == Kind.UNDO ? new BlockPlan.Block(change.x(), change.y(), change.z(), change.before(), change.beforeData())
                        : new BlockPlan.Block(change.x(), change.y(), change.z(), change.after(), change.afterData()));
                expects.add(kind == Kind.UNDO ? new BlockPlan.Block(change.x(), change.y(), change.z(), change.after(), change.afterData())
                        : new BlockPlan.Block(change.x(), change.y(), change.z(), change.before(), change.beforeData()));
            }
            // The images unpack in canonical order, so the plans are built straight from them (S12: far less garbage).
            Optional<BlockPlan> plan = BlockPlan.ofCanonical(dataVersion, writes);
            Optional<BlockPlan> expected = BlockPlan.ofCanonical(dataVersion, expects);
            if (plan.isEmpty() || expected.isEmpty()) {
                BlockPlan.Builder planBuilder = BlockPlan.builder(dataVersion);
                BlockPlan.Builder expectedBuilder = BlockPlan.builder(dataVersion);
                for (int i = 0; i < writes.size(); i++) {
                    BlockPlan.Block write = writes.get(i);
                    planBuilder.set(write.x(), write.y(), write.z(), write.state(), write.data());
                    expectedBuilder.set(write.x(), write.y(), write.z(), expects.get(i).state(), expects.get(i).data());
                }
                plan = Optional.of(planBuilder.build());
                expected = Optional.of(expectedBuilder.build());
            }
            return new Prepared(original, new ApplyJob(plan.get(), expected.get(), guard));
        });
        this.pending.put(owner, pending);
        submit(pending.preparing);
        return Request.accepted(pending.handle);
    }

    /** Hands {@code work} to the helper; does it here once the server is stopping, or if the helper won't take it. */
    private void submit(HelperTask<?> work) {
        if (this.stopping) {
            work.run();
            return;
        }
        try {
            this.helper.work(work);
        } catch (RejectedExecutionException e) {
            work.run();
        }
    }

    /**
     * Moves every request on as far as it can go, and returns those finished, side effects settled. Also files the undo
     * images the helper has finished writing ({@link UndoStore#afterWrites}).
     */
    public List<Result> afterTick() {
        this.store.afterWrites();
        List<Result> results = new ArrayList<>();
        for (Iterator<Map.Entry<String, Pending>> it = this.pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Pending> next = it.next();
            Result result = advance(next.getKey(), next.getValue());
            if (result != null) {
                it.remove();
                results.add(result);
            }
        }
        return results;
    }

    /** Moves {@code pending} through as many stages as are ready; its result once it has one, else null. */
    private Result advance(String owner, Pending pending) {
        Handle handle = pending.handle;
        if (handle.stage == Stage.PREPARING) {
            if (!pending.preparing.isDone()) {
                return null;
            }
            Prepared prepared;
            try {
                prepared = pending.preparing.result().join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof IOException) {
                    return ended(pending, UNDO_DATA_UNREADABLE);
                }
                this.log.accept("[stratos] preparing " + handle + " failed: " + cause);
                return ended(pending, INTERNAL_ERROR);
            }
            pending.original = prepared.original();
            pending.job = prepared.job();
            if (this.journal != null) {
                // F-2+: its BEGIN goes first; the job hands over pre-images while checking.
                String planHash = handle.kind == Kind.APPLY ? pending.draft.planHash()
                        : Optional.ofNullable(this.records.get(handle.operationId)).map(OperationRecord::planHash).orElse("");
                pending.run = this.journal.begin(handle.kind, handle.operationId, this.dimension, owner, planHash);
                pending.job.journal(pending.run);
                handle.run = pending.run.id();
            }
            this.scheduler.submit(pending.job);
            handle.job = pending.job;
            handle.stage = Stage.PLACING;
        }
        if (handle.stage == Stage.PLACING) {
            if (!pending.job.status().finished()) {
                return null;
            }
            pending.finished = this.clock.get();
            if (pending.run != null) {
                pending.run.end(pending.job);
            }
            startFiling(pending);
            handle.stage = Stage.FILING;
        }
        if (handle.stage == Stage.FILING) {
            if (!pending.filing.isDone()) {
                return null;
            }
            release(pending); // the helper is done with the images
            pending.filed = switch (handle.kind) {
                case APPLY -> finishApply(owner, pending);
                case UNDO -> finishUndo(owner, pending);
                case REDO -> finishRedo(owner, pending);
            };
            this.changes++;
            // Then the side effects, if any: removing, then making (R-7), each once the one before is answered.
            pending.steps = steps(pending);
            pending.step = 0;
            handle.stage = Stage.SIDE_EFFECTS;
        }
        while (pending.step < pending.steps.size()) {
            TrackStep step = pending.steps.get(pending.step);
            if (pending.outcomes == null) {
                pending.outcomes = write(pending, step);
            }
            if (!pending.outcomes.isDone()) {
                return null;
            }
            settle(pending, step);
            pending.outcomes = null;
            pending.step++;
        }
        handle.stage = Stage.DONE;
        return pending.settled.isEmpty() ? pending.filed : pending.filed.withSideEffects(pending.settled);
    }

    /** Lets go of the undo entries {@code pending} held for the helper (an undo's or redo's, a delete's or rebuild's). */
    private void release(Pending pending) {
        if (pending.entry != null) {
            this.store.release(pending.entry);
        }
        pending.held.forEach(this.store::release);
        pending.held = List.of();
    }

    /** {@code pending} ends before its job was placed (nothing written), with {@code code}. */
    private Result ended(Pending pending, String code) {
        release(pending);
        Handle handle = pending.handle;
        handle.stage = Stage.DONE;
        Optional<UUID> operationId = handle.kind == Kind.APPLY ? Optional.empty() : Optional.of(handle.operationId);
        return new Result(handle, handle.owner, handle.kind, operationId, Optional.empty(), 0, Optional.of(code), List.of());
    }

    /** Hands the work that grows with the edit to the helper, now that {@code pending}'s blocks are done. */
    private void startFiling(Pending pending) {
        ApplyJob job = pending.job;
        if (pending.handle.kind == Kind.APPLY) {
            UUID id = pending.handle.operationId;
            int dataVersion = job.plan().dataVersion();
            pending.filing = new HelperTask<>(() -> {
                List<BlockChange> changed = job.changes(); // the job is finished: nothing writes to its changes any more
                UndoStore.Images images = changed.isEmpty() ? null : this.store.compress(id, 1, dataVersion, changed, false);
                return new ApplyFiling(changed.size(), images, SectionHashes.of(changed));
            });
        } else {
            Kind kind = pending.handle.kind;
            UndoStore.Entry entry = pending.entry;
            List<BlockChange> original = pending.original;
            int kept = kind == Kind.UNDO ? job.skippedBlocks() : 0;
            int done = job.changedBlocks();
            // Only a job that stopped partway leaves a part done and a part not: that part gets its own operation id.
            UUID partId = done > 0 && done + kept < original.size() ? this.ids.get() : null;
            UUID id = entry.id();
            int generation = this.store.nextGeneration(entry);
            int dataVersion = entry.dataVersion();
            pending.filing = new HelperTask<>(() -> parts(kind, original, job, id, generation, partId, dataVersion));
        }
        submit(pending.filing);
    }

    /**
     * Works out an undo's or redo's done and remaining parts (helper): the done part moves to the other history, the
     * remaining part stays. When everything is done, the operation moves whole, re-recorded if some blocks were kept by
     * hand; when only part is, the operation is re-recorded as the remaining part and the done part split off as
     * {@code partId}.
     */
    private PartsFiling parts(Kind kind, List<BlockChange> original, ApplyJob job, UUID id, int generation, UUID partId, int dataVersion) {
        long[] written = sortedKeys(job.changes());
        long[] kept = kind == Kind.UNDO ? sortedKeys(job.skipped()) : new long[0];
        List<BlockChange> done = new ArrayList<>(written.length);
        List<BlockChange> remaining = new ArrayList<>();
        for (BlockChange change : original) {
            long key = change.positionKey();
            if (Arrays.binarySearch(written, key) >= 0) {
                done.add(change);
            } else if (Arrays.binarySearch(kept, key) < 0) {
                remaining.add(change);
            }
        }
        UndoStore.Images replaceWith = null;
        UndoStore.Images part = null;
        List<OperationRecord.SectionHash> partHashes = List.of();
        if (remaining.isEmpty()) {
            if (!done.isEmpty() && kept.length > 0) {
                replaceWith = this.store.compress(id, generation, dataVersion, done, false);
            }
        } else if (!done.isEmpty()) {
            if (partId == null) {
                throw new IllegalStateException("a part split off without an id");
            }
            replaceWith = this.store.compress(id, generation, dataVersion, remaining, false);
            part = this.store.compress(partId, 1, dataVersion, done, false);
            partHashes = SectionHashes.of(done);
        }
        return new PartsFiling(done.size(), remaining.size(), kept.length, replaceWith, part, partHashes);
    }

    private static long[] sortedKeys(List<BlockChange> changes) {
        long[] keys = new long[changes.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = changes.get(i).positionKey();
        }
        Arrays.sort(keys);
        return keys;
    }

    /** The filing's result, or null after logging why the helper's work failed. */
    private <T> T filingResult(Pending pending, Class<T> type) {
        try {
            return type.cast(pending.filing.result().join());
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            this.log.accept("[stratos] filing " + pending.handle + " failed: " + cause);
            return null;
        }
    }

    /**
     * The side effects to remove and make, in that order, now that {@code pending}'s blocks are filed: none unless they
     * all were.
     * <ul>
     *   <li><b>Apply:</b> make the planned ones. From R-7 a delete or rebuild first removes the rails of the road it
     *       replaces that its plan doesn't have.</li>
     *   <li><b>Undo:</b> remove the owned ones, then (R-7) make again those it removed.</li>
     *   <li><b>Redo:</b> (R-7) remove again those it removed, then make the planned ones it neither owns nor inherited.</li>
     * </ul>
     */
    private List<TrackStep> steps(Pending pending) {
        OperationRecord record = this.records.get(pending.handle.operationId);
        List<TrackStep> steps = new ArrayList<>();
        if (record == null && pending.repairs != null && pending.filed.code().filter(ApplyJob.Failure.NOTHING_TO_CHANGE.name()::equals).isPresent()) {
            // R-7: a rebuild whose blocks are all as built still puts back the road's missing track (owner: "missing track made
            // again"), for the operation that owns it: no new operation, so nothing to undo.
            OperationRecord latest = this.records.get(pending.repairs);
            if (latest != null && latest.status() == OperationRecord.Status.APPLIED) {
                step(steps, false, latest.planned());
            }
            return steps;
        }
        if (record == null || pending.filed.code().filter(UNDO_NOT_RECORDED::equals).isPresent()) {
            return steps;
        }
        switch (pending.handle.kind) {
            case APPLY -> {
                if (record.status() == OperationRecord.Status.APPLIED) {
                    if (pending.roadEffects != null) {
                        List<SideEffect> remove = new ArrayList<>(pending.roadEffects);
                        remove.removeAll(record.planned());
                        step(steps, true, remove);
                    }
                    step(steps, false, record.planned());
                }
            }
            case UNDO -> {
                if (record.status() == OperationRecord.Status.UNDONE) {
                    step(steps, true, record.owned());
                    step(steps, false, record.removed());
                }
            }
            case REDO -> {
                if (record.status() == OperationRecord.Status.APPLIED) {
                    step(steps, true, record.removed());
                    List<SideEffect> missing = new ArrayList<>(record.planned());
                    missing.removeAll(record.owned());
                    missing.removeAll(record.inherited());
                    step(steps, false, missing);
                }
            }
        }
        return steps;
    }

    private static void step(List<TrackStep> steps, boolean remove, List<SideEffect> effects) {
        if (!effects.isEmpty()) {
            steps.add(new TrackStep(remove, List.copyOf(effects)));
        }
    }

    /** Asks the writer to carry out {@code step} of {@code pending}'s side effects. */
    private CompletableFuture<List<SideEffectWriter.Result>> write(Pending pending, TrackStep step) {
        if (pending.run != null) {
            pending.run.track(step.remove() ? JournalRecord.TrackAction.REMOVE : JournalRecord.TrackAction.MAKE, step.effects());
        }
        try {
            CompletableFuture<List<SideEffectWriter.Result>> future = step.remove() ? this.writer.remove(this.dimension, step.effects())
                    : this.writer.make(this.dimension, step.effects());
            return Objects.requireNonNull(future, "the side-effect writer's answer");
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * The writer's answer to {@code step}, filed into the record:
     * <ul>
     *   <li>made ones are owned, removed ones no longer (undo), leftovers kept;</li>
     *   <li>(R-7) a delete or rebuild notes the road's rails it removed ({@code removed}, made again by its undo), and a
     *       rail of its plan the road already had is inherited, not owned, so its undo leaves it to the road.</li>
     * </ul>
     */
    private void settle(Pending pending, TrackStep step) {
        List<SideEffectWriter.Result> results;
        String noAnswer = "the side-effect writer gave no answer";
        try {
            results = pending.outcomes.join();
            if (results == null) {
                results = List.of();
            }
        } catch (RuntimeException e) {
            results = List.of();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            noAnswer = "the side-effect writer failed: " + cause;
        }
        List<SideEffectOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < step.effects().size(); i++) {
            SideEffectWriter.Result result = i < results.size() && results.get(i) != null ? results.get(i)
                    : new SideEffectWriter.Result(SideEffectWriter.Outcome.FAILED, noAnswer);
            outcomes.add(new SideEffectOutcome(step.effects().get(i), result, step.remove()));
        }
        if (pending.run != null) {
            pending.run.trackDone(step.remove() ? JournalRecord.TrackAction.REMOVE : JournalRecord.TrackAction.MAKE, step.effects(),
                    outcomes.stream().map(o -> o.result().outcome()).toList());
        }
        for (SideEffectOutcome outcome : outcomes) {
            // R-7: a rail of the road that a rebuild found still there was kept, neither made nor removed: nothing to report.
            boolean kept = !outcome.removal() && outcome.result().outcome() == SideEffectWriter.Outcome.ALREADY_THERE && pending.roadEffects != null
                    && pending.roadEffects.contains(outcome.effect());
            if (!kept) {
                pending.settled.add(outcome);
            }
        }
        OperationRecord record = this.records.get(pending.handle.operationId);
        if (record == null && pending.repairs != null && this.records.containsKey(pending.repairs)) {
            // R-7: the track a rebuild with nothing else to change made again belongs to the road's latest operation, unless
            // that already holds it (owned, or inherited from the road before it).
            OperationRecord latest = this.records.get(pending.repairs);
            List<SideEffect> owned = new ArrayList<>(latest.owned());
            for (SideEffectOutcome outcome : outcomes) {
                if (outcome.result().outcome() == SideEffectWriter.Outcome.DONE && !owned.contains(outcome.effect())
                        && !latest.inherited().contains(outcome.effect())) {
                    owned.add(outcome.effect());
                }
            }
            putRecord(latest.withOwned(owned));
            return;
        }
        if (record == null) {
            return;
        }
        List<SideEffect> owned = new ArrayList<>(record.owned());
        List<SideEffect> inherited = new ArrayList<>(record.inherited());
        List<SideEffect> removed = new ArrayList<>(record.removed());
        Kind kind = pending.handle.kind;
        for (SideEffectOutcome outcome : outcomes) {
            SideEffectWriter.Outcome what = outcome.result().outcome();
            SideEffect effect = outcome.effect();
            if (step.remove() && kind == Kind.UNDO) {
                if (what == SideEffectWriter.Outcome.DONE || what == SideEffectWriter.Outcome.NOT_THERE) {
                    owned.remove(effect);
                }
            } else if (step.remove()) {
                // A delete's or rebuild's removal of the road's rails (apply, redo): undo makes those removed again.
                if (what == SideEffectWriter.Outcome.DONE && !removed.contains(effect)) {
                    removed.add(effect);
                } else if (what == SideEffectWriter.Outcome.NOT_THERE) {
                    removed.remove(effect);
                }
            } else if (kind != Kind.UNDO) {
                if (what == SideEffectWriter.Outcome.DONE && !owned.contains(effect)) {
                    owned.add(effect);
                } else if (what == SideEffectWriter.Outcome.ALREADY_THERE && kind == Kind.APPLY && pending.roadEffects != null
                        && pending.roadEffects.contains(effect) && !inherited.contains(effect)) {
                    inherited.add(effect); // the road's own rail, which a rebuild keeps
                }
            }
            // An undo's making again of the rails it had removed needs nothing filed: the road's own records still hold them.
        }
        putRecord(record.withTrack(owned, inherited, removed));
    }

    private Result finishApply(String owner, Pending pending) {
        ApplyJob job = pending.job;
        UUID operationId = pending.handle.operationId;
        ApplyFiling filing = filingResult(pending, ApplyFiling.class);
        int changed = filing != null ? filing.changed() : job.changedBlocks();
        Optional<UUID> id = Optional.empty();
        boolean undoAvailable = false;
        if (changed > 0) {
            // A new edit makes the player's undone operations unreachable (spec mod-suite-v1 §6.6).
            Deque<UndoStore.Entry> redoStack = stack(this.redo, owner);
            List<UndoStore.Entry> unreachable = new ArrayList<>(redoStack);
            redoStack.clear();
            unreachable.forEach(this::drop);
            if (filing != null) {
                UndoStore.Entry entry = this.store.add(owner, filing.images());
                if (this.store.contains(entry)) {
                    stack(this.undo, owner).push(entry);
                    undoAvailable = true;
                }
            }
            id = Optional.of(operationId);
        }
        if (pending.run != null && (filing != null || changed == 0)) {
            pending.run.filed(changed > 0 ? List.of(operationId) : List.of()); // a save may list it once its images are on disk
        }
        boolean failed = job.status() == ApplyJob.Status.FAILED;
        OperationRecord.Status status = changed == 0 ? (failed ? OperationRecord.Status.FAILED : OperationRecord.Status.APPLIED)
                : (failed ? OperationRecord.Status.PARTLY_APPLIED : OperationRecord.Status.APPLIED);
        OperationRecord.Draft draft = pending.draft;
        // R-7: a delete or rebuild that only asked about hand edits, or had nothing to change, leaves no record.
        boolean asked = changed == 0 && job.failureCode().filter(f -> f == ApplyJob.Failure.MANUAL_EDITS || f == ApplyJob.Failure.NOTHING_TO_CHANGE).isPresent();
        if (!asked) {
            putRecord(new OperationRecord(operationId, this.dimension, draft.module(), draft.tool(), draft.params(), draft.planHash(), CoreInfo.version(),
                    owner, pending.finished, status, undoAvailable, filing != null ? filing.hashes() : List.of(), draft.sideEffects(), List.of(), List.of(),
                    List.of(), draft.palettes()));
        }
        Optional<String> code = filing == null && changed > 0 ? Optional.of(UNDO_NOT_RECORDED) : code(Kind.APPLY, job);
        return new Result(pending.handle, owner, Kind.APPLY, id, Optional.of(job), 0, code, List.of());
    }

    private Result finishUndo(String owner, Pending pending) {
        ApplyJob job = pending.job;
        UndoStore.Entry entry = pending.entry;
        PartsFiling filing = filingResult(pending, PartsFiling.class);
        if (filing == null) {
            return new Result(pending.handle, owner, Kind.UNDO, Optional.of(entry.id()), Optional.of(job), 0, Optional.of(UNDO_NOT_RECORDED), List.of());
        }
        UUID part = moveAfter(owner, entry, filing, stack(this.undo, owner), stack(this.redo, owner), OperationRecord.Status.UNDONE, pending.finished);
        filed(pending, entry, part);
        if (filing.done() > 0 || filing.remaining() == 0) {
            updateStatus(entry.id(), filing.remaining() == 0 ? OperationRecord.Status.UNDONE : OperationRecord.Status.PARTLY_UNDONE);
        }
        return new Result(pending.handle, owner, Kind.UNDO, Optional.of(entry.id()), Optional.of(job), filing.kept(), code(Kind.UNDO, job), List.of());
    }

    private Result finishRedo(String owner, Pending pending) {
        ApplyJob job = pending.job;
        UndoStore.Entry entry = pending.entry;
        PartsFiling filing = filingResult(pending, PartsFiling.class);
        if (filing == null) {
            return new Result(pending.handle, owner, Kind.REDO, Optional.of(entry.id()), Optional.of(job), 0, Optional.of(UNDO_NOT_RECORDED), List.of());
        }
        UUID part = moveAfter(owner, entry, filing, stack(this.redo, owner), stack(this.undo, owner), OperationRecord.Status.APPLIED, pending.finished);
        filed(pending, entry, part);
        if (filing.done() > 0 || filing.remaining() == 0) {
            updateStatus(entry.id(), filing.remaining() == 0 ? OperationRecord.Status.APPLIED : OperationRecord.Status.PARTLY_UNDONE);
        }
        return new Result(pending.handle, owner, Kind.REDO, Optional.of(entry.id()), Optional.of(job), 0, code(Kind.REDO, job), List.of());
    }

    /** Tells the journal an undo or redo is filed (F-2+): its blocks are under {@code entry}, and {@code part} if it split. */
    private void filed(Pending pending, UndoStore.Entry entry, UUID part) {
        if (pending.run != null) {
            pending.run.filed(part == null ? List.of(entry.id()) : List.of(entry.id(), part));
        }
    }

    private void updateStatus(UUID id, OperationRecord.Status status) {
        OperationRecord record = this.records.get(id);
        if (record != null) {
            putRecord(record.withStatus(status));
        }
    }

    /**
     * Files an undo or redo: the done part moves from {@code from} to {@code to}, the remaining part stays on
     * {@code from}. When everything is done, the same entry moves (re-recorded if some blocks were dropped). A part
     * split off gets its own record, with {@code partStatus}, the time {@code time} and no side effects (they stay with
     * the original). Returns the part's id, or null if nothing split off.
     */
    private UUID moveAfter(String owner, UndoStore.Entry entry, PartsFiling filing, Deque<UndoStore.Entry> from, Deque<UndoStore.Entry> to,
            OperationRecord.Status partStatus, Instant time) {
        if (!this.store.contains(entry)) {
            from.remove(entry); // dropped while it ran
            return null;
        }
        if (filing.remaining() == 0) {
            from.remove(entry);
            if (filing.done() == 0) {
                drop(entry);
            } else {
                if (filing.replaceWith() != null) {
                    this.store.replace(entry, filing.replaceWith());
                }
                if (this.store.contains(entry)) {
                    to.push(entry);
                }
            }
        } else if (filing.done() > 0) {
            this.store.replace(entry, filing.replaceWith());
            UndoStore.Entry part = this.store.add(owner, filing.part());
            if (this.store.contains(part)) {
                to.push(part);
            }
            OperationRecord original = this.records.get(entry.id());
            if (original != null) {
                putRecord(new OperationRecord(part.id(), original.dimension(), original.module(), original.tool(), original.params(), original.planHash(),
                        original.coreVersion(), owner, time, partStatus, this.store.contains(part), filing.partHashes(), List.of(), List.of(), List.of(),
                        List.of(), original.palettes()));
            }
            return part.id();
        }
        return null;
    }

    private static Optional<String> code(Kind kind, ApplyJob job) {
        if (job.status() != ApplyJob.Status.FAILED) {
            return Optional.empty();
        }
        ApplyJob.Failure failure = job.failureCode().orElse(ApplyJob.Failure.STOPPED);
        return Optional.of(kind == Kind.REDO && failure == ApplyJob.Failure.CONFLICT ? "REDO_CONFLICT" : failure.name());
    }

    private Deque<UndoStore.Entry> stack(Map<String, Deque<UndoStore.Entry>> stacks, String owner) {
        return stacks.computeIfAbsent(owner, o -> new ArrayDeque<>());
    }

    /** Removes {@code entry} from the store: it can no longer be undone or redone, and its record says so. */
    private void drop(UndoStore.Entry entry) {
        this.store.remove(entry);
        forget(entry);
    }

    private void putRecord(OperationRecord record) {
        this.records.put(record.id(), record);
        this.unsavedRecords.add(record.id());
    }

    /** The cap dropped an operation: it can no longer be undone or redone, and its record says so. */
    private void forget(UndoStore.Entry entry) {
        boolean known = stack(this.undo, entry.owner()).remove(entry);
        known |= stack(this.redo, entry.owner()).remove(entry);
        if (known) {
            this.changes++;
        }
        OperationRecord record = this.records.get(entry.id());
        if (record != null) {
            putRecord(record.withUndoAvailable(false));
        }
    }

    // --- F-2b: the server's stop ---

    /**
     * The server is stopping (F-2b): stops every job ({@link ApplyScheduler#abortAll}: what was placed can still be
     * undone); requests still being prepared end {@code STOPPED} with nothing placed; the rest are filed now, on this
     * thread, waiting for work the helper has already started, so the world's last save holds every placed block's
     * undo. Returns the results complete now (one still waiting for its side effects stays in flight).
     */
    public List<Result> stop(WorldWriter world) {
        this.stopping = true;
        this.scheduler.abortAll(world);
        List<Result> results = new ArrayList<>();
        for (Iterator<Pending> it = this.pending.values().iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.handle.stage == Stage.PREPARING) {
                pending.preparing.cancel(); // never placed: whatever it made is dropped
                it.remove();
                results.add(ended(pending, STOPPED));
            } else if (pending.handle.stage == Stage.FILING) {
                pending.filing.finishHere(STOP_WAIT_SECONDS);
                if (!pending.filing.isDone()) {
                    this.log.accept("[stratos] filing " + pending.handle + " didn't finish while the server stopped");
                }
            }
        }
        results.addAll(afterTick()); // placing ones are filed here, on this thread
        return results;
    }

    /** The record of operation {@code id}, if it ran here. */
    public Optional<OperationRecord> record(UUID id) {
        return Optional.ofNullable(this.records.get(id));
    }

    /** Every record known here, oldest first: those made this session, and those of operations still in a history. */
    public List<OperationRecord> records() {
        return List.copyOf(this.records.values());
    }

    // --- F-5: saved with the world ---

    /** Whether anything changed since {@link #markSaved}: a history, or a record. */
    public boolean hasUnsavedChanges() {
        return this.changes != this.savedChanges || !this.unsavedRecords.isEmpty();
    }

    /** The records changed since they were last saved. */
    public List<OperationRecord> unsavedRecords() {
        List<OperationRecord> unsaved = new ArrayList<>();
        this.unsavedRecords.forEach(id -> unsaved.add(this.records.get(id)));
        return unsaved;
    }

    /** {@code saved} were written: each is saved unless it changed again meanwhile. */
    public void recordsSaved(List<OperationRecord> saved) {
        for (OperationRecord record : saved) {
            if (record.equals(this.records.get(record.id()))) {
                this.unsavedRecords.remove(record.id());
            }
        }
    }

    /** How many times a history changed: a save notes it, and once written says so ({@link #markSaved}). */
    public long changeCount() {
        return this.changes;
    }

    /**
     * The histories as they were at change count {@code version} are saved (F-2b: a save is written by the helper, and
     * filed once it is done; changes since stay unsaved).
     */
    public void markSaved(long version) {
        this.savedChanges = StrictMath.max(this.savedChanges, version);
    }

    /**
     * Every player's history here with anything in it, sorted by player, for the undo index. A request still in flight
     * isn't in it yet: the next save after it is filed saves it (F-2b).
     */
    public List<UndoIndex.History> histories() {
        Set<String> owners = new TreeSet<>(this.undo.keySet());
        owners.addAll(this.redo.keySet());
        List<UndoIndex.History> histories = new ArrayList<>();
        for (String owner : owners) {
            List<UUID> undoIds = stack(this.undo, owner).stream().map(UndoStore.Entry::id).toList();
            List<UUID> redoIds = stack(this.redo, owner).stream().map(UndoStore.Entry::id).toList();
            if (!undoIds.isEmpty() || !redoIds.isEmpty()) {
                histories.add(new UndoIndex.History(this.dimension, owner, undoIds, redoIds));
            }
        }
        return histories;
    }

    /**
     * Brings back the saved histories of this dimension and the records of their operations (F-5), after the store was
     * restored. An id the store doesn't hold any more is left out. Call before any request.
     */
    public void restore(List<UndoIndex.History> histories, Map<UUID, OperationRecord> savedRecords) {
        for (UndoIndex.History history : histories) {
            if (!history.dimension().equals(this.dimension)) {
                continue;
            }
            for (UUID id : history.undo()) {
                this.store.entry(id).ifPresent(stack(this.undo, history.owner())::addLast);
            }
            for (UUID id : history.redo()) {
                this.store.entry(id).ifPresent(stack(this.redo, history.owner())::addLast);
            }
        }
        savedRecords.forEach((id, record) -> {
            if (record.dimension().equals(this.dimension)) {
                this.records.put(id, record);
            }
        });
    }

    /**
     * {@code owner}'s operations here that they can still undo or redo, in the order they were made, with their records
     * (F-5: {@code YourRoads}). Operations without a record are left out.
     */
    public List<HistoryEntry> history(String owner) {
        List<HistoryEntry> entries = new ArrayList<>();
        List<UndoStore.Entry> undoable = new ArrayList<>(stack(this.undo, owner));
        Collections.reverse(undoable); // oldest first
        for (UndoStore.Entry entry : undoable) {
            OperationRecord record = this.records.get(entry.id());
            if (record != null) {
                entries.add(new HistoryEntry(record, false));
            }
        }
        for (UndoStore.Entry entry : stack(this.redo, owner)) { // the top was undone last, so it was made first
            OperationRecord record = this.records.get(entry.id());
            if (record != null) {
                entries.add(new HistoryEntry(record, true));
            }
        }
        return entries;
    }

    /** How many operations {@code owner} can undo. */
    public int undoDepth(String owner) {
        return stack(this.undo, owner).size();
    }

    /** How many undone operations {@code owner} can redo. */
    public int redoDepth(String owner) {
        return stack(this.redo, owner).size();
    }

    /** Whether {@code owner} has a request running. */
    public boolean isBusy(String owner) {
        return this.pending.containsKey(owner);
    }
}
