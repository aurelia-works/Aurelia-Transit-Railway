/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.palette.BlockLookup;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.road.palette.PaletteFile;
import org.mtr.aurelia.road.road.palette.PaletteRole;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.tool.Answer;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Text;
import org.mtr.aurelia.road.tool.Planned;
import org.mtr.aurelia.road.tool.Question;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.WorldTask;

/** The Edit roads tool (task R-7; owner, 2026-10-09): pick a road, the box, the requests, the hand-edit question. */
class EditRoadsToolTest {
    private static final RoadPlanParams ROAD = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));
    private static final UUID ID = new UUID(5, 5);
    private static final Optional<Target> ON_THE_ROAD = Optional.of(new Target(20, 64, 0, 8));

    private final PlacedRoads roads = new PlacedRoads();
    private final EditRoadsTool tool = new EditRoadsTool(this.roads, inVanilla());

    EditRoadsToolTest() {
        BlockPlan plan = RoadPlanner.plan(ROAD, WorldLimits.OVERWORLD).plan().orElseThrow();
        this.roads.placed(ID, ROAD, plan);
    }

    /** Road settings with Vanilla chosen in the hub, the palette {@link #ROAD} is in, so the box offers no rebuild in another. */
    private static RoadSettings inVanilla() {
        RoadSettings settings = new RoadSettings();
        settings.setPalette(BuiltInPalettes.VANILLA_ID);
        return settings;
    }

    /** Works out the job the tool shows, as the session would, and hands the answer back; the commit it offers, if any. */
    private Optional<Commit<?>> plan() {
        var job = this.tool.output().job().orElseThrow();
        this.tool.planned(job, job.run(WorldLimits.OVERWORLD));
        return this.tool.takeCommit();
    }

    @Test
    void theBoxOffersARebuildInTheHubsPaletteWhenTheRoadIsInAnother() {
        // R-9 (owner, 2026-10-10): a palette change rebuilds the road.
        Palettes palettes = Palettes.of(List.of(BuiltInPalettes.vanilla(), bricks()), List.of());
        RoadSettings settings = new RoadSettings(() -> false, () -> false, () -> palettes);
        EditRoadsTool painter = new EditRoadsTool(this.roads, settings);
        painter.input(new ToolInput.Aim(ON_THE_ROAD));
        assertEquals(Text.of("stratos.palette.vanilla"), painter.hud().line("stratos.tool.line.palette").orElseThrow().value(), "the road's palette");
        painter.input(new ToolInput.Use(ON_THE_ROAD));
        assertEquals(List.of(EditRoadsTool.EDIT, EditRoadsTool.REBUILD, EditRoadsTool.DELETE, EditRoadsTool.CANCEL),
                painter.question().orElseThrow().choices().stream().map(Question.Choice::id).toList(), "the hub's palette is the road's");
        painter.input(new ToolInput.Choose(EditRoadsTool.CANCEL));

        assertTrue(settings.setPalette("bricks"));
        painter.input(new ToolInput.Use(ON_THE_ROAD));
        Question question = painter.question().orElseThrow();
        assertEquals(List.of(EditRoadsTool.EDIT, EditRoadsTool.REBUILD, EditRoadsTool.REBUILD_IN, EditRoadsTool.DELETE, EditRoadsTool.CANCEL),
                question.choices().stream().map(Question.Choice::id).toList());
        assertEquals(Text.of("stratos.edit.choice.rebuild_in", "Bricks"), question.choices().get(2).label(), "named by its file");
        painter.input(new ToolInput.Choose(EditRoadsTool.REBUILD_IN));
        RoadPlanParams sent = painter.editParams().orElseThrow();
        assertEquals(RoadPlanParams.Tool.REBUILD, sent.tool());
        assertEquals(ROAD.road().withPalette("bricks"), sent.road(), "the road with only its palette changed");
    }

    @Test
    void aRoadInStratosBlocksMayBeBakedToVanilla() {
        // B-3 (owner, 2026-10-15): the box offers it for a road whose palette places Stratos blocks; the server bakes it.
        RoadPlanParams uk = ROAD.withPalette(BuiltInPalettes.STRATOS_UK_ID);
        PlacedRoads ukRoads = new PlacedRoads();
        UUID id = new UUID(6, 6);
        ukRoads.placed(id, uk, RoadPlanner.plan(uk, WorldLimits.OVERWORLD).plan().orElseThrow());
        EditRoadsTool baker = new EditRoadsTool(ukRoads, inVanilla());
        baker.input(new ToolInput.Aim(ON_THE_ROAD));
        baker.input(new ToolInput.Use(ON_THE_ROAD));
        assertEquals(List.of(EditRoadsTool.EDIT, EditRoadsTool.REBUILD, EditRoadsTool.REBUILD_IN, EditRoadsTool.BAKE, EditRoadsTool.DELETE,
                EditRoadsTool.CANCEL), baker.question().orElseThrow().choices().stream().map(Question.Choice::id).toList());
        assertEquals(Text.of("stratos.edit.choice.bake"), baker.question().orElseThrow().choices().get(3).label());

        baker.input(new ToolInput.Choose(EditRoadsTool.BAKE));
        assertEquals(EditRoadsTool.Stage.SENDING, baker.stage());
        assertEquals("stratos.edit.state.baking", baker.hud().state().key());
        assertEquals(Optional.empty(), baker.takeCommit(), "a world task, not a plan");
        assertEquals(Optional.of(new WorldTask(Protocol.TASK_BAKE_ROAD, Optional.of(id))), baker.takeTask());
        assertEquals(Optional.empty(), baker.takeTask(), "taken once");
        baker.answered(new Answer.Sent(3));
        baker.answered(new Answer.Progress(1, 4));
        assertEquals(EditRoadsTool.Stage.SENDING, baker.stage());
        baker.answered(new Answer.TaskDone(Map.of("blocks", "120", "kept", "0")));
        assertEquals(EditRoadsTool.Stage.AIMING, baker.stage());
        assertEquals(Optional.of(Text.of("stratos.edit.notice.baked", "120")), baker.hud().notice().flatMap(HudModel.Notice::text));

        baker.input(new ToolInput.Use(ON_THE_ROAD));
        baker.input(new ToolInput.Choose(EditRoadsTool.BAKE));
        baker.takeTask();
        baker.answered(new Answer.Rejected("NOT_YOUR_ROAD", Map.of()));
        assertEquals(EditRoadsTool.Stage.AIMING, baker.stage());
        assertEquals("NOT_YOUR_ROAD", baker.hud().notice().flatMap(HudModel.Notice::reason).orElseThrow().code());
    }

    @Test
    void aRoadInTheVanillaPaletteHasNothingToBake() {
        this.tool.input(new ToolInput.Use(ON_THE_ROAD));
        assertTrue(this.tool.question().orElseThrow().choices().stream().noneMatch(choice -> choice.id().equals(EditRoadsTool.BAKE)));
        this.tool.input(new ToolInput.Choose(EditRoadsTool.BAKE));
        assertEquals(Optional.empty(), this.tool.takeTask());
    }

    /** A palette "bricks" named "Bricks", every role a full block. */
    private static Palette bricks() {
        Map<PaletteRole, List<Family>> roles = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            if (role.required()) {
                roles.put(role, List.of(Family.full("minecraft:bricks")));
            }
        }
        try {
            return Palette.resolve(new PaletteFile("bricks", Optional.of("Bricks"), 3465, roles), BlockLookup.VANILLA);
        } catch (PaletteException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aimingOutlinesTheRoadAndUseAsksWhatToDo() {
        assertEquals("stratos.edit.state.aim", this.tool.hud().state().key());
        this.tool.input(new ToolInput.Aim(ON_THE_ROAD));
        assertEquals(Optional.of(ID), this.tool.aimed().map(PlacedRoads.Road::operationId));
        assertEquals(new EditRoadsTool.ShowJob(ROAD), this.tool.output().job().orElseThrow());
        HudModel hud = this.tool.hud();
        assertEquals("stratos.edit.state.pick", hud.state().key());
        assertEquals("stratos.road.profile.city-17", hud.lines().get(0).value().key());
        assertEquals(List.of("40.0"), hud.lines().get(2).value().args());
        assertEquals("stratos.edit.value.joined_none", hud.lines().get(4).value().key());

        this.tool.input(new ToolInput.Use(ON_THE_ROAD));
        assertEquals(EditRoadsTool.Stage.CHOOSING, this.tool.stage());
        Question question = this.tool.question().orElseThrow();
        assertEquals(List.of(EditRoadsTool.EDIT, EditRoadsTool.REBUILD, EditRoadsTool.DELETE, EditRoadsTool.CANCEL), question.choices().stream().map(Question.Choice::id)
                .toList());
        assertEquals(EditRoadsTool.CANCEL, question.wayOut().id());

        this.tool.input(new ToolInput.Choose(EditRoadsTool.CANCEL));
        assertEquals(EditRoadsTool.Stage.AIMING, this.tool.stage());
        assertEquals(Optional.empty(), this.tool.question());
    }

    @Test
    void rebuildAsksAboutHandEditsThenSendsTheAnswer() {
        this.tool.input(new ToolInput.Use(ON_THE_ROAD));
        this.tool.input(new ToolInput.Choose(EditRoadsTool.REBUILD));
        assertEquals(EditRoadsTool.Stage.SENDING, this.tool.stage());
        assertInstanceOf(EditRoadsTool.EditJob.class, this.tool.output().job().orElseThrow());
        Commit<?> commit = plan().orElseThrow();
        assertEquals(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, ID, ROAD, Optional.empty()), commit.params());
        assertEquals(RoadPlanner.plan(ROAD, WorldLimits.OVERWORLD).plan().orElseThrow(), commit.plan());

        this.tool.answered(new Answer.Sent(1));
        this.tool.answered(new Answer.Rejected(Protocol.MANUAL_EDITS, Map.of("count", "12")));
        assertEquals(EditRoadsTool.Stage.ASKING, this.tool.stage());
        assertEquals(12, this.tool.handEdits());
        Question question = this.tool.question().orElseThrow();
        assertEquals(List.of("12"), question.body().args());
        assertEquals(List.of(EditRoadsTool.OVERWRITE, EditRoadsTool.KEEP, EditRoadsTool.CANCEL), question.choices().stream()
                .map(Question.Choice::id).toList());

        this.tool.input(new ToolInput.Choose(EditRoadsTool.KEEP));
        Commit<?> again = plan().orElseThrow();
        assertEquals(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, ID, ROAD, Optional.of(RoadPlanParams.ManualEdits.KEEP)), again.params());
        this.tool.answered(new Answer.Accepted(10, 1));
        this.tool.answered(new Answer.Done());
        assertEquals(EditRoadsTool.Stage.AIMING, this.tool.stage());
        assertEquals("stratos.edit.notice.rebuilt", this.tool.hud().notice().orElseThrow().text().orElseThrow().key());
    }

    @Test
    void deleteIsDrawnInRedAndNothingToChangeIsNews() {
        this.tool.input(new ToolInput.Use(ON_THE_ROAD));
        this.tool.input(new ToolInput.Choose(EditRoadsTool.DELETE));
        var job = this.tool.output().job().orElseThrow();
        Planned planned = job.run(WorldLimits.OVERWORLD);
        assertTrue(planned.removal(), "the road being deleted is drawn in red");
        this.tool.planned(job, planned);
        Commit<?> commit = this.tool.takeCommit().orElseThrow();
        assertEquals(RoadPlanParams.Tool.DELETE, ((RoadPlanParams) commit.params()).tool());
        assertEquals(List.of(), commit.sideEffects());

        this.tool.answered(new Answer.Rejected(Protocol.NOTHING_TO_CHANGE, Map.of()));
        assertEquals(EditRoadsTool.Stage.AIMING, this.tool.stage());
        HudModel.Notice notice = this.tool.hud().notice().orElseThrow();
        assertEquals(HudModel.Level.INFO, notice.level());
        assertEquals("stratos.edit.notice.nothing_to_delete", notice.text().orElseThrow().key());
    }

    /** Minecraft's yaw facing east. */
    private static final float EAST = -90F;

    private static Optional<Target> at(int x, int y, int z) {
        return Optional.of(new Target(x, y, z, 12.5));
    }

    /** Works out {@code tool}'s job as the session would and hands the answer back. */
    private static void arrive(EditRoadsTool tool) {
        var job = tool.output().job().orElseThrow();
        tool.planned(job, job.run(WorldLimits.OVERWORLD));
    }

    @Test
    void editShowsTheRoadAsPlacedAndTheHubFollowsIt() {
        // R-10 (owner, 2026-10-13): the hub's settings become the road's.
        RoadSettings settings = new RoadSettings();
        settings.setProfile(RoadProfiles.byId("residential-15").orElseThrow());
        settings.toggleShape();
        EditRoadsTool editing = new EditRoadsTool(this.roads, settings);
        editing.input(new ToolInput.Use(ON_THE_ROAD));
        editing.input(new ToolInput.Choose(EditRoadsTool.EDIT));
        assertEquals(EditRoadsTool.Stage.EDITING, editing.stage());
        assertEquals("city-17", settings.profile().id());
        assertEquals(RoadPlanParams.Vertical.QUADRATIC, settings.vertical());
        CurveTool editor = editing.editor().orElseThrow();
        assertEquals(Optional.of(ROAD.road()), editor.params().map(RoadPlanParams::road), "unchanged, the road is as placed");
        assertEquals(List.of(new Handle(0, 64, 0, Handle.Kind.START), new Handle(40, 64, 0, Handle.Kind.END)), editing.output().handles());
        HudModel hud = editing.hud();
        assertEquals("stratos.tool.roads.edit", hud.titleKey());
        assertEquals(Text.of("stratos.edit.state.editing"), hud.state());
        assertEquals(Optional.of(new HudModel.Hint(HudModel.Action.ATTACK, "stratos.edit.hint.leave")), hud.hint(HudModel.Action.ATTACK));

        editing.input(new ToolInput.Attack(ON_THE_ROAD));
        assertEquals(EditRoadsTool.Stage.AIMING, editing.stage(), "attack leaves the road as it was");
        assertEquals("stratos.edit.notice.unchanged", editing.hud().notice().orElseThrow().text().orElseThrow().key());
        assertEquals(Optional.empty(), editing.takeCommit());
    }

    @Test
    void anUnchangedRoadComesOutOfTheEditorAsPlaced() {
        // Each end faces its own heading, and MTR's connector turns it back into the same road (straights at every heading, and a bend).
        List<RoadPlanParams> roads = new java.util.ArrayList<>();
        for (Heading heading : Heading.values()) {
            End start = End.at(0, 64, 0, heading);
            roads.add(RoadPlanParams.straight("city-17", start, RoadPlanner.snapEnd(start, 30)));
        }
        roads.add(RoadPlanParams.curve("residential-15", End.at(0, 64, 0, Heading.E), End.at(40, 70, 40, Heading.S)));
        roads.add(RoadPlanParams.curve("alley-7", End.at(0, 64, 0, Heading.ENE), End.at(30, 64, -20, Heading.NE)));
        for (RoadPlanParams road : roads) {
            RoadSettings settings = new RoadSettings();
            settings.follow(road);
            CurveTool editor = new CurveTool(settings);
            editor.load(road);
            assertEquals(Optional.of(road.road()), editor.params().map(RoadPlanParams::road), road.toString());
        }
    }

    @Test
    void anEditMovesAnEndTakesTheHubsLayoutAndIsSentAsARebuild() {
        RoadSettings settings = new RoadSettings();
        EditRoadsTool editing = new EditRoadsTool(this.roads, settings);
        editing.input(new ToolInput.Use(ON_THE_ROAD));
        editing.input(new ToolInput.Choose(EditRoadsTool.EDIT));
        editing.input(new ToolInput.Aim(ON_THE_ROAD, EAST)); // off the handles: picking is armed
        editing.input(new ToolInput.Aim(at(39, 64, 1), EAST));
        editing.input(new ToolInput.Use(at(39, 64, 1), EAST));
        assertEquals(Optional.of(Handle.Kind.END), editing.editor().orElseThrow().held());
        assertEquals(Text.of("stratos.tool.state.moving_end"), editing.hud().state());
        editing.input(new ToolInput.Aim(at(50, 64, 0), EAST));
        arrive(editing);
        editing.input(new ToolInput.Use(at(50, 64, 0), EAST)); // put down
        settings.setProfile(RoadProfiles.byId("residential-15").orElseThrow()); // the hub's Layout list, while editing
        editing.input(new ToolInput.Aim(ON_THE_ROAD, EAST));
        arrive(editing);
        editing.input(new ToolInput.Use(ON_THE_ROAD, EAST));
        assertEquals(EditRoadsTool.Stage.SENDING, editing.stage());
        assertEquals(Text.of("stratos.edit.state.sending_edit"), editing.hud().state());

        var job = editing.output().job().orElseThrow();
        assertInstanceOf(EditRoadsTool.EditJob.class, job);
        editing.planned(job, job.run(WorldLimits.OVERWORLD));
        Commit<?> commit = editing.takeCommit().orElseThrow();
        RoadPlanParams sent = (RoadPlanParams) commit.params();
        RoadPlanParams road = RoadPlanParams.curve("residential-15", End.at(0, 64, 0, Heading.E), End.at(50, 64, 0, Heading.E));
        assertEquals(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, ID, road, Optional.empty()), sent);
        assertEquals(RoadPlanner.plan(road, WorldLimits.OVERWORLD).plan().orElseThrow(), commit.plan(), "the edited road's blocks");

        editing.answered(new Answer.Sent(1));
        editing.answered(new Answer.Rejected(Protocol.MANUAL_EDITS, Map.of("count", "3")));
        assertEquals(EditRoadsTool.Stage.ASKING, editing.stage());
        assertEquals("stratos.edit.question.edits_edit", editing.question().orElseThrow().body().key());
        editing.input(new ToolInput.Choose(EditRoadsTool.OVERWRITE));
        arrive(editing);
        assertEquals(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, ID, road, Optional.of(RoadPlanParams.ManualEdits.OVERWRITE)),
                editing.takeCommit().orElseThrow().params());
        editing.answered(new Answer.Done());
        assertEquals(EditRoadsTool.Stage.AIMING, editing.stage());
        assertEquals("stratos.edit.notice.edited", editing.hud().notice().orElseThrow().text().orElseThrow().key());
    }

    @Test
    void aServerRefusalOfAnEditGoesBackToEditing() {
        EditRoadsTool editing = new EditRoadsTool(this.roads, new RoadSettings());
        editing.input(new ToolInput.Use(ON_THE_ROAD));
        editing.input(new ToolInput.Choose(EditRoadsTool.EDIT));
        editing.input(new ToolInput.Aim(ON_THE_ROAD, EAST));
        arrive(editing);
        editing.input(new ToolInput.Use(ON_THE_ROAD, EAST));
        arrive(editing);
        editing.takeCommit().orElseThrow();
        editing.answered(new Answer.Rejected("VOLUME_LIMIT", Map.of()));
        assertEquals(EditRoadsTool.Stage.EDITING, editing.stage(), "the road is shown again, to change or leave");
        assertEquals("VOLUME_LIMIT", editing.hud().notice().orElseThrow().reason().orElseThrow().code());
        editing.input(new ToolInput.Aim(ON_THE_ROAD, EAST));
        arrive(editing);
        editing.input(new ToolInput.Use(ON_THE_ROAD, EAST));
        assertEquals(EditRoadsTool.Stage.SENDING, editing.stage(), "and can be sent again");
    }

    @Test
    void attackOrCancelLetsGoAndAGoneRoadIsRefusedByThePlanner() {
        this.tool.input(new ToolInput.Use(ON_THE_ROAD));
        this.tool.input(new ToolInput.Attack(ON_THE_ROAD));
        assertEquals(EditRoadsTool.Stage.AIMING, this.tool.stage());

        this.tool.input(new ToolInput.Use(ON_THE_ROAD));
        this.roads.undone(ID); // undone while the box asked
        this.tool.input(new ToolInput.Choose(EditRoadsTool.REBUILD));
        assertEquals(Optional.empty(), plan(), "nothing sent");
        assertEquals(EditRoadsTool.Stage.AIMING, this.tool.stage());
        assertEquals("ROAD_GONE", this.tool.hud().notice().orElseThrow().reason().orElseThrow().code());
    }
}
