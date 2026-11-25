/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.spi.SideEffectWriter.Outcome;
import org.mtr.aurelia.road.undo.Operations.Refusal;
import org.mtr.aurelia.road.undo.Operations.Result;

/**
 * Deleting and rebuilding a road as operations (task R-7; owner, 2026-10-09): the ground from the road's undo images,
 * the blocks of joined roads left, the hand-edit question and its answers, the track's removing, keeping and making, and
 * undo and redo of each.
 */
class OperationsReplaceTest {
    private static final long BUDGET = 5_000_000;
    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";
    private static final String GRASS = "minecraft:grass_block";
    private static final SideEffect RAIL_1 = new SideEffect.TrackRail(0, 65, 0, 2, 40, 65, 0, 2, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);
    private static final SideEffect RAIL_2 = new SideEffect.TrackRail(40, 65, 4, 6, 0, 65, 4, 6, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);

    private final FakeWorld world = new FakeWorld();
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicLong clock = new AtomicLong();
    private final FakeWriter writer = new FakeWriter();
    private Operations operations = operations(200);

    private Operations operations(int maxOperations) {
        return new Operations(new ApplyScheduler(), new UndoStore(new FakeStorage(), 0, maxOperations, 1L << 30), () -> new UUID(0,
                this.nextId.incrementAndGet()), this.writer, () -> Instant.ofEpochSecond(this.clock.incrementAndGet()));
    }

