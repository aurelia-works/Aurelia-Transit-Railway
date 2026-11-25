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
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
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
import org.mtr.aurelia.road.tool.Planned;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.ToolOutput;
import org.mtr.aurelia.road.tool.ToolState;

/** The straight road tool on its own (R-1's shell, R-2's three steps): the session's part is in {@code ToolSessionTest}. */
class StraightToolTest {
    final StraightTool tool = new StraightTool();

    static Optional<Target> at(int x, int y, int z) {
        return Optional.of(new Target(x, y, z, 12.5));
    }

    /** What the session does when a plan arrives: works out the tool's current job and hands it back. */
    void arrive(WorldLimits limits) {
        StraightTool.RoadJob job = (StraightTool.RoadJob) this.tool.output().job().orElseThrow();
        this.tool.planned(job, job.run(limits));
    }

    /** Start at (0, 64, 0), aim at (20, 64, 0), the plan arrives, the end is fixed: step 3. */
    void readyToPlace() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(20, 64, 0)));
        arrive(WorldLimits.OVERWORLD);
        this.tool.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
    }

    @Test
    void theLayoutComesFromTheSharedSettings() {
        RoadSettings settings = new RoadSettings();
        StraightTool straight = new StraightTool(settings);
        straight.input(new ToolInput.Use(at(0, 64, 0)));
        straight.input(new ToolInput.Aim(at(20, 64, 0)));
        assertEquals("city-17", straight.params().orElseThrow().profileId());
        assertEquals(new Line("stratos.tool.line.layout", Text.of("stratos.road.profile.city-17"), false), straight.hud().lines().get(0));
        // Chosen in the hub while the end follows the aim: the road and its Layout row change at once.
        settings.setProfile(RoadProfiles.RESIDENTIAL_15);
        RoadPlanParams road = straight.params().orElseThrow();
        assertEquals("residential-15", road.profileId());
        assertEquals(new StraightTool.RoadJob(road), straight.output().job().orElseThrow(), "the preview is planned again");
        assertEquals(new Line("stratos.tool.line.layout", Text.of("stratos.road.profile.residential-15"), false), straight.hud().lines().get(0));
    }

    @Test
    void startEndConfirmThenTheServerPlacesIt() {
        assertEquals(ToolState.IDLE, this.tool.state());
        this.tool.input(new ToolInput.Use(Optional.empty()));
        assertEquals(ToolState.IDLE, this.tool.state(), "clicking the sky marks nothing");
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        assertEquals(ToolState.PLACING, this.tool.state());
        assertEquals(new ToolOutput(Optional.empty(), List.of(new Handle(0, 64, 0, Handle.Kind.START))), this.tool.output(), "no end yet");

        this.tool.input(new ToolInput.Aim(at(20, 64, 0)));
        RoadPlanParams road = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID);
        assertEquals(Optional.of(road), this.tool.params());
        assertEquals(new ToolOutput(Optional.of(new StraightTool.RoadJob(road)), RoadPlanner.handles(road)), this.tool.output());
        arrive(WorldLimits.OVERWORLD);

        this.tool.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Aim(at(35, 64, 0)));
        assertEquals(Optional.of(road), this.tool.params(), "a fixed end doesn't follow the aim");

        this.tool.input(new ToolInput.Use(Optional.empty()));
        assertEquals(ToolState.COMMITTING, this.tool.state(), "placing doesn't need an aim");
        Commit<?> commit = this.tool.takeCommit().orElseThrow();
        assertEquals(new Commit<>(RoadsModule.INSTANCE, road, RoadPlanner.plan(road, WorldLimits.OVERWORLD).plan().orElseThrow()), commit);
        assertEquals(Optional.empty(), this.tool.takeCommit(), "each commit is taken once");
        this.tool.input(new ToolInput.Attack(Optional.empty()));
        assertEquals(ToolState.COMMITTING, this.tool.state(), "clicks wait while the server has it");

        this.tool.answered(new Answer.Sent(3));
        this.tool.answered(new Answer.Accepted(357, 2));
        this.tool.answered(new Answer.Progress(1, 2));
        assertEquals(ToolState.COMMITTING, this.tool.state());
        this.tool.answered(new Answer.Done());
        assertEquals(ToolState.IDLE, this.tool.state(), "placed: ready for the next road");
        assertEquals(ToolOutput.EMPTY, this.tool.output());
    }

    @Test
    void attackTakesTheLastStepBack() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(30, 64, 0)));
        this.tool.input(new ToolInput.Attack(Optional.empty()));
        assertEquals(ToolState.PLACING, this.tool.state());
        assertEquals(End.at(30, 64, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1), "the end follows the aim again");
        this.tool.input(new ToolInput.Attack(Optional.empty()));
        assertEquals(ToolState.IDLE, this.tool.state());
        assertEquals(ToolOutput.EMPTY, this.tool.output());
        this.tool.input(new ToolInput.Attack(Optional.empty()));
        assertEquals(ToolState.IDLE, this.tool.state(), "nothing to take back");
    }

    @Test
    void theEndSnapsOntoAHeadingAtTheAimedHeight() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(35, 70, 14))); // 21.8°: ESE, 37.7 blocks along, 6 up
        RoadPlanParams params = this.tool.params().orElseThrow();
        assertEquals(End.at(0, 64, 0, Heading.ESE), params.ends().get(0));
        assertEquals(End.at(29, 70, 12, Heading.ESE), params.ends().get(1), "the nearest end within 0.05 of the line, at the aimed height (R-4)");
        HudModel hud = this.tool.hud();
        assertEquals(new Line("stratos.tool.line.length", Text.of("stratos.tool.value.blocks", "31.4"), false), hud.lines().get(2));
        assertEquals(new Line("stratos.tool.line.heading", Text.of("stratos.tool.value.heading", "ESE", "22.5"), false), hud.lines().get(3));
        Line slope = hud.lines().get(4);
        assertEquals("stratos.tool.line.slope", slope.labelKey());
        assertEquals("+6", slope.value().args().get(0));
        assertEquals("8.3", slope.value().args().get(2));
        assertTrue(slope.warning(), "6 up over 31 blocks is over 1:12");

        this.tool.input(new ToolInput.Aim(Optional.empty()));
        assertEquals(Optional.of(params), this.tool.params(), "aiming at nothing keeps the last end");
        this.tool.input(new ToolInput.Aim(at(0, 64, 0)));
        assertEquals(Optional.of(params), this.tool.params(), "aiming at the start keeps the last end");
        this.tool.input(new ToolInput.Aim(at(1, 64, 0)));
        assertEquals(End.at(2, 64, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1), "never shorter than 2 blocks");
    }

    @Test
    void theLiveEndFollowsTheAimAtAllSixteenHeadings() {
        this.tool.input(new ToolInput.Use(at(100, 70, -40)));
        for (Heading heading : Heading.values()) {
            Vec2 aimed = new Frame(Vec2.ZERO, heading).point(60, 0);
            int dx = (int) StrictMath.rint(aimed.x());
            int dz = (int) StrictMath.rint(aimed.z());
            this.tool.input(new ToolInput.Aim(at(100 + dx, 72, -40 + dz)));
            StraightTool.Measure m = this.tool.measure().orElseThrow();
            assertEquals(heading, m.heading(), "aiming 60 blocks along " + heading);
            assertEquals(2, m.rise());
            End end = this.tool.params().orElseThrow().ends().get(1);
            End snapped = RoadPlanner.snapEnd(End.at(100, 70, -40, heading), m.along());
            assertEquals(End.at(snapped.x(), 72, snapped.z(), heading), end, "at the aimed block's height");
            assertTrue(RoadPlanner.plan(this.tool.params().orElseThrow(), WorldLimits.OVERWORLD).isPlanned(), heading.name());
        }
    }

    @Test
    void headingsRoundToTheNearestOfSixteen() {
        assertEquals(Heading.E, StraightTool.nearestHeading(10, 0));
        assertEquals(Heading.S, StraightTool.nearestHeading(0, 10));
        assertEquals(Heading.W, StraightTool.nearestHeading(-10, 0));
        assertEquals(Heading.N, StraightTool.nearestHeading(0, -10));
        assertEquals(Heading.E, StraightTool.nearestHeading(10, 1), "5.7° is nearer E than ESE");
        assertEquals(Heading.ESE, StraightTool.nearestHeading(10, 3), "16.7° is nearer ESE");
        assertEquals(Heading.ENE, StraightTool.nearestHeading(10, -3));
        assertEquals(Heading.SE, StraightTool.nearestHeading(7, 7));
    }

    @Test
    void aRefusedRoadCantBeFixedOrPlaced() {
        WorldLimits border = new WorldLimits(-64, 320, -100, 15, -100, 100);
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(20, 64, 0)));
        arrive(border);
        assertEquals(Optional.of(Notice.reason(Level.ERROR, RoadPlanner.WORLD_LIMIT, Map.of())), this.tool.hud().notice());
        assertEquals(Optional.empty(), this.tool.hud().hint(Action.USE), "nothing to fix");
        this.tool.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(ToolState.PLACING, this.tool.state());

        // Fixed before its answer came, then refused: the end follows the aim again.
        this.tool.input(new ToolInput.Aim(at(25, 64, 0)));
        this.tool.input(new ToolInput.Use(at(25, 64, 0)));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Aim(at(10, 64, 0)));
        arrive(border);
        assertEquals(ToolState.PLACING, this.tool.state());
        assertEquals(End.at(10, 64, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1));
    }

    @Test
    void anAnswerForAnOlderEndIsIgnored() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(20, 64, 0)));
        StraightTool.RoadJob old = (StraightTool.RoadJob) this.tool.output().job().orElseThrow();
        this.tool.input(new ToolInput.Aim(at(30, 64, 0)));
        this.tool.planned(old, old.run(WorldLimits.OVERWORLD));
        assertEquals(Optional.empty(), this.tool.planned());
        this.tool.input(new ToolInput.Use(at(30, 64, 0)));
        this.tool.input(new ToolInput.Use(at(30, 64, 0)));
        assertEquals(Optional.empty(), this.tool.takeCommit(), "a road can't be placed before its plan has arrived");
    }

    @Test
    void notSentRejectedAndFailedGoBackToStepThree() {
        readyToPlace();
        this.tool.input(new ToolInput.Use(Optional.empty()));
        this.tool.takeCommit();
        this.tool.answered(new Answer.NotSent(Protocol.EDITING_OFF, Map.of()));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        assertEquals(Optional.of(Notice.reason(Level.ERROR, Protocol.EDITING_OFF, Map.of())), this.tool.hud().notice());
        this.tool.input(new ToolInput.Aim(at(5, 64, 5)));
        assertEquals(Optional.of(Notice.reason(Level.ERROR, Protocol.EDITING_OFF, Map.of())), this.tool.hud().notice(), "aiming keeps it");
        this.tool.input(new ToolInput.Use(Optional.empty()));
        this.tool.takeCommit();
        assertEquals(Optional.empty(), this.tool.hud().notice(), "trying again clears it");

        this.tool.answered(new Answer.Sent(4));
        this.tool.answered(new Answer.Rejected(Protocol.BUSY, Map.of()));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Use(Optional.empty()));
        this.tool.answered(new Answer.Sent(5));
        this.tool.answered(new Answer.Accepted(357, 2));
        this.tool.answered(new Answer.Failed("BLOCK_ENTITY"));
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        assertTrue(this.tool.planned().orElseThrow().isPlanned(), "the road is still there to place again");
    }

    @Test
    void theHudSaysWhatToDoInEachStep() {
        HudModel idle = this.tool.hud();
        assertEquals("stratos.tool.roads.straight", idle.titleKey());
        assertEquals(1, idle.step());
        assertEquals(3, idle.steps());
        assertEquals(Text.of("stratos.tool.state.pick_start"), idle.state());
        assertEquals(List.of(
                new Line("stratos.tool.line.layout", Text.of("stratos.road.profile.city-17"), false),
                new Line("stratos.tool.line.palette", Text.of("stratos.palette.stratos_uk"), false),
                new Line("stratos.tool.line.length", HudModel.NONE, false),
                new Line("stratos.tool.line.heading", HudModel.NONE, false),
                new Line("stratos.tool.line.slope", HudModel.NONE, false),
                new Line("stratos.tool.line.shape", Text.of("stratos.tool.value.quadratic"), false),
                new Line("stratos.tool.line.nodes", Text.of("stratos.tool.value.nodes_wool"), false),
                new Line("stratos.tool.line.crossing", Text.of("stratos.tool.value.crossing_none"), false),
                new Line("stratos.tool.line.blocks", HudModel.NONE, false)), idle.lines(), "the same rows in every state, dashes for now");
        assertEquals(List.of(new Hint(Action.USE, "stratos.tool.hint.set_start"), new Hint(Action.TOOL_KEY, "stratos.tool.hint.put_away"),
                new Hint(Action.UNDO, "stratos.tool.hint.undo"), new Hint(Action.REDO, "stratos.tool.hint.redo"),
                new Hint(Action.SHAPE, "stratos.tool.hint.shape"), new Hint(Action.RADIUS_TYPE, "stratos.tool.hint.type_radius"),
                new Hint(Action.NODES, "stratos.tool.hint.nodes"), new Hint(Action.CROSSINGS, "stratos.tool.hint.crossing")), idle.hints(),
                "the radius steps only with Two radii");

        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        HudModel noEnd = this.tool.hud();
        assertEquals(2, noEnd.step());
        assertEquals(Text.of("stratos.tool.state.aim_end"), noEnd.state());
        assertEquals(Optional.empty(), noEnd.hint(Action.USE), "no end to set yet");
        assertEquals(Optional.of(new Hint(Action.ATTACK, "stratos.tool.hint.take_back_start")), noEnd.hint(Action.ATTACK));

        this.tool.input(new ToolInput.Aim(at(20, 64, 0)));
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.set_end")), this.tool.hud().hint(Action.USE));
        assertEquals(new Line("stratos.tool.line.blocks", HudModel.NONE, false), this.tool.hud().lines().get(8), "not worked out yet");
        assertEquals(Optional.of(new Hint(Action.RAISE, "stratos.tool.hint.height")), this.tool.hud().hint(Action.RAISE), "the end follows: height keys");
        arrive(WorldLimits.OVERWORLD);
        HudModel placing = this.tool.hud();
        assertEquals(new Line("stratos.tool.line.slope", Text.of("stratos.tool.value.level"), false), placing.lines().get(4));
        assertEquals(new Line("stratos.tool.line.blocks", Text.of("stratos.tool.value.count", "2,982"), false), placing.lines().get(8)); // B-1: the footway a layer up, one more block a column

        this.tool.input(new ToolInput.Use(at(20, 64, 0)));
        HudModel check = this.tool.hud();
        assertEquals(3, check.step());
        assertEquals(Text.of("stratos.tool.state.check_place"), check.state());
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.place")), check.hint(Action.USE));
        assertEquals(Optional.of(new Hint(Action.ATTACK, "stratos.tool.hint.take_back_end")), check.hint(Action.ATTACK));
        assertEquals(Optional.empty(), check.hint(Action.RAISE), "a fixed end doesn't follow: no height keys");

        this.tool.input(new ToolInput.Use(Optional.empty()));
        HudModel sending = this.tool.hud();
        assertEquals(3, sending.step());
        assertEquals(Text.of("stratos.tool.state.placing"), sending.state());
        assertEquals(List.of(new Hint(Action.TOOL_KEY, "stratos.tool.hint.put_away")), sending.hints(), "only the tool key does anything");
    }

    @Test
    void aWarningShowsInTheHud() {
        // The tool's own ends never warn, so this hands it an answer with one, as a later warning would arrive.
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(20, 64, 0)));
        StraightTool.RoadJob job = (StraightTool.RoadJob) this.tool.output().job().orElseThrow();
        Planned planned = job.run(WorldLimits.OVERWORLD);
        PlanResult.Warning warning = new PlanResult.Warning(RoadPlanner.STEP_22_5, Map.of("d", "0.054"));
        this.tool.planned(job, Planned.of(PlanResult.planned(planned.result().plan().orElseThrow(), List.of(warning))));
        assertEquals(Optional.of(Notice.reason(Level.WARNING, RoadPlanner.STEP_22_5, Map.of("d", "0.054"))), this.tool.hud().notice());
    }

    @Test
    void rightAfterFixingTheEndUsePlacesEvenOnItsHandle() {
        readyToPlace();
        this.tool.input(new ToolInput.Use(at(20, 64, 0)));
        assertEquals(ToolState.COMMITTING, this.tool.state(), "R-2's 'fix, place' still places the road");
    }

    @Test
    void theEndHandleIsPickedUpMovedAndPutDown() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(10, 64, 6)));
        this.tool.input(new ToolInput.Aim(at(21, 64, 0)));
        assertEquals(List.of(new Handle(0, 64, 0, Handle.Kind.START), new Handle(20, 64, 0, Handle.Kind.END, true)), this.tool.output().handles());
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.pick_up_end")), this.tool.hud().hint(Action.USE));
        this.tool.input(new ToolInput.Use(at(21, 64, 0)));
        assertEquals(Optional.of(Handle.Kind.END), this.tool.held());
        assertEquals(Text.of("stratos.tool.state.moving_end"), this.tool.hud().state());
        this.tool.input(new ToolInput.Aim(at(30, 64, 1)));
        RoadPlanParams moved = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(30, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID);
        assertEquals(Optional.of(moved), this.tool.params(), "a held end follows the aim as in step 2");
        arrive(WorldLimits.OVERWORLD);
        this.tool.input(new ToolInput.Use(at(30, 64, 1)));
        assertEquals(Optional.empty(), this.tool.held());
        assertEquals(ToolState.PREVIEWING, this.tool.state());
        this.tool.input(new ToolInput.Aim(at(5, 64, 9)));
        assertEquals(Optional.of(moved), this.tool.params());
    }

    @Test
    void aHeldStartTurnsTheRoadAndKeepsItsLength() {
        readyToPlace();
        this.tool.input(new ToolInput.Aim(at(10, 64, 6)));
        this.tool.input(new ToolInput.Aim(at(0, 64, 0)));
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        assertEquals(Optional.of(Handle.Kind.START), this.tool.held());
        this.tool.input(new ToolInput.Aim(at(-10, 64, 0)));
        assertEquals(Optional.of(RoadPlanParams.straight("city-17", End.at(-10, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID)), this.tool.params());
        this.tool.input(new ToolInput.Aim(at(20, 64, -20)));
        assertEquals(Optional.of(RoadPlanParams.straight("city-17", End.at(20, 64, -20, Heading.S), End.at(20, 64, 0, Heading.S)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID)), this.tool.params(),
                "the heading turns to the nearest of the 16 towards the end");
        this.tool.input(new ToolInput.Attack(at(20, 64, -20)));
        assertEquals(Optional.empty(), this.tool.held());
        assertEquals(Optional.of(RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E)).withNodeAids(RoadPlanParams.NodeAids.WOOL).withPalette(BuiltInPalettes.DEFAULT_ID)), this.tool.params(),
                "attack puts it back");
    }

    @Test
    void pageUpAndPageDownRaiseAndLowerTheEndThatFollows() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(80, 64, 0)));
        for (int i = 0; i < 3; i++) {
            this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.RAISE, at(80, 64, 0), 0F));
        }
        assertEquals(End.at(80, 67, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1));
        assertEquals(new Line("stratos.tool.line.slope", Text.of("stratos.tool.value.slope", "+3", "7.5", "8.3"), false), this.tool.hud().lines().get(4),
                "the steepest lane against City street's 1:12");
        this.tool.input(new ToolInput.Aim(at(60, 62, 0)));
        assertEquals(End.at(60, 65, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1), "the adjustment rides on the aimed height");
        this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.LOWER, Optional.empty(), 0F));
        assertEquals(End.at(60, 64, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1), "aiming at nothing: the end itself moves");
        this.tool.input(new ToolInput.Aim(at(80, 64, 0)));
        arrive(WorldLimits.OVERWORLD);
        this.tool.input(new ToolInput.Use(at(80, 64, 0)));
        this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.RAISE, at(80, 64, 0), 0F));
        assertEquals(66, this.tool.params().orElseThrow().ends().get(1).y(), "a fixed end doesn't move");
    }

    @Test
    void aPickedUpHandleKeepsItsHeight() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(80, 66, 0)));
        arrive(WorldLimits.OVERWORLD);
        this.tool.input(new ToolInput.Use(at(80, 66, 0)));
        this.tool.input(new ToolInput.Aim(at(40, 64, 9)));
        this.tool.input(new ToolInput.Aim(at(80, 65, 0)));
        this.tool.input(new ToolInput.Use(at(80, 65, 0)));
        assertEquals(Optional.of(Handle.Kind.END), this.tool.held());
        assertEquals(66, this.tool.params().orElseThrow().ends().get(1).y(), "picking up doesn't move it");
        this.tool.input(new ToolInput.Aim(at(70, 63, 0)));
        assertEquals(End.at(70, 64, 0, Heading.E), this.tool.params().orElseThrow().ends().get(1), "it keeps its height above the aimed block");
        this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.RAISE, at(70, 63, 0), 0F));
        assertEquals(65, this.tool.params().orElseThrow().ends().get(1).y());
    }

    @Test
    void aTooSteepRoadIsRefusedWithItsGradeInTheWarningColour() {
        this.tool.input(new ToolInput.Use(at(0, 64, 0)));
        this.tool.input(new ToolInput.Aim(at(40, 64, 0)));
        for (int i = 0; i < 5; i++) {
            this.tool.input(new ToolInput.Adjust(ToolInput.Adjustment.RAISE, at(40, 64, 0), 0F));
        }
        arrive(WorldLimits.OVERWORLD);
        PlanResult result = this.tool.planned().orElseThrow().result();
        assertEquals(Optional.of(PieceSolver.GRADE_TOO_STEEP), result.code());
        assertEquals(Map.of("grade", "25.0", "max", "8.3", "minLength", "120"), result.args());
        assertEquals(new Line("stratos.tool.line.slope", Text.of("stratos.tool.value.slope", "+5", "25.0", "8.3"), true), this.tool.hud().lines().get(4),
                "the readout says what the refusal says");
        this.tool.input(new ToolInput.Use(at(40, 64, 0)));
        assertEquals(ToolState.PLACING, this.tool.state(), "a refused road can't be fixed");
    }

    @Test
    void theShapeKeysChangeTheSharedSettings() {
        RoadSettings settings = new RoadSettings();
        StraightTool straight = new StraightTool(settings);
        CurveTool curve = new CurveTool(settings);
        straight.input(new ToolInput.Use(at(0, 64, 0)));
        straight.input(new ToolInput.Aim(at(80, 67, 0)));
        straight.input(new ToolInput.Adjust(ToolInput.Adjustment.RADIUS_UP));
        assertEquals(RoadSettings.DEFAULT_RADIUS, settings.radius(), "with Quadratic the radius keys do nothing");
        straight.input(new ToolInput.Adjust(ToolInput.Adjustment.SHAPE));
        straight.input(new ToolInput.Adjust(ToolInput.Adjustment.RADIUS_UP));
        assertEquals(new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII, java.util.OptionalDouble.of(110)),
                straight.params().orElseThrow().vertical());
        assertEquals(new Line("stratos.tool.line.shape", Text.of("stratos.tool.value.two_radii", "110"), false), straight.hud().lines().get(5));
        assertEquals(Optional.of(new Hint(Action.RADIUS_UP, "stratos.tool.hint.radius")), straight.hud().hint(Action.RADIUS_UP));
        curve.input(new ToolInput.Use(at(0, 64, 0), -90F));
        curve.input(new ToolInput.Aim(at(40, 66, 40), 0F));
        assertEquals(RoadPlanParams.Vertical.Shape.TWO_RADII, curve.params().orElseThrow().vertical().shape(), "the tools share the shape");
        settings.setRadius(5000);
        Line shape = straight.hud().lines().get(5);
        assertEquals("stratos.tool.value.two_radii_mtr", shape.value().key(), "MTR shortens 5000 on 80 blocks rising 3");
        assertEquals(List.of("5000", "534"), shape.value().args(), "(L² + H²) / 4H = 6409 / 12");
    }

    @Test
    void resetDropsEverything() {
        readyToPlace();
        this.tool.input(new ToolInput.Use(Optional.empty()));
        this.tool.reset();
        assertEquals(ToolState.IDLE, this.tool.state());
        assertEquals(Optional.empty(), this.tool.measure());
        assertEquals(Optional.empty(), this.tool.takeCommit());
        assertEquals(Optional.empty(), this.tool.planned());
        assertEquals(ToolOutput.EMPTY, this.tool.output());
    }
}
