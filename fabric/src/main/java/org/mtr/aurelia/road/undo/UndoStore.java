/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.Helper;
import org.mtr.aurelia.road.spi.Storage;

/**
 * The undo images of one world (spec mod-suite-v1 §6.6, world-data-format §6): per operation, its changed blocks, one
 * compressed image per chunk section ({@link UndoCodec}).
 * <ul>
 *   <li>Images stay in memory up to a budget; beyond it the least recently used operations are written to
 *       {@code undo/<operationId>/<generation>/s.<sx>.<sy>.<sz>.bin} and read back when needed. From F-2b every
 *       generation is written as soon as it is recorded (behind, below), and a spill only drops the copy in memory.</li>
 *   <li>A cap on operations and bytes (owner, 2026-10-05: 200 operations or 1 GB) drops the oldest operations first;
 *       the {@link #onPruned} listeners hear about each one, so histories can forget them.</li>
 *   <li><b>Saved with the world (F-5):</b> {@link #flush} writes every image not yet on disk and says what the index
 *       ({@code undo/index.json}, {@link UndoIndexJson}) must hold; {@link #restore} brings the operations back after a
 *       restart, their images on disk.</li>
 *   <li><b>Image files are never overwritten:</b> re-recording an operation (an undo that kept hand edits, or stopped
 *       halfway) starts a new generation folder. In a {@code persistent} store, folders the saved index may still name
 *       are deleted only by {@link #deleteUnused}, once a newer index is saved, so the last saved index always matches
 *       the files on disk.</li>
 * </ul>
 * <b>Threads (F-2b):</b> the store itself is server thread only, with these exceptions:
 * <ul>
 *   <li>{@link #compress} (compressing an operation's images) and {@link Captured#read} (reading and unpacking images
 *       {@link #capture captured} on the server thread) run on any thread, so the helper can do them.</li>
 *   <li><b>Images are written behind:</b> a newly recorded generation's files are written by the helper's disk work
 *       ({@link Helper#disk}), a file at a time, while the player carries on. {@link #afterWrites} (server thread) then
 *       marks the entry written. Until then its images stay in memory (a spill skips it), and a save leaves it out
 *       ({@link #savable}) for the next one; {@link #flush} and {@link #finishWrites} finish the writing on the calling
 *       thread, for the server's last save.</li>
 *   <li>An entry captured for reading is <b>held</b> until {@link #release}: the cap skips it, so its images are never
 *       deleted while they are read.</li>
 * </ul>
 * The {@link Storage} must allow two threads at once.
 */
public final class UndoStore {
    public static final long DEFAULT_MEMORY_BYTES = 64L << 20;
    public static final int DEFAULT_MAX_OPERATIONS = 200;
    public static final long DEFAULT_MAX_BYTES = 1L << 30;
    static final String FOLDER = "undo";

    /** One operation's images. */
    public static final class Entry {
        private final UUID id;
        private final String owner;
        private final int dataVersion;
        private Map<BlockPlan.SectionPos, byte[]> memory; // null while the images are only on disk
        private List<BlockPlan.SectionPos> sections;
        private int blocks;
        private long bytes;
        private long lastUsed;
        private int generation;
        private boolean written; // this generation's images are all on disk
        private int holds; // captures not yet released (F-2b): the cap skips a held entry
        private ImageWrite write; // this generation's images being written behind (F-2b)
        private boolean writeFailed; // writing this generation's images failed; a save tries again

        private Entry(UUID id, String owner, int dataVersion) {
            this.id = id;
            this.owner = owner;
            this.dataVersion = dataVersion;
        }

        public UUID id() {
            return this.id;
        }

        public String owner() {
            return this.owner;
        }

        public int dataVersion() {
            return this.dataVersion;
        }

        /** How many blocks the operation changed (as now recorded). */
        public int blocks() {
            return this.blocks;
        }

