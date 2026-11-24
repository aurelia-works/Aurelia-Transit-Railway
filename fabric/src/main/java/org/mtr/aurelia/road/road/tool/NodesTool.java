/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.nodes.NodeAids;
import org.mtr.aurelia.road.road.nodes.NodeCheck;
import org.mtr.aurelia.road.tool.Answer;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Action;
import org.mtr.aurelia.road.tool.HudModel.Hint;
import org.mtr.aurelia.road.tool.HudModel.Level;
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
 * The Lane nodes tool (task R-6; owner, 2026-10-06), the third road tool: it shows the checker's list for a road placed
 * this visit, and clears that road's blue-wool markers.
 * <ul>
 *   <li><b>The road</b> is the one the "Lane nodes" panel would show ({@link NodeAids#focus}): the road whose node or
 *       blocks the player aims at, else the nearest within 48 blocks. The HUD has the panel's rows.</li>
 *   <li><b>Use</b> clears its markers when it has wool left: a {@code CLEAR_MARKERS} request for the road as placed.
 *       The server writes the surface block back only where the wool still is, so nothing else (an MTR node least of
 *       all) is touched. The preview shows the blocks that will change; {@code -} undoes it.</li>
 *   <li><b>Attack</b> does nothing.</li>
 * </ul>
 */
public final class NodesTool implements Tool {
    public static final String ID = "roads.nodes";

    private final NodeAids aids;
    private Optional<Target> aim = Optional.empty();
    private float yaw;
    private RoadPlanParams plannedFor;
    private Planned planned;
    private Commit<RoadPlanParams> commit;
    private boolean committing;
    private Notice notice;

    public NodesTool(NodeAids aids) {
        this.aids = Objects.requireNonNull(aids, "aids");
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
        return "stratos.tool.roads.nodes";
    }

    @Override
    public Set<Action> keys() {
        return EnumSet.of(Action.USE, Action.ATTACK, Action.TOOL_KEY, Action.UNDO, Action.REDO);
    }

    @Override
    public ToolState state() {
        return this.committing ? ToolState.COMMITTING : ToolState.IDLE;
    }

    /** The road and node the HUD shows now. */
    public Optional<NodeAids.Focus> focus() {
        return this.aids.focus(this.aim);
    }

    /** The request that would clear the shown road's markers, if it has wool left. */
    public Optional<RoadPlanParams> clearParams() {
        Optional<NodeAids.Focus> focus = focus();
        if (focus.isEmpty()) {
            return Optional.empty();
        }
        Optional<NodeCheck.Report> report = this.aids.report(focus.get().road());
        if (report.isEmpty() || report.get().markers() == 0) {
            return Optional.empty();
        }
        return Optional.of(RoadPlanParams.clearMarkers(focus.get().road().params()));
    }

    /** The planner's answer for {@link #clearParams()}, once it has arrived. */
    public Optional<Planned> planned() {
        Optional<RoadPlanParams> params = clearParams();
        return params.isPresent() && params.get().equals(this.plannedFor) ? Optional.of(this.planned) : Optional.empty();
    }

    @Override
    public void input(ToolInput input) {
        this.yaw = input.yaw();
        this.aim = input.target();
        if (this.committing || !(input instanceof ToolInput.Use)) {
            return;
        }
        Optional<Planned> current = planned();
        if (current.isPresent() && current.get().isPlanned()) {
            this.notice = null;
            BlockPlan plan = current.get().result().plan().orElseThrow();
            this.commit = new Commit<>(RoadsModule.INSTANCE, clearParams().orElseThrow(), plan);
            this.committing = true;
        }
    }

    @Override
    public ToolOutput output() {
        return new ToolOutput(clearParams().map(StraightTool.RoadJob::new), List.of());
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
        if (answer instanceof Answer.NotSent notSent) {
            this.committing = false;
            this.notice = Notice.reason(Level.ERROR, notSent.code(), notSent.args());
        } else if (answer instanceof Answer.Rejected || answer instanceof Answer.Failed || answer instanceof Answer.Done) {
            this.committing = false; // the server's answer is in the HUD; the Markers row follows the world
        }
    }

    @Override
    public void reset() {
        this.aim = Optional.empty();
        this.plannedFor = null;
        this.planned = null;
        this.commit = null;
        this.committing = false;
        this.notice = null;
    }

    /** One step: the panel's rows for the road shown, use to clear its markers. */
    @Override
    public HudModel hud() {
        Optional<NodeAids.Focus> focus = focus();
        Optional<Planned> current = planned();
        boolean clearable = current.map(Planned::isPlanned).orElse(false);
        Text state = this.committing ? Text.of("stratos.nodes.state.clearing") : this.aids.state(focus, true);
        List<Hint> hints = new ArrayList<>();
        if (clearable && !this.committing) {
            hints.add(new Hint(Action.USE, "stratos.tool.hint.clear_markers"));
        }
        hints.add(new Hint(Action.TOOL_KEY, "stratos.tool.hint.put_away"));
        if (!this.committing) {
            hints.add(new Hint(Action.UNDO, "stratos.tool.hint.undo"));
            hints.add(new Hint(Action.REDO, "stratos.tool.hint.redo"));
        }
        return new HudModel(titleKey(), 0, 0, state, this.aids.lines(focus, this.yaw), notice(current), hints);
    }

    /** Why the request wasn't sent; else why clearing is refused. */
    private Optional<Notice> notice(Optional<Planned> current) {
        if (this.notice != null) {
            return Optional.of(this.notice);
        }
        if (current.isPresent() && !current.get().isPlanned()) {
            PlanResult result = current.get().result();
            return Optional.of(Notice.reason(Level.ERROR, result.code().orElseThrow(), result.args()));
        }
        return Optional.empty();
    }
}
