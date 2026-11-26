/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.preview.Preview;
import org.mtr.aurelia.road.preview.PreviewModel;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.Message.ApplyProgress;
import org.mtr.aurelia.road.proto.Message.ApplyState;
import org.mtr.aurelia.road.proto.Message.PlanAccepted;
import org.mtr.aurelia.road.proto.Message.PlanRejected;
import org.mtr.aurelia.road.proto.Message.TaskProgress;
import org.mtr.aurelia.road.proto.Message.TaskResult;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.spi.ToolEnvironment;

/**
 * The player's tools and the one in hand (owner, 2026-10-05: crosshair tool mode). At most one tool is active; while one
 * is, the platform sends it the use, attack and aim input instead of letting the game have it.
 *
 * <p><b>Round trips</b> (R-1's acceptance): switching away from a tool or putting it away resets it to
 * {@link ToolState#IDLE}, so anything half-placed is dropped and its preview cleared; its settings stay. Choosing it
 * again starts it from {@code IDLE}.
 *
 * <p><b>Plans and placing</b> (R-2):
 * <ul>
 *   <li>When the tool's output names a new {@link PlanJob}, the session works it out and builds its preview on the tool
 *       planner thread (plans never block a frame: spec mod-suite-v1 §7). Only the newest job counts: an older one is
 *       cancelled, and its answer dropped if it arrives anyway. A refused plan is drawn in red (roads-v1 §12).</li>
 *   <li>When the tool offers a {@link Commit}, the session sends it at once and reports what became of it: the server's
 *       answers to that request, and the progress of the operation it started, go to the tool that sent it, as long as
 *       it is still in hand.</li>
 *   <li>A {@code PLAN_HASH_MISMATCH} refusal works the tool's plan out again and rebuilds the whole preview
 *       (editor-protocol §5: "The preview has been refreshed").</li>
 *   <li>When only the handles change (R-3: one aimed at or picked up), they are redrawn at once; the plan isn't worked
 *       out again.</li>
 *   <li>When a commit has been placed in full, the platform hears of it ({@link ToolEnvironment#placed}), even if its
 *       tool was put away meanwhile (R-6: the road tools' roads are remembered for the node aids).</li>
 *   <li>B-3: a tool's world task ({@link Tool#takeTask}) is sent like a commit, and its progress and end go back to the
 *       tool the same way.</li>
 * </ul>
 * The platform registers the session as an {@link EditorClient.Listener}. Client thread only, except the background work.
 */
public final class ToolSession implements EditorClient.Listener {
    /** The empty plan a handles-only preview is built on; its DataVersion never reaches a world. */
    private static final BlockPlan NO_BLOCKS = BlockPlan.builder(0).build();
    private static final int NONE = -1;

    private final Preview preview;
    private final ToolEnvironment environment;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private Tool active;
    private Tool last;
    /** The output on screen, or being worked out for the screen. */
    private ToolOutput shown = ToolOutput.EMPTY;
    /** The preview on screen for {@link #shown}'s job, once its plan has arrived; null before. */
    private PreviewModel shownModel;
    /** Goes up whenever {@link #shown} changes or the tool is put away; a background answer for an older one is dropped. */
    private long generation;
    private Future<?> planning;
    /** The commit in flight: the tool that sent it, its request id and, once accepted, its operation. */
    private Tool requester;
    private int requestId = NONE;
    private UUID operationId;
    /** The request the tool in hand sent last, until it is put away (the mismatch message names the refresh). */
    private int lastRequestId = NONE;
    /** Commits sent and not yet answered, by request id; then accepted ones by operation id, until placed (R-6). */
    private final Map<Integer, Commit<?>> sent = new HashMap<>();
    private final Map<UUID, Commit<?>> placing = new HashMap<>();

