/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.bake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * Bake to vanilla's plan (task B-3; ADR-0044): the Stratos blocks found in a world, each turned into its vanilla block
 * ({@link BakeMap}) where it stands. It is applied as an ordinary operation, so undo puts every Stratos block back, and
 * only where the Stratos block is still there ({@link #expected}: a block changed since it was found is left).
 */
public final class Bake {
    /** A Stratos block found in a world: where, and its state. */
    public record Found(int x, int y, int z, String state) {
        public Found {
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * The plan: {@code writes}, each found block's vanilla state; {@code expected}, its state as found; {@code unknown},
     * the states of Stratos blocks this Stratos has no vanilla block for (left as they are), each once, sorted.
     */
    public record Plan(BlockPlan writes, BlockPlan expected, List<String> unknown) {
        public Plan {
            Objects.requireNonNull(writes, "writes");
            Objects.requireNonNull(expected, "expected");
            unknown = List.copyOf(unknown);
        }

        /** How many blocks it bakes. */
        public int blockCount() {
            return this.writes.blockCount();
        }
    }

    private Bake() {
    }

    /** The plan that bakes {@code found} in a world of {@code dataVersion}; empty when there is nothing to bake. */
    public static Optional<Plan> plan(int dataVersion, Collection<Found> found) {
        BlockPlan.Builder writes = BlockPlan.builder(dataVersion);
        BlockPlan.Builder expected = BlockPlan.builder(dataVersion);
        TreeSet<String> unknown = new TreeSet<>();
        int count = 0;
        for (Found block : found) {
            Optional<String> vanilla = BakeMap.vanilla(block.state());
            if (vanilla.isEmpty()) {
                if (BakeMap.isStratos(block.state())) {
                    unknown.add(block.state());
                }
                continue;
            }
            writes.set(block.x(), block.y(), block.z(), vanilla.get());
            expected.set(block.x(), block.y(), block.z(), block.state());
            count++;
        }
        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(new Plan(writes.build(), expected.build(), new ArrayList<>(unknown)));
    }
}
