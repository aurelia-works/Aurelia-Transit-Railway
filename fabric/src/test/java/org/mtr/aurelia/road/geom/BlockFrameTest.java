/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The block-centre frame: which block a point is in (edges and negative coordinates included), whole-block steps,
 * and the along/sideways frame of spec roads-v1 §4.2.
 */
class BlockFrameTest {
    @Test
    void aPointOnABlockEdgeBelongsToTheBlockWithTheLargerIndex() {
        for (int edge2 = -5; edge2 <= 3; edge2 += 2) { // the edges -2.5, -1.5, ..., +1.5
            double edge = edge2 / 2.0;
            int above = (int) StrictMath.floor(edge) + 1;
            assertEquals(above, new Vec2(edge, 0).block().x(), "on the edge " + edge);
            assertEquals(above, new Vec2(StrictMath.nextUp(edge), 0).block().x(), "just above " + edge);
            assertEquals(above - 1, new Vec2(StrictMath.nextDown(edge), 0).block().x(), "just below " + edge);
            assertEquals(above, new Vec2(0, edge).block().z(), "z on the edge " + edge);
        }
        // Negative coordinates round down, never towards 0: -0.6 is in block -1, -0.5 in block 0.
        assertEquals(new BlockXZ(-1, -1), new Vec2(-0.6, -0.6).block());
        assertEquals(new BlockXZ(0, 0), new Vec2(-0.5, -0.5).block());
        assertEquals(new BlockXZ(0, 0), new Vec2(0.49, -0.49).block());
        // The trap in floor(x + 0.5): just below 0.5, that sum rounds up to exactly 1.0.
        assertEquals(new BlockXZ(0, 0), new Vec2(StrictMath.nextDown(0.5), StrictMath.nextDown(0.5)).block());
    }

    @Test
    void blockCentresRoundTripNearTheWorldBorder() {
        for (int x : new int[] {-29_999_999, -29_999_998, -1, 0, 1, 29_999_998, 29_999_999}) {
            BlockXZ block = new BlockXZ(x, -x);
            assertEquals(block, block.centre().block(), block.toString());
            assertEquals(new BlockXZ(x + 1, -x), new Vec2(x + 0.5, -x).block(), "the edge above " + x);
            assertEquals(block, new Vec2(StrictMath.nextDown(x + 0.5), -x).block(), "just below the edge above " + x);
        }
        assertEquals(29_999_999.5, new BlockXZ(29_999_999, 0).centre().worldX());
        assertEquals(-29_999_998.5, new BlockXZ(0, -29_999_999).centre().worldZ());
    }

