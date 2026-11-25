/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.ApplyJob.Failure;
import org.mtr.aurelia.road.ops.ApplyJob.Guard;
import org.mtr.aurelia.road.ops.ApplyJob.Status;

/**
 * The apply engine's R-7 additions: the {@link Guard#ASK} guard (count every block not as expected, write nothing),
 * expected states that any block meets ({@link ApplyJob#ANY}), and jobs that refuse to start with nothing to change.
 */
class ApplyJobAskTest {
    private static final long BUDGET = 5_000_000;

    /** {@code count} blocks of {@code state} in a row along x from {@code (0, 64, 0)}. */
    private static BlockPlan row(int count, String state) {
        return ApplyJobTest.row(count, state);
    }

    private static Status run(FakeWorld world, ApplyJob job) {
        while (!job.step(world, BUDGET, () -> world.now).finished()) {
            // keep ticking
        }
        return job.status();
    }

    /** A world with a road of stone along x 0..9, as a road left it. */
    private static FakeWorld road() {
        FakeWorld world = new FakeWorld();
        for (int x = 0; x < 10; x++) {
            world.blocks.put(FakeWorld.key(x, 64, 0), "minecraft:stone");
        }
        return world;
    }

    @Test
    void askCountsEveryBlockChangedByHandAndWritesNothing() {
        FakeWorld world = road();
        world.blocks.put(FakeWorld.key(2, 64, 0), "minecraft:torch"); // changed by hand
        world.blocks.put(FakeWorld.key(7, 64, 0), "minecraft:air"); // broken
        world.blocks.put(FakeWorld.key(8, 64, 0), "minecraft:dirt"); // already as the edit would leave it: not counted
        ApplyJob job = ApplyJob.refusingNothing(row(10, "minecraft:dirt"), row(10, "minecraft:stone"), Guard.ASK);
        assertSame(Status.FAILED, run(world, job));
        assertEquals(Optional.of(Failure.MANUAL_EDITS), job.failureCode());
        assertEquals(2, job.manualEdits());
        assertEquals(0, job.changedBlocks());
        assertEquals(0, world.writes.size(), "nothing written");
    }

    @Test
    void askWithNothingChangedByHandWritesEverything() {
        FakeWorld world = road();
        ApplyJob job = ApplyJob.refusingNothing(row(10, "minecraft:dirt"), row(10, "minecraft:stone"), Guard.ASK);
        assertSame(Status.DONE, run(world, job));
        assertEquals(0, job.manualEdits());
        assertEquals(10, job.changedBlocks());
    }

    @Test
    void anyStateIsAlwaysAsExpected() {
        FakeWorld world = road();
        world.blocks.put(FakeWorld.key(12, 64, 0), "minecraft:grass_block"); // where the road never was
        BlockPlan.Builder expected = BlockPlan.builder(3465);
        for (int x = 0; x < 14; x++) {
            expected.set(x, 64, 0, x < 10 ? "minecraft:stone" : ApplyJob.ANY);
        }
        ApplyJob job = ApplyJob.refusingNothing(row(14, "minecraft:dirt"), expected.build(), Guard.ASK);
        assertSame(Status.DONE, run(world, job));
        assertEquals(14, job.changedBlocks(), "the blocks with any state expected are written like a new road's");
    }

    @Test
    void keepLeavesChangedBlocksAndTheirBlockEntities() {
        FakeWorld world = road();
        world.blocks.put(FakeWorld.key(3, 64, 0), "minecraft:chest[facing=north]");
        world.blockEntities.add(FakeWorld.key(3, 64, 0));
        world.foreignBlockEntities.add(FakeWorld.key(3, 64, 0)); // another mod's: Stratos can't put it back
        ApplyJob job = ApplyJob.refusingNothing(row(10, "minecraft:dirt"), row(10, "minecraft:stone"), Guard.SKIP_CHANGED);
        assertSame(Status.DONE, run(world, job), "a block kept as it is doesn't stop the edit for its block entity");
        assertEquals(9, job.changedBlocks());
        assertEquals(1, job.skippedBlocks());
        assertEquals("minecraft:chest[facing=north]", world.blockState(3, 64, 0));
    }

    @Test
    void aJobWithNothingToChangeRefusesToStart() {
        FakeWorld world = road();
        ApplyJob job = ApplyJob.refusingNothing(row(10, "minecraft:stone"), row(10, "minecraft:stone"), Guard.ASK);
        assertSame(Status.FAILED, run(world, job));
        assertEquals(Optional.of(Failure.NOTHING_TO_CHANGE), job.failureCode());
        assertEquals(0, world.writes.size());

        world.blocks.put(FakeWorld.key(4, 64, 0), "minecraft:torch"); // the only difference, kept by hand
        ApplyJob keep = ApplyJob.refusingNothing(row(10, "minecraft:stone"), row(10, "minecraft:stone"), Guard.SKIP_CHANGED);
        assertSame(Status.FAILED, run(world, keep));
        assertEquals(Optional.of(Failure.NOTHING_TO_CHANGE), keep.failureCode(), "keeping the only changed block leaves nothing to do");

        ApplyJob overwrite = ApplyJob.refusingNothing(row(10, "minecraft:stone"), null, Guard.NONE);
        assertSame(Status.DONE, run(world, overwrite));
        assertEquals(1, overwrite.changedBlocks(), "overwriting it is something to do");
    }
}
