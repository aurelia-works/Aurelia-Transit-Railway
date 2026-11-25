/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.Angle;
import org.mtr.aurelia.road.geom.mtr.Position;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;

/** The per-lane solver (task G-5, roads-v1 §5 and §12) on City street. */
class PieceSolverTest {
    static PieceSolution solve(int x1, int z1, Heading h1, int x2, int z2, Heading h2) {
        return solve(Presets.Preset.GAME_SCALE, x1, z1, h1, x2, z2, h2);
    }

    static PieceSolution solve(Presets.Preset preset, int x1, int z1, Heading h1, int x2, int z2, Heading h2) {
        return PieceSolver.solve(RoadProfiles.CITY_17, preset, RoadPlanParams.Vertical.QUADRATIC, End.at(x1, 64, z1, h1), End.at(x2, 64, z2, h2));
    }

    static void assertRefused(String code, Map<String, String> args, PieceSolution solution) {
        PieceSolution.Refused refused = assertInstanceOf(PieceSolution.Refused.class, solution, "refused with " + code);
        assertEquals(code, refused.code());
        assertEquals(args, refused.args());
    }

    static PieceSolution.Solved solved(PieceSolution solution) {
        return assertInstanceOf(PieceSolution.Solved.class, solution, () -> "solved, not " + solution);
    }

    @Test
    void aStraightRoadHasALaneEitherSideOfTheCentreLine() {
        PieceSolution.Solved straight = solved(solve(100, -50, Heading.E, 140, -50, Heading.E));
        assertEquals(2, straight.lanes().size());
        LaneRail left = straight.lanes().get(0);
        LaneRail right = straight.lanes().get(1);
        assertEquals(1, left.number());
        assertEquals(End.at(100, 64, -53, Heading.E), left.start(), "left lane: 3 north");
        assertEquals(End.at(140, 64, -53, Heading.E), left.end());
        assertEquals(End.at(100, 64, -47, Heading.E), right.start(), "right lane: 3 south");
        assertEquals(End.at(140, 64, -47, Heading.E), right.end());
        assertEquals(40, right.rail().getLength(), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, PieceSolver.tightestArc(right.rail()), "straight");
        assertEquals(6, PieceSolver.closestApproach(left.rail(), right.rail()), 1e-9);
    }

    @Test
    void aRightAngleBendHasConcentricLanesSixApart() {
        PieceSolution.Solved bend = solved(solve(0, 0, Heading.E, 60, 60, Heading.S));
        assertEquals(60, PieceSolver.tightestArc(bend.reference()), 1e-9);
        assertEquals(63, PieceSolver.tightestArc(bend.lanes().get(0).rail()), 1e-9, "the left lane is outside a right-hand bend");
        assertEquals(57, PieceSolver.tightestArc(bend.lanes().get(1).rail()), 1e-9, "the right lane is inside");
        assertEquals(6, PieceSolver.closestApproach(bend.lanes().get(0).rail(), bend.lanes().get(1).rail()), 1e-3);
        assertEquals(End.at(63, 64, 60, Heading.S), bend.lanes().get(0).end(), "heading south, the left lane is to the east");
    }

    @Test
    void sCurvesAndTwentyTwoPointFiveDegreeStraightsAreSolved() {
        PieceSolution.Solved s = solved(solve(0, 0, Heading.E, 80, 8, Heading.E));
        for (LaneRail lane : s.lanes()) {
            assertEquals(202, PieceSolver.tightestArc(lane.rail()), 1e-9, "both lanes: r = (s² + d²) / 4d");
        }
        PieceSolution.Solved diagonal = solved(solve(0, 0, Heading.ESE, 29, 12, Heading.ESE));
        assertEquals(End.at(-1, 64, 3, Heading.ESE), diagonal.lanes().get(1).start(), "22.5°: the right lane at (−1, 3)");
        assertTrue(PieceSolver.tightestArc(diagonal.lanes().get(0).rail()) > 20_000, "a near-straight S-bend");
        PieceSolution.Solved diagonal45 = solved(solve(0, 0, Heading.SE, 40, 40, Heading.SE));
        assertEquals(StrictMath.sqrt(32), PieceSolver.closestApproach(diagonal45.lanes().get(0).rail(), diagonal45.lanes().get(1).rail()), 1e-9);
    }

