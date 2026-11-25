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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.bake.Bake;
import org.mtr.aurelia.road.bake.BakeMap;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.ApplyScheduler;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Message.Direction;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.TaskProgress;
import org.mtr.aurelia.road.proto.Message.TaskRequest;
import org.mtr.aurelia.road.proto.Message.TaskResult;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.spi.BlockFinder;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.UndoStore;

/** B-3's world tasks through the server: bake the world, bake a road, redraw markings (owner, 2026-10-15). */
class WorldTasksTest {
    static final UUID ALICE = new UUID(0, 1);
    static final UUID BOB = new UUID(0, 2);
    static final String CORE = "core-test";

    final FakeWorld world = new FakeWorld();
    final AtomicLong operationIds = new AtomicLong();
    final AtomicLong undoIds = new AtomicLong();
    final AtomicLong clock = new AtomicLong();
    final Operations operations = new Operations(new ApplyScheduler(), new UndoStore(new FakeStorage(), 0, 200, 1L << 30),
            () -> new UUID(9, this.undoIds.incrementAndGet()), SideEffectWriter.UNAVAILABLE, Instant::now);
    final List<String> log = new ArrayList<>();
    final Map<UUID, List<Message>> sent = new HashMap<>();
    BiPredicate<UUID, String> permissions = (player, node) -> true;
    /** How many ticks the fake finder takes, and whether it fails. */
    int findTicks = 3;
    boolean findFails;
    final List<Optional<Set<Long>>> searches = new ArrayList<>();
    EditorServer server;

    WorldTasksTest() {
        for (String id : List.of("asphalt", "dropped_kerb", "footway_flags", "gully", "kerb", "white_line", "yellow_line")) {
            this.world.modBlocks.add(BakeMap.NAMESPACE + id);
        }
    }

    EditorServer server() {
        if (this.server == null) {
            this.server = new EditorServer(new EditorServer.ServerInfo("0.1.0", CORE, 3465, 1, EditorLimits.NONE), List.of(RoadsModule.INSTANCE),
                    (player, node) -> this.permissions.test(player, node), player -> new EditorServer.EditWorld(this.operations, WorldLimits.OVERWORLD),
                    Runnable::run, this.clock::get, () -> new UUID(7, this.operationIds.incrementAndGet()), this.log::add);
            this.server.worldTasks(() -> List.of(new EditorServer.EditWorld(this.operations, WorldLimits.OVERWORLD)), new FakeFinder());
        }
        return this.server;
    }

    /** Finds the fake world's Stratos blocks after {@link #findTicks} ticks. */
    final class FakeFinder implements BlockFinder {
        @Override
        public Search find(String dimension, Optional<Set<Long>> cells) {
            WorldTasksTest.this.searches.add(cells);
            return new Search() {
                int ticks;
                boolean cancelled;

                @Override
                public void tick() {
                    this.ticks++;
                }

                @Override
                public int done() {
                    return StrictMath.min(this.ticks, total());
                }

                @Override
                public int total() {
                    return WorldTasksTest.this.findTicks;
                }

                @Override
                public boolean finished() {
                    return this.cancelled || this.ticks >= WorldTasksTest.this.findTicks;
                }

                @Override
                public Optional<String> failure() {
                    return this.cancelled ? Optional.of("cancelled") : WorldTasksTest.this.findFails ? Optional.of("a region file is unreadable")
                            : Optional.empty();
                }

                @Override
                public List<Bake.Found> found() {
                    List<Bake.Found> found = new ArrayList<>();
                    WorldTasksTest.this.world.blocks.forEach((key, state) -> {
                        String[] xyz = key.split(",");
                        int x = Integer.parseInt(xyz[0]);
                        int y = Integer.parseInt(xyz[1]);
                        int z = Integer.parseInt(xyz[2]);
                        if (BakeMap.isStratos(state) && cells.map(set -> set.contains(BlockChange.positionKey(x, y, z))).orElse(true)) {
                            found.add(new Bake.Found(x, y, z, state));
                        }
                    });
                    return found;
                }

                @Override
                public void cancel() {
                    this.cancelled = true;
                }
            };
        }
    }

    void joinAndAck(UUID player) {
        server().join(player, message -> {
            try {
                this.sent.computeIfAbsent(player, p -> new ArrayList<>()).add(Messages.decode(Messages.encode(message), Direction.TO_CLIENT));
            } catch (MalformedMessage e) {
                throw new AssertionError(e);
            }
        });
        send(player, new HelloAck(Protocol.VERSION, "0.1.0", List.of("roads")));
        take(player);
    }

    void send(UUID player, Message message) {
        server().receive(player, Messages.encode(message));
    }

    List<Message> take(UUID player) {
        List<Message> messages = new ArrayList<>(this.sent.getOrDefault(player, List.of()));
        this.sent.remove(player);
        return messages;
    }

