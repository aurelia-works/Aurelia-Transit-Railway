/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.Direction;
import org.mtr.aurelia.road.proto.Message.Hello;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.HelloResult;
import org.mtr.aurelia.road.proto.Message.Limits;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.Redo;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.proto.Message.YourRoads;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.ManualHelper;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;
import org.mtr.aurelia.road.undo.UndoStore;

/** The server's side of the protocol: handshake, plan requests, progress, undo and redo (editor-protocol §3, §5; task F-3). */
class EditorServerTest {
    static final UUID ALICE = new UUID(0, 1);
    static final UUID BOB = new UUID(0, 2);
    static final String CORE = "core-test";
    static final long SECOND = 1_000_000_000L;

    final FakeWorld world = new FakeWorld();
    final AtomicLong clock = new AtomicLong();
    final AtomicLong operationIds = new AtomicLong();
    final AtomicLong undoIds = new AtomicLong();
    /** What the "game" does with track: nothing can be built until a test sets a {@link FakeTrack}. */
    SideEffectWriter writer = SideEffectWriter.UNAVAILABLE;
    Operations operations = new Operations(new ApplyScheduler(), new UndoStore(new FakeStorage(), 0, 200, 1L << 30),
            () -> new UUID(9, this.undoIds.incrementAndGet()), new SideEffectWriter() {
                @Override
                public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
                    return EditorServerTest.this.writer.make(dimension, effects);
                }

                @Override
                public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
                    return EditorServerTest.this.writer.remove(dimension, effects);
                }
            }, Instant::now);
    /** The server's optional features beside its modules (T-2: track). */
    final List<String> capabilities = new ArrayList<>();
    /** The world's limits: MTR's node block for the track tests. */
    WorldLimits limits = WorldLimits.OVERWORLD;
    final List<String> log = new ArrayList<>();
    final Map<UUID, List<Message>> sent = new HashMap<>();
    final Deque<Runnable> heldTasks = new ArrayDeque<>();
    BiPredicate<UUID, String> permissions = (player, node) -> true;
    /** The server's rate and volume limits (F-6); they apply only to players without {@code stratos.limits.bypass}. */
    EditorLimits editorLimits = EditorLimits.NONE;
    boolean holdTasks;
    ExecutorService threads;
    final List<PlanModule<?>> modules = new ArrayList<>(List.of(RoadsModule.INSTANCE));
    EditorServer server;

    @AfterEach
    void stopThreads() {
        if (this.threads != null) {
            this.threads.shutdownNow();
        }
    }

    EditorServer server() {
        if (this.server == null) {
            this.server = new EditorServer(new EditorServer.ServerInfo("0.1.0", CORE, 3465, 1, this.editorLimits, this.capabilities), this.modules,
                    (player, node) -> this.permissions.test(player, node),
                    player -> new EditorServer.EditWorld(this.operations, this.limits),
                    task -> {
                        if (this.threads != null) {
                            this.threads.execute(task);
                        } else if (this.holdTasks) {
                            this.heldTasks.add(task);
                        } else {
                            task.run();
                        }
                    },
                    this.clock::get, () -> new UUID(7, this.operationIds.incrementAndGet()), this.log::add);
        }
        return this.server;
    }

    /** Every message to a player goes through the real encoding, so each one is proven sendable. */
    void join(UUID player) {
        server().join(player, message -> {
            try {
                this.sent.computeIfAbsent(player, p -> new ArrayList<>()).add(Messages.decode(Messages.encode(message), Direction.TO_CLIENT));
            } catch (MalformedMessage e) {
                throw new AssertionError(e);
            }
        });
    }

    void send(UUID player, Message message) {
        server().receive(player, Messages.encode(message));
    }

    void joinAndAck(UUID player) {
        join(player);
        send(player, new HelloAck(Protocol.VERSION, "0.1.0", List.of("roads")));
        take(player);
    }

    /** The messages sent to {@code player} since the last call. */
    List<Message> take(UUID player) {
        List<Message> messages = new ArrayList<>(this.sent.getOrDefault(player, List.of()));
        this.sent.remove(player);
        return messages;
    }

    @Test
    void aRepairAfterACrashIsToldToPlayersWhoCanEditNowAndWhenTheyJoin() {
        UUID alice = new UUID(1, 1);
        UUID bob = new UUID(1, 2);
        UUID carol = new UUID(1, 3);
        this.permissions = (player, node) -> !player.equals(bob);
        joinAndAck(alice);
        joinAndAck(bob);
        Message.Notice notice = EditorServer.repairNotice(2, 1, 0);
        assertEquals(Protocol.REPAIRED, notice.code());
        assertEquals(List.of("2", "1"), List.copyOf(notice.args().values()));
        server().repaired(notice);
        assertEquals(notice, only(alice, Message.Notice.class));
        assertEquals(List.of(), take(bob), "bob can't edit");
        join(carol);
        send(carol, new HelloAck(Protocol.VERSION, "0.1.0", List.of("roads")));
        List<Message> toCarol = take(carol);
        assertEquals(notice, toCarol.get(toCarol.size() - 1), "after HelloResult and YourRoads");
        assertEquals(Protocol.REPAIRED_ATTENTION, EditorServer.repairNotice(0, 0, 1).code());
    }

    @Test
    void requestsWhileTheWorldIsBeingRepairedAreRefused() {
        UUID alice = new UUID(1, 1);
        joinAndAck(alice);
        this.operations.setRepairing(true);
        send(alice, new Message.Undo(1));
        Message.UndoRedoResult result = only(alice, Message.UndoRedoResult.class);
        assertEquals(Optional.of(Protocol.REPAIRING), result.reasonCode());
    }

    <T extends Message> T only(UUID player, Class<T> type) {
        List<Message> messages = take(player);
        assertEquals(1, messages.size(), "one message: " + messages);
        return assertInstanceOf(type, messages.get(0));
    }

    static RoadPlanParams road(int x0, int x1) {
        return RoadPlanParams.straight("city-17", End.at(x0, 64, 0, Heading.E), End.at(x1, 64, 0, Heading.E));
    }

    static BlockPlan planOf(RoadPlanParams params) {
        return RoadPlanner.plan(params, WorldLimits.OVERWORLD).plan().orElseThrow();
    }

    static Digest hashOf(RoadPlanParams params) {
        return Digest.of(PlanHash.of(planOf(params), CORE));
    }

    void request(UUID player, int id, RoadPlanParams params, Digest hash) {
        send(player, new PlanRequest(id, "roads", params.tool().name(), params.encode(), hash));
    }

    /** Runs the apply engine and the server until everything queued has finished. */
    void runApply() {
        for (int tick = 0; tick < 100_000; tick++) {
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            this.operations.afterTick().forEach(server()::finished);
            server().tick();
            if (this.operations.scheduler().isIdle() && !this.operations.isBusy(ALICE.toString()) && !this.operations.isBusy(BOB.toString())) {
                return;
            }
        }
        throw new AssertionError("the apply engine never finished");
    }

    /** A result the server didn't start (for notices and logging). */
    static Operations.Result result(String owner, Operations.Kind kind, Optional<UUID> operationId, Optional<String> code,
            List<Operations.SideEffectOutcome> sideEffects) {
        Operations.Handle handle = new Operations.Handle(kind, owner, operationId.orElse(new UUID(0, 0)));
        return new Operations.Result(handle, owner, kind, operationId, Optional.empty(), 0, code, sideEffects);
    }

    Map<String, String> placed() {
        Map<String, String> blocks = new HashMap<>(this.world.blocks);
        blocks.values().removeIf("minecraft:air"::equals);
        return blocks;
    }

    @Test
    void playersWhoCanEditHearTheMarkForStratosBlocksAfterTheHandshakeAndWhenItChanges() {
        List<Boolean> saved = new ArrayList<>();
        server().stratosBlocks(false, saved::add);
        assertEquals(List.of(), saved, "what the platform loaded isn't saved again");
        join(ALICE);
        send(ALICE, new HelloAck(Protocol.VERSION, "0.1.0", List.of()));
        List<Message> toAlice = take(ALICE);
        assertEquals(new Message.StratosBlocks(false), toAlice.get(toAlice.size() - 1), "after YourRoads (UI-1c)");
        join(BOB); // never answers Hello, so can't edit
        take(BOB);
        server().stratosBlockPlaced();
        assertEquals(List.of(new Message.StratosBlocks(true)), take(ALICE));
        assertEquals(List.of(), take(BOB), "bob can't edit");
        assertEquals(List.of(true), saved);
        server().stratosBlockPlaced();
        assertEquals(List.of(), take(ALICE), "once it is on, a placement says nothing");
        assertEquals(List.of(true), saved);
        assertTrue(server().stratosBlocks());
    }

    @Test
    void theHandshakeEnablesEditingWhenTheProtocolsMatch() {
        join(ALICE);
        Hello hello = only(ALICE, Hello.class);
        assertEquals(new Hello(Protocol.VERSION, "0.1.0", CORE, 3465, 1, List.of("roads"), Limits.NONE), hello);
        assertFalse(server().isEditing(ALICE));
        assertTrue(server().hasSession(ALICE));
        assertFalse(server().hasSession(BOB));
        send(ALICE, new HelloAck(Protocol.VERSION, "0.1.0", List.of()));
        assertEquals(List.of(new HelloResult(true, Optional.empty()), new Message.Palettes(List.of(BuiltInPalettes.stratosUk().json(), BuiltInPalettes.vanilla().json())),
                new YourRoads(List.of()), new Message.StratosBlocks(true)), take(ALICE),
                "then the server's palettes (R-9), the player's roads (F-5): none yet, and (UI-1c) the mark for Stratos blocks, on until the platform says");
        assertTrue(server().isEditing(ALICE));
        assertTrue(this.log.isEmpty());
    }

    @Test
    void differentProtocolsTurnEditingOffAndAreLoggedOnce() {
        join(ALICE);
        take(ALICE);
        send(ALICE, new HelloAck(Protocol.VERSION + 1, "0.2.0", List.of("roads")));
        assertEquals(new HelloResult(false, Optional.of(Protocol.PROTOCOL_MISMATCH)), only(ALICE, HelloResult.class));
        send(ALICE, new HelloAck(Protocol.VERSION + 1, "0.2.0", List.of("roads")));
        take(ALICE);
        assertEquals(1, this.log.size(), "logged once: " + this.log);
        assertTrue(this.log.get(0).contains("0.2.0") && this.log.get(0).contains("0.1.0"), this.log.get(0));

        RoadPlanParams params = road(0, 20);
        request(ALICE, 4, params, hashOf(params));
        assertEquals(new PlanRejected(4, Protocol.EDITING_OFF, Map.of(), Optional.empty()), only(ALICE, PlanRejected.class));
        send(ALICE, new Undo(5));
        assertEquals(new UndoRedoResult(5, Optional.empty(), false, Optional.of(Protocol.EDITING_OFF), 0), only(ALICE, UndoRedoResult.class));
        assertTrue(this.world.writes.isEmpty());
    }

    @Test
    void aMatchingRequestIsAppliedReportedAndCanBeUndoneAndRedone() {
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 20);
        BlockPlan plan = planOf(params);
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        PlanAccepted accepted = only(ALICE, PlanAccepted.class);
        assertEquals(1, accepted.requestId());
        assertEquals(hashOf(params), accepted.planHash());
        assertEquals(plan.blockCount(), accepted.blockCount());
        assertEquals(17 * 21 * 8, accepted.blockCount(), "per column: the surface, 2 of foundation and 5 of clearance");
        assertEquals(plan.sections().size(), accepted.sectionCount());

        runApply();
        ApplyProgress done = (ApplyProgress) take(ALICE).get(0);
        assertEquals(new ApplyProgress(accepted.operationId(), ApplyState.DONE, accepted.sectionCount(), accepted.sectionCount(), Optional.empty()),
                done);
        Map<String, String> expected = new HashMap<>();
        plan.blocks().stream().filter(b -> !b.state().equals("minecraft:air")) // the clearance: air where there was air
                .forEach(b -> expected.put(FakeWorld.key(b.x(), b.y(), b.z()), b.state()));
        assertEquals(expected, placed());
        assertEquals(1, this.operations.undoDepth(ALICE.toString()));

        send(ALICE, new Undo(2));
        runApply();
        assertEquals(new UndoRedoResult(2, Optional.of(accepted.operationId()), true, Optional.empty(), 0), only(ALICE, UndoRedoResult.class));
        assertEquals(Map.of(), placed(), "the operation id in PlanAccepted is the undo entry's");

        send(ALICE, new Redo(3));
        runApply();
        assertEquals(new UndoRedoResult(3, Optional.of(accepted.operationId()), true, Optional.empty(), 0), only(ALICE, UndoRedoResult.class));
        assertEquals(expected, placed());
    }

    /**
     * R-5: a road joining another is checked against the server's own records: the road it joins must be applied in the
     * player's world now, and its end not taken. The record keeps the join and the dashes.
     */
    @Test
    void aRoadJoiningAnotherIsCheckedAgainstTheServersRecords() throws Exception {
        joinAndAck(ALICE);
        RoadPlanParams a = road(0, 20);
        request(ALICE, 1, a, hashOf(a));
        server().tick();
        UUID first = only(ALICE, PlanAccepted.class).operationId();
        runApply();
        take(ALICE);
        RoadPlanParams b = RoadPlanParams.straight("city-17", new End(20, 64, 0, Heading.E, Optional.of(first), OptionalInt.of(1)),
                End.at(40, 64, 0, Heading.E)).withDashes(new RoadPlanParams.Dashes(20, RoadPlanParams.Dashes.Anchor.START));
        Digest hash = Digest.of(PlanHash.of(RoadPlanner.plan(b, WorldLimits.OVERWORLD, Map.of(first, a)).plan().orElseThrow(), CORE));
        request(ALICE, 2, b, hash);
        server().tick();
        UUID second = only(ALICE, PlanAccepted.class).operationId();
        runApply();
        take(ALICE);
        assertEquals(b, RoadsModule.INSTANCE.paramsFromJson("STRAIGHT", this.operations.record(second).orElseThrow().params()));
        // Bob can't start another road there: A's end is taken (a junction).
        joinAndAck(BOB);
        request(BOB, 3, b, hash);
        server().tick();
        assertEquals(Map.of("feature", "junction"), only(BOB, PlanRejected.class).args());
        // With both undone, A has gone.
        send(ALICE, new Undo(4));
        runApply();
        send(ALICE, new Undo(5));
        runApply();
        take(ALICE);
        request(ALICE, 6, b, hash);
        server().tick();
        assertEquals(RoadJoins.ROAD_END_GONE, only(ALICE, PlanRejected.class).reasonCode());
    }

    @Test
    void aPlacedRoadsRecordHoldsTheRequestsModuleToolParametersHashAndAuthor() throws Exception {
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 20);
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        PlanAccepted accepted = only(ALICE, PlanAccepted.class);
        runApply();
        OperationRecord record = this.operations.record(accepted.operationId()).orElseThrow();
        assertEquals("roads", record.module());
        assertEquals("STRAIGHT", record.tool());
        assertEquals(RoadsModule.INSTANCE.paramsToJson(params), record.params());
        assertEquals(params, RoadsModule.INSTANCE.paramsFromJson(record.tool(), record.params()), "the road can be planned again from its record");
        assertEquals(hashOf(params).hex(), record.planHash());
        assertEquals(ALICE.toString(), record.author());
        assertEquals(OperationRecord.Status.APPLIED, record.status());
        assertTrue(record.undoAvailable());
        assertEquals(planOf(params).sections().size(), record.sections().size(), "every section the road changed");
        assertEquals(List.of(), record.planned(), "no track: the road was placed without the Track node aid");

        send(ALICE, new Undo(2));
        runApply();
        assertEquals(OperationRecord.Status.UNDONE, this.operations.record(accepted.operationId()).orElseThrow().status());
    }

    @Test
    void aReadOnlyWorldTurnsEditingOffAndRefusesEverythingWithWorldReadOnly() {
        this.operations.setReadOnly(true); // the downgrade guard (F-5)
        join(ALICE);
        take(ALICE);
        send(ALICE, new HelloAck(Protocol.VERSION, "0.1.0", List.of("roads")));
        assertEquals(new HelloResult(false, Optional.of(Protocol.WORLD_READ_ONLY)), only(ALICE, HelloResult.class), "and no YourRoads");
        assertFalse(server().isEditing(ALICE));
        RoadPlanParams params = road(0, 20);
        request(ALICE, 1, params, hashOf(params));
        assertEquals(new PlanRejected(1, Protocol.WORLD_READ_ONLY, Map.of(), Optional.empty()), only(ALICE, PlanRejected.class));
        send(ALICE, new Undo(2));
        assertEquals(new UndoRedoResult(2, Optional.empty(), false, Optional.of(Protocol.WORLD_READ_ONLY), 0), only(ALICE, UndoRedoResult.class));
        send(ALICE, new Redo(3));
        assertEquals(new UndoRedoResult(3, Optional.empty(), false, Optional.of(Protocol.WORLD_READ_ONLY), 0), only(ALICE, UndoRedoResult.class));
        server().dimensionChanged(ALICE);
        assertEquals(List.of(), take(ALICE), "no roads for a player who can't edit");
        assertTrue(this.world.writes.isEmpty());
    }

    @Test
    void yourRoadsListsThePlayersRoadsInTheirHistoryInTheOrderTheyWereMade() {
        joinAndAck(ALICE);
        joinAndAck(BOB);
        List<RoadPlanParams> roads = List.of(road(0, 20), road(0, 20).withNodeAids(RoadPlanParams.NodeAids.WOOL), road(100, 140));
        List<UUID> ids = new ArrayList<>();
        int request = 1;
        for (RoadPlanParams params : roads) {
            request(ALICE, request++, params, hashOf(params));
            server().tick();
            ids.add(only(ALICE, PlanAccepted.class).operationId());
            runApply();
            take(ALICE);
        }
        RoadPlanParams bobs = road(300, 320);
        request(BOB, 1, bobs, hashOf(bobs));
        server().tick();
        take(BOB);
        runApply();
        send(ALICE, new Undo(request));
        runApply();
        take(ALICE);

        server().dimensionChanged(ALICE);
        assertEquals(new YourRoads(List.of(new YourRoads.Road(ids.get(0), "roads", "STRAIGHT", roads.get(0).encode(), false),
                new YourRoads.Road(ids.get(1), "roads", "STRAIGHT", roads.get(1).encode(), false),
                new YourRoads.Road(ids.get(2), "roads", "STRAIGHT", roads.get(2).encode(), true))), only(ALICE, YourRoads.class),
                "alice's three roads, the undone one marked; never bob's");
    }

    /** Stands in for Stratos Transit's track writer: each rail gets the outcome set for it, DONE otherwise. */
    static final class FakeTrack implements SideEffectWriter {
        final Map<SideEffect, Outcome> make = new HashMap<>();
        final Map<SideEffect, Outcome> remove = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public CompletableFuture<List<Result>> make(String dimension, List<SideEffect> effects) {
            return answer("make", dimension, effects, this.make);
        }

        @Override
        public CompletableFuture<List<Result>> remove(String dimension, List<SideEffect> effects) {
            return answer("remove", dimension, effects, this.remove);
        }

        private CompletableFuture<List<Result>> answer(String what, String dimension, List<SideEffect> effects, Map<SideEffect, Outcome> outcomes) {
            this.calls.add(what + " " + effects.size() + " in " + dimension);
            List<Result> results = new ArrayList<>();
            for (SideEffect effect : effects) {
                Outcome outcome = outcomes.getOrDefault(effect, Outcome.DONE);
                results.add(outcome == Outcome.DONE ? Result.DONE : new Result(outcome, "set by the test"));
            }
            return CompletableFuture.completedFuture(results);
        }
    }

    static RoadPlanParams trackRoad(int x0, int x1) {
        return RoadPlanParams.straight("city-17", End.at(x0, 64, 0, Heading.E), End.at(x1, 64, 0, Heading.E), RoadPlanParams.Vertical.QUADRATIC,
                RoadPlanParams.NodeAids.TRACK);
    }

    static PlanResult trackPlan(RoadPlanParams params) {
        return RoadPlanner.plan(params, WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK)));
    }

    /** The hash a client sends for a road with track: its blocks and its rails. */
    static Digest trackHashOf(RoadPlanParams params) {
        PlanResult result = trackPlan(params);
        return Digest.of(PlanHash.of(result.plan().orElseThrow(), result.sideEffects(), CORE));
    }

    @Test
    void helloListsTrackOnlyWhenTheServerCanBuildIt() {
        join(ALICE);
        assertEquals(List.of("roads"), only(ALICE, Hello.class).capabilities());
        this.capabilities.add(Protocol.CAPABILITY_TRACK);
        this.server = null;
        join(BOB);
        assertEquals(List.of("roads", "track"), only(BOB, Hello.class).capabilities(), "Stratos Transit is loaded (T-2)");
    }

    @Test
    void aRoadWithTrackIsRefusedByAServerThatCantBuildItWithNothingPlaced() {
        this.limits = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
        joinAndAck(ALICE);
        RoadPlanParams params = trackRoad(0, 20);
        request(ALICE, 1, params, trackHashOf(params));
        server().tick();
        assertEquals(new PlanRejected(1, Protocol.TRACK_UNAVAILABLE, Map.of(), Optional.empty()), only(ALICE, PlanRejected.class));
        runApply();
        assertTrue(this.world.writes.isEmpty(), "nothing placed");
        assertEquals(0, this.operations.undoDepth(ALICE.toString()));
        assertTrue(take(ALICE).isEmpty());
    }

    @Test
    void theHashCoversTheTrack() {
        this.limits = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
        this.capabilities.add(Protocol.CAPABILITY_TRACK);
        joinAndAck(ALICE);
        RoadPlanParams params = trackRoad(0, 20);
        Digest blocksOnly = Digest.of(PlanHash.of(trackPlan(params).plan().orElseThrow(), CORE));
        request(ALICE, 1, params, blocksOnly);
        server().tick();
        PlanRejected rejected = only(ALICE, PlanRejected.class);
        assertEquals(Protocol.PLAN_HASH_MISMATCH, rejected.reasonCode(), "a client that left the track out of its hash");
        assertEquals(Optional.of(trackHashOf(params)), rejected.serverPlanHash());
    }

    @Test
    void aRoadsTrackIsMadeOwnedAndRemovedAndThePlayerHearsOfAnyLeftUndone() {
        this.limits = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
        this.capabilities.add(Protocol.CAPABILITY_TRACK);
        FakeTrack track = new FakeTrack();
        this.writer = track;
        this.world.modBlocks.add(MtrNodes.BLOCK);
        joinAndAck(ALICE);
        RoadPlanParams params = trackRoad(0, 20);
        List<SideEffect> rails = trackPlan(params).sideEffects();
        assertEquals(2, rails.size());
        track.make.put(rails.get(1), SideEffectWriter.Outcome.FAILED);
        request(ALICE, 1, params, trackHashOf(params));
        server().tick();
        PlanAccepted accepted = only(ALICE, PlanAccepted.class);
        runApply();
        List<Message> after = take(ALICE);
        assertEquals(2, after.size(), "the road's answer, then the notice: " + after);
        assertInstanceOf(ApplyProgress.class, after.get(0));
        assertEquals(ApplyState.DONE, ((ApplyProgress) after.get(0)).state(), "the road is placed anyway");
        assertEquals(new Notice(Protocol.TRACK_NOT_BUILT, Map.of("count", "1", "total", "2")), after.get(1));
        OperationRecord record = this.operations.record(accepted.operationId()).orElseThrow();
        assertEquals(rails, record.planned(), "the record plans every lane's rail");
        assertEquals(List.of(rails.get(0)), record.owned(), "and owns the one made");
        assertEquals(List.of("make 2 in " + Operations.OVERWORLD), track.calls);

        track.remove.put(rails.get(0), SideEffectWriter.Outcome.FAILED);
        send(ALICE, new Undo(2));
        runApply();
        after = take(ALICE);
        assertEquals(List.of(new UndoRedoResult(2, Optional.of(accepted.operationId()), true, Optional.empty(), 0),
                new Notice(Protocol.TRACK_NOT_REMOVED, Map.of("count", "1", "total", "1"))), after, "undone anyway, the track left");
        assertEquals(List.of(rails.get(0)), this.operations.record(accepted.operationId()).orElseThrow().owned(), "still owned");
        assertEquals(Map.of(), placed());

        track.make.clear();
        send(ALICE, new Redo(3));
        runApply();
        assertEquals(new UndoRedoResult(3, Optional.of(accepted.operationId()), true, Optional.empty(), 0), only(ALICE, UndoRedoResult.class),
                "redo makes only the rail not owned (now DONE): no notice");
        assertEquals("make 1 in " + Operations.OVERWORLD, track.calls.get(track.calls.size() - 1));
        assertEquals(Set.copyOf(rails), Set.copyOf(this.operations.record(accepted.operationId()).orElseThrow().owned()));
    }

    @Test
    void aPlayersRailAlreadyThereOrOneGoneAlreadyIsNoNotice() {
        SideEffect rail = new SideEffect.TrackRail(0, 65, 0, 2, 40, 65, 0, 2, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);
        assertEquals(List.of(), EditorServer.notices(result(ALICE.toString(), Operations.Kind.APPLY, Optional.empty(), Optional.empty(),
                List.of(new Operations.SideEffectOutcome(rail, new SideEffectWriter.Result(SideEffectWriter.Outcome.ALREADY_THERE, "a player's"))))));
        assertEquals(List.of(), EditorServer.notices(result(ALICE.toString(), Operations.Kind.UNDO, Optional.empty(), Optional.empty(),
                List.of(new Operations.SideEffectOutcome(rail, new SideEffectWriter.Result(SideEffectWriter.Outcome.NOT_THERE, "gone"), true)))));
        assertEquals(List.of(new Notice(Protocol.TRACK_NOT_BUILT, Map.of("count", "1", "total", "1"))),
                EditorServer.notices(result(ALICE.toString(), Operations.Kind.REDO, Optional.empty(), Optional.empty(),
                        List.of(new Operations.SideEffectOutcome(rail, new SideEffectWriter.Result(SideEffectWriter.Outcome.NOT_THERE, "odd"))))),
                "NOT_THERE makes nothing: a redo that gets it built nothing");
        assertEquals(List.of(), EditorServer.notices(result(ALICE.toString(), Operations.Kind.APPLY, Optional.empty(), Optional.empty(), List.of())),
                "no track, no notice");
    }

    @Test
    void aDeleteOrRebuildCountsItsRemovingAndItsMakingApart() {
        SideEffect rail = new SideEffect.TrackRail(0, 65, 0, 2, 40, 65, 0, 2, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);
        SideEffect other = new SideEffect.TrackRail(0, 65, 4, 6, 40, 65, 4, 6, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);
        SideEffectWriter.Result failed = new SideEffectWriter.Result(SideEffectWriter.Outcome.FAILED, "MTR said no");
        assertEquals(List.of(new Notice(Protocol.TRACK_NOT_REMOVED, Map.of("count", "1", "total", "2")), new Notice(Protocol.TRACK_NOT_BUILT,
                Map.of("count", "1", "total", "1"))), EditorServer.notices(result(ALICE.toString(), Operations.Kind.APPLY, Optional.empty(),
                        Optional.empty(), List.of(new Operations.SideEffectOutcome(rail, failed, true), new Operations.SideEffectOutcome(other,
                                new SideEffectWriter.Result(SideEffectWriter.Outcome.NOT_THERE, "gone"), true),
                                new Operations.SideEffectOutcome(other, failed)))),
                "a rebuild's removal and its making are told apart, the removal first");
    }

    @Test
    void sideEffectsThatWerentCarriedOutAreLogged() {
        SideEffect rail = new SideEffect.TrackRail(0, 65, 0, 2, 40, 65, 0, 2, 40, 0, SideEffect.TrackRail.Shape.QUADRATIC, 0);
        UUID id = new UUID(3, 3);
        server().finished(result("gametest", Operations.Kind.UNDO, Optional.of(id), Optional.empty(),
                List.of(new Operations.SideEffectOutcome(rail, SideEffectWriter.Result.DONE),
                        new Operations.SideEffectOutcome(rail, new SideEffectWriter.Result(SideEffectWriter.Outcome.UNAVAILABLE, "no Stratos Transit")))));
        assertEquals(1, this.log.size(), "only the one not done: " + this.log);
        assertTrue(this.log.get(0).contains("undo of operation " + id) && this.log.get(0).contains("UNAVAILABLE (no Stratos Transit)"), this.log.get(0));
    }

    @Test
    void clearingMarkersWritesOnlyWhereTheWoolIsAndNeverTouchesAnMtrNode() {
        joinAndAck(ALICE);
        RoadPlanParams road = road(0, 40).withNodeAids(RoadPlanParams.NodeAids.WOOL);
        request(ALICE, 1, road, hashOf(road));
        server().tick();
        take(ALICE);
        runApply();
        take(ALICE);
        List<BlockPlan.Block> markers = planOf(road).blocks().stream().filter(b -> b.state().equals("minecraft:blue_wool")).toList();
        assertEquals(4, markers.size());
        BlockPlan.Block low = markers.get(0);
        String node = "mtr:rail[facing=true,is_22_5=false,is_45=false,is_connected=false,waterlogged=false]";
        this.world.blocks.put(FakeWorld.key(low.x(), low.y(), low.z()), node); // a player's node, a block too low

        RoadPlanParams clear = RoadPlanParams.clearMarkers(road);
        request(ALICE, 2, clear, hashOf(clear));
        server().tick();
        PlanAccepted accepted = only(ALICE, PlanAccepted.class);
        assertEquals(4, accepted.blockCount(), "the plan covers every marker spot");
        runApply();
        assertEquals(ApplyState.DONE, ((ApplyProgress) take(ALICE).get(0)).state());
        assertEquals(node, this.world.blocks.get(FakeWorld.key(low.x(), low.y(), low.z())), "the node stays");
        for (BlockPlan.Block marker : markers.subList(1, 4)) {
            assertEquals("minecraft:polished_deepslate", this.world.blocks.get(FakeWorld.key(marker.x(), marker.y(), marker.z())));
        }

        send(ALICE, new Undo(3));
        runApply();
        assertEquals(new UndoRedoResult(3, Optional.of(accepted.operationId()), true, Optional.empty(), 0), only(ALICE, UndoRedoResult.class));
        for (BlockPlan.Block marker : markers.subList(1, 4)) {
            assertEquals("minecraft:blue_wool", this.world.blocks.get(FakeWorld.key(marker.x(), marker.y(), marker.z())), "the wool is back");
        }
        assertEquals(node, this.world.blocks.get(FakeWorld.key(low.x(), low.y(), low.z())));
    }

    @Test
    void aHashMismatchIsRefusedWithTheServersHashAndChangesNothing() {
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 20);
        request(ALICE, 1, params, Digest.of(new byte[Digest.LENGTH]));
        server().tick();
        assertEquals(new PlanRejected(1, Protocol.PLAN_HASH_MISMATCH, Map.of(), Optional.of(hashOf(params))), only(ALICE, PlanRejected.class));
        runApply();
        assertTrue(this.world.writes.isEmpty(), "nothing was placed");
        assertEquals(0, this.operations.undoDepth(ALICE.toString()));
    }

    @Test
    void plannerRefusalsAndUnknownModulesAreReported() {
        joinAndAck(ALICE);
        request(ALICE, 1, road(0, 1), Digest.of(new byte[Digest.LENGTH]));
        server().tick();
        assertEquals(new PlanRejected(1, RoadPlanner.TOO_SHORT, Map.of(), Optional.empty()), only(ALICE, PlanRejected.class));

        send(ALICE, new PlanRequest(2, "terrain", "BRUSH", new byte[0], Digest.of(new byte[Digest.LENGTH])));
        assertEquals(new PlanRejected(2, Protocol.NOT_AVAILABLE_YET, Map.of("feature", "terrain"), Optional.empty()), only(ALICE, PlanRejected.class));
    }

    @Test
    void playersWithoutPermissionAreRefused() {
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 20);
        this.permissions = (player, node) -> false;
        request(ALICE, 1, params, hashOf(params));
        assertEquals(new PlanRejected(1, Protocol.PERMISSION_DENIED, Map.of("node", EditorServer.NODE_USE), Optional.empty()),
                only(ALICE, PlanRejected.class));
        this.permissions = (player, node) -> !node.equals("stratos.roads.apply");
        request(ALICE, 2, params, hashOf(params));
        assertEquals(new PlanRejected(2, Protocol.PERMISSION_DENIED, Map.of("node", "stratos.roads.apply"), Optional.empty()),
                only(ALICE, PlanRejected.class));
        this.permissions = (player, node) -> !node.equals(EditorServer.NODE_UNDO);
        send(ALICE, new Undo(3));
        assertEquals(new UndoRedoResult(3, Optional.empty(), false, Optional.of(Protocol.PERMISSION_DENIED), 0), only(ALICE, UndoRedoResult.class));
        assertTrue(this.world.writes.isEmpty());
    }

    /** F-6: a new road needs roads.apply; a rebuild, delete or clear-markers roads.edit; pre-oriented MTR nodes their own. */
    @Test
    void eachRequestNeedsItsOwnNodes() {
        RoadsModule roads = RoadsModule.INSTANCE;
        RoadPlanParams road = road(0, 20);
        RoadPlanParams mtr = road.withNodeAids(RoadPlanParams.NodeAids.PREORIENTED);
        UUID id = new UUID(5, 5);
        assertEquals(List.of(RoadsModule.NODE_APPLY), roads.nodes(road));
        assertEquals(List.of(RoadsModule.NODE_APPLY), roads.nodes(road.withNodeAids(RoadPlanParams.NodeAids.TRACK)), "Track needs no more");
        assertEquals(List.of(RoadsModule.NODE_APPLY, RoadsModule.NODE_PREORIENTED), roads.nodes(mtr));
        assertEquals(List.of(RoadsModule.NODE_EDIT, RoadsModule.NODE_PREORIENTED),
                roads.nodes(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, id, mtr, Optional.empty())), "a rebuild places its nodes again");
        assertEquals(List.of(RoadsModule.NODE_EDIT), roads.nodes(RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, id, mtr, Optional.empty())));
        assertEquals(List.of(RoadsModule.NODE_EDIT), roads.nodes(RoadPlanParams.clearMarkers(mtr)));

        joinAndAck(ALICE);
        this.permissions = (player, node) -> !node.equals(RoadsModule.NODE_EDIT);
        RoadPlanParams delete = RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, id, road, Optional.empty());
        request(ALICE, 1, delete, hashOf(road));
        assertEquals(new PlanRejected(1, Protocol.PERMISSION_DENIED, Map.of("node", RoadsModule.NODE_EDIT), Optional.empty()),
                only(ALICE, PlanRejected.class));
        this.permissions = (player, node) -> !node.equals(RoadsModule.NODE_PREORIENTED);
        request(ALICE, 2, mtr, Digest.of(new byte[Digest.LENGTH])); // refused before planning
        assertEquals(new PlanRejected(2, Protocol.PERMISSION_DENIED, Map.of("node", RoadsModule.NODE_PREORIENTED), Optional.empty()),
                only(ALICE, PlanRejected.class));
        request(ALICE, 3, road, hashOf(road));
        server().tick();
        assertInstanceOf(PlanAccepted.class, only(ALICE, PlanAccepted.class), "the same road without MTR nodes is fine");
    }

    /** F-6: Hello tells each player their limits; those with stratos.limits.bypass have none. */
    @Test
    void helloTellsLimitedPlayersTheirLimits() {
        this.editorLimits = new EditorLimits(100, 1000, 2, 5);
        this.permissions = (player, node) -> !(player.equals(ALICE) && node.equals(EditorServer.NODE_LIMITS_BYPASS));
        join(ALICE);
        join(BOB);
        assertEquals(new Limits(100, 1000), only(ALICE, Hello.class).limits());
        assertEquals(Limits.NONE, only(BOB, Hello.class).limits());
    }

    /** F-6: an edit over the limit for one is refused with its size and the limit, nothing written; bypass lifts it. */
    @Test
    void anEditOverTheVolumeLimitIsRefusedWithNothingWritten() {
        this.editorLimits = new EditorLimits(100, 0, 0, 0);
        this.permissions = (player, node) -> !(player.equals(ALICE) && node.equals(EditorServer.NODE_LIMITS_BYPASS));
        joinAndAck(ALICE);
        joinAndAck(BOB);
        RoadPlanParams params = road(0, 20);
        int blocks = planOf(params).blockCount();
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        assertEquals(new PlanRejected(1, Protocol.VOLUME_LIMIT, Map.of("blocks", Integer.toString(blocks), "max", "100"), Optional.empty()),
                only(ALICE, PlanRejected.class));
        assertTrue(this.world.writes.isEmpty());

        server().setLimits(new EditorLimits(0, 100, 0, 0));
        request(ALICE, 2, params, hashOf(params));
        server().tick();
        assertEquals(new PlanRejected(2, Protocol.VOLUME_LIMIT, Map.of("blocks", Integer.toString(blocks), "max", "100"), Optional.empty()),
                only(ALICE, PlanRejected.class), "an edit bigger than a minute's limit could never fit");

        request(BOB, 1, params, hashOf(params));
        server().tick();
        assertInstanceOf(PlanAccepted.class, only(BOB, PlanAccepted.class));
    }

    /** F-6: the blocks of a player's accepted plans in the last minute; undo and redo are neither limited nor counted. */
    @Test
    void aPlayersBlocksPerMinuteAreLimitedAndUndoIsnt() {
        RoadPlanParams first = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E));
        RoadPlanParams second = RoadPlanParams.straight("city-17", End.at(0, 64, 40, Heading.E), End.at(20, 64, 40, Heading.E));
        RoadPlanParams third = RoadPlanParams.straight("city-17", End.at(0, 64, 80, Heading.E), End.at(20, 64, 80, Heading.E));
        int blocks = planOf(first).blockCount();
        this.editorLimits = new EditorLimits(0, 2 * blocks, 0, 0);
        this.permissions = (player, node) -> !node.equals(EditorServer.NODE_LIMITS_BYPASS);
        joinAndAck(ALICE);
        request(ALICE, 1, first, hashOf(first));
        runApply();
        take(ALICE);
        this.clock.set(10 * SECOND);
        request(ALICE, 2, second, hashOf(second));
        runApply();
        take(ALICE);
        this.clock.set(20 * SECOND);
        request(ALICE, 3, third, hashOf(third));
        server().tick();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("used", Integer.toString(2 * blocks));
        args.put("max", Integer.toString(2 * blocks));
        args.put("seconds", "40");
        PlanRejected rejected = only(ALICE, PlanRejected.class);
        assertEquals(new PlanRejected(3, Protocol.VOLUME_PER_MINUTE, args, Optional.empty()), rejected);
        assertEquals(List.of("used", "max", "seconds"), List.copyOf(rejected.args().keySet()), "in the message's order");

        send(ALICE, new Undo(4));
        runApply();
        assertTrue(only(ALICE, UndoRedoResult.class).ok(), "undo isn't limited");
        send(ALICE, new Redo(5));
        runApply();
        assertTrue(only(ALICE, UndoRedoResult.class).ok());

        this.clock.set(60 * SECOND);
        request(ALICE, 6, third, hashOf(third));
        server().tick();
        assertInstanceOf(PlanAccepted.class, only(ALICE, PlanAccepted.class), "the first road's blocks are over a minute old; the redo wasn't counted");
    }

    /** F-6: plan requests from a limited player take from a token bucket; a refused one takes nothing. */
    @Test
    void planRequestsAreRateLimited() {
        this.editorLimits = new EditorLimits(0, 0, 2, 5);
        this.permissions = (player, node) -> !(player.equals(ALICE) && node.equals(EditorServer.NODE_LIMITS_BYPASS));
        joinAndAck(ALICE);
        joinAndAck(BOB);
        RoadPlanParams params = road(0, 20);
        for (int id = 1; id <= 6; id++) {
            request(ALICE, id, params, hashOf(params));
            request(BOB, id, params, hashOf(params));
        }
        List<String> alice = take(ALICE).stream().map(m -> ((PlanRejected) m).reasonCode()).toList();
        assertEquals(List.of(Protocol.BUSY, Protocol.BUSY, Protocol.BUSY, Protocol.BUSY, Protocol.RATE_LIMITED), alice,
                "the first is being worked out; five fit the burst");
        assertFalse(take(BOB).stream().anyMatch(m -> ((PlanRejected) m).reasonCode().equals(Protocol.RATE_LIMITED)), "Bob bypasses the limits");
        runApply();
        take(ALICE);
        take(BOB);
        request(ALICE, 7, params, hashOf(params));
        assertEquals(Protocol.RATE_LIMITED, only(ALICE, PlanRejected.class).reasonCode());
        this.clock.addAndGet(SECOND / 2);
        request(ALICE, 8, params, hashOf(params));
        server().tick();
        assertInstanceOf(PlanAccepted.class, only(ALICE, PlanAccepted.class), "half a second later there is one request to spare");
    }

    @Test
    void oneRequestAtATime() {
        joinAndAck(ALICE);
        this.holdTasks = true;
        RoadPlanParams params = road(0, 20);
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        assertTrue(take(ALICE).isEmpty(), "still being worked out");
        request(ALICE, 2, params, hashOf(params));
        assertEquals(Protocol.BUSY, only(ALICE, PlanRejected.class).reasonCode());
        send(ALICE, new Undo(3));
        assertEquals(Optional.of(Protocol.BUSY), only(ALICE, UndoRedoResult.class).reasonCode());

        this.heldTasks.poll().run();
        server().tick();
        assertEquals(1, only(ALICE, PlanAccepted.class).requestId());
        // Now the apply engine holds Alice's operation.
        this.holdTasks = false;
        request(ALICE, 4, road(0, 30), hashOf(road(0, 30)));
        server().tick();
        assertEquals(Protocol.BUSY, only(ALICE, PlanRejected.class).reasonCode());
        send(ALICE, new Undo(5));
        assertEquals(Optional.of("BUSY"), only(ALICE, UndoRedoResult.class).reasonCode());

        // Bob isn't held up by Alice.
        joinAndAck(BOB);
        RoadPlanParams other = road(100, 120);
        request(BOB, 1, other, hashOf(other));
        server().tick();
        assertEquals(1, only(BOB, PlanAccepted.class).requestId());
    }

    /** A module whose planner waits until its thread is interrupted. */
    static final class SlowModule implements PlanModule<byte[]> {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        public String id() {
            return "slow";
        }

        @Override
        public String tool(byte[] params) {
            return "WAIT";
        }

        @Override
        public byte[] encode(byte[] params) {
            return params;
        }

        @Override
        public byte[] decode(String tool, byte[] bytes) {
            return bytes;
        }

        @Override
        public PlanResult plan(byte[] params, WorldLimits limits) {
            this.entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                this.interrupted.countDown();
            }
            throw new IllegalStateException("interrupted");
        }
    }

    @Test
    void plansThatTakeTooLongAreStoppedAndRefused() throws InterruptedException {
        SlowModule slow = new SlowModule();
        this.modules.add(slow);
        this.threads = Executors.newSingleThreadExecutor();
        joinAndAck(ALICE);
        this.clock.set(5 * SECOND);
        send(ALICE, new PlanRequest(1, "slow", "WAIT", new byte[0], Digest.of(new byte[Digest.LENGTH])));
        assertTrue(slow.entered.await(10, TimeUnit.SECONDS), "the planner started");
        this.clock.set(5 * SECOND + EditorServer.PLAN_TIME_LIMIT_NANOS);
        server().tick();
        assertTrue(take(ALICE).isEmpty(), "exactly at the limit is still allowed");
        this.clock.addAndGet(1);
        server().tick();
        assertEquals(new PlanRejected(1, Protocol.PLAN_TOO_COMPLEX, Map.of("seconds", "2"), Optional.empty()), only(ALICE, PlanRejected.class));
        assertTrue(slow.interrupted.await(10, TimeUnit.SECONDS), "the planner's thread was interrupted");

        // The player can ask again straight away.
        RoadPlanParams params = road(0, 20);
        request(ALICE, 2, params, hashOf(params));
        for (int i = 0; i < 1000 && this.sent.getOrDefault(ALICE, List.of()).isEmpty(); i++) {
            Thread.sleep(5);
            server().tick();
        }
        assertEquals(2, only(ALICE, PlanAccepted.class).requestId());
    }

    @Test
    void progressIsReportedAboutOnceASecond() {
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 200); // 21 × 201 blocks: about 85 ticks at 0.1 ms per block
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        PlanAccepted accepted = only(ALICE, PlanAccepted.class);
        List<ApplyProgress> reports = new ArrayList<>();
        int ticks = 0;
        while (!this.operations.scheduler().isIdle() || this.operations.isBusy(ALICE.toString())) {
            this.clock.addAndGet(SECOND / 2);
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            this.operations.afterTick().forEach(server()::finished);
            server().tick();
            take(ALICE).forEach(m -> reports.add((ApplyProgress) m));
            ticks++;
        }
        assertTrue(ticks > 50, "a long job: " + ticks + " ticks");
        ApplyProgress last = reports.get(reports.size() - 1);
        assertEquals(ApplyState.DONE, last.state());
        List<ApplyProgress> applying = reports.subList(0, reports.size() - 1);
        assertTrue(applying.size() >= ticks / 2 - 2 && applying.size() <= ticks / 2 + 1, applying.size() + " reports in " + ticks + " half-second ticks");
        int previous = 0;
        for (ApplyProgress report : applying) {
            assertEquals(ApplyState.APPLYING, report.state());
            assertEquals(accepted.operationId(), report.operationId());
            assertEquals(accepted.sectionCount(), report.sectionsTotal());
            assertTrue(report.sectionsDone() >= previous, "never goes backwards");
            previous = report.sectionsDone();
        }
        assertTrue(previous > 0 && previous < accepted.sectionCount(), "the reports show the job part-way: " + previous);
        assertEquals(accepted.sectionCount(), last.sectionsDone());
    }

    @Test
    void everySectionPlacedIsReportedOnceWhileTheHelperFilesTheEdit() {
        ManualHelper helper = new ManualHelper();
        this.operations = new Operations(new ApplyScheduler(), new UndoStore(new FakeStorage(), 0, 200, 1L << 30),
                () -> new UUID(9, this.undoIds.incrementAndGet()), SideEffectWriter.UNAVAILABLE, Instant::now, Operations.OVERWORLD, helper,
                this.log::add);
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 20);
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        PlanAccepted accepted = only(ALICE, PlanAccepted.class);
        assertEquals(1, helper.work.size(), "the job is built on the helper");
        helper.work.poll().run();
        for (int tick = 0; helper.work.isEmpty(); tick++) {
            assertTrue(tick < 1000, "the blocks never finished");
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            this.operations.afterTick().forEach(server()::finished);
            server().tick();
        }
        assertEquals(new ApplyProgress(accepted.operationId(), ApplyState.APPLYING, accepted.sectionCount(), accepted.sectionCount(), Optional.empty()),
                only(ALICE, ApplyProgress.class), "every section placed; the client shows \"getting undo ready\"");
        server().tick();
        assertTrue(take(ALICE).isEmpty(), "said once");
        send(ALICE, new Undo(2));
        assertEquals(new UndoRedoResult(2, Optional.empty(), false, Optional.of(Protocol.BUSY), 0), only(ALICE, UndoRedoResult.class),
                "busy until it is filed");

        helper.work.poll().run(); // the helper files it
        this.operations.afterTick().forEach(server()::finished);
        assertEquals(ApplyState.DONE, only(ALICE, ApplyProgress.class).state());
        assertEquals(1, this.operations.undoDepth(ALICE.toString()));
    }

    @Test
    void malformedRecordsAreDroppedAndCounted() {
        joinAndAck(ALICE);
        server().receive(ALICE, new byte[] {99});
        server().receive(ALICE, Messages.encode(new Hello(1, "x", "y", 1, 1, List.of(), Limits.NONE))); // server → client only
        RoadPlanParams params = road(0, 20);
        send(ALICE, new PlanRequest(1, "roads", "CURVE", params.encode(), hashOf(params))); // tool and params disagree
        send(ALICE, new PlanRequest(2, "roads", "STRAIGHT", new byte[] {0, 1}, hashOf(params))); // truncated params
        assertTrue(take(ALICE).isEmpty(), "dropped without an answer");
        assertEquals(4, server().malformedCount(ALICE));
        assertEquals(4, this.log.size());
        for (int i = 0; i < 10; i++) {
            server().receive(ALICE, new byte[] {99});
        }
        assertEquals(14, server().malformedCount(ALICE));
        assertEquals(5, this.log.size(), "only the first few are logged");
        // Bytes from a player who never registered the channel are ignored.
        server().receive(BOB, Messages.encode(new Undo(1)));
        assertTrue(take(BOB).isEmpty());
    }

    @Test
    void resultsAfterLeavingOrForOthersAreIgnored() {
        joinAndAck(ALICE);
        RoadPlanParams params = road(0, 20);
        request(ALICE, 1, params, hashOf(params));
        server().tick();
        take(ALICE);
        server().leave(ALICE);
        assertFalse(server().hasSession(ALICE));
        runApply();
        assertTrue(take(ALICE).isEmpty());
        assertEquals(1, this.operations.undoDepth(ALICE.toString()), "the operation finished and stays undoable");
        // Operations the network didn't start (the GameTests use their own owners) don't reach anyone.
        this.operations.apply("gametest-owner", planOf(road(50, 70)));
        runApply();
        this.operations.afterTick();
    }

    /** The hash of a rebuild or delete as the client works it out, with {@code road} standing as {@code id} (R-7). */
    static Digest editHash(RoadPlanParams edit, UUID id, RoadPlanParams road) {
        PlanResult result = RoadPlanner.plan(edit, WorldLimits.OVERWORLD, Map.of(id, road));
        return Digest.of(PlanHash.of(result.plan().orElseThrow(), result.sideEffects(), CORE));
    }

    @Test
    void aDeleteAsksAboutHandEditsBeforeItIsAcceptedThenKeepsThem() {
        joinAndAck(ALICE);
        RoadPlanParams road = road(0, 40);
        request(ALICE, 1, road, hashOf(road));
        runApply();
        UUID id = take(ALICE).stream().filter(PlanAccepted.class::isInstance).map(m -> ((PlanAccepted) m).operationId()).findFirst().orElseThrow();
        BlockPlan.Block surface = planOf(road).blocks().stream().filter(b -> b.y() == 64).findFirst().orElseThrow();
        String torch = "minecraft:torch";
        this.world.blocks.put(FakeWorld.key(surface.x(), surface.y(), surface.z()), torch); // a hand edit in the road's surface
        Map<String, String> edited = placed();

        RoadPlanParams delete = RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, id, road, Optional.empty());
        request(ALICE, 2, delete, editHash(delete, id, road));
        runApply();
        assertEquals(new PlanRejected(2, Protocol.MANUAL_EDITS, Map.of("count", "1"), Optional.empty()), only(ALICE, PlanRejected.class),
                "refused before PlanAccepted, with the count");
        assertEquals(edited, placed(), "nothing written");
        assertEquals(1, this.operations.undoDepth(ALICE.toString()));

        RoadPlanParams keep = RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, id, road, Optional.of(RoadPlanParams.ManualEdits.KEEP));
        request(ALICE, 3, keep, editHash(keep, id, road));
        runApply();
        List<Message> answers = take(ALICE);
        assertInstanceOf(PlanAccepted.class, answers.get(0), answers.toString());
        assertEquals(ApplyState.DONE, assertInstanceOf(ApplyProgress.class, answers.get(answers.size() - 1)).state());
        assertEquals(Map.of(FakeWorld.key(surface.x(), surface.y(), surface.z()), torch), placed(), "the road is gone; the torch was kept");
        assertEquals("DELETE", this.operations.records().get(this.operations.records().size() - 1).tool());

        send(ALICE, new Undo(4));
        runApply();
        assertTrue(only(ALICE, UndoRedoResult.class).ok());
        assertEquals(edited, placed(), "undo brings the road back around the torch");

        send(ALICE, new Redo(5));
        runApply();
        take(ALICE);
        RoadPlanParams rebuild = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, id, road, Optional.empty());
        request(ALICE, 6, rebuild, editHash(rebuild, id, road));
        runApply();
        assertEquals(RoadPlanner.ROAD_GONE, only(ALICE, PlanRejected.class).reasonCode(), "a deleted road can't be rebuilt");
    }

    @Test
    void aRebuildOfAnUntouchedRoadHasNothingToChangeAndYourRoadsListsRebuildsAndDeletes() {
        joinAndAck(ALICE);
        RoadPlanParams road = road(0, 40);
        request(ALICE, 1, road, hashOf(road));
        runApply();
        UUID id = take(ALICE).stream().filter(PlanAccepted.class::isInstance).map(m -> ((PlanAccepted) m).operationId()).findFirst().orElseThrow();
        RoadPlanParams rebuild = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, id, road, Optional.empty());
        request(ALICE, 2, rebuild, editHash(rebuild, id, road));
        runApply();
        assertEquals(Protocol.NOTHING_TO_CHANGE, only(ALICE, PlanRejected.class).reasonCode());

        BlockPlan.Block surface = planOf(road).blocks().stream().filter(b -> b.y() == 64).findFirst().orElseThrow();
        this.world.blocks.put(FakeWorld.key(surface.x(), surface.y(), surface.z()), "minecraft:air"); // broken: the ground was air
        request(ALICE, 3, rebuild, editHash(rebuild, id, road));
        runApply();
        assertEquals(Protocol.MANUAL_EDITS, only(ALICE, PlanRejected.class).reasonCode());
        RoadPlanParams overwrite = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, id, road, Optional.of(RoadPlanParams.ManualEdits.OVERWRITE));
        request(ALICE, 4, overwrite, editHash(overwrite, id, road));
        runApply();
        take(ALICE);
        assertEquals(surface.state(), this.world.blockState(surface.x(), surface.y(), surface.z()), "repaired");

        join(ALICE);
        send(ALICE, new HelloAck(Protocol.VERSION, "0.1.0", List.of("roads")));
        YourRoads yours = assertInstanceOf(YourRoads.class, take(ALICE).get(3)); // after Hello, HelloResult and (R-9) Palettes
        assertEquals(List.of("STRAIGHT", "REBUILD"), yours.roads().stream().map(YourRoads.Road::tool).toList());
    }
}
