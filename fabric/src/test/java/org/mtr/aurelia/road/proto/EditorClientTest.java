/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.Hello;
import org.mtr.aurelia.road.proto.Message.HelloAck;
import org.mtr.aurelia.road.proto.Message.HelloResult;
import org.mtr.aurelia.road.proto.Message.Limits;
import org.mtr.aurelia.road.proto.Message.Notice;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.PlanRequest;
import org.mtr.aurelia.road.proto.Message.Undo;
import org.mtr.aurelia.road.proto.Message.UndoRedoResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.road.nodes.MtrNodes;

/** The client's side of the protocol, alone and talking to a real {@link EditorServer} through the encoding (task F-3). */
class EditorClientTest {
    static final RoadPlanParams ROAD = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E));

    final List<Message> toServer = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    final EditorClient client = new EditorClient("0.1.0", EditorServerTest.CORE, List.of("roads"), this.toServer::add, message -> { });

    {
        this.client.addListener(new EditorClient.Listener() {
            @Override
            public void editingChanged(boolean enabled, Optional<String> reasonCode) {
                EditorClientTest.this.events.add("editing " + enabled + reasonCode.map(c -> " " + c).orElse(""));
            }

            @Override
            public void planAccepted(PlanAccepted accepted) {
                EditorClientTest.this.events.add("accepted " + accepted.requestId());
            }

            @Override
            public void planRejected(PlanRejected rejected) {
                EditorClientTest.this.events.add("rejected " + rejected.requestId() + " " + rejected.reasonCode());
            }

            @Override
            public void progress(ApplyProgress progress) {
                EditorClientTest.this.events.add("progress " + progress.state());
            }

            @Override
            public void undoRedo(UndoRedoResult result) {
                EditorClientTest.this.events.add("undo/redo " + result.requestId() + " " + result.ok());
            }

            @Override
            public void notice(Notice notice) {
                EditorClientTest.this.events.add("notice " + notice.code() + " " + notice.args());
            }
        });
    }

    void receive(Message message) {
        this.client.receive(Messages.encode(message));
    }

    @Test
    void nothingIsSentBeforeTheServerSaysHello() {
        assertFalse(this.client.editingEnabled());
        EditorClient.Attempt attempt = this.client.request(RoadsModule.INSTANCE, ROAD, WorldLimits.OVERWORLD);
        assertFalse(attempt.isSent());
        assertEquals(Optional.of(Protocol.EDITING_OFF), attempt.code());
        assertTrue(attempt.plan().isPresent(), "the plan is still worked out, for the preview");
        assertEquals(OptionalInt.empty(), this.client.undo());
        assertEquals(OptionalInt.empty(), this.client.redo());
        // A stray HelloResult without a Hello doesn't turn editing on.
        receive(new HelloResult(true, Optional.empty()));
        assertFalse(this.client.editingEnabled());
        assertTrue(this.toServer.isEmpty());
    }

    @Test
    void theServersPalettesAreTheOnesTheClientPlansWith() {
        // R-9: Stratos's built-in ones until they arrive, the server's after, and the built-in ones again with a new Hello.
        assertEquals(Palettes.BUILT_IN, this.client.palettes());
        Palette secondVanilla = BuiltInPalettes.provisional(); // another palette with the id vanilla
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        receive(new HelloResult(true, Optional.empty()));
        receive(new Message.Palettes(List.of(BuiltInPalettes.vanilla().json(), "{ not a palette", secondVanilla.json())));
        assertEquals(List.of(BuiltInPalettes.vanilla()), this.client.palettes().all(), "a broken one is dropped, a second vanilla too");
        assertEquals(Optional.of(BuiltInPalettes.provisional()), this.client.palettes().snapshot(BuiltInPalettes.provisional().snapshotId()));
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        assertEquals(Palettes.BUILT_IN, this.client.palettes());
    }

    @Test
    void theClientKeepsWhatTheServerLastSaidAboutStratosBlocks() {
        // UI-1c: it has them until the server says (the safe side), and again with a new Hello.
        assertTrue(this.client.stratosBlocks());
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        receive(new HelloResult(true, Optional.empty()));
        receive(new Message.StratosBlocks(false));
        assertFalse(this.client.stratosBlocks());
        receive(new Message.StratosBlocks(true));
        assertTrue(this.client.stratosBlocks());
        receive(new Message.StratosBlocks(false));
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        assertTrue(this.client.stratosBlocks());
    }

    @Test
    void theHandshakeAnswersHelloAndFollowsHelloResult() {
        Hello hello = new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE);
        receive(hello);
        assertEquals(List.of(new HelloAck(Protocol.VERSION, "0.1.0", List.of("roads"))), this.toServer);
        assertEquals(Optional.of(hello), this.client.serverHello());
        receive(new HelloResult(false, Optional.of(Protocol.PROTOCOL_MISMATCH)));
        assertFalse(this.client.editingEnabled());
        receive(new HelloResult(true, Optional.empty()));
        assertTrue(this.client.editingEnabled());
        this.client.disconnected();
        assertFalse(this.client.editingEnabled());
        assertEquals(Optional.empty(), this.client.serverHello());
        assertEquals(List.of("editing false PROTOCOL_MISMATCH", "editing true", "editing false"), this.events);
    }

    @Test
    void aRequestCarriesTheParametersAndTheLocalPlansHash() throws MalformedMessage {
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        receive(new HelloResult(true, Optional.empty()));
        this.toServer.clear();

        EditorClient.Attempt attempt = this.client.request(RoadsModule.INSTANCE, ROAD, WorldLimits.OVERWORLD);
        assertEquals(OptionalInt.of(0), attempt.requestId());
        BlockPlan plan = RoadPlanner.plan(ROAD, WorldLimits.OVERWORLD).plan().orElseThrow();
        assertEquals(Optional.of(plan), attempt.plan());
        PlanRequest request = assertInstanceOf(PlanRequest.class, this.toServer.get(0));
        assertEquals(new PlanRequest(0, "roads", "STRAIGHT", ROAD.encode(), Digest.of(PlanHash.of(plan, EditorServerTest.CORE))), request);
        assertEquals(ROAD, RoadsModule.INSTANCE.decode(request.tool(), request.params()));

        // A local refusal sends nothing.
        RoadPlanParams tooShort = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(1, 64, 0, Heading.E));
        EditorClient.Attempt refused = this.client.request(RoadsModule.INSTANCE, tooShort, WorldLimits.OVERWORLD);
        assertEquals(Optional.of(RoadPlanner.TOO_SHORT), refused.code());
        assertEquals(1, this.toServer.size());

        assertEquals(OptionalInt.of(1), this.client.undo());
        assertEquals(new Undo(1), this.toServer.get(1));
        assertEquals(2, this.client.sendPlanRequest("roads", "STRAIGHT", new byte[0], Digest.of(new byte[Digest.LENGTH])));
    }

    /** R-2: the road tool sends the plan it already worked out off the client thread, with that plan's hash. */
    @Test
    void sendPlannedSendsThePlansHashAndNothingWhileEditingIsOff() {
        BlockPlan plan = RoadPlanner.plan(ROAD, WorldLimits.OVERWORLD).plan().orElseThrow();
        EditorClient.Attempt off = this.client.sendPlanned(RoadsModule.INSTANCE, ROAD, plan);
        assertEquals(Optional.of(Protocol.EDITING_OFF), off.code());
        assertTrue(this.toServer.isEmpty());

        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        receive(new HelloResult(true, Optional.empty()));
        this.toServer.clear();
        EditorClient.Attempt sent = this.client.sendPlanned(RoadsModule.INSTANCE, ROAD, plan);
        assertEquals(OptionalInt.of(0), sent.requestId());
        assertEquals(Optional.of(plan), sent.plan());
        assertEquals(List.of(new PlanRequest(0, "roads", "STRAIGHT", ROAD.encode(), Digest.of(PlanHash.of(plan, EditorServerTest.CORE)))),
                this.toServer);
    }

    /** T-2: a road with track is sent with the hash of its blocks and its rails; the server says whether it has track. */
    @Test
    void trackIsInTheHashAndTheServersCapabilitySaysWhetherItCanBeBuilt() {
        assertFalse(this.client.serverHas(Protocol.CAPABILITY_TRACK), "no server yet");
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads"), Limits.NONE));
        assertFalse(this.client.serverHas(Protocol.CAPABILITY_TRACK), "a server without Stratos Transit");
        receive(new Hello(Protocol.VERSION, "0.1.0", "core", 3465, 1, List.of("roads", "track"), Limits.NONE));
        assertTrue(this.client.serverHas(Protocol.CAPABILITY_TRACK));
        receive(new HelloResult(true, Optional.empty()));
        this.toServer.clear();

        RoadPlanParams road = ROAD.withNodeAids(RoadPlanParams.NodeAids.TRACK);
        WorldLimits mtr = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
        PlanResult planned = RoadPlanner.plan(road, mtr);
        assertEquals(2, planned.sideEffects().size());
        this.client.request(RoadsModule.INSTANCE, road, mtr);
        Digest withTrack = Digest.of(PlanHash.of(planned.plan().orElseThrow(), planned.sideEffects(), EditorServerTest.CORE));
        assertEquals(withTrack, assertInstanceOf(PlanRequest.class, this.toServer.get(0)).clientPlanHash());
        this.client.sendPlanned(RoadsModule.INSTANCE, road, planned.plan().orElseThrow(), planned.sideEffects());
        assertEquals(withTrack, assertInstanceOf(PlanRequest.class, this.toServer.get(1)).clientPlanHash());

        receive(new Notice(Protocol.TRACK_NOT_BUILT, Map.of("count", "1", "total", "2")));
        String heard = this.events.get(this.events.size() - 1);
        assertTrue(heard.startsWith("notice TRACK_NOT_BUILT {") && heard.contains("count=1") && heard.contains("total=2"), heard);
        this.client.disconnected();
        assertFalse(this.client.serverHas(Protocol.CAPABILITY_TRACK), "gone with the server");
    }

    @Test
    void answersReachTheListenersAndMalformedOnesAreCounted() {
        receive(new PlanRejected(3, Protocol.BUSY, Map.of(), Optional.empty()));
        receive(new UndoRedoResult(4, Optional.empty(), false, Optional.of("NOTHING_TO_UNDO"), 0));
        this.client.receive(new byte[] {42});
        this.client.receive(Messages.encode(new Undo(1))); // client → server only
        assertEquals(List.of("rejected 3 BUSY", "undo/redo 4 false"), this.events);
        assertEquals(2, this.client.malformedCount());
    }

    /** The client and the server joined by the real encoding, both ways: the whole Slice A flow. */
    @Test
    void endToEndThroughTheEncoding() {
        EditorServerTest side = new EditorServerTest();
        List<Message> loop = new ArrayList<>();
        EditorClient player = new EditorClient("0.1.0", EditorServerTest.CORE, List.of("roads"),
                message -> side.server().receive(EditorServerTest.ALICE, Messages.encode(message)), message -> { });
        player.addListener(new EditorClient.Listener() {
            @Override
            public void planAccepted(PlanAccepted accepted) {
                loop.add(accepted);
            }

            @Override
            public void planRejected(PlanRejected rejected) {
                loop.add(rejected);
            }

            @Override
            public void progress(ApplyProgress progress) {
                loop.add(progress);
            }

            @Override
            public void undoRedo(UndoRedoResult result) {
                loop.add(result);
            }
        });
        side.server().join(EditorServerTest.ALICE, message -> player.receive(Messages.encode(message)));
        assertTrue(player.editingEnabled(), "Hello → HelloAck → HelloResult happened on join");

        EditorClient.Attempt attempt = player.request(RoadsModule.INSTANCE, ROAD, WorldLimits.OVERWORLD);
        assertTrue(attempt.isSent());
        side.server().tick();
        side.runApply();
        PlanAccepted accepted = assertInstanceOf(PlanAccepted.class, loop.get(0));
        assertEquals(new ApplyProgress(accepted.operationId(), ApplyState.DONE, accepted.sectionCount(), accepted.sectionCount(), Optional.empty()),
                loop.get(1));
        assertEquals(17 * 21 * 3, side.placed().size(), "the surface and its 2 blocks of foundation; the clearance is air");

        // The wrong hash on purpose (what the harness does): refused with the server's hash, nothing placed.
        RoadPlanParams other = RoadPlanParams.straight("city-17", End.at(0, 64, 40, Heading.E), End.at(20, 64, 40, Heading.E));
        int id = player.sendPlanRequest("roads", "STRAIGHT", other.encode(), Digest.of(new byte[Digest.LENGTH]));
        side.server().tick();
        PlanRejected mismatch = assertInstanceOf(PlanRejected.class, loop.get(2));
        assertEquals(id, mismatch.requestId());
        assertEquals(Protocol.PLAN_HASH_MISMATCH, mismatch.reasonCode());
        assertEquals(Optional.of(Digest.of(PlanHash.of(RoadPlanner.plan(other, WorldLimits.OVERWORLD).plan().orElseThrow(), EditorServerTest.CORE))),
                mismatch.serverPlanHash());
        assertEquals(17 * 21 * 3, side.placed().size(), "the surface and its 2 blocks of foundation; the clearance is air");

        player.undo();
        side.runApply();
        UndoRedoResult undone = assertInstanceOf(UndoRedoResult.class, loop.get(3));
        assertTrue(undone.ok());
        assertEquals(Optional.of(accepted.operationId()), undone.operationId());
        assertEquals(Map.of(), side.placed());

        // A client with another core version previews the same blocks but can't commit them (editor-protocol §3).
        EditorClient older = new EditorClient("0.0.9", "core-older", List.of("roads"),
                message -> side.server().receive(EditorServerTest.BOB, Messages.encode(message)), message -> { });
        List<Message> olderAnswers = new ArrayList<>();
        older.addListener(new EditorClient.Listener() {
            @Override
            public void planRejected(PlanRejected rejected) {
                olderAnswers.add(rejected);
            }
        });
        side.server().join(EditorServerTest.BOB, message -> older.receive(Messages.encode(message)));
        older.request(RoadsModule.INSTANCE, ROAD, WorldLimits.OVERWORLD);
        side.server().tick();
        assertEquals(Protocol.PLAN_HASH_MISMATCH, ((PlanRejected) olderAnswers.get(0)).reasonCode());
        assertEquals(Map.of(), side.placed());
    }
}
