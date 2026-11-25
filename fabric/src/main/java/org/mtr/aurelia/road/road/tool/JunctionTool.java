/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.junction.JunctionSnap;
import org.mtr.aurelia.road.road.junction.JunctionTemplate;
import org.mtr.aurelia.road.road.junction.JunctionTemplates;
import org.mtr.aurelia.road.road.junction.PlacedJunction;
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
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.ToolOutput;
import org.mtr.aurelia.road.tool.ToolState;

/**
 * The Junction tool (G-11; roads-v1 §11): places a T-junction, crossroads or roundabout, in the road layout and palette
 * the road tools use. One step: the junction follows the aim, and use places it.
 * <ul>
 *   <li><b>Snapping:</b> aimed within {@link JunctionSnap#RADIUS} of a free road end at its height, one arm takes that end
 *       ({@link JunctionSnap#snap}), and any other arm landing on a free end facing it takes that one too. Anywhere else
 *       it stands on the aimed block, Page Up / Page Down raising it.</li>
 *   <li><b>Attack</b> turns it: on a road end, the next arm takes the end; elsewhere a quarter turn.</li>
 *   <li><b>The shape key</b> picks the next kind, <b>the radius keys</b> its size: corner radius for T-junctions and
 *       crossroads, the ring's radius for roundabouts (the R7 mini roundabout included).</li>
 *   <li>A road is drawn onto a free arm with the straight or curved road tool ({@link JoinableRoads}).</li>
 * </ul>
 */
public final class JunctionTool implements Tool {
    public static final String ID = "roads.junction";
    /** The kinds, in the order the shape key steps through them. */
    public static final List<String> KINDS = List.of(JunctionTemplates.T_JUNCTION, JunctionTemplates.CROSSROADS, JunctionTemplates.ROUNDABOUT);

    private final RoadSettings settings;
    private final JoinableRoads joins;
    private int kind;
    private final int[] sizes = new int[KINDS.size()];
    private int turn;
    private int raise;
    private Optional<Target> aim = Optional.empty();
    private RoadPlanParams plannedFor;
    private Planned planned;
    private Commit<RoadPlanParams> commit;
    private boolean committing;
    private Notice notSent;