    /** Rails made and removed, by the "game". */
    private static final class FakeWriter implements SideEffectWriter {
        final Set<SideEffect> rails = new HashSet<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
            this.calls.add("make " + effects.size());
            List<Result> results = new ArrayList<>();
            effects.forEach(effect -> results.add(this.rails.add(effect) ? Result.DONE : new Result(Outcome.ALREADY_THERE, "there")));
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
            this.calls.add("remove " + effects.size());
            List<Result> results = new ArrayList<>();
            effects.forEach(effect -> results.add(this.rails.remove(effect) ? Result.DONE : new Result(Outcome.NOT_THERE, "gone")));
            return CompletableFuture.completedFuture(results);
        }
    }

    /** The ground: dirt along x 0..19 at y 64, a tree's leaves above x 3, grass at x 20..21. */
    private void ground() {
        for (int x = 0; x < 20; x++) {
            this.world.blocks.put(FakeWorld.key(x, 64, 0), DIRT);
        }
        this.world.blocks.put(FakeWorld.key(3, 65, 0), "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]");
        this.world.blocks.put(FakeWorld.key(20, 64, 0), GRASS);
        this.world.blocks.put(FakeWorld.key(21, 64, 0), GRASS);
    }

    /** A road from x0 up to (not including) x1: stone at y 64, air above (its clearance). */
    private static BlockPlan road(int x0, int x1) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int x = x0; x < x1; x++) {
            plan.set(x, 64, 0, STONE);
            plan.set(x, 65, 0, "minecraft:air");
        }
        return plan.build();
    }

    private Map<String, String> snapshot() {
        Map<String, String> blocks = new HashMap<>(this.world.blocks);
        blocks.values().removeIf("minecraft:air"::equals);
        return blocks;
    }

    private Result run(String owner) {
        for (int tick = 0; tick < 10_000; tick++) {
            this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now);
            for (Result result : this.operations.afterTick()) {
                if (result.owner().equals(owner)) {
                    return result;
                }
            }
        }
        throw new AssertionError("no result for " + owner);
    }

    private static OperationRecord.Draft draft(String tool, List<SideEffect> rails) {
        return new OperationRecord.Draft("roads", tool, "{\"tool\":\"" + tool + "\"}", "ab".repeat(32), rails);
    }

    /** Places {@code plan} as alice's road, with {@code rails}; its id. */
    private UUID place(BlockPlan plan, List<SideEffect> rails) {
        UUID id = UUID.randomUUID();
        assertTrue(this.operations.apply("alice", plan, id, Optional.empty(), draft("STRAIGHT", rails)).isAccepted());
        assertEquals(Optional.empty(), run("alice").code());
        return id;
    }

    private Result replace(String owner, BlockPlan plan, UUID road, List<UUID> chain, Optional<BlockPlan> asPlaced, Set<Long> keep, boolean delete,
            ApplyJob.Guard guard, List<SideEffect> rails, UUID id) {
        Operations.Request request = this.operations.replace(owner, plan, new Replacement(road, chain, asPlaced, asPlaced, keep, delete, guard), id,
                draft(delete ? "DELETE" : "REBUILD", rails));
        assertTrue(request.isAccepted(), "refused: " + request.refusal());
        return run(owner);
    }

    private static BlockPlan none() {
        return BlockPlan.builder(3465).build();
    }

    private OperationRecord record(UUID id) {
        return this.operations.record(id).orElseThrow();
    }

    @Test
    void deletePutsBackTheGroundBlockForBlockAndItsUndoBringsTheRoadBack() {
        ground();
        Map<String, String> before = snapshot();
        UUID road = place(road(0, 20), List.of(RAIL_1, RAIL_2));
        Map<String, String> placed = snapshot();
        assertEquals(Set.of(RAIL_1, RAIL_2), this.writer.rails);

        UUID delete = UUID.randomUUID();
        Result deleted = replace("alice", none(), road, List.of(road), Optional.empty(), Set.of(), true, ApplyJob.Guard.ASK, List.of(), delete);
        assertEquals(Optional.empty(), deleted.code());
        assertEquals(before, snapshot(), "dirt and the tree's leaves are back");
        assertEquals(Set.of(), this.writer.rails, "its track is gone");
        assertEquals(List.of(RAIL_1, RAIL_2), record(delete).removed());
        assertEquals(List.of(RAIL_1, RAIL_2), record(road).owned(), "the road's own record still holds its rails, for when it comes back");
        assertEquals(2, this.operations.undoDepth("alice"));

        this.operations.undo("alice");
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals(placed, snapshot(), "undo brings the road back");
        assertEquals(Set.of(RAIL_1, RAIL_2), this.writer.rails, "and its track");

        this.operations.redo("alice");
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals(before, snapshot());
        assertEquals(Set.of(), this.writer.rails);

        this.operations.undo("alice");
        run("alice");
        this.operations.undo("alice"); // now the road itself
        run("alice");
        assertEquals(before, snapshot());
        assertEquals(Set.of(), this.writer.rails, "the road's undo removes the rails the delete's undo made again");
    }

    @Test
    void handEditsAreCountedFirstThenKeptOrOverwritten() {
        ground();
        UUID road = place(road(0, 20), List.of());
        this.world.blocks.put(FakeWorld.key(5, 64, 0), "minecraft:air"); // a kerb broken
        this.world.blocks.put(FakeWorld.key(9, 65, 0), "minecraft:lantern[hanging=false,waterlogged=false]"); // a lamp on the footway
        Map<String, String> edited = snapshot();

        UUID asked = UUID.randomUUID();
        Result ask = replace("alice", none(), road, List.of(road), Optional.of(road(0, 20)), Set.of(), true, ApplyJob.Guard.ASK, List.of(), asked);
        assertEquals(Optional.of("MANUAL_EDITS"), ask.code());
        assertEquals(1, ask.job().orElseThrow().manualEdits(), "the kerb counts; the lamp doesn't: a delete never writes where it stands");
        assertEquals(edited, snapshot(), "nothing written");
        assertEquals(Optional.empty(), this.operations.record(asked), "a question leaves no record");
        assertEquals(1, this.operations.undoDepth("alice"));

        UUID kept = UUID.randomUUID();
        Result keep = replace("alice", none(), road, List.of(road), Optional.of(road(0, 20)), Set.of(), true, ApplyJob.Guard.SKIP_CHANGED, List.of(),
                kept);
        assertEquals(Optional.empty(), keep.code());
        assertEquals("minecraft:air", this.world.blockState(5, 64, 0), "the broken kerb stays broken: the ground isn't put back there");
        assertEquals("minecraft:lantern[hanging=false,waterlogged=false]", this.world.blockState(9, 65, 0), "the lamp stays");
        assertEquals(DIRT, this.world.blockState(4, 64, 0), "the rest is ground");

        this.operations.undo("alice");
        run("alice");
        UUID overwritten = UUID.randomUUID();
        Result overwrite = replace("alice", none(), road, List.of(road), Optional.of(road(0, 20)), Set.of(), true, ApplyJob.Guard.NONE, List.of(),
                overwritten);
        assertEquals(Optional.empty(), overwrite.code());
        assertEquals(DIRT, this.world.blockState(5, 64, 0), "the ground is back where the kerb was broken");
        assertEquals("minecraft:lantern[hanging=false,waterlogged=false]", this.world.blockState(9, 65, 0),
                "a delete only puts back what the road changed: the lamp's block was air before the road too");
    }

    @Test
    void rebuildRepairsKeepsTheTrackItHasAndMakesTheTrackItLost() {
        ground();
        UUID road = place(road(0, 20), List.of(RAIL_1, RAIL_2));
        Map<String, String> placed = snapshot();
        this.world.blocks.put(FakeWorld.key(5, 64, 0), "minecraft:air"); // broken
        this.writer.rails.remove(RAIL_2); // a player took a rail away with MTR's brush

        Result ask = replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false, ApplyJob.Guard.ASK,
                List.of(RAIL_1, RAIL_2), UUID.randomUUID());
        assertEquals(Optional.of("MANUAL_EDITS"), ask.code());
        assertEquals(1, ask.job().orElseThrow().manualEdits());

        UUID rebuild = UUID.randomUUID();
        this.writer.calls.clear();
        Result rebuilt = replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false, ApplyJob.Guard.NONE,
                List.of(RAIL_1, RAIL_2), rebuild);
        assertEquals(Optional.empty(), rebuilt.code());
        assertEquals(placed, snapshot(), "repaired");
        assertEquals(1, rebuilt.job().orElseThrow().changedBlocks());
        assertEquals(List.of("make 2"), this.writer.calls, "nothing of the road's to remove; both rails asked for");
        assertEquals(Set.of(RAIL_1, RAIL_2), this.writer.rails);
        assertEquals(List.of(RAIL_2), record(rebuild).owned(), "the rail it made again");
        assertEquals(List.of(RAIL_1), record(rebuild).inherited(), "the rail the road still had");
        assertEquals(List.of(RAIL_2, RAIL_1), record(rebuild).roadEffects());

        this.operations.undo("alice");
        run("alice");
        assertEquals("minecraft:air", this.world.blockState(5, 64, 0), "undo puts the broken block back");
        assertEquals(Set.of(RAIL_1), this.writer.rails, "and removes only the rail it made");

        this.operations.redo("alice");
        run("alice");
        assertEquals(Set.of(RAIL_1, RAIL_2), this.writer.rails);
        assertEquals(List.of(RAIL_2), record(rebuild).owned());
    }

    @Test
    void aRebuildAsksAboutABlockOnTheRoadWhenItKnowsThePlanAsPlaced() {
        ground();
        UUID road = place(road(0, 20), List.of());
        String lantern = "minecraft:lantern[hanging=false,waterlogged=false]";
        this.world.blocks.put(FakeWorld.key(9, 65, 0), lantern); // on the footway, where the road's clearance was air already
        Result ask = replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false, ApplyJob.Guard.ASK, List.of(),
                UUID.randomUUID());
        assertEquals(Optional.of("MANUAL_EDITS"), ask.code());
        assertEquals(1, ask.job().orElseThrow().manualEdits());
        assertEquals(lantern, this.world.blockState(9, 65, 0));

        // Planned by an older Stratos (no plan as placed): the road never changed that block, so it is written like a new road's.
        Result older = replace("alice", road(0, 20), road, List.of(road), Optional.empty(), Set.of(), false, ApplyJob.Guard.ASK, List.of(),
                UUID.randomUUID());
        assertEquals(Optional.empty(), older.code());
        assertEquals("minecraft:air", this.world.blockState(9, 65, 0));
    }

    @Test
    void aRebuildWithNothingToChangeLeavesNoRecord() {
        ground();
        UUID road = place(road(0, 20), List.of(RAIL_1));
        UUID rebuild = UUID.randomUUID();
        Result result = replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false, ApplyJob.Guard.ASK,
                List.of(RAIL_1), rebuild);
        assertEquals(Optional.of("NOTHING_TO_CHANGE"), result.code());
        assertEquals(Optional.empty(), this.operations.record(rebuild));
        assertEquals(List.of("make 1", "make 1"), this.writer.calls, "placing it, then the rebuild asking for its track");
        assertEquals(List.of(), result.sideEffects(), "and is all there: kept, nothing to report");
        assertEquals(1, this.operations.undoDepth("alice"));
    }

    @Test
    void aRebuildWithNoBlockToChangeStillPutsBackMissingTrackForTheRoad() {
        ground();
        UUID road = place(road(0, 20), List.of(RAIL_1, RAIL_2));
        this.writer.rails.remove(RAIL_2); // it failed to build, or a player removed it at a shared node
        UUID rebuild = UUID.randomUUID();
        Result result = replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false, ApplyJob.Guard.ASK,
                List.of(RAIL_1, RAIL_2), rebuild);
        assertEquals(Optional.of("NOTHING_TO_CHANGE"), result.code(), "no block to change, so no new operation");
        assertEquals(Optional.empty(), this.operations.record(rebuild));
        assertEquals(Set.of(RAIL_1, RAIL_2), this.writer.rails, "the missing rail is made again");
        assertEquals(List.of(RAIL_1, RAIL_2), record(road).owned(), "still the road's, as placed");

        this.operations.undo("alice");
        run("alice");
        assertEquals(Set.of(), this.writer.rails, "undoing the road removes it with the rest");
    }

    @Test
    void theGroundComesFromTheFirstOperationThatChangedEachBlockAndARebuildsLeftoversGoBack() {
        ground();
        Map<String, String> before = snapshot();
        UUID road = place(road(0, 20), List.of(RAIL_1));
        // A rebuild by a newer Stratos that plans the road two blocks longer, over the grass, and drops its first block.
        UUID rebuild = UUID.randomUUID();
        assertEquals(Optional.empty(), replace("alice", road(1, 22), road, List.of(road), Optional.empty(), Set.of(), false, ApplyJob.Guard.ASK,
                List.of(RAIL_1), rebuild).code());
        assertEquals(DIRT, this.world.blockState(0, 64, 0), "a block the new plan no longer has goes back to the ground");
        assertEquals(STONE, this.world.blockState(21, 64, 0));

        UUID delete = UUID.randomUUID();
        assertEquals(Optional.empty(), replace("alice", none(), road, List.of(road, rebuild), Optional.empty(), Set.of(), true, ApplyJob.Guard.ASK,
                List.of(), delete).code());
        assertEquals(before, snapshot(), "dirt from the placing, grass from the rebuild, leaves from the placing");
        assertEquals(Set.of(), this.writer.rails);
        assertEquals(List.of(RAIL_1), record(delete).removed(), "the rail the rebuild inherited goes too");
    }

    @Test
    void anEditMovesTheRoadWithItsTrackAsOneOperationAndUndoPutsItBackExactly() {
        // R-10 (owner, 2026-10-13): an edit is a rebuild with a new plan. The road moves from x 0..19 to x 10..21, over the
        // grass, where a lamp stands that the road never touched: it is written over like a new road's block, unasked.
        ground();
        String lantern = "minecraft:lantern[hanging=false,waterlogged=false]";
        this.world.blocks.put(FakeWorld.key(21, 65, 0), lantern);
        Map<String, String> before = snapshot();
        UUID road = place(road(0, 20), List.of(RAIL_1));
        Map<String, String> placed = snapshot();

        UUID edit = UUID.randomUUID();
        this.writer.calls.clear();
        assertEquals(Optional.empty(), replace("alice", road(10, 22), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false,
                ApplyJob.Guard.ASK, List.of(RAIL_2), edit).code(), "nothing changed by hand on the old road: no question");
        for (int x = 0; x < 10; x++) {
            assertEquals(DIRT, this.world.blockState(x, 64, 0), "the old road's blocks the new one hasn't go back to the ground");
        }
        assertEquals("minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]", this.world.blockState(3, 65, 0));
        assertEquals(STONE, this.world.blockState(21, 64, 0), "the new road over the grass");
        assertEquals("minecraft:air", this.world.blockState(21, 65, 0), "and its clearance over the lamp");
        assertEquals(List.of("remove 1", "make 1"), this.writer.calls, "the old track goes first, then the new is made");
        assertEquals(Set.of(RAIL_2), this.writer.rails);
        assertEquals(List.of(RAIL_1), record(edit).removed());
        assertEquals(List.of(RAIL_2), record(edit).owned());
        assertEquals(2, this.operations.undoDepth("alice"), "one operation");

        this.operations.undo("alice");
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals(placed, snapshot(), "undo puts the road back where it was, block for block");
        assertEquals(Set.of(RAIL_1), this.writer.rails, "with its track");
        assertEquals(lantern, before.get(FakeWorld.key(21, 65, 0)));

        this.operations.redo("alice");
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals(Set.of(RAIL_2), this.writer.rails);
        this.operations.undo("alice");
        run("alice");
        this.operations.undo("alice");
        run("alice");
        assertEquals(before, snapshot(), "undoing the placing too leaves the ground, the lamp included");
        assertEquals(Set.of(), this.writer.rails);
    }

    @Test
    void aHandEditARebuildOverwroteDoesntComeBackWithTheGround() {
        ground();
        UUID road = place(road(0, 20), List.of());
        String lantern = "minecraft:lantern[hanging=false,waterlogged=false]";
        this.world.blocks.put(FakeWorld.key(9, 65, 0), lantern); // put on the road by hand
        UUID rebuild = UUID.randomUUID();
        assertEquals(Optional.empty(), replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false,
                ApplyJob.Guard.NONE, List.of(), rebuild).code(), "the rebuild overwrote it with the road's air");
        Result deleted = replace("alice", none(), road, List.of(road, rebuild), Optional.of(road(0, 20)), Set.of(), true, ApplyJob.Guard.ASK,
                List.of(), UUID.randomUUID());
        assertEquals(Optional.empty(), deleted.code());
        assertEquals("minecraft:air", this.world.blockState(9, 65, 0), "the ground there is the road's air, which the placing found: not the lantern");
        assertEquals(DIRT, this.world.blockState(9, 64, 0));
    }

    @Test
    void theBlocksOfAJoinedRoadAreLeft() {
        ground();
        UUID road = place(road(0, 20), List.of());
        Set<Long> joined = new HashSet<>();
        for (int x = 19; x < 30; x++) { // a road joined at its end shares its last row
            joined.add(BlockChange.positionKey(x, 64, 0));
            joined.add(BlockChange.positionKey(x, 65, 0));
        }
        replace("alice", none(), road, List.of(road), Optional.empty(), joined, true, ApplyJob.Guard.ASK, List.of(), UUID.randomUUID());
        assertEquals(DIRT, this.world.blockState(18, 64, 0));
        assertEquals(STONE, this.world.blockState(19, 64, 0), "the joint row stays the joined road's");
    }

    @Test
    void aDeleteNeedsTheImagesOfEveryOperationThatBuiltTheRoadButARebuildDoesnt() {
        this.operations = operations(1);
        ground();
        UUID road = place(road(0, 20), List.of());
        place(road(100, 102), List.of()); // the cap of one drops the road's images
        assertEquals(Optional.of(Refusal.ROAD_GONE), this.operations.replace("alice", none(), new Replacement(road, List.of(UUID.randomUUID()),
                Optional.empty(), Optional.empty(), Set.of(), true, ApplyJob.Guard.ASK), UUID.randomUUID(), draft("DELETE", List.of())).refusal(), "an unknown operation");
        assertEquals(Optional.of(Refusal.ROAD_TOO_OLD), this.operations.replace("alice", none(), new Replacement(road, List.of(road),
                Optional.empty(), Optional.empty(), Set.of(), true, ApplyJob.Guard.ASK), UUID.randomUUID(), draft("DELETE", List.of())).refusal());
        this.world.blocks.put(FakeWorld.key(5, 64, 0), "minecraft:air");
        UUID rebuild = UUID.randomUUID();
        assertEquals(Optional.empty(), replace("alice", road(0, 20), road, List.of(road), Optional.of(road(0, 20)), Set.of(), false,
                ApplyJob.Guard.NONE, List.of(), rebuild).code(), "a rebuild needs no ground");
        assertEquals(STONE, this.world.blockState(5, 64, 0));
    }

    @Test
    void onlyTheRoadsOwnerMayEditItAndOnlyWhileItIsPlaced() {
        ground();
        UUID road = place(road(0, 20), List.of());
        assertEquals(Optional.of(Refusal.NOT_YOUR_ROAD), this.operations.replace("bob", none(), new Replacement(road, List.of(road),
                Optional.empty(), Optional.empty(), Set.of(), true, ApplyJob.Guard.ASK), UUID.randomUUID(), draft("DELETE", List.of())).refusal());
        this.operations.undo("alice");
        run("alice");
        assertEquals(Optional.of(Refusal.ROAD_GONE), this.operations.replace("alice", none(), new Replacement(road, List.of(road),
                Optional.empty(), Optional.empty(), Set.of(), true, ApplyJob.Guard.ASK), UUID.randomUUID(), draft("DELETE", List.of())).refusal());
    }
}
