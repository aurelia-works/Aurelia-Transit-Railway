/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.util.Objects;

/**
 * Something an operation changes outside the world's blocks, which undo removes and redo makes again (task G-9). The
 * platform carries it out through a {@link org.mtr.aurelia.road.spi.SideEffectWriter}; core only describes it.
 */
public sealed interface SideEffect permits SideEffect.TrackRail {
    /**
     * Hidden MTR track between two lane nodes (ADR-0040; task T-2): the node blocks (where MTR's node sits, on top of the
     * road surface), their MTR headings (0–7, {@code LaneNode.mtrFacing}), the speed for a vehicle setting off from each
     * end in km/h, 0 for "not this way" (one way with traffic; spike S14), and the road's vertical shape with its radius
     * (0 when it has none), so sloped track follows the road (G-3). The track writer makes it an MTR rail with no
     * style. Core knows nothing of MTR.
     */
    record TrackRail(int ax, int ay, int az, int aFacing, int bx, int by, int bz, int bFacing, int speedFromA, int speedFromB, Shape shape,
            double radius) implements SideEffect {
        /** The rail's vertical shape, as MTR's {@code Rail.Shape} has it (the road's, roads-v1 §6.1). */
        public enum Shape {
            QUADRATIC,
            TWO_RADII
        }

        public TrackRail {
            if (aFacing < 0 || aFacing > 7 || bFacing < 0 || bFacing > 7) {
                throw new IllegalArgumentException("MTR headings are 0 to 7, not " + aFacing + " and " + bFacing);
            }
            if (speedFromA < 0 || speedFromB < 0 || speedFromA == 0 && speedFromB == 0) {
                throw new IllegalArgumentException("A rail needs a speed one way at least, not " + speedFromA + "/" + speedFromB);
            }
            if (ax == bx && ay == by && az == bz) {
                throw new IllegalArgumentException("A rail joins two different nodes");
            }
            Objects.requireNonNull(shape, "shape");
            if (!(Double.isFinite(radius) && radius >= 0)) {
                throw new IllegalArgumentException("A rail's vertical radius is 0 or more, not " + radius);
            }
        }

        /** The faster of its two speeds: the speed of the MTR rail type it is made as. */
        public int speed() {
            return StrictMath.max(this.speedFromA, this.speedFromB);
        }
    }
}
