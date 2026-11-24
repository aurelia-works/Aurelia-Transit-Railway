/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;

/**
 * Where a lane's node sits relative to the centre-line node (spec roads-v1 §5.1): the lane's exact offset
 * {@code d · n(θ)}, rounded to the nearest block, the same either side (owner, 2026-10-06: task G-5). Every lane node is
 * then a block centre.
 * <ul>
 *   <li>Cardinal headings: exact, such as (0, 3) for City street's right lane heading east.</li>
 *   <li>45°: City street's lanes at (∓2, ±2), 5.66 apart.</li>
 *   <li>22.5°: City street's lanes at (∓1, ±3) and its rotations, 6.32 apart.</li>
 * </ul>
 * The centre line stays exactly midway between a profile's mirror-image lanes, and right-hand traffic mirrors cleanly.
 * Spike S6 re-checks the offsets with real MTR rails.
 */
public final class LaneOffsets {
    private LaneOffsets() {
    }

    /** The step from the centre-line node to the node of a lane {@code offset} blocks to the right (negative: left). */
    public static BlockStep of(Heading heading, double offset) {
        Vec2 normal = heading.normal();
        return new BlockStep(round(offset * normal.x()), round(offset * normal.z()));
    }

    private static int round(double value) {
        return (int) StrictMath.rint(value);
    }
}
