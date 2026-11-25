/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfiles;

/** The lane-node generator and the refusal of pieces MTR would join the other way round (task G-8). */
class LaneNodesTest {
    static PieceSolution solve(End start, End end) {
        return PieceSolver.solve(RoadProfiles.CITY_17, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, start, end);
    }

    static PieceSolution.Solved solved(End start, End end) {
        return assertInstanceOf(PieceSolution.Solved.class, solve(start, end));
    }

    @Test
    void aRoadHasOneMoreNodePerLaneThanItHasPieces() {
        End a = End.at(100, 64, 200, Heading.E);
        End b = End.at(140, 64, 200, Heading.E);
        End c = End.at(180, 64, 240, Heading.S);
        End d = End.at(180, 64, 280, Heading.S);
        List<LaneNode> nodes = LaneNodes.of(List.of(solved(a, b), solved(b, c), solved(c, d)));
        assertEquals(List.of(
                new LaneNode(1, 100, 64, 197, Heading.E), new LaneNode(1, 140, 64, 197, Heading.E),
                new LaneNode(1, 183, 64, 240, Heading.S), new LaneNode(1, 183, 64, 280, Heading.S),
                new LaneNode(2, 100, 64, 203, Heading.E), new LaneNode(2, 140, 64, 203, Heading.E),
                new LaneNode(2, 177, 64, 240, Heading.S), new LaneNode(2, 177, 64, 280, Heading.S)), nodes,
                "lane 1 (left) is north heading east and east heading south; pieces share their nodes");
    }

    @Test
    void aNodeStoresItsHeadingModulo180() {
        assertEquals(0, new LaneNode(1, 0, 64, 0, Heading.E).mtrFacing());
        assertEquals(0, new LaneNode(1, 0, 64, 0, Heading.W).mtrFacing(), "nodes are direction-less");
        assertEquals(3, new LaneNode(1, 0, 64, 0, Heading.of(11)).mtrFacing());
        assertEquals(65, new LaneNode(1, 0, 64, 0, Heading.E).nodeY(), "on top of the surface block (S6a)");
    }

    @Test
    void piecesThatDontMeetAreRefused() {
        PieceSolution.Solved one = solved(End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));
        PieceSolution.Solved elsewhere = solved(End.at(41, 64, 0, Heading.E), End.at(80, 64, 0, Heading.E));
        assertThrows(IllegalArgumentException.class, () -> LaneNodes.of(List.of(one, elsewhere)));
        assertThrows(IllegalArgumentException.class, () -> LaneNodes.of(List.of()));
    }

    @Test
    void aBendMtrWouldJoinTheOtherWayRoundIsRefused() {
        // From G-8's survey: heading south, ending far round to the west heading NNW.
        PieceSolution solution = solve(End.at(0, 64, 0, Heading.S), End.at(-130, 64, 1, Heading.NNW));
        PieceSolution.Refused refused = assertInstanceOf(PieceSolution.Refused.class, solution);
        assertEquals(PieceSolver.NODES_JOIN_OTHER_WAY, refused.code());
        assertEquals(1, refused.args().size());
        assertEquals(Map.of("n", refused.args().get("n")), refused.args());
    }
}
