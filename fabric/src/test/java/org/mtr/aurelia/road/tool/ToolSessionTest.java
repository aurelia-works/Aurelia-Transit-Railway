/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.preview.Preview;
import org.mtr.aurelia.road.preview.PreviewModel;
import org.mtr.aurelia.road.proto.Digest;
import org.mtr.aurelia.road.proto.Message;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.road.tool.StraightTool;
import org.mtr.aurelia.road.spi.PreviewSink;

/** Choosing, switching and putting away tools (R-1's round trips), and working plans out and placing them (R-2). */
class ToolSessionTest {
    /** A stand-in tool with a setting (a width) that must survive round trips, one point, and optionally a plan job. */
    static final class StandIn implements Tool {
        int width = 3;
        Target point;
        int resets;
        PlanJob job;
        final List<Planned> answers = new ArrayList<>();

        @Override
        public String id() {
            return "test.stand_in";
        }

        @Override
        public String moduleId() {
            return "test";
        }

        @Override
        public String titleKey() {
            return "test";
        }

        @Override
        public ToolState state() {
            return this.point == null ? ToolState.IDLE : ToolState.PLACING;
        }

        @Override
        public void input(ToolInput input) {
            if (input instanceof ToolInput.Use use) {
                use.target().ifPresent(t -> this.point = t);
            }
        }

        @Override
        public ToolOutput output() {
            return this.point == null ? ToolOutput.EMPTY
                    : new ToolOutput(Optional.ofNullable(this.job), List.of(new Handle(this.point.x(), this.point.y(), this.point.z(), Handle.Kind.END)));
        }

        @Override
        public HudModel hud() {
            return new HudModel("test", 0, 0, HudModel.Text.of("test"), List.of(), Optional.empty(), List.of());
        }

        @Override
        public void reset() {
            this.point = null;
            this.resets++;
        }

        @Override
        public void planned(PlanJob planJob, Planned planned) {
            this.answers.add(planned);
        }
    }

    final List<String> sink = new ArrayList<>();
    final Preview preview = new Preview(new PreviewSink() {
        @Override
        public void update(PreviewModel model, PreviewModel.Diff diff) {
            ToolSessionTest.this.sink.add("show " + model.handles().size() + (model.style() == PreviewModel.Style.REFUSED ? " red" : ""));
        }

        @Override
        public void clear() {
            ToolSessionTest.this.sink.add("clear");
        }
    });
    final ManualEnvironment environment = new ManualEnvironment();
    final ToolSession session = new ToolSession(this.preview, this.environment);
    final StraightTool straight = new StraightTool();
    final StandIn standIn = new StandIn();

    {
        this.session.register(this.straight);
        this.session.register(this.standIn);
    }

    @Test
    void aToolsWorldTaskIsSentAndItsAnswersGoBackToIt() {
        // B-3: the Edit roads tool's bake is a world task, answered like a commit.
        RoadPlanParams uk = RoadPlanParams.straight("city-17", RoadPlanParams.End.at(0, 64, 0, Heading.E), RoadPlanParams.End.at(40, 64, 0, Heading.E))
                .withPalette(BuiltInPalettes.STRATOS_UK_ID);
        org.mtr.aurelia.road.road.nodes.PlacedRoads roads = new org.mtr.aurelia.road.road.nodes.PlacedRoads();
        UUID id = new UUID(6, 6);
        roads.placed(id, uk, RoadPlanner.plan(uk, WorldLimits.OVERWORLD).plan().orElseThrow());
        org.mtr.aurelia.road.road.tool.EditRoadsTool edit = new org.mtr.aurelia.road.road.tool.EditRoadsTool(roads);
        ToolSession own = new ToolSession(this.preview, this.environment);
        own.register(edit);
        own.select(edit.id());
        own.input(new ToolInput.Use(at(20, 64, 0)));
        own.input(new ToolInput.Choose(org.mtr.aurelia.road.road.tool.EditRoadsTool.BAKE));
        assertEquals(1, this.environment.tasks.size());
        int requestId = this.environment.tasks.keySet().iterator().next();
        assertEquals(new WorldTask(Protocol.TASK_BAKE_ROAD, Optional.of(id)), this.environment.tasks.get(requestId));
        assertTrue(own.ownsRequest(requestId));
        own.taskProgress(new Message.TaskProgress(requestId + 1, Protocol.STAGE_FINDING, 1, 2)); // another request's
        own.taskProgress(new Message.TaskProgress(requestId, Protocol.STAGE_FINDING, 1, 2));
        assertEquals(org.mtr.aurelia.road.road.tool.EditRoadsTool.Stage.SENDING, edit.stage());
        own.taskResult(new Message.TaskResult(requestId, true, Optional.empty(), Map.of("blocks", "7", "kept", "0")));
        assertEquals(org.mtr.aurelia.road.road.tool.EditRoadsTool.Stage.AIMING, edit.stage());
        assertEquals("stratos.edit.notice.baked", edit.hud().notice().flatMap(HudModel.Notice::text).orElseThrow().key());

        this.environment.editing = false;
        own.input(new ToolInput.Use(at(20, 64, 0)));
        own.input(new ToolInput.Choose(org.mtr.aurelia.road.road.tool.EditRoadsTool.BAKE));
        assertEquals(Protocol.EDITING_OFF, edit.hud().notice().flatMap(HudModel.Notice::reason).orElseThrow().code(), "not sent");
    }

