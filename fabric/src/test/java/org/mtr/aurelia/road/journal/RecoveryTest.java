/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationJson;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.Operations.Refusal;
import org.mtr.aurelia.road.undo.Operations.Result;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.world.Migrations;
import org.mtr.aurelia.road.world.WorldData;
import org.mtr.aurelia.road.world.WorldMeta;

/**
 * Crashes and their repair (F-2+; world-data-format §5): a world in memory, the blocks Minecraft has written to disk
 * (chunk by chunk, when the test says), MTR's rails in memory and as saved, and Stratos's files. A crash keeps only what
 * is on disk; the next start repairs, so Stratos's blocks and track match its saved undo history.
 */
class RecoveryTest {
    static final String OVERWORLD = Operations.OVERWORLD;
    static final Instant T0 = Instant.parse("2026-10-09T12:00:00Z");
    static final SideEffect RAIL = new SideEffect.TrackRail(0, 65, 0, 2, 40, 65, 0, 2, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);

    /** MTR's rails: in memory, and as MTR last saved them. */
    static final class FakeTrack implements SideEffectWriter {
        final Set<SideEffect> live = new LinkedHashSet<>();
        final Set<SideEffect> saved = new LinkedHashSet<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
            List<Result> results = new ArrayList<>();
            for (SideEffect effect : effects) {
                this.calls.add("make " + effect);
                results.add(this.live.add(effect) ? Result.DONE : new Result(Outcome.ALREADY_THERE, "there already"));
            }
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
            List<Result> results = new ArrayList<>();
            for (SideEffect effect : effects) {
                this.calls.add("remove " + effect);
                results.add(this.live.remove(effect) ? Result.DONE : new Result(Outcome.NOT_THERE, "gone already"));
            }
            return CompletableFuture.completedFuture(results);
        }
    }

    final FakeStorage storage = new FakeStorage();
    final AtomicReference<Instant> now = new AtomicReference<>(T0);
    final AtomicLong ids = new AtomicLong();
    final List<String> log = new ArrayList<>();
    final FakeTrack track = new FakeTrack();
    FakeWorld world = new FakeWorld();
    /** The blocks Minecraft has written to disk, by position; and their block entities' data. */
    final Map<String, String> disk = new HashMap<>();
    final Map<String, String> diskData = new HashMap<>();

    WorldData load() {
        return WorldData.load(this.storage, new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(1, this.ids.incrementAndGet()), this.track,
                this.now::get, 3465, "stratos 0.1.0+test"), Migrations.ALL, this.log::add);
    }

    static BlockPlan row(int x0, int count, String state) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int x = x0; x < x0 + count; x++) {
            plan.set(x, 64, 0, state);
        }
        return plan.build();
    }

    static int chunkOf(String key) {
        return StrictMath.floorDiv(Integer.parseInt(key.substring(0, key.indexOf(','))), 16);
    }

    /** Minecraft writes chunks {@code from} to {@code to} (x, along the row) to disk. */
    void saveChunks(int from, int to) {
        this.disk.keySet().removeIf(key -> chunkOf(key) >= from && chunkOf(key) <= to);
        this.diskData.keySet().removeIf(key -> chunkOf(key) >= from && chunkOf(key) <= to);
        this.world.blocks.forEach((key, state) -> {
            if (chunkOf(key) >= from && chunkOf(key) <= to) {
                this.disk.put(key, state);
            }
        });
        this.world.blockEntityData.forEach((key, data) -> {
            if (chunkOf(key) >= from && chunkOf(key) <= to) {
                this.diskData.put(key, data);
            }
        });
    }

    /** A world save, as Minecraft does it: Stratos's part first, then every chunk; MTR saves too. */
    void worldSave(WorldData data) throws IOException {
        data.worldSave();
        saveChunks(-100, 100);
        this.track.saved.clear();
        this.track.saved.addAll(this.track.live);
    }

    /** The JVM dies: only what is on disk is left (and the journal as flushed). */
    void crash() {
        this.storage.crash(0);
        this.world = new FakeWorld();
        this.world.blocks.putAll(this.disk);
        this.world.blockEntityData.putAll(this.diskData);
        this.world.blockEntities.addAll(this.diskData.keySet());
        this.track.live.clear();
        this.track.live.addAll(this.track.saved);
    }

    /** The world opens again: its data loads, and the repair runs to the end. */
    WorldData restart() {
        WorldData data = load();
        repair(data);
        return data;
    }

    void repair(WorldData data) {
        data.startRepair(dimension -> dimension.equals(OVERWORLD));
        for (int tick = 0; data.isRepairing(); tick++) {
            assertTrue(tick < 10_000, "the repair finishes");
            tickAll(data);
        }
    }

    void tickAll(WorldData data) {
        for (Operations operations : data.allOperations()) {
            operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            operations.afterTick();
        }
        data.afterTick();
    }

    Result run(WorldData data, Operations operations, String owner) {
        for (int tick = 0; tick < 10_000; tick++) {
            operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            for (Result result : operations.afterTick()) {
                if (result.owner().equals(owner)) {
                    data.afterTick();
                    return result;
                }
            }
            data.afterTick();
        }
        throw new AssertionError("no result for " + owner);
    }

    /** Starts {@code owner}'s request and places {@code slices} slices of it (0.1 ms a block: 50 a slice), then stops. */
    static void placeSlices(Operations operations, FakeWorld world, int slices) {
        operations.afterTick(); // its job starts
        for (int i = 0; i < slices; i++) {
            operations.scheduler().tick(world, 5_000_000, () -> world.now);
        }
    }

    long stoneIn(int from, int to) {
        long count = 0;
        for (int x = from; x < to; x++) {
            if (this.world.blockState(x, 64, 0).equals("minecraft:stone")) {
                count++;
            }
        }
        return count;
    }

    @Test
    void anEditCutOffHalfwayIsRolledBack() {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 200, "minecraft:stone"), new UUID(7, 1));
        placeSlices(overworld, this.world, 2);
        assertEquals(100, stoneIn(0, 200), "half placed");
        saveChunks(0, 3); // Minecraft wrote some of its chunks meanwhile
        crash();
        assertEquals(64, stoneIn(0, 200), "on disk: half a road");

        WorldData restarted = load();
        assertTrue(restarted.isRepairing());
        assertEquals(Optional.of(Refusal.REPAIRING), restarted.operations(OVERWORLD).apply("alice", row(500, 1, "minecraft:dirt")).refusal());
        repair(restarted);
        assertEquals(0, stoneIn(0, 200), "rolled back");
        assertEquals(Optional.of(new Recovery.Report(1, 0, 0, 0)), restarted.takeRepairReport());
        assertEquals(Optional.empty(), restarted.takeRepairReport(), "told once");
        assertEquals(0, restarted.operations(OVERWORLD).undoDepth("alice"), "as the history: nothing saved");
        assertTrue(this.log.stream().anyMatch(line -> line.contains("rolled back")), String.join("\n", this.log));
    }

    @Test
    void aFinishedEditNotYetSavedIsRolledBack() {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        assertEquals(Optional.empty(), run(data, overworld, "alice").code());
        saveChunks(-100, 100); // Minecraft wrote every chunk, but Stratos hadn't saved
        crash();
        assertEquals(100, stoneIn(0, 100));
        WorldData restarted = restart();
        assertEquals(0, stoneIn(0, 100), "the road without its undo history is gone");
        assertEquals(new Recovery.Report(1, 0, 0, 0), restarted.repairReport().orElseThrow());
    }

    @Test
    void aSavedEditMinecraftHadntFullyWrittenIsCompleted() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        UUID road = new UUID(7, 1);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), road);
        run(data, overworld, "alice");
        data.save(); // Stratos saved
        assertTrue(new String(this.storage.read(WorldData.INDEX), StandardCharsets.UTF_8).contains("\"journal\""));
        saveChunks(0, 2); // but Minecraft had written only some chunks
        crash();
        assertEquals(48, stoneIn(0, 100));

        WorldData restarted = restart();
        assertEquals(100, stoneIn(0, 100), "completed");
        assertEquals(new Recovery.Report(0, 1, 0, 0), restarted.repairReport().orElseThrow());
        Operations again = restarted.operations(OVERWORLD);
        assertEquals(1, again.undoDepth("alice"));
        again.undo("alice");
        assertEquals(Optional.empty(), run(restarted, again, "alice").code());
        assertEquals(0, stoneIn(0, 100), "and its undo works");
    }

    @Test
    void anUndoCutOffIsRolledBackSoTheRoadMatchesItsHistory() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        run(data, overworld, "alice");
        worldSave(data);
        overworld.undo("alice");
        overworld.afterTick(); // unpacked at once (no helper)
        placeSlices(overworld, this.world, 1);
        assertEquals(50, stoneIn(0, 100), "half undone");
        saveChunks(0, 1);
        crash();
        assertEquals(68, stoneIn(0, 100));

        WorldData restarted = restart();
        assertEquals(100, stoneIn(0, 100), "the saved history says placed, so the undo is rolled back");
        assertEquals(new Recovery.Report(1, 0, 0, 1), restarted.repairReport().orElseThrow(), "the apply needed nothing");
        assertEquals(1, restarted.operations(OVERWORLD).undoDepth("alice"));
    }

    @Test
    void aCrashDuringTheRepairIsRepairedAgainAndARepairedRunQuietly() {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 200, "minecraft:stone"), new UUID(7, 1));
        placeSlices(overworld, this.world, 2);
        saveChunks(0, 6);
        crash();

        WorldData repairing = load();
        repairing.startRepair(dimension -> true);
        tickAll(repairing); // part of the repair
        assertTrue(repairing.isRepairing());
        saveChunks(0, 1);
        crash(); // during the repair
        assertTrue(stoneIn(0, 200) > 0);

        WorldData second = restart();
        assertEquals(0, stoneIn(0, 200));
        assertEquals(new Recovery.Report(1, 0, 0, 0), second.repairReport().orElseThrow());

        saveChunks(-100, 100);
        crash(); // and again, before the repair is safely saved
        WorldData third = restart();
        assertEquals(0, stoneIn(0, 200));
        assertEquals(Optional.empty(), third.repairReport(), "repaired once already: quietly");
    }

    @Test
    void aChestInTheWayComesBackWithItsItems() {
        String chest = "minecraft:chest[facing=north,type=single,waterlogged=false]";
        String items = "{Items:[{Count:3b,Slot:0b,id:\"minecraft:apple\"}],id:\"minecraft:chest\"}";
        this.world.blocks.put(FakeWorld.key(10, 64, 0), chest);
        this.world.blockEntities.add(FakeWorld.key(10, 64, 0));
        this.world.blockEntityData.put(FakeWorld.key(10, 64, 0), items);
        saveChunks(-100, 100);
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 32, "minecraft:stone"), new UUID(7, 1));
        placeSlices(overworld, this.world, 1);
        assertEquals("minecraft:stone", this.world.blockState(10, 64, 0));
        saveChunks(0, 0);
        crash();
        assertEquals("minecraft:stone", this.world.blockState(10, 64, 0));

        restart();
        assertEquals(chest, this.world.blockState(10, 64, 0));
        assertEquals(items, this.world.blockEntityData(10, 64, 0), "with its items");
    }

    @Test
    void aRolledBackRoadLosesTheTrackItMadeAndACompletedOneGetsItBack() throws IOException {
        OperationRecord.Draft withTrack = new OperationRecord.Draft("roads", "STRAIGHT", "{}", "ab".repeat(32), List.of(RAIL));
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 50, "minecraft:stone"), new UUID(7, 1), Optional.empty(), withTrack);
        run(data, overworld, "alice");
        assertTrue(this.track.live.contains(RAIL));
        this.track.saved.addAll(this.track.live); // MTR saved; Stratos didn't
        saveChunks(-100, 100);
        crash();
        restart();
        assertEquals(0, stoneIn(0, 50));
        assertFalse(this.track.live.contains(RAIL), "the rail Stratos made is removed with the road");

        this.storage.files.clear();
        this.disk.clear();
        this.diskData.clear();
        this.track.saved.clear();
        this.world = new FakeWorld();
        data = load();
        overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 50, "minecraft:stone"), new UUID(7, 2), Optional.empty(), withTrack);
        run(data, overworld, "alice");
        data.save(); // Stratos saved; MTR and Minecraft hadn't
        crash();
        assertFalse(this.track.live.contains(RAIL));
        restart();
        assertEquals(50, stoneIn(0, 50));
        assertTrue(this.track.live.contains(RAIL), "the completed road's rail is made again");
    }

    @Test
    void aRecordCutShortAtTheEndIsIgnored() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        placeSlices(overworld, this.world, 1);
        saveChunks(-100, 100);
        crash();
        String segment = this.storage.list(Journal.FOLDER).get(0);
        byte[] bytes = this.storage.read(segment);
        byte[] torn = Arrays.copyOf(bytes, bytes.length + 11);
        System.arraycopy(JournalCodec.encode(new JournalRecord.Retired(new UUID(9, 9))), 0, torn, bytes.length, 11);
        this.storage.files.put(segment, torn);
        WorldData restarted = restart();
        assertEquals(0, stoneIn(0, 100));
        assertEquals(new Recovery.Report(1, 0, 0, 0), restarted.repairReport().orElseThrow());
    }

    @Test
    void aDamagedJournalIsReportedAndNothingIsGuessed() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        placeSlices(overworld, this.world, 2);
        saveChunks(-100, 100);
        crash();
        String segment = this.storage.list(Journal.FOLDER).get(0);
        byte[] bytes = this.storage.read(segment);
        int begin = JournalCodec.encode(new JournalRecord.Begin(new UUID(0, 0), Operations.Kind.APPLY, new UUID(7, 1), OVERWORLD, "alice", "", 0))
                .length;
        bytes[begin + 30] ^= 1; // inside the first pre-image
        this.storage.files.put(segment, bytes);
        WorldData restarted = restart();
        assertEquals(1, restarted.repairReport().orElseThrow().needAttention());
        assertTrue(this.log.stream().anyMatch(line -> line.contains("is damaged after byte")), String.join("\n", this.log));
        assertEquals(100, stoneIn(0, 100), "its pre-images couldn't be read: nothing was guessed");
    }

    @Test
    void runsAreRetiredOnceSafelySavedAndTheirSegmentsGo() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), new UUID(7, 1));
        run(data, overworld, "alice");
        Journal journal = data.journal().orElseThrow();
        assertEquals(1, journal.runsKept());
        String first = Journal.path(0);
        assertTrue(this.storage.exists(first));
        this.now.set(T0.plusSeconds(1));
        worldSave(data); // lists it
        this.now.set(T0.plusSeconds(12));
        worldSave(data); // the save before this one began less than 10 s after the edit
        assertEquals(1, journal.runsKept());
        this.now.set(T0.plusSeconds(20));
        worldSave(data); // the one before (12 s) began 10 s after it: its chunks were written then
        assertEquals(0, journal.runsKept(), "retired");
        assertFalse(this.storage.exists(first), "its segment is deleted");
        assertFalse(data.wantsWorldSaves(), "nothing kept: world saves needn't reach Stratos for the journal");

        crash();
        WorldData restarted = load();
        assertFalse(restarted.isRepairing(), "a retired run is never repaired");
        assertEquals(10, stoneIn(0, 10));
    }

    @Test
    void aCleanStopDeletesTheJournalButAFailedLastSaveKeepsIt() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 10, "minecraft:stone"), new UUID(7, 1));
        run(data, overworld, "alice");
        data.stop();
        this.storage.failWrites = true;
        try {
            data.save();
        } catch (IOException expected) {
            // the last save failed
        }
        this.storage.failWrites = false;
        data.stopped();
        assertTrue(this.storage.count(Journal.FOLDER) > 0, "kept: the edit isn't safely saved");

        WorldData restarted = restart(); // the edit is rolled back, as nothing saved it
        assertEquals(0, stoneIn(0, 10));
        restarted.stop();
        restarted.save();
        restarted.stopped();
        assertEquals(0, this.storage.count(Journal.FOLDER), "a clean stop: the journal goes");
    }

    @Test
    void aRolledBackEditsSavedRecordSaysSo() throws Exception {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        UUID road = new UUID(7, 1);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), road);
        run(data, overworld, "alice");
        // A save while its images are written behind leaves it out of the index, but saves its record (F-2b).
        this.storage.write(WorldData.RECORDS + "/" + road + ".json", OperationJson.write(overworld.record(road).orElseThrow()).getBytes(StandardCharsets.UTF_8));
        saveChunks(-100, 100);
        crash();
        WorldData restarted = restart();
        assertTrue(restarted.hasUnsavedChanges());
        restarted.save();
        OperationRecord record = OperationJson.read(new String(this.storage.read(WorldData.RECORDS + "/" + road + ".json"), StandardCharsets.UTF_8));
        assertEquals(OperationRecord.Status.ROLLED_BACK, record.status());
        assertFalse(record.undoAvailable());
    }

    @Test
    void aReadOnlyWorldNeverReadsOrWritesTheJournal() throws IOException {
        WorldData data = load();
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        placeSlices(overworld, this.world, 1);
        crash();
        this.storage.write(WorldData.META, new WorldMeta(CoreInfo.FORMAT_VERSION + 1, 3465, new UUID(5, 5), "a", "a", T0, List.of()).toJson()
                .getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> files = new HashMap<>(this.storage.files);
        WorldData readOnly = load();
        assertTrue(readOnly.readOnly().isPresent());
        assertFalse(readOnly.isRepairing());
        assertEquals(Optional.empty(), readOnly.journal());
        readOnly.startRepair(dimension -> true);
        readOnly.save();
        readOnly.stopped();
        assertEquals(files.keySet(), this.storage.files.keySet(), "nothing written or deleted");
    }

    @Test
    void aRepairInADimensionThatIsntThereNeedsAttention() {
        WorldData data = load();
        Operations nether = data.operations("minecraft:the_nether");
        nether.apply("bob", row(0, 100, "minecraft:netherrack"), new UUID(7, 1));
        placeSlices(nether, this.world, 1);
        crash();
        WorldData restarted = load();
        restarted.startRepair(dimension -> false);
        for (int tick = 0; restarted.isRepairing(); tick++) {
            assertTrue(tick < 100);
            tickAll(restarted);
        }
        assertEquals(new Recovery.Report(0, 0, 1, 0), restarted.repairReport().orElseThrow());
        assertTrue(this.log.stream().anyMatch(line -> line.contains("isn't in this world")), String.join("\n", this.log));
    }

    @Test
    void theJobWaitsForItsPreimagesOnTheJournalThread() throws IOException {
        org.mtr.aurelia.road.undo.ManualHelper helper = new org.mtr.aurelia.road.undo.ManualHelper();
        WorldData data = WorldData.load(this.storage, new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(1, this.ids.incrementAndGet()),
                this.track, this.now::get, 3465, "stratos 0.1.0+test", helper), Migrations.ALL, this.log::add);
        Operations overworld = data.operations(OVERWORLD);
        overworld.apply("alice", row(0, 100, "minecraft:stone"), new UUID(7, 1));
        helper.runWork(); // its job is built
        overworld.afterTick();
        ApplyJob job = overworld.scheduler().jobs().get(0);
        overworld.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
        assertEquals(ApplyJob.Status.APPLYING, job.status());
        assertEquals(0, job.changedBlocks(), "its pre-images aren't on disk yet");
        assertEquals(0, this.storage.logFlushes);
        helper.runJournal();
        assertTrue(this.storage.logFlushes > 0);
        overworld.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
        assertTrue(job.changedBlocks() > 0, "written once they are");
        RunLog logged = JournalScan.read(this.storage).runs().get(0);
        assertEquals(7, logged.preimages().size(), "a pre-image per section, on disk");
        assertEquals(Operations.Kind.APPLY, logged.begin().orElseThrow().kind());
    }
}
