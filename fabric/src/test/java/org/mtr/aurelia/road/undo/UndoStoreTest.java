/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.Storage;

/** The undo-image codec, the undo store's memory budget and caps, and the storage path rules (task F-2). */
class UndoStoreTest {
    /** A road-like change set: a 21 × 30 strip of grass replaced by concrete, with a dashed white line. */
    static List<BlockChange> roadLike(int x0, int z0) {
        List<BlockChange> changes = new ArrayList<>();
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 21; z++) {
                String after = z == 10 && x % 6 < 2 ? "minecraft:white_concrete" : "minecraft:gray_concrete";
                changes.add(new BlockChange(x0 + x, 64, z0 + z, "minecraft:grass_block[snowy=false]", after));
            }
        }
        return changes;
    }

    /** {@code changes} in a fixed order: the store groups them by chunk section, so order isn't meaningful. */
    static List<BlockChange> sorted(List<BlockChange> changes) {
        List<BlockChange> copy = new ArrayList<>(changes);
        copy.sort(java.util.Comparator.comparingLong(BlockChange::positionKey));
        return copy;
    }

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    @Test
    void theCodecRoundTripsAndCompressesRoadLikeImagesWell() throws IOException {
        List<BlockChange> changes = roadLike(-40, 300); // negative x: sections round down
        int total = 0;
        long compressed = 0;
        for (var section : UndoCodec.bySection(changes).entrySet()) {
            byte[] image = UndoCodec.encode(3465, section.getKey(), section.getValue());
            UndoCodec.Section decoded = UndoCodec.decode(image);
            assertEquals(3465, decoded.dataVersion());
            assertEquals(section.getKey(), decoded.pos());
            assertEquals(section.getValue(), decoded.changes());
            total += decoded.changes().size();
            compressed += image.length;
        }
        assertEquals(630, total);
        // 630 blocks with ~40-character states would be ~50 KB as text; the image is a small fraction of that.
        assertTrue(compressed < 3_000, "compressed to " + compressed + " bytes");
    }

    @Test
    void theCodecRejectsGarbageAndBlocksOutsideTheSection() {
        assertThrows(IOException.class, () -> UndoCodec.decode(new byte[] {1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> UndoCodec.encode(3465, new BlockPlan.SectionPos(0, 4, 0),
                List.of(new BlockChange(16, 64, 0, "minecraft:air", "minecraft:stone"))));
    }

    @Test
    void imagesBeyondTheMemoryBudgetSpillToDiskAndComeBackExactly() {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 4_000, 200, 1L << 30);
        UndoStore.Entry first = store.add(id(1), "alice", 3465, roadLike(0, 0));
        assertFalse(first.onDisk());
        UndoStore.Entry second = store.add(id(2), "alice", 3465, roadLike(100, 0));
        UndoStore.Entry third = store.add(id(3), "alice", 3465, roadLike(200, 0));
        assertTrue(store.memoryBytes() <= 4_000, "memory " + store.memoryBytes());
        assertTrue(first.onDisk(), "the least recently used images were written out first");
        assertTrue(storage.count("undo/" + first.id()) > 0);
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(first)), "and read back exactly");
        assertEquals(sorted(roadLike(200, 0)), sorted(store.changes(third)));
        assertEquals(sorted(roadLike(100, 0)), sorted(store.changes(second)));
        assertEquals(3, store.size());
    }

    @Test
    void aZeroBudgetPutsEveryImageOnDisk() {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30);
        UndoStore.Entry entry = store.add(id(1), "bob", 3465, roadLike(0, 0));
        assertTrue(entry.onDisk());
        assertEquals(0, store.memoryBytes());
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(entry)));
        store.remove(entry);
        assertFalse(storage.exists("undo/" + entry.id()), "removing deletes the files");
        assertEquals(0, store.totalBytes());
    }

    @Test
    void theCapsDropTheOldestOperationsFirst() {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 1L << 20, 3, 1L << 30);
        List<UndoStore.Entry> pruned = new ArrayList<>();
        store.onPruned(pruned::add);
        List<UndoStore.Entry> entries = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            entries.add(store.add(id(i), "carol", 3465, roadLike(i * 100, 0)));
        }
        assertEquals(3, store.size());
        assertEquals(List.of(entries.get(0), entries.get(1)), pruned, "operations 1 and 2 were dropped, oldest first");
        assertFalse(store.contains(entries.get(0)));
        assertThrows(IllegalArgumentException.class, () -> store.changes(entries.get(0)), "a dropped operation can't be read");

        long oneImage = entries.get(4).bytes();
        UndoStore byBytes = new UndoStore(new FakeStorage(), 1L << 20, 200, oneImage * 2 + oneImage / 2);
        for (int i = 1; i <= 4; i++) {
            byBytes.add(id(i), "dave", 3465, roadLike(i * 100, 0));
        }
        assertEquals(2, byBytes.size(), "the byte cap keeps only what fits");
    }

    @Test
    void aFailedSpillKeepsTheImagesInMemory() {
        FakeStorage storage = new FakeStorage();
        storage.failWrites = true;
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30);
        UndoStore.Entry entry = store.add(id(1), "erin", 3465, roadLike(0, 0));
        assertFalse(entry.onDisk());
        assertEquals(1, store.spillFailures());
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(entry)));
    }

    @Test
    void replaceReRecordsAnOperation() {
        UndoStore store = new UndoStore(new FakeStorage(), 0, 200, 1L << 30);
        UndoStore.Entry entry = store.add(id(1), "fay", 3465, roadLike(0, 0));
        List<BlockChange> fewer = roadLike(0, 0).subList(0, 100);
        store.replace(entry, fewer);
        assertEquals(sorted(fewer), sorted(store.changes(entry)));
        assertEquals(100, entry.blocks());
    }

    @Test
    void reRecordingStartsANewGenerationFolder() {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30);
        UndoStore.Entry entry = store.add(id(1), "fay", 3465, roadLike(0, 0));
        assertEquals(1, entry.generation());
        assertTrue(storage.count("undo/" + entry.id() + "/1") > 0);
        store.replace(entry, roadLike(0, 0).subList(0, 100));
        assertEquals(2, entry.generation());
        assertTrue(storage.count("undo/" + entry.id() + "/2") > 0);
        assertEquals(0, storage.count("undo/" + entry.id() + "/1"), "a store that isn't saved deletes the old generation at once");
    }

    @Test
    void aPersistentStoreDeletesOnlyAfterTheNextSave() throws IOException {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30, true);
        UndoStore.Entry kept = store.add(id(1), "gil", 3465, roadLike(0, 0));
        UndoStore.Entry removed = store.add(id(2), "gil", 3465, roadLike(100, 0));
        store.replace(kept, roadLike(0, 0).subList(0, 50));
        store.remove(removed);
        assertTrue(storage.count("undo/" + kept.id() + "/1") > 0, "the saved index may still name generation 1");
        assertTrue(storage.count("undo/" + removed.id()) > 0, "and the removed operation");
        List<UndoStore.Saved> saved = store.flush();
        assertEquals(1, saved.size());
        assertEquals(new UndoStore.Saved(kept.id(), "gil", 3465, 2, 50, kept.bytes(), saved.get(0).sections()), saved.get(0));
        store.deleteUnused(); // after the index naming only generation 2 is saved
        assertEquals(0, storage.count("undo/" + kept.id() + "/1"));
        assertFalse(storage.exists("undo/" + removed.id()));
        assertEquals(sorted(roadLike(0, 0).subList(0, 50)), sorted(store.changes(kept)));
    }

    @Test
    void everyImageIsWrittenWhenRecordedAndKeptInMemoryWithinTheBudget() throws IOException {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 1L << 20, 200, 1L << 30, true);
        UndoStore.Entry entry = store.add(id(1), "hal", 3465, roadLike(0, 0));
        assertTrue(storage.count("undo/" + entry.id() + "/1") > 0, "on disk at once (F-2b: written behind; here with no helper)");
        assertFalse(entry.onDisk(), "and still in memory, within the budget");
        int writes = storage.writes;
        store.flush();
        assertEquals(writes, storage.writes, "so a save writes nothing more");
    }

    @Test
    void newImagesAreWrittenBehindAFileAtATimeAndStayInMemoryUntilThen() throws IOException {
        FakeStorage storage = new FakeStorage();
        ManualHelper helper = new ManualHelper();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30, true, helper);
        UndoStore.Entry entry = store.add(id(1), "ida", 3465, roadLike(0, 0));
        assertEquals(1, store.writesInFlight());
        assertEquals(0, storage.count("undo"), "nothing written yet");
        assertEquals(4, helper.disk.size(), "a task per image file (the strip spans four sections)");
        assertFalse(entry.onDisk(), "it stays in memory, beyond the zero budget, until written");
        assertEquals(new UndoStore.Savable(List.of(), List.of(id(1))), store.savable(), "a save leaves it out for now");
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(entry)), "it can be read meanwhile");

        helper.runAll();
        assertTrue(storage.count("undo/" + id(1) + "/1") > 0);
        assertEquals(1, store.writesInFlight(), "until the server thread files the write");
        store.afterWrites();
        assertEquals(0, store.writesInFlight());
        assertTrue(entry.onDisk(), "written, so it left memory");
        assertEquals(List.of(id(1)), store.savable().saved().stream().map(UndoStore.Saved::id).toList());
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(entry)));
    }

    @Test
    void aGenerationReRecordedBeforeItIsWrittenIsNeverWritten() throws IOException {
        FakeStorage storage = new FakeStorage();
        ManualHelper helper = new ManualHelper();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30, true, helper);
        UndoStore.Entry entry = store.add(id(1), "jan", 3465, roadLike(0, 0));
        store.replace(entry, roadLike(0, 0).subList(0, 40)); // generation 2, before generation 1 was written
        helper.runAll();
        store.afterWrites();
        assertEquals(0, storage.count("undo/" + id(1) + "/1"), "generation 1's files were never written");
        assertTrue(storage.count("undo/" + id(1) + "/2") > 0);
        assertTrue(entry.onDisk());
        assertTrue(store.takeUnused().contains("undo/" + id(1) + "/1"), "its folder is given up all the same");

        UndoStore.Entry removed = store.add(id(2), "jan", 3465, roadLike(100, 0));
        store.remove(removed);
        helper.runAll();
        store.afterWrites();
        assertEquals(0, storage.count("undo/" + id(2)), "a removed operation's files are never written");
    }

    @Test
    void finishWritesCompletesThemOnThisThreadForTheLastSave() throws IOException {
        FakeStorage storage = new FakeStorage();
        ManualHelper helper = new ManualHelper();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30, true, helper);
        UndoStore.Entry entry = store.add(id(1), "kai", 3465, roadLike(0, 0));
        store.finishWrites(5);
        assertTrue(entry.onDisk(), "written without the helper");
        int writes = storage.writes;
        helper.runAll();
        assertEquals(writes, storage.writes, "the helper's tasks then do nothing");
        assertEquals(1, store.flush().size());
    }

    @Test
    void aRestoredStoreReadsItsImagesFromDiskAndDeletesWhatNoIndexNames() throws IOException {
        FakeStorage storage = new FakeStorage();
        UndoStore before = new UndoStore(storage, 1L << 20, 200, 1L << 30, true);
        UndoStore.Entry entry = before.add(id(1), "ivy", 3465, roadLike(0, 0));
        UndoStore.Entry lost = before.add(id(2), "ivy", 3465, roadLike(100, 0));
        List<UndoStore.Saved> saved = before.flush();
        storage.delete("undo/" + lost.id() + "/1/" + "s.6.4.0.bin"); // one of its files went missing
        before.add(id(3), "ivy", 3465, roadLike(200, 0)); // written after the save
        before.flush();
        storage.write("undo/" + id(9) + "/s.0.4.0.bin", new byte[] {1}); // a session file from before F-5
        storage.write("undo/index.json", new byte[] {2});

        UndoStore after = new UndoStore(storage, 1L << 20, 200, 1L << 30, true);
        assertEquals(List.of(lost.id()), after.restore(saved), "the operation with a missing image is left out");
        assertEquals(1, after.size());
        UndoStore.Entry restored = after.entry(entry.id()).orElseThrow();
        assertTrue(restored.onDisk());
        assertEquals(sorted(roadLike(0, 0)), sorted(after.changes(restored)));
        assertEquals(entry.bytes(), after.totalBytes());
        after.deleteUnknown();
        assertEquals(List.of("undo/index.json"), storage.list("undo").stream().filter(f -> !f.startsWith("undo/" + entry.id() + "/1/")).toList(),
                "only the restored operation's images and the index are left");
    }

    // --- F-2b: compressed on the helper, held while read ---

    @Test
    void imagesCompressedOffTheStoreAreTheStoresOwn() throws Exception {
        UndoStore store = new UndoStore(new FakeStorage(), 1L << 20, 200, 1L << 30);
        UndoStore.Entry direct = store.add(id(1), "jo", 3465, roadLike(0, 0));
        // Compressed on another thread, as the helper does.
        java.util.concurrent.ExecutorService thread = java.util.concurrent.Executors.newSingleThreadExecutor();
        UndoStore.Images images;
        try {
            images = thread.submit(() -> store.compress(id(2), 1, 3465, roadLike(0, 0), false)).get();
        } finally {
            thread.shutdownNow();
        }
        assertEquals(630, images.blocks());
        assertFalse(images.written());
        UndoStore.Entry staged = store.add("jo", images);
        assertEquals(direct.bytes(), staged.bytes());
        assertEquals(direct.blocks(), staged.blocks());
        assertEquals(sorted(store.changes(direct)), sorted(store.changes(staged)));
        assertThrows(IllegalArgumentException.class, () -> store.add("jo", store.compress(id(3), 2, 3465, roadLike(0, 0), false)),
                "a new operation's images are generation 1");
    }

    @Test
    void imagesWrittenWhenCompressedLeaveNothingForASpillOrASave() throws IOException {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 0, 200, 1L << 30, true);
        UndoStore.Images images = store.compress(id(1), 1, 3465, roadLike(0, 0), true);
        assertTrue(images.written());
        assertEquals(images.sections(), storage.count("undo/" + id(1) + "/1"), "one file per section, in generation 1's folder");
        int writes = storage.writes;
        UndoStore.Entry entry = store.add("kim", images);
        assertTrue(entry.onDisk(), "the zero budget spilled it");
        assertEquals(writes, storage.writes, "without writing again");
        store.flush();
        assertEquals(writes, storage.writes, "and a save has nothing to write");
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(entry)));

        UndoStore.Images fewer = store.compress(id(1), store.nextGeneration(entry), 3465, roadLike(0, 0).subList(0, 40), true);
        store.replace(entry, fewer);
        assertEquals(2, entry.generation());
        assertTrue(storage.count("undo/" + id(1) + "/2") > 0);
        assertEquals(sorted(roadLike(0, 0).subList(0, 40)), sorted(store.changes(entry)));
        assertThrows(IllegalArgumentException.class, () -> store.replace(entry, store.compress(id(1), 2, 3465, roadLike(0, 0), false)),
                "images for the wrong generation");
    }

    @Test
    void aFailedWriteWhenCompressingLeavesTheImagesInMemory() {
        FakeStorage storage = new FakeStorage();
        storage.failWrites = true;
        UndoStore store = new UndoStore(storage, 1L << 20, 200, 1L << 30);
        UndoStore.Images images = store.compress(id(1), 1, 3465, roadLike(0, 0), true);
        assertFalse(images.written());
        UndoStore.Entry entry = store.add("lee", images);
        assertFalse(entry.onDisk());
        assertEquals(1, store.spillFailures(), "counted as a failed spill");
        storage.failWrites = false;
        assertEquals(sorted(roadLike(0, 0)), sorted(store.changes(entry)));
    }

    @Test
    void aHeldOperationIsNeverDroppedByTheCapAndItsImagesStayReadable() throws IOException {
        FakeStorage storage = new FakeStorage();
        UndoStore store = new UndoStore(storage, 0, 2, 1L << 30);
        List<UndoStore.Entry> pruned = new ArrayList<>();
        store.onPruned(pruned::add);
        UndoStore.Entry oldest = store.add(id(1), "max", 3465, roadLike(0, 0));
        UndoStore.Entry second = store.add(id(2), "max", 3465, roadLike(100, 0));
        UndoStore.Captured captured = store.capture(oldest);
        assertTrue(store.isHeld(oldest));
        store.add(id(3), "max", 3465, roadLike(200, 0));
        assertEquals(List.of(second), pruned, "the next oldest went instead");
        assertTrue(store.contains(oldest));
        assertEquals(sorted(roadLike(0, 0)), sorted(captured.read()), "read after the cap ran");

        store.release(oldest);
        assertFalse(store.isHeld(oldest));
        store.add(id(4), "max", 3465, roadLike(300, 0));
        assertEquals(List.of(second, oldest), pruned, "once let go it can be dropped");
        assertThrows(IOException.class, captured::read, "its files are gone now");
    }

    @Test
    void storagePathsCantLeaveTheStratosFolder() {
        assertEquals("undo/abc/s.0.4.-2.bin", Storage.checkPath("undo/abc/s.0.4.-2.bin"));
        for (String bad : new String[] {"", "../x", "undo/../../x", "/undo", "C:/undo", "undo\\x", "undo//x", "undo/.", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> Storage.checkPath(bad), bad);
        }
    }

    // --- F-1+: block-entity data in the images ---

    /** The format version an image was written with, read from its first bytes. */
    private static int versionOf(byte[] image) throws IOException {
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(image)))) {
            in.readInt();
            return in.readUnsignedShort();
        }
    }

    @Test
    void anImageWithBlockEntityDataIsVersionTwoAndOneWithoutStaysVersionOne() throws IOException {
        BlockPlan.SectionPos pos = new BlockPlan.SectionPos(0, 4, 0);
        String chest = "minecraft:chest[facing=north,type=single,waterlogged=false]";
        String items = "{Items:[{Count:3b,Slot:0b,id:\"minecraft:apple\"}],id:\"minecraft:chest\"}";
        List<BlockChange> plain = List.of(new BlockChange(1, 64, 1, "minecraft:grass_block[snowy=false]", "minecraft:stone"));
        byte[] v1 = UndoCodec.encode(3465, pos, plain);
        assertEquals(1, versionOf(v1), "written as before F-1+");
        assertEquals(plain, UndoCodec.decode(v1).changes());

        List<BlockChange> withData = List.of(new BlockChange(1, 64, 1, "minecraft:grass_block[snowy=false]", "minecraft:stone"),
                new BlockChange(2, 64, 1, chest, "minecraft:stone", items, null),
                new BlockChange(3, 64, 1, "minecraft:air", chest, null, "{Items:[]}"),
                new BlockChange(4, 64, 1, chest, chest, items, items));
        byte[] v2 = UndoCodec.encode(3465, pos, withData);
        assertEquals(2, versionOf(v2));
        assertEquals(withData, UndoCodec.decode(v2).changes(), "states and data back exactly");
    }

    @Test
    void theStoreKeepsBlockEntityDataThroughTheDisk() {
        UndoStore store = new UndoStore(new FakeStorage(), 0, 200, 1L << 30);
        String chest = "minecraft:chest[facing=north,type=single,waterlogged=false]";
        List<BlockChange> changes = new ArrayList<>(roadLike(0, 0));
        changes.set(5, new BlockChange(changes.get(5).x(), 64, changes.get(5).z(), chest, "minecraft:gray_concrete", "{Items:[]}", null));
        UndoStore.Entry entry = store.add(id(1), "lou", 3465, changes);
        assertTrue(entry.onDisk());
        assertEquals(sorted(changes), sorted(store.changes(entry)));
    }
}
