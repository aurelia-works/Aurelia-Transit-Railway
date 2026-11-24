/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * An editor tool (spec mod-suite-v1 §6.2): a state machine driven by {@link ToolInput}s, with its output (shown through
 * the preview) and its HUD. A tool turns the player's input into a plan request's parameters; core turns those into
 * a block plan. Client thread only.
 *
 * <p>From R-2 a tool can place what it shows: its output names a {@link PlanJob}, which the session works out in the
 * background and hands back ({@link #planned}); when the player confirms, the tool offers a {@link Commit}
 * ({@link #takeCommit}), which the session sends, and the session reports what became of it ({@link #answered}).
 */
public interface Tool {
    /** A stable id, {@code <module>.<name>}, such as {@code roads.straight}. */
    String id();

    /** The module whose hub tab lists the tool, such as {@code roads}. */
    String moduleId();

    /** The lang key of the tool's name. */
    String titleKey();

    ToolState state();

    void input(ToolInput input);

    ToolOutput output();

    HudModel hud();

    /**
     * The bindings this tool ever uses (R-6): the HUD draws only the key rows that hold one of them. Every binding by
     * default.
     */
    default Set<HudModel.Action> keys() {
        return EnumSet.allOf(HudModel.Action.class);
    }

    /**
     * Back to {@link ToolState#IDLE}: anything half-placed is dropped, settings (such as a chosen profile) are kept.
     * Called whenever the tool is put away or another is chosen (R-1's round-trip rule).
     */
    void reset();

    /** The answer to {@code job}, which is still the job in the tool's {@link #output}. */
    default void planned(PlanJob job, Planned planned) {
    }

    /** The plan the player has just confirmed, if any; the session sends it at once. Each commit is returned only once. */
    default Optional<Commit<?>> takeCommit() {
        return Optional.empty();
    }

    /**
     * The world task the player has just asked for, if any (B-3: bake a road to vanilla); the session sends it at once,
     * like a commit, and its answers come back the same way. Each is returned only once.
     */
    default Optional<WorldTask> takeTask() {
        return Optional.empty();
    }

    /** What became of the tool's last commit. Only arrives while the tool hasn't been reset since. */
    default void answered(Answer answer) {
    }

    /**
     * The question the tool asks now, if any (R-7): the platform shows it in a box, and sends the player's pick as
     * {@link ToolInput.Choose}. None by default.
     */
    default Optional<Question> question() {
        return Optional.empty();
    }
}