        /** The compressed size of its images. */
        public long bytes() {
            return this.bytes;
        }

        /** Whether its images are on disk rather than in memory. */
        public boolean onDisk() {
            return this.memory == null;
        }

        /** Which recording of the operation its images are: 1, then one more each time it is re-recorded. */
        public int generation() {
            return this.generation;
        }
    }

    /**
     * An operation's images, compressed by {@link #compress} on any thread, to record with {@link #add(String, Images)}
     * or {@link #replace(Entry, Images)} on the server thread (F-2b). Never changed once made.
     */
    public static final class Images {
        private final UUID id;
        private final int generation;
        private final int dataVersion;
        private final Map<BlockPlan.SectionPos, byte[]> images; // canonical order, unmodifiable
        private final int blocks;
        private final long bytes;
        private final boolean written; // all of them are in the generation's folder on disk
        private final boolean writeFailed;

        private Images(UUID id, int generation, int dataVersion, Map<BlockPlan.SectionPos, byte[]> images, int blocks, long bytes, boolean written,
                boolean writeFailed) {
            this.id = id;
            this.generation = generation;
            this.dataVersion = dataVersion;
            this.images = images;
            this.blocks = blocks;
            this.bytes = bytes;
            this.written = written;
            this.writeFailed = writeFailed;
        }

        public UUID id() {
            return this.id;
        }

        public int generation() {
            return this.generation;
        }

        /** How many changed blocks they hold. */
        public int blocks() {
            return this.blocks;
        }

        /** Their compressed size. */
        public long bytes() {
            return this.bytes;
        }

        /** How many section images there are. */
        public int sections() {
            return this.images.size();
        }

        /** Whether they were written to disk when they were compressed. */
        public boolean written() {
            return this.written;
        }
    }

    /**
     * An entry's images as they were when {@link #capture captured}, to read on any thread with {@link #read} (F-2b: an
     * undo or redo unpacks them on the helper thread). Never changed once made: compressed images are never changed, and
     * image files are never overwritten.
     */
    public static final class Captured {
        private final UUID id;
        private final int generation;
        private final int blocks;
        private final List<BlockPlan.SectionPos> sections;
        private final Map<BlockPlan.SectionPos, byte[]> memory; // null: read them from disk
        private final Storage storage;

        private Captured(UUID id, int generation, int blocks, List<BlockPlan.SectionPos> sections, Map<BlockPlan.SectionPos, byte[]> memory,
                Storage storage) {
            this.id = id;
            this.generation = generation;
            this.blocks = blocks;
            this.sections = sections;
            this.memory = memory;
            this.storage = storage;
        }

        /** The recorded changes, read from memory or disk and unpacked. Any thread. Throws if an image can't be read. */
        public List<BlockChange> read() throws IOException {
            List<BlockChange> changes = new ArrayList<>(this.blocks);
            for (BlockPlan.SectionPos pos : this.sections) {
                byte[] image = this.memory != null ? this.memory.get(pos) : this.storage.read(path(this.id, this.generation, pos));
                if (image == null) {
                    throw new IOException("the undo image of section " + pos + " is missing");
                }
                changes.addAll(UndoCodec.decode(image).changes());
            }
            return changes;
        }
    }

    /** What a save can name now (F-2b): the operations whose images are on disk, and those still being written. */
    public record Savable(List<Saved> saved, List<UUID> waiting) {
        public Savable {
            saved = List.copyOf(saved);
            waiting = List.copyOf(waiting);
        }
    }

    /** One generation's images being written by the helper, a file at a time (F-2b). */
    private static final class ImageWrite {
        final UUID id;
        final int generation;
        final List<HelperTask<Void>> tasks = new ArrayList<>();
        final AtomicInteger left;
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile boolean failed;

        ImageWrite(UUID id, int generation, int files) {
            this.id = id;
            this.generation = generation;
            this.left = new AtomicInteger(files);
        }

