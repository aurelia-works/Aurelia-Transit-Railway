/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfiles;

/**
 * Golden cases for the per-lane solver (task G-5): City street pieces from block (0, 0) at y 64 (game-scale), with each
 * lane's nodes, length and tightest radius, or the refusal, compared with
 * fabric/src/test/resources/road-snapshots/solve/city-17-golden.txt. To accept a deliberate change, run
 * {@code JAVA_TOOL_OPTIONS=-Dstratos.updateSnapshots=true ./gradlew :fabric:test --tests <class>} and review the diff before committing.
 */
class PieceSolverGoldenTest {
    private static final Path FILE = Path.of("src", "test", "resources", "road-snapshots", "solve", "city-17-golden.txt");

    record Case(String name, Heading from, int x, int z, Heading to) {
    }

    static List<Case> cases() {
        java.util.ArrayList<Case> cases = new java.util.ArrayList<>();
        for (Heading heading : Heading.values()) {
            End end = RoadPlanner.snapEnd(End.at(0, 64, 0, heading), 40);
            cases.add(new Case("straight, 40 along " + heading, heading, end.x(), end.z(), heading));
        }
        cases.addAll(List.of(
                new Case("right-angle bend, radius 60", Heading.E, 60, 60, Heading.S),
                new Case("right-angle bend, radius 40", Heading.E, 40, 40, Heading.S),
                new Case("left-hand right-angle bend", Heading.E, 60, -60, Heading.N),
                new Case("line then arc", Heading.E, 80, 40, Heading.S),
                new Case("45-degree bend", Heading.E, 60, 25, Heading.SE),
                new Case("45-degree bend, tighter", Heading.E, 40, 12, Heading.SE),
                new Case("22.5-degree bend", Heading.E, 60, 6, Heading.ESE),
                new Case("bend from a diagonal", Heading.SE, 30, 60, Heading.S),
                new Case("bend between 22.5-degree headings", Heading.ESE, 40, 40, Heading.SSE),
                new Case("S-bend, 8 over 80", Heading.E, 80, 8, Heading.E),
                new Case("S-bend at the limit, 12 over 61", Heading.E, 61, 12, Heading.E),
                new Case("S-bend on a diagonal, 20 degrees", Heading.SE, 100, 70, Heading.SE),
                new Case("S-bend on a diagonal, 17 degrees", Heading.NE, 117, -86, Heading.NE),
                new Case("S-bend on a diagonal at the limit", Heading.SE, 41, 61, Heading.SE),
                new Case("too short", Heading.E, 1, 0, Heading.E),
                new Case("back on itself", Heading.E, 40, 0, Heading.W),
                new Case("end beside the start", Heading.E, 0, 10, Heading.E),
                new Case("S-bend too sharp", Heading.E, 60, 12, Heading.E),
                new Case("no MTR path", Heading.E, 40, 40, Heading.N),
                new Case("one lane can't follow", Heading.E, 25, 10, Heading.ESE),
                new Case("tighter than the road is wide", Heading.E, 8, 8, Heading.S),
                new Case("inner lane under 30", Heading.E, 30, 30, Heading.S)));
        return cases;
    }

    static String describe(Case c) {
        PieceSolution solution = PieceSolver.solve(RoadProfiles.CITY_17, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC,
                End.at(0, 64, 0, c.from()), End.at(c.x(), 64, c.z(), c.to()));
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-38s %-3s (0, 0) -> %-3s (%d, %d): ", c.name(), c.from(), c.to(), c.x(), c.z()));
        if (solution instanceof PieceSolution.Refused refused) {
            return line.append("refused ").append(refused.code()).append(refused.args().isEmpty() ? "" : " " + refused.args()).toString();
        }
        PieceSolution.Solved solved = (PieceSolution.Solved) solution;
        line.append(String.format(Locale.ROOT, "centre %.3f long%s", solved.reference().getLength(), radius(PieceSolver.tightestArc(solved.reference()))));
        for (LaneRail lane : solved.lanes()) {
            line.append(String.format(Locale.ROOT, " | lane %d (%d, %d) -> (%d, %d) %.3f long%s", lane.number(), lane.start().x(), lane.start().z(),
                    lane.end().x(), lane.end().z(), lane.rail().getLength(), radius(PieceSolver.tightestArc(lane.rail()))));
        }
        line.append(String.format(Locale.ROOT, " | spacing %.3f", PieceSolver.closestApproach(solved.lanes().get(0).rail(), solved.lanes().get(1).rail())));
        return line.toString();
    }

    private static String radius(double r) {
        return r == Double.POSITIVE_INFINITY ? ", straight" : String.format(Locale.ROOT, ", r %.2f", r);
    }

    @Test
    void goldenCases() throws IOException {
        StringBuilder actual = new StringBuilder("City street (city-17) pieces solved lane by lane (G-5); start block (0, 0), y 64, game-scale\n");
        for (Case c : cases()) {
            actual.append(describe(c)).append('\n');
        }
        // The straights land where the tool would end a 40-block road (RoadPlanner.snapEnd), within the line's half block.
        for (Heading heading : Heading.values()) {
            End end = RoadPlanner.snapEnd(End.at(0, 64, 0, heading), 40);
            assertTrue(StrictMath.abs(new Frame(Vec2.ZERO, heading).side(new Vec2(end.x(), end.z()))) <= 0.05);
        }
        if (Boolean.getBoolean("stratos.updateSnapshots")) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, actual.toString(), StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.isRegularFile(FILE), FILE + " is missing; run with JAVA_TOOL_OPTIONS=-Dstratos.updateSnapshots=true to create it");
        assertEquals(Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"), actual.toString(), FILE + " differs from the solver's output");
    }
}
