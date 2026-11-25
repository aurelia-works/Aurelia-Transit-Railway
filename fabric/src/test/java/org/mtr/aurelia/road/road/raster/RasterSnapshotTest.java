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
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;

/**
 * ASCII snapshots (spec roads-v1 §2, G-6): a 12-block road of every built-in profile (City street; from G-4+ also
 * residential 15 and 17 and alley 7) from block (0, 0) at each of the 16 headings, with its plan hash on the first line,
 * compared with fabric/src/test/resources/road-snapshots/raster/. Any difference fails. To accept a deliberate change, run
 * {@code JAVA_TOOL_OPTIONS=-Dstratos.updateSnapshots=true ./gradlew :fabric:test --tests <class>} and review the diff before committing.
 */
class RasterSnapshotTest {
    private static final Path DIRECTORY = Path.of("src", "test", "resources", "road-snapshots", "raster");
    private static final String CORE_VERSION = "snapshot";

    @Test
    void everyProfileAtSixteenHeadings() throws IOException {
        boolean update = Boolean.getBoolean("stratos.updateSnapshots");
        List<String> differences = new ArrayList<>();
        for (RoadProfile profile : RoadProfiles.all()) {
            differences.addAll(snapshots(profile, update));
        }
        assertTrue(differences.isEmpty(), String.join("\n", differences));
        if (!update) {
            try (var files = Files.list(DIRECTORY)) {
                assertEquals(16 * RoadProfiles.all().size(), files.count(), "one snapshot per profile and heading");
            }
        }
    }

    private static List<String> snapshots(RoadProfile profile, boolean update) throws IOException {
        List<String> differences = new ArrayList<>();
        for (Heading heading : Heading.values()) {
            RoadPiece piece = RoadPiece.straight(profile, new BlockXZ(0, 0), heading, 12, 64);
            String actual = profile.id() + ", 12 blocks from block (0, 0), heading " + heading + " (" + heading.degrees() + " degrees)\n"
                    + "hash " + PlanHash.hex(Rasteriser.plan(piece, BuiltInPalettes.vanilla()), CORE_VERSION) + "\n"
                    + RasterAscii.render(Rasteriser.columns(piece));
            Path file = DIRECTORY.resolve(String.format(Locale.ROOT, "%s-%02d-%s.txt", profile.id(), heading.index(), heading));
            if (update) {
                Files.createDirectories(DIRECTORY);
                Files.writeString(file, actual, StandardCharsets.UTF_8);
                continue;
            }
            if (!Files.isRegularFile(file)) {
                differences.add(file + " is missing; run with JAVA_TOOL_OPTIONS=-Dstratos.updateSnapshots=true to create it");
            } else if (!Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").equals(actual)) {
                differences.add(file + " differs from the rasteriser's output");
            }
        }
        return differences;
    }
}