        boolean isDone() {
            return this.left.get() == 0;
        }
    }

    /** What the index keeps about one operation (F-5), its images being on disk. */
    public record Saved(UUID id, String owner, int dataVersion, int generation, int blocks, long bytes, List<BlockPlan.SectionPos> sections) {
        public Saved {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            if (generation < 1 || blocks < 0 || bytes < 0) {
                throw new IllegalArgumentException("Bad saved undo entry " + id);
            }
            sections = List.copyOf(sections);
        }
    }

    private final Storage storage;
    private final long memoryBudget;
    private final int maxOperations;
    private final long maxBytes;
    private final boolean persistent;
    private final Helper helper;
    private final List<ImageWrite> writes = new ArrayList<>(); // in flight, oldest first
    private final Map<UUID, Entry> entries = new LinkedHashMap<>(); // oldest first
    private final List<Consumer<Entry>> prunedListeners = new ArrayList<>();
    private final Set<String> unusedFolders = new LinkedHashSet<>(); // to delete once a newer index is saved
    private long memoryBytes;
    private long totalBytes;
    private long useCounter;
    private int spillFailures;

    /** A store whose files go as soon as nothing needs them (tests, and worlds that aren't saved). */
    public UndoStore(Storage storage, long memoryBudget, int maxOperations, long maxBytes) {
        this(storage, memoryBudget, maxOperations, maxBytes, false);
    }

    /**
     * A store; when {@code persistent} (F-5: saved with the world), image folders a saved index may name are deleted only
     * by {@link #deleteUnused}.
     */
    public UndoStore(Storage storage, long memoryBudget, int maxOperations, long maxBytes, boolean persistent) {
        this(storage, memoryBudget, maxOperations, maxBytes, persistent, Helper.AT_ONCE);
    }

