/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.raster.KerbEdge.Facing;
import org.mtr.aurelia.road.road.raster.KerbEdge.Shape;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * B-1: real kerbs in the Stratos UK palette. ASCII snapshots of where each kerb meets the road, compared with
 * core/src/test/resources/snapshots/kerbs/ (accept a deliberate change with {@code -PupdateSnapshots=true} and review the
 * diff), and over many roads: wherever the raised footway meets the road at a side, a kerb's stone runs along that side.
 */
class KerbSnapshotTest {
    private static final Path DIRECTORY = Path.of("src", "test", "resources", "road-snapshots", "kerbs");
    private static final String LEGEND = "kerbs facing the road: ^ > v < straight (stone on that side); n e s w outer corner (stone on that side"
            + " and the next clockwise); 1 2 3 4 inner corner (stone in the corner clockwise from north, east, south, west); F footway;"
            + " L lane, * lane centre, | : centre line, G gutter";

    record Case(RoadProfile profile, String file, Heading from, int x, int z, Heading to) {
    }

    static final List<Case> CASES = List.of(
            new Case(RoadProfiles.CITY_17, "city-17-straight", Heading.E, 12, 0, Heading.E),
            new Case(RoadProfiles.CITY_17, "city-17-bend-90-r40", Heading.E, 40, 40, Heading.S),
            new Case(RoadProfiles.CITY_17, "city-17-bend-45", Heading.E, 60, 25, Heading.SE),
            new Case(RoadProfiles.CITY_17, "city-17-straight-22", Heading.ESE, 29, 12, Heading.ESE),
            new Case(RoadProfiles.CITY_17, "city-17-diagonal", Heading.SE, 20, 20, Heading.SE),
            new Case(RoadProfiles.RESIDENTIAL_15, "residential-15-bend-90-r20", Heading.E, 20, 20, Heading.S),
            new Case(RoadProfiles.ALLEY_7, "alley-7-bend-90-r12", Heading.E, 12, 12, Heading.S));

