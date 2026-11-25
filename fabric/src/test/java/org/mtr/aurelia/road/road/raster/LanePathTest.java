/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.Position;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/** Arcs, lane paths and MTR's pieces rebuilt exactly (task G-6+). */
class LanePathTest {
    /** A rail from (0, 0) heading {@code from} to (x, z) heading {@code to}, with MTR's heading convention (G-5). */
    static RailMath rail(int x, int z, Heading from, Heading to) {
        return new RailMath(new Position(0, 64, 0), MtrRails.angle(from), new Position(x, 64, z), MtrRails.angle(to.opposite()),
                Rail.Shape.QUADRATIC, 0);
    }

    /** Rails roads have: the centre line and both lanes of random pieces the solver (G-5) accepts. */
    @Test
    void mtrsPiecesAreRebuiltExactly() {
        Random random = new Random(20261006L);
        int rails = 0;
        int arcs = 0;
        double worst = 0;
        while (rails < 2_000) {
            Heading from = Heading.of(random.nextInt(16));
            Heading to = Heading.of(random.nextInt(16));
            PieceSolution solution = PieceSolver.solve(RoadProfiles.CITY_17, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC,
                    RoadPlanParams.End.at(0, 64, 0, from), RoadPlanParams.End.at(random.nextInt(401) - 200, 64, random.nextInt(401) - 200, to));
            if (!(solution instanceof PieceSolution.Solved solved)) {
                continue;
            }
            List<RailMath> roadRails = new ArrayList<>(List.of(solved.reference()));
            solved.lanes().forEach(lane -> roadRails.add(lane.rail()));
            for (RailMath rail : roadRails) {
                rails++;
                LanePath lane = LanePath.of(rail);
                arcs += (int) lane.pieces().stream().filter(ArcSegment.class::isInstance).count();
                assertEquals(rail.getLength(), lane.length(), 1e-9);
                for (int m = 0; m <= 64; m++) {
                    double u = rail.getLength() * m / 64;
                    Vec2 mtr = MtrRails.horizontal(rail.getPosition(u, false));
                    Vec2 ours = lane.point(u);
                    worst = StrictMath.max(worst, mtr.distanceTo(ours));
                    // Back again: exact but at an S-curve's join, where MTR's own two arcs miss each other by up to 4.4e-5 (S5).
                    LaneCurve.Local back = lane.local(ours);
                    assertEquals(u, back.s(), LanePath.JOIN, "s round trip at " + u + " on " + lane);
                    assertEquals(0, back.d(), LanePath.JOIN, "on the lane");
                }
            }
        }
        System.out.printf(Locale.ROOT, "G-6+: %,d rails of solved pieces (%,d arcs) rebuilt from MTR's pieces: worst distance from getPosition %.2e%n",
                rails, arcs, worst);
        assertTrue(worst <= 1e-9, "worst " + worst);
        assertTrue(arcs > 1_000, "arcs tested: " + arcs);
    }

    @Test
    void anArcMeasuresAlongAndAcross() {
        // E → S, radius 40: centre (0, 40), from straight north of it (−90°) a quarter turn clockwise.
        ArcSegment arc = (ArcSegment) LanePath.of(rail(40, 40, Heading.E, Heading.S)).pieces().get(0);
        assertEquals(new Vec2(0, 40).x(), arc.centre().x(), 1e-12);
        assertEquals(40, arc.radius(), 1e-12);
        assertEquals(StrictMath.PI / 2, arc.sweep(), 1e-12, "a right-hand bend sweeps clockwise (positive)");
        assertEquals(new LaneCurve.Local(0, 0), round(arc.local(new Vec2(0, 0))));
        assertEquals(new LaneCurve.Local(0, -3), round(arc.local(new Vec2(0, -3))), "north of an eastward road is left (negative)");
        assertEquals(new LaneCurve.Local(0, 3), round(arc.local(new Vec2(0, 3))), "towards the centre is right");
        assertEquals(20 * StrictMath.PI, arc.local(new Vec2(40, 40)).s(), 1e-9, "the end");
        assertEquals(0, arc.local(new Vec2(40, 40)).d(), 1e-9);
        assertTrue(arc.local(new Vec2(-1, 0)).s() < 0, "before the start");
        assertEquals(1, arc.startTangent().x(), 1e-12);
        assertEquals(1, arc.endTangent().z(), 1e-12, "it ends heading south");
    }

    @Test
    void aLanePathCarriesOnAlongItsTangentsAtBothEnds() {
        LanePath bend = LanePath.of(rail(40, 40, Heading.E, Heading.S));
        LaneCurve.Local before = bend.local(new Vec2(-2, 1));
        assertEquals(-2, before.s(), 1e-12, "behind the start, along the start tangent");
        assertEquals(1, before.d(), 1e-12);
        LaneCurve.Local after = bend.local(new Vec2(41, 43));
        assertEquals(bend.length() + 3, after.s(), 1e-9, "past the end, along the end tangent");
        assertEquals(-1, after.d(), 1e-9, "heading south, east is the left");
    }

    @Test
    void lineThenArcIsContinuousAcrossTheJoin() {
        LanePath lane = LanePath.of(rail(80, 40, Heading.E, Heading.S)); // 40 of line, then a quarter arc of radius 40
        assertEquals(2, lane.pieces().size());
        assertTrue(lane.pieces().get(0) instanceof LineSegment);
        assertEquals(40, lane.pieces().get(0).length(), 1e-9);
        for (double s = 39.5; s <= 40.5; s += 0.125) {
            Vec2 p = lane.point(s);
            assertEquals(s, lane.local(p).s(), 1e-9, "s " + s);
            Vec2 off = p.plus(new Vec2(0, -2));
            assertTrue(lane.local(off).d() < 0, "north of the join is left");
        }
    }

    private static LaneCurve.Local round(LaneCurve.Local local) {
        return new LaneCurve.Local(StrictMath.rint(local.s() * 1e9) / 1e9, StrictMath.rint(local.d() * 1e9) / 1e9);
    }
}
