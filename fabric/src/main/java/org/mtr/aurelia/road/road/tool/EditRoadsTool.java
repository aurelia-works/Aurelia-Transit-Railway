/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.bake.BakeMap;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.tool.Answer;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Action;
import org.mtr.aurelia.road.tool.HudModel.Hint;
import org.mtr.aurelia.road.tool.HudModel.Level;
import org.mtr.aurelia.road.tool.HudModel.Line;
import org.mtr.aurelia.road.tool.HudModel.Notice;
import org.mtr.aurelia.road.tool.HudModel.Text;
import org.mtr.aurelia.road.tool.PlanJob;
import org.mtr.aurelia.road.tool.Planned;
import org.mtr.aurelia.road.tool.Question;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.ToolOutput;
import org.mtr.aurelia.road.tool.ToolState;
import org.mtr.aurelia.road.tool.WorldTask;

/**
 * The Edit roads tool (task R-7; owner, 2026-10-09), the fourth road tool: pick one of your placed roads, then rebuild or
 * delete it.
 * <ul>
 *   <li><b>Picking:</b> the road whose node or blocks the player aims at, among the node aids' roads ({@link PlacedRoads}:
 *       yours you can still undo or redo, and this visit's). It is drawn over itself in its own colours, outlined, and the
 *       HUD names it.</li>
 *   <li><b>Use</b> on it asks, in a box ({@link #question}): Rebuild, Delete or Cancel.</li>
 *   <li><b>Rebuild</b> sends a {@code REBUILD} request: the road planned again as placed (today's Stratos), track
 *       included. <b>Delete</b> sends a {@code DELETE} request; its preview is the road in red. Both name the road by its
 *       id and carry its parameters as they stand.</li>
 *   <li><b>Hand edits:</b> when the server answers {@code MANUAL_EDITS {count}}, nothing has changed yet and the box asks:
 *       Overwrite them, Keep them, or Cancel. The answer goes in a new request ({@code manualEdits}).</li>
 *   <li>{@code NOTHING_TO_CHANGE} says so in the strip ("Nothing to rebuild: the road is as Stratos built it."). {@code -}
 *       undoes a rebuild or delete like any edit.</li>
 *   <li><b>Attack</b> takes the step back: it lets go of a picked road.</li>
 *   <li><b>R-9:</b> the HUD names the road's palette, and when the hub's palette ({@link RoadSettings#palette}) is another,
 *       the box also offers "Rebuild in <that palette>": a {@code REBUILD} whose parameters are the road's with that
 *       palette (owner, 2026-10-10: a palette change rebuilds the road, roads-v1 §8).</li>
 *   <li><b>R-10 (owner, 2026-10-13): Edit</b>, first in the box, shows the road with its two end handles, as the curve tool's
 *       step 3 ({@link CurveTool#load}): an end is picked up, moved (it may join another road's free end, as when
 *       drawing) and put down as there; the hub's layout, palette, node aid and shape become the road's and change it while
 *       it is edited (they stay for the next road). <b>Use</b> sends a {@code REBUILD} with the new parameters, asking
 *       about hand edits as a rebuild does; <b>attack</b> leaves the road as it was. <b>R-10+:</b> a joined road is
 *       edited too: its joined ends stay where they are (their handles can't be picked up), and the roads joined to it
 *       keep their joins; an edit that would break one is refused ({@code ROAD_JOINED}).</li>
 *   <li><b>B-3: Bake to vanilla</b>, offered for a road whose palette places Stratos blocks, asks the server to turn the
 *       road's Stratos blocks into vanilla ones where they stand ({@code BAKE_ROAD}, a world task): one operation, undone
 *       like any edit.</li>
 * </ul>
 */
public final class EditRoadsTool implements Tool {
    public static final String ID = "roads.edit";

    /** The box's choices, as {@link ToolInput.Choose} carries them back. */
    public static final String REBUILD = "rebuild";
    /** R-9: rebuild in the hub's palette. */
    public static final String REBUILD_IN = "rebuild_in";
    /** R-10: edit the road's ends, layout, palette, node aid or shape. */
    public static final String EDIT = "edit";
    public static final String DELETE = "delete";
    /** B-3: bake the road's Stratos blocks to vanilla. */
    public static final String BAKE = "bake";
    public static final String OVERWRITE = "overwrite";
    public static final String KEEP = "keep";
    public static final String CANCEL = "cancel";

