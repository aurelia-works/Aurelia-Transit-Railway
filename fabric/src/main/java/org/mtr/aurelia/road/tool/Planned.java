/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.Objects;
import java.util.Optional;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.proto.PlanResult;

/**
 * A {@link PlanJob}'s answer: the planner's result (a plan with its warnings, or a refusal) and, for a refusal that still
 * has a shape (such as a road crossing the world border), the blocks to draw in red (roads-v1 §12). From R-7 a plan that
 * removes what it shows (a road being deleted) is a {@code removal}, also drawn in red.
 */
public record Planned(PlanResult result, Optional<BlockPlan> refusedShape, boolean removal) {
    public Planned {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(refusedShape, "refusedShape");
        if (result.isPlanned() && refusedShape.isPresent()) {
            throw new IllegalArgumentException("Only a refused plan has a refused shape");
        }
    }

    /** An answer that isn't a removal. */
    public Planned(PlanResult result, Optional<BlockPlan> refusedShape) {
        this(result, refusedShape, false);
    }

    public static Planned of(PlanResult result) {
        return new Planned(result, Optional.empty());
    }

    /** {@code result}, whose plan is what will be removed (R-7: a delete), drawn in red. */
    public static Planned removal(PlanResult result) {
        return new Planned(result, Optional.empty(), true);
    }

    public boolean isPlanned() {
        return this.result.isPlanned();
    }
}
