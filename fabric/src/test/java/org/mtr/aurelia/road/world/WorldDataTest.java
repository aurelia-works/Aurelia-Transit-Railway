/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.journal.Journal;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.undo.ManualHelper;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.Operations.Refusal;
import org.mtr.aurelia.road.undo.Operations.Result;

/** A world's Stratos data saved and loaded, the downgrade guard, and what is never written (task F-5). */
class WorldDataTest {
    static final String OVERWORLD = Operations.OVERWORLD;
    static final String NETHER = "minecraft:the_nether";
    static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    final FakeStorage storage = new FakeStorage();
    final FakeWorld world = new FakeWorld();
    final AtomicLong ids = new AtomicLong();
    final List<String> log = new ArrayList<>();

    WorldData.Settings settings(int dataVersion) {
        return new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(1, this.ids.incrementAndGet()), SideEffectWriter.UNAVAILABLE, () -> NOW,
                dataVersion, "stratos 0.1.0+test");
    }

    WorldData load() {
        return WorldData.load(this.storage, settings(3465), Migrations.ALL, this.log::add);
    }

    static BlockPlan row(int x0, int count, String state) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int x = x0; x < x0 + count; x++) {
            plan.set(x, 64, 0, state);
        }
        return plan.build();
    }

    /** Ticks {@code operations} until {@code owner}'s request is filed. */
    Result run(Operations operations, String owner) {
        for (int tick = 0; tick < 10_000; tick++) {
            operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            for (Result result : operations.afterTick()) {
                if (result.owner().equals(owner)) {
                    return result;
                }
            }
        }
        throw new AssertionError("no result for " + owner);
    }

    /** Starts the repair after a restart and ticks until it is done (F-2+). */
    void repair(WorldData data) {
        data.startRepair(dimension -> true);
        for (int tick = 0; data.isRepairing(); tick++) {
            assertTrue(tick < 10_000, "the repair finishes");
            for (Operations operations : data.allOperations()) {
                operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            }
            data.afterTick();
        }
    }

    Map<String, String> snapshot() {
        Map<String, String> blocks = new HashMap<>(this.world.blocks);
        blocks.values().removeIf("minecraft:air"::equals);
        return blocks;
    }

    String text(String path) throws IOException {
        return new String(this.storage.read(path), StandardCharsets.UTF_8);
    }

    @Test
    void nothingIsWrittenToAWorldNobodyEdited() throws IOException {
        WorldData data = load();
        data.operations(OVERWORLD);
        assertFalse(data.hasUnsavedChanges());
        data.save();
        assertEquals(Map.of(), this.storage.files);
        assertEquals(0, this.storage.writes);
        assertEquals(Optional.empty(), data.meta());
    }

    @Test
    void historiesAndRecordsComeBackAfterARestartAndUndoStillWorks() throws IOException {
        Map<String, String> before = snapshot();
        WorldData first = load();
        Operations overworld = first.operations(OVERWORLD);
        UUID road = new UUID(7, 1);
        UUID second = new UUID(7, 2);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), road, Optional.empty(),
                new OperationRecord.Draft("roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", "ab".repeat(32), List.of()));
        run(overworld, "alice");
        Map<String, String> afterRoad = snapshot();
        overworld.apply("alice", row(200, 10, "minecraft:dirt"), second);
        run(overworld, "alice");
        overworld.undo("alice");
        run(overworld, "alice"); // second is undone: on alice's redo history
        Operations nether = first.operations(NETHER);
        nether.apply("bob", row(500, 5, "minecraft:netherrack"), new UUID(7, 3));
        run(nether, "bob");
        assertTrue(first.hasUnsavedChanges());
        first.save();
        assertFalse(first.hasUnsavedChanges());
        assertTrue(this.storage.exists(WorldData.META));
        assertEquals(CoreInfo.FORMAT_VERSION, first.meta().orElseThrow().formatVersion());
        assertTrue(text(WorldData.META).contains("\"createdBy\": \"stratos 0.1.0+test\""));
        first.stopped(); // the server stopped cleanly after that save (F-2+): the journal goes
        assertEquals(0, this.storage.count(Journal.FOLDER));

        WorldData restarted = load(); // the server starts again
        Operations again = restarted.operations(OVERWORLD);
        assertEquals(1, again.undoDepth("alice"));
        assertEquals(1, again.redoDepth("alice"));
        assertEquals(1, restarted.operations(NETHER).undoDepth("bob"));
        assertEquals(overworld.record(road), again.record(road), "the record came back as it was");
        assertEquals(overworld.record(second), again.record(second));
        assertEquals(OperationRecord.Status.UNDONE, again.record(second).orElseThrow().status());
        assertEquals(List.of(new Operations.HistoryEntry(again.record(road).orElseThrow(), false),
                new Operations.HistoryEntry(again.record(second).orElseThrow(), true)), again.history("alice"));
        assertFalse(restarted.hasUnsavedChanges(), "loading changes nothing");

        again.undo("alice");
        assertEquals(Optional.empty(), run(again, "alice").code());
        Map<String, String> expected = new HashMap<>(before);
        for (int x = 500; x < 505; x++) {
            expected.put(FakeWorld.key(x, 64, 0), "minecraft:netherrack"); // bob's, in the one fake world both dimensions share
        }
        assertEquals(expected, snapshot(), "the road is undone exactly, from images saved before the restart");
        again.redo("alice");
        run(again, "alice");
        Map<String, String> redone = new HashMap<>(afterRoad);
        for (int x = 500; x < 505; x++) {
            redone.put(FakeWorld.key(x, 64, 0), "minecraft:netherrack");
        }
        assertEquals(redone, snapshot());
        assertEquals(List.of(), this.log);
    }

    @Test
    void aSaveKeepsTheSnapshotOfEveryPaletteARecordNamesAndALoadReadsThemBack() throws IOException {
        // R-9 (world-data-format §2): palettes/<snapshot id>.json, before the records; never written twice.
        Palette vanilla = BuiltInPalettes.vanilla();
        WorldData data = load();
        data.addPalettes(List.of(vanilla));
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), new UUID(7, 1), Optional.empty(),
                new OperationRecord.Draft("roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", "ab".repeat(32), List.of(), List.of(vanilla.snapshotId())));
        run(overworld, "alice");
        overworld.apply("alice", row(100, 10, "minecraft:stone"), new UUID(7, 2), Optional.empty(),
                new OperationRecord.Draft("roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", "ab".repeat(32), List.of(), List.of("gone@000000000000")));
        run(overworld, "alice");
        data.save();
        String path = WorldData.PALETTES + "/" + vanilla.snapshotId() + ".json";
        assertTrue(this.storage.exists(path), "the snapshot is saved with the record that names it");
        assertTrue(text(path).contains("\"minecraft:polished_deepslate\""), "readable JSON");
        assertEquals(List.of("[stratos] no palette snapshot gone@000000000000 to save with the records that name it"), this.log);
        int mark = this.storage.written.size();
        overworld.apply("alice", row(200, 10, "minecraft:stone"), new UUID(7, 3), Optional.empty(),
                new OperationRecord.Draft("roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", "ab".repeat(32), List.of(), List.of(vanilla.snapshotId())));
        run(overworld, "alice");
        data.save();
        List<String> second = List.copyOf(this.storage.written.subList(mark, this.storage.written.size()));
        assertTrue(second.stream().noneMatch(written -> written.startsWith(WorldData.PALETTES)), "the snapshot is on disk already: " + second);

        this.storage.write(WorldData.PALETTES + "/vanilla@ffffffffffff.json", this.storage.read(path)); // a file whose name isn't its content
        this.storage.write(WorldData.PALETTES + "/broken@000000000000.json", "{".getBytes(StandardCharsets.UTF_8));
        this.log.clear();
        WorldData restarted = load();
        assertEquals(List.of(vanilla), restarted.paletteSnapshots(), "only the snapshot that is what its name says");
        assertEquals(2, this.log.stream().filter(line -> line.contains("palette snapshot")).count(), "the two others are logged and ignored: " + this.log);
    }

    @Test
    void aSaveWritesOnlyWhatChanged() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), new UUID(7, 1));
        run(overworld, "alice");
        data.save();
        int writes = this.storage.writes;
        data.save();
        assertEquals(writes, this.storage.writes, "nothing changed, nothing written");
        overworld.apply("alice", row(100, 10, "minecraft:stone"), new UUID(7, 2));
        run(overworld, "alice");
        data.save();
        // the new record, its one image, the index and meta.json
        assertEquals(writes + 4, this.storage.writes);
    }

    @Test
    void theLastSavedIndexStillMatchesTheFilesAfterACrash() throws IOException {
        Map<String, String> before = snapshot();
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        run(overworld, "alice");
        data.save();
        Map<String, String> saved = snapshot();
        // An undo stops halfway, so the operation is re-recorded (a new generation), and the server crashes before saving.
        overworld.undo("alice");
        overworld.afterTick(); // its job is queued
        overworld.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
        assertEquals(Optional.of("STOPPED"), overworld.stop(this.world).get(0).code());
        assertEquals(2, data.store().entry(new UUID(7, 1)).orElseThrow().generation(), "re-recorded");
        overworld.apply("alice", row(300, 10, "minecraft:stone"), new UUID(7, 2));
        run(overworld, "alice");
        // After the crash the world's blocks are as they were saved, and so is Stratos's data.
        this.world.blocks.clear();
        saved.forEach(this.world.blocks::put);

        WorldData restarted = load();
        Operations again = restarted.operations(OVERWORLD);
        assertEquals(1, again.undoDepth("alice"));
        assertEquals(Optional.empty(), again.record(new UUID(7, 2)), "the operation after the save is gone, as its blocks are");
        // F-2+: the journal still has the stopped undo and the edit after the save; the world is already as saved.
        assertEquals(Optional.of(Refusal.REPAIRING), again.undo("alice").refusal());
        repair(restarted);
        assertEquals(Optional.empty(), restarted.repairReport(), "nothing needed changing, so nobody is told");
        assertEquals(saved, snapshot());
        again.undo("alice");
        assertEquals(Optional.empty(), run(again, "alice").code());
        assertEquals(before, snapshot(), "undone from the generation the saved index names");
        assertTrue(this.storage.list("undo").stream().noneMatch(file -> file.contains(new UUID(7, 2).toString())),
                "images written after the save were deleted on load");
    }

    @Test
    void theCapsPruningIsSavedInTheRecords() throws IOException {
        WorldData data = WorldData.load(this.storage, new WorldData.Settings(0, 2, 1L << 30, () -> new UUID(1, this.ids.incrementAndGet()),
                SideEffectWriter.UNAVAILABLE, () -> NOW, 3465, "stratos 0.1.0+test"), Migrations.ALL, this.log::add);
        Operations overworld = data.operations(OVERWORLD);
        for (int i = 1; i <= 3; i++) {
            overworld.apply("alice", row(i * 100, 10, "minecraft:stone"), new UUID(7, i));
            run(overworld, "alice");
        }
        data.save();
        assertTrue(text(WorldData.RECORDS + "/" + new UUID(7, 1) + ".json").contains("\"undoAvailable\": false"));
        assertTrue(text(WorldData.RECORDS + "/" + new UUID(7, 3) + ".json").contains("\"undoAvailable\": true"));
        assertFalse(this.storage.exists("undo/" + new UUID(7, 1)), "its images went once the index stopped naming them");
    }

    /** A world whose work, image writes and saves wait for {@code helper}. */
    WorldData loadWith(ManualHelper helper) {
        return WorldData.load(this.storage, new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(1, this.ids.incrementAndGet()),
                SideEffectWriter.UNAVAILABLE, () -> NOW, 3465, "stratos 0.1.0+test", helper), Migrations.ALL, this.log::add);
    }

    /** Ticks {@code operations} until {@code owner}'s request has its answer, the helper doing its work (not its disk tasks). */
    void runWork(Operations operations, ManualHelper helper, String owner) {
        for (int tick = 0; operations.isBusy(owner); tick++) {
            assertTrue(tick < 1000);
            operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            operations.afterTick();
            helper.runWork();
        }
    }

    @Test
    void aSaveIsWrittenByTheHelperAndLeavesOutAnEditStillBeingFiled() throws IOException {
        ManualHelper helper = new ManualHelper();
        WorldData data = loadWith(helper);
        Operations overworld = data.operations(OVERWORLD);
        UUID first = new UUID(7, 1);
        UUID second = new UUID(7, 2);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), first);
        runWork(overworld, helper, "alice");
        helper.runDisk(); // its image written behind
        overworld.afterTick();
        Operations.Handle handle = overworld.apply("alice", row(100, 10, "minecraft:stone"), second).handle().orElseThrow();
        while (handle.stage() != Operations.Stage.FILING) {
            helper.runWork();
            overworld.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            overworld.afterTick();
        }
        assertEquals(1, helper.work.size(), "the second edit's filing waits for the helper");

        int writes = this.storage.writes;
        data.save(); // an autosave now
        assertEquals(writes, this.storage.writes, "the server thread wrote nothing");
        assertEquals(1, data.savesInFlight());
        assertEquals(1, helper.disk.size(), "the helper writes the save");
        helper.runDisk();
        data.afterTick();
        assertEquals(0, data.savesInFlight());
        String index = text(WorldData.INDEX);
        assertTrue(index.contains(first.toString()) && !index.contains(second.toString()), index);
        assertFalse(this.storage.exists(WorldData.RECORDS + "/" + second + ".json"));
        assertTrue(this.storage.exists(WorldData.META));

        helper.runWork(); // the second edit is filed
        assertEquals(1, overworld.afterTick().size());
        assertTrue(data.hasUnsavedChanges());
        helper.runDisk(); // its image
        overworld.afterTick();
        writes = this.storage.writes;
        data.save();
        helper.runDisk();
        data.afterTick();
        assertTrue(text(WorldData.INDEX).contains(second.toString()));
        assertEquals(writes + 3, this.storage.writes, "the record, the index and meta.json: its image was written already");
        assertFalse(data.hasUnsavedChanges());
        assertEquals(List.of(), this.log);
    }

    @Test
    void aSaveLeavesOutAnOperationWhoseImagesAreStillBeingWritten() throws IOException {
        ManualHelper helper = new ManualHelper();
        WorldData data = loadWith(helper);
        Operations overworld = data.operations(OVERWORLD);
        UUID road = new UUID(7, 1);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), road);
        runWork(overworld, helper, "alice");
        assertEquals(1, data.store().writesInFlight(), "its image is still to be written");

        data.save();
        assertEquals(1, data.lastSaveLeftOut());
        assertEquals(2, helper.disk.size(), "the image, then the save");
        helper.runDisk();
        data.afterTick();
        String index = text(WorldData.INDEX);
        assertFalse(index.contains(road.toString()), "the index names only what was on disk when the save began: " + index);
        assertTrue(this.storage.exists(WorldData.RECORDS + "/" + road + ".json"), "the record is saved all the same");
        assertTrue(data.hasUnsavedChanges(), "so the history is saved next time");

        data.save();
        assertEquals(0, data.lastSaveLeftOut());
        helper.runDisk();
        data.afterTick();
        assertTrue(text(WorldData.INDEX).contains(road.toString()));
        assertFalse(data.hasUnsavedChanges());

        WorldData restarted = load(); // the history came back
        assertEquals(1, restarted.operations(OVERWORLD).undoDepth("alice"));
    }

    @Test
    void stoppingFinishesTheWritingAndTheLastSaveIsCompleteAndWrittenAtOnce() throws IOException {
        ManualHelper helper = new ManualHelper();
        WorldData data = loadWith(helper);
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), new UUID(7, 1));
        runWork(overworld, helper, "alice");
        data.save(); // left out: its image isn't written; this save waits for the helper too
        assertEquals(1, data.savesInFlight());

        data.stop();
        assertEquals(0, data.savesInFlight(), "the save in flight was written on this thread");
        assertEquals(0, data.store().writesInFlight(), "and the image");
        data.save(); // the world's last save
        int writes = this.storage.writes;
        helper.runDisk();
        assertEquals(writes, this.storage.writes, "the helper's tasks, taken over, do nothing late");
        assertTrue(text(WorldData.INDEX).contains(new UUID(7, 1).toString()), "complete, without the helper");
        assertFalse(data.hasUnsavedChanges());
        data.stopped(); // F-2+: the server has stopped; the journal goes
        assertEquals(0, this.storage.count(Journal.FOLDER));

        WorldData restarted = load();
        assertEquals(1, restarted.operations(OVERWORLD).undoDepth("alice"));
        assertEquals(List.of(), this.log);
    }

    /** Puts a minimal world's data on disk: one saved road, by a Stratos of {@code formatVersion}. */
    void savedWorldWith(String metaJson) throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), new UUID(7, 1));
        run(overworld, "alice");
        data.save();
        data.stopped(); // a clean stop (F-2+)
        this.storage.write(WorldData.META, metaJson.getBytes(StandardCharsets.UTF_8));
    }

    void assertReadOnly(WorldData.ReadOnly why) throws IOException {
        Map<String, byte[]> files = new HashMap<>(this.storage.files);
        int writes = this.storage.writes;
        int deletes = this.storage.deletes;
        this.log.clear();
        WorldData data = load();
        assertEquals(Optional.of(why), data.readOnly());
        Operations overworld = data.operations(OVERWORLD);
        assertTrue(overworld.isReadOnly());
        assertEquals(Optional.of(Refusal.WORLD_READ_ONLY), overworld.apply("alice", row(50, 5, "minecraft:dirt")).refusal());
        assertEquals(Optional.of(Refusal.WORLD_READ_ONLY), overworld.undo("alice").refusal());
        assertEquals(Optional.of(Refusal.WORLD_READ_ONLY), overworld.redo("alice").refusal());
        assertFalse(data.hasUnsavedChanges());
        data.save();
        assertEquals(writes, this.storage.writes, "nothing written");
        assertEquals(deletes, this.storage.deletes, "nothing deleted, not even tmp/");
        assertEquals(files.keySet(), this.storage.files.keySet());
        assertEquals(1, this.log.size(), "logged once: " + this.log);
        assertTrue(this.log.get(0).contains("read-only (" + why + ")"), this.log.get(0));
    }

    @Test
    void aNewerFormatOpensReadOnlyEvenWithFieldsThisStratosDoesntKnow() throws IOException {
        savedWorldWith("{\"formatVersion\": " + (CoreInfo.FORMAT_VERSION + 1) + ", \"lastWrittenBy\": \"stratos 9.0.0+mc1.20.1\", \"newField\": [1]}");
        this.storage.write("tmp/write-1.tmp", new byte[] {1});
        assertReadOnly(WorldData.ReadOnly.NEWER_FORMAT);
        assertTrue(this.log.get(0).contains("stratos 9.0.0+mc1.20.1"), this.log.get(0));
    }

    @Test
    void aNewerGameOpensReadOnly() throws IOException {
        savedWorldWith(new WorldMeta(CoreInfo.FORMAT_VERSION, 3700, new UUID(5, 5), "stratos 0.1.0+test", "stratos 0.1.0+test", NOW, List.of())
                .toJson());
        assertReadOnly(WorldData.ReadOnly.NEWER_DATA_VERSION);
    }

    @Test
    void anUnreadableMetaOpensReadOnly() throws IOException {
        savedWorldWith("{\"formatVersion\": 1, \"dataVersion\": ");
        assertReadOnly(WorldData.ReadOnly.UNREADABLE);
        this.storage.files.clear(); // a second world
        this.world.blocks.clear();
        savedWorldWith("{\"formatVersion\": 1}");
        assertReadOnly(WorldData.ReadOnly.UNREADABLE);
    }

    @Test
    void anUnreadableIndexStartsAnEmptyHistoryAndKeepsTheFiles() throws IOException {
        savedWorldWith(new WorldMeta(CoreInfo.FORMAT_VERSION, 3465, new UUID(5, 5), "a", "a", NOW, List.of()).toJson());
        this.storage.write(WorldData.INDEX, "{\"operations\": 7}".getBytes(StandardCharsets.UTF_8));
        long images = this.storage.count("undo");
        WorldData data = load();
        assertEquals(Optional.empty(), data.readOnly());
        assertEquals(0, data.operations(OVERWORLD).undoDepth("alice"));
        assertEquals(images, this.storage.count("undo"), "the images are kept");
        assertEquals(1, this.log.size());
        assertTrue(this.log.get(0).contains("undo history can't be read"), this.log.get(0));
    }

    @Test
    void anUnreadableRecordLeavesItsOperationWithoutOne() throws IOException {
        savedWorldWith(new WorldMeta(CoreInfo.FORMAT_VERSION, 3465, new UUID(5, 5), "a", "a", NOW, List.of()).toJson());
        this.storage.write(WorldData.RECORDS + "/" + new UUID(7, 1) + ".json", "{".getBytes(StandardCharsets.UTF_8));
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        assertEquals(1, overworld.undoDepth("alice"), "it can still be undone");
        assertEquals(Optional.empty(), overworld.record(new UUID(7, 1)));
        assertTrue(this.log.get(0).contains("can't be read"), this.log.get(0));
    }

    @Test
    void anOlderWorldIsMigratedAndAMissingStepMakesItReadOnly() throws IOException {
        // There is no formatVersion 0; this world claims it, and no step migrates from it.
        savedWorldWith(new WorldMeta(0, 3465, new UUID(5, 5), "a", "a", NOW, List.of()).toJson());
        assertReadOnly(WorldData.ReadOnly.MIGRATION_FAILED);

        Migration zeroToOne = new Migration() {
            @Override
            public int from() {
                return 0;
            }

            @Override
            public void migrate(org.mtr.aurelia.road.spi.Storage files) {
            }
        };
        WorldData data = WorldData.load(this.storage, settings(3465), List.of(zeroToOne), this.log::add);
        assertEquals(Optional.empty(), data.readOnly());
        assertEquals(CoreInfo.FORMAT_VERSION, data.meta().orElseThrow().formatVersion());
        assertEquals(1, data.meta().orElseThrow().migrations().size());
        assertEquals(1, data.operations(OVERWORLD).undoDepth("alice"), "the history is read after the migration");
    }
}