    @Test
    void vectorsRejectNonFiniteNumbersAndStoreMinusZeroAsZero() {
        assertThrows(IllegalArgumentException.class, () -> new Vec2(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Vec2(0, Double.POSITIVE_INFINITY));
        assertEquals(new Vec2(0, 1), new Vec2(-0.0, 1));
        assertEquals(new Vec2(0, 1), new Vec2(1, 0).rotateQuarterClockwise());
        assertEquals(new Vec2(0, 0), new Vec2(0, 0).times(-1));
        assertThrows(IllegalArgumentException.class, () -> new Vec2(3e9, 0).block());
    }

    @Test
    void vectorBasics() {
        Vec2 a = new Vec2(3, 4);
        assertEquals(5, a.length());
        assertEquals(new Vec2(4, 6), a.plus(new Vec2(1, 2)));
        assertEquals(new Vec2(2, 2), a.minus(new Vec2(1, 2)));
        assertEquals(new Vec2(6, 8), a.times(2));
        assertEquals(11, a.dot(new Vec2(1, 2)));
        assertEquals(5, a.distanceTo(Vec2.ZERO));
        assertEquals(new Vec2(-4, 3), a.rotateQuarterClockwise());
        assertEquals(new Vec2(3, -4), a.mirrored());
        assertTrue(new Vec2(1, 0).cross(new Vec2(0, 1)) > 0, "south is clockwise of east");
        assertTrue(new Vec2(1, 0).cross(new Vec2(0, -1)) < 0, "north is anticlockwise of east");
    }

    @Test
    void wholeBlockSteps() {
        // Spec roads-v1 §5.1: heading i + 4 uses the step of heading i turned 90°, (x, z) -> (-z, x).
        BlockStep step = new BlockStep(-1, 5);
        assertEquals(new BlockStep(-5, -1), step.rotateQuarterClockwise());
        assertEquals(step, step.rotateQuarterClockwise().rotateQuarterClockwise().rotateQuarterClockwise().rotateQuarterClockwise());
        assertEquals(new BlockStep(-1, -5), step.mirrored());
        assertEquals(new BlockStep(-3, 15), step.times(3));
        assertEquals(new Vec2(-1, 5), step.toVec2());
        assertEquals(step.toVec2().rotateQuarterClockwise(), step.rotateQuarterClockwise().toVec2());

        BlockXZ node = new BlockXZ(-7, 12);
        assertEquals(new BlockXZ(-8, 17), node.plus(step));
        assertEquals(step, node.stepTo(node.plus(step)));
        assertEquals(node.centre().plus(step.toVec2()), node.plus(step).centre(), "a step from a block centre lands on a block centre");

        assertThrows(ArithmeticException.class, () -> new BlockXZ(Integer.MAX_VALUE, 0).plus(new BlockStep(1, 0)));
        assertThrows(ArithmeticException.class, () -> new BlockStep(Integer.MAX_VALUE, 0).times(2));
        assertThrows(ArithmeticException.class, () -> new BlockStep(0, Integer.MIN_VALUE).mirrored());
    }

    @Test
    void frameMatchesSection42OnHandWorkedCases() {
        Frame east = new Frame(Vec2.ZERO, Heading.E);
        assertEquals(3, east.along(new Vec2(3, 2)));
        assertEquals(2, east.side(new Vec2(3, 2)), "south of an eastbound driver is to their right");

        Frame south = new Frame(new Vec2(10, 10), Heading.S);
        assertEquals(2, south.along(new Vec2(13, 12)));
        assertEquals(-3, south.side(new Vec2(13, 12)), "east of a southbound driver is to their left");

        Frame ese = new Frame(new Vec2(-4, 7), Heading.ESE);
        Vec2 point = ese.origin().plus(Heading.ESE.tangent().times(10)).plus(Heading.ESE.normal().times(4));
        assertEquals(10, ese.along(point), 1e-12);
        assertEquals(4, ese.side(point), 1e-12);
        assertEquals(point.x(), ese.point(10, 4).x(), 1e-12);
        assertEquals(point.z(), ese.point(10, 4).z(), 1e-12);
    }

    @Test
    void framePointRoundTripsForRandomPointsAndEveryHeading() {
        Random random = new Random(20261004L);
        for (Heading heading : Heading.values()) {
            for (int i = 0; i < 500; i++) {
                Frame frame = new Frame(new Vec2(random.nextInt(60_000) - 30_000, random.nextInt(60_000) - 30_000), heading);
                Vec2 p = frame.origin().plus(new Vec2(random.nextDouble() * 1024 - 512, random.nextDouble() * 1024 - 512));
                Vec2 back = frame.point(frame.along(p), frame.side(p));
                assertEquals(p.x(), back.x(), 1e-9, heading + " " + p);
                assertEquals(p.z(), back.z(), 1e-9, heading + " " + p);
            }
        }
        // On the cardinals the frame is exact: a whole-block point gives whole-number s and d.
        Frame north = new Frame(new BlockXZ(5, 5).centre(), Heading.N);
        assertEquals(3, north.along(new Vec2(4, 2)));
        assertEquals(-1, north.side(new Vec2(4, 2)));
    }
}
