/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom.mtr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;

/** The link between Stratos's headings and MTR's angles (G-1 decision), and the MtrRails adapter. */
class MtrRailsTest {
    @Test
    void headingIIsMtrAngleI() {
        for (Heading heading : Heading.values()) {
            Angle angle = MtrRails.angle(heading);
            org.mtr.core.tool.Angle mtr = org.mtr.core.tool.Angle.values()[heading.index()];
            assertSame(Angle.values()[heading.index()], angle);
            assertSame(heading, MtrRails.heading(angle));
            assertEquals(mtr.name(), angle.name(), heading.name());

            // The ported angle is MTR's, bit for bit, except tan and halfTan, which may differ by 1 ulp under
            // StrictMath (spike S5: SWW and NWW).
            assertEquals(mtr.angleDegrees, angle.angleDegrees, heading.name());
            assertEquals(mtr.angleRadians, angle.angleRadians, heading.name());
            assertEquals(mtr.sin, angle.sin, heading.name());
            assertEquals(mtr.cos, angle.cos, heading.name());
            assertEquals(mtr.tan, angle.tan, StrictMath.ulp(mtr.tan), heading.name());
            assertEquals(mtr.halfTan, angle.halfTan, StrictMath.ulp(mtr.halfTan), heading.name());

            // Stratos's heading points the same way: exact at the cardinals, MTR's values elsewhere.
            if (heading.isCardinal()) {
                assertEquals(angle.cos, heading.cos(), 1e-15, heading.name());
                assertEquals(angle.sin, heading.sin(), 1e-15, heading.name());
            } else {
                assertEquals(angle.cos, heading.cos(), heading.name());
                assertEquals(angle.sin, heading.sin(), heading.name());
            }
        }
    }

    @Test
    void positionsRoundTripThroughMtr() {
        BlockXZ start = new BlockXZ(-1200, 340);
        Position position = MtrRails.position(start, 64);
        assertEquals(-1200, position.getX());
        assertEquals(64, position.getY());
        assertEquals(340, position.getZ());

        // A straight east from that block: MTR's position at distance 0 is the block centre (+0.5 in world terms),
        // which is the block itself in the block-centre frame.
        RailMath straight = new RailMath(position, MtrRails.angle(Heading.E), MtrRails.position(new BlockXZ(-1190, 340), 64),
                MtrRails.angle(Heading.E), Rail.Shape.QUADRATIC, 0);
        assertTrue(MtrRails.isValid(straight));
        assertEquals(10, straight.getLength(), 1e-12);
        Vec2 first = MtrRails.horizontal(straight.getPosition(0, false));
        assertEquals(start.centre(), first);
        assertEquals(start, first.block());
        assertEquals(new BlockXZ(-1190, 340), MtrRails.horizontal(straight.getPosition(10, false)).block());
    }

    @Test
    void invalidRailsAreReported() {
        // Spike S5's example: parallel headings with the end beside the start, so MTR can't build it.
        RailMath beside = new RailMath(new Position(12758, 64, -24421), Angle.E, new Position(12758, 72, -24427), Angle.E,
                Rail.Shape.QUADRATIC, 0);
        assertFalse(MtrRails.isValid(beside));
        assertEquals(0, beside.getLength());
    }
}