    /** A tool sharing {@code settings} with the other road tools, snapping onto {@code joins}' road ends. */
    public JunctionTool(RoadSettings settings, JoinableRoads joins) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.joins = Objects.requireNonNull(joins, "joins");
        this.sizes[0] = 1; // CORNER_RADII 10
        this.sizes[1] = 1;
        this.sizes[2] = 1; // RING_RADII 13
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
        return "stratos.tool.roads.junction";
    }

    @Override
    public Set<Action> keys() {
        return EnumSet.of(Action.USE, Action.ATTACK, Action.TOOL_KEY, Action.UNDO, Action.REDO, Action.NODES, Action.RAISE, Action.LOWER, Action.SHAPE,
                Action.RADIUS_UP, Action.RADIUS_DOWN);
    }

    @Override
    public ToolState state() {
        return this.committing ? ToolState.COMMITTING : this.aim.isPresent() ? ToolState.PREVIEWING : ToolState.IDLE;
    }

    /** The kind of junction chosen. */
    public String kind() {
        return KINDS.get(this.kind);
    }

    /** Its size: corner radius, or the ring's radius for a roundabout. */
    public int size() {
        return sizes(this.kind).get(this.sizes[this.kind]);
    }

    private static List<Integer> sizes(int kind) {
        return KINDS.get(kind).equals(JunctionTemplates.ROUNDABOUT) ? JunctionTemplates.RING_RADII : JunctionTemplates.CORNER_RADII;
    }

    /** The template chosen, in the road tools' layout on every arm; empty when that layout has no one lane each way. */
    private Optional<JunctionTemplate> template() {
        String id = kind();
        return JunctionTemplates.of(id, size(), Collections.nCopies(JunctionTemplates.armCount(id), this.settings.profile().id()),
                this.settings.drivingSide());
    }

    /** Where the junction goes, as a request: snapped onto a road end near the aim, or on the aimed block. */
    public Optional<RoadPlanParams> params() {
        Optional<JunctionTemplate> template = template();
        if (this.aim.isEmpty() || template.isEmpty()) {
            return Optional.empty();
        }
        Target at = this.aim.get();
        int arms = template.get().arms().size();
        JunctionSnap.Result snap = JunctionSnap.snap(template.get(), this.joins.graph(), at.x(), at.y(), at.z(), StrictMath.floorMod(this.turn, arms));
        RoadPlanParams params;
        if (snap instanceof JunctionSnap.Placed placed) {
            params = placed.params();
        } else if (snap instanceof JunctionSnap.Refused refused && !refused.code().equals(JunctionSnap.NO_ROAD_END)) {
            return Optional.empty(); // on a road end it can't take: the HUD says why
        } else {
            params = new PlacedJunction(template.get(), at.x(), at.y() + this.raise, at.z(), StrictMath.floorMod(this.turn, 4))
                    .params(Collections.nCopies(arms, Optional.empty()));
        }
        return Optional.of(params.withPalette(this.settings.palette().id()).withNodeAids(this.settings.nodeAids()));
    }

    /** The planner's answer for {@link #params()}, once it has arrived. */
    public Optional<Planned> planned() {
        Optional<RoadPlanParams> params = params();
        return params.isPresent() && params.get().equals(this.plannedFor) ? Optional.of(this.planned) : Optional.empty();
    }

    @Override
    public void input(ToolInput input) {
        if (input instanceof ToolInput.Aim aimInput) {
            this.aim = aimInput.target();
            return;
        }
        if (this.committing) {
            return; // the junction is with the server
        }
        this.notSent = null;
        this.aim = input.target().isPresent() ? input.target() : this.aim;
        if (input instanceof ToolInput.Adjust adjust) {
            switch (adjust.adjustment()) {
                case SHAPE -> this.kind = (this.kind + 1) % KINDS.size();
                case RADIUS_UP -> this.sizes[this.kind] = StrictMath.min(this.sizes[this.kind] + 1, sizes(this.kind).size() - 1);
                case RADIUS_DOWN -> this.sizes[this.kind] = StrictMath.max(this.sizes[this.kind] - 1, 0);
                case RAISE -> this.raise++;
                case LOWER -> this.raise--;
                case NODES -> this.settings.cycleNodeAids();
            }
        } else if (input instanceof ToolInput.Attack) {
            this.turn++;
        } else if (input instanceof ToolInput.Use) {
            Optional<Planned> current = planned();
            if (current.isPresent() && current.get().isPlanned()) {
                PlanResult result = current.get().result();
                this.commit = new Commit<>(RoadsModule.INSTANCE, params().orElseThrow(), result.plan().orElseThrow(), result.sideEffects());
                this.committing = true;
            }
        }
    }

    @Override
    public ToolOutput output() {
        return new ToolOutput(params().map(params -> new StraightTool.RoadJob(params, this.joins.placed())), List.of());
    }

    @Override
    public void planned(PlanJob job, Planned answer) {
        if (job instanceof StraightTool.RoadJob road) {
            this.plannedFor = road.params();
            this.planned = answer;
        }
    }

    @Override
    public Optional<Commit<?>> takeCommit() {
        Optional<Commit<?>> taken = Optional.ofNullable(this.commit);
        this.commit = null;
        return taken;
    }

    @Override
    public void answered(Answer answer) {
        if (answer instanceof Answer.NotSent notSentAnswer) {
            this.committing = false;
            this.notSent = Notice.reason(Level.ERROR, notSentAnswer.code(), notSentAnswer.args());
        } else if (answer instanceof Answer.Rejected || answer instanceof Answer.Failed) {
            this.committing = false; // the server's message is in the HUD
        } else if (answer instanceof Answer.Done) {
            this.committing = false;
            this.raise = 0;
            this.plannedFor = null; // placed: the next one is planned afresh, against the junction just placed
            this.planned = null;
        }
    }

    @Override
    public void reset() {
        this.aim = Optional.empty();
        this.turn = 0;
        this.raise = 0;
        this.plannedFor = null;
        this.planned = null;
        this.commit = null;
        this.committing = false;
        this.notSent = null;
    }

    @Override
    public HudModel hud() {
        ToolState state = state();
        Optional<Planned> current = planned();
        boolean placeable = current.map(Planned::isPlanned).orElse(false);
        Text blocks = placeable
                ? Text.of("stratos.tool.value.count", String.format(Locale.ROOT, "%,d", current.get().result().plan().orElseThrow().blockCount()))
                : HudModel.NONE;
        List<Line> lines = List.of(
                new Line("stratos.tool.line.junction", Text.of("stratos.junction." + kind()), false),
                new Line("stratos.tool.line.size", Text.of("stratos.tool.value.blocks", Integer.toString(size())), false),
                new Line("stratos.tool.line.layout", Text.of("stratos.road.profile." + this.settings.profile().id()), false),
                new Line("stratos.tool.line.palette", RoadSettings.name(this.settings.palette()), false),
                ElevationHud.nodes(this.settings),
                new Line("stratos.tool.line.blocks", blocks, false));
        String stateKey = switch (state) {
            case COMMITTING -> "stratos.tool.state.placing";
            case PREVIEWING -> "stratos.tool.state.check_place";
            default -> "stratos.tool.state.aim_junction";
        };
        return new HudModel(titleKey(), 1, 1, Text.of(stateKey), lines, notice(current), hints(state, placeable));
    }

    /** Why the commit wasn't sent; else why the planner refused it (a layout without one lane each way: no template). */
    private Optional<Notice> notice(Optional<Planned> current) {
        if (this.notSent == null && this.aim.isPresent()) {
            Optional<JunctionTemplate> template = template();
            if (template.isEmpty()) {
                return Optional.of(Notice.reason(Level.ERROR, RoadPlanner.UNKNOWN_JUNCTION, Map.of()));
            }
            Target at = this.aim.get();
            JunctionSnap.Result snap = JunctionSnap.snap(template.get(), this.joins.graph(), at.x(), at.y(), at.z(),
                    StrictMath.floorMod(this.turn, template.get().arms().size()));
            if (snap instanceof JunctionSnap.Refused refused && !refused.code().equals(JunctionSnap.NO_ROAD_END)) {
                return Optional.of(Notice.reason(Level.ERROR, refused.code(), Map.of()));
            }
        }
        return RoadNotices.notice(this.notSent, current, Optional.empty(), this.joins);
    }

    private List<Hint> hints(ToolState state, boolean placeable) {
        List<Hint> hints = new ArrayList<>();
        if (state != ToolState.COMMITTING) {
            if (placeable) {
                hints.add(new Hint(Action.USE, "stratos.tool.hint.place_junction"));
            }
            hints.add(new Hint(Action.ATTACK, "stratos.tool.hint.turn_junction"));
            hints.add(new Hint(Action.RAISE, "stratos.tool.hint.height"));
            hints.add(new Hint(Action.LOWER, "stratos.tool.hint.height"));
            hints.add(new Hint(Action.SHAPE, "stratos.tool.hint.junction_kind"));
            hints.add(new Hint(Action.RADIUS_UP, "stratos.tool.hint.junction_size"));
            hints.add(new Hint(Action.RADIUS_DOWN, "stratos.tool.hint.junction_size"));
            hints.add(new Hint(Action.NODES, "stratos.tool.hint.nodes"));
        }
        hints.add(new Hint(Action.TOOL_KEY, "stratos.tool.hint.put_away"));
        if (state != ToolState.COMMITTING) {
            hints.add(new Hint(Action.UNDO, "stratos.tool.hint.undo"));
            hints.add(new Hint(Action.REDO, "stratos.tool.hint.redo"));
        }
        return hints;
    }
}
