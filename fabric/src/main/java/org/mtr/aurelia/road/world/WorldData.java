/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.journal.Journal;
import org.mtr.aurelia.road.journal.JournalScan;
import org.mtr.aurelia.road.journal.Recovery;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.spi.Helper;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.spi.Storage;
import org.mtr.aurelia.road.undo.HelperTask;
import org.mtr.aurelia.road.undo.OperationJson;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.UndoIndex;
import org.mtr.aurelia.road.undo.UndoIndexJson;
import org.mtr.aurelia.road.undo.UndoStore;

/**
 * One world's Stratos data under {@code <world>/stratos/} (world-data-format; task F-5): its {@code meta.json}, the
 * operation records, the undo images and every player's undo history, per dimension.
 * <ul>
 *   <li><b>Loading</b> ({@link #load}): the downgrade guard first (§8): data newer than this Stratos ({@code formatVersion}
 *       or DataVersion), or a {@code meta.json} that can't be read, makes the world <b>read-only</b>, and nothing more is
 *       read or written. Older data is migrated (§7). Then {@code tmp/} is emptied, the undo index and the records of
 *       the operations in it are read, and image files no index names are deleted.</li>
 *   <li><b>Saving</b> ({@link #save}), with the world's own saves: the changed records, the images not yet on disk, the
 *       undo index, then {@code meta.json} last. A world nobody edited gets no files at all.</li>
 *   <li><b>From R-9, palette snapshots</b> ({@value #PALETTES}): before the records, the snapshot of every palette a record
 *       to write names that isn't on disk yet ({@link #addPalettes} tells it the server's); never deleted. They are read
 *       on load ({@link #paletteSnapshots}), so a road keeps the exact palette it was built with.</li>
 *   <li>Data that can't be read is never guessed at: an unreadable index starts an empty history and keeps the files;
 *       an unreadable record leaves its operation without one. Each is logged.</li>
 *   <li><b>From F-2b</b> nothing is written on the server thread while the game runs:
 *       <ul>
 *         <li>each operation's undo images are written to disk behind, by the helper, once the player has their answer
 *             ({@link UndoStore});</li>
 *         <li>a save takes a snapshot on the server thread (the changed records, the operations on disk, the histories)
 *             and the helper writes it, in the same order as before; {@link #afterTick} files it once written;</li>
 *         <li>an operation whose images are still being written, or an edit still being filed, is left out of that save
 *             and saved by the next one, as an edit made just after a save is. The index never names a file that isn't
 *             on disk.</li>
 *         <li>When the server stops ({@link #stop}), saves and writes in flight are finished and the last save runs on
 *             the server thread, complete.</li>
 *       </ul></li>
 *   <li><b>From F-2+, the journal</b> ({@link Journal}; world-data-format §5): every edit is a run in it, written by the
 *       journal thread. On loading, after the index, the journal is read and the repair planned ({@link Recovery}):
 *       runs the saved index lists are completed, the others rolled back. The platform starts it once the server has
 *       started ({@link #startRepair}); until it is done every request is refused with {@code REPAIRING}. Each save's
 *       index lists the runs whose results it holds (once the journal has them on disk); each world save
 *       ({@link #worldSave}) starts a new segment and retires the runs safely saved. On a clean stop the journal goes
 *       ({@link #stopped}). A read-only world has no journal at all.</li>
 * </ul>
 * Server thread only.
 */
public final class WorldData {
    public static final String META = "meta.json";
    public static final String INDEX = "undo/index.json";
    public static final String RECORDS = "operations/records";
    /** R-9: a snapshot of every palette a record names, {@code palettes/<snapshot id>.json} (world-data-format §2). */
    public static final String PALETTES = "palettes";

    /** Why a world's Stratos data is read-only. */
    public enum ReadOnly {
        /** Saved by a Stratos with a newer {@code formatVersion}. */
        NEWER_FORMAT,
        /** Saved by a newer game (a higher DataVersion): its block states can't be read safely. */
        NEWER_DATA_VERSION,
        /** {@code meta.json} can't be read. */
        UNREADABLE,
        /** Older data couldn't be migrated; it is left as it was. */
        MIGRATION_FAILED
    }