    /** A store whose new images are written to disk by {@code helper}'s disk work (F-2b). */
    public UndoStore(Storage storage, long memoryBudget, int maxOperations, long maxBytes, boolean persistent, Helper helper) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.helper = Objects.requireNonNull(helper, "helper");
        if (memoryBudget < 0 || maxOperations < 1 || maxBytes < 1) {
            throw new IllegalArgumentException("Bad undo store limits");
        }
        this.memoryBudget = memoryBudget;
        this.maxOperations = maxOperations;
        this.maxBytes = maxBytes;
        this.persistent = persistent;
    }

    /** Adds a listener for every operation the cap drops. */
    public void onPruned(Consumer<Entry> listener) {
        this.prunedListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Compresses {@code changes} (all of one DataVersion) into the images of generation {@code generation} of operation
     * {@code id}; with {@code write}, also writes them to that generation's folder on this thread. <b>Any thread:</b> it
     * uses the storage and nothing else of the store. Images recorded unwritten are written behind (F-2b); if writing
     * fails, they stay in memory and a save tries again.
     */
    public Images compress(UUID id, int generation, int dataVersion, List<BlockChange> changes, boolean write) {
        Objects.requireNonNull(id, "id");
        if (generation < 1) {
            throw new IllegalArgumentException("Bad generation " + generation);
        }
        SortedMap<BlockPlan.SectionPos, List<BlockChange>> bySection = UndoCodec.bySection(changes);
        Map<BlockPlan.SectionPos, byte[]> images = new LinkedHashMap<>();
        long bytes = 0;
        for (Map.Entry<BlockPlan.SectionPos, List<BlockChange>> section : bySection.entrySet()) {
            byte[] image = UndoCodec.encode(dataVersion, section.getKey(), section.getValue());
            images.put(section.getKey(), image);
            bytes += image.length;
        }
        boolean written = false;
        boolean failed = false;
        if (write) {
            try {
                for (Map.Entry<BlockPlan.SectionPos, byte[]> image : images.entrySet()) {
                    this.storage.write(path(id, generation, image.getKey()), image.getValue());
                }
                written = true;
            } catch (IOException e) {
                failed = true; // kept in memory; a spill or the next save writes them
            }
        }
        return new Images(id, generation, dataVersion, Collections.unmodifiableMap(images), changes.size(), bytes, written, failed);
    }

    /** Records an operation's changes and returns its entry. May spill older images and drop the oldest operations. */
    public Entry add(UUID id, String owner, int dataVersion, List<BlockChange> changes) {
        return add(owner, compress(id, 1, dataVersion, changes, false));
    }

    /**
     * Records an operation's images, compressed already as its generation 1 ({@link #compress}), and returns its entry.
     * May spill older images and drop the oldest operations.
     */
    public Entry add(String owner, Images images) {
        if (images.generation != 1) {
            throw new IllegalArgumentException("A new operation's images are generation 1, not " + images.generation);
        }
        if (this.entries.containsKey(images.id)) {
            throw new IllegalArgumentException("Operation " + images.id + " is already recorded");
        }
        Entry entry = new Entry(images.id, Objects.requireNonNull(owner, "owner"), images.dataVersion);
        entry.generation = 1;
        this.entries.put(images.id, entry);
        record(entry, images);
        enforceLimits(entry);
        return entry;
    }

    /** Whether {@code entry} is still recorded (not removed or pruned). */
    public boolean contains(Entry entry) {
        return this.entries.get(entry.id) == entry;
    }

    /** The entry of operation {@code id}, if it is recorded. */
    public Optional<Entry> entry(UUID id) {
        return Optional.ofNullable(this.entries.get(id));
    }

    /** The changes recorded for {@code entry}, read from memory or disk on this thread. */
    public List<BlockChange> changes(Entry entry) {
        Captured captured = capture(entry);
        try {
            return captured.read();
        } catch (IOException e) {
            throw new UncheckedIOException("Undo images of " + entry.id + " can't be read", e);
        } finally {
            release(entry);
        }
    }

    /**
     * Captures {@code entry}'s images to read on another thread ({@link Captured#read}), and holds the entry until
     * {@link #release}: the cap skips it, so none of its images is deleted while they are read (F-2b).
     */
    public Captured capture(Entry entry) {
        requireContained(entry);
        entry.lastUsed = ++this.useCounter;
        entry.holds++;
        return new Captured(entry.id, entry.generation, entry.blocks, entry.sections, entry.memory, this.storage);
    }

    /** Lets go of {@code entry}, held by {@link #capture}. */
    public void release(Entry entry) {
        if (entry.holds > 0) {
            entry.holds--;
        }
    }

    /** Whether {@code entry} is held by a {@link #capture} not yet released. */
    public boolean isHeld(Entry entry) {
        return entry.holds > 0;
    }

    /** The generation a re-recording of {@code entry} will be, to {@link #compress} its images ahead (F-2b). */
    public int nextGeneration(Entry entry) {
        return entry.generation + 1;
    }

    /** Replaces the changes recorded for {@code entry} (after an undo kept some hand edits, or stopped halfway). */
    public void replace(Entry entry, List<BlockChange> changes) {
        requireContained(entry);
        replace(entry, compress(entry.id, nextGeneration(entry), entry.dataVersion, changes, false));
    }

    /** As {@link #replace(Entry, List)}, with the images compressed already as its {@link #nextGeneration}. */
    public void replace(Entry entry, Images images) {
        requireContained(entry);
        if (!images.id.equals(entry.id) || images.generation != nextGeneration(entry)) {
            throw new IllegalArgumentException("Images of " + images.id + " generation " + images.generation + " can't re-record " + entry.id
                    + " generation " + entry.generation);
        }
        dropImages(entry);
        entry.generation = images.generation;
        record(entry, images);
        enforceLimits(entry);
    }

    /** Forgets {@code entry} and deletes its images. */
    public void remove(Entry entry) {
        if (contains(entry)) {
            dropImages(entry);
            this.entries.remove(entry.id);
            unused(FOLDER + "/" + entry.id);
        }
    }

    public int size() {
        return this.entries.size();
    }

    public long memoryBytes() {
        return this.memoryBytes;
    }

    public long totalBytes() {
        return this.totalBytes;
    }

    /** How many times writing images to disk failed (they then stayed in memory). */
    public int spillFailures() {
        return this.spillFailures;
    }

    /** How many generations' images are being written behind now (F-2b). */
    public int writesInFlight() {
        return this.writes.size();
    }

    /**
     * Has the helper write {@code entry}'s images to disk, a file at a time (F-2b), unless they are written, being
     * written, or failed already.
     */
    private void requestWrite(Entry entry) {
        if (entry.written || entry.write != null || entry.writeFailed || entry.memory == null) {
            return;
        }
        ImageWrite write = new ImageWrite(entry.id, entry.generation, entry.memory.size());
        for (Map.Entry<BlockPlan.SectionPos, byte[]> image : entry.memory.entrySet()) {
            String path = path(entry.id, entry.generation, image.getKey());
            byte[] bytes = image.getValue();
            write.tasks.add(new HelperTask<>(() -> {
                if (!write.cancelled.get() && !write.failed) {
                    try {
                        this.storage.write(path, bytes);
                    } catch (IOException e) {
                        write.failed = true;
                    }
                }
                write.left.decrementAndGet();
                return null;
            }));
        }
        entry.write = write;
        this.writes.add(write);
        for (HelperTask<Void> task : write.tasks) {
            try {
                this.helper.disk(task);
            } catch (RejectedExecutionException e) {
                task.run();
            }
        }
        if (write.isDone()) {
            settle(write); // done at once (no helper)
        }
    }

    /**
     * Files the writes the helper has finished (F-2b): their entries are on disk now, so a spill may drop them from
     * memory and a save may name them. Server thread; call once a tick.
     */
    public void afterWrites() {
        boolean settled = false;
        for (ImageWrite write : List.copyOf(this.writes)) {
            if (write.isDone()) {
                settle(write);
                settled = true;
            }
        }
        if (settled) {
            spillToBudget();
        }
    }

    private void settle(ImageWrite write) {
        this.writes.remove(write);
        Entry entry = this.entries.get(write.id);
        if (entry == null || entry.write != write) {
            return; // removed or re-recorded meanwhile: its folder was given up then
        }
        entry.write = null;
        if (write.failed) {
            entry.writeFailed = true;
            this.spillFailures++;
        } else {
            entry.written = true;
        }
    }

    /**
     * Finishes every write in flight on this thread, waiting up to {@code seconds} for files the helper is writing now
     * (F-2b: the server's last save), and files them.
     */
    public void finishWrites(long seconds) {
        for (ImageWrite write : List.copyOf(this.writes)) {
            for (HelperTask<Void> task : write.tasks) {
                task.finishHere(seconds);
            }
        }
        afterWrites();
    }

    /**
     * Writes every image not yet on disk on this thread (keeping it in memory too), finishing those being written behind
     * first, and returns what the index must hold, oldest first (F-5). Throws if a write fails: the index must then not
     * be saved, as it would name missing files.
     */
    public List<Saved> flush() throws IOException {
        finishWrites(120);
        List<Saved> saved = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (!entry.written) {
                writeImages(entry);
                entry.written = true;
                entry.writeFailed = false;
            }
            saved.add(new Saved(entry.id, entry.owner, entry.dataVersion, entry.generation, entry.blocks, entry.bytes, entry.sections));
        }
        return saved;
    }

    /**
     * What a save can name now without writing images on the server thread (F-2b): the operations already on disk; those
     * still being written wait for the next save. One whose writing failed is written again behind.
     */
    public Savable savable() {
        afterWrites();
        List<Saved> saved = new ArrayList<>();
        List<UUID> waiting = new ArrayList<>();
        for (Entry entry : List.copyOf(this.entries.values())) {
            if (!entry.written && entry.writeFailed && entry.write == null) {
                entry.writeFailed = false;
                requestWrite(entry);
            }
            if (entry.written) {
                saved.add(new Saved(entry.id, entry.owner, entry.dataVersion, entry.generation, entry.blocks, entry.bytes, entry.sections));
            } else {
                waiting.add(entry.id);
            }
        }
        return new Savable(saved, waiting);
    }

    /**
     * The folders waiting to be deleted, handed over to a save that will delete them once its index is written (F-2b);
     * the store forgets them. Those the save couldn't delete come back through {@link #unusedAgain}.
     */
    public List<String> takeUnused() {
        List<String> folders = List.copyOf(this.unusedFolders);
        this.unusedFolders.clear();
        return folders;
    }

    /** Folders a save couldn't delete (or didn't get to): tried again after the next save. */
    public void unusedAgain(Collection<String> folders) {
        this.unusedFolders.addAll(folders);
    }

    /**
     * Deletes the image folders no longer needed, once an index that doesn't name them is saved (F-5). A folder that
     * can't be deleted is tried again next time.
     */
    public void deleteUnused() {
        for (Iterator<String> it = this.unusedFolders.iterator(); it.hasNext(); ) {
            String folder = it.next();
            try {
                this.storage.delete(folder);
                it.remove();
            } catch (IOException e) {
                // tried again after the next save; the folder is unknown to the index, so a restart deletes it too
            }
        }
    }

    /**
     * Brings back the operations a saved index lists, oldest first, their images on disk (F-5). An operation with a
     * missing image file is left out; the ids of those left out are returned. Call before anything is added.
     */
    public List<UUID> restore(List<Saved> saved) {
        if (!this.entries.isEmpty()) {
            throw new IllegalStateException("Restore an undo store before anything is added to it");
        }
        List<UUID> missing = new ArrayList<>();
        for (Saved s : saved) {
            Entry entry = new Entry(s.id(), s.owner(), s.dataVersion());
            entry.generation = s.generation();
            entry.sections = s.sections();
            if (this.entries.containsKey(s.id()) || !entry.sections.stream().allMatch(pos -> this.storage.exists(path(entry, pos)))) {
                missing.add(s.id());
                continue;
            }
            entry.blocks = s.blocks();
            entry.bytes = s.bytes();
            entry.written = true;
            entry.lastUsed = ++this.useCounter;
            this.entries.put(entry.id, entry);
            this.totalBytes += entry.bytes;
        }
        return missing;
    }

    /**
     * Deletes every file under {@code undo/} that no recorded operation's current images use: images written after the
     * last save, old generations, and the session-only files of Stratos before F-5. Leaves {@code undo/index.json}.
     */
    public void deleteUnknown() throws IOException {
        Set<String> folders = new LinkedHashSet<>();
        for (String file : this.storage.list(FOLDER)) {
            String[] parts = file.split("/");
            if (parts.length < 3) {
                continue; // undo/index.json
            }
            Entry entry = entryNamed(parts[1]);
            if (entry == null) {
                folders.add(FOLDER + "/" + parts[1]);
            } else if (parts.length != 4 || !parts[2].equals(Integer.toString(entry.generation))) {
                folders.add(parts.length == 4 ? FOLDER + "/" + parts[1] + "/" + parts[2] : file);
            }
        }
        for (String folder : folders) {
            this.storage.delete(folder);
        }
    }

    private Entry entryNamed(String name) {
        try {
            return this.entries.get(UUID.fromString(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Takes {@code images} as {@code entry}'s current generation, and has them written behind unless they are on disk. */
    private void record(Entry entry, Images images) {
        entry.memory = images.images;
        entry.sections = List.copyOf(images.images.keySet());
        entry.blocks = images.blocks;
        entry.bytes = images.bytes;
        entry.written = images.written;
        entry.writeFailed = images.writeFailed;
        entry.lastUsed = ++this.useCounter;
        if (images.writeFailed) {
            this.spillFailures++;
        }
        this.memoryBytes += images.bytes;
        this.totalBytes += images.bytes;
        requestWrite(entry);
    }

    /**
     * Removes an entry's images from memory and the totals; any on disk, or being written, are deleted now, or after the
     * next save.
     */
    private void dropImages(Entry entry) {
        if (entry.memory != null) {
            this.memoryBytes -= entry.bytes;
        }
        if (entry.written) {
            unused(generationFolder(entry));
        } else if (entry.write != null) {
            entry.write.cancelled.set(true); // its files not yet written never will be
            entry.write = null;
            unused(generationFolder(entry));
        }
        this.totalBytes -= entry.bytes;
        entry.memory = null;
        entry.bytes = 0;
        entry.written = false;
        entry.writeFailed = false;
    }

    /** {@code folder} is no longer needed: deleted now, or, in a persistent store, once a newer index is saved. */
    private void unused(String folder) {
        if (this.persistent) {
            this.unusedFolders.add(folder);
            return;
        }
        try {
            this.storage.delete(folder);
        } catch (IOException e) {
            // Nothing depends on it; the next start deletes what no index names.
        }
    }

    /**
     * Drops the oldest operations beyond the caps (never the one just recorded, nor one held), then spills down to the
     * memory budget.
     */
    private void enforceLimits(Entry newest) {
        prune(newest);
        spillToBudget();
    }

    private void prune(Entry newest) {
        List<Entry> oldestFirst = new ArrayList<>(this.entries.values());
        for (Entry entry : oldestFirst) {
            if (this.entries.size() <= this.maxOperations && this.totalBytes <= this.maxBytes) {
                break;
            }
            if (entry != newest && entry.holds == 0) {
                remove(entry);
                this.prunedListeners.forEach(listener -> listener.accept(entry));
            }
        }
    }

    /** Drops the least recently used images on disk from memory, down to the budget. */
    private void spillToBudget() {
        if (this.memoryBytes <= this.memoryBudget) {
            return;
        }
        List<Entry> inMemory = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (entry.memory != null) {
                inMemory.add(entry);
            }
        }
        inMemory.sort(Comparator.comparingLong(entry -> entry.lastUsed));
        for (Entry entry : inMemory) {
            if (this.memoryBytes <= this.memoryBudget) {
                break;
            }
            spill(entry);
        }
    }

    /** Drops {@code entry}'s images from memory once they are on disk; until then (written behind, F-2b) they stay. */
    private void spill(Entry entry) {
        if (!entry.written) {
            return;
        }
        entry.memory = null;
        this.memoryBytes -= entry.bytes;
    }

    private void writeImages(Entry entry) throws IOException {
        for (Map.Entry<BlockPlan.SectionPos, byte[]> image : entry.memory.entrySet()) {
            this.storage.write(path(entry, image.getKey()), image.getValue());
        }
    }

    private static String generationFolder(Entry entry) {
        return FOLDER + "/" + entry.id + "/" + entry.generation;
    }

    private static String path(Entry entry, BlockPlan.SectionPos pos) {
        return path(entry.id, entry.generation, pos);
    }

    private static String path(UUID id, int generation, BlockPlan.SectionPos pos) {
        return FOLDER + "/" + id + "/" + generation + "/s." + pos.x() + "." + pos.y() + "." + pos.z() + ".bin";
    }

    private void requireContained(Entry entry) {
        if (!contains(entry)) {
            throw new IllegalArgumentException("Operation " + entry.id + " is no longer recorded");
        }
    }

    /** For tests: the folders waiting for {@link #deleteUnused}. */
    Set<String> unusedFolders() {
        return new HashSet<>(this.unusedFolders);
    }
}
