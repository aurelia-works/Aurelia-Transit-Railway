/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.ApplyJob.Status;

/** The apply engine with a journal (F-2+; world-data-format §5): pre-images first, then the blocks. */
class ApplyJobJournalTest {
    private static final long BUDGET = 5_000_000;

    /** A journal in memory: tickets go on disk only when the test says. */
    static final class FakeJournal implements JobJournal {
        record Part(long ticket, BlockPlan.SectionPos section, List<BlockChange> changes) {
        }

        final List<Part> parts = new ArrayList<>();
        final Map<BlockPlan.SectionPos, long[]> done = new LinkedHashMap<>();
        long next;
        long onDisk;
        boolean failed;

        @Override
        public long preimage(BlockPlan.SectionPos section, int dataVersion, List<BlockChange> changes) {
            this.parts.add(new Part(++this.next, section, List.copyOf(changes)));
            return this.next;
        }

        @Override
        public boolean onDisk(long ticket) {
            return ticket <= this.onDisk;
        }

        @Override
        public boolean failed() {
            return this.failed;
        }

        @Override
        public void sectionDone(BlockPlan.SectionPos section, long[] written) {
            long[] bits = this.done.computeIfAbsent(section, s -> new long[64]);
            for (int i = 0; i < 64; i++) {
                bits[i] |= written[i];
            }
        }

        void allOnDisk() {
            this.onDisk = this.next;
        }

        int bitCount() {
            int count = 0;
            for (long[] bits : this.done.values()) {
                for (long word : bits) {
                    count += Long.bitCount(word);
                }
            }
            return count;
        }
    }

    static Status step(ApplyJob job, FakeWorld world) {
        return job.step(world, BUDGET, () -> world.now);
    }

    @Test
    void aSectionIsWrittenOnlyOnceItsPreimageIsOnDisk() {
        FakeWorld world = new FakeWorld();
        world.writeCost = 0; // the whole plan fits a slice: only the journal holds it back
        ApplyJob job = new ApplyJob(ApplyJobTest.row(100, "minecraft:stone")); // x 0 to 99: 7 sections
        FakeJournal journal = new FakeJournal();
        job.journal(journal);
        assertSame(Status.APPLYING, step(job, world), "checked, and waiting for the first pre-image");
        assertEquals(7, journal.parts.size(), "one pre-image per section");
        assertEquals(List.of(), world.writes, "nothing written before its pre-image is on disk");
        BlockChange first = journal.parts.get(0).changes().get(0);
        assertEquals(new BlockChange(0, 64, 0, "minecraft:air", "minecraft:stone"), first, "as found, and as planned");

        journal.onDisk = journal.parts.get(0).ticket();
        assertSame(Status.APPLYING, step(job, world));
        assertEquals(16, world.writes.size(), "the first section only");
        journal.allOnDisk();
        assertSame(Status.DONE, step(job, world));
        assertEquals(100, world.writes.size());
        assertEquals(7, journal.done.size(), "every section reported done");
        assertEquals(100, journal.bitCount(), "a bit for every block written");
    }

    @Test
    void blocksAlreadyRightAreLeftOutOfThePreimage() {
        FakeWorld world = new FakeWorld();
        for (int x = 0; x < 10; x++) {
            world.blocks.put(FakeWorld.key(x, 64, 0), "minecraft:stone");
        }
        ApplyJob job = new ApplyJob(ApplyJobTest.row(16, "minecraft:stone"));
        FakeJournal journal = new FakeJournal();
        job.journal(journal);
        step(job, world);
        assertEquals(6, journal.parts.get(0).changes().size());
        journal.allOnDisk();
        while (!step(job, world).finished()) {
            // on
        }
        assertEquals(6, job.changedBlocks());
    }

    @Test
    void aBlockChangedBetweenCheckingAndPlacingWaitsForItsNewPreimage() {
        FakeWorld world = new FakeWorld();
        world.writeCost = 0;
        ApplyJob job = new ApplyJob(ApplyJobTest.row(32, "minecraft:stone"));
        FakeJournal journal = new FakeJournal();
        job.journal(journal);
        step(job, world); // checked
        world.blocks.put(FakeWorld.key(5, 64, 0), "minecraft:water"); // water flowed in after checking
        world.blocks.put(FakeWorld.key(20, 64, 0), "minecraft:dirt"); // and a player put dirt here
        journal.allOnDisk();
        assertSame(Status.LATE, step(job, world), "the two changed blocks wait for their new pre-images");
        assertEquals(30, world.writes.size());
        assertEquals(2, job.lateBlocks());
        assertEquals(4, journal.parts.size(), "two sections, then a late part for each");
        assertEquals("minecraft:water", journal.parts.get(2).changes().get(0).before(), "noted again as it is now");
        world.blocks.put(FakeWorld.key(20, 64, 0), "minecraft:sand"); // and changed again before it could be written
        journal.allOnDisk();
        assertSame(Status.DONE, step(job, world));
        assertEquals("minecraft:stone", world.blockState(5, 64, 0), "written once its new pre-image was on disk");
        assertEquals("minecraft:sand", world.blockState(20, 64, 0), "changed yet again: left as it is");
        assertEquals(1, job.changedWhilePlacing());
        assertTrue(job.changes().contains(new BlockChange(5, 64, 0, "minecraft:water", "minecraft:stone")), "its undo puts the water back");
        assertEquals(31, journal.bitCount());
    }

