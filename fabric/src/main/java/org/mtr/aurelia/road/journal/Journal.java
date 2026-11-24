/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.JobJournal;
import org.mtr.aurelia.road.spi.Helper;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.spi.Storage;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * One world's journal (world-data-format §5; task F-2+): the running log on disk of every apply, undo and redo (a
 * <b>run</b>), so a crash can be repaired when the world opens again ({@link Recovery}).
 *
 * <p><b>Writing.</b> The server thread queues records ({@link #begin}, then the {@link Run}'s methods); the journal
 * thread ({@link Helper#journal}, the platform's "Stratos journal") encodes them, compressing pre-images, appends
 * everything queued to the open segment and flushes it to disk once for the lot, then marks those records on disk.
 * Every record has a ticket; a job writes a section only once its pre-image's ticket is on disk ({@link JobJournal}).
 * Nothing is written on the server thread while the game runs (F-2b). If writing fails (a full disk), the journal says
 * so ({@link #failed}) until the next run begins, which starts a new segment and tries again.
 *
 * <p><b>Segments</b>, {@code operations/journal/<8 digits>.log}: each world session starts a new one, as does each world
 * save ({@link #worldSaved}) and every 64 MB. Segments are deleted oldest first, once every run with a record in them
 * is retired and that is on disk, so a run's records are never left without its first ones.
 *
 * <p><b>Retiring</b> (owner, 2026-10-09: an edit is kept only until it is safely saved): a run is retired once the saved
 * undo index lists it ({@link #listable}) or a repair rolled it back, <b>and</b> Minecraft has surely written its
 * chunks. That is at the second world save starting at least {@link #CHUNK_SAVE_WAIT} after the run ended or was
 * repaired: the first of those saves wrote its chunks, as Minecraft saves a changed chunk at most 10 s after its last
 * save. On a clean stop the whole journal goes ({@link #stopped}).
 *
 * <p><b>Threads:</b> {@link #begin}, the {@link Run}s' methods but {@link Run#onDisk} and {@link Run#failed}, and the
 * run bookkeeping are server thread only; {@link #awaitOnDisk} may be called on any thread. The {@link Storage} is used
 * by the journal thread only, but for {@link #stopped}.
 */
public final class Journal {
    public static final String FOLDER = "operations/journal";
    /** Minecraft's longest wait between two saves of a changed chunk (1.20.1's {@code ChunkMap}: 10 s). */
    public static final Duration CHUNK_SAVE_WAIT = Duration.ofSeconds(10);
    /** A new segment starts once the open one is past this size. */
    static final long SEGMENT_BYTES = 64L << 20;
    /** Records are appended in pieces of at most this size. */
    private static final int APPEND_BYTES = 4 << 20;

    /** One queued record with its ticket, or a request to start a new segment ({@code record} null). */
    private record Item(long ticket, JournalRecord record) {
    }

    /** What the server thread keeps about a run not yet retired. */
    private static final class RunState {
        final UUID run;
        Instant settled; // when its job ended, or a repair dealt with it; null before
        boolean filed; // Operations filed it: its blocks are in the histories
        List<UUID> entries = List.of(); // the undo store entries its filing made or moved
        boolean listed; // a saved index listed it (sticky)
        boolean rolledBack; // a repair rolled it back: never listed
        boolean repairing; // a repair is still dealing with it

        RunState(UUID run) {
            this.run = run;
        }
    }

    private final Storage storage;
    private final Helper helper;
    private final Supplier<Instant> clock;
    private final Consumer<String> log;
    private final ConcurrentLinkedQueue<Item> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final Object onDiskLock = new Object();
    private final Object appendLock = new Object(); // tickets and the queue's order; never held while writing
    private final Object writeLock = new Object(); // the journal thread's state, while it writes
    private long lastTicket; // under appendLock
    private volatile long onDisk;
    private volatile IOException failure;
    private volatile int epoch; // one more after each failure: a run begun before it can't trust its tickets
    private volatile int segmentsDeleted;
    private volatile long bytesWritten;
    private volatile int flushes;
    // Journal thread only (and the stopped server's thread).
    private Storage.Log open;
    private int openNumber = -1;
    private long openBytes;
    private boolean unflushed; // appended to the open segment since its last flush
    private int nextNumber;
    private final SortedMap<Integer, Set<UUID>> segmentRuns = new TreeMap<>();
    private final Set<UUID> retiredOnDisk = new HashSet<>();
    // Server thread only.
    private final Map<UUID, RunState> runs = new LinkedHashMap<>();
    private Instant lastWorldSave;
    private Instant previousWorldSave;
    private boolean failureLogged;

    /** A journal that continues after {@code scan} (the journal found on disk), in a new segment. */
    public Journal(Storage storage, Helper helper, Supplier<Instant> clock, Consumer<String> log, JournalScan scan) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.helper = Objects.requireNonNull(helper, "helper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        this.nextNumber = scan.lastSegment() + 1;
        scan.segments().forEach((number, runs) -> this.segmentRuns.put(number, new LinkedHashSet<>(runs)));
        for (RunLog run : scan.runs()) {
            if (run.retired()) {
                this.retiredOnDisk.add(run.run());
            }
        }
    }

    /** The path of segment {@code number}. */
    static String path(int number) {
        return FOLDER + "/" + String.format(java.util.Locale.ROOT, "%08d", number) + ".log";
    }

    // --- Writing ---

    /** Queues {@code record}; returns its ticket. Server thread (or any thread holding no other journal call). */
    private long append(JournalRecord record) {
        long ticket;
        synchronized (this.appendLock) {
            ticket = ++this.lastTicket;
            this.queue.add(new Item(ticket, record));
        }
        schedule();
        return ticket;
    }

    /** Has the journal thread drain the queue, unless a drain is already queued or running. */
    private void schedule() {
        if (this.draining.compareAndSet(false, true)) {
            try {
                this.helper.journal(this::drainAll);
            } catch (RejectedExecutionException e) {
                drainAll(); // the helper has stopped: the server's stop, so this thread writes
            }
        }
    }

    /** Journal thread: drains until the queue stays empty. */
    private void drainAll() {
        while (true) {
            try {
                drainOnce();
            } catch (RuntimeException | Error e) {
                this.failure = new IOException("the journal thread failed", e); // never left draining for ever
                this.epoch++;
                this.draining.set(false);
                synchronized (this.onDiskLock) {
                    this.onDiskLock.notifyAll();
                }
                throw e;
            }
            this.draining.set(false);
            if (this.queue.isEmpty() || !this.draining.compareAndSet(false, true)) {
                return;
            }
        }
    }

    /** Journal thread: appends everything queued now, flushes once, and marks it on disk. */
    private void drainOnce() {
        synchronized (this.writeLock) {
            writeQueued();
        }
    }

    private void writeQueued() {
        long last = 0;
        List<UUID> retired = new ArrayList<>();
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        Item item;
        while ((item = this.queue.poll()) != null) {
            last = item.ticket();
            if (this.failure != null) {
                continue; // dropped: nothing waiting on them may go ahead
            }
            try {
                if (item.record() == null) {
                    finishSegment(pending); // a new segment starts with the next record
                    continue;
                }
                byte[] bytes = JournalCodec.encode(item.record());
                if (this.open == null) {
                    openNext();
                }
                pending.write(bytes, 0, bytes.length);
                this.segmentRuns.computeIfAbsent(this.openNumber, n -> new LinkedHashSet<>()).add(item.record().run());
                if (item.record() instanceof JournalRecord.Retired r) {
                    retired.add(r.run());
                }
                if (pending.size() >= APPEND_BYTES) {
                    appendPending(pending);
                }
            } catch (IOException | RuntimeException e) {
                fail(e instanceof IOException io ? io : new IOException(e));
            }
        }
        if (this.failure == null) {
            try {
                appendPending(pending);
                flushOpen();
                if (this.openBytes >= SEGMENT_BYTES) {
                    closeOpen();
                }
            } catch (IOException | RuntimeException e) {
                fail(e instanceof IOException io ? io : new IOException(e));
            }
        }
        if (this.failure == null) {
            this.retiredOnDisk.addAll(retired);
            if (!retired.isEmpty()) {
                deleteRetiredSegments();
            }
        }
        if (last != 0) {
            synchronized (this.onDiskLock) {
                if (this.failure == null) {
                    this.onDisk = StrictMath.max(this.onDisk, last);
                }
                this.onDiskLock.notifyAll();
            }
        }
    }

    private void appendPending(ByteArrayOutputStream pending) throws IOException {
        if (pending.size() == 0) {
            return;
        }
        byte[] bytes = pending.toByteArray();
        this.open.append(bytes);
        this.openBytes += bytes.length;
        this.bytesWritten += bytes.length;
        this.unflushed = true;
        pending.reset();
    }

    /** Puts what was appended to the open segment on disk, if anything was. */
    private void flushOpen() throws IOException {
        if (this.open != null && this.unflushed) {
            this.open.flush();
            this.unflushed = false;
            this.flushes++;
        }
    }

    /** Ends the open segment: what is pending appended and flushed, then closed. */
    private void finishSegment(ByteArrayOutputStream pending) throws IOException {
        appendPending(pending);
        flushOpen();
        closeOpen();
    }

    /** Opens the next segment, skipping numbers taken already (a journal that couldn't be listed when the world opened). */
    private void openNext() throws IOException {
        for (int tries = 0; ; tries++) {
            int number = this.nextNumber++;
            try {
                this.open = this.storage.openLog(path(number));
            } catch (java.nio.file.FileAlreadyExistsException e) {
                if (tries >= 1000) {
                    throw e;
                }
                continue;
            }
            this.openNumber = number;
            this.openBytes = 0;
            return;
        }
    }

    private void closeOpen() throws IOException {
        if (this.open != null) {
            Storage.Log closing = this.open;
            this.open = null;
            this.unflushed = false;
            closing.close();
        }
    }

    /** Journal thread: deletes segments oldest first while every run in them is retired on disk. */
    private void deleteRetiredSegments() {
        while (!this.segmentRuns.isEmpty()) {
            int first = this.segmentRuns.firstKey();
            if (first == this.openNumber && this.open != null || !this.retiredOnDisk.containsAll(this.segmentRuns.get(first))) {
                return;
            }
            try {
                this.storage.delete(path(first));
            } catch (IOException e) {
                return; // tried again after the next retiring
            }
            this.segmentRuns.remove(first);
            this.segmentsDeleted++;
            Set<UUID> stillThere = new HashSet<>();
            this.segmentRuns.values().forEach(stillThere::addAll);
            this.retiredOnDisk.retainAll(stillThere);
        }
    }

    private void fail(IOException e) {
        this.failure = e;
        this.epoch++;
        try {
            closeOpen();
        } catch (IOException ignored) {
            // already failing
        }
    }

    /** Whether the record with {@code ticket}, and every one before it, is on disk. Any thread. */
    public boolean isOnDisk(long ticket) {
        return this.onDisk >= ticket;
    }

    /** Whether writing failed (a full disk), since the last run began. Any thread. */
    public boolean failed() {
        return this.failure != null;
    }

    /**
     * Waits up to {@code millis} until the record with {@code ticket} is on disk; whether it is. Any thread: a save's
     * writing on the helper (its index may only list runs whose records are all on disk), and the server's stop.
     */
    public boolean awaitOnDisk(long ticket, long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        synchronized (this.onDiskLock) {
            while (this.onDisk < ticket && this.failure == null) {
                long left = (deadline - System.nanoTime()) / 1_000_000L;
                if (left <= 0) {
                    return false;
                }
                try {
                    this.onDiskLock.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return this.onDisk >= ticket;
        }
    }

    /** The ticket of the last record queued. */
    public long lastTicket() {
        synchronized (this.appendLock) {
            return this.lastTicket;
        }
    }

    // --- Runs ---

    /**
     * Begins a run (queues its {@code BEGIN}): an apply, undo or redo of {@code operation} in {@code dimension}, by
     * {@code owner}. If writing failed before, a new segment is tried first.
     */
    public Run begin(Operations.Kind kind, UUID operation, String dimension, String owner, String planHash) {
        if (this.failure != null) {
            logFailure();
            synchronized (this.appendLock) {
                this.failure = null; // a new run tries again, in a new segment
                this.queue.add(new Item(++this.lastTicket, null));
            }
            this.failureLogged = false;
        }
        UUID id = UUID.randomUUID();
        this.runs.put(id, new RunState(id));
        Run run = new Run(id, this.epoch);
        append(new JournalRecord.Begin(id, kind, operation, dimension, owner, planHash, this.clock.get().toEpochMilli()));
        return run;
    }

    private void logFailure() {
        if (!this.failureLogged && this.failure != null) {
            this.failureLogged = true;
            this.log.accept("[stratos] the journal couldn't be written, so edits stop until it can: " + this.failure);
        }
    }

    /** One run's records (F-2+): the job's {@link JobJournal}, and what {@code Operations} tells the journal about it. */
    public final class Run implements JobJournal {
        private final UUID id;
        private final int epoch;

        private Run(UUID id, int epoch) {
            this.id = id;
            this.epoch = epoch;
        }

        public UUID id() {
            return this.id;
        }

        @Override
        public long preimage(BlockPlan.SectionPos section, int dataVersion, List<BlockChange> changes) {
            return append(new JournalRecord.Preimage(this.id, section, dataVersion, changes));
        }

        @Override
        public boolean onDisk(long ticket) {
            // Read the epoch after onDisk: a failure since the run began means its records may be lost.
            boolean onDisk = isOnDisk(ticket);
            return onDisk && this.epoch == Journal.this.epoch;
        }

        @Override
        public boolean failed() {
            boolean failed = Journal.this.failed() || this.epoch != Journal.this.epoch;
            if (failed) {
                logFailure();
            }
            return failed;
        }

        @Override
        public void sectionDone(BlockPlan.SectionPos section, long[] written) {
            append(new JournalRecord.SectionDone(this.id, section, written));
        }

        /** The run's job ended: how, and how many blocks it wrote. */
        public void end(ApplyJob job) {
            JournalRecord.EndStatus status = job.status() == ApplyJob.Status.DONE ? JournalRecord.EndStatus.DONE
                    : job.failureCode().filter(ApplyJob.Failure.STOPPED::equals).isPresent() ? JournalRecord.EndStatus.STOPPED : JournalRecord.EndStatus.FAILED;
            append(new JournalRecord.End(this.id, status, job.failureCode().map(Enum::name).orElse(""), job.changedBlocks()));
            RunState state = Journal.this.runs.get(this.id);
            if (state != null) {
                state.settled = Journal.this.clock.get();
            }
        }

        /**
         * The run is filed into the histories, its blocks recorded under {@code entries} (the undo store entries it made
         * or moved; none if it changed nothing), so a save may list it once those are on disk.
         */
        public void filed(List<UUID> entries) {
            RunState state = Journal.this.runs.get(this.id);
            if (state != null) {
                state.filed = true;
                state.entries = List.copyOf(entries);
            }
        }

        /** Before the side-effect writer is asked to make or remove {@code effects}. */
        public void track(JournalRecord.TrackAction action, List<SideEffect> effects) {
            append(new JournalRecord.Track(this.id, action, effects));
        }

        /** The side-effect writer's answers. */
        public void trackDone(JournalRecord.TrackAction action, List<SideEffect> effects, List<SideEffectWriter.Outcome> outcomes) {
            append(new JournalRecord.TrackDone(this.id, action, effects, outcomes));
        }
    }

    // --- After a crash ---

    /**
     * The runs a repair is about to deal with (server thread, once, before any new run): each is noted as listed if
     * the saved index lists it, so later saves keep listing it until it is retired.
     */
    public void repairing(Collection<RunLog> unretired, Set<UUID> listed) {
        for (RunLog log : unretired) {
            RunState state = new RunState(log.run());
            state.listed = listed.contains(log.run());
            state.repairing = true;
            this.runs.put(log.run(), state);
        }
    }

    /** A repair dealt with {@code run}: rolled back (never to be listed) or completed. Queues its {@code RECOVERED}. */
    public void recovered(UUID run, JournalRecord.Outcome outcome, String detail, boolean rolledBack) {
        append(new JournalRecord.Recovered(run, outcome, detail));
        RunState state = this.runs.get(run);
        if (state != null) {
            state.repairing = false;
            state.rolledBack = rolledBack;
            state.settled = this.clock.get();
        }
    }

    /**
     * The runs a save may list now (server thread, for its snapshot): those listed before, and those filed whose entries
     * are none of {@code waiting} (operations whose images are still being written, F-2b).
     */
    public List<UUID> listable(Collection<UUID> waiting) {
        Set<UUID> notYet = new HashSet<>(waiting);
        List<UUID> listable = new ArrayList<>();
        for (RunState state : this.runs.values()) {
            if (state.rolledBack) {
                continue;
            }
            if (state.listed || state.filed && state.entries.stream().noneMatch(notYet::contains)) {
                listable.add(state.run);
            }
        }
        return listable;
    }

    /** A world save is starting (server thread): a new segment starts, and the save is noted for retiring. */
    public void worldSaved() {
        this.previousWorldSave = this.lastWorldSave;
        this.lastWorldSave = this.clock.get();
        append(null);
    }

    /**
     * The world save before the latest one started at this time (null if none yet): the time a save just taken can
     * retire runs by ({@link #saved}).
     */
    public Instant previousWorldSave() {
        return this.previousWorldSave;
    }

    /**
     * A save was written (server thread): the runs it {@code listed} stay listed. If it was a world save, whose previous
     * world save began at {@code retireBy} (null: none), every run listed (or rolled back) that settled at least
     * {@link #CHUNK_SAVE_WAIT} before that is retired. Returns the runs retired.
     */
    public List<UUID> saved(Collection<UUID> listed, boolean worldSave, Instant retireBy) {
        for (UUID run : listed) {
            RunState state = this.runs.get(run);
            if (state != null) {
                state.listed = true;
            }
        }
        List<UUID> retired = new ArrayList<>();
        if (!worldSave || retireBy == null) {
            return retired;
        }
        for (RunState state : List.copyOf(this.runs.values())) {
            boolean safe = (state.listed && listed.contains(state.run)) || state.rolledBack;
            if (safe && !state.repairing && state.settled != null && !retireBy.isBefore(state.settled.plus(CHUNK_SAVE_WAIT))) {
                append(new JournalRecord.Retired(state.run));
                this.runs.remove(state.run);
                retired.add(state.run);
            }
        }
        return retired;
    }

    /** Whether the journal keeps any run not yet retired (server thread): {@code stratos_meta} stays dirty meanwhile. */
    public boolean keepsRuns() {
        return !this.runs.isEmpty();
    }

    /** How many runs are not yet retired. */
    public int runsKept() {
        return this.runs.size();
    }

    /**
     * The server has stopped (server thread; F-2+): Minecraft has written every chunk and the last save is done. If
     * {@code lastSaveWritten} and every run is listed or rolled back (none still being repaired), the whole journal is
     * deleted; otherwise it is kept for the next start. Either way the open segment is closed.
     */
    public void stopped(boolean lastSaveWritten) {
        awaitOnDisk(lastTicket(), 120_000);
        boolean clean = lastSaveWritten && this.failure == null;
        for (RunState state : this.runs.values()) {
            if (state.repairing || state.settled == null || !(state.listed || state.rolledBack)) {
                clean = false;
            }
        }
        synchronized (this.writeLock) {
            try {
                closeOpen();
                if (clean) {
                    this.storage.delete(FOLDER);
                    this.segmentRuns.clear();
                }
            } catch (IOException e) {
                this.log.accept("[stratos] couldn't delete the journal after a clean stop; the next start retires it: " + e);
            }
        }
        if (!clean && !this.runs.isEmpty()) {
            this.log.accept("[stratos] the journal is kept: " + this.runs.size() + " runs aren't safely saved yet");
        }
    }

    // --- Numbers, for S12 and the tests ---

    /** Bytes appended this session. */
    public long bytesWritten() {
        return this.bytesWritten;
    }

    /** Flushes to disk this session. */
    public int flushes() {
        return this.flushes;
    }

    /** Segments deleted this session. */
    public int segmentsDeleted() {
        return this.segmentsDeleted;
    }
}
