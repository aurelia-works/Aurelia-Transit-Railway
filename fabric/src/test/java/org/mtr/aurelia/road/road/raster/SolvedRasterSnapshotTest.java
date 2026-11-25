/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * ASCII snapshots of solved pieces (task G-6+; residential and alley from G-4+): each lane on its own MTR rail, square ends, with the plan
 * hash on the second line, compared with fabric/src/test/resources/road-snapshots/raster-solved/. To accept a deliberate
 * change, run {@code JAVA_TOOL_OPTIONS=-Dstratos.updateSnapshots=true ./gradlew :fabric:test --tests <class>} and review the diff before committing. Also checks
 * that a solved cardinal straight is block for block G-6's straight piece.
 */
class SolvedRasterSnapshotTest {
    private static final Path DIRECTORY = Path.of("src", "test", "resources", "road-snapshots", "raster-solved");
    private static final String CORE_VERSION = "snapshot";

    record Case(RoadProfile profile, String file, String what, Heading from, int x, int z, Heading to) {
        /** A City street case. */
        Case(String file, String what, Heading from, int x, int z, Heading to) {
            this(RoadProfiles.CITY_17, file, what, from, x, z, to);
        }
    }

    static final List<Case> CASES = List.of(
            new Case("bend-90-r40", "a right-angle bend, radius 40", Heading.E, 40, 40, Heading.S),
            new Case("bend-90-left", "a left-hand right-angle bend, radius 40", Heading.E, 40, -40, Heading.N),
            new Case("line-then-arc", "40 blocks of line, then a right-angle bend of radius 40", Heading.E, 80, 40, Heading.S),
            new Case("bend-45", "a 45-degree bend ending on a diagonal", Heading.E, 60, 25, Heading.SE),
            new Case("bend-22", "a 22.5-degree bend", Heading.E, 60, 6, Heading.ESE),
            new Case("straight-22", "a 22.5-degree straight on MTR's near-straight S-bends", Heading.ESE, 29, 12, Heading.ESE),
            new Case("s-bend", "an S-bend shifting 8 over 80", Heading.E, 80, 8, Heading.E),
            new Case("s-bend-diagonal", "a diagonal S-bend, about 20 degrees", Heading.SE, 100, 70, Heading.SE),
            new Case(RoadProfiles.RESIDENTIAL_15, "residential-15-bend-90-r20", "a right-angle bend, radius 20 (inner lane 17)", Heading.E, 20, 20,
                    Heading.S),
            new Case(RoadProfiles.RESIDENTIAL_17, "residential-17-bend-45", "a 45-degree bend ending on a diagonal", Heading.E, 60, 25, Heading.SE),
            new Case(RoadProfiles.ALLEY_7, "alley-7-bend-90-r12", "a right-angle bend, radius 12", Heading.E, 12, 12, Heading.S),
            new Case(RoadProfiles.ALLEY_7, "alley-7-s-bend", "an S-bend shifting 4 over 24", Heading.E, 24, 4, Heading.E));

    @Test
    void solvedPiecesMatchTheirSnapshots() throws IOException {
        boolean update = Boolean.getBoolean("stratos.updateSnapshots");
        List<String> differences = new ArrayList<>();
        for (Case c : CASES) {
            RoadPiece piece = piece(c.profile(), c.from(), c.x(), c.z(), c.to());
            String actual = String.format(Locale.ROOT, "%s, %s: (0, 0) heading %s to (%d, %d) heading %s, solved lane by lane (G-5), closed end",
                    c.profile().id(), c.what(), c.from(), c.x(), c.z(), c.to()) + "\n"
                    + "hash " + PlanHash.hex(Rasteriser.plan(piece, BuiltInPalettes.vanilla()), CORE_VERSION) + "\n"
                    + RasterAscii.render(Rasteriser.columns(piece));
            Path file = DIRECTORY.resolve(c.file() + ".txt");
            if (update) {
                Files.createDirectories(DIRECTORY);
                Files.writeString(file, actual, StandardCharsets.UTF_8);
            } else if (!Files.isRegularFile(file)) {
                differences.add(file + " is missing; run with JAVA_TOOL_OPTIONS=-Dstratos.updateSnapshots=true to create it");
            } else if (!Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").equals(actual)) {
                differences.add(file + " differs from the rasteriser's output");
            }
        }
        assertTrue(differences.isEmpty(), String.join("\n", differences));
    }

    /** A solved cardinal straight has the same lanes and ends as G-6's straight piece, so it must give the same blocks. */
    @Test
    void aSolvedCardinalStraightIsG6sStraight() {
        for (Heading heading : List.of(Heading.E, Heading.S, Heading.W, Heading.N)) {
            for (int length : new int[] {2, 7, 40}) {
                int ex = (int) StrictMath.rint(heading.tangent().x() * length);
                int ez = (int) StrictMath.rint(heading.tangent().z() * length);
                RoadPiece solved = piece(heading, ex, ez, heading);
                RoadPiece straight = RoadPiece.straight(RoadProfiles.CITY_17, new BlockXZ(0, 0), heading, length, 64);
                Map<BlockXZ, Column> expected = Rasteriser.columns(straight);
                assertEquals(expected, Rasteriser.columns(solved), heading + " " + length);
            }
        }
    }

    static RoadPiece piece(Heading from, int x, int z, Heading to) {
        return piece(RoadProfiles.CITY_17, from, x, z, to);
    }

    static RoadPiece piece(RoadProfile profile, Heading from, int x, int z, Heading to) {
        PieceSolution solution = PieceSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC,
                End.at(0, 64, 0, from), End.at(x, 64, z, to));
        if (!(solution instanceof PieceSolution.Solved solved)) {
            throw new AssertionError(profile.id() + " " + from + " to (" + x + ", " + z + ") " + to + " was refused: " + solution);
        }
        return RoadPiece.solved(profile, solved, true, 0);
    }
}