    /** Where the tool is. */
    public enum Stage {
        /** Aiming: the road aimed at is outlined; use picks it. */
        AIMING,
        /** The box asks what to do with the picked road. */
        CHOOSING,
        /** R-10: the picked road is shown with its end handles, its settings the hub's, until use sends it or attack leaves it. */
        EDITING,
        /** Rebuild, delete or (B-3) bake was chosen: the request's plan is being worked out, then sent, then placed. */
        SENDING,
        /** The server found blocks changed by hand: the box asks whether to overwrite or keep them. */
        ASKING
    }

    /** The plan of a road as it stands, to draw it while it is aimed at or picked. */
    public record ShowJob(RoadPlanParams road) implements PlanJob {
        public ShowJob {
            Objects.requireNonNull(road, "road");
        }

        @Override
        public Planned run(WorldLimits limits) {
            return Planned.of(RoadPlanner.asPlaced(this.road, limits));
        }
    }

    /** The plan of a rebuild or delete, checked against {@code placed} (the standing roads by road id), as the server will. */
    public record EditJob(RoadPlanParams params, Map<UUID, RoadPlanParams> placed) implements PlanJob {
        public EditJob {
            Objects.requireNonNull(params, "params");
            placed = Map.copyOf(placed);
        }

        @Override
        public Planned run(WorldLimits limits) {
            PlanResult result = RoadPlanner.plan(this.params, limits, this.placed);
            return this.params.tool() == RoadPlanParams.Tool.DELETE ? Planned.removal(result) : Planned.of(result);
        }
    }

    private final PlacedRoads roads;
    private final RoadSettings settings;
    private Optional<Target> aim = Optional.empty();
    private Stage stage = Stage.AIMING;
    private PlacedRoads.Road picked;
    private RoadPlanParams.Tool action;
    private String newPalette; // R-9: the palette a rebuild puts the road in, when it isn't the road's
    private Optional<RoadPlanParams.ManualEdits> answer = Optional.empty();
    private int handEdits;
    private EditJob editJob;
    /** R-10: the curve tool showing the road being edited, and the road it was sent as (null unless editing). */
    private CurveTool editor;
    private RoadPlanParams edited;
    private Commit<RoadPlanParams> commit;
    /** B-3: a bake of the picked road, until the session sends it; {@link #baking} until it ends. */
    private WorldTask task;
    private boolean baking;
    private boolean sent;
    private Notice notice;

    public EditRoadsTool(PlacedRoads roads) {
        this(roads, new RoadSettings());
    }