    @Test
    void kerbsMatchTheirSnapshots() throws IOException {
        boolean update = Boolean.getBoolean("stratos.updateSnapshots");
        List<String> differences = new ArrayList<>();
        for (Case c : CASES) {
            RoadPiece piece = piece(c.profile(), c.from(), c.x(), c.z(), c.to());
            String actual = String.format(Locale.ROOT, "%s: (0, 0) heading %s to (%d, %d) heading %s, Stratos UK", c.profile().id(), c.from(), c.x(), c.z(), c.to())
                    + "\n" + LEGEND + "\n"
                    + "hash " + PlanHash.hex(Rasteriser.plan(piece, BuiltInPalettes.stratosUk()), "snapshot") + "\n"
                    + render(kerbed(piece));
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

    /**
     * Every side where the footway meets the road has a kerb's stone along it, on bends at every angle. A column with the
     * road on three sides, or on two opposite ones, is a strip one block wide (on a hairpin, where a row of the road steps
     * a block); no kerb has stone on three sides, so those are only counted, and must stay rare.
     */
    @Test
    void theRoadNeverMeetsTheFootwayWithoutAKerb() {
        int roads = 0;
        int kerbs = 0;
        int strips = 0;
        List<String> bare = new ArrayList<>();
        for (RoadProfile profile : List.of(RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_15)) {
            for (Heading from : List.of(Heading.E, Heading.ESE, Heading.SE)) {
                for (int x = 20; x <= 80; x += 15) {
                    for (int z = -60; z <= 60; z += 15) {
                        for (Heading to : Heading.values()) {
                            Optional<RoadPiece> piece = solved(profile, from, x, z, to);
                            if (piece.isEmpty()) {
                                continue;
                            }
                            roads++;
                            Map<BlockXZ, Column> columns = kerbed(piece.get());
                            for (Map.Entry<BlockXZ, Column> entry : columns.entrySet()) {
                                Column column = entry.getValue();
                                if (!raised(column)) {
                                    continue;
                                }
                                kerbs += column.kerb().isPresent() ? 1 : 0;
                                Set<Facing> road = EnumSet.noneOf(Facing.class);
                                for (Facing side : Facing.values()) {
                                    Column beside = columns.get(entry.getKey().plus(side.step()));
                                    if (beside != null && !raised(beside)) {
                                        road.add(side);
                                    }
                                }
                                if (road.size() > 2 || road.size() == 2 && road.stream().noneMatch(side -> road.contains(side.clockwise()))) {
                                    strips++;
                                    continue;
                                }
                                for (Facing side : road) {
                                    if (!stone(column).contains(side)) {
                                        bare.add(profile.id() + " " + from + " to (" + x + ", " + z + ") " + to + ": " + entry.getKey() + " " + side);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(roads > 200, "enough roads: " + roads);
        assertTrue(kerbs > 10_000, "enough kerbs: " + kerbs);
        assertTrue(strips * 10_000 < kerbs, "strips one block wide are rare: " + strips + " in " + kerbs + " kerbs");
        assertEquals(List.of(), bare.subList(0, StrictMath.min(10, bare.size())), bare.size() + " sides without a kerb");
    }

    /** On a straight road along the grid, the kerbs are exactly the kerb band, straight, and the footway and kerb stand a layer up. */
    @Test
    void aStraightRoadsKerbsAreItsKerbBandOneLayerUp() {
        RoadPiece piece = piece(RoadProfiles.CITY_17, Heading.E, 12, 0, Heading.E);
        Map<BlockXZ, Column> plain = Rasteriser.columns(piece);
        Map<BlockXZ, Column> kerbed = kerbed(piece);
        for (Map.Entry<BlockXZ, Column> entry : plain.entrySet()) {
            Column column = kerbed.get(entry.getKey());
            assertEquals(entry.getValue().band(), column.band(), entry.getKey().toString());
            if (column.band() == Band.KERB) {
                Facing facing = entry.getKey().z() < 0 ? Facing.SOUTH : Facing.NORTH;
                assertEquals(Optional.of(new KerbEdge(facing, Shape.STRAIGHT)), column.kerb(), entry.getKey().toString());
            }
        }
        BlockPlan plan = Rasteriser.plan(piece, BuiltInPalettes.stratosUk());
        Map<String, String> at = new HashMap<>();
        plan.blocks().forEach(block -> at.put(block.x() + "," + block.y() + "," + block.z(), block.state()));
        assertEquals("stratos:asphalt[layers=8]", at.get("5,64,0"), "the lane's top, a whole block");
        assertEquals("minecraft:air", at.get("5,65,0"));
        assertEquals("stratos:kerb[facing=south,layers=8,shape=straight]", at.get("5,64,-6"));
        assertEquals("stratos:kerb[facing=south,layers=1,shape=straight]", at.get("5,65,-6"), "the kerb a layer above");
        assertEquals("stratos:footway_flags[layers=1]", at.get("5,65,-8"));
        assertEquals("stratos:kerb[facing=north,layers=1,shape=straight]", at.get("5,65,6"));
        assertEquals("minecraft:air", at.get("5,66,6"));
    }

    /** {@code piece}'s columns as a Stratos UK plan gives them its kerbs. */
    static Map<BlockXZ, Column> kerbed(RoadPiece piece) {
        Map<BlockXZ, Column> columns = new HashMap<>();
        for (Map.Entry<BlockXZ, Rasteriser.Cell> entry : Rasteriser.withKerbs(new ArrayList<>(Rasteriser.cells(piece).entrySet()))) {
            columns.put(entry.getKey(), entry.getValue().column());
        }
        return columns;
    }

    private static boolean raised(Column column) {
        return column.band() == Band.KERB || column.band() == Band.FOOTWAY;
    }

    /** The sides a column's kerb stone runs along. */
    private static Set<Facing> stone(Column column) {
        if (column.kerb().isEmpty()) {
            return Set.of();
        }
        KerbEdge kerb = column.kerb().get();
        return switch (kerb.shape()) {
            case STRAIGHT -> Set.of(kerb.facing());
            case OUTER -> Set.of(kerb.facing(), kerb.facing().clockwise());
            case INNER -> Set.of();
        };
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
                text.append(symbol(columns.get(new BlockXZ(x, z))));
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static char symbol(Column column) {
        if (column == null || column.kerb().isEmpty()) {
            return RasterAscii.symbol(column);
        }
        KerbEdge kerb = column.kerb().get();
        int side = kerb.facing().ordinal();
        return switch (kerb.shape()) {
            case STRAIGHT -> "^>v<".charAt(side);
            case OUTER -> "nesw".charAt(side);
            case INNER -> "1234".charAt(side);
        };
    }

    private static RoadPiece piece(RoadProfile profile, Heading from, int x, int z, Heading to) {
        return solved(profile, from, x, z, to).orElseThrow(() -> new AssertionError(profile.id() + " " + from + " to (" + x + ", " + z + ") " + to + " was refused"));
    }

    private static Optional<RoadPiece> solved(RoadProfile profile, Heading from, int x, int z, Heading to) {
        PieceSolution solution = PieceSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC,
                End.at(0, 64, 0, from), End.at(x, 64, z, to));
        return solution instanceof PieceSolution.Solved solved ? Optional.of(RoadPiece.solved(profile, solved, true, 0)) : Optional.empty();
    }
}
