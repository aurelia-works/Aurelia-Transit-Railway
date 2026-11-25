/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.VerticalProfile;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.geom.mtr.Vector;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.solve.LaneRail;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * G-7's acceptance: 300 random sloped City street pieces anywhere in the world (a third straights, a third S-bends, a
 * third anything), rising or falling 1 to 6 blocks. Every accepted piece has neighbouring lanes within 0.25 of each
 * other in height (measured by brute force, independently of the solver), no lane steeper than 1:12 (measured from the
 * rail's own heights), every column's fitted top within a quarter block of its target, and at every lane-centre column
 * a target within 0.05 of the rail's height plus one. Every refused one is refused for a reason the solver gives.
 */
class SlopedRoadTest {
    private static final Set<String> REASONS = Set.of(PieceSolver.GRADE_TOO_STEEP, PieceSolver.CROSS_LANE_STEP, PieceSolver.NO_MTR_PATH,
            PieceSolver.LANE_SOLVE_FAILED, PieceSolver.S_CURVE_TOO_SHARP, PieceSolver.RADIUS_TOO_SMALL, PieceSolver.INNER_EDGE_DEGENERATE,
            PieceSolver.LANE_SPACING, PieceSolver.NODES_JOIN_OTHER_WAY, PieceSolver.REVERSE_HEADING, PieceSolver.PARALLEL_BESIDE, RoadPlanner.TOO_SHORT);

    @Test
    void slopedPiecesKeepTheirLanesLevelWithEachOtherAndTheSurfaceOnTheRails() {
        Random random = new Random(20261006L);
        Map<String, Integer> refusals = new TreeMap<>();
        int accepted = 0;
        long columns = 0;
        double worstStep = 0;
        double worstGrade = 0;
        double worstFit = 0;
        double worstOnRail = 0;
        for (int attempt = 0; accepted < 300; attempt++) {
            assertTrue(attempt < 20_000, "too few pieces accepted");
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            int y = random.nextInt(200);
            Heading from = Heading.of(random.nextInt(16));
            int rise = (1 + random.nextInt(6)) * (random.nextBoolean() ? 1 : -1);
            End start = End.at(x, y, z, from);
            End end;
            switch (attempt % 3) {
                case 0 -> {
                    End along = RoadPlanner.snapEnd(start, 30 + random.nextInt(120));
                    end = End.at(along.x(), y + rise, along.z(), from);
                }
                case 1 -> {
                    Vec2 p = from.tangent().times(60 + random.nextInt(80)).plus(from.normal().times(random.nextInt(17) - 8));
                    end = End.at(x + (int) StrictMath.rint(p.x()), y + rise, z + (int) StrictMath.rint(p.z()), from);
                }
                default -> end = End.at(x + random.nextInt(241) - 120, y + rise, z + random.nextInt(241) - 120, Heading.of(random.nextInt(16)));
            }
            PieceSolution solution = PieceSolver.solve(RoadProfiles.CITY_17, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, start, end);
            if (solution instanceof PieceSolution.Refused refused) {
                assertTrue(REASONS.contains(refused.code()), "refused for " + refused);
                refusals.merge(refused.code(), 1, Integer::sum);
                continue;
            }
            PieceSolution.Solved solved = (PieceSolution.Solved) solution;
            accepted++;
            // Neighbouring lanes, by brute force: every half block along one, against the nearest of all points on the other.
            for (int i = 0; i + 1 < solved.lanes().size(); i++) {
                double step = heightStep(solved.lanes().get(i).rail(), solved.lanes().get(i + 1).rail());
                worstStep = StrictMath.max(worstStep, step);
                assertTrue(step <= PieceSolver.MAX_CROSS_LANE_STEP + 0.02, start + " -> " + end + ": lanes " + step + " apart");
            }
            // Grades, from the rails' own heights.
            for (LaneRail lane : solved.lanes()) {
                double grade = measuredGrade(lane.rail());
                worstGrade = StrictMath.max(worstGrade, grade);
                assertTrue(grade <= 1.0 / 12 + 1e-6, start + " -> " + end + ": lane " + lane.number() + " climbs " + grade);
                assertTrue(VerticalProfile.of(lane.rail(), rise).steepestGrade() <= 1.0 / 12 + 1e-12);
            }
            // The surface.
            RoadPiece piece = RoadPiece.solved(RoadProfiles.CITY_17, solved, true, 0);
            for (Map.Entry<BlockXZ, Rasteriser.Cell> entry : Rasteriser.cells(piece).entrySet()) {
                double top = entry.getValue().top();
                double fitted = SurfaceFit.fit(top, BuiltInPalettes.vanilla().steps());
                worstFit = StrictMath.max(worstFit, StrictMath.abs(fitted - top));
                assertTrue(StrictMath.abs(fitted - top) <= 0.25, "fitted " + fitted + " for " + top);
                columns++;
                if (entry.getValue().column().laneCentre()) {
                    Vec2 p = new Vec2(entry.getKey().x() - piece.anchor().x(), entry.getKey().z() - piece.anchor().z());
                    double nearest = Double.POSITIVE_INFINITY;
                    for (int lane = 0; lane < piece.lanes().size(); lane++) {
                        LaneCurve.Local local = piece.lanes().get(lane).local(p);
                        if (StrictMath.abs(local.d()) <= 0.5 + 1e-9) {
                            nearest = StrictMath.min(nearest, StrictMath.abs(top - (piece.height(lane, local.s()) + 1)));
                        }
                    }
                    if (nearest != Double.POSITIVE_INFINITY) {
                        worstOnRail = StrictMath.max(worstOnRail, nearest);
                        assertTrue(nearest <= 0.05, start + " -> " + end + ": the surface at a lane centre is " + nearest + " off its rail");
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "G-7: 300 sloped pieces accepted (%,d columns), refused %s; worst cross-lane step %.3f, steepest lane %.4f "
                + "(1:12 = %.4f), worst fit %.3f, worst lane-centre target %.4f off the rail%n", columns, refusals, worstStep, worstGrade, 1.0 / 12,
                worstFit, worstOnRail);
    }

    /** The largest height difference between points every half block along {@code a} and the nearest point of {@code b}. */
    private static double heightStep(RailMath a, RailMath b) {
        Vector[] onB = samples(b, 0.05);
        double worst = 0;
        for (Vector p : samples(a, 0.5)) {
            Vector best = onB[0];
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Vector q : onB) {
                double d = (p.x() - q.x()) * (p.x() - q.x()) + (p.z() - q.z()) * (p.z() - q.z());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = q;
                }
            }
            worst = StrictMath.max(worst, StrictMath.abs(p.y() - best.y()));
        }
        return worst;
    }

    /** The steepest climb between points a tenth of a block apart along the rail. */
    private static double measuredGrade(RailMath rail) {
        Vector[] points = samples(rail, 0.1);
        double worst = 0;
        for (int i = 0; i + 1 < points.length; i++) {
            double dx = points[i + 1].x() - points[i].x();
            double dz = points[i + 1].z() - points[i].z();
            double run = StrictMath.sqrt(dx * dx + dz * dz);
            if (run > 1e-9) {
                worst = StrictMath.max(worst, StrictMath.abs(points[i + 1].y() - points[i].y()) / run);
            }
        }
        return worst;
    }

    private static Vector[] samples(RailMath rail, double step) {
        double length = rail.getLength();
        int count = (int) StrictMath.ceil(length / step) + 1;
        Vector[] points = new Vector[count];
        for (int i = 0; i < count; i++) {
            points[i] = rail.getPosition(length * i / (count - 1), false);
        }
        return points;
    }
}