    static Optional<Target> at(int x, int y, int z) {
        return Optional.of(new Target(x, y, z, 10));
    }

    static BlockPlan roadPlan(int length) {
        RoadPlanParams params = RoadPlanParams.straight("city-17", RoadPlanParams.End.at(0, 64, 0, Heading.E),
                RoadPlanParams.End.at(length, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.WOOL)
                .withPalette(BuiltInPalettes.DEFAULT_ID); // the tools' default
        return RoadPlanner.plan(params, WorldLimits.OVERWORLD).plan().orElseThrow();
    }

    /** The straight tool in hand with a 20-block road east of (0, 64, 0), worked out and fixed: step 3. */
    void roadReadyToPlace() {
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        this.session.input(new ToolInput.Aim(at(20, 64, 0)));
        this.environment.runAll();
        this.session.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(ToolState.PREVIEWING, this.straight.state());
    }

    @Test
    void changedSettingsWorkTheToolsPlanOutAgain() {
        RoadSettings settings = new RoadSettings();
        ToolSession own = new ToolSession(this.preview, this.environment);
        StraightTool tool = new StraightTool(settings);
        own.register(tool);
        own.select(StraightTool.ID);
        own.input(new ToolInput.Use(at(0, 64, 0)));
        own.input(new ToolInput.Aim(at(80, 67, 0)));
        this.environment.runAll();
        settings.setRadius(250); // as the radius box does
        own.settingsChanged();
        assertFalse(this.environment.background.isEmpty(), "the road with the new shape is worked out");
        this.environment.runAll();
        assertEquals(RoadPlanParams.Vertical.Shape.TWO_RADII, tool.planned().orElseThrow().result().isPlanned()
                ? tool.params().orElseThrow().vertical().shape() : null);
    }

    /** T-2: a road placed with Track is sent with its rails, so their hash goes with it. */
    @Test
    void aRoadPlacedWithTrackIsSentWithItsRails() {
        RoadSettings settings = new RoadSettings(() -> true, () -> true);
        this.environment.limits = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
        ToolSession own = new ToolSession(this.preview, this.environment);
        StraightTool tool = new StraightTool(settings);
        own.register(tool);
        own.select(StraightTool.ID);
        own.input(new ToolInput.Use(at(0, 64, 0)));
        own.input(new ToolInput.Aim(at(20, 64, 0)));
        this.environment.runAll();
        own.input(new ToolInput.Use(at(20, 64, 0)));
        own.input(new ToolInput.Use(at(20, 64, 0)));
        ManualEnvironment.Sent sent = this.environment.sent.get(this.environment.sent.size() - 1);
        assertEquals(RoadPlanParams.NodeAids.TRACK, ((RoadPlanParams) sent.params()).nodeAids());
        assertEquals(tool.planned().orElseThrow().result().sideEffects(), sent.sideEffects());
        assertEquals(2, sent.sideEffects().size(), "one rail per lane");
    }

    @Test
    void aHandleLightingUpIsRedrawnWithoutWorkingThePlanOutAgain() {
        roadReadyToPlace();
        this.sink.clear();
        this.session.input(new ToolInput.Aim(at(10, 64, 6)));
        this.session.input(new ToolInput.Aim(at(20, 64, 0)));
        assertTrue(this.environment.background.isEmpty(), "same plan: nothing to work out");
        assertEquals(List.of("show 2"), this.sink, "only the handles are redrawn");
        assertTrue(this.preview.current().handles().get(1).active());
        assertFalse(this.preview.current().sections().isEmpty(), "the road is still shown");
        this.session.input(new ToolInput.Aim(at(10, 64, 6)));
        assertFalse(this.preview.current().handles().get(1).active());
    }

    /** Places the road ready to place, and returns the request id it was sent with. */
    int place() {
        this.session.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(ToolState.COMMITTING, this.straight.state());
        return this.environment.sent.get(this.environment.sent.size() - 1).requestId();
    }

    static PlanAccepted accepted(int requestId, UUID operation) {
        return new PlanAccepted(requestId, operation, Digest.of(new byte[Digest.LENGTH]), 357, 2);
    }

    // ---- R-1: round trips ----

    @Test
    void switchingAwayAndBackStartsFromIdleWithThePreviewCleared() {
        this.session.select(StraightTool.ID);
        assertTrue(this.session.input(new ToolInput.Use(at(0, 64, 0))));
        assertEquals(ToolState.PLACING, this.straight.state());
        assertEquals(List.of("show 1"), this.sink, "the start handle is shown");

        this.session.select("test.stand_in");
        assertEquals(ToolState.IDLE, this.straight.state(), "the half-placed road was dropped");
        assertEquals(List.of("show 1", "clear"), this.sink);
        assertTrue(this.preview.current().isEmpty());

        this.session.select(StraightTool.ID);
        assertEquals(ToolState.IDLE, this.straight.state());
        assertEquals(Optional.of(this.straight), this.session.active());
        assertTrue(this.preview.current().isEmpty(), "nothing comes back on its own");
    }

    @Test
    void settingsSurviveARoundTripButPointsDont() {
        this.session.select("test.stand_in");
        this.standIn.width = 7;
        this.session.input(new ToolInput.Use(at(1, 2, 3)));
        assertEquals(ToolState.PLACING, this.standIn.state());
        this.session.select(StraightTool.ID);
        this.session.select("test.stand_in");
        assertEquals(7, this.standIn.width, "a setting stays");
        assertEquals(ToolState.IDLE, this.standIn.state(), "a point doesn't");
    }

    @Test
    void leavingPutsTheToolAwayAndGivesInputBack() {
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        this.session.leave();
        assertEquals(Optional.empty(), this.session.active());
        assertEquals(Optional.empty(), this.session.hud());
        assertEquals(ToolState.IDLE, this.straight.state());
        assertTrue(this.preview.current().isEmpty());
        assertFalse(this.session.input(new ToolInput.Use(at(5, 64, 5))), "with no tool, the game keeps its clicks");
        assertFalse(this.session.input(new ToolInput.Attack(Optional.empty())));
        assertEquals(ToolState.IDLE, this.straight.state());
    }

    @Test
    void aLeftClickWithNothingToTakeBackPutsTheToolAwayAndTheGameGetsIt() {
        // 2026-11-26: with the road tool out and nothing set, a left click has to break the block aimed at (a rail node).
        this.session.select(StraightTool.ID);
        assertFalse(this.session.input(new ToolInput.Attack(at(0, 64, 0))), "the game gets the click");
        assertEquals(Optional.empty(), this.session.active());
        assertEquals(Optional.of(this.straight), this.session.last(), "the tool key brings it back");
    }

    @Test
    void aLeftClickThatTakesAStepBackStaysWithTheTool() {
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        assertTrue(this.session.input(new ToolInput.Attack(at(5, 64, 0))));
        assertEquals(Optional.of(this.straight), this.session.active());
        assertEquals(ToolState.IDLE, this.straight.state(), "the start was taken back");
    }

    @Test
    void theHudSaysWhatALeftClickDoesWhenItPutsTheToolAway() {
        this.session.select(StraightTool.ID);
        assertEquals(Optional.of("stratos.tool.hint.put_away"),
                this.session.hud().flatMap(hud -> hud.hint(HudModel.Action.ATTACK)).map(HudModel.Hint::textKey));
    }

    @Test
    void theToolKeyTogglesTheLastTool() {
        this.session.toggle();
        assertEquals(Optional.empty(), this.session.active(), "no tool used yet: nothing to bring back");
        this.session.select("test.stand_in");
        this.session.toggle();
        assertEquals(Optional.empty(), this.session.active());
        assertEquals(Optional.of(this.standIn), this.session.last());
        this.session.toggle();
        assertEquals(Optional.of(this.standIn), this.session.active());
    }

    @Test
    void choosingTheActiveToolAgainChangesNothing() {
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        this.session.select(StraightTool.ID);
        assertEquals(ToolState.PLACING, this.straight.state());
    }

    @Test
    void toolsAreListedByModuleAndIdsAreUnique() {
        assertEquals(List.of(this.straight), this.session.tools("roads"));
        assertEquals(List.of(this.straight, this.standIn), this.session.tools());
        assertThrows(IllegalArgumentException.class, () -> this.session.register(new StraightTool()));
        assertThrows(IllegalArgumentException.class, () -> this.session.select("roads.curve"));
    }

    // ---- R-2: plans in the background ----

    @Test
    void plansAreWorkedOutInTheBackgroundAndOnlyTheNewestIsShown() {
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        this.session.input(new ToolInput.Aim(at(20, 64, 0)));
        assertEquals(1, this.environment.background.size(), "the road is worked out off the client thread");
        assertTrue(this.session.planning());
        assertEquals(List.of("show 1"), this.sink, "nothing new is drawn until the plan arrives");
        FutureTask<?> first = this.environment.background.get(0);

        this.session.input(new ToolInput.Aim(at(40, 64, 0)));
        assertTrue(first.isCancelled(), "a newer road stops the older one");
        this.session.input(new ToolInput.Aim(at(40, 64, 0)));
        assertEquals(2, this.environment.background.size(), "aiming at the same block again starts nothing");
        this.environment.runAll();
        assertEquals(List.of("show 1", "show 2"), this.sink, "only the 40-block road is drawn");
        assertEquals(roadPlan(40).blockCount(), this.preview.current().blockCount());
        assertFalse(this.session.planning());
        assertEquals(roadPlan(40), this.straight.planned().orElseThrow().result().plan().orElseThrow());
    }

    @Test
    void anAnswerThatArrivesAfterANewerRoadIsDropped() {
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        this.session.input(new ToolInput.Aim(at(20, 64, 0)));
        this.environment.runBackground(); // worked out; its answer waits for the client thread
        this.session.input(new ToolInput.Aim(at(30, 64, 0)));
        this.environment.runClientThread(); // the 20-block answer arrives late
        assertEquals(List.of("show 1"), this.sink, "the old answer isn't shown");
        assertEquals(Optional.empty(), this.straight.planned());
        this.environment.runAll();
        assertEquals(roadPlan(30).blockCount(), this.preview.current().blockCount());
    }

    @Test
    void aRefusedPlanIsDrawnInRed() {
        this.environment.limits = new WorldLimits(-64, 320, -100, 15, -100, 100); // the border cuts the road
        this.session.select(StraightTool.ID);
        this.session.input(new ToolInput.Use(at(0, 64, 0)));
        this.session.input(new ToolInput.Aim(at(20, 64, 0)));
        this.environment.runAll();
        assertEquals(List.of("show 1", "show 2 red"), this.sink);
        assertEquals(PreviewModel.Style.REFUSED, this.preview.current().style());
        assertTrue(this.preview.current().blockCount() > 0, "the road's shape is drawn");
    }

    @Test
    void aJobThatThrowsIsReportedAndRefused() {
        this.standIn.job = limits -> {
            throw new IllegalStateException("a bug");
        };
        this.session.select("test.stand_in");
        this.session.input(new ToolInput.Use(at(1, 2, 3)));
        this.environment.runAll();
        assertEquals(1, this.environment.problems.size());
        assertEquals(Optional.of(Protocol.INTERNAL_ERROR), this.standIn.answers.get(0).result().code());
        assertEquals(PreviewModel.Style.REFUSED, this.preview.current().style());
    }

    // ---- R-2: placing ----

    @Test
    void aCommitIsSentOnceAndItsAnswersReachTheTool() {
        roadReadyToPlace();
        int id = place();
        assertEquals(1, this.environment.sent.size());
        ManualEnvironment.Sent sent = this.environment.sent.get(0);
        assertEquals("roads", sent.module());
        assertEquals(this.straight.params().orElseThrow(), sent.params());
        assertEquals(roadPlan(20), sent.plan(), "the plan the preview shows is sent, not worked out again");
        this.session.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(1, this.environment.sent.size(), "clicking again while it is placed sends nothing");

        UUID operation = UUID.randomUUID();
        this.session.planAccepted(accepted(id + 1, operation));
        this.session.planAccepted(accepted(id, operation));
        this.session.progress(new ApplyProgress(operation, ApplyState.APPLYING, 1, 2, Optional.empty()));
        this.session.progress(new ApplyProgress(UUID.randomUUID(), ApplyState.DONE, 2, 2, Optional.empty()));
        assertEquals(ToolState.COMMITTING, this.straight.state(), "another request's answers change nothing");
        assertFalse(this.preview.current().isEmpty(), "the preview stays while the road is placed");

        this.session.progress(new ApplyProgress(operation, ApplyState.DONE, 2, 2, Optional.empty()));
        assertEquals(ToolState.IDLE, this.straight.state(), "placed: ready for the next road");
        assertTrue(this.preview.current().isEmpty());
        assertTrue(this.session.ownsRequest(id), "the tool in hand sent it");
    }

    @Test
    void thePlatformHearsOfEachPlacedCommitEvenAfterTheToolIsPutAway() {
        roadReadyToPlace();
        int id = place();
        UUID operation = UUID.randomUUID();
        this.session.planAccepted(accepted(id, operation));
        this.session.leave(); // put away while the road is placed: it is still placed
        assertEquals(List.of(), this.environment.placed);
        this.session.progress(new ApplyProgress(operation, ApplyState.DONE, 2, 2, Optional.empty()));
        assertEquals(1, this.environment.placed.size());
        assertEquals(operation, this.environment.placed.get(0).getValue());
        assertEquals(roadPlan(20), this.environment.placed.get(0).getKey().plan());
        this.session.progress(new ApplyProgress(operation, ApplyState.DONE, 2, 2, Optional.empty()));
        assertEquals(1, this.environment.placed.size(), "once");

        roadReadyToPlace();
        int failing = place();
        UUID failed = UUID.randomUUID();
        this.session.planAccepted(accepted(failing, failed));
        this.session.progress(new ApplyProgress(failed, ApplyState.FAILED, 1, 2, Optional.of("BLOCK_ENTITY")));
        assertEquals(1, this.environment.placed.size(), "a failed apply isn't placed");
    }

    @Test
    void aRefusalOrAFailureGoesBackToStepThree() {
        roadReadyToPlace();
        int id = place();
        this.session.planRejected(new PlanRejected(id, Protocol.BUSY, Map.of(), Optional.empty()));
        assertEquals(ToolState.PREVIEWING, this.straight.state());
        assertFalse(this.preview.current().isEmpty(), "the road is still shown");

        int second = place();
        UUID operation = UUID.randomUUID();
        this.session.planAccepted(accepted(second, operation));
        this.session.progress(new ApplyProgress(operation, ApplyState.FAILED, 1, 2, Optional.of("BLOCK_ENTITY")));
        assertEquals(ToolState.PREVIEWING, this.straight.state());
        this.session.planRejected(new PlanRejected(second, Protocol.BUSY, Map.of(), Optional.empty()));
        assertEquals(ToolState.PREVIEWING, this.straight.state(), "a finished request's late answers change nothing");
    }

    @Test
    void aMismatchWorksThePlanOutAgainAndRedrawsTheWholePreview() {
        roadReadyToPlace();
        int id = place();
        this.sink.clear();
        this.session.planRejected(new PlanRejected(id, Protocol.PLAN_HASH_MISMATCH, Map.of(), Optional.of(Digest.of(new byte[Digest.LENGTH]))));
        assertEquals(ToolState.PREVIEWING, this.straight.state());
        assertEquals(List.of("clear"), this.sink, "the old preview goes at once");
        assertEquals(1, this.environment.background.size(), "and the road is worked out again");
        this.environment.runAll();
        assertEquals(List.of("clear", "show 2"), this.sink, "then every section is drawn afresh");
        assertTrue(this.session.ownsRequest(id), "so the message can say the preview was refreshed");
        this.session.leave();
        assertFalse(this.session.ownsRequest(id));
    }

    @Test
    void puttingTheToolAwayWhileItsRoadIsPlacedDropsTheAnswers() {
        roadReadyToPlace();
        int id = place();
        this.session.leave();
        UUID operation = UUID.randomUUID();
        this.session.planAccepted(accepted(id, operation));
        this.session.progress(new ApplyProgress(operation, ApplyState.DONE, 2, 2, Optional.empty()));
        this.session.select(StraightTool.ID);
        assertEquals(ToolState.IDLE, this.straight.state());
        assertTrue(this.preview.current().isEmpty());
        assertFalse(this.session.ownsRequest(id));
    }

    @Test
    void whenEditingIsOffNothingIsSentAndTheHudSaysWhy() {
        this.environment.editing = false;
        roadReadyToPlace();
        this.session.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(List.of(), this.environment.sent);
        assertEquals(ToolState.PREVIEWING, this.straight.state());
        assertEquals(Optional.of(HudModel.Notice.reason(HudModel.Level.ERROR, Protocol.EDITING_OFF, Map.of())), this.session.hud().orElseThrow().notice());
    }

    @Test
    void losingTheServerMidRequestGoesBackToStepThree() {
        roadReadyToPlace();
        place();
        this.session.editingChanged(false, Optional.empty());
        assertEquals(ToolState.PREVIEWING, this.straight.state());
        assertFalse(this.preview.current().isEmpty(), "the road is still shown");
    }
}
