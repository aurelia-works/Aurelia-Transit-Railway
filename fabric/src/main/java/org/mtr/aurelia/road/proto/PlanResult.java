/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.undo.Replacement;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * What a module's planner made of a request: a block plan, or a refusal code (such as roads-v1 §12's {@code TOO_SHORT})
 * with the arguments its message needs, in order. A plan may come with warnings (roads-v1 §12's "warn" rows, such as
 * {@code STEP_22_5}): they don't stop it, and only the tool's HUD shows them (R-2). The same code runs on the client (for
 * the preview) and on the server (before applying).
 *
 * <p>From R-6 a plan may say where it applies: {@code onlyWhere} is a plan with exactly the same positions, giving the
 * state each block must have now to be written; any block in another state is left as it is (the apply engine's
 * {@code SKIP_CHANGED} guard, as undo uses). Clearing node markers writes only where the wool still is.
 *
 * <p>From T-2 a plan may have side effects: the hidden MTR track its road carries (ADR-0040), made once its blocks are
 * placed. The plan hash covers them, so the client's preview and the server's road agree on the track too.
 *
 * <p>From R-7 a server's plan may replace a placed road ({@link #replacement}: a delete or rebuild). It says which of the
 * road's operations hold its blocks, so the server builds the job from their undo images; the client never has one, and
 * the hash doesn't cover it.
 */
public record PlanResult(Optional<BlockPlan> plan, Optional<String> code, Map<String, String> args, List<Warning> warnings,
        Optional<BlockPlan> onlyWhere, List<SideEffect> sideEffects, Optional<Replacement> replacement) {
    /** Something the player should know about a plan that is still placed as it is: a code and its arguments, in order. */
    public record Warning(String code, Map<String, String> args) {
        public Warning {
            Fields.name(code, "code");
            args = Fields.args(args);
        }
    }

    public PlanResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(code, "code");
        if (plan.isPresent() == code.isPresent()) {
            throw new IllegalArgumentException("A plan result has either a plan or a refusal code");
        }
        args = Fields.args(args);
        code.ifPresent(c -> Fields.name(c, "code"));
        warnings = List.copyOf(warnings);
        if (code.isPresent() && !warnings.isEmpty()) {
            throw new IllegalArgumentException("A refusal has no warnings");
        }
        Objects.requireNonNull(onlyWhere, "onlyWhere");
        if (onlyWhere.isPresent() && (plan.isEmpty() || !samePositions(plan.get(), onlyWhere.get()))) {
            throw new IllegalArgumentException("Only a plan has an only-where plan, with exactly the plan's positions");
        }
        sideEffects = List.copyOf(sideEffects);
        if (code.isPresent() && !sideEffects.isEmpty()) {
            throw new IllegalArgumentException("A refusal has no side effects");
        }
        Objects.requireNonNull(replacement, "replacement");
        if (code.isPresent() && replacement.isPresent()) {
            throw new IllegalArgumentException("A refusal replaces nothing");
        }
    }

    /** A result that replaces no road. */
    public PlanResult(Optional<BlockPlan> plan, Optional<String> code, Map<String, String> args, List<Warning> warnings,
            Optional<BlockPlan> onlyWhere, List<SideEffect> sideEffects) {
        this(plan, code, args, warnings, onlyWhere, sideEffects, Optional.empty());
    }

    /** The same plan, replacing the road {@code replacement} describes (R-7: the server's delete or rebuild). */
    public PlanResult withReplacement(Replacement replacement) {
        return new PlanResult(this.plan, this.code, this.args, this.warnings, this.onlyWhere, this.sideEffects, Optional.of(replacement));
    }

    /** A result without side effects. */
    public PlanResult(Optional<BlockPlan> plan, Optional<String> code, Map<String, String> args, List<Warning> warnings,
            Optional<BlockPlan> onlyWhere) {
        this(plan, code, args, warnings, onlyWhere, List.of());
    }

    /** A result without an only-where plan or side effects. */
    public PlanResult(Optional<BlockPlan> plan, Optional<String> code, Map<String, String> args, List<Warning> warnings) {
        this(plan, code, args, warnings, Optional.empty());
    }

    public static PlanResult planned(BlockPlan plan) {
        return planned(plan, List.of());
    }

    public static PlanResult planned(BlockPlan plan, List<Warning> warnings) {
        return planned(plan, warnings, List.of());
    }

    /** {@code plan} with its warnings and the side effects to carry out once its blocks are placed (T-2: track). */
    public static PlanResult planned(BlockPlan plan, List<Warning> warnings, List<SideEffect> sideEffects) {
        return new PlanResult(Optional.of(plan), Optional.empty(), Map.of(), warnings, Optional.empty(), sideEffects);
    }

    /** {@code plan}, written only where each block is now in its state in {@code onlyWhere} (R-6). */
    public static PlanResult plannedWhere(BlockPlan plan, BlockPlan onlyWhere) {
        return new PlanResult(Optional.of(plan), Optional.empty(), Map.of(), List.of(), Optional.of(onlyWhere));
    }

    public static PlanResult refused(String code) {
        return refused(code, Map.of());
    }

    public static PlanResult refused(String code, Map<String, String> args) {
        return new PlanResult(Optional.empty(), Optional.of(code), args, List.of());
    }

    public boolean isPlanned() {
        return this.plan.isPresent();
    }

    private static boolean samePositions(BlockPlan a, BlockPlan b) {
        List<BlockPlan.Block> first = a.blocks();
        List<BlockPlan.Block> second = b.blocks();
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            BlockPlan.Block x = first.get(i);
            BlockPlan.Block y = second.get(i);
            if (x.x() != y.x() || x.y() != y.y() || x.z() != y.z()) {
                return false;
            }
        }
        return true;
    }
}