    /**
     * How the world's operations are made, and who writes the files.
     *
     * @param memoryBudget  the undo images' memory budget, in bytes
     * @param maxOperations the undo cap's number of operations
     * @param maxBytes      the undo cap's bytes
     * @param ids           new operation (and world) ids
     * @param writer        the side-effect writer
     * @param clock         the time, for records and {@code meta.json}
     * @param dataVersion   the running game's DataVersion
     * @param writtenBy     this Stratos, such as {@code stratos 0.1.0+mc1.20.1}
     * @param helper        where the work that grows with an edit, and the writing, run (F-2b: the "Stratos helper" thread)
     */
    public record Settings(long memoryBudget, int maxOperations, long maxBytes, Supplier<UUID> ids, SideEffectWriter writer, Supplier<Instant> clock,
            int dataVersion, String writtenBy, Helper helper) {
        public Settings {
            Objects.requireNonNull(ids, "ids");
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(writtenBy, "writtenBy");
            Objects.requireNonNull(helper, "helper");
        }

        /** Settings that do all their work and writing on the server thread ({@link Helper#AT_ONCE}): for tests. */
        public Settings(long memoryBudget, int maxOperations, long maxBytes, Supplier<UUID> ids, SideEffectWriter writer, Supplier<Instant> clock,
                int dataVersion, String writtenBy) {
            this(memoryBudget, maxOperations, maxBytes, ids, writer, clock, dataVersion, writtenBy, Helper.AT_ONCE);
        }
    }

    /** How long the server's stop waits for writing the helper has already started. */
    private static final long STOP_WAIT_SECONDS = 120;

    /**
     * A save the helper is writing (F-2b): the records and history versions it covers, whether it named every operation,
     * and the folders it deletes, to file once it is done. Its task answers the folders it couldn't delete. From F-2+
     * also the journal runs its index lists, and whether it was a world save that may retire runs, by when.
     */
    private record SaveRun(HelperTask<List<String>> task, Map<Operations, List<OperationRecord>> records, Map<Operations, Long> versions,
            boolean complete, List<String> folders, List<UUID> listed, boolean worldSave, Instant retireBy, List<OperationRecord> loose,
            List<String> palettes) {
    }

    private final Storage storage;
    private final Settings settings;
    private final Consumer<String> log;
    private final UndoStore store;
    private final Deque<SaveRun> saving = new ArrayDeque<>();
    private int lastSaveLeftOut;
    private boolean stopping;
    private final Map<String, Operations> dimensions = new LinkedHashMap<>();
    private List<UndoIndex.History> savedHistories = List.of();
    private Map<UUID, OperationRecord> savedRecords = Map.of();
    private WorldMeta meta;
    private ReadOnly readOnly;
    private boolean changedOnLoad;
    // F-2+: the journal and the repair after a crash.
    private Journal journal; // null in a read-only world
    private Recovery repair; // null when there is nothing to repair
    private boolean repairing; // the repair hasn't finished
    private Recovery.Report report; // once the repair has finished
    private boolean reportTaken;
    private final Map<UUID, OperationRecord> looseRecords = new LinkedHashMap<>(); // records outside any history to rewrite (rolled back)
    private List<UUID> lastListed = List.of(); // the runs the last written index listed
    private boolean lastSaveFailed;
    // R-9: palette snapshots by snapshot id: the world's (read on load) and those the server added; which are on disk.
    private final Map<String, Palette> paletteSnapshots = new java.util.concurrent.ConcurrentHashMap<>(); // the platform may add from any thread
    private final Map<String, Palette> paletteSnapshotsRead = new LinkedHashMap<>();
    private final Set<String> palettesOnDisk = new HashSet<>();
    private final Set<String> palettesMissingLogged = new HashSet<>();

