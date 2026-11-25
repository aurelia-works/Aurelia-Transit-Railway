/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.spi.SideEffectWriter.Outcome;
import org.mtr.aurelia.road.undo.Operations.Kind;
import org.mtr.aurelia.road.undo.Operations.Refusal;
import org.mtr.aurelia.road.undo.Operations.Result;

/**
 * Per-player undo and redo on top of the apply engine and the undo store (spec mod-suite-v1 §6.6, task F-2), with
 * operation records and side effects (task G-9).
 */
class OperationsTest {
    private static final long BUDGET = 5_000_000;

    private final FakeWorld world = new FakeWorld();
    private final FakeStorage storage = new FakeStorage();
    private final AtomicLong nextId = new AtomicLong();
    private Operations operations = new Operations(new ApplyScheduler(), new UndoStore(this.storage, 0, 200, 1L << 30),
            () -> new UUID(0, this.nextId.incrementAndGet()));

    /** {@code count} blocks of {@code state} in a row along x from {@code (x0, 64, 0)}. */
    private static BlockPlan row(int x0, int count, String state) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int x = x0; x < x0 + count; x++) {
            plan.set(x, 64, 0, state);
        }
        return plan.build();
    }

    /** Ticks until {@code owner}'s request is filed, and returns its result. */
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

    /** The world's blocks, ignoring air (the fake world stores air explicitly once a block is set back to it). */
    private Map<String, String> snapshot() {
        Map<String, String> blocks = new HashMap<>(this.world.blocks);
        blocks.values().removeIf("minecraft:air"::equals);
        return blocks;
    }

    @Test
    void undoThenRedoRestoresBothStatesExactly() {
        this.world.blocks.put(FakeWorld.key(3, 64, 0), "minecraft:dirt");
        this.world.blocks.put(FakeWorld.key(7, 64, 0), "minecraft:stone"); // already stone: not changed
        Map<String, String> before = snapshot();

        assertTrue(this.operations.apply("alice", row(0, 100, "minecraft:stone")).isAccepted());
        Result applied = run("alice");
        assertEquals(Kind.APPLY, applied.kind());
        assertEquals(99, applied.job().orElseThrow().changedBlocks());
        Map<String, String> after = snapshot();
        assertEquals(1, this.operations.undoDepth("alice"));
        assertTrue(this.storage.count("undo") > 0, "the zero memory budget put the images on disk");

        assertTrue(this.operations.undo("alice").isAccepted());
        Result undone = run("alice");
        assertEquals(Optional.empty(), undone.code());
        assertEquals(0, undone.keptByHand());
        assertEquals(before, snapshot());
        assertEquals(0, this.operations.undoDepth("alice"));
        assertEquals(1, this.operations.redoDepth("alice"));

        assertTrue(this.operations.redo("alice").isAccepted());
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals(after, snapshot());
        assertEquals(1, this.operations.undoDepth("alice"));
        assertEquals(0, this.operations.redoDepth("alice"));
    }

    @Test
    void aNewEditClearsTheRedoHistory() {
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        run("alice");
        this.operations.undo("alice");
        run("alice");
        assertEquals(1, this.operations.redoDepth("alice"));
        this.operations.apply("alice", row(50, 10, "minecraft:dirt"));
        run("alice");
        assertEquals(0, this.operations.redoDepth("alice"));
        assertEquals(Optional.of(Refusal.NOTHING_TO_REDO), this.operations.redo("alice").refusal());
        assertEquals(Optional.of(Refusal.NOTHING_TO_UNDO), this.operations.undo("bob").refusal());
    }

    @Test
    void aPlayerWithARequestRunningIsBusy() {
        this.operations.apply("alice", row(0, 1000, "minecraft:stone"));
        this.operations.afterTick();
        this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now); // 50 of 1,000 blocks
        assertTrue(this.operations.isBusy("alice"));
        assertEquals(Optional.of(Refusal.BUSY), this.operations.undo("alice").refusal());
        assertEquals(Optional.of(Refusal.BUSY), this.operations.apply("alice", row(5000, 1, "minecraft:dirt")).refusal());
        assertTrue(this.operations.apply("bob", row(2000, 5, "minecraft:dirt")).isAccepted(), "another player isn't held up");
        run("alice");
        assertFalse(this.operations.isBusy("alice"));
    }

    @Test
    void eachPlayerHasTheirOwnHistory() {
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        run("alice");
        this.operations.apply("bob", row(100, 10, "minecraft:dirt"));
        run("bob");
        this.operations.undo("bob");
        run("bob");
        assertEquals("minecraft:air", this.world.blockState(100, 64, 0), "bob's road is undone");
        assertEquals("minecraft:stone", this.world.blockState(0, 64, 0), "alice's is untouched");
        this.operations.undo("alice");
        run("alice");
        assertEquals("minecraft:air", this.world.blockState(0, 64, 0));
    }

    @Test
    void handEditsAreKeptAndReported() {
        this.operations.apply("alice", row(0, 20, "minecraft:stone"));
        run("alice");
        this.world.blocks.put(FakeWorld.key(5, 64, 0), "minecraft:gold_block"); // a player changes one block by hand

        this.operations.undo("alice");
        Result undone = run("alice");
        assertEquals(1, undone.keptByHand());
        assertEquals(Optional.empty(), undone.code());
        assertEquals("minecraft:gold_block", this.world.blockState(5, 64, 0), "the hand edit survives");
        for (int x = 0; x < 20; x++) {
            if (x != 5) {
                assertEquals("minecraft:air", this.world.blockState(x, 64, 0), "x " + x);
            }
        }

        this.operations.redo("alice");
        assertEquals(Optional.empty(), run("alice").code(), "redo leaves the kept block out, so it doesn't conflict");
        assertEquals("minecraft:gold_block", this.world.blockState(5, 64, 0));
        assertEquals("minecraft:stone", this.world.blockState(4, 64, 0));
    }

    @Test
    void redoIsRefusedWithNothingWrittenIfABlockChangedSinceTheUndo() {
        this.operations.apply("alice", row(0, 20, "minecraft:stone"));
        run("alice");
        this.operations.undo("alice");
        run("alice");
        this.world.blocks.put(FakeWorld.key(12, 64, 0), "minecraft:oak_planks"); // built on after the undo
        int writes = this.world.writes.size();

        this.operations.redo("alice");
        Result redone = run("alice");
        assertEquals(Optional.of("REDO_CONFLICT"), redone.code());
        assertEquals(writes, this.world.writes.size(), "nothing was written");
        assertEquals(1, this.operations.redoDepth("alice"), "the operation stays redoable once the conflict is cleared");
    }

    @Test
    void anUndoStoppedHalfwaySplitsIntoItsDoneAndUndoneParts() {
        Map<String, String> before = snapshot();
        this.operations.apply("alice", row(0, 100, "minecraft:stone"));
        run("alice");
        Map<String, String> after = snapshot();

        this.operations.undo("alice");
        this.operations.afterTick(); // its job is built (at once here) and queued
        this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now); // 50 restored
        Result stopped = this.operations.stop(this.world).get(0); // the server stops
        assertEquals(Optional.of("STOPPED"), stopped.code());
        assertEquals(1, this.operations.undoDepth("alice"), "the 50 not yet undone stay undoable");
        assertEquals(1, this.operations.redoDepth("alice"), "the 50 undone are redoable");

        this.operations.undo("alice");
        run("alice");
        assertEquals(before, snapshot());
        this.operations.redo("alice");
        run("alice");
        this.operations.redo("alice");
        run("alice");
        assertEquals(after, snapshot());
    }

    @Test
    void operationsDroppedByTheCapCantBeUndone() {
        this.operations = new Operations(new ApplyScheduler(), new UndoStore(this.storage, 0, 2, 1L << 30), () -> new UUID(0, this.nextId.incrementAndGet()));
        for (int i = 0; i < 3; i++) {
            this.operations.apply("alice", row(i * 100, 10, "minecraft:stone"));
            run("alice");
        }
        assertEquals(2, this.operations.undoDepth("alice"));
        for (int i = 0; i < 2; i++) {
            this.operations.undo("alice");
            run("alice");
        }
        assertEquals(Optional.of(Refusal.NOTHING_TO_UNDO), this.operations.undo("alice").refusal());
        assertEquals("minecraft:stone", this.world.blockState(0, 64, 0), "the oldest operation stays in the world");
    }

    @Test
    void unreadableImagesAreReported() {
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        run("alice");
        this.storage.files.clear(); // the images vanished
        int writes = this.world.writes.size();
        assertTrue(this.operations.undo("alice").isAccepted(), "from F-2b the images are read on the helper");
        Result undone = run("alice");
        assertEquals(Optional.of(Operations.UNDO_DATA_UNREADABLE), undone.code());
        assertEquals(Optional.empty(), undone.job(), "no job was built");
        assertEquals(writes, this.world.writes.size(), "nothing was placed");
        assertEquals(1, this.operations.undoDepth("alice"), "the operation stays where it was");
        assertFalse(this.operations.isBusy("alice"));
    }

    @Test
    void anOnlyWherePlanWritesOnlyBlocksInTheirStateAndUndoCoversOnlyThose() {
        // R-6's clear markers: asphalt back where the wool still is; a node put in a marker spot by hand stays.
        for (int x = 0; x < 4; x++) {
            this.world.blocks.put(FakeWorld.key(x, 64, 0), "minecraft:blue_wool");
        }
        this.world.blocks.put(FakeWorld.key(2, 64, 0), "mtr:rail[facing=true,is_22_5=false,is_45=false,is_connected=false,waterlogged=false]");
        UUID id = new UUID(9, 9);
        assertTrue(this.operations.apply("alice", row(0, 4, "minecraft:gray_concrete"), id,
                Optional.of(row(0, 4, "minecraft:blue_wool"))).isAccepted());
        Result applied = run("alice");
        assertEquals(Optional.empty(), applied.code());
        assertEquals(3, applied.job().orElseThrow().changedBlocks());
        assertEquals(1, applied.job().orElseThrow().skipped().size());
        assertEquals("minecraft:gray_concrete", this.world.blocks.get(FakeWorld.key(0, 64, 0)));
        assertTrue(this.world.blocks.get(FakeWorld.key(2, 64, 0)).startsWith("mtr:rail["), "the node is never touched");

        assertTrue(this.operations.undo("alice").isAccepted());
        Result undone = run("alice");
        assertEquals(Optional.of(id), undone.operationId());
        assertEquals(0, undone.keptByHand(), "the node was never part of the operation");
        assertEquals("minecraft:blue_wool", this.world.blocks.get(FakeWorld.key(3, 64, 0)));
        assertTrue(this.world.blocks.get(FakeWorld.key(2, 64, 0)).startsWith("mtr:rail["));
        assertTrue(this.operations.redo("alice").isAccepted());
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals("minecraft:gray_concrete", this.world.blocks.get(FakeWorld.key(3, 64, 0)));
    }

    @Test
    void anApplyCanBeGivenItsOperationIdUpFront() {
        UUID chosen = new UUID(42, 42);
        assertTrue(this.operations.apply("alice", row(0, 10, "minecraft:stone"), chosen).isAccepted());
        Result applied = run("alice");
        assertEquals(Optional.of(chosen), applied.operationId());
        assertTrue(this.operations.undo("alice").isAccepted());
        assertEquals(Optional.of(chosen), run("alice").operationId(), "the undo history entry has the chosen id");
    }

    // --- G-9: records and side effects ---

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
    private static final String HASH = "ab".repeat(32);
    private static final SideEffect RAIL_1 = new SideEffect.TrackRail(0, 65, 0, 2, 40, 65, 0, 2, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);
    private static final SideEffect RAIL_2 = new SideEffect.TrackRail(40, 65, 4, 6, 0, 65, 4, 6, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);

    /**
     * A writer standing in for Stratos Transit: it keeps the "rails in the game", and notes for every call what it was
     * asked and what the world's block at (0, 64, 0) was then, so tests can tell the blocks came first.
     */
    private final class FakeWriter implements SideEffectWriter {
        final Set<SideEffect> rails = new HashSet<>();
        final Set<SideEffect> failing = new HashSet<>();
        final List<String> calls = new ArrayList<>();
        final List<String> dimensions = new ArrayList<>();
        boolean available = true;
        boolean answerLater;
        CompletableFuture<Void> later;

        @Override
        public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
            this.dimensions.add(dimension);
            return answer("make", effects);
        }

        @Override
        public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
            this.dimensions.add(dimension);
            return answer("remove", effects);
        }

        private CompletableFuture<List<Result>> answer(String what, List<SideEffect> effects) {
            this.calls.add(what + " " + effects.size() + " over " + OperationsTest.this.world.blockState(0, 64, 0));
            List<Result> results = new ArrayList<>();
            for (SideEffect effect : effects) {
                if (!this.available) {
                    results.add(new Result(Outcome.UNAVAILABLE, "no Stratos Transit"));
                } else if (this.failing.contains(effect)) {
                    results.add(new Result(Outcome.FAILED, "MTR said no"));
                } else if (what.equals("make")) {
                    results.add(this.rails.add(effect) ? Result.DONE : new Result(Outcome.ALREADY_THERE, "a player's rail"));
                } else {
                    results.add(this.rails.remove(effect) ? Result.DONE : new Result(Outcome.NOT_THERE, "gone already"));
                }
            }
            if (this.answerLater) {
                this.later = new CompletableFuture<>();
                return this.later.thenApply(ignored -> results);
            }
            return CompletableFuture.completedFuture(results);
        }
    }

    private final FakeWriter writer = new FakeWriter();

    private Operations withWriter(int maxOperations) {
        return new Operations(new ApplyScheduler(), new UndoStore(this.storage, 0, maxOperations, 1L << 30),
                () -> new UUID(0, this.nextId.incrementAndGet()), this.writer, () -> NOW);
    }

    private static OperationRecord.Draft draft(List<SideEffect> sideEffects) {
        return new OperationRecord.Draft("roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", HASH, sideEffects);
    }

    private OperationRecord record(UUID id) {
        return this.operations.record(id).orElseThrow();
    }

    /** Applies ten blocks of stone from (0, 64, 0) for alice with {@code sideEffects}; returns the operation id. */
    private UUID applyRoad(List<SideEffect> sideEffects) {
        UUID id = new UUID(7, this.nextId.incrementAndGet());
        assertTrue(this.operations.apply("alice", row(0, 10, "minecraft:stone"), id, Optional.empty(), draft(sideEffects)).isAccepted());
        return id;
    }

    private static List<Outcome> outcomes(Result result) {
        return result.sideEffects().stream().map(outcome -> outcome.result().outcome()).toList();
    }

    @Test
    void anApplyIsRecordedAndItsUndoAndRedoUpdateTheRecord() {
        this.operations = withWriter(200);
        UUID id = applyRoad(List.of());
        Result applied = run("alice");
        assertEquals(List.of(), applied.sideEffects());
        OperationRecord record = record(id);
        assertEquals(new OperationRecord(id, Operations.OVERWORLD, "roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", HASH, CoreInfo.version(), "alice", NOW,
                OperationRecord.Status.APPLIED, true, record.sections(), List.of(), List.of()), record);
        assertEquals(1, record.sections().size(), "ten blocks along x from 0 are all in one section");
        assertEquals(new OperationRecord.SectionHash(0, 4, 0, record.sections().get(0).hash()), record.sections().get(0));
        assertEquals(SectionHashes.of(applied.job().orElseThrow().changes()), record.sections());

        this.operations.undo("alice");
        run("alice");
        assertEquals(OperationRecord.Status.UNDONE, record(id).status());
        this.operations.redo("alice");
        run("alice");
        assertEquals(OperationRecord.Status.APPLIED, record(id).status());
        assertEquals(List.of(record(id)), this.operations.records());
        assertEquals(List.of(), this.writer.calls, "no side effects, no calls");
    }

    @Test
    void anApplyWithoutADraftIsRecordedAndAStoppedOneIsPartlyApplied() {
        this.operations = withWriter(200);
        UUID plain = new UUID(1, 1);
        this.operations.apply("bob", row(500, 3, "minecraft:dirt"), plain);
        run("bob");
        assertEquals("none", record(plain).module());
        assertEquals(OperationRecord.Status.APPLIED, record(plain).status());

        UUID id = new UUID(7, 7);
        this.operations.apply("alice", row(0, 1000, "minecraft:stone"), id, Optional.empty(), draft(List.of(RAIL_1)));
        this.operations.afterTick(); // its job is queued
        this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now); // part of the way
        Result stopped = this.operations.stop(this.world).get(0);
        assertEquals(Optional.of("STOPPED"), stopped.code());
        assertEquals(OperationRecord.Status.PARTLY_APPLIED, record(id).status());
        assertEquals(List.of(), stopped.sideEffects(), "track is laid only on a finished road");
        assertEquals(List.of(), this.writer.calls);
    }

    @Test
    void blocksComeBeforeSideEffectsEveryTime() {
        this.operations = withWriter(200);
        UUID id = applyRoad(List.of(RAIL_1, RAIL_2));
        Result applied = run("alice");
        assertEquals(List.of(new Operations.SideEffectOutcome(RAIL_1, SideEffectWriter.Result.DONE),
                new Operations.SideEffectOutcome(RAIL_2, SideEffectWriter.Result.DONE)), applied.sideEffects());
        assertEquals(List.of(RAIL_1, RAIL_2), record(id).owned());

        this.operations.undo("alice");
        Result undone = run("alice");
        assertEquals(List.of(Outcome.DONE, Outcome.DONE), outcomes(undone));
        assertEquals(List.of(), record(id).owned());
        assertEquals(Set.of(), this.writer.rails);

        this.operations.redo("alice");
        run("alice");
        assertEquals(List.of(RAIL_1, RAIL_2), record(id).owned());
        assertEquals(List.of("make 2 over minecraft:stone", "remove 2 over minecraft:air", "make 2 over minecraft:stone"), this.writer.calls,
                "each call came after the blocks: stone laid, air put back");
    }

    @Test
    void aRailAlreadyThereIsNeverOwnedOrRemoved() {
        this.operations = withWriter(200);
        this.writer.rails.add(RAIL_1); // a player joined these nodes with MTR's own connector
        UUID id = applyRoad(List.of(RAIL_1, RAIL_2));
        Result applied = run("alice");
        assertEquals(List.of(Outcome.ALREADY_THERE, Outcome.DONE), outcomes(applied));
        assertEquals(List.of(RAIL_2), record(id).owned());

        this.operations.undo("alice");
        run("alice");
        assertEquals(Set.of(RAIL_1), this.writer.rails, "the player's rail stays");
        assertEquals("remove 1 over minecraft:air", this.writer.calls.get(1));
    }

    @Test
    void anUndoThatCantRemoveTheTrackStillUndoesTheRoadAndKeepsTheLeftovers() {
        this.operations = withWriter(200);
        UUID id = applyRoad(List.of(RAIL_1, RAIL_2));
        run("alice");
        this.writer.available = false; // Stratos Transit was taken out of the pack

        this.operations.undo("alice");
        Result undone = run("alice");
        assertEquals(Optional.empty(), undone.code(), "the road itself undid fine");
        assertEquals("minecraft:air", this.world.blockState(0, 64, 0));
        assertEquals(List.of(Outcome.UNAVAILABLE, Outcome.UNAVAILABLE), outcomes(undone));
        assertEquals(OperationRecord.Status.UNDONE, record(id).status());
        assertEquals(List.of(RAIL_1, RAIL_2), record(id).owned(), "the rails still there stay owned, to be tidied later");
    }

    @Test
    void redoMakesOnlyTheMissingRails() {
        this.operations = withWriter(200);
        this.writer.failing.add(RAIL_2);
        UUID id = applyRoad(List.of(RAIL_1, RAIL_2));
        Result applied = run("alice");
        assertEquals(List.of(Outcome.DONE, Outcome.FAILED), outcomes(applied));
        assertEquals(List.of(RAIL_1), record(id).owned());

        this.writer.available = false;
        this.operations.undo("alice");
        run("alice");
        assertEquals(List.of(RAIL_1), record(id).owned(), "a leftover");

        this.writer.available = true;
        this.writer.failing.clear();
        this.operations.redo("alice");
        Result redone = run("alice");
        assertEquals(List.of(new Operations.SideEffectOutcome(RAIL_2, SideEffectWriter.Result.DONE)), redone.sideEffects(),
                "only the rail not there is made");
        assertEquals(List.of(RAIL_1, RAIL_2), record(id).owned());
    }

    @Test
    void thePlayerStaysBusyUntilTheSideEffectsSettle() {
        this.operations = withWriter(200);
        this.writer.answerLater = true;
        UUID id = applyRoad(List.of(RAIL_1));
        for (int tick = 0; tick < 100; tick++) {
            this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now);
            assertEquals(List.of(), this.operations.afterTick(), "no answer before the track's");
        }
        assertEquals("minecraft:stone", this.world.blockState(9, 64, 0), "the blocks are done");
        assertEquals(List.of("make 1 over minecraft:stone"), this.writer.calls);
        assertTrue(this.operations.isBusy("alice"));
        assertEquals(Optional.of(Refusal.BUSY), this.operations.undo("alice").refusal());

        this.writer.later.complete(null);
        List<Result> results = this.operations.afterTick();
        assertEquals(1, results.size());
        assertEquals(List.of(Outcome.DONE), outcomes(results.get(0)));
        assertFalse(this.operations.isBusy("alice"));
        assertEquals(List.of(RAIL_1), record(id).owned());
    }

    @Test
    void aWriterThatThrowsIsReportedAsFailed() {
        this.operations = new Operations(new ApplyScheduler(), new UndoStore(this.storage, 0, 200, 1L << 30),
                () -> new UUID(0, this.nextId.incrementAndGet()), new SideEffectWriter() {
                    @Override
                    public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
                        throw new IllegalStateException("broken");
                    }

                    @Override
                    public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
                        return CompletableFuture.failedFuture(new IllegalStateException("broken too"));
                    }
                }, () -> NOW);
        UUID id = applyRoad(List.of(RAIL_1));
        Result applied = run("alice");
        assertEquals(List.of(Outcome.FAILED), outcomes(applied));
        assertTrue(applied.sideEffects().get(0).result().reason().contains("broken"), applied.sideEffects().get(0).result().reason());
        assertEquals(List.of(), record(id).owned());
        assertEquals(OperationRecord.Status.APPLIED, record(id).status(), "the road is placed all the same");
    }

    @Test
    void aSplitKeepsTheSideEffectsWithTheOriginalOperation() {
        this.operations = withWriter(200);
        UUID id = new UUID(7, 7);
        this.operations.apply("alice", row(0, 100, "minecraft:stone"), id, Optional.empty(), draft(List.of(RAIL_1)));
        run("alice");
        this.operations.undo("alice");
        this.operations.afterTick();
        this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now); // 50 restored
        Result stopped = this.operations.stop(this.world).get(0);
        assertEquals(List.of(), stopped.sideEffects(), "the track stays until the whole road is undone");
        assertEquals(OperationRecord.Status.PARTLY_UNDONE, record(id).status());
        assertEquals(List.of(RAIL_1), record(id).owned());
        List<OperationRecord> records = this.operations.records();
        assertEquals(2, records.size());
        OperationRecord part = records.get(1);
        assertEquals(OperationRecord.Status.UNDONE, part.status());
        assertEquals(List.of(), part.planned());
        assertEquals(List.of(), part.owned());
        assertEquals("roads", part.module());

        this.operations.undo("alice"); // the rest
        run("alice");
        assertEquals(OperationRecord.Status.UNDONE, record(id).status());
        assertEquals(List.of(), record(id).owned());
        assertEquals(List.of("make 1 over minecraft:stone", "remove 1 over minecraft:air"), this.writer.calls);
    }

    @Test
    void aPrunedOrUnreachableOperationsRecordSaysItsUndoIsGone() {
        this.operations = withWriter(2);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID id = new UUID(7, i);
            this.operations.apply("alice", row(i * 100, 10, "minecraft:stone"), id, Optional.empty(), draft(List.of()));
            run("alice");
            ids.add(id);
        }
        assertFalse(record(ids.get(0)).undoAvailable(), "dropped by the cap");
        assertTrue(record(ids.get(1)).undoAvailable());
        assertTrue(record(ids.get(2)).undoAvailable());

        this.operations.undo("alice");
        run("alice");
        assertTrue(record(ids.get(2)).undoAvailable(), "undone, and redoable");
        this.operations.apply("alice", row(300, 3, "minecraft:dirt"));
        run("alice");
        assertFalse(record(ids.get(2)).undoAvailable(), "a new edit made it unreachable");
    }

    // --- F-2b: the helper ---

    private final ManualHelper helper = new ManualHelper();

    /** Operations and a store whose work and writing wait for the test's helper. */
    private Operations withHelper(int maxOperations) {
        return new Operations(new ApplyScheduler(), new UndoStore(this.storage, 0, maxOperations, 1L << 30, false, this.helper),
                () -> new UUID(0, this.nextId.incrementAndGet()), this.writer, () -> NOW, Operations.OVERWORLD, this.helper, line -> {
                    throw new AssertionError("logged: " + line);
                });
    }

    /** Lets the helper write everything waiting, and files it: the images are on disk. */
    private void writeBehind() {
        this.helper.runAll();
        this.operations.afterTick();
        assertEquals(0, this.operations.store().writesInFlight());
    }

    /** As {@link #run}, the helper doing its work after every tick. */
    private Result runWithHelper(String owner) {
        for (int tick = 0; tick < 10_000; tick++) {
            this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now);
            for (Result result : this.operations.afterTick()) {
                if (result.owner().equals(owner)) {
                    return result;
                }
            }
            this.helper.runAll();
        }
        throw new AssertionError("no result for " + owner);
    }

    /** Ticks until {@code handle}'s job is placed and its filing waits for the helper. */
    private void placeUntilFiling(Operations.Handle handle) {
        for (int tick = 0; tick < 10_000 && handle.stage() != Operations.Stage.FILING; tick++) {
            this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now);
            assertEquals(List.of(), this.operations.afterTick());
        }
        assertEquals(Operations.Stage.FILING, handle.stage());
    }

    @Test
    void theWorkThatGrowsWithAnEditRunsOnTheHelperAndThePlayerStaysBusyUntilItsDone() {
        this.operations = withHelper(200);
        Operations.Handle handle = this.operations.apply("alice", row(0, 100, "minecraft:stone")).handle().orElseThrow();
        assertEquals(Operations.Stage.PREPARING, handle.stage());
        assertEquals(Optional.empty(), handle.job(), "the job is built on the helper");
        assertTrue(this.operations.scheduler().isIdle());
        assertEquals(List.of(), this.operations.afterTick());
        assertEquals(Operations.Stage.PREPARING, handle.stage(), "nothing happens until the helper has built it");

        assertEquals(1, this.helper.runWork());
        assertEquals(List.of(), this.operations.afterTick());
        assertEquals(Operations.Stage.PLACING, handle.stage());
        assertEquals(List.of(handle.job().orElseThrow()), this.operations.scheduler().jobs());

        placeUntilFiling(handle);
        assertEquals("minecraft:stone", this.world.blockState(99, 64, 0), "every block is placed");
        assertTrue(this.operations.isBusy("alice"), "but the player stays busy");
        assertEquals(Optional.of(Refusal.BUSY), this.operations.undo("alice").refusal());
        assertEquals(0, this.operations.store().size(), "nothing filed yet");
        assertEquals(0, this.storage.count("undo"));

        assertEquals(1, this.helper.runWork());
        List<Result> results = this.operations.afterTick();
        assertEquals(1, results.size());
        assertEquals(handle, results.get(0).handle());
        assertEquals(Operations.Stage.DONE, handle.stage());
        assertEquals(1, this.operations.undoDepth("alice"));
        assertEquals(OperationRecord.Status.APPLIED, record(handle.operationId()).status());
        assertEquals(NOW, record(handle.operationId()).time());
        assertFalse(this.operations.isBusy("alice"), "the player has the answer");

        UndoStore.Entry entry = this.operations.store().entry(handle.operationId()).orElseThrow();
        assertEquals(0, this.storage.count("undo"), "before the images are written");
        assertFalse(entry.onDisk(), "they stay in memory until written, beyond the zero budget");
        assertEquals(1, this.operations.store().writesInFlight());
        assertEquals(7, this.helper.disk.size(), "a file to write per section: x 0 to 99 spans seven");
        writeBehind();
        assertTrue(this.storage.count("undo") > 0, "the helper wrote them behind");
        assertTrue(entry.onDisk(), "and then they left memory");

        Operations.Handle undo = this.operations.undo("alice").handle().orElseThrow();
        assertEquals(handle.operationId(), undo.operationId());
        assertTrue(this.operations.store().isHeld(entry), "held while its images are read");
        assertEquals(Optional.empty(), runWithHelper("alice").code());
        assertFalse(this.operations.store().isHeld(entry), "let go once filed");
        assertEquals("minecraft:air", this.world.blockState(99, 64, 0));
        assertEquals(1, this.operations.redoDepth("alice"));
    }

    @Test
    void theCapNeverDropsTheOperationBeingUnpacked() {
        this.operations = withHelper(2);
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        runWithHelper("alice"); // the oldest operation
        this.operations.apply("bob", row(100, 10, "minecraft:dirt"));
        runWithHelper("bob");

        assertTrue(this.operations.undo("alice").isAccepted());
        Runnable unpacking = this.helper.work.poll(); // alice's images aren't read yet
        this.operations.apply("bob", row(200, 10, "minecraft:dirt"));
        runWithHelper("bob"); // a third operation: over the cap of 2
        assertEquals(1, this.operations.undoDepth("bob"), "bob's older operation was dropped instead");
        assertEquals(1, this.operations.undoDepth("alice"));

        unpacking.run();
        assertEquals(Optional.empty(), runWithHelper("alice").code(), "alice's images were still there");
        assertEquals("minecraft:air", this.world.blockState(0, 64, 0));
    }

    @Test
    void imagesThatVanishBeforeTheHelperReadsThemEndTheRequestWithNothingPlaced() {
        this.operations = withHelper(200);
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        runWithHelper("alice");
        writeBehind(); // on disk, and out of memory
        UndoStore.Entry entry = this.operations.store().entry(this.operations.records().get(0).id()).orElseThrow();
        assertTrue(this.operations.undo("alice").isAccepted());
        this.storage.files.clear(); // a damaged disk
        int writes = this.world.writes.size();
        Result undone = runWithHelper("alice");
        assertEquals(Optional.of(Operations.UNDO_DATA_UNREADABLE), undone.code());
        assertEquals(writes, this.world.writes.size());
        assertFalse(this.operations.store().isHeld(entry));
        assertEquals(1, this.operations.undoDepth("alice"));
    }

    @Test
    void stoppingFilesWhatWasPlacedAndDropsWhatWasStillBeingPrepared() {
        this.operations = withHelper(200);
        Operations.Handle alice = this.operations.apply("alice", row(0, 20, "minecraft:stone")).handle().orElseThrow();
        this.helper.runWork();
        this.operations.afterTick();
        placeUntilFiling(alice); // placed; its filing waits on the helper
        Operations.Handle carol = this.operations.apply("carol", row(500, 1000, "minecraft:stone")).handle().orElseThrow();
        this.helper.work.pollLast().run(); // only carol's job is built: alice's filing still waits
        this.operations.afterTick();
        this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now); // carol's job part-way
        Operations.Handle bob = this.operations.apply("bob", row(100, 20, "minecraft:dirt")).handle().orElseThrow(); // not yet built

        List<Result> results = this.operations.stop(this.world);
        assertEquals(3, results.size());
        Result bobs = results.stream().filter(r -> r.handle() == bob).findFirst().orElseThrow();
        assertEquals(Optional.of(Operations.STOPPED), bobs.code());
        assertEquals(Optional.empty(), bobs.job());
        assertEquals("minecraft:air", this.world.blockState(100, 64, 0), "bob's edit never started");
        Result alices = results.stream().filter(r -> r.handle() == alice).findFirst().orElseThrow();
        assertEquals(Optional.empty(), alices.code());
        assertEquals(1, this.operations.undoDepth("alice"), "filed on the stopping thread");
        Result carols = results.stream().filter(r -> r.handle() == carol).findFirst().orElseThrow();
        assertEquals(Optional.of("STOPPED"), carols.code());
        assertEquals(1, this.operations.undoDepth("carol"), "what carol's edit placed can be undone");
        assertEquals(OperationRecord.Status.PARTLY_APPLIED, record(carol.operationId()).status());
        assertFalse(this.operations.isBusy("alice") || this.operations.isBusy("bob") || this.operations.isBusy("carol"));
        assertEquals(2, this.helper.work.size(), "alice's filing (taken over by the stopping thread) and bob's preparing (dropped) are still queued");
        int files = this.storage.files.size();
        this.helper.runWork();
        assertEquals(files, this.storage.files.size(), "running them late does nothing");
        assertEquals(List.of(), this.operations.afterTick());
        assertEquals("minecraft:air", this.world.blockState(100, 64, 0));
    }

    /** One scenario: an apply, a hand edit, an undo that keeps it, a redo, and an undo stopped halfway. */
    private List<Object> scenario(boolean withHelper) {
        this.operations = withHelper ? withHelper(200) : withWriter(200);
        Map<String, String> before = snapshot();
        this.operations.apply("alice", row(0, 200, "minecraft:stone"), new UUID(7, 1), Optional.empty(), draft(List.of(RAIL_1)));
        Result applied = withHelper ? runWithHelper("alice") : run("alice");
        this.world.blocks.put(FakeWorld.key(5, 64, 0), "minecraft:gold_block");
        this.operations.undo("alice");
        Result undone = withHelper ? runWithHelper("alice") : run("alice");
        this.operations.redo("alice");
        Result redone = withHelper ? runWithHelper("alice") : run("alice");
        this.operations.undo("alice");
        if (withHelper) {
            this.helper.runWork();
        }
        this.operations.afterTick();
        this.operations.scheduler().tick(this.world, BUDGET, () -> this.world.now); // 50 put back
        Result stopped = this.operations.stop(this.world).get(0);
        List<Object> outcome = new ArrayList<>();
        for (Result result : List.of(applied, undone, redone, stopped)) {
            outcome.add(List.of(result.kind(), result.operationId(), result.keptByHand(), result.code(), result.sideEffects()));
        }
        outcome.add(this.operations.records());
        outcome.add(this.operations.histories());
        for (OperationRecord record : this.operations.records()) {
            outcome.add(this.operations.store().entry(record.id()).map(entry -> List.of(entry.generation(), entry.blocks(), entry.bytes(),
                    UndoStoreTest.sorted(this.operations.store().changes(entry)))));
        }
        outcome.add(snapshot());
        outcome.add(this.writer.rails);
        this.world.blocks.clear();
        before.forEach(this.world.blocks::put);
        this.writer.rails.clear();
        this.nextId.set(0);
        return outcome;
    }

    @Test
    void theHelperGivesTheSameHistoriesRecordsAndImagesAsDoingTheWorkAtOnce() {
        List<Object> atOnce = scenario(false);
        List<Object> staged = scenario(true);
        assertEquals(atOnce, staged);
        assertEquals(2, this.operations.records().size(), "the road and the part split off: " + this.operations.records());
    }

    // --- F-1+: block entities through undo and redo ---

    private static final String CHEST = "minecraft:chest[facing=north,type=single,waterlogged=false]";
    private static final String ITEMS = "{Items:[{Count:3b,Slot:0b,id:\"minecraft:apple\"}]}";

    @Test
    void aChestARoadReplacedComesBackWithItsItemsOnEveryUndo() {
        this.world.setBlockState(5, 64, 0, CHEST, ITEMS);
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        run("alice");
        assertEquals("minecraft:stone", this.world.blockState(5, 64, 0));
        this.operations.undo("alice");
        assertEquals(Optional.empty(), run("alice").code());
        assertEquals(CHEST, this.world.blockState(5, 64, 0));
        assertEquals(ITEMS, this.world.blockEntityData(5, 64, 0), "with its items, from images on disk");
        this.operations.redo("alice");
        assertEquals(Optional.empty(), run("alice").code());
        this.operations.undo("alice");
        run("alice");
        assertEquals(ITEMS, this.world.blockEntityData(5, 64, 0), "and again");
    }

    @Test
    void aRedoIsRefusedIfARestoredChestsItemsChangedSinceTheUndo() {
        this.world.setBlockState(5, 64, 0, CHEST, ITEMS);
        this.operations.apply("alice", row(0, 10, "minecraft:stone"));
        run("alice");
        this.operations.undo("alice");
        run("alice");
        this.world.blockEntityData.put(FakeWorld.key(5, 64, 0), "{Items:[]}"); // the apples are taken out
        this.operations.redo("alice");
        assertEquals(Optional.of("REDO_CONFLICT"), run("alice").code(), "or the next undo would bring the apples back a second time");
        assertEquals(CHEST, this.world.blockState(5, 64, 0));
    }
}