    /** The tool for {@code roads}, offering a rebuild in {@code settings}' palette (R-9). */
    public EditRoadsTool(PlacedRoads roads, RoadSettings settings) {
        this.roads = Objects.requireNonNull(roads, "roads");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String moduleId() {
        return "roads";
    }

    @Override
    public String titleKey() {
        return "stratos.tool.roads.edit";
    }

    @Override
    public Set<Action> keys() {
        // R-10: editing a road takes the curve tool's keys (heights, shape, radius, node aid).
        return this.stage == Stage.EDITING ? EnumSet.allOf(Action.class) : EnumSet.of(Action.USE, Action.ATTACK, Action.TOOL_KEY, Action.UNDO, Action.REDO);
    }

    @Override
    public ToolState state() {
        return switch (this.stage) {
            case AIMING -> ToolState.IDLE;
            case CHOOSING, EDITING, ASKING -> ToolState.PREVIEWING;
            case SENDING -> ToolState.COMMITTING;
        };
    }

    public Stage stage() {
        return this.stage;
    }

    /** The road picked with use, while the box asks about it or its edit is being sent. */
    public Optional<PlacedRoads.Road> picked() {
        return Optional.ofNullable(this.picked);
    }

    /** The road the player aims at now: its node, else its blocks. */
    public Optional<PlacedRoads.Road> aimed() {
        if (this.aim.isEmpty()) {
            return Optional.empty();
        }
        Target target = this.aim.get();
        Optional<PlacedRoads.Aimed> node = this.roads.nodeAt(target.x(), target.y(), target.z());
        if (node.isPresent()) {
            return Optional.of(node.get().road());
        }
        return this.roads.containing(target.x(), target.y(), target.z());
    }

    /** The road the HUD and the preview show: the picked one, else the one aimed at. */
    public Optional<PlacedRoads.Road> shown() {
        return this.picked != null ? Optional.of(this.picked) : aimed();
    }

    /** The request a rebuild or delete of the picked road sends now, once chosen. */
    public Optional<RoadPlanParams> editParams() {
        if (this.picked == null || this.action == null) {
            return Optional.empty();
        }
        RoadPlanParams road = this.edited != null ? this.edited
                : this.newPalette != null ? this.picked.params().withPalette(this.newPalette) : this.picked.params();
        return Optional.of(RoadPlanParams.edit(this.action, this.picked.operationId(), road, this.answer));
    }

    /** The road being edited, as the curve tool shows it (R-10). */
    public Optional<CurveTool> editor() {
        return Optional.ofNullable(this.editor);
    }

    @Override
    public void input(ToolInput input) {
        if (!(input instanceof ToolInput.Choose)) {
            this.aim = input.target(); // the box's picks carry no aim
        }
        if (this.stage == Stage.EDITING) {
            edit(input);
            return;
        }
        if (input instanceof ToolInput.Use) {
            Optional<PlacedRoads.Road> road = aimed();
            if (this.stage == Stage.AIMING && road.isPresent()) {
                this.picked = road.get();
                this.stage = Stage.CHOOSING;
                this.notice = null;
            }
        } else if (input instanceof ToolInput.Attack) {
            if (this.stage == Stage.CHOOSING || this.stage == Stage.ASKING) {
                letGo(null);
            }
        } else if (input instanceof ToolInput.Choose choose) {
            choose(choose.choice());
        }
    }

    /** R-10: the editing stage's input: the curve tool's, but attack with no handle held leaves the road as it was. */
    private void edit(ToolInput input) {
        if (input instanceof ToolInput.Choose) {
            return;
        }
        if (input instanceof ToolInput.Attack && this.editor.held().isEmpty()) {
            letGo(Notice.text(Level.INFO, Text.of("stratos.edit.notice.unchanged")));
            return;
        }
        if (!(input instanceof ToolInput.Aim)) {
            this.notice = null;
        }
        this.editor.input(input);
        Optional<Commit<?>> placing = this.editor.takeCommit();
        if (placing.isPresent()) {
            this.edited = (RoadPlanParams) placing.get().params();
            this.action = RoadPlanParams.Tool.REBUILD;
            this.answer = Optional.empty();
            send();
        }
    }

    /** R-10: starts editing the picked road (R-10+: its joined ends stay put); the hub's settings become the road's. */
    private void startEditing() {
        UUID id = this.picked.operationId();
        this.settings.follow(this.picked.params());
        this.editor = new CurveTool(this.settings, JoinableRoads.editing(id, this.roads::roads));
        this.editor.load(this.picked.params());
        this.stage = Stage.EDITING;
        this.notice = null;
    }

    private void choose(String choice) {
        if (this.stage == Stage.CHOOSING) {
            if (choice.equals(EDIT)) {
                startEditing();
            } else if (choice.equals(BAKE) && bakeable(this.picked)) {
                this.stage = Stage.SENDING;
                this.baking = true;
                this.notice = null;
                this.task = new WorldTask(Protocol.TASK_BAKE_ROAD, Optional.of(this.picked.operationId()));
            } else if (choice.equals(REBUILD) || choice.equals(DELETE) || choice.equals(REBUILD_IN) && otherPalette().isPresent()) {
                this.action = choice.equals(DELETE) ? RoadPlanParams.Tool.DELETE : RoadPlanParams.Tool.REBUILD;
                this.newPalette = choice.equals(REBUILD_IN) ? otherPalette().orElseThrow() : null;
                this.answer = Optional.empty();
                send();
            } else {
                letGo(null);
            }
        } else if (this.stage == Stage.ASKING) {
            if (choice.equals(OVERWRITE) || choice.equals(KEEP)) {
                this.answer = Optional.of(choice.equals(OVERWRITE) ? RoadPlanParams.ManualEdits.OVERWRITE : RoadPlanParams.ManualEdits.KEEP);
                send();
            } else {
                letGo(Notice.text(Level.INFO, Text.of("stratos.edit.notice.unchanged")));
            }
        }
    }

    /** Works the request's plan out ({@link #output}); it is sent once it arrives ({@link #planned}). */
    private void send() {
        this.stage = Stage.SENDING;
        this.sent = false;
        this.commit = null;
        this.notice = null;
        this.editJob = new EditJob(editParams().orElseThrow(), placed());
    }

    /** Back to aiming, with {@code notice} in the strip (or none). */
    private void letGo(Notice notice) {
        this.stage = Stage.AIMING;
        this.picked = null;
        this.action = null;
        this.newPalette = null;
        this.answer = Optional.empty();
        this.editJob = null;
        this.editor = null;
        this.edited = null;
        this.commit = null;
        this.task = null;
        this.baking = false;
        this.sent = false;
        this.notice = notice;
    }

    /** The standing roads by road id, as the planner checks a rebuild or delete against them. */
    private Map<UUID, RoadPlanParams> placed() {
        Map<UUID, RoadPlanParams> placed = new LinkedHashMap<>();
        for (PlacedRoads.Road road : this.roads.roads()) {
            placed.putIfAbsent(road.operationId(), road.params());
        }
        return placed;
    }

    @Override
    public ToolOutput output() {
        if (this.stage == Stage.SENDING && this.editJob != null) {
            return new ToolOutput(Optional.of(this.editJob), List.of());
        }
        if (this.editor != null) {
            return this.editor.output(); // R-10: the road being edited
        }
        return new ToolOutput(shown().map(road -> new ShowJob(road.params())), List.of());
    }

    @Override
    public void planned(PlanJob job, Planned answer) {
        if (this.stage == Stage.EDITING) {
            this.editor.planned(job, answer);
            return;
        }
        if (this.stage != Stage.SENDING || !job.equals(this.editJob) || this.sent) {
            return;
        }
        if (!answer.isPlanned()) {
            PlanResult result = answer.result();
            refused(result.code().orElseThrow(), result.args());
            return;
        }
        PlanResult result = answer.result();
        this.commit = new Commit<>(RoadsModule.INSTANCE, this.editJob.params(), result.plan().orElseThrow(), result.sideEffects());
        this.sent = true;
    }

    @Override
    public Optional<WorldTask> takeTask() {
        Optional<WorldTask> taken = Optional.ofNullable(this.task);
        this.task = null;
        return taken;
    }

    @Override
    public Optional<Commit<?>> takeCommit() {
        Optional<Commit<?>> taken = Optional.ofNullable(this.commit);
        this.commit = null;
        return taken;
    }

    /** A refusal of the request: for an edit, back to editing the road (R-10); else back to aiming. */
    private void refused(String code, Map<String, String> args) {
        Notice reason = Notice.reason(Level.ERROR, code, args);
        if (this.edited == null) {
            letGo(reason);
            return;
        }
        this.editor.answered(new Answer.Rejected(code, args)); // the curve tool shows the road again, ready to change
        this.stage = Stage.EDITING;
        this.action = null;
        this.edited = null;
        this.answer = Optional.empty();
        this.editJob = null;
        this.commit = null;
        this.sent = false;
        this.notice = reason;
    }

    @Override
    public void answered(Answer answer) {
        if (answer instanceof Answer.NotSent notSent) {
            refused(notSent.code(), notSent.args());
        } else if (answer instanceof Answer.TaskDone done) {
            String blocks = done.args().getOrDefault("blocks", "0");
            letGo(Notice.text(Level.INFO, blocks.equals("0") ? Text.of("stratos.edit.notice.nothing_to_bake")
                    : Text.of("stratos.edit.notice.baked", blocks)));
        } else if (answer instanceof Answer.Rejected rejected) {
            if (rejected.code().equals(Protocol.MANUAL_EDITS) && this.picked != null) {
                this.handEdits = parseCount(rejected.args().get("count"));
                this.stage = Stage.ASKING;
                this.editJob = null;
                this.sent = false;
            } else if (rejected.code().equals(Protocol.NOTHING_TO_CHANGE)) {
                String track = rejected.args().get("track");
                letGo(Notice.text(Level.INFO, track != null ? Text.of("stratos.edit.notice.track_repaired", track)
                        : Text.of(this.action == RoadPlanParams.Tool.DELETE ? "stratos.edit.notice.nothing_to_delete"
                                : this.edited != null ? "stratos.edit.notice.nothing_to_edit" : "stratos.edit.notice.nothing_to_rebuild")));
            } else {
                refused(rejected.code(), rejected.args());
            }
        } else if (answer instanceof Answer.Done) {
            letGo(Notice.text(Level.INFO, Text.of(this.action == RoadPlanParams.Tool.DELETE ? "stratos.edit.notice.deleted"
                    : this.edited != null ? "stratos.edit.notice.edited" : "stratos.edit.notice.rebuilt")));
        } else if (answer instanceof Answer.Failed failed) {
            letGo(Notice.reason(Level.ERROR, failed.code(), Map.of()));
        }
    }

    private static int parseCount(String text) {
        try {
            return text == null ? 0 : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The hub's palette, when the picked road is in another one: what "Rebuild in …" puts it in (R-9). */
    private Optional<String> otherPalette() {
        if (this.picked == null) {
            return Optional.empty();
        }
        String chosen = this.settings.palette().id();
        return chosen.equals(this.picked.params().paletteId()) ? Optional.empty() : Optional.of(chosen);
    }

    /**
     * Whether {@code road}'s palette, as this world has it, places any Stratos block (B-3: a road it doesn't has nothing to
     * bake).
     */
    private boolean bakeable(PlacedRoads.Road road) {
        Optional<Palette> palette = this.settings.palettes().byId(road.params().paletteId());
        if (palette.isEmpty()) {
            return false;
        }
        for (Family family : palette.get().families().values()) {
            if (BakeMap.isStratos(family.full()) || family.half().filter(BakeMap::isStratos).isPresent()
                    || family.layers().filter(BakeMap::isStratos).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** How many blocks the server found changed by hand, while the box asks about them. */
    public int handEdits() {
        return this.handEdits;
    }

    @Override
    public Optional<Question> question() {
        if (this.picked == null) {
            return Optional.empty();
        }
        Text title = Text.of("stratos.road.profile." + this.picked.params().profileId());
        if (this.stage == Stage.CHOOSING) {
            List<Question.Choice> choices = new ArrayList<>();
            choices.add(new Question.Choice(EDIT, "stratos.edit.choice.edit"));
            choices.add(new Question.Choice(REBUILD, "stratos.edit.choice.rebuild"));
            // A built-in palette is named in the lang file ("Rebuild in Vanilla"); any other by its file's name, else its id.
            otherPalette().ifPresent(id -> choices.add(new Question.Choice(REBUILD_IN, BuiltInPalettes.isBuiltIn(id)
                    ? Text.of("stratos.edit.choice.rebuild_in." + id)
                    : Text.of("stratos.edit.choice.rebuild_in", this.settings.palettes().byId(id).flatMap(p -> p.name()).orElse(id)))));
            if (bakeable(this.picked)) {
                choices.add(new Question.Choice(BAKE, "stratos.edit.choice.bake"));
            }
            choices.add(new Question.Choice(DELETE, "stratos.edit.choice.delete"));
            choices.add(new Question.Choice(CANCEL, "stratos.edit.choice.cancel"));
            String body = bakeable(this.picked) ? "stratos.edit.question.what_bake" : "stratos.edit.question.what";
            return Optional.of(new Question(title, Text.of(body, length(this.picked)), choices));
        }
        if (this.stage == Stage.ASKING) {
            String body = this.action == RoadPlanParams.Tool.DELETE ? "stratos.edit.question.edits_delete"
                    : this.edited != null ? "stratos.edit.question.edits_edit" : "stratos.edit.question.edits_rebuild";
            return Optional.of(new Question(title, Text.of(body, Integer.toString(this.handEdits)), List.of(
                    new Question.Choice(OVERWRITE, "stratos.edit.choice.overwrite"), new Question.Choice(KEEP, "stratos.edit.choice.keep"),
                    new Question.Choice(CANCEL, "stratos.edit.choice.cancel"))));
        }
        return Optional.empty();
    }

    @Override
    public void reset() {
        this.aim = Optional.empty();
        letGo(null);
        this.handEdits = 0;
    }

    @Override
    public HudModel hud() {
        if (this.stage == Stage.EDITING) {
            return editingHud();
        }
        Optional<PlacedRoads.Road> road = shown();
        Text state = switch (this.stage) {
            case AIMING -> Text.of(road.isPresent() ? "stratos.edit.state.pick" : this.roads.roads().isEmpty() ? "stratos.edit.state.no_roads"
                    : "stratos.edit.state.aim");
            case CHOOSING, ASKING -> Text.of("stratos.edit.state.choose");
            case SENDING -> Text.of(this.baking ? "stratos.edit.state.baking"
                    : this.action == RoadPlanParams.Tool.DELETE ? "stratos.edit.state.deleting"
                    : this.edited != null ? "stratos.edit.state.sending_edit" : "stratos.edit.state.rebuilding");
            case EDITING -> throw new IllegalStateException("editingHud draws it");
        };
        List<Line> lines = new ArrayList<>();
        if (road.isPresent()) {
            RoadPlanParams params = road.get().params();
            lines.add(new Line("stratos.tool.line.layout", Text.of("stratos.road.profile." + params.profileId()), false));
            lines.add(new Line("stratos.tool.line.palette", RoadSettings.name(params.paletteId(), this.settings.palettes()), false));
            lines.add(new Line("stratos.tool.line.length", Text.of("stratos.tool.value.blocks", length(road.get())), false));
            lines.add(new Line("stratos.tool.line.nodes", Text.of(nodes(params.nodeAids())), false));
            lines.add(new Line("stratos.edit.line.joined", Text.of(joined(road.get())), false));
        } else {
            lines.add(new Line("stratos.tool.line.layout", HudModel.NONE, false));
            lines.add(new Line("stratos.tool.line.palette", HudModel.NONE, false));
            lines.add(new Line("stratos.tool.line.length", HudModel.NONE, false));
            lines.add(new Line("stratos.tool.line.nodes", HudModel.NONE, false));
            lines.add(new Line("stratos.edit.line.joined", HudModel.NONE, false));
        }
        List<Hint> hints = new ArrayList<>();
        if (this.stage == Stage.AIMING && road.isPresent()) {
            hints.add(new Hint(Action.USE, "stratos.tool.hint.edit_road"));
        }
        if (this.stage == Stage.CHOOSING || this.stage == Stage.ASKING) {
            hints.add(new Hint(Action.ATTACK, "stratos.tool.hint.let_go"));
        }
        hints.add(new Hint(Action.TOOL_KEY, "stratos.tool.hint.put_away"));
        if (this.stage != Stage.SENDING) {
            hints.add(new Hint(Action.UNDO, "stratos.tool.hint.undo"));
            hints.add(new Hint(Action.REDO, "stratos.tool.hint.redo"));
        }
        return new HudModel(titleKey(), 0, 0, state, lines, Optional.ofNullable(this.notice), hints);
    }

    /**
     * R-10: the curve tool's rows and keys for the road being edited (so the panel is at most the curve tool's size),
     * under the Edit roads title, with use and attack named for the edit.
     */
    private HudModel editingHud() {
        HudModel curve = this.editor.hud();
        List<Hint> hints = new ArrayList<>();
        for (Hint hint : curve.hints()) {
            String key = switch (hint.textKey()) {
                case "stratos.tool.hint.place" -> "stratos.edit.hint.confirm";
                case "stratos.tool.hint.take_back_end" -> "stratos.edit.hint.leave";
                default -> hint.textKey();
            };
            hints.add(new Hint(hint.action(), key));
        }
        Text state = this.editor.held().isPresent() ? curve.state() : Text.of("stratos.edit.state.editing");
        Optional<Notice> notice = this.notice != null ? Optional.of(this.notice) : curve.notice();
        return new HudModel(titleKey(), 0, 0, state, curve.lines(), notice, hints);
    }

    private static String length(PlacedRoads.Road road) {
        return String.format(Locale.ROOT, "%.1f", road.length());
    }

    private static String nodes(RoadPlanParams.NodeAids aids) {
        return switch (aids) {
            case TRACK -> "stratos.tool.value.nodes_track";
            case WOOL -> "stratos.tool.value.nodes_wool";
            case PREORIENTED -> "stratos.tool.value.nodes_mtr";
            case NONE -> "stratos.tool.value.nodes_none";
        };
    }

    /** Which of the road's ends another standing road joins, or it joins (R-5): the HUD's words for it. */
    private String joined(PlacedRoads.Road road) {
        RoadGraph graph = RoadGraph.of(placed());
        boolean start = graph.taken(road.operationId(), 0);
        boolean end = graph.taken(road.operationId(), 1);
        return start && end ? "stratos.edit.value.joined_both" : start ? "stratos.edit.value.joined_start"
                : end ? "stratos.edit.value.joined_end" : "stratos.edit.value.joined_none";
    }
}
