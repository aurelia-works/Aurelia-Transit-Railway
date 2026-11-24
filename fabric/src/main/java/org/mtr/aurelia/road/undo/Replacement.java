/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * What a delete or a rebuild replaces (task R-7; owner, 2026-10-09), as its module works it out from the records: the
 * operations an {@link Operations#replace} needs to build its job from their undo images.
 *
 * @param road     the road's id (the operation that first placed it), for the log
 * @param chain    the road's operations whose undo images hold its blocks, oldest first: placing it, then each rebuild
 *                 still applied
 * @param asPlaced the road's plan as placed, when today's planner gives exactly the plan its latest operation placed (its
 *                 plan hash): every block of it then has an expected state, changed when placed or not
 * @param firstPlaced the plan of the operation that first placed the road, when today's planner gives exactly it: a block of
 *                 it that operation didn't change already had its planned state, so that is the ground there, even where a
 *                 later rebuild changed it (a hand edit it overwrote)
 * @param keep     the positions ({@link org.mtr.aurelia.road.ops.BlockChange#positionKey}) of the roads joined to
 *                 it, which a delete or rebuild leaves as they are (owner: they keep what they share)
 * @param delete   true for a delete: nothing new is written, the road's blocks go back to the ground
 * @param guard    what the job does with blocks not as the road left them: {@link ApplyJob.Guard#ASK} (no answer yet:
 *                 count them and write nothing), {@link ApplyJob.Guard#SKIP_CHANGED} (keep them) or
 *                 {@link ApplyJob.Guard#NONE} (overwrite them)
 */
public record Replacement(UUID road, List<UUID> chain, Optional<BlockPlan> asPlaced, Optional<BlockPlan> firstPlaced, Set<Long> keep, boolean delete,
        ApplyJob.Guard guard) {
    public Replacement {
        Objects.requireNonNull(road, "road");
        chain = List.copyOf(chain);
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("A road has at least the operation that placed it");
        }
        Objects.requireNonNull(asPlaced, "asPlaced");
        Objects.requireNonNull(firstPlaced, "firstPlaced");
        keep = Set.copyOf(keep);
        Objects.requireNonNull(guard, "guard");
        if (guard == ApplyJob.Guard.REFUSE_CHANGED) {
            throw new IllegalArgumentException("A delete or rebuild asks, keeps or overwrites");
        }
    }

    /** The operation that built the road last: its record holds the side effects the road has now. */
    public UUID latest() {
        return this.chain.get(this.chain.size() - 1);
    }
}
