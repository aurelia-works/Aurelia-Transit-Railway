/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.road.raster.ArcSegment;
import org.mtr.aurelia.road.road.raster.LineSegment;

/** B-2: tracing a line to every block its paint touches, and the chords' distance from the line. */
class LineTracerTest {
    /** A line along the middle of two lanes 6 blocks apart, from {@code start} along {@code direction} (a unit vector). */
    private static MarkingCurve straight(Vec2 start, Vec2 direction, double length) {
        Vec2 side = direction.rotateQuarterClockwise().times(3);
        return new MarkingCurve(new LineSegment(start.minus(side), direction, length), new LineSegment(start.plus(side), direction, length), 0);
    }

    @Test
    void aLineAlongBlockCentresIsOneRowOfStraightChords() {
        List<LineTracer.Traced> traced = LineTracer.trace(straight(Vec2.ZERO, new Vec2(1, 0), 20), LineKind.WHITE);
        int k = LineChords.snap(0.5);
        int across = LineChords.index(LineChords.WEST, k, LineChords.EAST, k);
        for (LineTracer.Traced t : traced) {
            assertEquals(0, t.block().z(), "only the row the line runs along: " + t);
            assertEquals(across, t.chord(), "west to east through the middle: " + t);
            if (t.block().x() >= 0 && t.block().x() <= 20) {
                assertEquals(t.block().x() - 0.55, t.from(), LineTracer.STEP + 1e-9, "the paint touches the block from half a block and its reach before");
                assertEquals(t.block().x() + 0.55, t.to(), LineTracer.STEP + 1e-9, "to half a block and its reach after (the road's own blocks: the walk stops 1.5 past each end)");
            }
        }
        Set<Integer> xs = new HashSet<>();
        traced.forEach(t -> xs.add(t.block().x()));
        for (int x = -2; x <= 22; x++) {
            assertTrue(xs.contains(x), "every block along it, and the carried-on ends: " + x);
        }
    }

    @Test
    void aDiagonalReachesEveryBlockItsPaintTouches() {
        Vec2 direction = new Vec2(1, 1).times(1 / StrictMath.sqrt(2));
        MarkingCurve curve = straight(new Vec2(0.3, -0.2), direction, 30);
        for (LineKind kind : LineKind.values()) {
            List<LineTracer.Traced> traced = LineTracer.trace(curve, kind);
            Set<BlockXZ> blocks = new HashSet<>();
            traced.forEach(t -> blocks.add(t.block()));
            for (double s = 0; s <= 30; s += 0.05) {
                Vec2 p = curve.at(s);
                for (double d = -0.9; d <= 0.9; d += 0.1) {
                    Vec2 q = p.plus(direction.rotateQuarterClockwise().times(d * kind.halfExtent()));
                    assertTrue(blocks.contains(q.block()), kind + ": paint at s " + s + " is in " + q.block() + ", which has no line block");
                }
            }
            assertWithin(curve, traced, kind, 1.0 / 64);
        }
    }

    @Test
    void aBendsChordsStayWithinAGridHalfStepAndTheirSag() {
        // Lanes of radius 37 and 43 round (0, 40): the line runs at radius 40, a quarter turn.
        Vec2 centre = new Vec2(0, 40);
        MarkingCurve curve = new MarkingCurve(new ArcSegment(centre, 37, -StrictMath.PI / 2, StrictMath.PI / 2),
                new ArcSegment(centre, 43, -StrictMath.PI / 2, StrictMath.PI / 2), 0);
        List<LineTracer.Traced> traced = LineTracer.trace(curve, LineKind.WHITE);
        assertTrue(traced.size() > 60, "a quarter of a circle of radius 40: " + traced.size());
        double sag = StrictMath.pow(1.25 * StrictMath.sqrt(2), 2) / (8 * 40);
        assertWithin(curve, traced, LineKind.WHITE, 1.0 / 64 + sag);
    }

    @Test
    void theSameLineMovedByWholeBlocksGivesTheSameChords() {
        Vec2 direction = new Vec2(StrictMath.cos(0.4), StrictMath.sin(0.4));
        List<LineTracer.Traced> here = LineTracer.trace(straight(new Vec2(0.25, 0.1), direction, 25), LineKind.WHITE);
        List<LineTracer.Traced> there = LineTracer.trace(straight(new Vec2(0.25 + 1000, 0.1 - 700), direction, 25), LineKind.WHITE);
        assertEquals(here.size(), there.size());
        for (int i = 0; i < here.size(); i++) {
            assertEquals(here.get(i).chord(), there.get(i).chord());
            assertEquals(new BlockXZ(here.get(i).block().x() + 1000, here.get(i).block().z() - 700), there.get(i).block());
        }
    }

    /** Every chord's drawn line, where its paint can be (inside the block or the paint's reach around it), within {@code limit} of the curve. */
    static void assertWithin(MarkingCurve curve, List<LineTracer.Traced> traced, LineKind kind, double limit) {
        double reach = kind.halfExtent();
        double worst = 0;
        for (LineTracer.Traced t : traced) {
            double[] e = LineChords.ends(t.chord());
            double x0 = t.block().x() - 0.5;
            double z0 = t.block().z() - 0.5;
            for (int j = 0; j <= 40; j++) {
                double u = e[0] + j / 40.0 * (e[2] - e[0]);
                double v = e[1] + j / 40.0 * (e[3] - e[1]);
                if (u < -reach || u > 1 + reach || v < -reach || v > 1 + reach) {
                    continue;
                }
                worst = StrictMath.max(worst, distance(curve, new Vec2(x0 + u, z0 + v), (t.from() + t.to()) / 2));
            }
        }
        assertTrue(worst <= limit + 1e-9, kind + ": a chord is " + worst + " from the line (limit " + limit + ")");
    }

    /** The distance from {@code p} to the curve, searched within 2 blocks of {@code s}: coarse steps, then refined. */
    static double distance(MarkingCurve curve, Vec2 p, double s) {
        double bestT = s;
        double best = Double.POSITIVE_INFINITY;
        for (double t = s - 2; t <= s + 2; t += 0.02) {
            double d = curve.at(t).distanceTo(p);
            if (d < best) {
                best = d;
                bestT = t;
            }
        }
        double lo = bestT - 0.02;
        double hi = bestT + 0.02;
        for (int i = 0; i < 50; i++) {
            double a = lo + (hi - lo) / 3;
            double b = hi - (hi - lo) / 3;
            if (curve.at(a).distanceTo(p) <= curve.at(b).distanceTo(p)) {
                hi = b;
            } else {
                lo = a;
            }
        }
        return StrictMath.min(best, curve.at((lo + hi) / 2).distanceTo(p));
    }
}