    static PieceSolution solve(RoadProfile profile, Presets.Preset preset, int x1, int z1, Heading h1, int x2, int z2, Heading h2) {
        return PieceSolver.solve(profile, preset, RoadPlanParams.Vertical.QUADRATIC, End.at(x1, 64, z1, h1), End.at(x2, 64, z2, h2));
    }

    @Test
    void residentialAndAlleyUseTheirOwnLimits() {
        Presets.Preset game = Presets.Preset.GAME_SCALE;
        Presets.Preset uk = Presets.Preset.UK_FAITHFUL;
        for (RoadProfile residential : List.of(RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17)) {
            // Lanes at ±3 like City street's, so a bend of radius 18 has an inner lane of 15: residential's minimum.
            assertRefused(PieceSolver.RADIUS_TOO_SMALL, ordered("r", "14.0", "group", "residential", "preset", "game_scale", "min", "15"),
                    solve(residential, game, 0, 0, Heading.E, 17, 17, Heading.S));
            assertTrue(solve(residential, game, 0, 0, Heading.E, 18, 18, Heading.S).isSolved(), residential.id() + ": inner lane 15");
            assertRefused(PieceSolver.RADIUS_TOO_SMALL, ordered("r", "57.0", "group", "residential", "preset", "uk_faithful", "min", "90"),
                    solve(residential, uk, 0, 0, Heading.E, 60, 60, Heading.S));
            assertTrue(solve(residential, uk, 0, 0, Heading.E, 93, 93, Heading.S).isSolved(), residential.id() + ": inner lane 90");
            // The diagonal lane offsets are City street's: (−1, 3) at 22.5°, sqrt(32) apart at 45°.
            PieceSolution.Solved diagonal = solved(solve(residential, game, 0, 0, Heading.ESE, 29, 12, Heading.ESE));
            assertEquals(End.at(-1, 64, 3, Heading.ESE), diagonal.lanes().get(1).start(), residential.id());
            PieceSolution.Solved diagonal45 = solved(solve(residential, game, 0, 0, Heading.SE, 40, 40, Heading.SE));
            assertEquals(StrictMath.sqrt(32), PieceSolver.closestApproach(diagonal45.lanes().get(0).rail(), diagonal45.lanes().get(1).rail()), 1e-9);
        }

        RoadProfile alley = RoadProfiles.ALLEY_7;
        assertRefused(PieceSolver.RADIUS_TOO_SMALL, ordered("r", "7.0", "group", "alley", "preset", "game_scale", "min", "8"),
                solve(alley, game, 0, 0, Heading.E, 7, 7, Heading.S));
        PieceSolution.Solved bend = solved(solve(alley, game, 0, 0, Heading.E, 8, 8, Heading.S));
        assertEquals(1, bend.lanes().size(), "one shared lane");
        assertEquals(End.at(0, 64, 0, Heading.E), bend.lanes().get(0).start(), "the lane's nodes are the road's own ends");
        assertEquals(End.at(8, 64, 8, Heading.S), bend.lanes().get(0).end());
        assertEquals(8, PieceSolver.tightestArc(bend.lanes().get(0).rail()), 1e-9);
        assertTrue(solve(alley, uk, 0, 0, Heading.E, 8, 8, Heading.S).isSolved(), "UK-faithful has no alley values, so game-scale's apply");
        PieceSolution.Solved s = solved(solve(alley, game, 0, 0, Heading.E, 24, 4, Heading.E));
        assertEquals(37, PieceSolver.tightestArc(s.lanes().get(0).rail()), 1e-9, "r = (s² + d²) / 4d");
    }

