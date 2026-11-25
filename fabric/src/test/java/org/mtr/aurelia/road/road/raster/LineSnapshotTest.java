/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;

/**
 * B-2: where Stratos UK's line blocks fall on City streets, as ASCII snapshots compared with
 * core/src/test/resources/snapshots/lines/ (accept a deliberate change with {@code -PupdateSnapshots=true} and review the
 * diff). Each {@code #} is a block the centre line's paint touches in a dash; dashes are whole blocks (ADR-0044).
 */
class LineSnapshotTest {
    private static final Path DIRECTORY = Path.of("src", "test", "resources", "road-snapshots", "lines");
    private static final String LEGEND = "# line block (the paint touches it, in a dash); : unpainted centre column; L lane, * lane centre; K kerb; F footway";

    record Case(RoadProfile profile, String file, Heading from, int x, int z, Heading to) {
    }

    static final List<Case> CASES = List.of(
            new Case(RoadProfiles.CITY_17, "city-17-straight", Heading.E, 30, 0, Heading.E),
            new Case(RoadProfiles.CITY_17, "city-17-bend-90-r40", Heading.E, 40, 40, Heading.S),
            new Case(RoadProfiles.CITY_17, "city-17-bend-45", Heading.E, 60, 25, Heading.SE),
            new Case(RoadProfiles.CITY_17, "city-17-straight-22", Heading.ESE, 29, 12, Heading.ESE),
            new Case(RoadProfiles.CITY_17, "city-17-diagonal", Heading.SE, 30, 30, Heading.SE),
            new Case(RoadProfiles.CITY_17, "city-17-s-bend", Heading.E, 80, 8, Heading.E));

    @Test
    void linesMatchTheirSnapshots() throws IOException {
        boolean update = Boolean.getBoolean("stratos.updateSnapshots");
        List<String> differences = new ArrayList<>();
        for (Case c : CASES) {
            RoadPiece piece = SolvedRasterSnapshotTest.piece(c.profile(), c.from(), c.x(), c.z(), c.to());
            String actual = String.format(Locale.ROOT, "%s: (0, 0) heading %s to (%d, %d) heading %s, Stratos UK", c.profile().id(), c.from(), c.x(), c.z(), c.to())
                    + "\n" + LEGEND + "\n"
                    + "hash " + PlanHash.hex(Rasteriser.plan(piece, BuiltInPalettes.stratosUk()), "snapshot") + "\n"
                    + render(lined(List.of(piece)));
            Path file = DIRECTORY.resolve(c.file() + ".txt");
            if (update) {
                Files.createDirectories(DIRECTORY);
                Files.writeString(file, actual, StandardCharsets.UTF_8);
            } else if (!Files.isRegularFile(file)) {
                differences.add(file + " is missing; run with -PupdateSnapshots=true to create it");
            } else if (!Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").equals(actual)) {
                differences.add(file + " differs from the rasteriser's output");
            }
        }
        assertTrue(differences.isEmpty(), String.join("\n", differences));
    }

    /** {@code pieces}' columns as a Stratos UK plan stacks them: with its kerbs and its lines. */
    static Map<BlockXZ, Column> lined(List<RoadPiece> pieces) {
        Map<BlockXZ, Column> columns = new HashMap<>();
        for (Map.Entry<BlockXZ, Rasteriser.Cell> entry : Rasteriser.planCells(pieces, BuiltInPalettes.stratosUk())) {
            columns.putIfAbsent(entry.getKey(), entry.getValue().column());
        }
        return columns;
    }

    static String render(Map<BlockXZ, Column> columns) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockXZ block : columns.keySet()) {
            minX = StrictMath.min(minX, block.x());
            minZ = StrictMath.min(minZ, block.z());
            maxX = StrictMath.max(maxX, block.x());
            maxZ = StrictMath.max(maxZ, block.z());
        }
        StringBuilder text = new StringBuilder();
        text.append("x ").append(minX).append("..").append(maxX).append(", z ").append(minZ).append("..").append(maxZ).append('\n');
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                Column column = columns.get(new BlockXZ(x, z));
                char symbol;
                if (column == null) {
                    symbol = '.';
                } else if (column.chord().isPresent()) {
                    symbol = '#';
                } else if (column.band() == Band.KERB || column.band() == Band.FOOTWAY) {
                    symbol = column.band() == Band.KERB ? 'K' : 'F';
                } else {
                    symbol = RasterAscii.symbol(column);
                }
                text.append(symbol);
            }
            text.append('\n');
        }
        return text.toString();
    }
}
