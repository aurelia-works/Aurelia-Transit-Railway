/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.RoadGroup;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.Section;

/** The rasteriser on straight City street pieces (spec roads-v1 §7.1), at all 16 headings. */
class RasteriserTest {
    private static final RoadProfile CITY = RoadProfiles.CITY_17;
    private static final LineMarking DASHES = LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4);
    private static final BlockXZ ORIGIN = new BlockXZ(0, 0);

    private static String crossSection(Map<BlockXZ, Column> columns, int x) {
        StringBuilder text = new StringBuilder();
        for (int z = -8; z <= 8; z++) {
            text.append(RasterAscii.symbol(columns.get(new BlockXZ(x, z))));
        }
        return text.toString();
    }

    @Test
    void theSpecsCrossSectionHeadingEast() {
        Map<BlockXZ, Column> columns = Rasteriser.columns(RoadPiece.straight(CITY, ORIGIN, Heading.E, 12, 64));
        // Spec roads-v1 §2 (F F F L L * L L | L L * L L F F F; ADR-0039), with footway 3 split into footway 2 + kerb 1.
        assertEquals("FFKLL*LL:LL*LLKFF", crossSection(columns, 3));
        assertEquals("FFKLL*LL|LL*LLKFF", crossSection(columns, 0));
        assertEquals('.', RasterAscii.symbol(columns.get(new BlockXZ(3, -9))));
        assertEquals('.', RasterAscii.symbol(columns.get(new BlockXZ(3, 9))));
        assertEquals(17 * 13, columns.size(), "x 0..12 (the end is closed) by z -8..8");
    }

    @Test
    void dashesAreTwoOnFourOffAndThePhaseShiftsThem() {
        Map<BlockXZ, Column> columns = Rasteriser.columns(RoadPiece.straight(CITY, ORIGIN, Heading.E, 24, 64));
        List<Integer> painted = new ArrayList<>();
        for (int x = 0; x <= 24; x++) {
            if (columns.get(new BlockXZ(x, 0)).painted()) {
                painted.add(x);
            }
        }
        assertEquals(List.of(0, 1, 6, 7, 12, 13, 18, 19, 24), painted);

        RoadPiece base = RoadPiece.straight(CITY, ORIGIN, Heading.E, 12, 64);
        Map<BlockXZ, Column> shifted = Rasteriser.columns(new RoadPiece(CITY, base.anchor(), base.lanes(), 64, true, 1));
        List<Integer> paintedShifted = new ArrayList<>();
        for (int x = 0; x <= 12; x++) {
            if (shifted.get(new BlockXZ(x, 0)).painted()) {
                paintedShifted.add(x);
            }
        }
        assertEquals(List.of(0, 5, 6, 11, 12), paintedShifted, "(x + 1) mod 6 < 2");
    }

    /** The band a column must get on a straight City street, worked out directly from its offset d from the reference line. */
    private static Column expected(double d, double s, double length, double phase) {
        if (s < 0 || s > length) {
            return null;
        }
        if (d >= -3 && d < 3) { // between the two lanes: m = d, centre line on [-0.5, 0.5)
            if (d >= -0.5 && d < 0.5) {
                return new Column(Band.CENTRE_LINE, Rasteriser.isPainted(DASHES, s + phase), false);
            }
            return new Column(Band.LANE, false, (d >= -3 && d < -2.5) || (d >= 2.5 && d < 3.5));
        }
        double e = d < 0 ? -d - 5.5 : d - 5.5; // beyond the outer lane's edge
        if (e < 0) {
            return new Column(Band.LANE, false, (d >= -3.5 && d < -2.5) || (d >= 2.5 && d < 3.5));
        }
        if (e < 1) {
            return new Column(Band.KERB, false, false);
        }
        if (e < 3) {
            return new Column(Band.FOOTWAY, false, false);
        }
        return null;
    }

    @Test
    void noGapsNoDoubleClaimsAndTheRightBandAtAllSixteenHeadings() {
        List<BlockXZ> anchors = List.of(ORIGIN, new BlockXZ(-1234, 567), new BlockXZ(29_999_000, -29_999_000), new BlockXZ(-29_999_000, -12));
        double[] lengths = {1, 7, 12, 40, 101};
        int checked = 0;
        for (Heading heading : Heading.values()) {
            for (BlockXZ anchor : anchors) {
                for (double length : lengths) {
                    RoadPiece piece = RoadPiece.straight(CITY, anchor, heading, length, 70);
                    Map<BlockXZ, Column> columns = Rasteriser.columns(piece);
                    // A plan builder refuses double claims, so building the plan proves there are none.
                    BlockPlan plan = Rasteriser.plan(piece, BuiltInPalettes.vanilla());
                    assertEquals(columns.size() * 8, plan.blockCount(), heading + " " + anchor + " " + length + ": the surface, 2 of foundation, 5 of air");

                    // The oracle measures from the anchor block's centre (city-17 is odd, so the reference starts there).
                    Frame frame = new Frame(Vec2.ZERO, heading);
                    Vec2 end = frame.point(length, 0);
                    int minX = (int) StrictMath.floor(StrictMath.min(0, end.x())) - 13;
                    int maxX = (int) StrictMath.ceil(StrictMath.max(0, end.x())) + 13;
                    int minZ = (int) StrictMath.floor(StrictMath.min(0, end.z())) - 13;
                    int maxZ = (int) StrictMath.ceil(StrictMath.max(0, end.z())) + 13;
                    int expectedCount = 0;
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            Vec2 p = new Vec2(x, z);
                            Column want = expected(Rasteriser.snap(frame.side(p)), Rasteriser.snap(frame.along(p)), length, 0);
                            Column got = columns.get(new BlockXZ(anchor.x() + x, anchor.z() + z));
                            assertEquals(want, got, heading + " " + anchor + " length " + length + " at local " + p);
                            expectedCount += want == null ? 0 : 1;
                            checked++;
                        }
                    }
                    assertEquals(expectedCount, columns.size(), "nothing outside the footprint: " + heading + " " + anchor + " " + length);
                }
            }
        }
        assertTrue(checked > 800_000, "columns checked: " + checked);
    }

    @Test
    void anOpenEndLeavesItsLastColumnToTheNextPiece() {
        RoadPiece closed = RoadPiece.straight(CITY, ORIGIN, Heading.E, 12, 64);
        RoadPiece open = new RoadPiece(CITY, closed.anchor(), closed.lanes(), 64, false, 0);
        Map<BlockXZ, Column> openColumns = Rasteriser.columns(open);
        assertTrue(openColumns.containsKey(new BlockXZ(0, 0)), "the start column is included");
        assertFalse(openColumns.containsKey(new BlockXZ(12, 0)), "s = L belongs to the next piece");
        assertTrue(Rasteriser.columns(closed).containsKey(new BlockXZ(12, 0)));
    }

    @Test
    void theSameRoadGivesTheSamePlanAndMovingItMovesEveryBlock() {
        RoadPiece piece = RoadPiece.straight(CITY, new BlockXZ(5, -7), Heading.S, 30, 64);
        BlockPlan a = Rasteriser.plan(piece, BuiltInPalettes.vanilla());
        BlockPlan b = Rasteriser.plan(piece, BuiltInPalettes.vanilla());
        assertEquals(a, b);
        assertEquals(PlanHash.hex(a, "test"), PlanHash.hex(b, "test"));

        for (int[] move : new int[][] {{1, 0}, {-17, 3}, {1_000_000, -2_000_000}, {-29_000_000, 29_000_000}}) {
            BlockPlan moved = Rasteriser.plan(RoadPiece.straight(CITY, new BlockXZ(5 + move[0], -7 + move[1]), Heading.S, 30, 64), BuiltInPalettes.vanilla());
            Map<String, String> expected = new HashMap<>();
            a.blocks().forEach(block -> expected.put((block.x() + move[0]) + "," + (block.z() + move[1]), block.state()));
            Map<String, String> actual = new HashMap<>();
            moved.blocks().forEach(block -> actual.put(block.x() + "," + block.z(), block.state()));
            assertEquals(expected, actual, "moved by " + move[0] + ", " + move[1]);
        }
    }

    @Test
    void theVanillaPaletteUsesTheOwnersBlocks() {
        Map<BlockXZ, Column> columns = Rasteriser.columns(RoadPiece.straight(CITY, ORIGIN, Heading.E, 12, 64));
        org.mtr.aurelia.road.road.palette.Palette palette = BuiltInPalettes.vanilla();
        assertEquals("minecraft:smooth_stone", palette.state(columns.get(new BlockXZ(0, -8))));
        assertEquals("minecraft:polished_andesite", palette.state(columns.get(new BlockXZ(0, -6))));
        assertEquals("minecraft:polished_deepslate", palette.state(columns.get(new BlockXZ(0, -5))));
        assertEquals("minecraft:polished_deepslate", palette.state(columns.get(new BlockXZ(0, -3))));
        assertEquals("minecraft:smooth_quartz", palette.state(columns.get(new BlockXZ(0, 0))));
        assertEquals("minecraft:polished_deepslate", palette.state(columns.get(new BlockXZ(3, 0))), "an unpainted gap in the dashes");
        assertEquals(3465, palette.dataVersion());
        assertThrows(IllegalArgumentException.class, () -> palette.state(new Column(Band.VERGE, false, false)));
        assertEquals(palette.state(columns.get(new BlockXZ(0, -3))), palette.state(new Column(Band.EDGE, false, false)), "an edge is road surface (R-9)");
    }

    @Test
    void piecesAreCheckedWhenMade() {
        List<LaneCurve> oneLane = List.of(new LineSegment(Vec2.ZERO, Heading.E.tangent(), 10));
        assertThrows(IllegalArgumentException.class, () -> new RoadPiece(CITY, ORIGIN, oneLane, 64, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new LineSegment(Vec2.ZERO, Heading.E.tangent(), 0));
        assertThrows(IllegalArgumentException.class, () -> new LineSegment(Vec2.ZERO, new Vec2(2, 0), 5));

        RoadProfile edgeLines = new RoadProfile("city-21", RoadGroup.CITY_STREET, List.of(
                Section.of(Band.FOOTWAY, 2), Section.of(Band.KERB, 1), Section.marked(Band.EDGE, 2, LineMarking.solid(LineMarking.Colour.WHITE)),
                Section.of(Band.LANE, 5), Section.marked(Band.CENTRE_LINE, 1, DASHES), Section.of(Band.LANE, 5),
                Section.marked(Band.EDGE, 2, LineMarking.solid(LineMarking.Colour.WHITE)), Section.of(Band.KERB, 1), Section.of(Band.FOOTWAY, 2)));
        assertThrows(IllegalArgumentException.class, () -> RoadPiece.straight(edgeLines, ORIGIN, Heading.E, 10, 64));
    }

    @Test
    void lineSegmentsMeasureAlongAndToTheRight() {
        LineSegment south = LineSegment.between(new Vec2(2, 2), new Vec2(2, 12));
        assertEquals(10, south.length());
        assertEquals(new Vec2(0, 1), south.direction());
        assertEquals(new LaneCurve.Local(3, -1), south.local(new Vec2(3, 5)), "east of a southbound line is to its left");
        assertNotEquals(0, south.local(new Vec2(1, 5)).d());
        assertEquals(new Vec2(2, 12), south.end());
    }
}
