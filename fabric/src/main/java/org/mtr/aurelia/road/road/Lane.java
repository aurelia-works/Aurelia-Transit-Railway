/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

/**
 * One lane of a profile: its index (0 is the leftmost), its centre's offset from the reference line in half blocks
 * (positive to the right, spec roads-v1 §1), and the direction its traffic runs.
 */
public record Lane(int index, int offsetHalfBlocks, Direction direction) {
    /**
     * Which way traffic runs, authored for left-hand traffic (UK): lanes left of the reference line run with the
     * reference direction, lanes right of it against it, and a lane on the line (an alley) is shared. Mirroring for
     * right-hand traffic swaps them (§11.4).
     */
    public enum Direction {
        WITH_REFERENCE,
        AGAINST_REFERENCE,
        BOTH;

        static Direction of(int offsetHalfBlocks) {
            return offsetHalfBlocks < 0 ? WITH_REFERENCE : offsetHalfBlocks > 0 ? AGAINST_REFERENCE : BOTH;
        }

        /**
         * The way traffic runs on this lane when the road is driven on {@code side} (§11.4): as authored for left-hand
         * traffic, swapped for right-hand traffic. A shared lane stays shared.
         */
        public Direction on(RoadPlanParams.DrivingSide side) {
            if (side == RoadPlanParams.DrivingSide.LHT) {
                return this;
            }
            return switch (this) {
                case WITH_REFERENCE -> AGAINST_REFERENCE;
                case AGAINST_REFERENCE -> WITH_REFERENCE;
                case BOTH -> BOTH;
            };
        }
    }

    /** The offset {@code d} in blocks: a whole number for odd-width profiles, a half (x.5) for even widths. */
    public double offset() {
        return this.offsetHalfBlocks / 2.0;
    }
}
