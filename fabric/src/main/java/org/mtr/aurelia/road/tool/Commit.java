/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.List;
import java.util.Objects;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.proto.PlanModule;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * A plan the player confirmed (task R-2), for the session to send: the module, its parameters, the plan the preview
 * shows and, from T-2, its side effects (track). The session sends their hash, so nothing is worked out again on the
 * client thread.
 *
 * @param <P> the module's parameter record
 */
public record Commit<P>(PlanModule<P> module, P params, BlockPlan plan, List<SideEffect> sideEffects) {
    public Commit {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(plan, "plan");
        sideEffects = List.copyOf(sideEffects);
    }

    /** A commit without side effects. */
    public Commit(PlanModule<P> module, P params, BlockPlan plan) {
        this(module, params, plan, List.of());
    }
}