    /** Runs the engine and the server until {@code player} hears how their task ended; every message on the way. */
    List<Message> runTask(UUID player) {
        List<Message> heard = new ArrayList<>();
        for (int tick = 0; tick < 100_000; tick++) {
            this.clock.addAndGet(300_000_000L); // a third of a second a tick, so progress is reported
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            this.operations.afterTick().forEach(server()::finished);
            server().tick();
            List<Message> now = take(player);
            heard.addAll(now);
            if (now.stream().anyMatch(TaskResult.class::isInstance)) {
                return heard;
            }
        }
        throw new AssertionError("the task never ended");
    }

    static TaskResult result(List<Message> heard) {
        return assertInstanceOf(TaskResult.class, heard.get(heard.size() - 1));
    }

    /** Places {@code params} for {@code player}, as the road tool does; its operation id. */
    UUID place(UUID player, int requestId, RoadPlanParams params) {
        BlockPlan plan = RoadPlanner.plan(params, WorldLimits.OVERWORLD).plan().orElseThrow();
        send(player, new PlanRequest(requestId, "roads", params.tool().name(), params.encode(), Digest.of(PlanHash.of(plan, CORE))));
        List<Message> heard = new ArrayList<>();
        for (int tick = 0; tick < 100_000; tick++) {
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            this.operations.afterTick().forEach(server()::finished);
            server().tick();
            heard.addAll(take(player));
            if (heard.stream().anyMatch(m -> m instanceof PlanRejected || m instanceof Message.ApplyProgress p && p.state() != Message.ApplyState.APPLYING)) {
                break;
            }
        }
        return heard.stream().filter(Message.PlanAccepted.class::isInstance).map(m -> ((Message.PlanAccepted) m).operationId()).findFirst()
                .orElseThrow(() -> new AssertionError("not placed: " + heard + " " + this.log));
    }

    static RoadPlanParams ukRoad(int z) {
        return RoadPlanParams.straight("city-17", End.at(0, 64, z, Heading.E), End.at(40, 64, z, Heading.E)).withPalette(BuiltInPalettes.STRATOS_UK_ID);
    }

    Map<String, String> stratosBlocks() {
        Map<String, String> stratos = new HashMap<>(this.world.blocks);
        stratos.values().removeIf(state -> !BakeMap.isStratos(state));
        return stratos;
    }

    @Test
    void bakingTheWorldTurnsEveryStratosBlockVanillaAndUndoBringsThemBack() {
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=8]");
        this.world.blocks.put(FakeWorld.key(0, 65, 0), "stratos:kerb[facing=north,layers=1,shape=straight]");
        this.world.blocks.put(FakeWorld.key(500, 70, -300), "stratos:footway_flags[layers=3]"); // placed by hand, far away
        this.world.blocks.put(FakeWorld.key(1, 64, 0), "minecraft:stone");
        Map<String, String> before = new HashMap<>(this.world.blocks);

        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        List<Message> heard = runTask(ALICE);
        assertEquals(new TaskResult(4, true, Optional.empty(), Map.of("blocks", "3", "kept", "0")), result(heard));
        assertTrue(heard.stream().anyMatch(m -> m instanceof TaskProgress p && p.stage().equals(Protocol.STAGE_FINDING)), "progress while finding: " + heard);
        assertEquals(Map.of(), stratosBlocks(), "a baked world has no Stratos blocks left");
        assertEquals("minecraft:polished_deepslate", this.world.blockState(0, 64, 0));
        assertEquals("minecraft:polished_andesite_slab[type=bottom,waterlogged=false]", this.world.blockState(0, 65, 0));
        assertEquals("minecraft:smooth_stone_slab[type=bottom,waterlogged=false]", this.world.blockState(500, 70, -300));
        assertEquals(List.of(Optional.<Set<Long>>empty()), this.searches, "every chunk");
        OperationRecord record = this.operations.records().get(0);
        assertEquals(EditorServer.WORLD_MODULE, record.module());
        assertEquals(Protocol.TASK_BAKE_WORLD, record.tool());
        assertEquals(ALICE.toString(), record.author());

