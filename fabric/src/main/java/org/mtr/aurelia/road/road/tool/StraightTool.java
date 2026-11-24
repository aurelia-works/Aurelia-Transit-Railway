/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadsModule;
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
 * The straight road tool (R-1's shell; R-2 places roads). Three steps (owner, 2026-10-06):
 * <ol>
 *   <li><b>Use</b> sets the start at the block aimed at.</li>
 *   <li>The end follows the aim: the heading is the nearest of the 16 from the start to the aimed block, and the end is
 *       {@link RoadPlanner#snapEnd} for the aimed distance along it (a 22.5° road ends only within 0.05 block of its
 *       line), at the start's height. <b>Use</b> fixes it.</li>
 *   <li>The preview stays while the player looks round it. <b>Use</b> places the road; then the tool waits for the
 *       server ({@link ToolState#COMMITTING}) and, once the road is placed, starts again from step 1.</li>
 * </ol>
 * <b>Attack</b> takes the last step back: the end, then the start. Aiming at nothing keeps the last end. A road the
 * planner refuses is drawn in red with the reason in the HUD, and can't be fixed or placed; a warning shows in the HUD.
 *
 * <p><b>Heights</b> (owner, 2026-10-06: R-4). The start is at the clicked block's height. The end, while it follows the
 * aim, is at the aimed block's height plus the Page Up / Page Down adjustment; a picked-up handle keeps its height (the
 * adjustment starts as its height above the aimed block). The shape (Quadratic or Two radii) and its radius come from
 * the shared {@link RoadSettings}, as does the layout (G-4+).
 *
 * <p><b>Handles</b> (owner, 2026-10-06: R-3). In step 3, aiming at a handle, <b>use</b> picks it up instead of placing;
 * <b>use</b> puts it down and <b>attack</b> puts it back. A held end follows the aim as in step 2. A held start follows
 * the aim, the heading becomes the nearest of the 16 from it to the end, and the end is snapped onto that heading at its
 * old distance.
 *
 * <p><b>Joining</b> (owner, 2026-10-09: R-5). A start set, or a start handle moved, near the end of one of the player's
 * roads ({@link JoinableRoads}) joins it: it takes that end's block and height, and the road runs on the way that road
 * leaves it, the end following the aim along that heading only. The end joins a road end straight ahead of the start
 * that faces it. The road the planner gets may be stored the other way round ({@link JoinableRoads#finish}); the
 * handles and the HUD stay the player's way.
 */
public final class StraightTool implements Tool {
    public static final String ID = "roads.straight";

    private final RoadSettings settings;
    private final JoinableRoads joins;
    private Target start;
    /** The road end the start joins (R-5), if it does: the road then runs on the way that road leaves it. */
    private Optional<JoinableRoads.Join> startJoin = Optional.empty();
    /** Page Up / Page Down's adjustment to whatever follows the aim (the end in step 2, or a held handle), in blocks. */
    private int raise;
    /** The end, while there is one; it follows the aim until fixed. */
    private RoadPlanParams.End end;
    private boolean endFixed;
    /** The handle being moved, and where the road was (to put it back); null while none is held. */
    private Handle.Kind held;
    private Target heldStart;
    private Optional<JoinableRoads.Join> heldStartJoin = Optional.empty();
    private RoadPlanParams.End heldEnd;
    /** A handle put down before the planner's answer came: picked up again if the answer is a refusal. */
    private Handle.Kind dropped;
    private final HandlePicker picker = new HandlePicker();
    private Optional<Target> aim = Optional.empty();
    /** The planner's answer and the road it is for (it may be for an older end while the newest is worked out). */
    private RoadPlanParams plannedFor;
    private Planned planned;
    /** Confirmed, until the session takes it. */
    private Commit<RoadPlanParams> commit;
    /** Confirmed and not yet answered for good. */
    private boolean committing;
    /** Why the last commit wasn't sent, until the player does something else. */
    private Notice notSent;

    /** Where the aimed block is from the start: the nearest of the 16 headings, the distance along it, and the height difference. */
    public record Measure(Heading heading, double along, int rise) {
    }

    /**
     * The road to work out and preview: the planner's outcome, off the client thread. From R-5 with the placed roads its
     * ends may join, copied on the client thread ({@link #of}).
     */
    public record RoadJob(RoadPlanParams params, Map<UUID, RoadPlanParams> placed) implements PlanJob {
        public RoadJob {
            Objects.requireNonNull(params, "params");
            placed = Map.copyOf(placed);
        }

        /** A road that joins nothing. */
        public RoadJob(RoadPlanParams params) {
            this(params, Map.of());
        }

        /** The job for {@code params}, with {@code joins}' roads when an end of it joins one (R-5). */
        public static RoadJob of(RoadPlanParams params, JoinableRoads joins) {
            boolean joining = params.ends().stream().anyMatch(end -> end.snapRoadId().isPresent());
            return new RoadJob(params, joining ? joins.placed() : Map.of());
        }

        @Override
        public Planned run(WorldLimits limits) {
            RoadPlanner.Outcome outcome = RoadPlanner.outcome(this.params, limits, this.placed);
            return new Planned(outcome.result(), outcome.refusedShape());
        }
    }

    /** A tool with its own settings (tests). */
    public StraightTool() {
        this(new RoadSettings());
    }

    /** A tool sharing {@code settings} with the other road tools, joining no roads. */
    public StraightTool(RoadSettings settings) {
        this(settings, JoinableRoads.NONE);
    }

    /** A tool sharing {@code settings} with the other road tools, whose ends may join {@code joins}' roads (R-5). */
    public StraightTool(RoadSettings settings, JoinableRoads joins) {
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
        return "stratos.tool.roads.straight";
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

    /** The handle being moved, if any. */
    public Optional<Handle.Kind> held() {
        return Optional.ofNullable(this.held);
    }

    public Optional<Target> start() {
        return Optional.ofNullable(this.start);
    }

    /**
     * The road as it stands, as the planner gets it (R-5: stored the way round that lets its dashes carry on, with the
     * dash pattern its joins call for), or empty without an end.
     */
    public Optional<RoadPlanParams> params() {
        return drawn().map(this.joins::finish);
    }

    /** The road from the start the player set to their end, naming the road ends they join. */
    private Optional<RoadPlanParams> drawn() {
        if (this.start == null || this.end == null) {
            return Optional.empty();
        }
        RoadPlanParams.End from = RoadPlanParams.End.at(this.start.x(), this.start.y(), this.start.z(), this.end.heading());
        RoadPlanParams.End joined = this.startJoin.map(j -> JoinableRoads.joined(from, j)).orElse(from);
        return Optional.of(RoadPlanParams.straight(this.settings.profile().id(), joined, this.end, this.settings.vertical(), this.settings.nodeAids())
                .withPalette(this.settings.palette().id()));
    }

    /** The planner's answer for the road as it stands, once it has arrived. */
    public Optional<Planned> planned() {
        Optional<RoadPlanParams> params = params();
        return params.isPresent() && params.get().equals(this.plannedFor) ? Optional.of(this.planned) : Optional.empty();
    }

    @Override
    public void input(ToolInput input) {
        if (input instanceof ToolInput.Aim aimInput) {
            this.aim = aimInput.target();
            if (this.held == Handle.Kind.START) {
                followStart();
            } else if (state() == ToolState.PLACING) {
                followAim();
            } else if (state() == ToolState.PREVIEWING) {
                drawn().ifPresent(p -> this.picker.aim(this.aim, RoadPlanner.handles(p)));
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
            use(use);
        } else if (input instanceof ToolInput.Attack) {
            if (this.held != null) {
                putBack();
                return;
            }
            switch (state()) {
                case PREVIEWING -> {
                    this.endFixed = false;
                    this.dropped = null;
                    this.raise = this.aim.map(a -> this.end.y() - a.y()).orElse(0); // the end keeps its height
                    followAim();
                }
                case PLACING -> reset();
                default -> {
                }
            }
        }
    }

    private void use(ToolInput.Use use) {
        if (this.held != null) {
            if (fixable()) {
                this.dropped = planned().isPresent() ? null : this.held;
                this.held = null;
                this.picker.disarm();
            }
            return;
        }
        switch (state()) {
            case IDLE -> use.target().ifPresent(target -> {
                this.startJoin = this.joins.near(target);
                this.start = this.startJoin.map(j -> new Target(j.port().x(), j.port().y(), j.port().z(), target.distance())).orElse(target);
                this.aim = use.target();
                this.raise = 0;
                followAim();
            });
            case PLACING -> {
                // A road known to be refused can't be fixed; one still being worked out is fixed now and let go if refused.
                if (fixable()) {
                    this.endFixed = true;
                    this.picker.disarm();
                }
            }
            case PREVIEWING -> {
                this.aim = use.target();
                Optional<Handle> aimed = aimedHandle();
                if (aimed.isPresent()) {
                    this.held = aimed.get().kind();
                    this.heldStart = this.start;
                    this.heldStartJoin = this.startJoin;
                    this.heldEnd = this.end;
                    this.dropped = null;
                    // Picking up never moves the handle: the adjustment starts as its height above the aimed block.
                    this.raise = (this.held == Handle.Kind.START ? this.start.y() : this.end.y()) - this.aim.orElseThrow().y();
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
            if (this.held == Handle.Kind.START) {
                followStart();
            } else {
                followAim();
            }
            return;
        }
        try { // aiming at nothing: move what follows by the step itself
            if (this.held == Handle.Kind.START) {
                this.start = new Target(this.start.x(), this.start.y() + step, this.start.z(), this.start.distance());
                this.startJoin = Optional.empty();
            } else if (this.end != null) {
                this.end = RoadPlanParams.End.at(this.end.x(), this.end.y() + step, this.end.z(), this.end.heading());
            }
        } catch (IllegalArgumentException e) {
            this.raise -= step; // beyond any world's height
        }
    }

    /** Whether something follows the aim now: the end in step 2, or a held handle. */
    private boolean following() {
        return this.start != null && !this.committing && (this.held != null || !this.endFixed);
    }

    private void putBack() {
        this.start = this.heldStart;
        this.startJoin = this.heldStartJoin;
        this.end = this.heldEnd;
        this.held = null;
    }

    /** The handle the player aims at, in step 3. */
    private Optional<Handle> aimedHandle() {
        if (state() != ToolState.PREVIEWING) {
            return Optional.empty();
        }
        return drawn().flatMap(p -> this.picker.pickable(this.aim, RoadPlanner.handles(p)));
    }

    /**
     * A held start follows the aim: the heading becomes the nearest of the 16 from it to the end, and the end is snapped
     * onto that heading at its old distance. Aiming at the end's own block keeps the start where it was.
     */
    private void followStart() {
        if (this.aim.isEmpty() || this.end == null) {
            return;
        }
        Target aimed = this.aim.get();
        Optional<JoinableRoads.Join> join = this.joins.near(aimed.x(), aimed.y() + this.raise, aimed.z());
        Target target = join.map(j -> new Target(j.port().x(), j.port().y(), j.port().z(), aimed.distance()))
                .orElseGet(() -> new Target(aimed.x(), aimed.y() + this.raise, aimed.z(), aimed.distance()));
        int dx = this.end.x() - target.x();
        int dz = this.end.z() - target.z();
        if (dx == 0 && dz == 0) {
            return;
        }
        // A joined start runs on the way the road it joins leaves it (R-5).
        Heading heading = join.map(j -> j.port().outgoing()).orElseGet(() -> nearestHeading(dx, dz));
        double along = new Frame(Vec2.ZERO, heading).along(new Vec2(dx, dz));
        try {
            RoadPlanParams.End from = RoadPlanParams.End.at(target.x(), target.y(), target.z(), heading);
            RoadPlanParams.End snapped = RoadPlanner.snapEnd(from, StrictMath.max(along, 0));
            this.end = RoadPlanParams.End.at(snapped.x(), this.end.y(), snapped.z(), heading); // the end keeps its height
            this.start = target;
            this.startJoin = join;
        } catch (IllegalArgumentException e) {
            // beyond any world's border or height: the start stays where it was
        }
    }

    /** Whether the end can be fixed: there is one, and it isn't known to be refused (one being worked out can). */
    private boolean fixable() {
        return this.end != null && planned().map(Planned::isPlanned).orElse(true);
    }

    /** Moves the end to where the aim points, snapped; with no usable aim the end stays. */
    private void followAim() {
        Optional<Measure> measure = measure();
        if (measure.isEmpty() || measure.get().heading() == null) {
            return;
        }
        Measure m = measure.get();
        try {
            RoadPlanParams.End from = RoadPlanParams.End.at(this.start.x(), this.start.y(), this.start.z(), m.heading());
            Target aimed = this.aim.orElseThrow();
            Optional<JoinableRoads.Join> join = this.joins.near(aimed.x(), aimed.y() + this.raise, aimed.z()).filter(j -> ahead(from, j));
            if (join.isPresent()) {
                this.end = JoinableRoads.joined(RoadPlanParams.End.at(this.start.x(), this.start.y(), this.start.z(), m.heading()), join.get());
                return;
            }
            RoadPlanParams.End snapped = RoadPlanner.snapEnd(from, m.along());
            this.end = RoadPlanParams.End.at(snapped.x(), aimed.y() + this.raise, snapped.z(), m.heading());
        } catch (IllegalArgumentException e) {
            this.end = null; // beyond any world's border or height
        }
    }

    /**
     * Whether a straight road from {@code from} can end at {@code join}'s road end (R-5): the end is on the start's
     * heading line (as the planner allows a straight end), at least the shortest road away, and faces the road: a road
     * leaving that end would run back along this one.
     */
    private static boolean ahead(RoadPlanParams.End from, JoinableRoads.Join join) {
        RoadJoins.Port port = join.port();
        Frame frame = new Frame(Vec2.ZERO, from.heading());
        Vec2 offset = new Vec2((double) port.x() - from.x(), (double) port.z() - from.z());
        return port.outgoing() == from.heading().opposite() && StrictMath.abs(frame.side(offset)) <= RoadPlanner.MTR_TOLERANCE
                && frame.along(offset) >= RoadPlanner.MIN_LENGTH;
    }

    @Override
    public ToolOutput output() {
        if (this.start == null) {
            return ToolOutput.EMPTY;
        }
        Optional<RoadPlanParams> drawn = drawn();
        if (drawn.isEmpty()) {
            return new ToolOutput(Optional.empty(), List.of(new Handle(this.start.x(), this.start.y(), this.start.z(), Handle.Kind.START)));
        }
        Handle.Kind active = this.held != null ? this.held : aimedHandle().map(Handle::kind).orElse(null);
        List<Handle> handles = new ArrayList<>();
        for (Handle handle : RoadPlanner.handles(drawn.get())) {
            handles.add(new Handle(handle.x(), handle.y(), handle.z(), handle.kind(), handle.kind() == active));
        }
        return new ToolOutput(params().map(p -> RoadJob.of(p, this.joins)), handles);
    }

    @Override
    public void planned(PlanJob job, Planned answer) {
        if (job instanceof RoadJob road && road.params().equals(params().orElse(null))) {
            this.plannedFor = road.params();
            this.planned = answer;
            if (answer.isPlanned() || this.committing) {
                this.dropped = null;
                return;
            }
            if (this.dropped != null) {
                this.held = this.dropped; // put down before the answer came, and refused: picked up again
                this.dropped = null;
            } else if (this.endFixed && this.held == null) {
                this.endFixed = false; // fixed before the answer came, and refused: the end follows the aim again
                followAim();
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
        this.startJoin = Optional.empty();
        this.end = null;
        this.raise = 0;
        this.endFixed = false;
        this.held = null;
        this.heldStart = null;
        this.heldStartJoin = Optional.empty();
        this.heldEnd = null;
        this.dropped = null;
        this.aim = Optional.empty();
        this.plannedFor = null;
        this.planned = null;
        this.commit = null;
        this.committing = false;
        this.notSent = null;
    }

    /** The live numbers from the start to the aimed block, or empty with no start, no aim, or the start itself aimed at. */
    public Optional<Measure> measure() {
        if (this.start == null || this.aim.isEmpty() || this.aim.get().sameBlock(this.start)) {
            return Optional.empty();
        }
        Target target = this.aim.get();
        int dx = target.x() - this.start.x();
        int dz = target.z() - this.start.z();
        int rise = target.y() - this.start.y();
        if (dx == 0 && dz == 0) {
            return Optional.of(new Measure(null, 0, rise));
        }
        // R-5: a joined start runs on the way the road it joins leaves it; an aim behind it gives the shortest road.
        Heading heading = this.startJoin.map(j -> j.port().outgoing()).orElseGet(() -> nearestHeading(dx, dz));
        double along = StrictMath.max(0, new Frame(Vec2.ZERO, heading).along(new Vec2(dx, dz)));
        return Optional.of(new Measure(heading, along, rise));
    }

    /** The nearest of the 16 headings to the direction (dx, dz); ties go to the lower index. */
    static Heading nearestHeading(int dx, int dz) {
        double degrees = StrictMath.toDegrees(StrictMath.atan2(dz, dx)); // east 0°, south +90° (roads-v1 §1)
        long index = StrictMath.round(degrees / Heading.STEP_DEGREES);
        return Heading.of((int) StrictMath.floorMod(index, (long) Heading.COUNT));
    }

    /**
     * Three steps: pick the start, aim at the end, check and place. The same seven values in every state (the HUD keeps
     * its size), with a dash where there is nothing to show yet.
     */
    @Override
    public HudModel hud() {
        ToolState state = state();
        Optional<RoadPlanParams> params = drawn(); // the player's way (R-5: the planner's may be turned round)
        Optional<RoadPlanParams> planning = params();
        Optional<Planned> current = planned();
        boolean placeable = current.map(Planned::isPlanned).orElse(false);
        boolean fixable = fixable();
        Text length = HudModel.NONE;
        Text heading = HudModel.NONE;
        Text blocks = HudModel.NONE;
        if (params.isPresent()) {
            RoadPlanParams.End from = params.get().ends().get(0);
            RoadPlanParams.End to = params.get().ends().get(1);
            double along = new Frame(Vec2.ZERO, from.heading()).along(new Vec2((double) to.x() - from.x(), (double) to.z() - from.z()));
            length = Text.of("stratos.tool.value.blocks", String.format(Locale.ROOT, "%.1f", along));
            heading = Text.of("stratos.tool.value.heading", from.heading().name(), String.format(Locale.ROOT, "%.1f", from.heading().degrees()));
        }
        if (placeable) {
            blocks = Text.of("stratos.tool.value.count", String.format(Locale.ROOT, "%,d", current.get().result().plan().orElseThrow().blockCount()));
        }
        List<Line> lines = List.of(
                new Line("stratos.tool.line.layout", Text.of("stratos.road.profile." + this.settings.profile().id()), false),
                new Line("stratos.tool.line.palette", RoadSettings.name(this.settings.palette()), false),
                new Line("stratos.tool.line.length", length, false),
                new Line("stratos.tool.line.heading", heading, false),
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
                case IDLE -> "stratos.tool.state.pick_start";
                case PLACING -> "stratos.tool.state.aim_end";
                case PREVIEWING -> "stratos.tool.state.check_place";
                case COMMITTING -> "stratos.tool.state.placing";
            };
        }
        return new HudModel(titleKey(), step, 3, Text.of(stateKey), lines, RoadNotices.notice(this.notSent, current, planning, this.joins),
                hints(state, fixable, placeable));
    }

    private List<Hint> hints(ToolState state, boolean fixable, boolean placeable) {
        List<Hint> hints = new ArrayList<>();
        if (this.held != null) {
            if (fixable) {
                hints.add(new Hint(Action.USE, "stratos.tool.hint.put_down"));
            }
            hints.add(new Hint(Action.ATTACK, "stratos.tool.hint.put_back"));
        } else {
            switch (state) {
                case IDLE -> hints.add(new Hint(Action.USE, "stratos.tool.hint.set_start"));
                case PLACING -> {
                    if (fixable) {
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
        ElevationHud.hints(hints, following() && this.end != null, state == ToolState.COMMITTING, this.settings);
        return hints;
    }
}