    @Test
    void aJournalThatCantBeWrittenStopsTheJob() {
        FakeWorld world = new FakeWorld();
        world.writeCost = 0;
        ApplyJob before = new ApplyJob(ApplyJobTest.row(32, "minecraft:stone"));
        FakeJournal broken = new FakeJournal();
        before.journal(broken);
        step(before, world);
        broken.failed = true;
        assertSame(Status.FAILED, step(before, world));
        assertEquals(Optional.of(ApplyJob.Failure.JOURNAL_FAILED), before.failureCode());
        assertEquals(List.of(), world.writes, "nothing written");

        ApplyJob midway = new ApplyJob(ApplyJobTest.row(32, "minecraft:stone"));
        FakeJournal failing = new FakeJournal();
        midway.journal(failing);
        step(midway, world);
        failing.onDisk = failing.parts.get(0).ticket();
        step(midway, world); // the first section
        failing.failed = true;
        while (!step(midway, world).finished()) {
            // the shell of what was written, then the failure
        }
        assertEquals(Optional.of(ApplyJob.Failure.JOURNAL_FAILED), midway.failureCode());
        assertEquals(16, midway.changedBlocks(), "what was placed can be undone");
    }

    @Test
    void aGuardedJobNotesOnlyTheBlocksInTheirExpectedState() {
        FakeWorld world = new FakeWorld();
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        BlockPlan.Builder expected = BlockPlan.builder(3465);
        for (int x = 0; x < 4; x++) {
            plan.set(x, 64, 0, "minecraft:air");
            expected.set(x, 64, 0, "minecraft:stone");
            world.blocks.put(FakeWorld.key(x, 64, 0), x == 2 ? "minecraft:dirt" : "minecraft:stone"); // x 2 changed by hand
        }
        ApplyJob undo = new ApplyJob(plan.build(), expected.build(), ApplyJob.Guard.SKIP_CHANGED);
        FakeJournal journal = new FakeJournal();
        undo.journal(journal);
        step(undo, world);
        assertEquals(3, journal.parts.get(0).changes().size(), "the hand edit isn't noted: it won't be written");
        journal.allOnDisk();
        assertSame(Status.DONE, step(undo, world));
        assertEquals(1, undo.skippedBlocks());
        assertEquals("minecraft:dirt", world.blockState(2, 64, 0));
    }

    @Test
    void aRepairJobWritesOnlyWhereItsExpectedStateIsAndRefusesNothing() {
        FakeWorld world = new FakeWorld();
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        BlockPlan.Builder expected = BlockPlan.builder(3465);
        for (int x = 0; x < 4; x++) {
            plan.set(x, 64, 0, "minecraft:air");
            expected.set(x, 64, 0, "minecraft:stone");
        }
        world.blocks.put(FakeWorld.key(0, 64, 0), "minecraft:stone");
        world.blocks.put(FakeWorld.key(1, 64, 0), "minecraft:chest"); // another mod's block entity, in a real game
        world.blockEntities.add(FakeWorld.key(1, 64, 0));
        world.foreignBlockEntities.add(FakeWorld.key(1, 64, 0));
        world.blocks.put(FakeWorld.key(3, 64, 0), "minecraft:stone");
        ApplyJob repair = ApplyJob.repair(plan.build(), expected.build());
        while (!step(repair, world).finished()) {
            // on
        }
        assertSame(Status.DONE, repair.status(), "never refused");
        assertEquals("minecraft:air", world.blockState(0, 64, 0));
        assertEquals("minecraft:chest", world.blockState(1, 64, 0), "left as it is");
        assertEquals("minecraft:air", world.blockState(2, 64, 0));
        assertEquals("minecraft:air", world.blockState(3, 64, 0));
        assertEquals(2, repair.changedBlocks());
        assertEquals(2, repair.skippedBlocks());
    }
}
