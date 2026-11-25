/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import java.util.ArrayList;
import java.util.List;
import org.mtr.aurelia.road.road.RoadPlanParams;

/**
 * The lane-node generator (spec roads-v1 §9; ADR-0033; task G-8): every node of a road made of solved pieces, lane by
 * lane, in order along the road. A road of k pieces has k + 1 nodes per lane; neighbouring pieces share theirs.
 *
 * <p>Each node pair along a lane is joined by MTR exactly as the lane was built: the solver refuses any lane MTR's
 * connector would join another way ({@link PieceSolver#NODES_JOIN_OTHER_WAY}), and {@code MtrJoinsLanesTest} checks
 * MTR 4.0.5's own connector against the lanes. Heights are each piece's height until G-7 and S6 settle how a node sits
 * on the surface.
 */
public final class LaneNodes {
    private LaneNodes() {
    }

    /** The nodes of the road made of {@code pieces}, in order: lane 1's nodes along the road, then lane 2's, and so on. */
    public static List<LaneNode> of(List<PieceSolution.Solved> pieces) {
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("A road has at least one piece");
        }
        for (int i = 0; i + 1 < pieces.size(); i++) {
            RoadPlanParams.End end = pieces.get(i).end();
            RoadPlanParams.End next = pieces.get(i + 1).start();
            if (end.x() != next.x() || end.y() != next.y() || end.z() != next.z() || end.heading() != next.heading()) {
                throw new IllegalArgumentException("Piece " + (i + 2) + " doesn't start where piece " + (i + 1) + " ends (" + end + ", " + next + ")");
            }
        }
        List<LaneNode> nodes = new ArrayList<>();
        int lanes = pieces.get(0).lanes().size();
        for (int lane = 0; lane < lanes; lane++) {
            for (int i = 0; i < pieces.size(); i++) {
                LaneRail rail = pieces.get(i).lanes().get(lane);
                if (i == 0) {
                    nodes.add(node(rail.number(), rail.start()));
                }
                nodes.add(node(rail.number(), rail.end()));
            }
        }
        return nodes;
    }

    private static LaneNode node(int lane, RoadPlanParams.End end) {
        return new LaneNode(lane, end.x(), end.y(), end.z(), end.heading());
    }
}