    public ToolSession(Preview preview, ToolEnvironment environment) {
        this.preview = Objects.requireNonNull(preview, "preview");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /** Adds a tool; ids must be unique. */
    public void register(Tool tool) {
        if (this.tools.putIfAbsent(tool.id(), tool) != null) {
            throw new IllegalArgumentException("Two tools are called " + tool.id());
        }
    }

    /** Every tool, in registration order. */
    public List<Tool> tools() {
        return List.copyOf(this.tools.values());
    }

    /** The tools of one module, in registration order. */
    public List<Tool> tools(String moduleId) {
        List<Tool> list = new ArrayList<>();
        this.tools.values().stream().filter(tool -> tool.moduleId().equals(moduleId)).forEach(list::add);
        return list;
    }

    public Optional<Tool> tool(String id) {
        return Optional.ofNullable(this.tools.get(id));
    }

    public Optional<Tool> active() {
        return Optional.ofNullable(this.active);
    }

    /** The tool used last, active or not ({@link #toggle} brings it back). */
    public Optional<Tool> last() {
        return Optional.ofNullable(this.last);
    }

    /** Takes {@code id} in hand, from {@code IDLE}. The tool in hand before it is reset and its preview cleared. */
    public void select(String id) {
        Tool tool = this.tools.get(id);
        if (tool == null) {
            throw new IllegalArgumentException("There is no tool called " + id);
        }
        if (tool == this.active) {
            return;
        }
        putAway();
        this.active = tool;
        this.last = tool;
        tool.reset();
        refresh(false);
    }

    /** Puts the tool in hand away: it is reset, and its preview and HUD go. */
    public void leave() {
        if (this.active != null) {
            putAway();
        }
    }

    /** The tool key: puts the active tool away, or brings the last one back. */
    public void toggle() {
        if (this.active != null) {
            leave();
        } else if (this.last != null) {
            select(this.last.id());
        }
    }

    /**
     * Sends {@code input} to the tool in hand. Returns false when no tool is active, so the game keeps the input. An attack
     * the tool has no step for (its HUD shows none) puts the tool away and also returns false, so the click breaks the
     * block aimed at instead of doing nothing (2026-11-26).
     */
    public boolean input(ToolInput input) {
        if (this.active == null) {
            return false;
        }
        if (input instanceof ToolInput.Attack && this.active.hud().hint(HudModel.Action.ATTACK).isEmpty()) {
            leave();
            return false;
        }
        this.active.input(input);
        update();
        return true;
    }

    /** Shows the tool in hand's output again after its settings changed outside its input (R-4: the radius box). */
    public void settingsChanged() {
        if (this.active != null) {
            update();
        }
    }

    /** The HUD of the tool in hand. Where the tool has no attack step, the attack row says it puts the tool away. */
    public Optional<HudModel> hud() {
        if (this.active == null) {
            return Optional.empty();
        }
        HudModel hud = this.active.hud();
        if (hud.hint(HudModel.Action.ATTACK).isPresent()) {
            return Optional.of(hud);
        }
        List<HudModel.Hint> hints = new ArrayList<>(hud.hints());
        hints.add(new HudModel.Hint(HudModel.Action.ATTACK, "stratos.tool.hint.put_away"));
        return Optional.of(new HudModel(hud.titleKey(), hud.step(), hud.steps(), hud.state(), hud.lines(), hud.notice(), hints));
    }

    /** The question the tool in hand asks now (R-7), for the platform's box. */
    public Optional<Question> question() {
        return this.active == null ? Optional.empty() : this.active.question();
    }

    /**
     * Whether request {@code id} is the last one the tool in hand sent, and the tool hasn't been put away since: then a
     * {@code PLAN_HASH_MISMATCH} for it refreshes the preview.
     */
    public boolean ownsRequest(int id) {
        return this.active != null && id != NONE && id == this.lastRequestId;
    }

    /** Whether a plan is being worked out in the background now. */
    public boolean planning() {
        return this.planning != null;
    }

    // ---- The server's answers (EditorClient.Listener) ----

    @Override
    public void planAccepted(PlanAccepted accepted) {
        Commit<?> commit = this.sent.remove(accepted.requestId());
        if (commit != null) {
            this.placing.put(accepted.operationId(), commit);
        }
        if (this.requester != null && this.operationId == null && accepted.requestId() == this.requestId) {
            this.operationId = accepted.operationId();
            tell(new Answer.Accepted(accepted.blockCount(), accepted.sectionCount()), false);
        }
    }

    @Override
    public void planRejected(PlanRejected rejected) {
        this.sent.remove(rejected.requestId());
        if (this.requester != null && this.operationId == null && rejected.requestId() == this.requestId) {
            boolean mismatch = rejected.reasonCode().equals(Protocol.PLAN_HASH_MISMATCH);
            finish(new Answer.Rejected(rejected.reasonCode(), rejected.args()), mismatch);
        }
    }

    @Override
    public void progress(ApplyProgress progress) {
        if (progress.state() == ApplyState.DONE || progress.state() == ApplyState.FAILED || progress.state() == ApplyState.ROLLED_BACK) {
            Commit<?> commit = this.placing.remove(progress.operationId());
            if (commit != null && progress.state() == ApplyState.DONE) {
                this.environment.placed(commit, progress.operationId());
            }
        }
        if (this.requester == null || this.operationId == null || !progress.operationId().equals(this.operationId)) {
            return;
        }
        switch (progress.state()) {
            case APPLYING -> tell(new Answer.Progress(progress.sectionsDone(), progress.sectionsTotal()), false);
            case DONE -> finish(new Answer.Done(), false);
            case FAILED, ROLLED_BACK -> finish(new Answer.Failed(progress.reasonCode().orElse(Protocol.INTERNAL_ERROR)), false);
            default -> {
            }
        }
    }

    @Override
    public void taskProgress(TaskProgress progress) {
        if (this.requester != null && this.operationId == null && progress.requestId() == this.requestId) {
            tell(new Answer.Progress(progress.done(), progress.total()), false);
        }
    }

    @Override
    public void taskResult(TaskResult result) {
        if (this.requester != null && this.operationId == null && result.requestId() == this.requestId) {
            finish(result.ok() ? new Answer.TaskDone(result.args()) : new Answer.Rejected(result.reasonCode().orElse(Protocol.INTERNAL_ERROR),
                    result.args()), false);
        }
    }

    @Override
    public void editingChanged(boolean enabled, Optional<String> reasonCode) {
        if (!enabled) {
            this.sent.clear();
            this.placing.clear();
        }
        if (!enabled && this.requester != null) {
            // The connection or the handshake ended mid-request: no answer will come.
            finish(new Answer.Rejected(Protocol.EDITING_OFF, Map.of()), false);
        }
    }

    // ---- Inside ----

    private void putAway() {
        if (this.active != null) {
            this.active.reset();
            this.active = null;
        }
        dropRequest();
        this.lastRequestId = NONE;
        stopPlanning();
        this.shown = ToolOutput.EMPTY;
        this.shownModel = null;
        this.generation++;
        this.preview.clear();
    }

    /** After the tool changed: send what it confirmed, then show what it wants shown. */
    private void update() {
        sendCommit();
        refresh(false);
    }

    private void sendCommit() {
        Optional<WorldTask> task = this.active.takeTask();
        if (task.isPresent()) {
            OptionalInt id = this.environment.sendTask(task.get());
            if (id.isPresent()) {
                this.requester = this.active;
                this.requestId = id.getAsInt();
                this.operationId = null;
                this.lastRequestId = this.requestId;
                this.active.answered(new Answer.Sent(this.requestId));
            } else {
                this.active.answered(new Answer.NotSent(Protocol.EDITING_OFF, Map.of()));
            }
            return;
        }
        Optional<Commit<?>> commit = this.active.takeCommit();
        if (commit.isEmpty()) {
            return;
        }
        EditorClient.Attempt attempt = send(commit.get());
        if (attempt.isSent()) {
            this.requester = this.active;
            this.requestId = attempt.requestId().getAsInt();
            this.operationId = null;
            this.lastRequestId = this.requestId;
            this.sent.put(this.requestId, commit.get());
            this.active.answered(new Answer.Sent(this.requestId));
        } else {
            this.active.answered(new Answer.NotSent(attempt.code().orElse(Protocol.INTERNAL_ERROR), attempt.args()));
        }
    }

    private <P> EditorClient.Attempt send(Commit<P> commit) {
        return this.environment.send(commit.module(), commit.params(), commit.plan(), commit.sideEffects());
    }

    /** Passes an answer to the tool that sent the request; it stays in flight. */
    private void tell(Answer answer, boolean refreshPreview) {
        Tool tool = this.requester;
        if (tool == this.active) {
            tool.answered(answer);
            sendCommit();
            refresh(refreshPreview);
        }
    }

    /** Passes the request's last answer to the tool that sent it. */
    private void finish(Answer answer, boolean refreshPreview) {
        Tool tool = this.requester;
        dropRequest();
        if (tool == this.active) {
            tool.answered(answer);
            sendCommit();
            refresh(refreshPreview);
        }
    }

    private void dropRequest() {
        this.requester = null;
        this.requestId = NONE;
        this.operationId = null;
    }

    /**
     * Shows the active tool's output if it changed (or, with {@code force}, works it out and draws it afresh). A plan job
     * goes to the tool planner thread; handles alone are shown at once.
     */
    private void refresh(boolean force) {
        ToolOutput output = this.active.output();
        if (!force && output.equals(this.shown)) {
            return;
        }
        boolean handlesOnly = !force && output.job().isPresent() && output.job().equals(this.shown.job());
        this.shown = output;
        if (handlesOnly) {
            // Same plan, other handles: redraw them now. A plan still being worked out takes them when it arrives.
            if (this.shownModel != null) {
                this.shownModel = this.shownModel.withHandles(output.handles());
                this.preview.show(this.shownModel);
            }
            return;
        }
        this.shownModel = null;
        this.generation++;
        stopPlanning();
        if (output.job().isEmpty()) {
            if (output.handles().isEmpty()) {
                this.preview.clear();
            } else {
                this.preview.show(PreviewModel.of(NO_BLOCKS, output.handles()));
            }
            return;
        }
        if (force) {
            this.preview.clear(); // so every section is drawn afresh
        }
        PlanJob job = output.job().get();
        List<Handle> handles = output.handles();
        WorldLimits limits = this.environment.limits();
        long ticket = this.generation;
        Tool tool = this.active;
        this.planning = this.environment.background(() -> work(ticket, tool, job, handles, limits));
    }

    private void stopPlanning() {
        if (this.planning != null) {
            this.planning.cancel(true);
            this.planning = null;
        }
    }

    /** On the tool planner thread: works {@code job} out and builds its preview, then hands both to the client thread. */
    private void work(long ticket, Tool tool, PlanJob job, List<Handle> handles, WorldLimits limits) {
        Planned planned;
        PreviewModel model;
        try {
            planned = job.run(limits);
            BlockPlan drawn = planned.result().plan().or(planned::refusedShape).orElse(NO_BLOCKS);
            model = PreviewModel.of(drawn, handles, planned.isPlanned() && !planned.removal() ? PreviewModel.Style.NORMAL : PreviewModel.Style.REFUSED);
        } catch (CancellationException e) {
            return; // a newer job replaced this one
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            this.environment.problem("Working out a plan for the tool " + tool.id() + " failed", e);
            planned = Planned.of(PlanResult.refused(Protocol.INTERNAL_ERROR));
            model = PreviewModel.of(NO_BLOCKS, handles, PreviewModel.Style.REFUSED);
        }
        Planned answer = planned;
        PreviewModel answerModel = model;
        this.environment.onClientThread(() -> deliver(ticket, tool, job, answer, answerModel));
    }

    /** On the client thread: shows a background answer, unless something newer has replaced it. */
    private void deliver(long ticket, Tool tool, PlanJob job, Planned planned, PreviewModel model) {
        if (ticket != this.generation || tool != this.active) {
            return;
        }
        this.planning = null;
        tool.planned(job, planned);
        this.shownModel = model.handles().equals(this.shown.handles()) ? model : model.withHandles(this.shown.handles());
        this.preview.show(this.shownModel);
        update();
    }
}
