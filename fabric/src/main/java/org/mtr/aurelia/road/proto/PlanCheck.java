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
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.undo.Replacement;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * The server's decision on a plan request (editor-protocol §5): a planner refusal passes through; otherwise the
 * server's own plan is accepted only if its hash equals the client's, and refused with {@code PLAN_HASH_MISMATCH} and
 * the server's hash if not. Pure, so it can run off the server thread.
 */
public final class PlanCheck {
    /**
     * Either the plan to apply, or a refusal (code and arguments). {@code serverPlanHash} is the server's hash whenever it
     * made a plan: the accepted plan's, or the one that didn't match ({@code PlanRejected.serverPlanHash}). An accepted
     * plan may have an only-where plan (R-6, {@link PlanResult#onlyWhere}); the hash covers the plan, and the server
     * works out both from the same parameters. From T-2 it may have side effects (track), which the hash covers too.
     * From R-7 it may replace a placed road (a delete or rebuild), which the hash doesn't cover: the client can't know it.
     */
    public record Decision(Optional<BlockPlan> plan, Optional<Digest> serverPlanHash, Optional<String> code, Map<String, String> args,
            Optional<BlockPlan> onlyWhere, List<SideEffect> sideEffects, Optional<Replacement> replacement) {
        public Decision {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(serverPlanHash, "serverPlanHash");
            Objects.requireNonNull(code, "code");
            if (plan.isPresent() == code.isPresent()) {
                throw new IllegalArgumentException("A decision has either a plan or a refusal code");
            }
            args = Fields.args(args);
            Objects.requireNonNull(onlyWhere, "onlyWhere");
            sideEffects = List.copyOf(sideEffects);
            Objects.requireNonNull(replacement, "replacement");
        }

        public Decision(Optional<BlockPlan> plan, Optional<Digest> serverPlanHash, Optional<String> code, Map<String, String> args) {
            this(plan, serverPlanHash, code, args, Optional.empty(), List.of(), Optional.empty());
        }

        public boolean isAccepted() {
            return this.plan.isPresent();
        }
    }

    private PlanCheck() {
    }

    /** The decision on {@code planned}, given the hash the client sent and the server's core version. */
    public static Decision check(PlanResult planned, Digest clientHash, String coreVersion) {
        Objects.requireNonNull(clientHash, "clientHash");
        if (!planned.isPlanned()) {
            return new Decision(Optional.empty(), Optional.empty(), planned.code(), planned.args());
        }
        BlockPlan plan = planned.plan().orElseThrow();
        Digest serverHash = Digest.of(PlanHash.of(plan, planned.sideEffects(), coreVersion));
        if (!serverHash.equals(clientHash)) {
            return new Decision(Optional.empty(), Optional.of(serverHash), Optional.of(Protocol.PLAN_HASH_MISMATCH), Map.of());
        }
        return new Decision(Optional.of(plan), Optional.of(serverHash), Optional.empty(), Map.of(), planned.onlyWhere(), planned.sideEffects(),
                planned.replacement());
    }
}
