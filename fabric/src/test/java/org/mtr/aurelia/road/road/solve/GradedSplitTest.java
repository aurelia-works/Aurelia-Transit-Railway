/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.VerticalProfile;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;

/**
 * R-5's graded bends (owner, 2026-10-09: split only when needed). Climbing bends of City street and residential, a
 * straight then a curve or a curve then a straight, at every heading and anywhere in the world, until 300 of them are
 * ones G-7's single piece refuses for {@code CROSS_LANE_STEP}. Each must be split into two pieces joined end to end at a
 * block centre at a whole-block height, each climbing with neighbouring lanes within 0.25 block of each other (measured
 * again here) and no steeper than the layout allows; or refused {@code GRADE_ON_MIXED_PIECE} with the one piece's step.
 * And every road one piece solves stays exactly that piece.
 */
class GradedSplitTest {
    @Test
    void bendsWhoseLanesClimbApartAreSplitOrRefused() {
        Random random = new Random(20261009L);
        Map<String, Integer> outcomes = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        List<String> refusedExamples = new ArrayList<>();
        int found = 0;
        double worstStep = 0;
        for (int attempt = 0; found < 300; attempt++) {
            assertTrue(attempt < 200_000, "too few bends refused for their cross-lane step");
            RoadProfile profile = random.nextBoolean() ? RoadProfiles.CITY_17 : RoadProfiles.RESIDENTIAL_15;
            End[] ends = bend(random, profile);
            PieceSolution one = PieceSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, ends[0], ends[1]);
            if (!(one instanceof PieceSolution.Refused refused) || !refused.code().equals(PieceSolver.CROSS_LANE_STEP)) {
                continue;
            }
            found++;
            RoadSolution solution = RoadSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, ends[0], ends[1]);
            if (solution instanceof RoadSolution.Refused no) {
                assertEquals(RoadSolver.GRADE_ON_MIXED_PIECE, no.code());
                assertEquals(refused.args().get("dy"), no.args().get("dy"));
                outcomes.merge(no.code(), 1, Integer::sum);
                if (refusedExamples.size() < 6) {
                    refusedExamples.add(String.format(Locale.ROOT, "refused %s from %s to (%+d, %+d, %+d) %s, dy %s", profile.id(), ends[0].heading(),
                            ends[1].x() - ends[0].x(), ends[1].y() - ends[0].y(), ends[1].z() - ends[0].z(), ends[1].heading(), no.args().get("dy")));
                }
                continue;
            }
            List<PieceSolution.Solved> pieces = ((RoadSolution.Solved) solution).pieces();
            assertEquals(2, pieces.size());
            End middle = pieces.get(0).end();
            assertEquals(middle, pieces.get(1).start(), "the parts meet");
            assertEquals(ends[0], pieces.get(0).start());
            assertEquals(ends[1], pieces.get(1).end());
            double max = Presets.limits(profile.group(), Presets.Preset.GAME_SCALE).maxGrade().value();
            for (PieceSolution.Solved piece : pieces) {
                long rise = (long) piece.end().y() - piece.start().y();
                for (int i = 0; i + 1 < piece.lanes().size(); i++) {
                    double step = PieceSolver.heightStep(piece.lanes().get(i).rail(), piece.lanes().get(i + 1).rail());
                    worstStep = StrictMath.max(worstStep, step);
                    assertTrue(step <= PieceSolver.MAX_CROSS_LANE_STEP, "part lanes " + step + " apart");
                }
                for (LaneRail lane : piece.lanes()) {
                    assertTrue(rise == 0 || VerticalProfile.of(lane.rail(), rise).steepestGrade() <= max + 1e-12);
                }
            }
            outcomes.merge("split", 1, Integer::sum);
            if (examples.size() < 3 && ends[0].heading() == Heading.E) {
                examples.add(String.format(Locale.ROOT, "%s %s -> %s split at %s", profile.id(), ends[0], ends[1], middle));
            }
        }
        System.out.printf(Locale.ROOT, "R-5: 300 climbing bends one piece refuses for CROSS_LANE_STEP: %s; worst step in a part %.3f%n", outcomes,
                worstStep);
        examples.forEach(System.out::println);
        refusedExamples.forEach(System.out::println);
        assertTrue(outcomes.getOrDefault("split", 0) > 0, "some are split");
    }

    @Test
    void roadsOnePieceSolvesStayThatPiece() {
        Random random = new Random(7);
        int checked = 0;
        for (int attempt = 0; checked < 300; attempt++) {
            assertTrue(attempt < 100_000);
            RoadProfile profile = RoadProfiles.all().get(random.nextInt(RoadProfiles.all().size()));
            End[] ends = bend(random, profile);
            PieceSolution one = PieceSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, ends[0], ends[1]);
            if (one instanceof PieceSolution.Solved solved) {
                RoadSolution whole = RoadSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, ends[0], ends[1]);
                assertEquals(List.of(solved.start(), solved.end()), List.of(((RoadSolution.Solved) whole).pieces().get(0).start(),
                        ((RoadSolution.Solved) whole).pieces().get(0).end()));
                assertEquals(1, ((RoadSolution.Solved) whole).pieces().size());
                checked++;
            }
        }
    }

    /** A climbing line + arc (or arc + line) bend with a long straight, near its layout's steepest grade. */
    private static End[] bend(Random random, RoadProfile profile) {
        int x = random.nextInt(2_000_001) - 1_000_000;
        int z = random.nextInt(2_000_001) - 1_000_000;
        int y = 60 + random.nextInt(100);
        Heading from = Heading.of(random.nextInt(16));
        int turn = (1 + random.nextInt(4)) * (random.nextBoolean() ? 1 : -1); // 22.5° to 90°, either way
        Heading to = from.rotate(turn);
        double radius = 30 + random.nextInt(60);
        double line = 20 + random.nextInt(120);
        double delta = StrictMath.toRadians(turn * Heading.STEP_DEGREES);
        // A right turn (clockwise) bends towards the normal; the arc's chord, in the start's frame.
        double along = radius * StrictMath.sin(StrictMath.abs(delta));
        double side = radius * (1 - StrictMath.cos(delta)) * StrictMath.signum(delta);
        Vec2 offset = from.tangent().times(line + along).plus(from.normal().times(side));
        double length = line + radius * StrictMath.abs(delta);
        double grade = Presets.limits(profile.group(), Presets.Preset.GAME_SCALE).maxGrade().value();
        int rise = (int) StrictMath.max(1, StrictMath.floor(length * grade / 2 * (0.5 + 0.5 * random.nextDouble())));
        rise *= random.nextBoolean() ? 1 : -1;
        End start = End.at(x, y, z, from);
        End end = End.at(x + (int) StrictMath.rint(offset.x()), y + rise, z + (int) StrictMath.rint(offset.z()), to);
        if (random.nextBoolean()) {
            return new End[] {start, end};
        }
        // The same bend driven the other way: the curve first, then the straight.
        return new End[] {End.at(end.x(), end.y(), end.z(), to.opposite()), End.at(start.x(), start.y(), start.z(), from.opposite())};
    }
}
