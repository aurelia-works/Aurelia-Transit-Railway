/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.Position;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.tool.Answer;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HandlePicker;
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
 * The curved road tool (task R-3): one MTR piece (line + arc, arc + line or an S-bend) between two ends set the way MTR
 * nodes are placed (owner, 2026-10-06). Three steps:
 * <ol>
 *   <li><b>Use</b> sets the start at the block aimed at, facing the way the player faces.</li>
 *   <li>The end follows the aim, and its facing the player's. <b>Use</b> fixes it, unless the road is known to be
 *       refused.</li>
 *   <li><b>Use</b> places the road; then the tool waits for the server and starts again from step 1 once it is placed.
 *       Aiming at a handle, <b>use</b> picks it up instead: it follows the aim and the facing until <b>use</b> puts it
 *       down, or <b>attack</b> puts it back.</li>
 * </ol>
 * <b>Attack</b> otherwise takes the last step back: the end, then the start. Aiming at nothing keeps the last end.
 *
 * <p><b>Facings, like MTR nodes:</b> a facing is the player's, snapped as MTR snaps a node's ({@link MtrRails#facing}).
 * An end has no front or back: the ported {@code Rail.getAngles} turns each facing towards the other end, as MTR's
 * connector does, so facing either way along the road gives the same road.
 *
 * <p><b>Heights</b> (owner, 2026-10-06: R-4). An end that follows the aim is at the aimed block's height plus the Page
 * Up / Page Down adjustment; a picked-up handle keeps its height. The shape (Quadratic or Two radii) and its radius come
 * from the shared {@link RoadSettings}, as does the layout (G-4+).
 */
public final class CurveTool implements Tool {
    public static final String ID = "roads.curve";

    /**
     * An end as the player set it: its block and the way the player faced; or (R-5) a road end it joins, whose block,
     * height and heading it takes (the facing is then that end's outgoing heading, which a node can't tell from its
     * opposite).
     */
    public record Point(int x, int y, int z, Heading facing, Optional<JoinableRoads.Join> join) {
        public Point {
            Objects.requireNonNull(facing, "facing");
            Objects.requireNonNull(join, "join");
        }

        /** An end that joins nothing. */
        public Point(int x, int y, int z, Heading facing) {
            this(x, y, z, facing, Optional.empty());
        }

        static Point of(Target target, float yaw) {
            return of(target, yaw, 0);
        }

        /** At the aimed block, {@code raise} blocks up. */
        static Point of(Target target, float yaw, int raise) {
            return new Point(target.x(), target.y() + raise, target.z(), MtrRails.facing(yaw));
        }

        /** At the road end {@code join}. */
        static Point at(JoinableRoads.Join join) {
            RoadJoins.Port port = join.port();
            return new Point(port.x(), port.y(), port.z(), port.outgoing(), Optional.of(join));
        }
    }

    private final RoadSettings settings;
    private final JoinableRoads joins;
    /** Page Up / Page Down's adjustment to whatever follows the aim (the end in step 2, or a held handle), in blocks. */
    private int raise;
    private Point start;
    /** The end, while there is one; it follows the aim until fixed. */
    private Point end;
    private boolean endFixed;
    /** The handle being moved, and where it was (to put it back); null while none is held. */
    private Handle.Kind held;
    private Point heldFrom;
    /** A handle put down before the planner's answer came: picked up again if the answer is a refusal. */
    private Handle.Kind dropped;
    private final HandlePicker picker = new HandlePicker();
    private Optional<Target> aim = Optional.empty();
    private float yaw;
    private RoadPlanParams plannedFor;
    private Planned planned;
    private Commit<RoadPlanParams> commit;
    private boolean committing;
    private Notice notSent;
    /** R-10: the tool shows a placed road being edited ({@link #load}): its ends stay set whatever the planner says. */
    private boolean editing;
    /** R-10+: the handles of the edited road's joined ends, which can't be picked up. */
    private final Set<Handle.Kind> pinned = EnumSet.noneOf(Handle.Kind.class);

    /** A tool with its own settings (tests). */
    public CurveTool() {
        this(new RoadSettings());
    }

    /** A tool sharing {@code settings} with the other road tools, joining no roads. */
    public CurveTool(RoadSettings settings) {
        this(settings, JoinableRoads.NONE);
    }

    /** A tool sharing {@code settings} with the other road tools, whose ends may join {@code joins}' roads (R-5). */
    public CurveTool(RoadSettings settings, JoinableRoads joins) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.joins = Objects.requireNonNull(joins, "joins");
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
        return "stratos.tool.roads.curve";
    }

    @Override
    public ToolState state() {
        if (this.start == null) {
            return ToolState.IDLE;
        }
        if (this.committing) {
            return ToolState.COMMITTING;
        }
        return this.endFixed && this.held == null ? ToolState.PREVIEWING : ToolState.PLACING;
    }

    public Optional<Point> start() {
        return Optional.ofNullable(this.start);
    }

    public Optional<Point> end() {
        return Optional.ofNullable(this.end);
    }

    /** The handle being moved, if any. */
    public Optional<Handle.Kind> held() {
        return Optional.ofNullable(this.held);
    }

    /**
     * The road as it stands, as the planner gets it (R-5: stored the way round that lets its dashes carry on, with the
     * dash pattern its joins call for), or empty without an end (or with the end on the start's block).
     */
    public Optional<RoadPlanParams> params() {
        return drawn().map(this.joins::finish);
    }

    /** The road from the start the player set to their end. */
    private Optional<RoadPlanParams> drawn() {
        return this.start == null || this.end == null ? Optional.empty()
                : road(this.settings.profile(), this.start, this.end, this.settings.vertical(), this.settings.nodeAids())
                        .map(params -> params.withPalette(this.settings.palette().id()));
    }

    /** {@code point}, or the road end it joins (R-5). */
    private Point joined(Point point) {
        return this.joins.near(point.x(), point.y(), point.z()).map(Point::at).orElse(point);
    }

    /** As {@link #road(RoadProfile, Point, Point, RoadPlanParams.Vertical)}, Quadratic. */
    public static Optional<RoadPlanParams> road(RoadProfile profile, Point a, Point b) {
        return road(profile, a, b, RoadPlanParams.Vertical.QUADRATIC);
    }

    /**
     * The road between two ends set like MTR nodes: each facing turned towards the other end by MTR's connector rule
     * ({@code Rail.getAngles}), each end at its own height (R-4). Empty when both ends are on the same block.
     */
    public static Optional<RoadPlanParams> road(RoadProfile profile, Point a, Point b, RoadPlanParams.Vertical vertical) {
        return road(profile, a, b, vertical, RoadPlanParams.NodeAids.NONE);
    }

    /** As {@link #road(RoadProfile, Point, Point, RoadPlanParams.Vertical)}, with the node aid {@code nodeAids} (R-6). */
    public static Optional<RoadPlanParams> road(RoadProfile profile, Point a, Point b, RoadPlanParams.Vertical vertical,
            RoadPlanParams.NodeAids nodeAids) {
        int dx = b.x() - a.x();
        int dz = b.z() - a.z();
        if (dx == 0 && dz == 0) {
            return Optional.empty();
        }
        var angles = Rail.getAngles(new Position(0, 0, 0), (float) a.facing().degrees(), new Position(dx, 0, dz), (float) b.facing().degrees());
        Heading from = MtrRails.heading(angles.left());
        Heading to = MtrRails.heading(angles.right()).opposite(); // MTR's end angle points back towards the start
        RoadPlanParams.End start = RoadPlanParams.End.at(a.x(), a.y(), a.z(), from);
        RoadPlanParams.End end = RoadPlanParams.End.at(b.x(), b.y(), b.z(), to);
        // R-5: a joined end names the road end it joins; the planner checks the road runs on from it.
        return Optional.of(RoadPlanParams.curve(profile.id(), a.join().map(j -> JoinableRoads.joined(start, j)).orElse(start),
                b.join().map(j -> JoinableRoads.joined(end, j)).orElse(end), vertical, nodeAids));
    }

    /**
     * Starts at step 3 with the placed road {@code road}'s ends (R-10: the Edit roads tool's edit): its handles can be
     * picked up and moved, and the layout, palette, node aid and shape are the shared settings'. Each end faces the way
     * its heading points, so an unchanged road comes out as placed.
     *
     * <p><b>R-10+</b>: a joined end ({@link JoinableRoads#pinned}: drawn onto another road, or another road drawn onto it)
     * keeps its join and stays where it is: its handle can't be picked up.
     */
    public void load(RoadPlanParams road) {
        reset();
        RoadPlanParams.End from = road.ends().get(0);
        RoadPlanParams.End to = road.ends().get(road.ends().size() - 1);
        this.start = this.joins.joinOf(road, 0).map(Point::at).orElse(new Point(from.x(), from.y(), from.z(), from.heading()));
        this.end = this.joins.joinOf(road, 1).map(Point::at).orElse(new Point(to.x(), to.y(), to.z(), to.heading()));
        if (this.joins.pinned(road, 0)) {
            this.pinned.add(Handle.Kind.START);
        }
        if (this.joins.pinned(road, 1)) {
            this.pinned.add(Handle.Kind.END);
        }
        this.endFixed = true;
        this.editing = true;
    }

    /** The planner's answer for the road as it stands, once it has arrived. */
    public Optional<Planned> planned() {
        Optional<RoadPlanParams> params = params();
        return params.isPresent() && params.get().equals(this.plannedFor) ? Optional.of(this.planned) : Optional.empty();
    }

    @Override
    public void input(ToolInput input) {
        this.yaw = input.yaw();
        if (input instanceof ToolInput.Aim aimInput) {
            this.aim = aimInput.target();
            follow();
            if (state() == ToolState.PREVIEWING) {
                this.picker.aim(this.aim, movable());
            }
            return;
        }
        if (this.committing) {
            return; // the road is with the server
        }
        this.notSent = null;
        if (input instanceof ToolInput.Adjust adjust) {
            adjust(adjust);
        } else if (input instanceof ToolInput.Use use) {
            this.aim = use.target();
            use();
        } else if (input instanceof ToolInput.Attack) {
            attack();
        }
    }

    private void use() {
        if (this.held != null) {
            if (fixable()) {
                this.dropped = planned().isPresent() ? null : this.held;
                this.held = null;
                this.picker.disarm();
            }
            return;
        }
        switch (state()) {
            case IDLE -> this.aim.ifPresent(target -> {
                this.start = joined(Point.of(target, this.yaw));
                this.raise = 0;
                follow();
            });
            case PLACING -> {
                // A road known to be refused can't be fixed; one still being worked out is fixed now and let go if refused.
                if (fixable()) {
                    this.endFixed = true;
                    this.picker.disarm();
                }
            }
            case PREVIEWING -> {
                Optional<Handle> aimed = aimedHandle();
                if (aimed.isPresent()) {
                    this.held = aimed.get().kind();
                    this.heldFrom = this.held == Handle.Kind.START ? this.start : this.end;
                    this.dropped = null;
                    // Picking up never moves the handle: the adjustment starts as its height above the aimed block.
                    this.raise = this.heldFrom.y() - this.aim.orElseThrow().y();
                    follow();
                    return;
                }
                Optional<Planned> current = planned();
                if (current.isPresent() && current.get().isPlanned()) {
                    PlanResult result = current.get().result();
                    this.commit = new Commit<>(RoadsModule.INSTANCE, params().orElseThrow(), result.plan().orElseThrow(), result.sideEffects());
                    this.committing = true;
                }
            }
            default -> {
            }
        }
    }

    private void attack() {
        if (this.held != null) {
            putBack();
            return;
        }
        switch (state()) {
            case PREVIEWING -> {
                this.endFixed = false;
                this.dropped = null;
                this.raise = this.aim.map(a -> this.end.y() - a.y()).orElse(0); // the end keeps its height
                follow();
            }
            case PLACING -> reset();
            default -> {
            }
        }
    }

    /** Page Up / Page Down (the end that follows, or a held handle), or a shape or radius key (R-4). */
    private void adjust(ToolInput.Adjust adjust) {
        if (ElevationHud.applySetting(adjust.adjustment(), this.settings)) {
            return;
        }
        if (!following()) {
            return;
        }
        int step = adjust.adjustment() == ToolInput.Adjustment.RAISE ? 1 : -1;
        this.raise += step;
        if (this.aim.isPresent()) {
            follow();
            return;
        }
        // Aiming at nothing: move what follows by the step itself.
        if (this.held == Handle.Kind.START) {
            this.start = new Point(this.start.x(), this.start.y() + step, this.start.z(), this.start.facing());
        } else if (this.end != null) {
            this.end = new Point(this.end.x(), this.end.y() + step, this.end.z(), this.end.facing());
        }
    }

    /** Whether something follows the aim now: the end in step 2, or a held handle. */
    private boolean following() {
        return this.start != null && !this.committing && (this.held != null || !this.endFixed);
    }

    private void putBack() {
        if (this.held == Handle.Kind.START) {
            this.start = this.heldFrom;
        } else {
            this.end = this.heldFrom;
        }
        this.held = null;
        this.heldFrom = null;
    }

    /** Whether the road can be fixed as it is: it exists, and isn't known to be refused (one being worked out can). */
    private boolean fixable() {
        return params().isPresent() && planned().map(Planned::isPlanned).orElse(true);
    }

    /** Moves whatever follows the aim (the end in step 2, or a held handle) to the aimed block and the player's facing. */
    private void follow() {
        if (this.start == null || this.committing || this.aim.isEmpty()) {
            return;
        }
        Point point = joined(Point.of(this.aim.get(), this.yaw, this.raise));
        if (this.held == Handle.Kind.START) {
            this.start = point;
        } else if (this.held == Handle.Kind.END || !this.endFixed) {
            this.end = point;
        }
    }

    /** The handle the player could pick up now, in step 3. */
    private Optional<Handle> aimedHandle() {
        return state() == ToolState.PREVIEWING ? this.picker.pickable(this.aim, movable()) : Optional.empty();
    }

    /** The handles that can be picked up: both, but for a joined end of a road being edited (R-10+). */
    private List<Handle> movable() {
        return handles(null).stream().filter(handle -> !this.pinned.contains(handle.kind())).toList();
    }

    /** The two handles, with {@code active} lit (or none); the end handle is at the road's height. */
    private List<Handle> handles(Handle.Kind active) {
        List<Handle> handles = new ArrayList<>();
        handles.add(new Handle(this.start.x(), this.start.y(), this.start.z(), Handle.Kind.START, active == Handle.Kind.START));
        if (this.end != null) {
            handles.add(new Handle(this.end.x(), this.start.y(), this.end.z(), Handle.Kind.END, active == Handle.Kind.END));
        }
        return handles;
    }

    @Override
    public ToolOutput output() {
        if (this.start == null) {
            return ToolOutput.EMPTY;
        }
        Handle.Kind active = this.held != null ? this.held : aimedHandle().map(Handle::kind).orElse(null);
        List<Handle> handles = handles(active);
        Optional<PlanJob> job = params().map(p -> StraightTool.RoadJob.of(p, this.joins));
        return new ToolOutput(job, handles);
    }

    @Override
    public void planned(PlanJob job, Planned answer) {
        if (job instanceof StraightTool.RoadJob road && road.params().equals(params().orElse(null))) {
            this.plannedFor = road.params();
            this.planned = answer;
            if (answer.isPlanned() || this.committing) {
                this.dropped = null;
                return;
            }
            if (this.dropped != null) {
                this.held = this.dropped; // put down before the answer came, and refused: picked up again
                this.dropped = null;
                follow();
            } else if (this.endFixed && this.held == null && !this.editing) {
                this.endFixed = false; // fixed before the answer came, and refused: the end follows the aim again
                follow();
            }
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
            this.committing = false; // back to step 3, the road still shown; the server's message is in the HUD
        } else if (answer instanceof Answer.Done) {
            reset(); // placed: ready for the next road
        }
    }

    @Override
    public void reset() {
        this.start = null;
        this.end = null;
        this.raise = 0;
        this.endFixed = false;
        this.held = null;
        this.heldFrom = null;
        this.dropped = null;
        this.aim = Optional.empty();
        this.plannedFor = null;
        this.planned = null;
        this.commit = null;
        this.committing = false;
        this.notSent = null;
        this.editing = false;
        this.pinned.clear();
    }

    /**
     * Three steps: pick the start, aim at the end, check and place (or move a handle). The same eight values in every
     * state (the HUD keeps its size), with a dash where there is nothing to show yet.
     */
    @Override
    public HudModel hud() {
        ToolState state = state();
        Optional<RoadPlanParams> params = drawn(); // in the player's order (R-5: the planner's may be turned round)
        Optional<RoadPlanParams> planning = params();
        Optional<Planned> current = planned();
        boolean placeable = current.map(Planned::isPlanned).orElse(false);
        Text length = HudModel.NONE;
        Text headings = HudModel.NONE;
        Text radius = HudModel.NONE;
        Text blocks = HudModel.NONE;
        if (params.isPresent()) {
            RoadPlanParams.End from = params.get().ends().get(0);
            RoadPlanParams.End to = params.get().ends().get(1);
            headings = Text.of("stratos.tool.value.headings", from.heading().name(), to.heading().name());
            Optional<RailMath> centre = PieceSolver.centreLine(from, to);
            if (centre.isPresent()) {
                length = Text.of("stratos.tool.value.blocks", String.format(Locale.ROOT, "%.1f", centre.get().getLength()));
                double r = PieceSolver.tightestArc(centre.get());
                radius = Double.isInfinite(r) ? Text.of("stratos.tool.value.straight")
                        : Text.of("stratos.tool.value.blocks", String.format(Locale.ROOT, "%.1f", r));
            }
        } else if (this.start != null) {
            headings = Text.of("stratos.tool.value.facing", this.start.facing().name());
        }
        if (placeable) {
            blocks = Text.of("stratos.tool.value.count", String.format(Locale.ROOT, "%,d", current.get().result().plan().orElseThrow().blockCount()));
        }
        List<Line> lines = List.of(
                new Line("stratos.tool.line.layout", Text.of("stratos.road.profile." + this.settings.profile().id()), false),
                new Line("stratos.tool.line.palette", RoadSettings.name(this.settings.palette()), false),
                new Line("stratos.tool.line.length", length, false),
                new Line("stratos.tool.line.headings", headings, false),
                new Line("stratos.tool.line.radius", radius, false),
                ElevationHud.slope(this.settings.profile(), this.settings, planning, current, RoadNotices.turned(params, planning)),
                ElevationHud.shape(this.settings, params),
                ElevationHud.nodes(this.settings),
                new Line("stratos.tool.line.blocks", blocks, false));
        int step = switch (state) {
            case IDLE -> 1;
            case PLACING -> this.held != null ? 3 : 2;
            default -> 3;
        };
        String stateKey;
        if (this.held != null) {
            stateKey = this.held == Handle.Kind.START ? "stratos.tool.state.moving_start" : "stratos.tool.state.moving_end";
        } else {
            stateKey = switch (state) {
                case IDLE -> "stratos.tool.state.pick_start_facing";
                case PLACING -> "stratos.tool.state.aim_end_facing";
                case PREVIEWING -> "stratos.tool.state.check_place";
                case COMMITTING -> "stratos.tool.state.placing";
            };
        }
        return new HudModel(titleKey(), step, 3, Text.of(stateKey), lines, RoadNotices.notice(this.notSent, current, planning, this.joins), hints(state, placeable));
    }

    private List<Hint> hints(ToolState state, boolean placeable) {
        List<Hint> hints = new ArrayList<>();
        if (this.held != null) {
            if (fixable()) {
                hints.add(new Hint(Action.USE, "stratos.tool.hint.put_down"));
            }
            hints.add(new Hint(Action.ATTACK, "stratos.tool.hint.put_back"));
        } else {
            switch (state) {
                case IDLE -> hints.add(new Hint(Action.USE, "stratos.tool.hint.set_start"));
                case PLACING -> {
                    if (fixable()) {
                        hints.add(new Hint(Action.USE, "stratos.tool.hint.set_end"));
                    }
                    hints.add(new Hint(Action.ATTACK, "stratos.tool.hint.take_back_start"));
                }
                case PREVIEWING -> {
                    Optional<Handle> aimed = aimedHandle();
                    if (aimed.isPresent()) {
                        hints.add(new Hint(Action.USE, aimed.get().kind() == Handle.Kind.START ? "stratos.tool.hint.pick_up_start"
                                : "stratos.tool.hint.pick_up_end"));
                    } else if (placeable) {
                        hints.add(new Hint(Action.USE, "stratos.tool.hint.place"));
                    }
                    hints.add(new Hint(Action.ATTACK, "stratos.tool.hint.take_back_end"));
                }
                default -> {
                }
            }
        }
        hints.add(new Hint(Action.TOOL_KEY, "stratos.tool.hint.put_away"));
        if (state != ToolState.COMMITTING) {
            hints.add(new Hint(Action.UNDO, "stratos.tool.hint.undo"));
            hints.add(new Hint(Action.REDO, "stratos.tool.hint.redo"));
        }
        ElevationHud.hints(hints, following(), state == ToolState.COMMITTING, this.settings);
        return hints;
    }
}
