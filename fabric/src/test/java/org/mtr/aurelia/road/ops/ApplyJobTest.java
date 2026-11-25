/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.ApplyJob.Status;
import org.mtr.aurelia.road.spi.ChunkXZ;

/** The apply engine against a fake world and a fake clock (spec mod-suite-v1 §6.5). */
public class ApplyJobTest {
    private static final long MS = 1_000_000;
    private static final long BUDGET = 5 * MS;

    /** A plan of {@code count} stone blocks in a row along x from (0, 64, 0), crossing chunk borders. */
    public static BlockPlan row(int count, String state) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int x = 0; x < count; x++) {
            plan.set(x, 64, 0, state);
        }
        return plan.build();
    }

    @Test
    void noBlockStartsAfterTheBudgetIsUsedAndTheNextSliceCarriesOn() {
        FakeWorld world = new FakeWorld();
        ApplyJob job = new ApplyJob(row(1000, "minecraft:stone"));
        int steps = 0;
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            steps++;
        }
        assertSame(Status.DONE, job.status());
        assertEquals(1000, world.writes.size());
        assertEquals(1000, job.changedBlocks());
        // 0.1 ms a block: blocks start at 0, 0.1 … 4.9 ms used, so exactly 50 per 5 ms slice, 20 slices in all.
        List<ApplyJob.Slice> working = job.slices().stream().filter(s -> s.blocks() > 0).toList();
        assertEquals(20, working.size());
        working.forEach(slice -> assertEquals(50, slice.blocks()));
        working.forEach(slice -> assertTrue(slice.nanos() <= BUDGET, "slice took " + slice.nanos()));
        assertTrue(job.latestBlockStartNanos() < BUDGET);
        assertEquals(new HashSet<>(world.writes).size(), world.writes.size(), "no block written twice");
        assertTrue(steps >= 19);
    }

    @Test
    void sectionsDoneCountsTheSectionsFinishedSoFar() {
        FakeWorld world = new FakeWorld();
        ApplyJob job = new ApplyJob(row(1000, "minecraft:stone")); // x 0 to 999: 63 sections of 16 blocks (the last has 8)
        assertEquals(63, job.sectionCount());
        assertEquals(0, job.sectionsDone());
        int previous = 0;
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // 50 blocks a slice: after n slices, 50n blocks are done, which is 50n / 16 whole sections; all of them in the
            // shell (F-1+), once every block is written.
            assertEquals(job.status() == Status.SHELL ? 63 : job.changedBlocks() / 16, job.sectionsDone());
            assertTrue(job.sectionsDone() >= previous);
            previous = job.sectionsDone();
        }
        assertEquals(63, job.sectionsDone());
    }

    @Test
    void itWaitsForChunksAndReleasesThem() {
        FakeWorld world = new FakeWorld();
        world.loadAfterChecks = 3;
        ApplyJob job = new ApplyJob(row(40, "minecraft:stone")); // x 0..39: chunks 0, 1 and 2
        assertEquals(List.of(new ChunkXZ(0, 0), new ChunkXZ(1, 0), new ChunkXZ(2, 0)), job.chunks());
        assertSame(Status.LOADING, job.step(world, BUDGET, () -> world.now));
        assertEquals(3, world.retained.size());
        assertTrue(world.writes.isEmpty());
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertSame(Status.DONE, job.status());
        assertEquals(3, world.retainCalls, "each chunk is retained once");
        assertEquals(Map.of(new ChunkXZ(0, 0), 1, new ChunkXZ(1, 0), 1, new ChunkXZ(2, 0), 1), world.retainCounts);
        assertEquals(1, world.releaseCalls);
        assertTrue(world.retained.isEmpty(), "and released at the end");
    }

    /** F-7: the platform hears once which chunks an edit changed, while they are still retained; never for no change. */
    @Test
    void thePlatformHearsWhichChunksChangedBeforeTheyAreReleased() {
        FakeWorld world = new FakeWorld();
        ApplyJob job = new ApplyJob(row(40, "minecraft:stone"));
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertEquals(List.of(List.of(new ChunkXZ(0, 0), new ChunkXZ(1, 0), new ChunkXZ(2, 0))), world.changedNotices);
        assertTrue(world.noticesWhileRetained, "before the chunks are released");

        ApplyJob again = new ApplyJob(row(40, "minecraft:stone")); // every block is stone already
        while (!again.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertEquals(0, again.changedBlocks());
        assertEquals(1, world.changedNotices.size(), "an edit that changed nothing says nothing");
    }

    @Test
    void loadingRespectsTheBudgetToo() {
        FakeWorld world = new FakeWorld();
        world.retainCost = MS; // asking for a chunk costs 1 ms here
        ApplyJob job = new ApplyJob(row(320, "minecraft:stone")); // x 0..319: 20 chunks
        assertEquals(20, job.chunks().size());
        assertSame(Status.LOADING, job.step(world, BUDGET, () -> world.now));
        assertEquals(5, world.retainCounts.size(), "five 1 ms requests fit in a 5 ms slice");
        job.slices().forEach(slice -> assertTrue(slice.nanos() <= BUDGET));
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertSame(Status.DONE, job.status());
        assertEquals(20, world.retainCounts.size());
        world.retainCounts.values().forEach(count -> assertEquals(1, count));
        job.slices().forEach(slice -> assertTrue(slice.nanos() <= BUDGET, "slice took " + slice.nanos()));
        assertTrue(world.retained.isEmpty());
    }

    @Test
    void badPlansAreRefusedWithNothingWritten() {
        FakeWorld world = new FakeWorld();
        ApplyJob badState = new ApplyJob(row(5, "minecraft:nonsense_block"));
        assertSame(Status.FAILED, badState.step(world, BUDGET, () -> world.now));
        assertTrue(badState.failure().orElseThrow().contains("not a block state"));

        world.dataVersion = 3700;
        ApplyJob wrongVersion = new ApplyJob(row(5, "minecraft:stone"));
        assertSame(Status.FAILED, wrongVersion.step(world, BUDGET, () -> world.now));
        assertTrue(wrongVersion.failure().orElseThrow().contains("DataVersion"));
        world.dataVersion = 3465;

        world.blockEntities.add(FakeWorld.key(7, 64, 0));
        world.foreignBlockEntities.add(FakeWorld.key(7, 64, 0)); // F-1+: another mod's, which Stratos can't put back
        ApplyJob overForeign = new ApplyJob(row(20, "minecraft:stone"));
        while (!overForeign.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertSame(Status.FAILED, overForeign.status());
        assertSame(ApplyJob.Failure.BLOCK_ENTITY, overForeign.failureCode().orElseThrow());
        assertTrue(overForeign.failure().orElseThrow().contains("minecraft:air at (7, 64, 0), another mod's block"), overForeign.failure().orElseThrow());

        assertTrue(world.writes.isEmpty(), "nothing was written by any refused plan");
        assertTrue(world.retained.isEmpty(), "and no chunk is left retained");
    }

    @Test
    void aBlockEntityAppearingMidApplyStopsTheJobAndItsUndoRestoresWhatWasPlaced() {
        FakeWorld world = new FakeWorld();
        world.blocks.put(FakeWorld.key(3, 64, 0), "minecraft:dirt");
        world.afterWrite = () -> {
            if (world.writes.size() == 10) {
                world.blockEntities.add(FakeWorld.key(10, 64, 0)); // someone places another mod's block entity just ahead
                world.foreignBlockEntities.add(FakeWorld.key(10, 64, 0));
            }
        };
        ApplyJob job = new ApplyJob(row(30, "minecraft:stone"));
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertSame(Status.FAILED, job.status());
        assertEquals(10, job.changedBlocks());
        assertTrue(job.failure().orElseThrow().contains("10 blocks were placed and can be undone"));
        assertTrue(world.retained.isEmpty());

        world.afterWrite = () -> { };
        ApplyJob undo = new ApplyJob(job.undoPlan());
        while (!undo.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertSame(Status.DONE, undo.status());
        assertEquals("minecraft:dirt", world.blockState(3, 64, 0));
        for (int x = 0; x < 10; x++) {
            if (x != 3) {
                assertEquals("minecraft:air", world.blockState(x, 64, 0), "x " + x);
            }
        }
    }

    @Test
    void unchangedBlocksAreSkippedAndUndoRestoresEveryChangedBlock() {
        FakeWorld world = new FakeWorld();
        Map<String, String> before = new HashMap<>();
        for (int x = 0; x < 100; x++) {
            String state = x % 3 == 0 ? "minecraft:stone" : x % 3 == 1 ? "minecraft:dirt" : "minecraft:air";
            if (!state.equals("minecraft:air")) {
                world.blocks.put(FakeWorld.key(x, 64, 0), state);
            }
            before.put(FakeWorld.key(x, 64, 0), state);
        }
        ApplyJob job = new ApplyJob(row(100, "minecraft:stone"));
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        assertEquals(66, job.changedBlocks(), "the 34 stone blocks were already stone");
        assertEquals(66, world.writes.size());
        assertEquals(66, job.undoPlan().blockCount());

        ApplyJob undo = new ApplyJob(job.undoPlan());
        while (!undo.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        for (int x = 0; x < 100; x++) {
            assertEquals(before.get(FakeWorld.key(x, 64, 0)), world.blockState(x, 64, 0), "x " + x);
        }
    }

    @Test
    void theSchedulerRunsJobsInOrderAndSharesTheBudget() {
        FakeWorld world = new FakeWorld();
        ApplyScheduler scheduler = new ApplyScheduler();
        ApplyJob first = scheduler.submit(row(30, "minecraft:stone")); // 3 ms
        ApplyJob second = scheduler.submit(row(30, "minecraft:dirt")); // 3 ms more
        long used = scheduler.tick(world, BUDGET, () -> world.now);
        assertSame(Status.DONE, first.status(), "the first job finishes in this tick");
        assertSame(Status.APPLYING, second.status(), "the second starts with the 2 ms left");
        assertEquals(20, second.changedBlocks());
        assertTrue(used <= BUDGET + 100_000, "used " + used);
        scheduler.tick(world, BUDGET, () -> world.now);
        assertSame(Status.DONE, second.status());
        assertTrue(scheduler.isIdle());
        assertEquals("minecraft:dirt", world.blockState(0, 64, 0), "the second job ran after the first");
    }

    @Test
    void abortingReleasesChunksAndKeepsTheUndo() {
        FakeWorld world = new FakeWorld();
        ApplyScheduler scheduler = new ApplyScheduler();
        ApplyJob job = scheduler.submit(row(200, "minecraft:stone"));
        scheduler.tick(world, BUDGET, () -> world.now);
        assertFalse(job.status().finished());
        scheduler.abortAll(world);
        assertSame(Status.FAILED, job.status());
        assertEquals(50, job.undoPlan().blockCount());
        assertTrue(world.retained.isEmpty());
        assertTrue(scheduler.isIdle());
    }

    // --- F-1+: block entities and the outer shell ---

    private static final String CHEST = "minecraft:chest[facing=north,type=single,waterlogged=false]";
    private static final String ITEMS = "{Items:[{Count:3b,Slot:0b,id:\"minecraft:apple\"}],id:\"minecraft:chest\",x:5,y:64,z:0}";

    private static void finish(ApplyJob job, FakeWorld world) {
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
    }

    @Test
    void aChestInTheWayIsSavedWithItsDataAndItsUndoPutsItBack() {
        FakeWorld world = new FakeWorld();
        world.setBlockState(5, 64, 0, CHEST, ITEMS);
        world.writes.clear();
        ApplyJob job = new ApplyJob(row(10, "minecraft:stone"));
        finish(job, world);
        assertSame(Status.DONE, job.status());
        assertEquals("minecraft:stone", world.blockState(5, 64, 0));
        assertFalse(world.blockEntities.contains(FakeWorld.key(5, 64, 0)), "the chest is gone");
        BlockChange chest = job.changes().stream().filter(change -> change.x() == 5).findFirst().orElseThrow();
        assertEquals(new BlockChange(5, 64, 0, CHEST, "minecraft:stone", ITEMS, null), chest);
        assertFalse(job.changes().get(0).hasData(), "the other blocks had none");

        ApplyJob undo = new ApplyJob(job.undoPlan());
        finish(undo, world);
        assertSame(Status.DONE, undo.status());
        assertEquals(CHEST, world.blockState(5, 64, 0));
        assertEquals(ITEMS, world.blockEntityData(5, 64, 0), "with its items");
    }

    @Test
    void dataThatChangedSinceCountsAsAChange() {
        FakeWorld world = new FakeWorld();
        world.setBlockState(5, 64, 0, CHEST, "{Items:[]}"); // the items were taken out since
        BlockPlan plan = BlockPlan.builder(3465).set(5, 64, 0, "minecraft:stone").build();
        BlockPlan expected = BlockPlan.builder(3465).set(5, 64, 0, CHEST, ITEMS).build();
        world.writes.clear();

        ApplyJob redo = new ApplyJob(plan, expected, ApplyJob.Guard.REFUSE_CHANGED);
        finish(redo, world);
        assertSame(ApplyJob.Failure.CONFLICT, redo.failureCode().orElseThrow());
        assertTrue(redo.failure().orElseThrow().contains("with other stored data"), redo.failure().orElseThrow());
        assertTrue(world.writes.isEmpty(), "nothing written");

        ApplyJob undo = new ApplyJob(plan, expected, ApplyJob.Guard.SKIP_CHANGED);
        finish(undo, world);
        assertSame(Status.DONE, undo.status());
        assertEquals(1, undo.skippedBlocks(), "kept, as a hand edit");
        assertEquals("{Items:[]}", world.blockEntityData(5, 64, 0));
    }

    @Test
    void aPlanBlockWithDataIsWrittenWhenOnlyItsDataDiffers() {
        FakeWorld world = new FakeWorld();
        world.setBlockState(5, 64, 0, CHEST, "{Items:[]}");
        world.writes.clear();
        ApplyJob job = new ApplyJob(BlockPlan.builder(3465).set(5, 64, 0, CHEST, ITEMS).build());
        finish(job, world);
        assertEquals(List.of(new BlockChange(5, 64, 0, CHEST, CHEST, "{Items:[]}", ITEMS)), job.changes());
        assertEquals(ITEMS, world.blockEntityData(5, 64, 0));

        ApplyJob again = new ApplyJob(BlockPlan.builder(3465).set(5, 64, 0, CHEST, ITEMS).build());
        finish(again, world);
        assertEquals(0, again.changedBlocks(), "already right");
    }

    @Test
    void theOuterShellTellsTheBlocksJustOutsideTheEditAndNoneInside() {
        FakeWorld world = new FakeWorld();
        world.blocks.put(FakeWorld.key(3, 64, 0), "minecraft:stone"); // planned, but already right: not written
        ApplyJob job = new ApplyJob(row(4, "minecraft:stone"));
        finish(job, world);
        assertEquals(3, job.changedBlocks());
        Set<String> expected = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            String from = FakeWorld.key(x, 64, 0);
            for (String side : List.of(FakeWorld.key(x, 63, 0), FakeWorld.key(x, 65, 0), FakeWorld.key(x, 64, -1), FakeWorld.key(x, 64, 1))) {
                expected.add(side + "<-" + from);
            }
        }
        expected.add(FakeWorld.key(-1, 64, 0) + "<-" + FakeWorld.key(0, 64, 0));
        expected.add(FakeWorld.key(3, 64, 0) + "<-" + FakeWorld.key(2, 64, 0)); // the block left alone hears it too
        assertEquals(expected, new HashSet<>(world.neighbourUpdates));
        assertEquals(expected.size(), world.neighbourUpdates.size(), "each told once");
    }

    @Test
    void theShellCrossesSectionsAndKeepsTheBudget() {
        FakeWorld world = new FakeWorld();
        world.writeCost = 0;
        world.neighbourCost = MS; // 1 ms a neighbour
        ApplyJob job = new ApplyJob(row(40, "minecraft:stone")); // three sections along x
        int shellSlices = 0;
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            if (job.status() == Status.SHELL) {
                shellSlices++;
                assertFalse(world.retained.isEmpty(), "the chunks stay retained during the shell");
            }
        }
        assertSame(Status.DONE, job.status());
        assertEquals(40 * 4 + 2, world.neighbourUpdates.size(), "four sides of each block and the row's two ends; none across the section borders");
        assertTrue(shellSlices >= 15, "the shell spread over the slices: " + shellSlices);
        assertTrue(job.slices().stream().allMatch(slice -> slice.nanos() <= BUDGET + 6 * MS), "a block's updates may finish past the budget");
        assertTrue(world.retained.isEmpty());
    }

    @Test
    void aJobThatFailsMidwayTellsTheBlocksAroundWhatItWroteAndAStopSkipsTheRest() {
        FakeWorld world = new FakeWorld();
        BlockPlan plan = row(30, "minecraft:stone");
        BlockPlan.Builder air = BlockPlan.builder(3465);
        for (int x = 0; x < 30; x++) {
            air.set(x, 64, 0, "minecraft:air");
        }
        world.afterWrite = () -> {
            if (world.writes.size() == 10) {
                world.blocks.put(FakeWorld.key(20, 64, 0), "minecraft:dirt"); // a player builds ahead of the redo
            }
        };
        ApplyJob redo = new ApplyJob(plan, air.build(), ApplyJob.Guard.REFUSE_CHANGED);
        finish(redo, world);
        assertSame(ApplyJob.Failure.CONFLICT, redo.failureCode().orElseThrow());
        assertEquals(20, redo.changedBlocks());
        assertTrue(world.neighbourUpdates.contains(FakeWorld.key(19, 65, 0) + "<-" + FakeWorld.key(19, 64, 0)), "the written part's shell");
        assertTrue(world.retained.isEmpty());

        FakeWorld stopping = new FakeWorld();
        stopping.neighbourCost = MS;
        ApplyJob job = new ApplyJob(row(10, "minecraft:stone"));
        while (job.status() != Status.SHELL) {
            job.step(stopping, BUDGET, () -> stopping.now);
        }
        job.abort(stopping);
        assertSame(Status.DONE, job.status(), "every block was written: done, without the rest of the shell");
        assertTrue(stopping.retained.isEmpty());
    }
}
