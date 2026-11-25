/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;

/** City street's lane offsets on every heading (owner, 2026-10-06: nearest block, symmetric; task G-5). */
class LaneOffsetsTest {
    @Test
    void cityStreetsLanesOnEveryHeading() {
        // The right lane (+3); the left lane is its mirror image through the centre-line node.
        BlockStep[] right = {
            new BlockStep(0, 3), new BlockStep(-1, 3), new BlockStep(-2, 2), new BlockStep(-3, 1),
            new BlockStep(-3, 0), new BlockStep(-3, -1), new BlockStep(-2, -2), new BlockStep(-1, -3),
            new BlockStep(0, -3), new BlockStep(1, -3), new BlockStep(2, -2), new BlockStep(3, -1),
            new BlockStep(3, 0), new BlockStep(3, 1), new BlockStep(2, 2), new BlockStep(1, 3)};
        for (Heading heading : Heading.values()) {
            BlockStep r = LaneOffsets.of(heading, 3);
            BlockStep l = LaneOffsets.of(heading, -3);
            assertEquals(right[heading.index()], r, heading.name());
            assertEquals(new BlockStep(-r.dx(), -r.dz()), l, heading + ": the left lane mirrors the right through the centre line");
            assertEquals(r.rotateQuarterClockwise(), LaneOffsets.of(heading.rotate(4), 3), heading + ": turning 90° turns the offset");
            double spacing = StrictMath.hypot(r.dx() - l.dx(), r.dz() - l.dz());
            double expected = heading.isCardinal() ? 6 : heading.isDiagonal() ? StrictMath.sqrt(32) : StrictMath.sqrt(40);
            assertEquals(expected, spacing, 1e-12, heading + ": 6, 5.66 or 6.32 apart");
        }
        assertEquals(new BlockStep(0, 0), LaneOffsets.of(Heading.SE, 0), "a lane on the centre line");
    }
}