    @Test
    void eachRefusalHasARealExample() {
        assertRefused(RoadPlanner.TOO_SHORT, Map.of(), solve(5, 5, Heading.E, 5, 5, Heading.S));
        assertRefused(RoadPlanner.TOO_SHORT, Map.of(), solve(5, 5, Heading.E, 6, 5, Heading.E));
        assertRefused(PieceSolver.REVERSE_HEADING, Map.of(), solve(0, 0, Heading.E, 40, 0, Heading.W));
        assertRefused(PieceSolver.PARALLEL_BESIDE, Map.of(), solve(0, 0, Heading.E, 0, 10, Heading.E));
        assertRefused(PieceSolver.S_CURVE_TOO_SHARP, ordered("phi", "22.6", "minLength", "61"), solve(0, 0, Heading.E, 60, 12, Heading.E));
        assertTrue(solve(0, 0, Heading.E, 61, 12, Heading.E).isSolved(), "61 blocks is long enough for a 12-block shift");
        assertRefused(PieceSolver.NO_MTR_PATH, Map.of(), solve(0, 0, Heading.E, 40, 40, Heading.N));
        assertRefused(PieceSolver.NO_MTR_PATH, Map.of(), solve(0, 0, Heading.E, -20, 0, Heading.E));
        assertRefused(PieceSolver.LANE_SOLVE_FAILED, Map.of("n", "2"), solve(0, 0, Heading.E, 25, 10, Heading.ESE));
        assertRefused(PieceSolver.INNER_EDGE_DEGENERATE, Map.of(), solve(0, 0, Heading.E, 8, 8, Heading.S));
        assertRefused(PieceSolver.RADIUS_TOO_SMALL, ordered("r", "27.0", "group", "city_street", "preset", "game_scale", "min", "30"),
                solve(0, 0, Heading.E, 30, 30, Heading.S));
        assertRefused(PieceSolver.RADIUS_TOO_SMALL, ordered("r", "57.0", "group", "city_street", "preset", "uk_faithful", "min", "127"),
                solve(Presets.Preset.UK_FAITHFUL, 0, 0, Heading.E, 60, 60, Heading.S));
        assertTrue(solve(Presets.Preset.UK_FAITHFUL, 0, 0, Heading.E, 130, 130, Heading.S).isSolved(), "inner lane 127");
    }

    @Test
    void laneSpacingIsMeasuredAlongThePiece() {
        // City street never squeezes below 5.2 once the other checks pass (the 22.5° S-bend rule), so the measure is checked directly.
        RailMath a = new RailMath(new Position(0, 64, 0), Angle.E, new Position(40, 64, 0), Angle.W, Rail.Shape.QUADRATIC, 0);
        RailMath b = new RailMath(new Position(0, 64, 5), Angle.E, new Position(40, 64, 5), Angle.W, Rail.Shape.QUADRATIC, 0);
        assertEquals(5, PieceSolver.closestApproach(a, b), 1e-9);
        RailMath bend = new RailMath(new Position(0, 64, 2), Angle.E, new Position(40, 64, 7), Angle.W, Rail.Shape.QUADRATIC, 0);
        assertEquals(2, PieceSolver.closestApproach(a, bend), 1e-6, "an S-bend drifting away from the straight starts 2 off it");
    }

    @Test
    void aPieceSolvesTheSameAnywhere() {
        for (int[] far : new int[][] {{0, 0}, {29_999_000, -29_999_000}, {-12_345_678, 7_654_321}}) {
            PieceSolution.Solved here = solved(solve(far[0], far[1], Heading.E, far[0] + 60, far[1] + 25, Heading.SE));
            assertEquals(End.at(far[0] + 60 + 2, 64, far[1] + 25 - 2, Heading.SE), here.lanes().get(0).end());
            assertEquals(86.91, PieceSolver.tightestArc(here.lanes().get(0).rail()), 0.01, "rails are built relative to the start node");
        }
    }