        send(ALICE, new Undo(5));
        runApply();
        assertTrue(take(ALICE).stream().anyMatch(m -> m instanceof UndoRedoResult r && r.ok()));
        assertEquals(before, this.world.blocks, "undo brings every Stratos block back");
    }

    @Test
    void aWorldWithNoStratosBlocksHasNothingToBake() {
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(1, 64, 0), "minecraft:stone");
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        assertEquals(new TaskResult(4, true, Optional.empty(), Map.of("blocks", "0", "kept", "0")), result(runTask(ALICE)));
        assertEquals(List.of(), this.operations.records(), "no operation");
    }

    @Test
    void aBakeLeavesABlockChangedSinceItWasFound() {
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=8]");
        this.world.blocks.put(FakeWorld.key(2, 64, 0), "stratos:asphalt[layers=8]");
        this.findTicks = 1;
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        server().tick(); // found
        this.world.blocks.put(FakeWorld.key(2, 64, 0), "stratos:asphalt[layers=2]"); // a player changed it meanwhile
        assertEquals(Map.of("blocks", "1", "kept", "1"), result(runTask(ALICE)).args());
        assertEquals("stratos:asphalt[layers=2]", this.world.blockState(2, 64, 0));
    }

    @Test
    void aWorldBakeThatLeavesNoStratosBlocksTurnsTheMarkOff() {
        List<Boolean> saved = new ArrayList<>();
        server().stratosBlocks(true, saved::add);
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=8]");
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        List<Message> heard = runTask(ALICE);
        assertEquals(new Message.StratosBlocks(false), heard.get(heard.size() - 2), "the mark goes off, then the result: " + heard);
        assertEquals(List.of(false), saved);
        assertFalse(server().stratosBlocks());

        send(ALICE, new TaskRequest(5, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        heard = runTask(ALICE);
        assertEquals(List.of(), heard.stream().filter(Message.StratosBlocks.class::isInstance).toList(), "already off: nothing more to say");
        assertEquals(List.of(false), saved);
    }

    @Test
    void theMarkStaysOnWhenABakeKeepsABlockOrMeetsOneItCantBake() {
        List<Boolean> saved = new ArrayList<>();
        server().stratosBlocks(true, saved::add);
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=8]");
        this.findTicks = 1;
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        server().tick(); // found
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=2]"); // a player changed it meanwhile
        assertEquals(Map.of("blocks", "0", "kept", "1"), result(runTask(ALICE)).args());
        assertTrue(server().stratosBlocks(), "a kept block is still a Stratos block");

        this.world.modBlocks.add(BakeMap.NAMESPACE + "a_block_from_a_newer_stratos");
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:a_block_from_a_newer_stratos");
        send(ALICE, new TaskRequest(5, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        assertEquals(Map.of("blocks", "0", "kept", "0"), result(runTask(ALICE)).args());
        assertTrue(server().stratosBlocks(), "a block with no vanilla block stays");

        this.world.blocks.put(FakeWorld.key(2, 64, 0), "stratos:asphalt[layers=8]");
        send(ALICE, new TaskRequest(6, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        assertEquals(Map.of("blocks", "1", "kept", "0"), result(runTask(ALICE)).args());
        assertTrue(server().stratosBlocks(), "baked one, but the unknown block stays");
        assertEquals(List.of(), saved);
    }

    @Test
    void theMarkStaysOnWhenAStratosBlockIsPlacedWhileTheWorldBakes() {
        List<Boolean> saved = new ArrayList<>();
        server().stratosBlocks(true, saved::add);
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=8]");
        this.findTicks = 5;
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        server().tick();
        server().stratosBlockPlaced(); // someone's road, somewhere the search has passed
        List<Message> heard = runTask(ALICE);
        assertEquals(Map.of("blocks", "1", "kept", "0"), result(heard).args());
        assertEquals(List.of(), heard.stream().filter(Message.StratosBlocks.class::isInstance).toList());
        assertTrue(server().stratosBlocks());
        assertEquals(List.of(), saved);
    }

    @Test
    void bakingARoadNeverTurnsTheMarkOff() {
        server().stratosBlocks(true, present -> {
        });
        joinAndAck(ALICE);
        UUID road = place(ALICE, 1, ukRoad(0));
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_ROAD, Optional.of(road)));
        assertTrue(result(runTask(ALICE)).ok());
        assertEquals(Map.of(), stratosBlocks(), "the road was the only one");
        assertTrue(server().stratosBlocks(), "only a world bake reads every chunk");
    }

    @Test
    void aFailedSearchStopsTheBakeWithNothingChanged() {
        joinAndAck(ALICE);
        this.world.blocks.put(FakeWorld.key(0, 64, 0), "stratos:asphalt[layers=8]");
        this.findFails = true;
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        TaskResult result = result(runTask(ALICE));
        assertFalse(result.ok());
        assertEquals(Optional.of(Protocol.FIND_FAILED), result.reasonCode());
        assertEquals("stratos:asphalt[layers=8]", this.world.blockState(0, 64, 0));
        assertTrue(this.log.stream().anyMatch(line -> line.contains("a region file is unreadable")), this.log.toString());
    }

    @Test
    void bakingTheWorldAndRedrawingNeedTheAdminNode() {
        joinAndAck(ALICE);
        this.permissions = (player, node) -> !node.equals(EditorServer.NODE_ADMIN);
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        assertEquals(List.of(new TaskResult(4, false, Optional.of(Protocol.PERMISSION_DENIED), Map.of("node", "stratos.admin"))), take(ALICE));
        send(ALICE, new TaskRequest(5, Protocol.TASK_REDRAW_MARKINGS, Optional.empty()));
        assertEquals(List.of(new TaskResult(5, false, Optional.of(Protocol.PERMISSION_DENIED), Map.of("node", "stratos.admin"))), take(ALICE));
        assertEquals(List.of(), this.searches);
    }

    @Test
    void whileATaskRunsThePlayersOtherRequestsAreBusy() {
        joinAndAck(ALICE);
        this.findTicks = 50;
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        send(ALICE, new TaskRequest(5, Protocol.TASK_BAKE_WORLD, Optional.empty()));
        assertEquals(List.of(new TaskResult(5, false, Optional.of(Protocol.BUSY), Map.of())), take(ALICE));
        RoadPlanParams road = ukRoad(0);
        send(ALICE, new PlanRequest(6, "roads", "STRAIGHT", road.encode(), Digest.of(new byte[Digest.LENGTH])));
        assertEquals(Protocol.BUSY, assertInstanceOf(PlanRejected.class, take(ALICE).get(0)).reasonCode());
        send(ALICE, new Undo(7));
        assertEquals(Optional.of(Protocol.BUSY), assertInstanceOf(UndoRedoResult.class, take(ALICE).get(0)).reasonCode());
        server().leave(ALICE);
        server().tick();
        assertEquals(List.of(), take(ALICE), "a player who left hears nothing more");
    }

    @Test
    void anUnknownTaskIsNotAvailable() {
        joinAndAck(ALICE);
        send(ALICE, new TaskRequest(4, "PAINT_IT_BLACK", Optional.empty()));
        assertEquals(List.of(new TaskResult(4, false, Optional.of(Protocol.NOT_AVAILABLE_YET), Map.of("feature", "world_tasks"))), take(ALICE));
    }

    @Test
    void bakingARoadBakesOnlyThatRoadsBlocks() {
        joinAndAck(ALICE);
        UUID first = place(ALICE, 1, ukRoad(0));
        place(ALICE, 2, ukRoad(40));
        int secondRoad = (int) stratosBlocks().keySet().stream().filter(key -> Integer.parseInt(key.split(",")[2]) >= 20).count();
        this.world.blocks.put(FakeWorld.key(200, 64, 200), "stratos:asphalt[layers=8]"); // by hand, elsewhere
        Map<String, String> stratos = stratosBlocks();

        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_ROAD, Optional.of(first)));
        TaskResult result = result(runTask(ALICE));
        assertTrue(result.ok(), result.toString());
        assertEquals(1, this.searches.size());
        assertTrue(this.searches.get(0).isPresent(), "only the road's blocks");
        Map<String, String> left = stratosBlocks();
        assertEquals(secondRoad + 1, left.size(), "the other road's Stratos blocks, and the one placed by hand, stay");
        assertEquals(stratos.size() - left.size(), Integer.parseInt(result.args().get("blocks")));
        assertEquals(Protocol.TASK_BAKE_ROAD, this.operations.records().get(this.operations.records().size() - 1).tool());
    }

    @Test
    void aRoadThatIsntYoursOrIsGoneIsntBaked() {
        joinAndAck(ALICE);
        joinAndAck(BOB);
        UUID bobs = place(BOB, 1, ukRoad(0));
        send(ALICE, new TaskRequest(4, Protocol.TASK_BAKE_ROAD, Optional.of(bobs)));
        assertEquals(List.of(new TaskResult(4, false, Optional.of("NOT_YOUR_ROAD"), Map.of())), take(ALICE));
        send(ALICE, new TaskRequest(5, Protocol.TASK_BAKE_ROAD, Optional.of(new UUID(3, 3))));
        assertEquals(List.of(new TaskResult(5, false, Optional.of("ROAD_GONE"), Map.of())), take(ALICE));
        this.permissions = (player, node) -> !node.equals(RoadsModule.NODE_EDIT);
        send(BOB, new TaskRequest(6, Protocol.TASK_BAKE_ROAD, Optional.of(bobs)));
        assertEquals(List.of(new TaskResult(6, false, Optional.of(Protocol.PERMISSION_DENIED), Map.of("node", RoadsModule.NODE_EDIT))), take(BOB));
    }

    void runApply() {
        for (int tick = 0; tick < 100_000; tick++) {
            this.operations.scheduler().tick(this.world, 5_000_000, () -> this.world.now);
            this.operations.afterTick().forEach(server()::finished);
            server().tick();
            if (this.operations.scheduler().isIdle() && !this.operations.isBusy(ALICE.toString())) {
                return;
            }
        }
        throw new AssertionError("the apply engine never finished");
    }
}
