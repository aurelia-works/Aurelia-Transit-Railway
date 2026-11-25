/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.util.List;
import java.util.Map;
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
 * @param only     owner, 2026-11-24: the operations of {@code chain} that also built other roads (an edit that moved a
 *                 joined end, and the roads joined there with it), each with the positions of this road's blocks before
 *                 and after it: only those of its undo images are this road's. An operation not named is all the road's.
 *                 Another road's operation named here, not of {@code chain}, holds the ground of blocks this road took over
 *                 from that road: only its images there count, for the ground, and it may have gone.
 * @param rails    the road's own track, when its latest operation also made other roads' (as {@code only}): of that
 *                 record's side effects, only these are the road's; empty when they all are
 * @param joined   the roads this edit takes with it (a moved joined end), each replaced as this one is, in the same job:
 *                 their delete and guard are this one's
 * @param sideSwitch P9: a road's or junction's switch of driving side, which changes its record and its track even when it
 *                 changes no block, so the job doesn't refuse to start for having nothing to write
 */
public record Replacement(UUID road, List<UUID> chain, Optional<BlockPlan> asPlaced, Optional<BlockPlan> firstPlaced, Set<Long> keep, boolean delete,
        ApplyJob.Guard guard, Map<UUID, Set<Long>> only, Optional<List<SideEffect>> rails, List<Replacement> joined, boolean sideSwitch) {
    /** A road whose operations built it alone, replaced on its own. */
    public Replacement(UUID road, List<UUID> chain, Optional<BlockPlan> asPlaced, Optional<BlockPlan> firstPlaced, Set<Long> keep, boolean delete,
            ApplyJob.Guard guard) {
        this(road, chain, asPlaced, firstPlaced, keep, delete, guard, Map.of(), Optional.empty(), List.of(), false);
    }

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
        only = Map.copyOf(only);
        Objects.requireNonNull(rails, "rails");
        rails = rails.map(List::copyOf);
        joined = List.copyOf(joined);
        if (delete && !joined.isEmpty()) {
            throw new IllegalArgumentException("A delete takes no other road with it");
        }
        if (delete && sideSwitch) {
            throw new IllegalArgumentException("A delete doesn't switch a road's side");
        }
    }

    /** This road, then the roads it takes with it ({@link #joined}). */
    public List<Replacement> parts() {
        List<Replacement> parts = new java.util.ArrayList<>();
        parts.add(this);
        parts.addAll(this.joined);
        return parts;
    }

    /** The operation that built the road last: its record holds the side effects the road has now. */
    public UUID latest() {
        return this.chain.get(this.chain.size() - 1);
    }
}
