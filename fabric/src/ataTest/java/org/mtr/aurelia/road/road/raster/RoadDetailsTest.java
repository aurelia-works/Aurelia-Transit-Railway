package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;

/**
 * B-4 gullies and P11 crossings on a straight road 100 blocks east from (0, 0): City street's kerbs at z = ±6, so its
 * channel is z = ±5; the alley's gutters are z = ±3. Gullies at 22.5 and 67.5 along each side.
 */
class RoadDetailsTest {
    private static final Palette UK = BuiltInPalettes.stratosUk();

    @Test
    void gulliesSitInTheChannelFacingTheKerb() {
        Map<BlockXZ, Column> gullies = details(RoadProfiles.CITY_17, UK, List.of(), Column.Detail.GULLY);
        assertEquals(Map.of(
                new BlockXZ(22, -5), KerbEdge.Facing.NORTH, new BlockXZ(67, -5), KerbEdge.Facing.NORTH,
                new BlockXZ(22, 5), KerbEdge.Facing.SOUTH, new BlockXZ(67, 5), KerbEdge.Facing.SOUTH), facings(gullies));
        for (Column gully : gullies.values()) {
            assertEquals(Band.LANE, gully.band());
            assertEquals("stratos:gully[facing=" + gully.kerb().get().facing().id() + ",layers=8]", UK.state(gully));
            assertEquals(0, UK.raise(gully));
        }
        // The alley has no kerb: its gullies go in the gutters, the grate on the road's outer edge.
        assertEquals(Map.of(
                new BlockXZ(22, -3), KerbEdge.Facing.NORTH, new BlockXZ(67, -3), KerbEdge.Facing.NORTH,
                new BlockXZ(22, 3), KerbEdge.Facing.SOUTH, new BlockXZ(67, 3), KerbEdge.Facing.SOUTH),
                facings(details(RoadProfiles.ALLEY_7, UK, List.of(), Column.Detail.GULLY)));
    }

    @Test
    void crossingDropsTheKerbsFlushOnBothSides() {
        Map<BlockXZ, Column> dropped = details(RoadProfiles.CITY_17, UK, List.of(new Crossing(50, 3)), Column.Detail.DROPPED_KERB);
        assertEquals(6, dropped.size());
        for (Map.Entry<BlockXZ, Column> entry : dropped.entrySet()) {
            BlockXZ at = entry.getKey();
            assertTrue(at.x() >= 49 && at.x() <= 51 && StrictMath.abs(at.z()) == 6, at.toString());
            Column column = entry.getValue();
            KerbEdge.Facing towardsRoad = at.z() < 0 ? KerbEdge.Facing.SOUTH : KerbEdge.Facing.NORTH;
            assertEquals(towardsRoad, column.kerb().get().facing());
            assertEquals("stratos:dropped_kerb[colour=buff,facing=" + towardsRoad.id() + ",layers=8]", UK.state(column));
            assertEquals(0, UK.raise(column)); // flush with the road; a kerb stands a layer up
        }
        assertEquals(1, UK.raise(cells(RoadProfiles.CITY_17, UK, List.of()).get(new BlockXZ(50, 6))));
    }

    @Test
    void palettesWithoutTheRolesPlaceNeither() {
        List<Crossing> crossing = List.of(new Crossing(50, 3));
        for (Column column : cells(RoadProfiles.CITY_17, BuiltInPalettes.vanilla(), crossing).values()) {
            assertTrue(column.detail().isEmpty(), column.toString());
        }
    }

    private static Map<BlockXZ, KerbEdge.Facing> facings(Map<BlockXZ, Column> columns) {
        Map<BlockXZ, KerbEdge.Facing> facings = new TreeMap<>(Rasteriser::compareZThenX);
        columns.forEach((at, column) -> facings.put(at, column.kerb().get().facing()));
        return facings;
    }

    private static Map<BlockXZ, Column> details(RoadProfile profile, BlockPalette palette, List<Crossing> crossings, Column.Detail detail) {
        Map<BlockXZ, Column> found = new TreeMap<>(Rasteriser::compareZThenX);
        cells(profile, palette, crossings).forEach((at, column) -> {
            if (column.detail().equals(java.util.Optional.of(detail))) {
                found.put(at, column);
            }
        });
        return found;
    }

    private static Map<BlockXZ, Column> cells(RoadProfile profile, BlockPalette palette, List<Crossing> crossings) {
        RoadPiece piece = RoadPiece.straight(profile, new BlockXZ(0, 0), Heading.E, 100, 64);
        Map<BlockXZ, Column> columns = new TreeMap<>(Rasteriser::compareZThenX);
        for (Map.Entry<BlockXZ, Rasteriser.Cell> entry : Rasteriser.planCells(List.of(piece), palette, crossings)) {
            columns.put(entry.getKey(), entry.getValue().column());
        }
        return columns;
    }
}
