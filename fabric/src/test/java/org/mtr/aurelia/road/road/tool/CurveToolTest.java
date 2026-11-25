/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.tool.Answer;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Action;
import org.mtr.aurelia.road.tool.HudModel.Hint;
import org.mtr.aurelia.road.tool.HudModel.Level;
import org.mtr.aurelia.road.tool.HudModel.Line;
import org.mtr.aurelia.road.tool.HudModel.Notice;
import org.mtr.aurelia.road.tool.HudModel.Text;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.ToolOutput;
import org.mtr.aurelia.road.tool.ToolState;

/** The curved road tool (R-3): ends set like MTR nodes, its three steps, dragging handles, refusals and the HUD. */
class CurveToolTest {
    /** Minecraft yaws facing each way (yaw = heading − 90°). */
    static final float EAST = -90F;
    static final float SOUTH = 0F;
    static final float WEST = 90F;
    static final float NORTH = 180F;

    static final RoadPlanParams BEND = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 40, Heading.S)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID);

    final CurveTool tool = new CurveTool();

    static Optional<Target> at(int x, int y, int z) {
        return Optional.of(new Target(x, y, z, 12.5));
    }

    /** What the session does when a plan arrives: works out the tool's current job and hands it back. */
    void arrive() {
        StraightTool.RoadJob job = (StraightTool.RoadJob) this.tool.output().job().orElseThrow();
        this.tool.planned(job, job.run(WorldLimits.OVERWORLD));
    }

    /** A start facing east at (0, 64, 0), an end facing south at (40, 64, 40), worked out and fixed: step 3. */
    void readyToPlace() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0), EAST));
        this.tool.input(new ToolInput.Aim(at(40, 64, 40), SOUTH));
        arrive();
        this.tool.input(new ToolInput.Use(at(40, 64, 40), SOUTH));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        assertEquals(Optional.of(BEND), this.tool.params());
    }

    @Test
    void theLayoutComesFromTheSharedSettings() {
        RoadSettings settings = new RoadSettings();
        CurveTool curve = new CurveTool(settings);
        settings.setProfile(RoadProfiles.ALLEY_7);
        curve.input(new ToolInput.Use(at(0, 64, 0), EAST));
        curve.input(new ToolInput.Aim(at(12, 64, 12), SOUTH));
        RoadPlanParams road = curve.params().orElseThrow();
        assertEquals("alley-7", road.profileId());
        assertEquals(new Line("stratos.tool.line.layout", Text.of("stratos.road.profile.alley-7"), false), curve.hud().lines().get(0));
        // A tight alley bend is planned with the long-vehicle warning, and the HUD shows it.
        StraightTool.RoadJob job = (StraightTool.RoadJob) curve.output().job().orElseThrow();
        curve.planned(job, job.run(WorldLimits.OVERWORLD));
        assertEquals(Optional.of(Notice.reason(Level.WARNING, RoadPlanner.TIGHT_FOR_LONG_VEHICLES, Map.of())), curve.hud().notice());
    }

    @Test
    void startEndPlaceThenTheServerPlacesIt() {
        assertEquals(ToolState.IDLE, this.tool.state());
        this.tool.input(new ToolInput.Use(Optional.empty(), EAST));
        assertEquals(ToolState.IDLE, this.tool.state(), "clicking the sky marks nothing");
        this.tool.input(new ToolInput.Use(at(0, 64, 0), EAST));
        assertEquals(ToolState.PLACING, this.tool.state());
        assertEquals(Optional.of(new CurveTool.Point(0, 64, 0, Heading.E)), this.tool.start());
        assertEquals(Optional.empty(), this.tool.params(), "the end is still on the start's block");

        this.tool.input(new ToolInput.Aim(at(40, 64, 40), SOUTH));
        assertEquals(Optional.of(BEND), this.tool.params());
        assertEquals(new ToolOutput(Optional.of(new StraightTool.RoadJob(BEND)), RoadPlanner.handles(BEND)), this.tool.output());
        arrive();
        assertTrue(this.tool.planned().orElseThrow().isPlanned());

        this.tool.input(new ToolInput.Use(at(40, 64, 40), SOUTH));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Aim(at(60, 64, 70), WEST));
        assertEquals(Optional.of(BEND), this.tool.params(), "a fixed end follows neither the aim nor the facing");

        this.tool.input(new ToolInput.Use(Optional.empty(), WEST));
        assertEquals(ToolState.COMMITTING, this.tool.state(), "placing doesn't need an aim");
        Commit<?> commit = this.tool.takeCommit().orElseThrow();
        assertEquals(RoadsModule.INSTANCE, commit.module());
        assertEquals(BEND, commit.params());
        assertEquals(RoadPlanner.plan(BEND, WorldLimits.OVERWORLD).plan().orElseThrow(), commit.plan());
        assertEquals(Optional.empty(), this.tool.takeCommit(), "each commit is taken once");

        this.tool.answered(new Answer.Sent(7));
        this.tool.answered(new Answer.Accepted(1000, 4));
        assertEquals(ToolState.COMMITTING, this.tool.state());
        this.tool.answered(new Answer.Done());
        assertEquals(ToolState.IDLE, this.tool.state(), "placed: ready for the next road");
    }

    @Test
    void endsAreDirectionlessLikeMtrNodes() {
        for (float startYaw : new float[] {EAST, WEST}) {
            for (float endYaw : new float[] {SOUTH, NORTH}) {
                CurveTool fresh = new CurveTool();
                fresh.input(new ToolInput.Use(at(0, 64, 0), startYaw));
                fresh.input(new ToolInput.Aim(at(40, 64, 40), endYaw));
                assertEquals(Optional.of(BEND), fresh.params(), "facing " + startYaw + " then " + endYaw);
            }
        }
        // An S-bend: both ends facing east-west, the end shifted 8 to the south.
        CurveTool s = new CurveTool();
        s.input(new ToolInput.Use(at(0, 64, 0), WEST));
        s.input(new ToolInput.Aim(at(80, 66, 8), WEST));
        assertEquals(Optional.of(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(80, 66, 8, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID)), s.params(),
                "each end at its own height (R-4)");
    }

    @Test
    void aFacingIsSnappedAsMtrSnapsANode() {
        assertEquals(Heading.E, MtrRails.facing(-90F));
        assertEquals(Heading.S, MtrRails.facing(0F));
        assertEquals(Heading.W, MtrRails.facing(90F));
        assertEquals(Heading.N, MtrRails.facing(180F));
        assertEquals(Heading.N, MtrRails.facing(-180F));
        assertEquals(Heading.SE, MtrRails.facing(-45F));
        assertEquals(Heading.E, MtrRails.facing(270F), "the game's yaw isn't wrapped");
        assertEquals(Heading.E, MtrRails.facing(-78.76F));
        assertEquals(Heading.ESE, MtrRails.facing(-78.75F), "a tie goes clockwise");
    }

    @Test
    void attackTakesTheLastStepBack() {
        readyToPlace();
        this.tool.input(new ToolInput.Attack(at(40, 64, 40), SOUTH));
        assertEquals(ToolState.PLACING, this.tool.state(), "the end follows the aim again");
        this.tool.input(new ToolInput.Aim(at(50, 64, 30), SOUTH));
        assertEquals(End.at(50, 64, 30, Heading.S), this.tool.params().orElseThrow().ends().get(1));
        this.tool.input(new ToolInput.Attack(at(50, 64, 30), SOUTH));
        assertEquals(ToolState.IDLE, this.tool.state());
        assertEquals(ToolOutput.EMPTY, this.tool.output());
    }

    @Test
    void aRefusedEndCantBeFixed() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0), EAST));
        this.tool.input(new ToolInput.Aim(at(15, 64, 15), SOUTH));
        arrive();
        PlanResult result = this.tool.planned().orElseThrow().result();
        assertEquals(Optional.of(PieceSolver.RADIUS_TOO_SMALL), result.code());
        this.tool.input(new ToolInput.Use(at(15, 64, 15), SOUTH));
        assertEquals(ToolState.PLACING, this.tool.state(), "a refused road can't be fixed");
        HudModel hud = this.tool.hud();
        assertEquals(Optional.of(Notice.reason(Level.ERROR, PieceSolver.RADIUS_TOO_SMALL, result.args())), hud.notice());
        assertEquals(Optional.empty(), hud.hint(Action.USE));

        // Fixed before the answer came, then refused: the end follows the aim again.
        CurveTool early = new CurveTool();
        early.input(new ToolInput.Use(at(0, 64, 0), EAST));
        early.input(new ToolInput.Aim(at(15, 64, 15), SOUTH));
        early.input(new ToolInput.Use(at(15, 64, 15), SOUTH));
        assertEquals(ToolState.PREVIEWING, early.state());
        StraightTool.RoadJob job = (StraightTool.RoadJob) early.output().job().orElseThrow();
        early.planned(job, job.run(WorldLimits.OVERWORLD));
        assertEquals(ToolState.PLACING, early.state());
    }

    @Test
    void rightAfterFixingTheEndUsePlacesEvenOnItsHandle() {
        readyToPlace();
        this.tool.input(new ToolInput.Use(at(40, 64, 40), SOUTH));
        assertEquals(ToolState.COMMITTING, this.tool.state(), "the second click of 'fix, place' places the road");
    }

    @Test
    void theEndHandleIsPickedUpMovedAndPutDown() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(20, 64, 20), SOUTH)); // off the handles: picking is armed
        this.tool.input(new ToolInput.Aim(at(41, 64, 39), SOUTH)); // next to the end handle
        assertEquals(List.of(new Handle(0, 64, 0, Handle.Kind.START), new Handle(40, 64, 40, Handle.Kind.END, true)), this.tool.output().handles(),
                "the handle under the crosshair lights up");
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.pick_up_end")), this.tool.hud().hint(Action.USE));

        this.tool.input(new ToolInput.Use(at(41, 64, 39), SOUTH));
        assertEquals(Optional.of(Handle.Kind.END), this.tool.held());
        assertEquals(ToolState.PLACING, this.tool.state(), "a held handle: the road can't be placed");
        HudModel moving = this.tool.hud();
        assertEquals(3, moving.step());
        assertEquals(Text.of("stratos.tool.state.moving_end"), moving.state());
        assertEquals(Optional.of(new Hint(Action.ATTACK, "stratos.tool.hint.put_back")), moving.hint(Action.ATTACK));

        this.tool.input(new ToolInput.Aim(at(40, 64, 46), SOUTH));
        RoadPlanParams moved = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 46, Heading.S)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID);
        assertEquals(Optional.of(moved), this.tool.params(), "the held end follows the aim and the facing");
        assertTrue(this.tool.output().handles().get(1).active(), "a held handle stays lit");
        arrive();
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.put_down")), this.tool.hud().hint(Action.USE));
        this.tool.input(new ToolInput.Use(at(40, 64, 46), SOUTH));
        assertEquals(Optional.empty(), this.tool.held());
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Aim(at(60, 64, 60), WEST));
        assertEquals(Optional.of(moved), this.tool.params(), "put down: it stays");
        this.tool.input(new ToolInput.Use(at(60, 64, 60), WEST));
        assertEquals(ToolState.COMMITTING, this.tool.state());
        assertEquals(moved, this.tool.takeCommit().orElseThrow().params(), "the moved road is placed");
    }

    @Test
    void attackPutsAHeldHandleBack() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(20, 64, 20), SOUTH));
        this.tool.input(new ToolInput.Aim(at(0, 64, 1), EAST));
        this.tool.input(new ToolInput.Use(at(0, 64, 1), EAST));
        assertEquals(Optional.of(Handle.Kind.START), this.tool.held(), "the start is the nearer handle");
        this.tool.input(new ToolInput.Aim(at(0, 64, -10), EAST));
        assertEquals(RoadPlanParams.curve("city-17", End.at(0, 64, -10, Heading.E), End.at(40, 64, 40, Heading.S)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID), this.tool.params().orElseThrow());
        assertEquals(Text.of("stratos.tool.state.moving_start"), this.tool.hud().state());
        this.tool.input(new ToolInput.Attack(at(0, 64, -10), EAST));
        assertEquals(Optional.empty(), this.tool.held());
        assertEquals(Optional.of(BEND), this.tool.params(), "back where it was");
        assertEquals(ToolState.PREVIEWING, this.tool.state());
    }

    @Test
    void aHandleCantBePutDownWhereTheRoadIsRefused() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(20, 64, 20), SOUTH));
        this.tool.input(new ToolInput.Aim(at(40, 64, 40), SOUTH));
        this.tool.input(new ToolInput.Use(at(40, 64, 40), SOUTH));
        this.tool.input(new ToolInput.Aim(at(15, 64, 15), SOUTH));
        arrive();
        this.tool.input(new ToolInput.Use(at(15, 64, 15), SOUTH));
        assertEquals(Optional.of(Handle.Kind.END), this.tool.held(), "known to be refused: it stays in hand");

        // Put down before the answer came, then refused: picked up again.
        this.tool.input(new ToolInput.Aim(at(14, 64, 14), SOUTH));
        this.tool.input(new ToolInput.Use(at(14, 64, 14), SOUTH));
        assertEquals(Optional.empty(), this.tool.held());
        arrive();
        assertEquals(Optional.of(Handle.Kind.END), this.tool.held());
        this.tool.input(new ToolInput.Attack(at(14, 64, 14), SOUTH));
        assertEquals(Optional.of(BEND), this.tool.params());
    }

    @Test
    void notSentRejectedAndFailedGoBackToStepThree() {
        readyToPlace();
        this.tool.input(new ToolInput.Use(Optional.empty(), EAST));
        this.tool.takeCommit();
        this.tool.answered(new Answer.NotSent("EDITING_OFF", Map.of()));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        assertEquals(Optional.of(Notice.reason(Level.ERROR, "EDITING_OFF", Map.of())), this.tool.hud().notice());
        this.tool.input(new ToolInput.Use(Optional.empty(), EAST));
        this.tool.answered(new Answer.Sent(3));
        this.tool.answered(new Answer.Rejected("BUSY", Map.of()));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Use(Optional.empty(), EAST));
        this.tool.answered(new Answer.Sent(4));
        this.tool.answered(new Answer.Failed("CONFLICT"));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
    }

    @Test
    void theHudSaysWhatToDoInEachStep() {
        HudModel idle = this.tool.hud();
        assertEquals("stratos.tool.roads.curve", idle.titleKey());
        assertEquals(1, idle.step());
        assertEquals(3, idle.steps());
        assertEquals(Text.of("stratos.tool.state.pick_start_facing"), idle.state());
        assertEquals(List.of("stratos.tool.line.layout", "stratos.tool.line.palette", "stratos.tool.line.length", "stratos.tool.line.headings", "stratos.tool.line.radius",
                "stratos.tool.line.slope", "stratos.tool.line.shape", "stratos.tool.line.nodes", "stratos.tool.line.crossing", "stratos.tool.line.blocks"),
                idle.lines().stream().map(Line::labelKey).toList());

        this.tool.input(new ToolInput.Use(at(0, 64, 0), EAST));
        HudModel noEnd = this.tool.hud();
        assertEquals(2, noEnd.step());
        assertEquals(Text.of("stratos.tool.state.aim_end_facing"), noEnd.state());
        assertEquals(Text.of("stratos.tool.value.facing", "E"), noEnd.lines().get(3).value());

        this.tool.input(new ToolInput.Aim(at(40, 66, 40), SOUTH));
        HudModel aiming = this.tool.hud();
        assertEquals(Text.of("stratos.tool.value.blocks", "62.8"), aiming.lines().get(2).value(), "a quarter circle of radius 40");
        assertEquals(Text.of("stratos.tool.value.headings", "E", "S"), aiming.lines().get(3).value());
        assertEquals(Text.of("stratos.tool.value.blocks", "40.0"), aiming.lines().get(4).value());
        Line slope = aiming.lines().get(5);
        assertEquals("stratos.tool.line.slope", slope.labelKey());
        assertEquals("+2", slope.value().args().get(0));
        assertTrue(!slope.warning(), "2 up over a 63-block bend is within 1:12");
        assertEquals(new Line("stratos.tool.line.blocks", HudModel.NONE, false), aiming.lines().get(9), "not worked out yet");
        arrive();
        String count = String.format(java.util.Locale.ROOT, "%,d", this.tool.planned().orElseThrow().result().plan().orElseThrow().blockCount());
        assertEquals(Text.of("stratos.tool.value.count", count), this.tool.hud().lines().get(9).value());
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.set_end")), this.tool.hud().hint(Action.USE));

        this.tool.input(new ToolInput.Use(at(40, 66, 40), SOUTH));
        HudModel check = this.tool.hud();
        assertEquals(3, check.step());
        assertEquals(Text.of("stratos.tool.state.check_place"), check.state());
        assertEquals(slope, check.lines().get(5), "the fixed end keeps its height");
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.place")), check.hint(Action.USE));
        assertEquals(Optional.of(new Hint(Action.ATTACK, "stratos.tool.hint.take_back_end")), check.hint(Action.ATTACK));

        // An S-bend shows its arcs' radius; a straight says so.
        CurveTool straight = new CurveTool();
        straight.input(new ToolInput.Use(at(0, 64, 0), EAST));
        straight.input(new ToolInput.Aim(at(30, 64, 0), EAST));
        assertEquals(Text.of("stratos.tool.value.straight"), straight.hud().lines().get(4).value());
    }

    @Test
    void pageUpRaisesTheEndThatFollows() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0), EAST));
        this.tool.input(new ToolInput.Aim(at(40, 64, 40), SOUTH));
        this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.RAISE, at(40, 64, 40), SOUTH));
        this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.RAISE, at(40, 64, 40), SOUTH));
        assertEquals(End.at(40, 66, 40, Heading.S), this.tool.params().orElseThrow().ends().get(1));
        this.tool.input(new ToolInput.Aim(at(40, 63, 44), SOUTH));
        assertEquals(End.at(40, 65, 44, Heading.S), this.tool.params().orElseThrow().ends().get(1));
    }

    @Test
    void resetDropsEverything() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(20, 64, 20), SOUTH));
        this.tool.input(new ToolInput.Aim(at(40, 64, 40), SOUTH));
        this.tool.input(new ToolInput.Use(at(40, 64, 40), SOUTH));
        this.tool.reset();
        assertEquals(ToolState.IDLE, this.tool.state());
        assertEquals(Optional.empty(), this.tool.held());
        assertEquals(ToolOutput.EMPTY, this.tool.output());
        assertEquals(Optional.empty(), this.tool.takeCommit());
    }
}