    /**
     * 2,000 random pieces at all 256 heading pairs within 150 blocks, anywhere: every solved lane closes on its nodes,
     * runs the right way, keeps at least 5.2 from its neighbour and 30 from its tightest arc's centre; every refusal is one
     * of the codes.
     */
    @Test
    void randomPiecesAreSolvedOrRefusedForAReason() {
        Random random = new Random(20261006L);
        Set<String> codes = Set.of(RoadPlanner.TOO_SHORT, PieceSolver.REVERSE_HEADING, PieceSolver.PARALLEL_BESIDE, PieceSolver.S_CURVE_TOO_SHARP,
                PieceSolver.NO_MTR_PATH, PieceSolver.LANE_SOLVE_FAILED, PieceSolver.INNER_EDGE_DEGENERATE, PieceSolver.RADIUS_TOO_SMALL,
                PieceSolver.LANE_SPACING, PieceSolver.NODES_JOIN_OTHER_WAY);
        Map<String, Integer> outcomes = new TreeMap<>();
        for (int i = 0; i < 2_000; i++) {
            int x1 = random.nextInt(2_000_001) - 1_000_000;
            int z1 = random.nextInt(2_000_001) - 1_000_000;
            Heading h1 = Heading.of(random.nextInt(16));
            Heading h2 = Heading.of(random.nextInt(16));
            int x2 = x1 + random.nextInt(301) - 150;
            int z2 = z1 + random.nextInt(301) - 150;
            PieceSolution solution = solve(x1, z1, h1, x2, z2, h2);
            if (solution instanceof PieceSolution.Refused refused) {
                assertTrue(codes.contains(refused.code()), refused.code());
                outcomes.merge(refused.code(), 1, Integer::sum);
                continue;
            }
            outcomes.merge("solved", 1, Integer::sum);
            List<LaneRail> lanes = ((PieceSolution.Solved) solution).lanes();
            for (LaneRail lane : lanes) {
                BlockStep a = new BlockStep(lane.start().x() - x1, lane.start().z() - z1);
                BlockStep b = new BlockStep(lane.end().x() - x1, lane.end().z() - z1);
                assertTrue(PieceSolver.usable(lane.rail(), a, b, h1, h2), "lane " + lane.number() + " of " + solution);
                assertTrue(PieceSolver.tightestArc(lane.rail()) >= 30);
            }
            assertTrue(PieceSolver.closestApproach(lanes.get(0).rail(), lanes.get(1).rail()) >= 5.2);
        }
        System.out.println(String.format(Locale.ROOT, "G-5: 2,000 random City street pieces: %s", outcomes));
        assertTrue(outcomes.getOrDefault("solved", 0) > 100, "enough solved pieces: " + outcomes);
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void theHudsSteepestGradeIsTheOneTheRefusalReports() {
        Random random = new Random(20261006L);
        int compared = 0;
        for (int attempt = 0; attempt < 400; attempt++) {
            End start = End.at(0, 64, 0, Heading.of(random.nextInt(16)));
            int rise = (3 + random.nextInt(10)) * (random.nextBoolean() ? 1 : -1);
            End end;
            if (attempt % 2 == 0) { // a straight climb: mostly too steep
                End along = RoadPlanner.snapEnd(start, 20 + random.nextInt(60));
                end = End.at(along.x(), 64 + rise, along.z(), start.heading());
            } else {
                end = End.at(random.nextInt(121) - 60, 64 + rise, random.nextInt(121) - 60, Heading.of(random.nextInt(16)));
            }
            RoadPlanParams.Vertical vertical = random.nextBoolean() ? RoadPlanParams.Vertical.QUADRATIC
                    : new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII, java.util.OptionalDouble.of(10 + random.nextInt(500)));
            PieceSolution solution = PieceSolver.solve(RoadProfiles.CITY_17, Presets.Preset.GAME_SCALE, vertical, start, end);
            if (solution instanceof PieceSolution.Refused refused && refused.code().equals(PieceSolver.GRADE_TOO_STEEP)) {
                double grade = PieceSolver.steepestGrade(RoadProfiles.CITY_17, vertical, start, end).orElseThrow();
                assertEquals(refused.args().get("grade"), PieceSolver.percents(grade * 100, 100.0 / 12)[0], start + " -> " + end);
                compared++;
            }
        }
        assertTrue(compared > 100, "enough refusals compared: " + compared);
        assertEquals(0, PieceSolver.steepestGrade(RoadProfiles.CITY_17, RoadPlanParams.Vertical.QUADRATIC, End.at(0, 64, 0, Heading.E),
                End.at(40, 64, 0, Heading.E)).orElseThrow(), "level");
    }
}