    private WorldData(Storage storage, Settings settings, Consumer<String> log) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
        this.store = new UndoStore(storage, settings.memoryBudget(), settings.maxOperations(), settings.maxBytes(), true, settings.helper());
    }

    /** Loads the world's Stratos data from {@code storage}, migrating it with {@code migrations} if it is older. */
    public static WorldData load(Storage storage, Settings settings, List<Migration> migrations, Consumer<String> log) {
        WorldData data = new WorldData(storage, settings, log);
        data.loadFiles(migrations);
        return data;
    }

    private void loadFiles(List<Migration> migrations) {
        if (this.storage.exists(META)) {
            String text;
            try {
                text = read(META);
            } catch (IOException e) {
                makeReadOnly(ReadOnly.UNREADABLE, "meta.json can't be read (" + e + ")");
                return;
            }
            OptionalInt version = WorldMeta.formatVersionOf(text);
            if (version.isPresent() && version.getAsInt() > CoreInfo.FORMAT_VERSION) {
                makeReadOnly(ReadOnly.NEWER_FORMAT, "it has formatVersion " + version.getAsInt() + ", written by "
                        + WorldMeta.lastWrittenByOf(text).orElse("an unknown Stratos") + "; this Stratos reads " + CoreInfo.FORMAT_VERSION);
                return;
            }
            WorldMeta read;
            try {
                read = WorldMeta.fromJson(text);
            } catch (JsonFormatException e) {
                makeReadOnly(ReadOnly.UNREADABLE, "meta.json can't be read: " + e.getMessage());
                return;
            }
            if (read.dataVersion() > this.settings.dataVersion()) {
                makeReadOnly(ReadOnly.NEWER_DATA_VERSION, "it was written by a game with DataVersion " + read.dataVersion() + "; this game has "
                        + this.settings.dataVersion());
                return;
            }
            if (read.formatVersion() < CoreInfo.FORMAT_VERSION) {
                try {
                    read = Migrations.run(this.storage, read, CoreInfo.FORMAT_VERSION, migrations, this.settings.clock().get(), this.settings.writtenBy(),
                            this.settings.dataVersion());
                    this.log.accept("[stratos] migrated this world's Stratos data to formatVersion " + CoreInfo.FORMAT_VERSION + "; the backup is "
                            + read.migrations().get(read.migrations().size() - 1).backup());
                } catch (IOException | RuntimeException e) {
                    makeReadOnly(ReadOnly.MIGRATION_FAILED, "migrating it from formatVersion " + read.formatVersion() + " failed: " + e);
                    return;
                }
            }
            this.meta = read;
        }
        try {
            this.storage.delete("tmp");
        } catch (IOException e) {
            this.log.accept("[stratos] couldn't empty stratos/tmp: " + e);
        }
        UndoIndex index = UndoIndex.EMPTY;
        boolean indexRead = true;
        if (this.storage.exists(INDEX)) {
            try {
                index = UndoIndexJson.read(read(INDEX));
            } catch (IOException | JsonFormatException e) {
                this.log.accept("[stratos] the undo history can't be read (" + e.getMessage() + "), so it starts empty; its files are kept");
                indexRead = false;
            }
        }
        List<UUID> missing = this.store.restore(index.operations());
        if (!missing.isEmpty()) {
            this.log.accept("[stratos] " + missing.size() + " operations' undo images are missing, so they can't be undone: " + missing);
            this.changedOnLoad = true;
        }
        Map<UUID, OperationRecord> records = new HashMap<>();
        Set<String> dimensionsInIndex = new LinkedHashSet<>();
        for (UndoIndex.History history : index.histories()) {
            dimensionsInIndex.add(history.dimension());
            List<UUID> ids = new ArrayList<>(history.undo());
            ids.addAll(history.redo());
            for (UUID id : ids) {
                String path = RECORDS + "/" + id + ".json";
                try {
                    records.put(id, OperationJson.read(read(path)));
                } catch (IOException | JsonFormatException e) {
                    this.log.accept("[stratos] the record of operation " + id + " can't be read (" + e.getMessage() + ")");
                }
            }
        }
        this.savedHistories = index.histories();
        this.savedRecords = records;
        loadPalettes();
        loadJournal(index, indexRead);
        dimensionsInIndex.forEach(this::operations); // every history is open, so the cap's pruning reaches it
        if (indexRead) {
            try {
                this.store.deleteUnknown();
            } catch (IOException e) {
                this.log.accept("[stratos] couldn't delete undo images no history uses: " + e);
            }
        }
    }

    /**
     * Reads the journal and plans the repair (F-2+): runs the saved index lists are completed, the others rolled back.
     * If the index can't be read, which runs it held is unknown: nothing is repaired (never a guess), and every run kept
     * is reported as needing attention.
     */
    private void loadJournal(UndoIndex index, boolean indexRead) {
        JournalScan scan;
        try {
            scan = JournalScan.read(this.storage);
        } catch (IOException e) {
            // A segment that can't be listed or read: new segments never overwrite one (openLog creates new files only).
            this.log.accept("[stratos] the journal can't be read (" + e + "); nothing is repaired, and its files are kept");
            this.journal = new Journal(this.storage, this.settings.helper(), this.settings.clock(), this.log, JournalScan.empty());
            this.report = new Recovery.Report(0, 0, 1, 0);
            return;
        }
        this.journal = new Journal(this.storage, this.settings.helper(), this.settings.clock(), this.log, scan);
        Set<UUID> listed = Set.copyOf(index.journal());
        this.lastListed = List.copyOf(index.journal());
        if (!indexRead && !scan.unretired().isEmpty()) {
            this.log.accept("[stratos] the undo history can't be read, so " + scan.unretired().size()
                    + " edits a crash may have cut off can't be repaired: which of them were saved is unknown. Their journal is kept");
            this.journal.repairing(scan.unretired(), Set.of()); // never retired: kept for an admin
            this.report = new Recovery.Report(0, 0, scan.unretired().size(), 0);
            return;
        }
        this.repair = Recovery.plan(scan, listed).orElse(null);
        if (this.repair != null) {
            this.repairing = true;
            this.journal.repairing(scan.unretired(), listed);
            for (String problem : this.repair.problems()) {
                this.log.accept("[stratos] " + problem);
            }
            this.log.accept("[stratos] the journal keeps " + scan.unretired().size() + " edits a crash may have cut off: " + this.repair.steps().stream()
                    .filter(step -> step.direction() == Recovery.Direction.ROLL_BACK).count() + " to roll back, " + this.repair.steps().stream()
                    .filter(step -> step.direction() == Recovery.Direction.COMPLETE).count() + " to complete");
        }
    }

    /**
     * Reads the world's palette snapshots (R-9). A file that can't be read, or whose content isn't the snapshot its name
     * says, is logged and left alone: never guessed at.
     */
    private void loadPalettes() {
        List<String> files;
        try {
            files = this.storage.list(PALETTES);
        } catch (IOException e) {
            this.log.accept("[stratos] the palette snapshots can't be listed (" + e + ")");
            return;
        }
        for (String path : files) {
            String name = path.substring(PALETTES.length() + 1);
            if (!name.endsWith(".json") || name.contains("/")) {
                continue;
            }
            String expected = name.substring(0, name.length() - ".json".length());
            try {
                Palette palette = Palette.read(read(path), path);
                if (!palette.snapshotId().equals(expected)) {
                    this.log.accept("[stratos] " + path + " holds the palette snapshot " + palette.snapshotId() + ", not " + expected + "; it is ignored");
                    continue;
                }
                this.paletteSnapshots.put(expected, palette);
                this.paletteSnapshotsRead.put(expected, palette);
                this.palettesOnDisk.add(expected);
            } catch (IOException | PaletteException e) {
                this.log.accept("[stratos] the palette snapshot " + path + " can't be read (" + e.getMessage() + "); it is ignored");
            }
        }
    }

    /**
     * Palettes this world's records may name (R-9: the server's current ones), so a save can write the snapshot of each one
     * a record names. A snapshot already known is kept as it is.
     */
    public void addPalettes(Iterable<Palette> palettes) {
        for (Palette palette : palettes) {
            this.paletteSnapshots.putIfAbsent(palette.snapshotId(), palette);
        }
    }

    /** The palette snapshots this world kept on disk (R-9), read when it loaded: the server plans older roads as placed with them. */
    public List<Palette> paletteSnapshots() {
        return List.copyOf(this.paletteSnapshotsRead.values());
    }

    private String read(String path) throws IOException {
        return new String(this.storage.read(path), StandardCharsets.UTF_8);
    }

    /** Why the world's Stratos data is read-only, if it is. */
    public Optional<ReadOnly> readOnly() {
        return Optional.ofNullable(this.readOnly);
    }

    /**
     * Makes the world read-only (also from outside: the platform's own record of the format may be newer, F-5). Every
     * request is then refused, and nothing is written. A repair planned already is dropped: a read-only world has no
     * journal (§8).
     */
    public void makeReadOnly(ReadOnly why, String detail) {
        if (this.readOnly == null) {
            this.log.accept("[stratos] this world's Stratos data is read-only (" + why + "): " + detail);
        }
        this.readOnly = Objects.requireNonNull(why, "why");
        this.journal = null;
        this.repair = null;
        this.repairing = false;
        this.report = null;
        this.dimensions.values().forEach(operations -> {
            operations.setReadOnly(true);
            operations.setRepairing(false);
        });
    }

    /** The world's {@code meta.json}, once there is one. */
    public Optional<WorldMeta> meta() {
        return Optional.ofNullable(this.meta);
    }

    /** The operations of {@code dimension} (such as {@code minecraft:overworld}), with its saved histories. */
    public Operations operations(String dimension) {
        return this.dimensions.computeIfAbsent(dimension, d -> {
            Operations operations = new Operations(new ApplyScheduler(), this.store, this.settings.ids(), this.settings.writer(), this.settings.clock(),
                    d, this.settings.helper(), this.log);
            operations.restore(this.savedHistories, this.savedRecords);
            operations.setReadOnly(this.readOnly != null);
            if (this.journal != null) {
                operations.journal(this.journal);
            }
            operations.setRepairing(this.repairing);
            return operations;
        });
    }

    /** Every dimension's operations opened so far. */
    public List<Operations> allOperations() {
        return List.copyOf(this.dimensions.values());
    }

    public UndoStore store() {
        return this.store;
    }

    /** The world's journal (F-2+); empty in a read-only world. */
    public Optional<Journal> journal() {
        return Optional.ofNullable(this.journal);
    }

    // --- F-2+: the repair after a crash ---

    /** The dimensions the repair writes in (the platform makes their worlds tick); empty when nothing is repaired. */
    public Set<String> repairDimensions() {
        return this.repair == null ? Set.of() : this.repair.dimensions();
    }

    /**
     * Starts the repair, once the server has started ({@code exists}: whether a dimension is in this world). Its jobs go
     * into each dimension's apply engine, in order. Nothing happens when there is nothing to repair.
     */
    public void startRepair(Predicate<String> exists) {
        if (this.repair == null || this.repair.started()) {
            return;
        }
        this.repair.start(dimension -> exists.test(dimension) ? Optional.of(operations(dimension).scheduler()) : Optional.empty());
        repairAfterTick(); // a repair with no blocks to write may finish at once
    }

    /** Whether a repair is planned and not yet finished; requests are refused with {@code REPAIRING} meanwhile. */
    public boolean isRepairing() {
        return this.repairing;
    }

    /** The repair, if one was planned (for the harness and the tests). */
    public Optional<Recovery> repair() {
        return Optional.ofNullable(this.repair);
    }

    /** What the finished repair did, for players who join later; empty if there was none (or it hasn't finished). */
    public Optional<Recovery.Report> repairReport() {
        return Optional.ofNullable(this.report).filter(Recovery.Report::anything);
    }

    /** The finished repair's report, once (for the players online when it finishes); empty otherwise. */
    public Optional<Recovery.Report> takeRepairReport() {
        if (this.reportTaken || this.report == null || this.repairing) {
            return Optional.empty();
        }
        this.reportTaken = true;
        return repairReport();
    }

    private void repairAfterTick() {
        if (this.repair == null || !this.repairing || !this.repair.started()) {
            return;
        }
        for (Recovery.Step step : this.repair.afterTick(this.settings.writer())) {
            UUID run = step.run().run();
            String what = step.run().begin().map(begin -> begin.kind().name().toLowerCase(java.util.Locale.ROOT) + " of operation " + begin.operation()
                    + " by " + begin.owner() + " in " + begin.dimension()).orElse("run " + run);
            this.log.accept("[stratos] repair after a crash: " + what + " " + step.outcome().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    + ": " + step.detail());
            if (this.journal != null) {
                this.journal.recovered(run, step.outcome(), step.detail(), step.direction() == Recovery.Direction.ROLL_BACK);
            }
        }
        if (this.repair.finished()) {
            this.repairing = false;
            this.dimensions.values().forEach(operations -> operations.setRepairing(false));
            this.report = this.repair.report();
            markRolledBack(this.repair.rolledBackApplies());
            this.log.accept("[stratos] repaired this world after a crash: " + this.report.rolledBack() + " edits rolled back, " + this.report.completed()
                    + " completed, " + this.report.unchanged() + " already right, " + this.report.needAttention() + " need attention");
        }
    }

    /** The records of rolled-back applies, if any were saved, now say so; they are written with the next save. */
    private void markRolledBack(List<UUID> operations) {
        for (UUID id : operations) {
            String path = RECORDS + "/" + id + ".json";
            if (!this.storage.exists(path)) {
                continue; // never saved
            }
            try {
                this.looseRecords.put(id, OperationJson.read(read(path)).withStatus(OperationRecord.Status.ROLLED_BACK).withUndoAvailable(false));
            } catch (IOException | JsonFormatException e) {
                this.log.accept("[stratos] the record of rolled-back operation " + id + " can't be read (" + e.getMessage() + "); it is left as it is");
            }
        }
    }

    // --- Saving ---

    /** Whether {@link #save} has anything to write. Never, in a read-only world. */
    public boolean hasUnsavedChanges() {
        return this.readOnly == null
                && (this.changedOnLoad || !this.looseRecords.isEmpty() || this.dimensions.values().stream().anyMatch(Operations::hasUnsavedChanges));
    }

    /**
     * Whether every world save should reach Stratos (F-2+: {@code stratos_meta} stays dirty): it has something to save,
     * or the journal keeps runs that world saves will retire.
     */
    public boolean wantsWorldSaves() {
        return hasUnsavedChanges() || (this.readOnly == null && this.journal != null && this.journal.keepsRuns());
    }

    /**
     * A world save (autosave, Save and Quit, {@code /save-all}, the server's stop): Minecraft writes its chunks right
     * after. From F-2+ the journal starts a new segment, and runs safely saved are retired: if this save writes
     * nothing, against the index last written.
     */
    public void worldSave() throws IOException {
        Instant retireBy = null;
        if (this.journal != null) {
            this.journal.worldSaved();
            retireBy = this.journal.previousWorldSave();
        }
        boolean writing = save(true, retireBy);
        if (!writing && this.journal != null) {
            this.journal.saved(this.lastListed, true, retireBy);
        }
    }

    /**
     * Saves what changed: the changed records, the undo index (naming only operations whose images are on disk), the
     * image folders it no longer names away, then {@code meta.json} last. Nothing in a read-only world, or one nobody
     * edited.
     *
     * <p>From F-2b the server thread only takes a snapshot; the helper writes it, and {@link #afterTick} files it.
     * Operations whose images are still being written are left out, for the next save. Once the server is stopping
     * ({@link #stop}), the save is written here and is complete, and a failure is thrown.
     *
     * <p>From F-2+ the index lists the journal runs whose results it holds, and the helper writes it only once the
     * journal has every record of theirs on disk.
     */
    public void save() throws IOException {
        save(false, null);
    }

    /** As {@link #save()}; returns whether it is writing anything. */
    private boolean save(boolean worldSave, Instant retireBy) throws IOException {
        afterTick();
        if (!hasUnsavedChanges()) {
            return false;
        }
        Map<Operations, List<OperationRecord>> records = new LinkedHashMap<>();
        Map<Operations, Long> versions = new LinkedHashMap<>();
        List<OperationRecord> toWrite = new ArrayList<>();
        for (Operations operations : this.dimensions.values()) {
            List<OperationRecord> unsaved = operations.unsavedRecords();
            records.put(operations, unsaved);
            versions.put(operations, operations.changeCount());
            toWrite.addAll(unsaved);
        }
        List<OperationRecord> loose = List.copyOf(this.looseRecords.values());
        toWrite.addAll(loose);
        // R-9: the snapshot of every palette a record to write names, unless it is on disk already; written before the records.
        Map<String, byte[]> palettes = new LinkedHashMap<>();
        for (OperationRecord record : toWrite) {
            for (String snapshotId : record.palettes()) {
                if (this.palettesOnDisk.contains(snapshotId) || palettes.containsKey(snapshotId)) {
                    continue;
                }
                Palette palette = this.paletteSnapshots.get(snapshotId);
                if (palette == null) {
                    if (this.palettesMissingLogged.add(snapshotId)) {
                        this.log.accept("[stratos] no palette snapshot " + snapshotId + " to save with the records that name it");
                    }
                    continue;
                }
                try {
                    palettes.put(snapshotId, StrictJson.write(StrictJson.parseObject(palette.snapshotJson(), "a palette snapshot"))
                            .getBytes(StandardCharsets.UTF_8));
                } catch (JsonFormatException e) {
                    throw new IllegalStateException("A palette's own snapshot isn't JSON", e);
                }
            }
        }
        List<UndoStore.Saved> saved;
        List<UUID> waiting;
        if (this.stopping) {
            saved = this.store.flush(); // the last save: every image on disk now
            waiting = List.of();
        } else {
            UndoStore.Savable savable = this.store.savable();
            saved = savable.saved();
            waiting = savable.waiting();
        }
        Set<UUID> named = new HashSet<>();
        saved.forEach(s -> named.add(s.id()));
        List<UndoIndex.History> histories = new ArrayList<>();
        for (Operations operations : this.dimensions.values()) {
            for (UndoIndex.History history : operations.histories()) {
                List<UUID> undo = history.undo().stream().filter(named::contains).toList();
                List<UUID> redo = history.redo().stream().filter(named::contains).toList();
                if (!undo.isEmpty() || !redo.isEmpty()) {
                    histories.add(new UndoIndex.History(history.dimension(), history.owner(), undo, redo));
                }
            }
        }
        Journal journal = this.journal;
        List<UUID> listed = journal == null ? List.of() : journal.listable(waiting);
        long journalTicket = journal == null ? 0 : journal.lastTicket();
        UndoIndex index = new UndoIndex(saved, histories, listed);
        List<String> folders = this.store.takeUnused();
        Instant now = this.settings.clock().get();
        this.meta = this.meta == null
                ? new WorldMeta(CoreInfo.FORMAT_VERSION, this.settings.dataVersion(), this.settings.ids().get(), this.settings.writtenBy(),
                        this.settings.writtenBy(), now, List.of())
                : this.meta.written(this.settings.writtenBy(), this.settings.dataVersion(), now);
        WorldMeta meta = this.meta;
        Consumer<String> log = this.log;
        HelperTask<List<String>> task = new HelperTask<>(() -> {
            // F-2+: every record of a listed run is on disk before the index lists it.
            if (journal != null && !journal.awaitOnDisk(journalTicket, STOP_WAIT_SECONDS * 1000)) {
                log.accept("[stratos] the journal didn't reach the disk before a save; the save goes ahead");
            }
            for (Map.Entry<String, byte[]> palette : palettes.entrySet()) {
                this.storage.write(PALETTES + "/" + palette.getKey() + ".json", palette.getValue());
            }
            for (OperationRecord record : toWrite) {
                this.storage.write(RECORDS + "/" + record.id() + ".json", OperationJson.write(record).getBytes(StandardCharsets.UTF_8));
            }
            this.storage.write(INDEX, UndoIndexJson.write(index).getBytes(StandardCharsets.UTF_8));
            List<String> failed = new ArrayList<>();
            for (String folder : folders) {
                try {
                    this.storage.delete(folder);
                } catch (IOException e) {
                    failed.add(folder); // tried again after the next save; a restart deletes it too, as no index names it
                }
            }
            this.storage.write(META, meta.toJson().getBytes(StandardCharsets.UTF_8));
            return failed;
        });
        SaveRun run = new SaveRun(task, records, versions, waiting.isEmpty(), folders, listed, worldSave, retireBy, loose, List.copyOf(palettes.keySet()));
        this.saving.add(run);
        this.lastSaveLeftOut = waiting.size();
        if (this.stopping) {
            task.run();
        } else {
            try {
                this.settings.helper().disk(task);
            } catch (RejectedExecutionException e) {
                task.run();
            }
        }
        afterTick(); // filed at once if it is written already
        if (this.stopping && task.result().isCompletedExceptionally()) {
            try {
                task.result().join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            }
        }
        return true;
    }

    /**
     * Files the saves and undo-image writes the helper has finished (F-2b): the records and histories they covered are
     * saved, unless they changed since. A save that failed is logged and leaves everything unsaved, for the next one.
     * From F-2+ also moves the repair on, and a filed save's listed runs stay listed (a world save retires the safe
     * ones). Server thread; call once a tick.
     */
    public void afterTick() {
        this.store.afterWrites();
        repairAfterTick();
        while (!this.saving.isEmpty() && this.saving.peek().task().isDone()) {
            SaveRun run = this.saving.poll();
            List<String> failed;
            try {
                failed = run.task().result().join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                this.log.accept("[stratos] couldn't save this world's Stratos data: " + cause);
                this.store.unusedAgain(run.folders());
                this.lastSaveFailed = true;
                continue;
            }
            run.records().forEach(Operations::recordsSaved);
            this.palettesOnDisk.addAll(run.palettes());
            if (run.complete()) {
                run.versions().forEach(Operations::markSaved);
            }
            for (OperationRecord record : run.loose()) {
                this.looseRecords.remove(record.id(), record);
            }
            this.changedOnLoad = false;
            this.store.unusedAgain(failed);
            this.lastSaveFailed = false;
            this.lastListed = run.listed();
            if (this.journal != null) {
                this.journal.saved(run.listed(), run.worldSave(), run.retireBy());
            }
        }
    }

    /** How many saves the helper is still writing (F-2b). */
    public int savesInFlight() {
        return this.saving.size();
    }

    /** How many operations the last save left out because their images were still being written (F-2b). */
    public int lastSaveLeftOut() {
        return this.lastSaveLeftOut;
    }

    /**
     * The server is stopping (F-2b): saves and image writes in flight are finished on this thread (waiting for any the
     * helper is in the middle of), and from now on a save is written here and is complete, for the world's last save.
     * From F-2+ the journal's records are put on disk too.
     */
    public void stop() {
        this.stopping = true;
        for (SaveRun run : List.copyOf(this.saving)) {
            run.task().finishHere(STOP_WAIT_SECONDS);
        }
        this.store.finishWrites(STOP_WAIT_SECONDS);
        if (this.journal != null && !this.journal.awaitOnDisk(this.journal.lastTicket(), STOP_WAIT_SECONDS * 1000)) {
            this.log.accept("[stratos] the journal didn't reach the disk while the server stopped");
        }
        afterTick();
    }

    /**
     * The server has stopped (F-2+): Minecraft has written every chunk, after Stratos's last save. If that save was
     * written and every run is safely saved, the journal is deleted; otherwise it is kept for the next start.
     */
    public void stopped() {
        if (this.journal != null) {
            this.journal.stopped(!this.lastSaveFailed && !this.repairing);
        }
    }
}
