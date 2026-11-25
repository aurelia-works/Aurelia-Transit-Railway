/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import java.util.Objects;
import org.mtr.aurelia.road.geom.Heading;

/**
 * Where a player puts an {@code mtr:rail} node for hidden track along a lane (spec roads-v1 §9; task G-8): the lane's
 * number (1 is the leftmost), the block, and the heading the lane runs at that node.
 */
public record LaneNode(int lane, int x, int y, int z, Heading heading) {
    public LaneNode {
        if (lane < 1) {
            throw new IllegalArgumentException("Lanes are numbered from 1, not " + lane);
        }
        Objects.requireNonNull(heading, "heading");
    }

    /**
     * The heading as an MTR node stores it: modulo 180° (nodes are direction-less; ADR-0033), as a heading index 0–7
     * (east, ESE, … SSW). The node checker (R-6) compares this.
     */
    public int mtrFacing() {
        return this.heading.index() % 8;
    }

    /**
     * The block the {@code mtr:rail} node goes in: the one above the surface block {@link #y()} (owner, 2026-10-06:
     * spike S6a), so the rail runs along the road's top face. Node ends are always on whole blocks (G-7).
     */
    public int nodeY() {
        return this.y + 1;
    }
}
