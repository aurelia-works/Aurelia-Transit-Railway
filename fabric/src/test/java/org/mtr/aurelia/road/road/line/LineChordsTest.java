/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.line;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** B-2: the line blocks' encoding (ADR-0046, as S15 measured it) and their tops, cut into asphalt and paint. */
class LineChordsTest {
    @Test
    void ninetySixHundredChordsEachOnceAndBackAgain() {
        assertEquals(40, LineChords.PER_EDGE);
        assertEquals(9_600, LineChords.COUNT);
        Set<String> seen = new HashSet<>();
        for (int chord = 0; chord < LineChords.COUNT; chord++) {
            double[] e = LineChords.ends(chord);
            int[] edges = LineChords.edges(chord);
            assertTrue(seen.add(e[0] + "," + e[1] + "," + e[2] + "," + e[3]), "chord " + chord + " repeats another");
            int ka = LineChords.snap(edges[0] == LineChords.NORTH || edges[0] == LineChords.SOUTH ? e[0] : e[1]);
            int kb = LineChords.snap(edges[1] == LineChords.NORTH || edges[1] == LineChords.SOUTH ? e[2] : e[3]);
            assertEquals(chord, LineChords.index(edges[1], kb, edges[0], ka), "either way round");
            for (double v : e) {
                assertTrue(v >= -LineChords.MARGIN && v <= 1 + LineChords.MARGIN, "on the band square: chord " + chord);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> LineChords.index(LineChords.EAST, 3, LineChords.EAST, 4));
        assertThrows(IllegalArgumentException.class, () -> LineChords.ends(LineChords.COUNT));
    }

    @Test
    void aPointSnapsToTheNearestGridPoint() {
        for (int k = 0; k < LineChords.PER_EDGE; k++) {
            double t = LineChords.point(LineChords.NORTH, k)[0];
            assertEquals(k, LineChords.snap(t));
            assertEquals(k, LineChords.snap(t + 0.49 / LineChords.GRID));
            assertEquals(k, LineChords.snap(t - 0.5 / LineChords.GRID), "half-open: the lower half step's edge belongs here");
        }
        assertEquals(0, LineChords.snap(-1));
        assertEquals(LineChords.PER_EDGE - 1, LineChords.snap(2));
    }

    @Test
    void ukWidthsAndTheBlocksIds() {
        assertEquals(0.050, LineKind.WHITE.halfExtent(), 1e-12);
        assertEquals(0.075, LineKind.WHITE_WIDE.halfExtent(), 1e-12);
        assertEquals(0.0375, LineKind.YELLOW.halfExtent(), 1e-12);
        assertEquals(0.1125, LineKind.DOUBLE_YELLOW.halfExtent(), 1e-12);
        for (LineKind kind : LineKind.values()) {
            assertTrue(kind.halfExtent() < LineChords.MARGIN, kind + "'s paint stays inside the band square");
        }
        // IndusShaders' line-block list (block.properties ID 10030) names exactly these.
        assertEquals(List.of("white_line", "white_line_wide", "yellow_line", "double_yellow_line"),
                List.of(LineKind.values()).stream().map(LineKind::id).toList());
    }

    @Test
    void everyTopIsCoveredOnceWithAsphaltFirst() {
        for (LineKind kind : LineKind.values()) {
            for (int chord = 0; chord < LineChords.COUNT; chord++) {
                List<LineTop.Piece> pieces = LineTop.pieces(kind, chord);
                String what = kind + " chord " + chord;
                assertEquals(1, pieces.stream().mapToDouble(LineTop.Piece::area).sum(), 1e-9, what);
                assertFalse(pieces.get(0).paint(), what + " starts with paint");
                for (LineTop.Piece piece : pieces) {
                    assertTrue(piece.x().length >= 3, what);
                    assertTrue(signedArea(piece) < 0, what + ": corners not in the up face's order");
                }
            }
        }
    }

    @Test
    void aLineAcrossTheMiddleIsPaintedItsFullWidth() {
        // West to east through the block's middle (the grid points nearest z = 0.5): 100 mm of white.
        int k = LineChords.snap(0.5);
        int chord = LineChords.index(LineChords.WEST, k, LineChords.EAST, k);
        double paint = LineTop.pieces(LineKind.WHITE, chord).stream().filter(LineTop.Piece::paint).mapToDouble(LineTop.Piece::area).sum();
        assertEquals(0.100, paint, 1e-9);
        double doubled = LineTop.pieces(LineKind.DOUBLE_YELLOW, chord).stream().filter(LineTop.Piece::paint).mapToDouble(LineTop.Piece::area).sum();
        assertEquals(0.150, doubled, 1e-9, "two 75 mm stripes");
    }

    /** Twice the signed area in (x, z): negative for the up face's corner order (0,0), (0,1), (1,1), (1,0). */
    private static double signedArea(LineTop.Piece piece) {
        double twice = 0;
        for (int i = 0; i < piece.x().length; i++) {
            int j = (i + 1) % piece.x().length;
            twice += piece.x()[i] * piece.z()[j] - piece.x()[j] * piece.z()[i];
        }
        return twice;
    }
}
